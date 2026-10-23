package ru.sanseddy.cctweakedunicodesupport.client;

import dan200.computercraft.core.terminal.TextBuffer;
import org.junit.jupiter.api.Test;
import ru.sanseddy.cctweakedunicodesupport.text.CraftOsCharset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WideGlyphRendererTest {
    @Test
    void usesOneFontDerivedWidthForEveryGlyph() {
        var codepoint = 0x1F642;
        char astralCell = CraftOsCharset.toAstralCell(codepoint);
        var text = new TextBuffer(new String(new char[]{
            'A', 'Я', astralCell, 'B'
        }));
        assertEquals(6, WideGlyphRenderer.cellWidth());
        assertEquals(6, WideGlyphRenderer.advance(text, 0));
        assertEquals(6, WideGlyphRenderer.advance(text, 1));
        assertEquals(6, WideGlyphRenderer.advance(text, 2));
        assertEquals(6, WideGlyphRenderer.advance(text, 3));

        assertEquals(0, WideGlyphRenderer.xAt(text, 0));
        assertEquals(6, WideGlyphRenderer.xAt(text, 1));
        assertEquals(12, WideGlyphRenderer.xAt(text, 2));
        assertEquals(18, WideGlyphRenderer.xAt(text, 3));
        assertEquals(24, WideGlyphRenderer.xAt(text, text.length()));

        assertEquals(0, WideGlyphRenderer.columnAt(text, 0));
        assertEquals(0, WideGlyphRenderer.columnAt(text, 5.99));
        assertEquals(1, WideGlyphRenderer.columnAt(text, 6));
        assertEquals(2, WideGlyphRenderer.columnAt(text, 12));
        assertEquals(3, WideGlyphRenderer.columnAt(text, 23.99));

        // Non-wide script: original behavior preserved (scaleY = 1.0, offsetY = 0.0)
        assertEquals(1.0f, WideGlyphRenderer.scaleForAdvance(4));
        assertEquals(1.0f, WideGlyphRenderer.scaleForAdvance(6));
        assertEquals(0.6f, WideGlyphRenderer.scaleForAdvance(10), 1e-6f);
        assertEquals(0.0f, WideGlyphRenderer.scaleForAdvance(0));

        assertEquals(1.0f, WideGlyphRenderer.scaleYForAdvance(4));
        assertEquals(1.0f, WideGlyphRenderer.scaleYForAdvance(6));
        assertEquals(1.0f, WideGlyphRenderer.scaleYForAdvance(10));

        assertEquals(2.0f, WideGlyphRenderer.centeredOffsetForAdvance(2));
        assertEquals(1.0f, WideGlyphRenderer.centeredOffsetForAdvance(4));
        assertEquals(0.0f, WideGlyphRenderer.centeredOffsetForAdvance(6));
        assertEquals(0.0f, WideGlyphRenderer.centeredOffsetForAdvance(10));
        assertEquals(0.0f, WideGlyphRenderer.centeredOffsetForAdvance(0));

        assertEquals(0.0f, WideGlyphRenderer.centeredOffsetYForAdvance(4));
        assertEquals(0.0f, WideGlyphRenderer.centeredOffsetYForAdvance(6));
        assertEquals(0.0f, WideGlyphRenderer.centeredOffsetYForAdvance(10));

        // Wide script filter verification (CJK / Han, Hiragana, Hangul, Fullwidth)
        var chineseNi = 0x4F60; // '你'
        assertTrue(WideGlyphRenderer.isWideScript(chineseNi));
        assertTrue(WideGlyphRenderer.isWideScript(0x3042)); // 'あ' (Hiragana)
        assertTrue(WideGlyphRenderer.isWideScript(0xD55C)); // '한' (Hangul)
        assertTrue(WideGlyphRenderer.isWideScript(0xFF21)); // 'Ａ' (Fullwidth Latin)
        assertFalse(WideGlyphRenderer.isWideScript('A'));
        assertFalse(WideGlyphRenderer.isWideScript(0x0406)); // Ukrainian 'І'
        assertFalse(WideGlyphRenderer.isWideScript(0x2211)); // '∑' (Math summation)

        // Filtered wide script behavior: 40% less vertical compression (larger, slightly stretched look)
        var expectedScaleX = 5.7f / 10.0f;
        var expectedScaleY = expectedScaleX + (1.0f - expectedScaleX) * 0.40f;
        assertEquals(expectedScaleX, WideGlyphRenderer.scaleForAdvance(chineseNi, 11), 1e-6f);
        assertEquals(expectedScaleY, WideGlyphRenderer.scaleYForAdvance(chineseNi, 11), 1e-6f);
        assertEquals(0.15f, WideGlyphRenderer.centeredOffsetForAdvance(chineseNi, 11), 1e-6f);
        assertEquals((9.0f - 9.0f * expectedScaleY) / 2.0f, WideGlyphRenderer.centeredOffsetYForAdvance(chineseNi, 11), 1e-6f);

        // Non-wide-script with advance > 6 preserves scaleY = 1.0f, offsetY = 0.0f
        assertEquals(0.6f, WideGlyphRenderer.scaleForAdvance(0x2211, 10), 1e-6f);
        assertEquals(1.0f, WideGlyphRenderer.scaleYForAdvance(0x2211, 10));
        assertEquals(0.0f, WideGlyphRenderer.centeredOffsetForAdvance(0x2211, 10));
        assertEquals(0.0f, WideGlyphRenderer.centeredOffsetYForAdvance(0x2211, 10));

        var fullAsciiRow = new TextBuffer("0".repeat(51));
        assertEquals(51 * 6, WideGlyphRenderer.xAt(fullAsciiRow, fullAsciiRow.length()));

        assertEquals(codepoint, WideGlyphRenderer.codepoint(text, 2));
    }
}
