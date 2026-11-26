package emu.grasscutter.net.packet;

import com.google.protobuf.Descriptors;
import emu.grasscutter.Grasscutter;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Applies runtime protobuf field-number overrides at packet boundaries. */
public final class ProtoFieldNumberOverrides {
    private static final String PROTO_PACKAGE = "emu.grasscutter.net.proto.";
    private static final int MAX_FIELD_NUMBER = 536870911;
    private static final int MAX_RECURSION_DEPTH = 64;

    private static final Map<String, Optional<Descriptors.FileDescriptor>> FILE_DESCRIPTOR_CACHE =
            new ConcurrentHashMap<>();
    private static volatile Map<FieldKey, FieldOverride> overrides = Map.of();

    private ProtoFieldNumberOverrides() {}

    public static Descriptors.Descriptor findMessageDescriptor(String messageName) {
        if (messageName == null || messageName.isBlank()) return null;

        int separator = messageName.indexOf(':');
        String sourceName = separator < 0 ? messageName : messageName.substring(0, separator);
        String typePath = separator < 0 ? messageName : messageName.substring(separator + 1);
        if (sourceName.endsWith(".proto")) {
            sourceName = sourceName.substring(0, sourceName.length() - ".proto".length());
        }
        String className = PROTO_PACKAGE + sourceName + "OuterClass";
        var fileDescriptor = FILE_DESCRIPTOR_CACHE.computeIfAbsent(className, ignored -> {
            try {
                Class<?> outerClass = Class.forName(className);
                var loadedDescriptor = (Descriptors.FileDescriptor)
                        outerClass.getMethod("getDescriptor").invoke(null);
                return Optional.of(loadedDescriptor);
            } catch (ReflectiveOperationException exception) {
                return Optional.empty();
            }
        }).orElse(null);
        if (fileDescriptor == null) return null;

        String[] path = typePath.split("\\.");
        var descriptor = fileDescriptor.findMessageTypeByName(path[0]);
        for (int i = 1; descriptor != null && i < path.length; i++) {
            descriptor = descriptor.findNestedTypeByName(path[i]);
        }
        return descriptor;
    }

    public static synchronized void setFieldNumber(
            Descriptors.Descriptor message, String fieldName, int newNumber) {
        var field = message.findFieldByName(fieldName);
        if (field == null) {
            throw new IllegalArgumentException("No field named " + fieldName + " in " + message.getName() + ".");
        }
        if (newNumber < 1 || newNumber > MAX_FIELD_NUMBER
                || (newNumber >= 19000 && newNumber <= 19999)) {
            throw new IllegalArgumentException("Field number must be in 1..536870911, excluding 19000..19999.");
        }
        for (var range : message.toProto().getReservedRangeList()) {
            if (newNumber >= range.getStart() && newNumber < range.getEnd()) {
                throw new IllegalArgumentException("Field number " + newNumber + " is reserved by "
                        + message.getName() + ".");
            }
        }
        if (field.getType() == Descriptors.FieldDescriptor.Type.GROUP) {
            throw new IllegalArgumentException("Runtime field-number overrides do not support protobuf groups.");
        }
        if (newNumber == field.getNumber()) {
            clearFieldNumber(message, fieldName);
            return;
        }

        var conflictingField = message.findFieldByNumber(newNumber);
        if (conflictingField != null) {
            throw new IllegalArgumentException("Field number " + newNumber + " is already used by "
                    + conflictingField.getName() + ".");
        }

        String messageName = message.getFullName();
        for (var entry : overrides.entrySet()) {
            if (entry.getKey().messageName.equals(messageName)
                    && entry.getValue().newNumber == newNumber
                    && !entry.getKey().fieldName.equals(fieldName)) {
                throw new IllegalArgumentException("Field number " + newNumber
                        + " is already assigned to " + entry.getKey().fieldName + ".");
            }
        }

        var updated = new HashMap<>(overrides);
        updated.put(new FieldKey(messageName, fieldName), new FieldOverride(field, newNumber));
        overrides = Map.copyOf(updated);
    }

    public static synchronized boolean clearFieldNumber(Descriptors.Descriptor message, String fieldName) {
        var key = new FieldKey(message.getFullName(), fieldName);
        if (!overrides.containsKey(key)) return false;
        var updated = new HashMap<>(overrides);
        updated.remove(key);
        overrides = Map.copyOf(updated);
        return true;
    }

