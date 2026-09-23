package ru.sanseddy.cctweakedunicodesupport.mixin;

import dan200.computercraft.api.filesystem.Mount;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.sanseddy.cctweakedunicodesupport.rom.ClasspathMount;
import ru.sanseddy.cctweakedunicodesupport.rom.RomOverlayMount;

/**
 * Overlays Unicode-aware ROM files on top of CC: Tweaked and any third-party mods (like CC: Graphics).
 * Hooks both getRomMount and createFileSystem (FileSystem.mount argument) so our overlay is guaranteed
 * to be on top regardless of third-party mixin priority or wrapping order.
 */
@Mixin(targets = "dan200.computercraft.core.computer.ComputerExecutor", remap = false, priority = 1500)
public abstract class ComputerExecutorMixin {
    @Unique
    private static final ClasspathMount cc_tweaked_unicode_support$overlay = new ClasspathMount("data/cc_tweaked_unicode_support/lua/rom")
        .addFile("apis/fs.lua")
        .addFile("apis/help.lua")
        .addFile("apis/textutils.lua")
        .addFile("apis/window.lua")
        .addFile("modules/main/cc/completion.lua")
        .addFile("modules/main/cc/image/nft.lua")
        .addFile("modules/main/cc/internal/error_printer.lua")
        .addFile("modules/main/cc/internal/menu.lua")
        .addFile("modules/main/cc/internal/syntax/lexer.lua")
        .addFile("modules/main/cc/pretty.lua")
        .addFile("modules/main/cc/strings.lua")
        .addFile("programs/advanced/multishell.lua")
        .addFile("programs/edit.lua")
        .addFile("programs/help.lua")
        .addFile("programs/rednet/chat.lua")
        .addFile("programs/shell.lua");

    @Unique
    private static boolean cc_tweaked_unicode_support$containsOverlay(Mount mount, Mount target) {
        if (mount == null) return false;
        if (mount == target) return true;
        if (mount instanceof RomOverlayMount rom) {
            return rom.getOverlay() == target
                || cc_tweaked_unicode_support$containsOverlay(rom.getOverlay(), target)
                || cc_tweaked_unicode_support$containsOverlay(rom.getBase(), target);
        }
        return false;
    }

    @Inject(method = "getRomMount", at = @At("RETURN"), cancellable = true)
    private void cc_tweaked_unicode_support$wrapRomMount(CallbackInfoReturnable<Mount> cir) {
        var original = cir.getReturnValue();
        if (original != null && !cc_tweaked_unicode_support$containsOverlay(original, cc_tweaked_unicode_support$overlay)) {
            cir.setReturnValue(new RomOverlayMount(original, cc_tweaked_unicode_support$overlay));
        }
    }

    @ModifyArg(
        method = "createFileSystem",
        at = @At(
            value = "INVOKE",
            target = "Ldan200/computercraft/core/filesystem/FileSystem;mount(Ljava/lang/String;Ljava/lang/String;Ldan200/computercraft/api/filesystem/Mount;)V"
        ),
        index = 2
    )
    private Mount cc_tweaked_unicode_support$wrapFinalRomMount(Mount mount) {
        if (mount != null && !cc_tweaked_unicode_support$containsOverlay(mount, cc_tweaked_unicode_support$overlay)) {
            return new RomOverlayMount(mount, cc_tweaked_unicode_support$overlay);
        }
        return mount;
    }
}

