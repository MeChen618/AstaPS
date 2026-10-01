package emu.grasscutter.server.born;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketFinishedParentQuestNotify;
import emu.grasscutter.server.packet.send.PacketPlayerEnterSceneNotify;
import emu.grasscutter.server.packet.send.PacketQuestGlobalVarNotify;
import emu.grasscutter.server.packet.send.PacketQuestListNotify;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Coordinates the native 7.1 intro between character creation and first scene entry. */
public final class BornIntroGate {
    private static final Map<GameSession, State> AWAITING_NATIVE_INTRO =
            Collections.synchronizedMap(new WeakHashMap<>());

    private static final class State {
        private boolean sawUnpaused;
        private int completedPauseCycles;
        private boolean cutoverStarted;
        private boolean earlySceneEntrySent;
        private boolean worldLoginComplete;
        private boolean questStarted;
    }

    private BornIntroGate() {}

    public static void arm(GameSession session) {
        if (session != null) {
            AWAITING_NATIVE_INTRO.put(session, new State());
        }
    }

    public static boolean isAwaiting(GameSession session) {
        return session != null && AWAITING_NATIVE_INTRO.containsKey(session);
    }

    /**
     * Player.onLogin normally emits PlayerEnterSceneNotify near the end of its initialization tail.
     * Fresh-born 7.1 sends that packet at the native intro boundary instead, so suppress the later
     * duplicate while preserving the already-issued scene token.
     */
    public static boolean shouldSuppressLoginSceneEntry(GameSession session) {
        if (session == null) return false;
        synchronized (AWAITING_NATIVE_INTRO) {
            State state = AWAITING_NATIVE_INTRO.get(session);
            return state != null && state.earlySceneEntrySent;
        }
    }

    /**
     * Runtime traces from the 7.1 client show two false->true pause cycles after 26105. The second
     * cycle ends exactly at the native intro handoff, so use that protocol-visible boundary instead
     * of a fixed delay.
     */
    public static void notePause(GameSession session, boolean paused) {
        if (session == null) return;

        int completedCycles = 0;
        boolean enterWorld = false;
        synchronized (AWAITING_NATIVE_INTRO) {
            State state = AWAITING_NATIVE_INTRO.get(session);
            if (state == null || state.cutoverStarted) return;

            if (!paused) {
                state.sawUnpaused = true;
                return;
            }
            if (!state.sawUnpaused) return;

            state.sawUnpaused = false;
            completedCycles = ++state.completedPauseCycles;
            if (completedCycles >= 2) {
                state.cutoverStarted = true;
                enterWorld = true;
            }
        }

        if (enterWorld) {
            enterWorld(session);
        }
    }

    private static void enterWorld(GameSession session) {
        var player = session.getPlayer();
        if (player == null) {
            AWAITING_NATIVE_INTRO.remove(session);
            return;
        }

        synchronized (player) {
            try {
                // The client needs scene-entry immediately at the native intro boundary. The full
                // login tail can be much slower on a cold account, while this packet only depends on
                // persisted player position/scene/world-level state.
                session.send(new PacketPlayerEnterSceneNotify(player));
                synchronized (AWAITING_NATIVE_INTRO) {
                    State state = AWAITING_NATIVE_INTRO.get(session);
                    if (state != null) state.earlySceneEntrySent = true;
                }

                player.onLogin();

                synchronized (AWAITING_NATIVE_INTRO) {
                    State state = AWAITING_NATIVE_INTRO.get(session);
                    if (state != null) state.worldLoginComplete = true;
                }
            } catch (Throwable t) {
                Grasscutter.getLogger()
                        .error(
                                "Failed to enter the world after the native fresh-player intro for uid {}.",
                                player.getUid(),
                                t);
            }
        }
    }

    /** Starts the fresh-player quest lifecycle once the first scene handshake has completed. */
    public static void finishOnSceneReady(GameSession session) {
        if (session == null) return;

        synchronized (AWAITING_NATIVE_INTRO) {
            State state = AWAITING_NATIVE_INTRO.get(session);
            if (state == null || !state.worldLoginComplete || state.questStarted) return;
            state.questStarted = true;
        }

        var player = session.getPlayer();
        if (player == null) {
            AWAITING_NATIVE_INTRO.remove(session);
            return;
        }

        try {
            player.getQuestManager().onPlayerBorn();
            session.send(new PacketFinishedParentQuestNotify(player));
            session.send(new PacketQuestListNotify(player));
            session.send(new PacketQuestGlobalVarNotify(player));
            AWAITING_NATIVE_INTRO.remove(session);
        } catch (Throwable t) {
            synchronized (AWAITING_NATIVE_INTRO) {
                State state = AWAITING_NATIVE_INTRO.get(session);
                if (state != null) state.questStarted = false;
            }
            Grasscutter.getLogger()
                    .error(
                            "Failed to start fresh-player quests after first scene entry for uid {}.",
                            player.getUid(),
                            t);
        }
    }
}
