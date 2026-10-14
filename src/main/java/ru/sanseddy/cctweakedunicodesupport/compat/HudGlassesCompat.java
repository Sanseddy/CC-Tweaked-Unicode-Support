package ru.sanseddy.cctweakedunicodesupport.compat;

import ru.sanseddy.cctweakedunicodesupport.text.CraftOsCharset;
import ru.sanseddy.cctweakedunicodesupport.text.Utf8;

public final class HudGlassesCompat {
    private HudGlassesCompat() {
    }

    public static String decodeTextForHud(String raw) {
        if (raw == null || raw.isEmpty()) return raw;

        var sb = new StringBuilder(raw.length());
        var i = 0;
        while (i < raw.length()) {
            char c = raw.charAt(i);

            // If the character is already a surrogate pair (e.g. from Java string), map to astral cell:
            if (Character.isHighSurrogate(c) && i + 1 < raw.length() && Character.isLowSurrogate(raw.charAt(i + 1))) {
                int codepoint = Character.toCodePoint(c, raw.charAt(i + 1));
                int legacy = CraftOsCharset.toLegacyByte(codepoint);
                if (legacy >= 0 && legacy < 256) {
                    sb.append((char) legacy);
                } else {
                    sb.append(CraftOsCharset.toAstralCell(codepoint));
                }
                i += 2;
                continue;
            }

            // If the character is already a legacy alias (\uFDD0..\uFDEE), recover the CC byte:
            if (c >= CraftOsCharset.LEGACY_ALIAS_BASE && c <= CraftOsCharset.LEGACY_ALIAS_END) {
                int legacy = CraftOsCharset.toLegacyByte(c);
                sb.append(legacy >= 0 && legacy < 256 ? (char) legacy : c);
                i++;
                continue;
            }

            // If the character is already an astral alias (\uF000..\uF7FF), keep it:
            if (c >= CraftOsCharset.ASTRAL_ALIAS_BASE && c < CraftOsCharset.ASTRAL_ALIAS_BASE + CraftOsCharset.MAX_ASTRAL_ALIASES) {
                sb.append(c);
                i++;
                continue;
            }

            // If already Unicode (> 255):
            if (c > 255) {
                // Check if this Unicode character can be mapped to a native CC terminal glyph (0..255)
                int legacy = CraftOsCharset.toLegacyByte(c);
                if (legacy >= 0 && legacy < 256) {
                    sb.append((char) legacy);
                } else {
                    sb.append(c);
                }
                i++;
                continue;
            }

            // Otherwise, c <= 255. Check for UTF-8 sequence starting here:
            int consumed = Utf8.sequenceLength(raw, i);
            if (consumed > 1) {
                int codepoint = Utf8.codepoint(raw, i, consumed);
                // Check if this UTF-8 sequence corresponds to a native CC terminal glyph:
                int legacy = CraftOsCharset.toLegacyByte(codepoint);
                if (legacy >= 0 && legacy < 256) {
                    sb.append((char) legacy);
                } else if (codepoint <= 0xFFFF) {
                    sb.append((char) codepoint);
                } else {
                    sb.append(CraftOsCharset.toAstralCell(codepoint));
                }
                i += consumed;
            } else {
                // Single byte (c <= 255):
                // In CC: HUD Glasses, bytes 0..255 are native CC terminal font characters!
                // Keeping as (char)(c & 0xFF) allows CharMap.remap to map it to cc_term.png (0xE000..0xE0FF)!
                sb.append((char) (c & 0xFF));
                i++;
            }
        }
        return sb.toString();
    }
}