    public static List<String> listFieldNumbers() {
        return overrides.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getValue().field.getContainingType().getFullName() + "."
                        + entry.getValue().field.getName() + ": "
                        + entry.getValue().field.getNumber() + " -> " + entry.getValue().newNumber)
                .toList();
    }

    public static byte[] rewriteOutgoing(int opcode, byte[] payload) {
        return rewrite(payload, rootDescriptor(opcode, null), false);
    }

    public static byte[] rewriteIncoming(int opcode, PacketHandler handler, byte[] payload) {
        return rewrite(payload, rootDescriptor(opcode, handler), true);
    }

    /** Rewrites on-wire fields back to generated field numbers for schema-based logging. */
    public static byte[] toGeneratedFieldNumbers(int opcode, PacketHandler handler, byte[] payload) {
        return rewrite(payload, rootDescriptor(opcode, handler), true);
    }

    private static Descriptors.Descriptor rootDescriptor(int opcode, PacketHandler handler) {
        String messageName = PacketOpcodesUtils.getOpcodeName(opcode);
        if ((messageName == null || messageName.equals("UNKNOWN")) && handler != null) {
            messageName = handler.getClass().getSimpleName().replaceFirst("^Handler", "");
        }
        return findMessageDescriptor(messageName);
    }

    private static byte[] rewrite(byte[] payload, Descriptors.Descriptor descriptor, boolean incoming) {
        Map<FieldKey, FieldOverride> currentOverrides = overrides;
        if (payload == null || payload.length == 0 || descriptor == null || currentOverrides.isEmpty()
                || !containsOverride(descriptor, currentOverrides, new HashSet<>())) {
            return payload;
        }

        var indexes = OverrideIndexes.create(currentOverrides);
        try {
            return rewriteMessage(payload, descriptor, incoming, indexes, 0).data;
        } catch (InvalidWireFormatException exception) {
            Grasscutter.getLogger().warn("Could not apply protobuf field-number overrides to {}: {}",
                    descriptor.getName(), exception.getMessage());
            return payload;
        }
    }

    private static boolean containsOverride(Descriptors.Descriptor descriptor,
            Map<FieldKey, FieldOverride> currentOverrides, Set<String> visited) {
        String messageName = descriptor.getFullName();
        if (currentOverrides.keySet().stream().anyMatch(key -> key.messageName.equals(messageName))) {
            return true;
        }
        if (!visited.add(messageName)) return false;

        for (var field : descriptor.getFields()) {
            if (field.getJavaType() == Descriptors.FieldDescriptor.JavaType.MESSAGE
                    && containsOverride(field.getMessageType(), currentOverrides, visited)) {
                return true;
            }
        }
        return false;
    }

    private static RewriteResult rewriteMessage(byte[] data, Descriptors.Descriptor descriptor,
            boolean incoming, OverrideIndexes indexes, int depth) {
        if (depth >= MAX_RECURSION_DEPTH) return new RewriteResult(data, false);

        var output = new ByteArrayOutputStream(data.length);
        int position = 0;
        boolean changed = false;
        while (position < data.length) {
            int fieldStart = position;
            Varint key = readVarint(data, position);
            position = key.end;
            long wireKey = key.value;
            int fieldNumber = (int) (wireKey >>> 3);
            int wireType = (int) (wireKey & 7);
            if (fieldNumber < 1 || fieldNumber > MAX_FIELD_NUMBER) {
                throw new InvalidWireFormatException("invalid field number " + fieldNumber);
            }

            var messageOverrides = indexes.byMessage.get(descriptor.getFullName());
            Descriptors.FieldDescriptor field = null;
            int rewrittenFieldNumber = fieldNumber;
            if (incoming && messageOverrides != null) {
                var fieldOverride = messageOverrides.byNewNumber.get(fieldNumber);
                if (fieldOverride != null) {
                    field = fieldOverride.field;
                    rewrittenFieldNumber = field.getNumber();
                }
            }
            if (field == null) {
                field = descriptor.findFieldByNumber(fieldNumber);
            }
            if (!incoming && field != null && messageOverrides != null) {
                var fieldOverride = messageOverrides.byOldNumber.get(field.getNumber());
                if (fieldOverride != null) rewrittenFieldNumber = fieldOverride.newNumber;
            }

            int valueStart = position;
            RewriteResult nested = null;
            switch (wireType) {
                case 0 -> position = readVarint(data, position).end;
                case 1 -> position = checkedEnd(position, 8, data.length);
                case 2 -> {
                    Varint length = readVarint(data, position);
                    int nestedStart = length.end;
                    if (length.value < 0 || length.value > data.length - nestedStart) {
                        throw new InvalidWireFormatException("invalid length-delimited field size");
                    }
                    int nestedEnd = nestedStart + (int) length.value;
                    if (field != null
                            && field.getJavaType() == Descriptors.FieldDescriptor.JavaType.MESSAGE) {
                        byte[] nestedData = new byte[(int) length.value];
                        System.arraycopy(data, nestedStart, nestedData, 0, nestedData.length);
                        try {
                            nested = rewriteMessage(nestedData, field.getMessageType(), incoming,
                                    indexes, depth + 1);
                        } catch (InvalidWireFormatException ignored) {
                            nested = null;
                        }
                    }

                    if (rewrittenFieldNumber != fieldNumber) {
                        writeVarint(output, ((long) rewrittenFieldNumber << 3) | wireType);
                        changed = true;
                    } else {
                        output.write(data, fieldStart, valueStart - fieldStart);
                    }
                    if (nested != null && nested.changed) {
                        writeVarint(output, nested.data.length);
                        output.write(nested.data, 0, nested.data.length);
                        changed = true;
                    } else {
                        output.write(data, valueStart, nestedEnd - valueStart);
                    }
                    position = nestedEnd;
                    continue;
                }
                case 3 -> position = skipGroup(data, position, fieldNumber, 0);
                case 4 -> throw new InvalidWireFormatException("unexpected end-group tag");
                case 5 -> position = checkedEnd(position, 4, data.length);
                default -> throw new InvalidWireFormatException("invalid wire type " + wireType);
            }

            if (rewrittenFieldNumber != fieldNumber) {
                writeVarint(output, ((long) rewrittenFieldNumber << 3) | wireType);
                changed = true;
            } else {
                output.write(data, fieldStart, valueStart - fieldStart);
            }
            output.write(data, valueStart, position - valueStart);
        }
        return changed
                ? new RewriteResult(output.toByteArray(), true)
                : new RewriteResult(data, false);
    }

    private static int skipGroup(byte[] data, int position, int groupFieldNumber, int depth) {
        if (depth >= MAX_RECURSION_DEPTH) {
            throw new InvalidWireFormatException("protobuf group nesting is too deep");
        }
        while (position < data.length) {
            Varint key = readVarint(data, position);
            position = key.end;
            int fieldNumber = (int) (key.value >>> 3);
            int wireType = (int) (key.value & 7);
            if (wireType == 4) {
                if (fieldNumber != groupFieldNumber) {
                    throw new InvalidWireFormatException("mismatched end-group tag");
                }
                return position;
            }
            switch (wireType) {
                case 0 -> position = readVarint(data, position).end;
                case 1 -> position = checkedEnd(position, 8, data.length);
                case 2 -> {
                    Varint length = readVarint(data, position);
                    position = length.end;
                    if (length.value < 0 || length.value > data.length - position) {
                        throw new InvalidWireFormatException("invalid group field size");
                    }
                    position += (int) length.value;
                }
                case 3 -> position = skipGroup(data, position, fieldNumber, depth + 1);
                case 5 -> position = checkedEnd(position, 4, data.length);
                default -> throw new InvalidWireFormatException("invalid wire type " + wireType);
            }
        }
        throw new InvalidWireFormatException("unterminated protobuf group");
    }

    private static int checkedEnd(int position, int size, int limit) {
        if (size > limit - position) {
            throw new InvalidWireFormatException("truncated fixed-width field");
        }
        return position + size;
    }

    private static Varint readVarint(byte[] data, int position) {
        long value = 0;
        int shift = 0;
        for (int count = 0; count < 10 && position < data.length; count++) {
            int next = data[position++] & 0xff;
            if (count == 9 && next > 1) {
                throw new InvalidWireFormatException("varint overflow");
            }
            value |= (long) (next & 0x7f) << shift;
            if ((next & 0x80) == 0) return new Varint(value, position);
            shift += 7;
        }
        throw new InvalidWireFormatException("truncated or overlong varint");
    }

    private static void writeVarint(ByteArrayOutputStream output, long value) {
        while ((value & ~0x7fL) != 0) {
            output.write((int) (value & 0x7f) | 0x80);
            value >>>= 7;
        }
        output.write((int) value);
    }

    private record FieldKey(String messageName, String fieldName) implements Comparable<FieldKey> {
        @Override
        public int compareTo(FieldKey other) {
            int messageOrder = messageName.compareTo(other.messageName);
            return messageOrder != 0 ? messageOrder : fieldName.compareTo(other.fieldName);
        }
    }

    private record FieldOverride(Descriptors.FieldDescriptor field, int newNumber) {}

    private record OverrideIndexes(Map<String, MessageIndexes> byMessage) {
        private static OverrideIndexes create(Map<FieldKey, FieldOverride> overrides) {
            var byMessage = new HashMap<String, MessageIndexes>();
            for (var entry : overrides.entrySet()) {
                var indexes = byMessage.computeIfAbsent(entry.getKey().messageName,
                        ignored -> new MessageIndexes());
                indexes.byOldNumber.put(entry.getValue().field.getNumber(), entry.getValue());
                indexes.byNewNumber.put(entry.getValue().newNumber, entry.getValue());
            }
            return new OverrideIndexes(byMessage);
        }
    }

    private static final class MessageIndexes {
        private final Map<Integer, FieldOverride> byOldNumber = new HashMap<>();
        private final Map<Integer, FieldOverride> byNewNumber = new HashMap<>();
    }

    private record RewriteResult(byte[] data, boolean changed) {}

    private record Varint(long value, int end) {}

    private static final class InvalidWireFormatException extends RuntimeException {
        private InvalidWireFormatException(String message) {
            super(message);
        }
    }
}
