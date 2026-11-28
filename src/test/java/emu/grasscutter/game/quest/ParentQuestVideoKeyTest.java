package emu.grasscutter.game.quest;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

final class ParentQuestVideoKeyTest {
    @TempDir Path directory;

    @Test
    void preservesOpaqueStringsWithoutNumericRoundingOrKeyOrderChanges() throws Exception {
        var path = write("""
                [{"parentQuestId":"5006","key1":"00112233445566778899aabbccddeeff",
                  "key2":"18446744073709551615"},
                 {"parentQuestId":6018,"key2":"9007199254740993"}]
                """);
        var keys = ParentQuestVideoKey.load(path);
        assertEquals(2, keys.size());
        assertEquals("00112233445566778899aabbccddeeff", keys.get(5006).key1());
        assertEquals("18446744073709551615", keys.get(5006).key2());
        assertEquals("", keys.get(6018).key1());
        assertEquals("9007199254740993", keys.get(6018).key2());
    }

    @Test
    void acceptsIdenticalDuplicatesButRejectsConflictingMappingsAsAWhole() throws Exception {
        var path = write("""
                [{"parentQuestId":5006,"key2":"123"},
                 {"parentQuestId":5006,"key2":"123"}]
                """);
        assertEquals(1, ParentQuestVideoKey.load(path).size());
        path = write("""
                [{"parentQuestId":5006,"key2":"123"},
                 {"parentQuestId":5006,"key2":"456"}]
                """);
        var conflict = path;
        assertThrows(IllegalArgumentException.class, () -> ParentQuestVideoKey.load(conflict));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{}", "null", "[null]", "[5]",
        "[{\"parentQuestId\":0,\"key2\":\"1\"}]",
        "[{\"parentQuestId\":-1,\"key2\":\"1\"}]",
        "[{\"parentQuestId\":5006.5,\"key2\":\"1\"}]",
        "[{\"parentQuestId\":4294967297,\"key2\":\"1\"}]",
        "[{\"parentQuestId\":true,\"key2\":\"1\"}]",
        "[{\"key2\":\"1\"}]",
        "[{\"parentQuestId\":5006}]",
        "[{\"parentQuestId\":5006,\"key1\":\" \",\"key2\":\"\"}]",
        "[{\"parentQuestId\":5006,\"key2\":18446744073709551615}]",
        "[{\"parentQuestId\":5006,\"key2\":true}]",
        "[{\"parentQuestId\":5006,\"key2\":null}]",
        "[{\"parentQuestId\":5006,\"key2\":{}}]",
        "[{\"version\":\"2.0\",\"videoKey\":123,\"videos\":[\"test\"]}]"
    })
    void rejectsInvalidOrUnmappedRecordsWithoutInventingKeys(String json) throws Exception {
        var path = write(json);
        assertThrows(IllegalArgumentException.class, () -> ParentQuestVideoKey.load(path));
    }

    private Path write(String json) throws Exception {
        return Files.writeString(directory.resolve(ParentQuestVideoKey.FILE_NAME), json);
    }
}
