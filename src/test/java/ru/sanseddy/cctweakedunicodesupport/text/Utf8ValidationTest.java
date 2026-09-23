package ru.sanseddy.cctweakedunicodesupport.text;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Utf8ValidationTest {

    @Test
    void testF5InvalidLeadByte() {
        byte[] bytes = new byte[]{(byte) 0xF5, (byte) 0x80, (byte) 0x80, (byte) 0x80};
        char[] out = new char[4];
        int read = Utf8.readCells(bytes, 0, bytes.length, out, 4);

        assertEquals(4, read);
        // 0xF5 is not a valid UTF-8 lead byte (exceeds U+10FFFF), read as 4 single bytes
        for (int i = 0; i < 4; i++) {
            assertEquals(CraftOsCharset.toCell(bytes[i] & 0xFF), out[i]);
        }
    }

    @Test
    void testFFInvalidByte() {
        byte[] bytes = new byte[]{(byte) 0xFF};
        char[] out = new char[1];
        int read = Utf8.readCells(bytes, 0, bytes.length, out, 1);

        assertEquals(1, read);
        assertEquals(CraftOsCharset.toCell(0xFF), out[0]);
    }

    @Test
    void testIncompleteSequence() {
        // F0 9F is a truncated 4-byte sequence
        byte[] bytes = new byte[]{(byte) 0xF0, (byte) 0x9F};
        char[] out = new char[2];
        int read = Utf8.readCells(bytes, 0, bytes.length, out, 2);

        assertEquals(2, read);
        assertEquals(CraftOsCharset.toCell(0xF0), out[0]);
        assertEquals(CraftOsCharset.toCell(0x9F), out[1]);
    }

    @Test
    void testInvalidContinuationByte() {
        // E2 28 A1: second byte 0x28 is not 0x80..0xBF
        byte[] bytes = new byte[]{(byte) 0xE2, 0x28, (byte) 0xA1};
        char[] out = new char[3];
        int read = Utf8.readCells(bytes, 0, bytes.length, out, 3);

        assertEquals(3, read);
        assertEquals(CraftOsCharset.toCell(0xE2), out[0]);
        assertEquals(CraftOsCharset.toCell(0x28), out[1]);
        assertEquals(CraftOsCharset.toCell(0xA1), out[2]);
    }

    @Test
    void testOverlongUtf8() {
        // Overlong encoding of ASCII 'A' (0x41): C1 81
        byte[] bytes = new byte[]{(byte) 0xC1, (byte) 0x81};
        char[] out = new char[2];
        int read = Utf8.readCells(bytes, 0, bytes.length, out, 2);

        assertEquals(2, read);
        assertEquals(CraftOsCharset.toCell(0xC1), out[0]);
        assertEquals(CraftOsCharset.toCell(0x81), out[1]);
    }

    @Test
    void testUtf8EncodedSurrogate() {
        // UTF-8 encoded high surrogate U+D800: ED A0 80
        byte[] bytes = new byte[]{(byte) 0xED, (byte) 0xA0, (byte) 0x80};
        char[] out = new char[3];
        int read = Utf8.readCells(bytes, 0, bytes.length, out, 3);

        assertEquals(3, read);
        // Surrogates must be rejected by wellFormed and not created as valid Unicode codepoints
        assertEquals(CraftOsCharset.toCell(0xED), out[0]);
        assertEquals(CraftOsCharset.toCell(0xA0), out[1]);
        assertEquals(CraftOsCharset.toCell(0x80), out[2]);
    }

    @Test
    void testMaximumValidCodepoint() {
        // U+10FFFF: F4 8F BF BF
        byte[] bytes = new byte[]{(byte) 0xF4, (byte) 0x8F, (byte) 0xBF, (byte) 0xBF};
        char[] out = new char[1];
        int read = Utf8.readCells(bytes, 0, bytes.length, out, 1);

        assertEquals(4, read);
        int codepoint = CraftOsCharset.cellToCodepoint(out[0]);
        assertEquals(0x10FFFF, codepoint);
    }

    @Test
    void testRegularEmoji() {
        // U+1F600 (😀): F0 9F 98 80
        byte[] bytes = new byte[]{(byte) 0xF0, (byte) 0x9F, (byte) 0x98, (byte) 0x80};
        char[] out = new char[1];
        int read = Utf8.readCells(bytes, 0, bytes.length, out, 1);

        assertEquals(4, read);
        int codepoint = CraftOsCharset.cellToCodepoint(out[0]);
        assertEquals(0x1F600, codepoint);
    }
}
