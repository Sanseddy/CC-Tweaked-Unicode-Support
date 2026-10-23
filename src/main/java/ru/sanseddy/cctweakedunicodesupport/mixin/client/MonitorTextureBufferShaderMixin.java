//? if <=1.21.1 {
package ru.sanseddy.cctweakedunicodesupport.mixin.client;

import dan200.computercraft.client.render.monitor.MonitorTextureBufferShader;
import dan200.computercraft.client.render.text.FixedWidthFontRenderer;
import dan200.computercraft.core.terminal.Terminal;
import dan200.computercraft.core.util.Colour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.ByteBuffer;

@Mixin(MonitorTextureBufferShader.class)
public class MonitorTextureBufferShaderMixin {
    @Inject(method = "setTerminalData", at = @At("HEAD"), cancellable = true)
    private static void cc_tweaked_unicode_support$blankTextCells(ByteBuffer buffer, Terminal terminal, CallbackInfo ci) {
        var width = terminal.getWidth();
        var height = terminal.getHeight();
        var pos = 0;

        for (var y = 0; y < height; y++) {
            var textColour = terminal.getTextColourLine(y);
            var background = terminal.getBackgroundColourLine(y);

            for (var x = 0; x < width; x++) {
                buffer.put(pos, (byte) ' ');
                buffer.put(pos + 1, (byte) FixedWidthFontRenderer.getColour(textColour.charAt(x), Colour.WHITE));
                buffer.put(pos + 2, (byte) FixedWidthFontRenderer.getColour(background.charAt(x), Colour.BLACK));
                pos += 3;
            }
        }

        buffer.limit(pos);
        ci.cancel();
    }
}
//?}
