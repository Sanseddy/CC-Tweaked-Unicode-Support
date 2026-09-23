package ru.sanseddy.cctweakedunicodesupport.mixin;

import dan200.computercraft.api.lua.Coerced;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.core.apis.TermMethods;
import dan200.computercraft.core.terminal.Terminal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.sanseddy.cctweakedunicodesupport.text.Utf8;

import java.nio.ByteBuffer;

@Mixin(TermMethods.class)
public abstract class TermMethodsMixin {
    @Shadow
    public abstract Terminal getTerminal() throws LuaException;

    @Inject(method = "write", at = @At("HEAD"), cancellable = true)
    private void cc_tweaked_unicode_support$writeDecoded(Coerced<String> textArg, CallbackInfo ci) throws LuaException {
        var text = Utf8.decode(textArg.value());
        var terminal = getTerminal();
        synchronized (terminal) {
            terminal.write(text);
            terminal.setCursorPos(terminal.getCursorX() + text.length(), terminal.getCursorY());
        }
        ci.cancel();
    }

    @Inject(method = "blit", at = @At("HEAD"), cancellable = true)
    private void cc_tweaked_unicode_support$blitDecoded(
        ByteBuffer text, ByteBuffer textColour, ByteBuffer backgroundColour, CallbackInfo ci
    ) throws LuaException {
        ru.sanseddy.cctweakedunicodesupport.text.TermBlitHelper.blit(getTerminal(), text, textColour, backgroundColour);
        ci.cancel();
    }



}
