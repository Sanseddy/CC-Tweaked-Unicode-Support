package ru.sanseddy.cctweakedunicodesupport.mixin.compat.advancedperipherals;

import dan200.computercraft.api.lua.LuaTable;
import de.srendi.advancedperipherals.common.smartglasses.modules.overlay.OverlayObject;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = OverlayObject.class, remap = false)
public class APOverlayObjectMixin {
    @Inject(method = "setPropertiesFromTable", at = @At("HEAD"), remap = false)
    @SuppressWarnings("unchecked")
    private void cc_tweaked_unicode_support$aliasTextProperty(LuaTable<?, ?> table, CallbackInfo ci) {
        if (table.containsKey("text") && !table.containsKey("content")) {
            try {
                ((LuaTable<Object, Object>) table).put("content", table.get("text"));
            } catch (Throwable ignored) {
            }
        }
    }
}
