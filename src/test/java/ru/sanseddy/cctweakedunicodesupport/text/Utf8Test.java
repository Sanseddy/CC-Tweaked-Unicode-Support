package ru.sanseddy.cctweakedunicodesupport.text;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Utf8Test {
    @Test
    void decodesUtf8AsCharacters() {
        assertEquals("Привет", Utf8.decode(byteString("Привет")));
        assertEquals(51, Utf8.decode(byteString("Я".repeat(51))).length());
        var astral = new String(Character.toChars(0x1F642));
        var decodedAstral = Utf8.decode(byteString(astral));
        assertEquals(1, decodedAstral.length());
        assertEquals(0x1F642, CraftOsCharset.cellToCodepoint(decodedAstral.charAt(0)));
    }

    @Test
    void countsDisplayCharactersInsteadOfUtf8Bytes() {
        var fullRow = "Я".repeat(51).getBytes(StandardCharsets.UTF_8);
        assertEquals(51, Utf8.characterCount(ByteBuffer.wrap(fullRow)));
        assertEquals(3, Utf8.characterCount(ByteBuffer.wrap("A🙂Я".getBytes(StandardCharsets.UTF_8))));
        assertEquals(2, Utf8.characterCount(ByteBuffer.wrap(new byte[]{ (byte) 0xC2, (byte) ' ' })));
    }

    @Test
    void wideDecodeKeepsOneCellPerLuaByte() {
        var raw = byteString("Я");
        var decoded = Utf8.decodeWide(raw);

        assertEquals(2, decoded.length());
        assertEquals('Я', decoded.charAt(0));
        assertEquals(CraftOsCharset.CONTINUATION, decoded.charAt(1));

        var astral = new String(Character.toChars(0x1F642));
        var wideAstral = Utf8.decodeWide(byteString(astral));
        assertEquals(4, wideAstral.length());
        assertEquals(CraftOsCharset.toAstralCell(0x1F642), wideAstral.charAt(0));
        assertEquals(CraftOsCharset.CONTINUATION, wideAstral.charAt(1));
        assertEquals(CraftOsCharset.CONTINUATION, wideAstral.charAt(2));
        assertEquals(CraftOsCharset.CONTINUATION, wideAstral.charAt(3));
    }

    @Test
    void malformedLeadFallsBackToIndependentLegacyBytes() {
        var raw = new byte[]{ (byte) 0xC2, (byte) ' ' };
        var decoded = Utf8.decodeWide(byteString(raw));

        assertEquals(2, decoded.length());
        assertEquals(CraftOsCharset.toCell(0xC2), decoded.charAt(0));
        assertEquals(' ', decoded.charAt(1));
        assertEquals(1, Utf8.sequenceLength(raw, 0, raw.length));
    }

    @Test
    void reservedMarkerSpellingsCannotForgeTerminalState() {
        for (var marker = CraftOsCharset.LEGACY_ALIAS_BASE; marker <= CraftOsCharset.CONTINUATION; marker++) {
            var utf8 = String.valueOf(marker).getBytes(StandardCharsets.UTF_8);
            assertNull(Utf8.toLegacy(utf8, 0, utf8.length));

            var decoded = Utf8.decodeWide(byteString(utf8));
            assertEquals(utf8.length, decoded.length());
            assertEquals('\uFFFD', decoded.charAt(0));
            for (var i = 1; i < decoded.length(); i++) {
                assertEquals(CraftOsCharset.CONTINUATION, decoded.charAt(i));
            }
        }
    }

    @Test
    void terminalWireEncodingRoundTripsEveryCellKind() {
        char astralCell = CraftOsCharset.toAstralCell(0x1F642);
        var expected = new char[]{
            'A', 'Я',
            CraftOsCharset.toCell(0x81), '\uE041', '\uFFFF',
            astralCell
        };
        var bytes = new ByteArrayOutputStream();
        for (var cell : expected) {
            var astral = CraftOsCharset.fromAstralCell(cell);
            Utf8.encode(astral > 0 ? astral : cell, bytes);
        }

        var actual = new char[expected.length];
        assertEquals(bytes.size(), Utf8.readCells(bytes.toByteArray(), 0, bytes.size(), actual, actual.length));
        assertArrayEquals(expected, actual);
        assertEquals(0x1F642, CraftOsCharset.cellToCodepoint(actual[5]));
    }

    @Test
    void filesystemConversionsPreserveCanonicalUtf8() {
        var cyrillic = "Русский".getBytes(StandardCharsets.UTF_8);
        assertNull(Utf8.toUnicode(cyrillic, 0, cyrillic.length));
        assertNull(Utf8.toLegacy(cyrillic, 0, cyrillic.length));

        var pua = "\uE041".getBytes(StandardCharsets.UTF_8);
        assertNull(Utf8.toLegacy(pua, 0, pua.length));

        var astral = new String(Character.toChars(0x1F642)).getBytes(StandardCharsets.UTF_8);
        assertNull(Utf8.toUnicode(astral, 0, astral.length));
        assertNull(Utf8.toLegacy(astral, 0, astral.length));
    }

    @Test
    void filesystemConversionsPreserveDocxZipContainer() {
        var docx = new byte[]{
            'P', 'K', 3, 4, 20, 0, 0, 0,
            (byte) 0xE2, (byte) 0x96, (byte) 0x8C,
            0, (byte) 0xFF, 1, 2, 3
        };

        assertTrue(Utf8.isBinaryContent(docx, 0, docx.length));
        assertNull(Utf8.toUnicode(docx, 0, docx.length));
        assertNull(Utf8.toLegacy(docx, 0, docx.length));
    }

    @Test
    void filesystemConversionsPreserveCommonBinaryFormats() {
        var formats = new byte[][]{
            {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10},
            {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0},
            {'G', 'I', 'F', '8', '9', 'a'},
            {'%', 'P', 'D', 'F', '-', '1', '.', '7'},
            {0x1F, (byte) 0x8B, 8, 0},
            {'R', 'a', 'r', '!', 0x1A, 7, 1, 0},
            {0x37, 0x7A, (byte) 0xBC, (byte) 0xAF, 0x27, 0x1C},
            {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'A', 'V', 'E'},
            {'%', 'P', 'D', 'F', '-', (byte) 0xE2, (byte) 0x96, (byte) 0x8C},
            {(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE},
            {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0},
            {'{', '\\', 'r', 't', 'f', '1'},
            {0, 1, 2, 3}
        };

        for (var format : formats) {
            assertTrue(Utf8.isBinaryContent(format, 0, format.length));
            assertNull(Utf8.toUnicode(format, 0, format.length));
            assertNull(Utf8.toLegacy(format, 0, format.length));
        }

        var text = "Русский текст\n".getBytes(StandardCharsets.UTF_8);
        assertFalse(Utf8.isBinaryContent(text, 0, text.length));
    }

    @Test
    void legacyTextPromotesAndFoldsBack() {
        var legacy = new byte[]{ (byte) 0x95 };
        var unicode = Utf8.toUnicode(legacy, 0, legacy.length);
        assertArrayEquals("\u258C".getBytes(StandardCharsets.UTF_8), unicode);
        assertArrayEquals(legacy, Utf8.toLegacy(unicode, 0, unicode.length));

        assertArrayEquals(new byte[]{ (byte) 0x81 }, Utf8.encodePreferLegacy(0x1FB00));
        assertArrayEquals("Я".getBytes(StandardCharsets.UTF_8), Utf8.encodePreferLegacy('Я'));
        assertArrayEquals("\uFFFD".getBytes(StandardCharsets.UTF_8),
            Utf8.encodePreferLegacy(CraftOsCharset.LEGACY_ALIAS_BASE));
    }

    private static String byteString(String text) {
        return byteString(text.getBytes(StandardCharsets.UTF_8));
    }

    private static String byteString(byte[] bytes) {
        return new String(bytes, StandardCharsets.ISO_8859_1);
    }
}
