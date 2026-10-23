//? if <=1.21.1 {
package ru.sanseddy.cctweakedunicodesupport.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dan200.computercraft.client.render.PocketItemRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.sanseddy.cctweakedunicodesupport.client.WideGlyphRenderer;

@Mixin(PocketItemRenderer.class)
public class PocketItemRendererMixin {
    @Inject(method = "renderItem", at = @At("HEAD"))
    private void cc_tweaked_unicode_support$beginGlyphPass(PoseStack transform, MultiBufferSource bufferSource, ItemStack stack, int light, CallbackInfo ci) {
        WideGlyphRenderer.begin();
    }

    @Inject(method = "renderItem", at = @At("RETURN"))
    private void cc_tweaked_unicode_support$flushGlyphPass(PoseStack transform, MultiBufferSource bufferSource, ItemStack stack, int light, CallbackInfo ci) {
        WideGlyphRenderer.flush(bufferSource);
    }
}
//?}
