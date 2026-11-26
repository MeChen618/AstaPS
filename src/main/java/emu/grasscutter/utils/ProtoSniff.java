package emu.grasscutter.utils;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Describes a protobuf payload without knowing its schema.
 *
 * <p>Every version re-obfuscates message names, field names, field NUMBERS and CmdIds, so after a
 * game update the generated classes read the wrong fields and a packet the server cannot name is
 * also a packet it cannot decode. Walking the wire format needs none of that: field numbers and
 * wire types are in the bytes themselves, and the values give the rest away - a version string, a
 * 344-character base64 RSA blob, a 64-hex token. That is how the 6.7 handshake was mapped, and it
 * is the only mapping method that needs nothing but a client willing to connect.
 */
public final class ProtoSniff {

    private ProtoSniff() {}

    /** Describes a payload and maxDepth level of nested length-delimited protobuf messages. */
    public static String describe(byte[] data) {
        return describe(data, 5);
    }

    /**
     * Describes a payload up to {@code maxDepth} message levels deep. The top-level payload is
     * depth 1.
     */
    public static String describe(byte[] data, int maxDepth) {
        if (maxDepth < 1) {
            throw new IllegalArgumentException("maxDepth must be at least 1");
        }
        return describe(data, 1, maxDepth);
    }

    private static String describe(byte[] data, int depth, int maxDepth) {
        if (data == null || data.length == 0) return "(empty)";
        var sb = new StringBuilder();
        int i = 0;
        int printed = 0;
        while (i < data.length && printed < 64) {
            long key;
            try {
                long[] kv = varint(data, i);
                key = kv[0];
                i = (int) kv[1];
            } catch (Exception e) {
                sb.append(" <undecodable at ").append(i).append(">");
                break;
            }
            int field = (int) (key >>> 3);
            int wire = (int) (key & 7);
            if (field == 0) {
                sb.append(" <field 0 at ").append(i).append(">");
                break;
            }
            if (sb.length() > 0) sb.append(' ');
            sb.append('#').append(field).append('=');
            try {
                switch (wire) {
                    case 0 -> {
                        long[] v = varint(data, i);
                        i = (int) v[1];
                        sb.append("varint(").append(v[0]).append(')');
                    }
                    case 1 -> {
                        sb.append("fixed64");
                        i += 8;
                    }
                    case 2 -> {
                        long[] len = varint(data, i);
                        i = (int) len[1];
                        if (len[0] < 0 || len[0] > data.length - i) {
                            sb.append("<bad len ").append(len[0]).append('>');
                            i = data.length;
                            break;
                        }
                        int n = (int) len[0];
                        sb.append(renderBytes(data, i, n));
                        if (depth < maxDepth && n > 0 && !isPrintable(data, i, n)) {
                            byte[] nested = Arrays.copyOfRange(data, i, i + n);
                            if (isValidMessage(nested)) {
                                sb.append(" { ").append(describe(nested, depth + 1, maxDepth)).append(" }");
                            }
                        }
                        i += n;
                    }
                    case 5 -> {
                        sb.append("fixed32");
                        i += 4;
                    }
                    default -> {
                        sb.append("<wire ").append(wire).append('>');
                        i = data.length;
                    }
                }
            } catch (Exception e) {
                sb.append("<truncated>");
                break;
            }
            printed++;
        }
        if (i < data.length) sb.append(" ...+").append(data.length - i).append('B');
        return sb.toString();
    }

    /** Length-delimited fields are the informative ones, so say what they look like. */
    private static String renderBytes(byte[] data, int off, int n) {
        var s = new String(data, off, n, StandardCharsets.UTF_8);
        if (isPrintable(s) && n > 0) {
            if (n > 48) return "\"" + s.substring(0, 24) + "…\"(" + n + " chars)";
            return "\"" + s + "\"";
        }
        return "<" + n + " bytes>";
    }

    private static boolean isPrintable(byte[] data, int off, int n) {
        return isPrintable(new String(data, off, n, StandardCharsets.UTF_8));
    }

    private static boolean isPrintable(String s) {
        for (int k = 0; k < s.length(); k++) {
            char c = s.charAt(k);
            if (c < 0x20 || c > 0x7e) return false;
        }
        return true;
    }

    private static boolean isValidMessage(byte[] data) {
        int i = 0;
        int fields = 0;
        while (i < data.length) {
            long[] kv;
            try {
                kv = varint(data, i);
            } catch (RuntimeException exception) {
                return false;
            }
            long key = kv[0];
            i = (int) kv[1];
            long field = key >>> 3;
            int wire = (int) (key & 7);
            if (field == 0 || field > 536870911) return false;
            try {
                switch (wire) {
                    case 0 -> i = (int) varint(data, i)[1];
                    case 1 -> i += 8;
                    case 2 -> {
                        long[] length = varint(data, i);
                        i = (int) length[1];
                        if (length[0] < 0 || length[0] > data.length - i) return false;
                        i += (int) length[0];
                    }
                    case 5 -> i += 4;
                    default -> {
                        return false;
                    }
                }
            } catch (RuntimeException exception) {
                return false;
            }
            if (i > data.length) return false;
            fields++;
        }
        return fields > 0;
    }

    private static long[] varint(byte[] data, int pos) {
        long value = 0;
        int shift = 0;
        while (pos < data.length) {
            byte b = data[pos++];
            value |= (long) (b & 0x7f) << shift;
            if ((b & 0x80) == 0) return new long[] {value, pos};
            shift += 7;
            if (shift > 63) throw new IllegalStateException("varint too long");
        }
        throw new IllegalStateException("varint ran off the end");
    }
}
