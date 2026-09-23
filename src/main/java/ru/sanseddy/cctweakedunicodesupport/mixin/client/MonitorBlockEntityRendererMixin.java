package ru.sanseddy.cctweakedunicodesupport.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dan200.computercraft.client.FrameInfo;
import dan200.computercraft.client.render.monitor.MonitorBlockEntityRenderer;
import dan200.computercraft.client.render.text.FixedWidthFontRenderer;
import dan200.computercraft.core.util.Colour;
import dan200.computercraft.shared.peripheral.monitor.MonitorBlockEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.sanseddy.cctweakedunicodesupport.client.WideGlyphRenderer;
import ru.sanseddy.cctweakedunicodesupport.text.CraftOsCharset;

@Mixin(MonitorBlockEntityRenderer.class)
public class MonitorBlockEntityRendererMixin {
    private static final String RENDER = "render(Ldan200/computercraft/shared/peripheral/monitor/MonitorBlockEntity;F"
        + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V";

    private static final float DEPTH_OFFSET = 0.002f;

    @Inject(
        method = RENDER,
        at = @At(
            value = "INVOKE",
            shift = At.Shift.AFTER,
            target = "Ldan200/computercraft/client/render/monitor/MonitorBlockEntityRenderer;renderTerminal("
                + "Lorg/joml/Matrix4f;"
                + "Ldan200/computercraft/shared/peripheral/monitor/ClientMonitor;"
                + "Ldan200/computercraft/client/render/monitor/MonitorRenderState;"
                + "Ldan200/computercraft/core/terminal/Terminal;FF)V"
        )
    )
    private void cc_tweaked_unicode_support$drawWideOverlay(
        MonitorBlockEntity monitor, float partialTicks, PoseStack transform,
        MultiBufferSource bufferSource, int lightmapCoord, int overlayLight, CallbackInfo ci
    ) {
        var origin = monitor.getOriginClientMonitor();
        if (origin == null) return;

        var terminal = origin.getTerminal();
        if (terminal == null) return;

        var pose = transform.last().pose();
        var foregroundPose = new org.joml.Matrix4f(pose).translate(0.0f, 0.0f, DEPTH_OFFSET * 2.0f);
        var palette = terminal.getPalette();

        // Pass 1: Render CC terminal font characters and cursor using RenderTypes.TERMINAL.
        // This pass must completely finish before any call to WideGlyphRenderer.draw(),
        // because WideGlyphRenderer requests font render types from MultiBufferSource,
        // which ends the active batch for RenderTypes.TERMINAL!
        FixedWidthFontRenderer.QuadEmitter terminalEmitter = null;

        for (var row = 0; row < terminal.getHeight(); row++) {
            var line = terminal.getLine(row);
            var colourLine = terminal.getTextColourLine(row);

            var offset = 0;
            for (var col = 0; col < line.length(); col++) {
                var cell = line.charAt(col);
                if (!WideGlyphRenderer.isLead(line, col)) continue;

                var codepoint = WideGlyphRenderer.codepoint(line, col);
                var glyphX = offset;
                offset += WideGlyphRenderer.cellWidth();

                if (cell == 0 || cell == ' ') continue;

                var terminalGlyph = CraftOsCharset.terminalOnlyGlyph(codepoint);
                if (terminalGlyph >= 0) {
                    if (terminalEmitter == null) {
                        terminalEmitter = new FixedWidthFontRenderer.QuadEmitter(
                            foregroundPose, bufferSource.getBuffer(dan200.computercraft.client.render.RenderTypes.TERMINAL)
                        );
                    }
                    var colour = palette.getRenderColours(FixedWidthFontRenderer.getColour(colourLine.charAt(col), Colour.BLACK));
                    FixedWidthFontRendererInvoker.cc_tweaked_unicode_support$drawChar(
                        terminalEmitter,
                        glyphX,
                        row * FixedWidthFontRenderer.FONT_HEIGHT,
                        terminalGlyph, colour, LightTexture.FULL_BRIGHT
                    );
                }
            }
        }

        if (FixedWidthFontRenderer.isCursorVisible(terminal) && FrameInfo.getGlobalCursorBlink()) {
            var cursorY = terminal.getCursorY();
            var cursorX = WideGlyphRenderer.xAt(terminal.getLine(cursorY), terminal.getCursorX());
            var colour = palette.getRenderColours(15 - terminal.getTextColour());
            if (terminalEmitter == null) {
                terminalEmitter = new FixedWidthFontRenderer.QuadEmitter(
                    foregroundPose, bufferSource.getBuffer(dan200.computercraft.client.render.RenderTypes.TERMINAL)
                );
            }
            FixedWidthFontRendererInvoker.cc_tweaked_unicode_support$drawChar(
                terminalEmitter, cursorX, cursorY * FixedWidthFontRenderer.FONT_HEIGHT,
                '_', colour, LightTexture.FULL_BRIGHT
            );
        }

        // Pass 2: Render all Unicode / non-terminal glyphs using Minecraft font.
        for (var row = 0; row < terminal.getHeight(); row++) {
            var line = terminal.getLine(row);
            var colourLine = terminal.getTextColourLine(row);

            var offset = 0;
            for (var col = 0; col < line.length(); col++) {
                var cell = line.charAt(col);
                if (!WideGlyphRenderer.isLead(line, col)) continue;

                var codepoint = WideGlyphRenderer.codepoint(line, col);
                var glyphX = offset;
                offset += WideGlyphRenderer.cellWidth();

                if (cell == 0 || cell == ' ') continue;

                var terminalGlyph = CraftOsCharset.terminalOnlyGlyph(codepoint);
                if (terminalGlyph < 0) {
                    var colour = palette.getRenderColours(FixedWidthFontRenderer.getColour(colourLine.charAt(col), Colour.BLACK));
                    WideGlyphRenderer.draw(
                        bufferSource, pose,
                        glyphX, row * FixedWidthFontRenderer.FONT_HEIGHT,
                        codepoint, colour, LightTexture.FULL_BRIGHT, DEPTH_OFFSET * 2.0f
                    );
                }
            }
        }
    }
}
