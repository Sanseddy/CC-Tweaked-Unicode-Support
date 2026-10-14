package ru.sanseddy.cctweakedunicodesupport.mixin.client;

import dan200.computercraft.client.FrameInfo;
import dan200.computercraft.client.render.text.FixedWidthFontRenderer;
import dan200.computercraft.core.terminal.Palette;
import dan200.computercraft.core.terminal.Terminal;
import dan200.computercraft.core.terminal.TextBuffer;
import dan200.computercraft.core.util.Colour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.sanseddy.cctweakedunicodesupport.client.WideGlyphRenderer;
import ru.sanseddy.cctweakedunicodesupport.compat.CCGraphicsCompat;
import ru.sanseddy.cctweakedunicodesupport.text.CraftOsCharset;

import static dan200.computercraft.client.render.RenderTypes.FULL_BRIGHT_LIGHTMAP;

@Mixin(FixedWidthFontRenderer.class)
public class FixedWidthFontRendererMixin {
    @Inject(method = "drawTerminalForeground", at = @At("HEAD"), cancellable = true)
    private static void cc_tweaked_unicode_support$suppressForegroundInGraphicsMode(
        FixedWidthFontRenderer.QuadEmitter emitter, float x, float y, Terminal terminal, CallbackInfo ci
    ) {
        if (CCGraphicsCompat.isGraphicsMode(terminal)) {
            ci.cancel();
        }
    }

    @Inject(method = "drawString", at = @At("HEAD"), cancellable = true)
    private static void cc_tweaked_unicode_support$drawWideString(
        FixedWidthFontRenderer.QuadEmitter emitter, float x, float y,
        TextBuffer text, TextBuffer textColour, Palette palette, int light, CallbackInfo ci
    ) {
        var offset = 0;
        for (var i = 0; i < text.length(); i++) {
            var cell = text.charAt(i);
            if (!WideGlyphRenderer.isLead(text, i)) continue;

            var colour = palette.getRenderColours(FixedWidthFontRenderer.getColour(textColour.charAt(i), Colour.BLACK));
            var glyphX = x + offset;
            var codepoint = WideGlyphRenderer.codepoint(text, i);
            offset += WideGlyphRenderer.cellWidth();

            if (cell == 0 || cell == ' ') continue;

            var terminalGlyph = CraftOsCharset.terminalOnlyGlyph(codepoint);
            if (terminalGlyph >= 0) {
                FixedWidthFontRendererInvoker.cc_tweaked_unicode_support$drawChar(emitter, glyphX, y, terminalGlyph, colour, light);
            } else {
                WideGlyphRenderer.record(emitter.poseMatrix(), glyphX, y, codepoint, colour, light);
            }
        }
        ci.cancel();
    }

    @Inject(method = "drawCursor", at = @At("HEAD"), cancellable = true)
    private static void cc_tweaked_unicode_support$drawMonospaceCursor(
        FixedWidthFontRenderer.QuadEmitter emitter, float x, float y, Terminal terminal, CallbackInfo ci
    ) {
        if (CCGraphicsCompat.isGraphicsMode(terminal)) {
            ci.cancel();
            return;
        }

        if (FixedWidthFontRenderer.isCursorVisible(terminal) && FrameInfo.getGlobalCursorBlink()) {
            var colour = terminal.getPalette().getRenderColours(15 - terminal.getTextColour());
            var cursorX = WideGlyphRenderer.xAt(terminal.getLine(terminal.getCursorY()), terminal.getCursorX());
            FixedWidthFontRendererInvoker.cc_tweaked_unicode_support$drawChar(
                emitter, x + cursorX, y + terminal.getCursorY() * FixedWidthFontRenderer.FONT_HEIGHT,
                '_', colour, FULL_BRIGHT_LIGHTMAP
            );
        }
        ci.cancel();
    }
}
