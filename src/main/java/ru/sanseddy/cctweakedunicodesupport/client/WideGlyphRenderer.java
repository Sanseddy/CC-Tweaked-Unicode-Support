package ru.sanseddy.cctweakedunicodesupport.client;

import dan200.computercraft.client.FrameInfo;
import dan200.computercraft.client.render.text.FixedWidthFontRenderer;
import dan200.computercraft.core.terminal.TextBuffer;
//? if <=1.21.1 {
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
//?}
import org.joml.Matrix4f;
import ru.sanseddy.cctweakedunicodesupport.text.CraftOsCharset;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class WideGlyphRenderer {

    private static final String[] BMP_GLYPHS = new String[0x10000];
    private static final Map<Integer, String> ASTRAL_GLYPHS = new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, String> eldest) {
            return size() > 256;
        }
    };

    private record Glyph(Matrix4f pose, float x, float y, int codepoint, int colour, int light) {
    }

    public static int codepoint(TextBuffer text, int index) {
        return CraftOsCharset.cellToCodepoint(text.charAt(index));
    }

    public static boolean isLead(TextBuffer text, int index) {
        return text.charAt(index) != CraftOsCharset.CONTINUATION;
    }

    public static int cellWidth() {
        return FixedWidthFontRenderer.FONT_WIDTH;
    }

    public static int cellHeight() {
        return FixedWidthFontRenderer.FONT_HEIGHT;
    }

    public static boolean isWideScript(int codepoint) {
        if (codepoint < 0x1100) return false;
        try {
            var script = Character.UnicodeScript.of(codepoint);
            if (script == Character.UnicodeScript.HAN
                || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA
                || script == Character.UnicodeScript.HANGUL
                || script == Character.UnicodeScript.BOPOMOFO) {
                return true;
            }
        } catch (IllegalArgumentException ignored) {
        }
        return (codepoint >= 0x2E80 && codepoint <= 0x33FF)
            || (codepoint >= 0xFE30 && codepoint <= 0xFE4F)
            || (codepoint >= 0xFF01 && codepoint <= 0xFF60)
            || (codepoint >= 0xFFE0 && codepoint <= 0xFFE6);
    }

    static float scaleForAdvance(int codepoint, int advance) {
        if (advance <= 0) return 0.0f;
        if (isWideScript(codepoint)) {
            if (advance <= cellWidth()) return 1.0f;
            var effectiveAdvance = advance - 1.0f;
            return Math.min(1.0f, (cellWidth() - 0.3f) / effectiveAdvance);
        }
        return Math.min(1.0f, (float) cellWidth() / advance);
    }

    static float scaleYForAdvance(int codepoint, int advance) {
        if (isWideScript(codepoint)) {
            if (advance <= cellWidth()) return 1.0f;
            var scaleX = scaleForAdvance(codepoint, advance);
            return scaleX + (1.0f - scaleX) * 0.40f;
        }
        return 1.0f;
    }

    static float centeredOffsetForAdvance(int codepoint, int advance) {
        if (advance <= 0) return 0.0f;
        if (isWideScript(codepoint)) {
            if (advance <= cellWidth()) {
                return (cellWidth() - advance) / 2.0f;
            }
            var visualWidth = (advance - 1.0f) * scaleForAdvance(codepoint, advance);
            return (cellWidth() - visualWidth) / 2.0f;
        }
        var renderedWidth = advance * scaleForAdvance(codepoint, advance);
        return (cellWidth() - renderedWidth) / 2.0f;
    }

    static float centeredOffsetYForAdvance(int codepoint, int advance) {
        if (isWideScript(codepoint)) {
            if (advance <= cellWidth()) return 0.0f;
            var renderedHeight = cellHeight() * scaleYForAdvance(codepoint, advance);
            return (cellHeight() - renderedHeight) / 2.0f;
        }
        return 0.0f;
    }

    static float scaleForAdvance(int advance) {
        return scaleForAdvance(0, advance);
    }

    static float scaleYForAdvance(int advance) {
        return scaleYForAdvance(0, advance);
    }

    static float centeredOffsetForAdvance(int advance) {
        return centeredOffsetForAdvance(0, advance);
    }

    static float centeredOffsetYForAdvance(int advance) {
        return centeredOffsetYForAdvance(0, advance);
    }

    public static int advance(TextBuffer text, int index) {
        return isLead(text, index) ? cellWidth() : 0;
    }

    public static int xAt(TextBuffer text, int endExclusive) {
        return Math.max(0, endExclusive) * cellWidth();
    }

    public static int columnAt(TextBuffer text, double pixelX) {
        if (text.length() == 0 || pixelX < 0) return -1;
        int col = (int) (pixelX / cellWidth());
        return Math.min(col, text.length() - 1);
    }

    private static final List<Glyph> PENDING = new ArrayList<>();

    private static int depth;
    private static long frame = -1;

    private static Matrix4f lastPose;
    private static Matrix4f lastSnapshot;

    private WideGlyphRenderer() {
    }

    public static void begin() {
        var currentFrame = FrameInfo.getRenderFrame();
        if (frame != currentFrame) {
            frame = currentFrame;
            depth = 0;
            PENDING.clear();
            lastPose = null;
            lastSnapshot = null;
        }

        if (depth++ == 0) {
            PENDING.clear();
            lastPose = null;
            lastSnapshot = null;
        }
    }

    public static void record(Matrix4f pose, float x, float y, int codepoint, int colour, int light) {
        if (depth == 0) return;

        if (pose != lastPose) {
            lastPose = pose;
            lastSnapshot = new Matrix4f(pose);
        }
        PENDING.add(new Glyph(lastSnapshot, x, y, codepoint, colour, light));
    }

    //? if <=1.21.1 {
    public static void flush(MultiBufferSource bufferSource) {
        if (depth > 0) depth--;
        if (depth > 0) return;

        for (var glyph : PENDING) {
            draw(bufferSource, glyph.pose(), glyph.x(), glyph.y(), glyph.codepoint(),
                glyph.colour(), glyph.light(), 0.0f);
        }
        PENDING.clear();
        lastPose = null;
        lastSnapshot = null;
    }

    public static void draw(
        MultiBufferSource bufferSource, Matrix4f pose, float x, float y,
        int codepoint, int colour, int light, float z
    ) {
        var font = Minecraft.getInstance().font;
        var text = glyph(codepoint);
        var advance = font.width(text);
        if (advance <= 0) return;

        var scaleX = scaleForAdvance(codepoint, advance);
        var scaleY = scaleYForAdvance(codepoint, advance);
        var offsetX = centeredOffsetForAdvance(codepoint, advance);
        var offsetY = centeredOffsetYForAdvance(codepoint, advance);
        var matrix = new Matrix4f(pose).translate(x + offsetX, y + offsetY, z).scale(scaleX, scaleY, 1.0f);

        font.drawInBatch(
            text, 0.0f, 0.0f, colour | 0xFF000000, false, matrix, bufferSource, Font.DisplayMode.NORMAL, 0, light
        );
    }
    //?} else {
    /*public static void renderTerminalWidget(
        net.minecraft.client.gui.GuiGraphicsExtractor graphics,
        dan200.computercraft.core.terminal.Terminal terminal,
        int innerX, int innerY
    ) {
        var textRenderer = graphics.textRenderer();
        var font = net.minecraft.client.Minecraft.getInstance().font;
        int height = terminal.getHeight();
        for (int y = 0; y < height; y++) {
            var text = terminal.getLine(y);
            var textColour = terminal.getTextColourLine(y);
            int len = Math.min(text.length(), textColour.length());
            for (int x = 0; x < len; x++) {
                char cell = text.charAt(x);
                if (!isLead(text, x)) continue;
                int codepoint = codepoint(text, x);
                if (cell == 0 || cell == ' ') continue;
                if (CraftOsCharset.terminalOnlyGlyph(codepoint) >= 0) continue;

                String glyphStr = glyph(codepoint);
                int advance = font.width(glyphStr);
                if (advance <= 0) continue;

                float scaleX = scaleForAdvance(codepoint, advance);
                float scaleY = scaleYForAdvance(codepoint, advance);
                float offX = centeredOffsetForAdvance(codepoint, advance);
                float offY = centeredOffsetYForAdvance(codepoint, advance);

                float glyphX = innerX + x * cellWidth() + offX;
                float glyphY = innerY + y * cellHeight() + offY;

                int colour = terminal.getPalette().getRenderColours(
                    dan200.computercraft.client.render.text.FixedWidthFontRenderer.getColour(
                        textColour.charAt(x), dan200.computercraft.core.util.Colour.BLACK
                    )
                );

                var pose = new org.joml.Matrix3x2f(graphics.pose())
                    .translate(glyphX, glyphY)
                    .scale(scaleX, scaleY);
                var params = new net.minecraft.client.gui.ActiveTextCollector.Parameters(pose);
                textRenderer.accept(
                    net.minecraft.client.gui.TextAlignment.LEFT,
                    0, 0,
                    params,
                    net.minecraft.network.chat.Component.literal(glyphStr)
                        .withStyle(s -> s.withColor(colour & 0xFFFFFF))
                );
            }
        }
    }
    *///?}

    public static String glyph(int codepoint) {
        if (codepoint < 0 || (codepoint >= 0xD800 && codepoint <= 0xDFFF) || codepoint > Character.MAX_CODE_POINT) {
            return "\uFFFD";
        }
        if (codepoint <= Character.MAX_VALUE) {
            var cached = BMP_GLYPHS[codepoint];
            if (cached == null) BMP_GLYPHS[codepoint] = cached = String.valueOf((char) codepoint);
            return cached;
        }
        return ASTRAL_GLYPHS.computeIfAbsent(codepoint, value -> new String(Character.toChars(value)));
    }
}
