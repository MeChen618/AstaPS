package emu.grasscutter.command.commands;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import emu.grasscutter.command.CommandMap;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

/** The public command grammar must not reintroduce duplicate routes. */
public final class CommandRedundancyTest {
    @Test
    void removedTopLevelCommandsStayUnregistered() {
        var map = new CommandMap(false);
        map.registerCommand("kill", new KillCommand());
        map.registerCommand("restore", new RestoreCommand());

        for (String removed : new String[] {"killall", "er", "e", "energy"}) {
            assertNull(map.getHandler(removed), removed);
        }
        assertNotNull(map.getHandler("killCharacter"));
        assertNotNull(map.getHandler("suicide"));
        assertNotNull(map.getHandler("restore"));
        assertTrue(map.getCommandLine().getSubcommands().containsKey("restore"));
    }

    @Test
    void setStatsKeepsSingleSetPathAndDistinctLockCommands() {
        CommandLine cli = new SetStatsCommand().createCommandLine(null, null);
        for (String retained : new String[] {"lock", "unlock"}) {
            assertTrue(cli.getSubcommands().containsKey(retained), retained);
        }
        for (String removed : new String[] {"set", "freeze", "unfreeze"}) {
            assertFalse(cli.getSubcommands().containsKey(removed), removed);
        }
        assertNotNull(cli.getCommandSpec().positionalParameters());
    }

    @Test
    void sceneTagsKeepOneNamePerOperation() {
        CommandLine cli = new SetSceneTagCommand().createCommandLine(null, null);
        for (String retained : new String[] {"add", "remove", "reset", "unlockall", "list"}) {
            assertTrue(cli.getSubcommands().containsKey(retained), retained);
        }
        for (String removed : new String[] {"set", "del", "restore"}) {
            assertFalse(cli.getSubcommands().containsKey(removed), removed);
        }
    }
}
