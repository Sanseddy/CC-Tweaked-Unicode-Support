//? if neoforge && <=1.21.1 {
package ru.sanseddy.cctweakedunicodesupport.mixin.compat.advancedperipherals;

import de.srendi.advancedperipherals.common.util.StringUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.sanseddy.cctweakedunicodesupport.text.Utf8;

@Mixin(value = StringUtil.class, remap = false)
public class APStringUtilMixin {
    @Inject(method = "byteStringToUTF8", at = @At("HEAD"), cancellable = true, remap = false)
    private static void cc_tweaked_unicode_support$safeByteStringToUTF8(String s, CallbackInfoReturnable<String> cir) {
        if (s == null) {
            cir.setReturnValue(null);
            return;
        }
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) > 255) {
                cir.setReturnValue(s);
                return;
            }
        }
        cir.setReturnValue(Utf8.decode(s));
    }
}
//?}
