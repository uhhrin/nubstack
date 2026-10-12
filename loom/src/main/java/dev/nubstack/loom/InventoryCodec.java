package dev.nubstack.loom;

import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;

/**
 * Loom inventory blob framing. V1 is a 1-byte header plus Paper {@code serializeItemsAsBytes} payload.
 */
public final class InventoryCodec {

    public static final byte FORMAT_V1 = 1;

    public enum Format {
        V1,
        LEGACY
    }

    public record Decoded(Format format, byte[] payload) {
    }

    private InventoryCodec() {
    }

    public static byte[] wrapV1(byte[] paperBytes) {
        Objects.requireNonNull(paperBytes, "paperBytes");
        byte[] out = new byte[paperBytes.length + 1];
        out[0] = FORMAT_V1;
        System.arraycopy(paperBytes, 0, out, 1, paperBytes.length);
        return out;
    }

    public static Decoded unwrapPayload(byte[] data) {
        Objects.requireNonNull(data, "data");
        if (data.length > 0 && data[0] == FORMAT_V1) {
            return new Decoded(Format.V1, Arrays.copyOfRange(data, 1, data.length));
        }
        return new Decoded(Format.LEGACY, data);
    }

    public static String encodeV1Base64(byte[] paperBytes) {
        return Base64.getEncoder().encodeToString(wrapV1(paperBytes));
    }

    public static Decoded decodeBase64(String blob) {
        if (blob == null || blob.isBlank()) {
            throw new IllegalArgumentException("inventory blob must not be blank");
        }
        return unwrapPayload(Base64.getDecoder().decode(blob.trim()));
    }
}
