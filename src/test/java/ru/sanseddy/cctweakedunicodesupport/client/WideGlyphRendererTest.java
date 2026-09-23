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

        assertEquals(1.0f, WideGlyphRenderer.scaleForAdvance(4));
        assertEquals(1.0f, WideGlyphRenderer.scaleForAdvance(6));
        assertEquals(0.6f, WideGlyphRenderer.scaleForAdvance(10));
        assertEquals(0.0f, WideGlyphRenderer.scaleForAdvance(0));
        assertEquals(2.0f, WideGlyphRenderer.centeredOffsetForAdvance(2));
        assertEquals(1.0f, WideGlyphRenderer.centeredOffsetForAdvance(4));
        assertEquals(0.0f, WideGlyphRenderer.centeredOffsetForAdvance(6));
        assertEquals(0.0f, WideGlyphRenderer.centeredOffsetForAdvance(10));
        assertEquals(0.0f, WideGlyphRenderer.centeredOffsetForAdvance(0));

        var fullAsciiRow = new TextBuffer("0".repeat(51));
        assertEquals(51 * 6, WideGlyphRenderer.xAt(fullAsciiRow, fullAsciiRow.length()));

        assertEquals(codepoint, WideGlyphRenderer.codepoint(text, 2));
    }
}
