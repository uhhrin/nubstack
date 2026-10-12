package dev.nubstack.loom;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class InventoryCodecTest {

    @Test
    void wrapV1_prefixesFormatByte() {
        byte[] payload = new byte[] {10, 20, 30};
        byte[] wrapped = InventoryCodec.wrapV1(payload);
        assertEquals(InventoryCodec.FORMAT_V1, wrapped[0]);
        assertArrayEquals(payload, InventoryCodec.unwrapPayload(wrapped).payload());
        assertEquals(InventoryCodec.Format.V1, InventoryCodec.unwrapPayload(wrapped).format());
    }

    @Test
    void unwrap_treatsNonV1AsLegacy() {
        byte[] legacy = new byte[] {0x00, 0x01, 0x02};
        InventoryCodec.Decoded decoded = InventoryCodec.unwrapPayload(legacy);
        assertEquals(InventoryCodec.Format.LEGACY, decoded.format());
        assertArrayEquals(legacy, decoded.payload());
    }

    @Test
    void encodeDecodeBase64_roundTripsHeader() {
        byte[] paperBytes = new byte[] {7, 8, 9};
        String blob = InventoryCodec.encodeV1Base64(paperBytes);
        byte[] raw = Base64.getDecoder().decode(blob);
        assertEquals(InventoryCodec.FORMAT_V1, raw[0]);
        InventoryCodec.Decoded decoded = InventoryCodec.decodeBase64(blob);
        assertEquals(InventoryCodec.Format.V1, decoded.format());
        assertArrayEquals(paperBytes, decoded.payload());
    }

    @Test
    void decodeBase64_rejectsBlank() {
        assertThrows(IllegalArgumentException.class, () -> InventoryCodec.decodeBase64(" "));
    }
}
