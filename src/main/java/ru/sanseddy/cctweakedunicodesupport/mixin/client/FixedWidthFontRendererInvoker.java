package ru.sanseddy.cctweakedunicodesupport.mixin.client;

import dan200.computercraft.client.render.text.FixedWidthFontRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(FixedWidthFontRenderer.class)
public interface FixedWidthFontRendererInvoker {
    //? if <=1.21.1 {
    @Invoker("drawChar")
    static void cc_tweaked_unicode_support$drawChar(
        FixedWidthFontRenderer.QuadEmitter emitter, float x, float y, int index, int colour, int light
    ) {
        throw new AssertionError("mixin did not apply");
    }
    //?} else {
    /*@Invoker("drawChar")
    static void cc_tweaked_unicode_support$drawChar(
        org.joml.Matrix4f matrix, com.mojang.blaze3d.vertex.VertexConsumer consumer, float x, float y, int index, int colour, int light
    ) {
        throw new AssertionError("mixin did not apply");
    }
    *///?}
}
