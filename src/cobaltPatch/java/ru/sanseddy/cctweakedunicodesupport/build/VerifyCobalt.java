package ru.sanseddy.cctweakedunicodesupport.build;

import org.squiddev.cobalt.LuaState;
import org.squiddev.cobalt.LuaString;
import org.squiddev.cobalt.OperationHelper;

import java.nio.charset.StandardCharsets;

public final class VerifyCobalt {
    private VerifyCobalt() {
    }

    public static void main(String[] args) throws Throwable {
        var text = LuaString.valueOf("Привет 1".getBytes(StandardCharsets.UTF_8));
        var length = OperationHelper.length(new LuaState(), text).checkInteger();
        if (length != 8) throw new IllegalStateException("Patched # returned " + length + " instead of 8");

        var binary = LuaString.valueOf(new byte[]{(byte) 0xE2, 0x28, (byte) 0xA1});
        var binaryLength = OperationHelper.length(new LuaState(), binary).checkInteger();
        if (binaryLength != 3) throw new IllegalStateException("Patched # changed malformed binary length to " + binaryLength);
    }
}
