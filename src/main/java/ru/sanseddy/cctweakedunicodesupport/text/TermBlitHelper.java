package ru.sanseddy.cctweakedunicodesupport.text;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.core.terminal.Terminal;

import java.nio.ByteBuffer;

public final class TermBlitHelper {
    private TermBlitHelper() {
    }

    public static void blit(
        Terminal terminal, ByteBuffer text, ByteBuffer textColour, ByteBuffer backgroundColour
    ) throws LuaException {
        var cells = Utf8.decode(Utf8.asByteString(text));
        var fgLen = textColour.remaining();
        var bgLen = backgroundColour.remaining();

        if (cells.length() != fgLen || cells.length() != bgLen) {
            throw new LuaException("Arguments must be the same length");
        }

        synchronized (terminal) {
            var x = terminal.getCursorX();
            var y = terminal.getCursorY();
            if (y >= 0 && y < terminal.getHeight()) {
                var line = terminal.getLine(y);
                var foreground = terminal.getTextColourLine(y);
                var background = terminal.getBackgroundColourLine(y);
                for (var i = 0; i < cells.length(); i++) {
                    line.setChar(x + i, cells.charAt(i));
                    char fgChar = (char) (textColour.get(textColour.position() + i) & 0xFF);
                    char bgChar = (char) (backgroundColour.get(backgroundColour.position() + i) & 0xFF);
                    foreground.setChar(x + i, fgChar);
                    background.setChar(x + i, bgChar);
                }
                terminal.setChanged();
            }

            terminal.setCursorPos(x + cells.length(), y);
        }
    }
}
