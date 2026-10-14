package ru.sanseddy.cctweakedunicodesupport.mixin.client.compat.hudglasses;

import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;
import org.semakol.hudglassescc.client.HudOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.sanseddy.cctweakedunicodesupport.text.CraftOsCharset;

@Mixin(value = HudOverlay.class, remap = false)
public class HudOverlayMixin {
    @Shadow(remap = false)
    private static Style HUD_STYLE;

    @Inject(method = "glyphSequence", at = @At("HEAD"), cancellable = true, remap = false)
    private static void cc_tweaked_unicode_support$astralGlyphSequence(char c, CallbackInfoReturnable<FormattedCharSequence> cir) {
        int codepoint = CraftOsCharset.cellToCodepoint(c);
        if (codepoint > 0xFFFF) {
            String str = new String(Character.toChars(codepoint));
            cir.setReturnValue(Component.literal(str).withStyle(HUD_STYLE).getVisualOrderText());
        }
    }

    @Redirect(
        method = "drawGlyphLegacy",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Font;drawInBatch(Lnet/minecraft/util/FormattedCharSequence;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)I"
        ),
        remap = false
    )
    private static int cc_tweaked_unicode_support$centerHudGlyph(
        Font font, FormattedCharSequence text, float x, float y, int color, boolean dropShadow,
        Matrix4f matrix, MultiBufferSource buffer, Font.DisplayMode displayMode, int backgroundColor, int packedLight
    ) {
        int advance = font.width(text);
        float scaleX = advance > 6 ? 6.0f / advance : 1.0f;
        float offsetX = (6.0f - advance * scaleX) / 2.0f;
        if (scaleX != 1.0f) {
            var scaledMatrix = new Matrix4f(matrix).translate(x + offsetX, y, 0.0f).scale(scaleX, 1.0f, 1.0f);
            return font.drawInBatch(text, 0.0f, 0.0f, color, dropShadow, scaledMatrix, buffer, displayMode, backgroundColor, packedLight);
        }
        return font.drawInBatch(text, x + offsetX, y, color, dropShadow, matrix, buffer, displayMode, backgroundColor, packedLight);
    }
}
