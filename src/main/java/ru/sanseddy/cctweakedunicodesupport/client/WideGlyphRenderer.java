package ru.sanseddy.cctweakedunicodesupport.client;

import dan200.computercraft.client.FrameInfo;
import dan200.computercraft.client.render.text.FixedWidthFontRenderer;
import dan200.computercraft.core.terminal.TextBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
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

    static float scaleForAdvance(int advance) {
        return advance <= 0 ? 0.0f : Math.min(1.0f, (float) cellWidth() / advance);
    }

    static float centeredOffsetForAdvance(int advance) {
        if (advance <= 0) return 0.0f;
        var renderedWidth = advance * scaleForAdvance(advance);
        return (cellWidth() - renderedWidth) / 2.0f;
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

        var scaleX = scaleForAdvance(advance);
        var offsetX = centeredOffsetForAdvance(advance);
        var matrix = new Matrix4f(pose).translate(x + offsetX, y, z).scale(scaleX, 1.0f, 1.0f);

        font.drawInBatch(
            text, 0.0f, 0.0f, colour | 0xFF000000, false, matrix, bufferSource, Font.DisplayMode.NORMAL, 0, light
        );
    }

    private static String glyph(int codepoint) {
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
