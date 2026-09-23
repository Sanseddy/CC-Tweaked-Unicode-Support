package ru.sanseddy.cctweakedunicodesupport.text;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.core.terminal.Terminal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.sanseddy.cctweakedunicodesupport.mixin.TermMethodsMixin;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class TermBlitTest {

    private Terminal terminal;

    @BeforeEach
    void setUp() {
        terminal = new Terminal(51, 19, true);
    }


    private static ByteBuffer toBuffer(String s) {
        return ByteBuffer.wrap(s.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void asciiWithMatchingColors() throws LuaException {
        TermBlitHelper.blit(terminal, toBuffer("Hello"), toBuffer("00000"), toBuffer("fffff"));
        assertEquals("Hello", terminal.getLine(0).toString().substring(0, 5));
        assertEquals("00000", terminal.getTextColourLine(0).toString().substring(0, 5));
        assertEquals("fffff", terminal.getBackgroundColourLine(0).toString().substring(0, 5));
    }

    @Test
    void cyrillicWithMatchingColors() throws LuaException {
        // "Привет" has 6 characters, but 12 bytes in UTF-8
        TermBlitHelper.blit(terminal, toBuffer("Привет"), toBuffer("123456"), toBuffer("abcdef"));
        var line = terminal.getLine(0).toString().substring(0, 6);
        assertEquals("Привет", line);
        assertEquals("123456", terminal.getTextColourLine(0).toString().substring(0, 6));
        assertEquals("abcdef", terminal.getBackgroundColourLine(0).toString().substring(0, 6));
    }

    @Test
    void emojiWithMatchingColors() throws LuaException {
        // "😀" is 1 terminal cell (represented by an astral alias)
        TermBlitHelper.blit(terminal, toBuffer("😀"), toBuffer("0"), toBuffer("f"));
        assertEquals(1, terminal.getCursorX());
        assertEquals("0", terminal.getTextColourLine(0).toString().substring(0, 1));
        assertEquals("f", terminal.getBackgroundColourLine(0).toString().substring(0, 1));
    }

    @Test
    void foregroundShorterThrowsException() {
        var ex = assertThrows(LuaException.class, () ->
            TermBlitHelper.blit(terminal, toBuffer("Привет"), toBuffer("12345"), toBuffer("123456"))
        );
        assertEquals("Arguments must be the same length", ex.getMessage());
    }

    @Test
    void foregroundLongerThrowsException() {
        var ex = assertThrows(LuaException.class, () ->
            TermBlitHelper.blit(terminal, toBuffer("Привет"), toBuffer("1234567"), toBuffer("123456"))
        );
        assertEquals("Arguments must be the same length", ex.getMessage());
    }

    @Test
    void backgroundShorterThrowsException() {
        var ex = assertThrows(LuaException.class, () ->
            TermBlitHelper.blit(terminal, toBuffer("Привет"), toBuffer("123456"), toBuffer("12345"))
        );
        assertEquals("Arguments must be the same length", ex.getMessage());
    }

    @Test
    void backgroundLongerThrowsException() {
        var ex = assertThrows(LuaException.class, () ->
            TermBlitHelper.blit(terminal, toBuffer("Привет"), toBuffer("123456"), toBuffer("1234567"))
        );
        assertEquals("Arguments must be the same length", ex.getMessage());
    }
}
