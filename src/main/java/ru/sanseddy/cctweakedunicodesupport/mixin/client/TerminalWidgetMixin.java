package ru.sanseddy.cctweakedunicodesupport.mixin.client;

import dan200.computercraft.core.input.UserComputerInput;
import dan200.computercraft.core.terminal.Terminal;
import dan200.computercraft.client.gui.widgets.TerminalWidget;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.sanseddy.cctweakedunicodesupport.client.WideGlyphRenderer;
import ru.sanseddy.cctweakedunicodesupport.network.WideCharPayload;
import ru.sanseddy.cctweakedunicodesupport.text.CraftOsCharset;

@Mixin(value = TerminalWidget.class, priority = 1500)
public class TerminalWidgetMixin {
    @Shadow
    @Final
    private Terminal terminal;

    @Shadow
    @Final
    private UserComputerInput computerInput;

    @Shadow
    @Final
    private int innerX;

    @Shadow
    @Final
    private int innerY;

    @Shadow
    private boolean inTermRegion(double mouseX, double mouseY) {
        throw new AssertionError("mixin did not apply");
    }

    @Unique
    private char cc_tweaked_unicode_support$pendingHighSurrogate;

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void cc_tweaked_unicode_support$typeWideChar(char ch, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (Character.isHighSurrogate(ch)) {
            cc_tweaked_unicode_support$pendingHighSurrogate = ch;
            cir.setReturnValue(true);
            return;
        }

        if (Character.isLowSurrogate(ch)) {
            var high = cc_tweaked_unicode_support$pendingHighSurrogate;
            cc_tweaked_unicode_support$pendingHighSurrogate = 0;
            if (high != 0) cc_tweaked_unicode_support$sendWideChar(Character.toCodePoint(high, ch), cir);
            return;
        }

        cc_tweaked_unicode_support$pendingHighSurrogate = 0;
        if (CraftOsCharset.isInternalMarker(ch)) {
            cir.setReturnValue(true);
            return;
        }
        if (CraftOsCharset.isLegacy(ch)) return;
        cc_tweaked_unicode_support$sendWideChar(ch, cir);
    }

    @Unique
    private static void cc_tweaked_unicode_support$sendWideChar(int codepoint, CallbackInfoReturnable<Boolean> cir) {
        var player = Minecraft.getInstance().player;
        if (player == null) return;

        PacketDistributor.sendToServer(new WideCharPayload(player.containerMenu.containerId, codepoint));
        cir.setReturnValue(true);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void cc_tweaked_unicode_support$monospaceMouseClick(
        double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir
    ) {
        if (cir.isCancelled() || !inTermRegion(mouseX, mouseY)) return;
        var row = (int) ((mouseY - innerY) / dan200.computercraft.client.render.text.FixedWidthFontRenderer.FONT_HEIGHT);
        var column = WideGlyphRenderer.columnAt(terminal.getLine(row), mouseX - innerX);
        if (column < 0) return;
        computerInput.mouseClick(button + 1, column + 1, row + 1);
        cir.setReturnValue(true);
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void cc_tweaked_unicode_support$monospaceMouseRelease(
        double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir
    ) {
        if (cir.isCancelled() || !inTermRegion(mouseX, mouseY)) return;
        var row = (int) ((mouseY - innerY) / dan200.computercraft.client.render.text.FixedWidthFontRenderer.FONT_HEIGHT);
        var column = WideGlyphRenderer.columnAt(terminal.getLine(row), mouseX - innerX);
        if (column < 0) return;
        computerInput.mouseUp(button + 1, column + 1, row + 1);
        cir.setReturnValue(true);
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void cc_tweaked_unicode_support$monospaceMouseDrag(
        double mouseX, double mouseY, int button, double dragX, double dragY,
        CallbackInfoReturnable<Boolean> cir
    ) {
        if (cir.isCancelled() || !inTermRegion(mouseX, mouseY)) return;
        var row = (int) ((mouseY - innerY) / dan200.computercraft.client.render.text.FixedWidthFontRenderer.FONT_HEIGHT);
        var column = WideGlyphRenderer.columnAt(terminal.getLine(row), mouseX - innerX);
        if (column < 0) return;
        computerInput.mouseDrag(button + 1, column + 1, row + 1);
        cir.setReturnValue(true);
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void cc_tweaked_unicode_support$monospaceMouseScroll(
        double mouseX, double mouseY, double deltaX, double deltaY,
        CallbackInfoReturnable<Boolean> cir
    ) {
        if (cir.isCancelled() || !inTermRegion(mouseX, mouseY) || deltaY == 0) return;
        var row = (int) ((mouseY - innerY) / dan200.computercraft.client.render.text.FixedWidthFontRenderer.FONT_HEIGHT);
        var column = WideGlyphRenderer.columnAt(terminal.getLine(row), mouseX - innerX);
        if (column < 0) return;
        computerInput.mouseScroll(deltaY < 0 ? 1 : -1, column + 1, row + 1);
        cir.setReturnValue(true);
    }
}
