package emu.grasscutter.command.commands;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static emu.grasscutter.config.Configuration.GAME_INFO;
import static emu.grasscutter.config.Configuration.SERVER;

import emu.grasscutter.command.Command;
import emu.grasscutter.Grasscutter.ServerDebugMode;
import java.util.Set;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

final class ProtoCommandTest {
    @Test
    void exposesRuntimePacketLoggingControls() {
        var command = new ProtoCommand().createCommandLine(null, null);

        assertEquals(Set.of("mode", "payload", "raw", "loops", "whitelist", "blacklist", "info", "field"),
                command.getSubcommands().keySet());
        assertEquals("grasscutter.command.debug", ProtoCommand.class.getAnnotation(Command.class).permission());
        assertDoesNotThrow(() -> command.parseArgs());
        assertDoesNotThrow(() -> command.parseArgs("mode", "whitelist"));
        assertDoesNotThrow(() -> command.parseArgs("mode", "mising"));
        assertDoesNotThrow(() -> command.parseArgs("payload", "on"));
        assertDoesNotThrow(() -> command.parseArgs("raw", "on"));
        assertDoesNotThrow(() -> command.parseArgs("field", "ForgeQueueData", "unfinish_count", "5"));
        assertDoesNotThrow(() -> command.parseArgs("field", "ForgeQueueData", "unfinish_count", "reset"));
        assertDoesNotThrow(() -> command.parseArgs(
                "field", "ShopCardProduct.proto:ShopCardProduct.ResinCard", "base_item_list", "5"));
        assertDoesNotThrow(() -> command.parseArgs("field", "list"));
        assertDoesNotThrow(() -> command.parseArgs("loops", "off"));
        assertDoesNotThrow(() -> command.parseArgs("whitelist", "add", "123"));
        assertDoesNotThrow(() -> command.parseArgs("whitelist", "add", "123", "124"));
        assertDoesNotThrow(() -> command.parseArgs("blacklist", "remove", "123"));
        assertDoesNotThrow(() -> command.parseArgs("blacklist", "remove", "123", "124"));
        assertDoesNotThrow(() -> command.parseArgs("whitelist", "clear"));
        assertDoesNotThrow(() -> command.parseArgs("blacklist", "clear"));
        assertDoesNotThrow(() -> command.parseArgs("info", "2060"));
    }

    @Test
    void fieldCommandAppliesAndResetsRuntimeOverrides() {
        var descriptor = emu.grasscutter.net.packet.ProtoFieldNumberOverrides
                .findMessageDescriptor("ForgeQueueData");
        emu.grasscutter.net.packet.ProtoFieldNumberOverrides.clearFieldNumber(
                descriptor, "unfinish_count");
        try {
            var command = new ProtoCommand().createCommandLine(null, null);
            command.execute("field", "ForgeQueueData", "unfinish_count", "5");
            assertTrue(emu.grasscutter.net.packet.ProtoFieldNumberOverrides.listFieldNumbers()
                    .contains("ForgeQueueData.unfinish_count: 15 -> 5"));

            command.execute("field", "ForgeQueueData", "unfinish_count", "reset");
            assertTrue(emu.grasscutter.net.packet.ProtoFieldNumberOverrides.listFieldNumbers().isEmpty());
        } finally {
            emu.grasscutter.net.packet.ProtoFieldNumberOverrides.clearFieldNumber(
                    descriptor, "unfinish_count");
        }
    }

    @Test
    void filtersRejectNonNumericOpcodes() {
        var command = new ProtoCommand().createCommandLine(null, null);

        assertThrows(CommandLine.ParameterException.class, () -> command.parseArgs("blacklist", "remove", "abc"));
    }

    @Test
    void modeCommandSupportsUnhandledMode() {
        var previousMode = GAME_INFO.logPackets;
        try {
            var command = new ProtoCommand().createCommandLine(null, null);
            command.execute("mode", "missing");
            assertEquals(ServerDebugMode.MISSING, GAME_INFO.logPackets);
        } finally {
            GAME_INFO.logPackets = previousMode;
        }
    }

