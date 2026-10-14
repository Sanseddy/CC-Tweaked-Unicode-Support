package ru.sanseddy.cctweakedunicodesupport.mixin;

import dan200.computercraft.core.terminal.Terminal;
import dan200.computercraft.core.util.Colour;
import dan200.computercraft.shared.computer.terminal.NetworkedTerminal;
import dan200.computercraft.shared.computer.terminal.TerminalState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import ru.sanseddy.cctweakedunicodesupport.text.CraftOsCharset;
import ru.sanseddy.cctweakedunicodesupport.text.Utf8;

import java.io.ByteArrayOutputStream;

@Mixin(NetworkedTerminal.class)
public class NetworkedTerminalMixin {
    private static final String BASE_16 = "0123456789abcdef";

    /**
     * @author Antigravity
     * @reason Encode terminal lines to UTF-8 while preserving method return flow for CC: Graphics RETURN injects.
     */
    @Overwrite
    public synchronized TerminalState write() {
        var terminal = (Terminal) (Object) this;
        var width = terminal.getWidth();
        var height = terminal.getHeight();

        var contents = new ByteArrayOutputStream(width * height * 2 + 48);
        for (var y = 0; y < height; y++) {
            var text = terminal.getLine(y);
            var textColour = terminal.getTextColourLine(y);
            var backColour = terminal.getBackgroundColourLine(y);

            for (var x = 0; x < width; x++) {
                var ch = text.charAt(x);
                var codepoint = CraftOsCharset.cellToCodepoint(ch);
                Utf8.encode(codepoint, contents);
            }

            for (var x = 0; x < width; x++) {
                contents.write(Terminal.getColour(backColour.charAt(x), Colour.BLACK) << 4
                    | Terminal.getColour(textColour.charAt(x), Colour.WHITE));
            }
        }

        for (var i = 0; i < 16; i++) {
            for (var channel : terminal.getPalette().getColour(i)) contents.write((int) (channel * 255.0) & 0xFF);
        }

        return TerminalStateAccessor.cc_tweaked_unicode_support$create(
            terminal.isColour(), width, height,
            terminal.getCursorX(), terminal.getCursorY(), terminal.getCursorBlink(),
            terminal.getTextColour(), terminal.getBackgroundColour(),
            contents.toByteArray()
        );
    }

    /**
     * @author Antigravity
     * @reason Decode terminal lines from UTF-8 while preserving method tail flow for CC: Graphics TAIL injects.
     */
    @Overwrite
    public synchronized void read(TerminalState state) {
        var accessor = (TerminalStateAccessor) state;
        var terminal = (Terminal) (Object) this;

        terminal.resize(accessor.cc_tweaked_unicode_support$width(), accessor.cc_tweaked_unicode_support$height());
        terminal.setCursorPos(accessor.cc_tweaked_unicode_support$cursorX(), accessor.cc_tweaked_unicode_support$cursorY());
        terminal.setCursorBlink(accessor.cc_tweaked_unicode_support$cursorBlink());
        terminal.setTextColour(accessor.cc_tweaked_unicode_support$cursorFgColour());
        terminal.setBackgroundColour(accessor.cc_tweaked_unicode_support$cursorBgColour());

        var width = terminal.getWidth();
        var height = terminal.getHeight();
        var contents = accessor.cc_tweaked_unicode_support$contents();
        var row = new char[width];
        var idx = 0;

        for (var y = 0; y < height; y++) {
            idx = Utf8.readCells(contents, idx, contents.length, row, width);

            var text = terminal.getLine(y);
            var textColour = terminal.getTextColourLine(y);
            var backColour = terminal.getBackgroundColourLine(y);
            for (var x = 0; x < width; x++) text.setChar(x, row[x]);
            for (var x = 0; x < width && idx < contents.length; x++) {
                var colour = contents[idx++];
                backColour.setChar(x, BASE_16.charAt(colour >> 4 & 15));
                textColour.setChar(x, BASE_16.charAt(colour & 15));
            }
        }

        for (var i = 0; i < 16 && idx + 2 < contents.length; i++) {
            var r = (contents[idx++] & 0xFF) / 255.0;
            var g = (contents[idx++] & 0xFF) / 255.0;
            var b = (contents[idx++] & 0xFF) / 255.0;
            terminal.getPalette().setColour(i, r, g, b);
        }

        terminal.setChanged();
    }
}
