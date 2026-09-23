package ru.sanseddy.cctweakedunicodesupport.text;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NetworkedTerminalRoundtripTest {

    @Test
    void roundtripPreservesUnicodeAndLegacyAcrossIndependentClientAndServer() {
        // Prepare server-side text line containing:
        // 1. ASCII: 'A'
        // 2. Cyrillic: 'Ж'
        // 3. Legacy CC:Tweaked symbol (byte 128 -> codepoint 0x1FB00)
        // 4. Emoji: '😀' (0x1F600)
        // 5. Another astral: '🚀' (0x1F680)

        int[] originalCodepoints = new int[]{
            'A',
            'Ж',
            CraftOsCharset.toCodepoint(0x81),
            0x1F600,
            0x1F680
        };

        // Server internal cells
        char[] serverCells = new char[originalCodepoints.length];
        for (int i = 0; i < originalCodepoints.length; i++) {
            int cp = originalCodepoints[i];
            if (cp <= 0xFFFF) {
                var legacyByte = CraftOsCharset.toLegacyByte(cp);
                serverCells[i] = legacyByte >= 0 ? CraftOsCharset.toCell(legacyByte) : (char) cp;
            } else {
                var legacyByte = CraftOsCharset.toLegacyByte(cp);
                serverCells[i] = legacyByte >= 0 ? CraftOsCharset.toCell(legacyByte) : CraftOsCharset.toAstralCell(cp);
            }
        }

        // Server writes to network: translates cellToCodepoint -> Utf8.encode
        var networkStream = new ByteArrayOutputStream();
        for (char ch : serverCells) {
            int codepoint = CraftOsCharset.cellToCodepoint(ch);
            Utf8.encode(codepoint, networkStream);
        }

        byte[] networkBytes = networkStream.toByteArray();

        // Simulate client with fresh/independent state
        char[] clientCells = new char[originalCodepoints.length];
        int readBytes = Utf8.readCells(networkBytes, 0, networkBytes.length, clientCells, clientCells.length);
        assertEquals(networkBytes.length, readBytes);

        // Client converts cells back to codepoints
        for (int i = 0; i < originalCodepoints.length; i++) {
            int clientCodepoint = CraftOsCharset.cellToCodepoint(clientCells[i]);
            assertEquals(originalCodepoints[i], clientCodepoint,
                "Codepoint at index " + i + " must match after network roundtrip");
        }
    }
}
