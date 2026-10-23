//? if <=1.21.1 {
package ru.sanseddy.cctweakedunicodesupport.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dan200.computercraft.client.render.CustomLecternRenderer;
import dan200.computercraft.shared.lectern.CustomLecternBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.sanseddy.cctweakedunicodesupport.client.WideGlyphRenderer;

@Mixin(CustomLecternRenderer.class)
public class CustomLecternRendererMixin {
    private static final String RENDER = "render(Ldan200/computercraft/shared/lectern/CustomLecternBlockEntity;F"
        + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V";

    @Inject(method = RENDER, at = @At("HEAD"))
    private void cc_tweaked_unicode_support$beginGlyphPass(
        CustomLecternBlockEntity lectern, float partialTick, PoseStack poseStack,
        MultiBufferSource buffer, int packedLight, int packedOverlay, CallbackInfo ci
    ) {
        WideGlyphRenderer.begin();
    }

    @Inject(method = RENDER, at = @At("RETURN"))
    private void cc_tweaked_unicode_support$flushGlyphPass(
        CustomLecternBlockEntity lectern, float partialTick, PoseStack poseStack,
        MultiBufferSource buffer, int packedLight, int packedOverlay, CallbackInfo ci
    ) {
        WideGlyphRenderer.flush(buffer);
    }
}
//?}
