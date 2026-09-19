package emu.grasscutter.utils;

import emu.grasscutter.config.ConfigContainer;
import emu.grasscutter.game.player.Player;

/** Builds gradient-rich watermark text with optional player UID suffix. */
public final class WatermarkGradientHelper {
    /** Mid stop: cyan-blue, clearly distinct from purple and pink. */
    private static final int MID_BLUE = 0x0EA5E9;

    private static final ThreadLocal<Player> CURRENT_PLAYER = new ThreadLocal<>();

    private WatermarkGradientHelper() {}

    public static void setCurrentPlayer(Player player) {
        CURRENT_PLAYER.set(player);
    }

    public static void clearCurrentPlayer() {
        CURRENT_PLAYER.remove();
    }

    public static Player getCurrentPlayer() {
        return CURRENT_PLAYER.get();
    }

    public static String buildDisplayText(ConfigContainer.GameOptions.WatermarkOptions opts, Player player) {
        String text = opts.text == null ? "" : opts.text.trim();
        if (player == null || player.getUid() <= 0) {
            return text;
        }
        String uid = String.valueOf(player.getUid());
        if (text.contains("{uid}")) {
            return text.replace("{uid}", uid);
        }
        if (text.endsWith("｜") || text.endsWith("|")) {
            return text + uid;
        }
        if (text.isEmpty()) {
            return uid;
        }
        return text + "｜" + uid;
    }

    /** Left-to-right purple -> blue -> pink across the full display string (including UID). */
    public static String applyGradient(ConfigContainer.GameOptions.WatermarkOptions opts, String text) {
        int purple = RichTextUtils.parseColor(opts.color);
        int pink = RichTextUtils.parseColor(opts.gradientTo);
        if (purple < 0 && pink < 0) {
            return text;
        }
        if (purple < 0) {
            purple = pink;
        }
        if (pink < 0) {
            pink = purple;
        }

        String perChar = applyTriGradient(text, purple, MID_BLUE, pink);
        if (WatermarkUtils.fits(perChar)) {
            return perChar;
        }

        // Per-char tags exceed 254 bytes: merge adjacent chars with the same gradient step.
        String compact = applyTriGradientCompact(text, purple, MID_BLUE, pink);
        if (WatermarkUtils.fits(compact)) {
            return compact;
        }

        return text;
    }

    private static int colorAt(float t, int start, int mid, int end) {
        if (t <= 0.5f) {
            return RichTextUtils.lerpColor(start, mid, t / 0.5f);
        }
        return RichTextUtils.lerpColor(mid, end, (t - 0.5f) / 0.5f);
    }

    private static String applyTriGradient(String text, int start, int mid, int end) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        int len = text.length();
        if (len == 1) {
            return RichTextUtils.colorize(text, start);
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            char ch = text.charAt(i);
            if (Character.isWhitespace(ch)) {
                sb.append(ch);
                continue;
            }
            float t = (float) i / (len - 1);
            sb.append(RichTextUtils.colorize(String.valueOf(ch), colorAt(t, start, mid, end)));
        }
        return sb.toString();
    }

    /**
     * Same tri-gradient curve as {@link #applyTriGradient}, but groups characters to stay within the
     * 254-byte WindSeed text budget while keeping UID digits on the blue->pink half of the ramp.
     */
    private static String applyTriGradientCompact(String text, int start, int mid, int end) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        int len = text.length();
        for (int segments = len; segments >= 1; segments--) {
            StringBuilder sb = new StringBuilder();
            for (int s = 0; s < segments; s++) {
                int from = s * len / segments;
                int to = (s + 1) * len / segments;
                if (from >= to) {
                    continue;
                }
                float centerT = len <= 1 ? 0f : ((from + to - 1) / 2f) / (len - 1);
                sb.append(RichTextUtils.colorize(text.substring(from, to), colorAt(centerT, start, mid, end)));
            }
            String candidate = sb.toString();
            if (WatermarkUtils.fits(candidate)) {
                return candidate;
            }
        }
        return text;
    }
}