    @Test
    void commandsUpdateTheLivePacketSettings() {
        var previousMode = GAME_INFO.logPackets;
        var previousPayload = GAME_INFO.isShowPacketPayload;
        var previousRawFields = GAME_INFO.isShowPacketRawFields;
        var previousLoops = GAME_INFO.isShowLoopPackets;
        var previousWhitelist = SERVER.debugWhitelist;
        var previousBlacklist = SERVER.debugBlacklist;

        try {
            SERVER.debugWhitelist = Set.of();
            SERVER.debugBlacklist = Set.of();
            var command = new ProtoCommand().createCommandLine(null, null);
            command.execute("mode", "whitelist");
            command.execute("payload", "on");
            command.execute("raw", "on");
            command.execute("loops", "on");
            command.execute("whitelist", "add", "123", "124");
            command.execute("blacklist", "add", "456", "789");

            assertEquals(Set.of(123, 124), SERVER.debugWhitelist);
            assertEquals(Set.of(456, 789), SERVER.debugBlacklist);

            command.execute("whitelist", "remove", "123", "124");
            assertEquals(Set.of(), SERVER.debugWhitelist);
            command.execute("whitelist", "add", "123", "124");
            command.execute("whitelist", "clear");
            command.execute("blacklist", "clear");

            assertEquals(ServerDebugMode.WHITELIST, GAME_INFO.logPackets);
            assertTrue(GAME_INFO.isShowPacketPayload);
            assertTrue(GAME_INFO.isShowPacketRawFields);
            assertTrue(GAME_INFO.isShowLoopPackets);
            assertEquals(Set.of(), SERVER.debugWhitelist);
            assertEquals(Set.of(), SERVER.debugBlacklist);
        } finally {
            GAME_INFO.logPackets = previousMode;
            GAME_INFO.isShowPacketPayload = previousPayload;
            GAME_INFO.isShowPacketRawFields = previousRawFields;
            GAME_INFO.isShowLoopPackets = previousLoops;
            SERVER.debugWhitelist = previousWhitelist;
            SERVER.debugBlacklist = previousBlacklist;
        }
    }

    @Test
    void protoInfoFormatsKnownOpcodeAndMessageNames() {
        String byOpcode = ProtoCommand.formatProtoInfo("2060");
        assertTrue(byOpcode.contains("Opcode: 2060"));
        assertTrue(byOpcode.contains("Name: EvtBulletDeactiveNotify"));
        assertTrue(byOpcode.contains("message EvtBulletDeactiveNotify {\n"
                + "  Vector disappear_pos = 1;\n"
                + "  uint32 entity_id = 5;\n"
                + "  ForwardType forward_type = 9;\n}"));

        String byMessageName = ProtoCommand.formatProtoInfo("ForgeQueueData");
        assertTrue(byMessageName.contains("Opcode: UNKNOWN"));
        assertTrue(byMessageName.contains("Name: ForgeQueueData"));
        assertTrue(byMessageName.contains("message ForgeQueueData {\n"
                + "  uint32 next_finish_timestamp = 14;\n"
                + "  uint32 finish_count = 3;\n"
                + "  uint32 unfinish_count = 15;"));
    }

    @Test
    void protoInfoReportsUnknownOpcodeAndName() {
        String unknownOpcode = ProtoCommand.formatProtoInfo("987654");
        assertTrue(unknownOpcode.contains("Opcode: 987654"));
        assertTrue(unknownOpcode.contains("Name: UNKNOWN"));
        assertTrue(unknownOpcode.contains("no protobuf definition found for UNKNOWN"));

        String unknownName = ProtoCommand.formatProtoInfo("MissingProtoMessage");
        assertTrue(unknownName.contains("Opcode: UNKNOWN"));
        assertTrue(unknownName.contains("Name: MissingProtoMessage"));
        assertTrue(unknownName.contains("no protobuf definition found for MissingProtoMessage"));
    }
}
