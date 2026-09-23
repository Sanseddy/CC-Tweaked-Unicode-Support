package ru.sanseddy.cctweakedunicodesupport.mixin.client;

import dan200.computercraft.client.render.text.DirectFixedWidthFontRenderer;
import dan200.computercraft.core.terminal.Palette;
import dan200.computercraft.core.terminal.TextBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DirectFixedWidthFontRenderer.class)
public class DirectFixedWidthFontRendererMixin {
    @Inject(method = "drawString", at = @At("HEAD"), cancellable = true)
    private static void cc_tweaked_unicode_support$blankText(
        DirectFixedWidthFontRenderer.QuadEmitter emitter, float x, float y,
        TextBuffer text, TextBuffer textColour, Palette palette, CallbackInfo ci
    ) {
        ci.cancel();
    }

    @Inject(method = "drawCursor", at = @At("HEAD"), cancellable = true)
    private static void cc_tweaked_unicode_support$cancelDirectCursor(
        DirectFixedWidthFontRenderer.QuadEmitter emitter, float x, float y,
        dan200.computercraft.core.terminal.Terminal terminal, CallbackInfo ci
    ) {
        ci.cancel();
    }
}
