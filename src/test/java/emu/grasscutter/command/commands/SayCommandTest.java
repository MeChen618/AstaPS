package emu.grasscutter.command.commands;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.command.Command;
import java.util.List;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

public final class SayCommandTest {
    @Test
    void sayBroadcastsWithoutAnExplicitTrailingSelector() {
        var message = SayCommand.parseMessageArguments(List.of("hello", "world"));
        assertEquals("hello world", message.message());
        assertNull(message.recipient());

        var mention = SayCommand.parseMessageArguments(List.of("hello", "alice@example.com"));
        assertEquals("hello alice@example.com", mention.message());
        assertNull(mention.recipient());

        var internalSelector = SayCommand.parseMessageArguments(
                List.of("hello", "@10001", "world"));
        assertEquals("hello @10001 world", internalSelector.message());
        assertNull(internalSelector.recipient());
    }

    @Test
    void onlyTheFinalWordCanTargetAPlayer() {
        var byUid = SayCommand.parseMessageArguments(List.of("hello", "world", "@10001"));
        assertEquals("hello world", byUid.message());
        assertEquals(10001, byUid.recipient().uid());

        var byUsername = SayCommand.parseMessageArguments(List.of("hello", "alice@"));
        assertEquals("hello", byUsername.message());
        assertEquals("alice", byUsername.recipient().username());

        var both = SayCommand.parseMessageArguments(List.of("hello", "alice@10001"));
        assertEquals("hello", both.message());
        assertEquals(10001, both.recipient().uid());
        assertEquals("alice", both.recipient().username());
    }

    @Test
    void emptyMessagesAndMalformedSelectorsAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> SayCommand.parseMessageArguments(List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> SayCommand.parseMessageArguments(List.of("@10001")));
        for (String invalid : List.of("@0", "@invalid", "alice@0", "alice@999999999999999")) {
            assertThrows(IllegalArgumentException.class,
                    () -> SayCommand.parseMessageArguments(List.of("hi", invalid)), invalid);
        }
    }

    @Test
    void picocliDocumentsOptionalSelectorWithoutConsumingInlineTarget() {
        var command = new SayCommand();
        var cli = command.createCommandLine(null, null);
        assertTrue(cli.getUsageMessage().contains("say <message...> [selector]"));
        assertDoesNotThrow(() -> cli.parseArgs("hello", "world"));
        assertDoesNotThrow(() -> cli.parseArgs("hello", "@10001"));
        assertDoesNotThrow(() -> cli.parseArgs("hello", "alice@10001"));
        assertThrows(CommandLine.ParameterException.class, () -> cli.parseArgs());

        var annotation = SayCommand.class.getAnnotation(Command.class);
        assertFalse(annotation.inlineTarget());
        assertEquals(Command.TargetRequirement.NONE, annotation.targetRequirement());
        assertEquals("server.sendmessage", annotation.permission());
        assertTrue(annotation.permissionTargeted().isEmpty());
    }
}
