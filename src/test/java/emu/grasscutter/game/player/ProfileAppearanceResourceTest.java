package emu.grasscutter.game.player;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.google.gson.JsonParser;

import emu.grasscutter.ServerResourceFixture;
import emu.grasscutter.utils.FileUtils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.nio.file.Files;
import java.util.TreeSet;

@ExtendWith(ServerResourceFixture.class)
final class ProfileAppearanceResourceTest {
    @Test
    void resourceBackedListsContainAllCurrentPortraitAndFrameIds() throws Exception {
        for (String table : new String[] {"ProfileFrame", "ProfilePicture"}) {
            var path = FileUtils.getExcelPath(table + "ExcelConfigData.json");
            assumeTrue(Files.isRegularFile(path), "requires the extracted resource tables");
            var expected = new TreeSet<Integer>();
            try (var reader = Files.newBufferedReader(path)) {
                for (var row : JsonParser.parseReader(reader).getAsJsonArray()) {
                    expected.add(row.getAsJsonObject().get("id").getAsInt());
                }
            }
            var actual =
                    table.equals("ProfileFrame")
                            ? BeyondProfilePictureTable.allFrameIds()
                            : BeyondProfilePictureTable.all().stream().map(row -> row.id).toList();
            assertFalse(expected.isEmpty());
            assertEquals(expected, new TreeSet<>(actual));
            assertEquals(expected.size(), actual.size());
        }
        assertTrue(BeyondProfilePictureTable.allFrameIds().contains(100021));
    }
}
