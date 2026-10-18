//? if <=1.21.1 {
package ru.sanseddy.cctweakedunicodesupport.mixin.client;

import dan200.computercraft.client.gui.AbstractComputerScreen;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.sanseddy.cctweakedunicodesupport.client.WideGlyphRenderer;

@Mixin(AbstractComputerScreen.class)
public class AbstractComputerScreenMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void cc_tweaked_unicode_support$beginGlyphPass(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        WideGlyphRenderer.begin();
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void cc_tweaked_unicode_support$flushGlyphPass(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        WideGlyphRenderer.flush(graphics.bufferSource());
    }
}
//?}
