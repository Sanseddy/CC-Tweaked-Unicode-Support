//? if <=1.21.1 {
package ru.sanseddy.cctweakedunicodesupport.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dan200.computercraft.client.render.PrintoutRenderer;
import dan200.computercraft.core.terminal.TextBuffer;
import dan200.computercraft.shared.media.items.PrintoutData;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.sanseddy.cctweakedunicodesupport.client.WideGlyphRenderer;

import java.util.List;

@Mixin(PrintoutRenderer.class)
public class PrintoutRendererMixin {
    private static final String DRAW_BUFFERS = "drawText(Lcom/mojang/blaze3d/vertex/PoseStack;"
        + "Lnet/minecraft/client/renderer/MultiBufferSource;IIII"
        + "[Ldan200/computercraft/core/terminal/TextBuffer;[Ldan200/computercraft/core/terminal/TextBuffer;)V";

    private static final String DRAW_LINES = "drawText(Lcom/mojang/blaze3d/vertex/PoseStack;"
        + "Lnet/minecraft/client/renderer/MultiBufferSource;IIIILjava/util/List;)V";

    @Inject(method = DRAW_BUFFERS, at = @At("HEAD"))
    private static void cc_tweaked_unicode_support$beginBuffers(
        PoseStack transform, MultiBufferSource bufferSource, int x, int y, int start, int light,
        TextBuffer[] text, TextBuffer[] colours, CallbackInfo ci
    ) {
        WideGlyphRenderer.begin();
    }

    @Inject(method = DRAW_BUFFERS, at = @At("RETURN"))
    private static void cc_tweaked_unicode_support$flushBuffers(
        PoseStack transform, MultiBufferSource bufferSource, int x, int y, int start, int light,
        TextBuffer[] text, TextBuffer[] colours, CallbackInfo ci
    ) {
        WideGlyphRenderer.flush(bufferSource);
    }

    @Inject(method = DRAW_LINES, at = @At("HEAD"))
    private static void cc_tweaked_unicode_support$beginLines(
        PoseStack transform, MultiBufferSource bufferSource, int x, int y, int start, int light,
        List<PrintoutData.Line> lines, CallbackInfo ci
    ) {
        WideGlyphRenderer.begin();
    }

    @Inject(method = DRAW_LINES, at = @At("RETURN"))
    private static void cc_tweaked_unicode_support$flushLines(
        PoseStack transform, MultiBufferSource bufferSource, int x, int y, int start, int light,
        List<PrintoutData.Line> lines, CallbackInfo ci
    ) {
        WideGlyphRenderer.flush(bufferSource);
    }
}
//?}
