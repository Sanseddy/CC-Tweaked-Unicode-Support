package ru.sanseddy.cctweakedunicodesupport.mixin.compat.hudglasses;

import dan200.computercraft.api.lua.Coerced;
import dan200.computercraft.api.lua.LuaException;
import org.semakol.hudglassescc.hud.HudBuffer;
import org.semakol.hudglassescc.peripheral.HudPeripheral;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.sanseddy.cctweakedunicodesupport.compat.HudGlassesCompat;
import ru.sanseddy.cctweakedunicodesupport.text.Utf8;

import java.nio.ByteBuffer;

@Mixin(value = HudPeripheral.class, remap = false)
public abstract class HudPeripheralMixin {
    @Shadow(remap = false)
    protected abstract HudBuffer buf();

    @Inject(method = "write", at = @At("HEAD"), cancellable = true, remap = false)
    private void cc_tweaked_unicode_support$hudWriteDecoded(Coerced<String> textArg, CallbackInfo ci) {
        if (textArg == null) return;
        var raw = textArg.value();
        if (raw == null || raw.isEmpty()) return;
        var text = HudGlassesCompat.decodeTextForHud(raw);
        buf().write(text);
        ci.cancel();
    }

    @Inject(method = "blit", at = @At("HEAD"), cancellable = true, remap = false)
    private void cc_tweaked_unicode_support$hudBlitDecoded(
        ByteBuffer text, ByteBuffer textColour, ByteBuffer backgroundColour, CallbackInfo ci
    ) throws LuaException {
        if (text == null || textColour == null || backgroundColour == null) return;
        var rawText = Utf8.asByteString(text);
        var cells = HudGlassesCompat.decodeTextForHud(rawText);
        var fgLen = textColour.remaining();
        var bgLen = backgroundColour.remaining();
        if (cells.length() != fgLen || cells.length() != bgLen) {
            throw new LuaException("Arguments must be the same length");
        }
        var fg = Utf8.asByteString(textColour);
        var bg = Utf8.asByteString(backgroundColour);
        buf().blit(cells, fg, bg);
        ci.cancel();
    }
}
