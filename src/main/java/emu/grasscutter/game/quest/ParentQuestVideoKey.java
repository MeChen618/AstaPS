package emu.grasscutter.game.quest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import emu.grasscutter.net.proto.ParentQuestOuterClass.ParentQuest;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** The two opaque strings in the 7.2 client cache, not QuestEncryptionKeys.json values. */
public record ParentQuestVideoKey(String key1, String key2) {
    public static final String FILE_NAME = "ParentQuestVideoKeys.json";

    public ParentQuestVideoKey {
        Objects.requireNonNull(key1);
        Objects.requireNonNull(key2);
        if (key1.isBlank() && key2.isBlank()) {
            throw new IllegalArgumentException("At least one video-key component is required");
        }
    }

    public void applyTo(ParentQuest.Builder parent) {
        parent.setVideoKey1(key1).setVideoKey2(key2);
    }

    /**
     * Reads an explicit parent-ID mapping. Strings stay strings, including unsigned 64-bit decimal
     * values. Do not guess a parent ID from a video name or a version-only row.
     */
    public static Map<Integer, ParentQuestVideoKey> load(Path path) throws IOException {
        JsonElement root;
        try (var reader = Files.newBufferedReader(path)) {
            root = JsonParser.parseReader(reader);
        }
        if (!root.isJsonArray()) {
            throw new IllegalArgumentException("Expected a video-key array");
        }

        var result = new HashMap<Integer, ParentQuestVideoKey>();
        for (var element : root.getAsJsonArray()) {
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException("Expected a video-key object");
            }
            var row = element.getAsJsonObject();
            var id = readId(row.get("parentQuestId"));
            var key = new ParentQuestVideoKey(readKey(row, "key1"), readKey(row, "key2"));
            var previous = result.putIfAbsent(id, key);
            if (previous != null && !previous.equals(key)) {
                throw new IllegalArgumentException("Conflicting video-key records for a parent quest");
            }
        }
        return Map.copyOf(result);
    }

    private static int readId(JsonElement value) {
        if (value == null || !value.isJsonPrimitive()) {
            throw new IllegalArgumentException("Missing parent quest ID");
        }
        var primitive = value.getAsJsonPrimitive();
        var text = primitive.getAsString();
        if ((!primitive.isNumber() && !primitive.isString()) || !text.matches("[0-9]+")) {
            throw new IllegalArgumentException("Invalid parent quest ID");
        }
        // Server quest IDs are positive signed ints; never truncate a JSON fraction or overflow.
        var id = new BigInteger(text);
        if (id.signum() <= 0 || id.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0) {
            throw new IllegalArgumentException("Parent quest ID is out of range");
        }
        return id.intValueExact();
    }

    private static String readKey(JsonObject row, String name) {
        var value = row.get(name);
        if (value == null) return "";
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Video-key components must be JSON strings");
        }
        return value.getAsString();
    }
}
