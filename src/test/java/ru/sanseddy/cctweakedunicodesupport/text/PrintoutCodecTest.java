package ru.sanseddy.cctweakedunicodesupport.text;

import dan200.computercraft.shared.media.items.PrintoutData;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PrintoutCodecTest {
    @Test
    void ordinaryStringCodecPreservesAliasesAstralPairsAndContinuations() {
        var smile = new String(Character.toChars(0x1F642));
        var decodedSmile = Utf8.decode(byteString(smile));
        var text = CraftOsCharset.toCell(0x81) + decodedSmile + " ".repeat(PrintoutData.LINE_LENGTH - 2);
        var line = new PrintoutData.Line(text, "f".repeat(PrintoutData.LINE_LENGTH));
        var original = new PrintoutData("Unicode", Collections.nCopies(PrintoutData.LINES_PER_PAGE, line));

        var buffer = Unpooled.buffer();
        try {
            PrintoutData.STREAM_CODEC.encode(buffer, original);
            assertEquals(original, PrintoutData.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    private static String byteString(String text) {
        return new String(text.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1);
    }
}
