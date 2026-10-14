package ru.sanseddy.cctweakedunicodesupport.compat.hudglasses;

import org.junit.jupiter.api.Test;
import ru.sanseddy.cctweakedunicodesupport.compat.HudGlassesCompat;
import ru.sanseddy.cctweakedunicodesupport.text.CraftOsCharset;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HudTextDecodeTest {

    private static String asByteString(String text) {
        var bytes = text.getBytes(StandardCharsets.UTF_8);
        var sb = new StringBuilder(bytes.length);
        for (byte b : bytes) {
            sb.append((char) (b & 0xFF));
        }
        return sb.toString();
    }

    @Test
    void decodesUkrainianLettersCorrectly() {
        var raw = asByteString("Привіт Іван і Їжак Євген Ґрунт");
        var decoded = HudGlassesCompat.decodeTextForHud(raw);
        assertEquals("Привіт Іван і Їжак Євген Ґрунт", decoded);
    }

    @Test
    void preservesCcNativeTerminalCharactersWithoutNoncharacterAliases() {
        // Single byte 140 (teletext character)
        var raw = Character.toString((char) 140);
        var decoded = HudGlassesCompat.decodeTextForHud(raw);
        // Must stay 140 (< 256) so CharMap.remap maps it to cc_term.png, NOT \uFDD0 (noncharacter box)
        assertEquals(1, decoded.length());
        assertEquals(140, (int) decoded.charAt(0));
    }

    @Test
    void recoversAliasesToNativeCcBytes() {
        // A string containing an internal alias \uFDD0
        char alias = CraftOsCharset.toCell(128); // teletext 0x1FB00 alias \uFDD0
        var raw = Character.toString(alias);
        var decoded = HudGlassesCompat.decodeTextForHud(raw);
        // Must convert back to byte 128
        assertEquals(1, decoded.length());
        assertEquals(128, (int) decoded.charAt(0));
    }

    @Test
    void mapsUtf8TeletextToCcTerminalGlyph() {
        var raw = asByteString(new String(Character.toChars(0x1FB00)));
        var decoded = HudGlassesCompat.decodeTextForHud(raw);
        assertEquals(1, decoded.length());
        int b = decoded.charAt(0);
        assertEquals(129, b);
    }

    @Test
    void doesNotDoubleDecode() {
        var alreadyDecoded = "Привіт Іван";
        var decoded = HudGlassesCompat.decodeTextForHud(alreadyDecoded);
        assertEquals("Привіт Іван", decoded);
    }

    @Test
    void decodesAstralEmojisToAstralAliases() {
        var raw = asByteString("🙂 😀");
        var decoded = HudGlassesCompat.decodeTextForHud(raw);
        assertEquals(3, decoded.length()); // 1 cell for 🙂, 1 space, 1 cell for 😀
        char emoji1 = decoded.charAt(0);
        char space = decoded.charAt(1);
        char emoji2 = decoded.charAt(2);
        assertEquals(' ', space);
        assertEquals(0x1F642, CraftOsCharset.cellToCodepoint(emoji1));
        assertEquals(0x1F600, CraftOsCharset.cellToCodepoint(emoji2));
    }
}
