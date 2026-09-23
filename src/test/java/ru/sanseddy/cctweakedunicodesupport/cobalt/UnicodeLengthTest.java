package ru.sanseddy.cctweakedunicodesupport.cobalt;

import org.junit.jupiter.api.Test;
import org.squiddev.cobalt.LuaState;
import org.squiddev.cobalt.LuaString;
import org.squiddev.cobalt.OperationHelper;
import org.squiddev.cobalt.UnicodeLength;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UnicodeLengthTest {

    @Test
    void countsAsciiCharacters() throws Throwable {
        var str = utf8("Hello, World!");
        assertEquals(13, UnicodeLength.length(str));
        assertEquals(13, OperationHelper.length(new LuaState(), str).checkInteger());
    }

    @Test
    void countsCyrillicCharacters() throws Throwable {
        var str = utf8("Привет, мир!");
        assertEquals(12, UnicodeLength.length(str));
        assertEquals(12, OperationHelper.length(new LuaState(), str).checkInteger());
    }

    @Test
    void countsEmojiCharacters() throws Throwable {
        // 4 emoji scalars (each 4 bytes = 16 bytes total)
        var str = utf8("😀🚀🎉🔥");
        assertEquals(4, UnicodeLength.length(str));
        assertEquals(4, OperationHelper.length(new LuaState(), str).checkInteger());
    }

    @Test
    void preservesByteLengthForMalformedUtf8() {
        assertEquals(3, UnicodeLength.length(LuaString.valueOf(new byte[]{(byte) 0xE2, 0x28, (byte) 0xA1})));
        assertEquals(2, UnicodeLength.length(LuaString.valueOf(new byte[]{(byte) 0xC0, (byte) 0xAF})));
        assertEquals(4, UnicodeLength.length(LuaString.valueOf(new byte[]{(byte) 0xF5, (byte) 0x80, (byte) 0x80, (byte) 0x80})));
        assertEquals(1, UnicodeLength.length(LuaString.valueOf(new byte[]{(byte) 0xFF})));
    }

    @Test
    void preservesByteLengthForBinaryStrings() throws Throwable {
        byte[] binary = new byte[]{0x00, 0x01, (byte) 0x80, (byte) 0xFF, 0x1B, 0x00, 0x42};
        var str = LuaString.valueOf(binary);
        assertEquals(binary.length, UnicodeLength.length(str));
        assertEquals(binary.length, OperationHelper.length(new LuaState(), str).checkInteger());
    }


    private static LuaString utf8(String value) {
        return LuaString.valueOf(value.getBytes(StandardCharsets.UTF_8));
    }
}
