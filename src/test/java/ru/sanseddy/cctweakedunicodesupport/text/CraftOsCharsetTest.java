package ru.sanseddy.cctweakedunicodesupport.text;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CraftOsCharsetTest {

    @TempDir
    Path tempDir;

    private Path testConfig;

    @BeforeEach
    void setUp() {
        testConfig = tempDir.resolve("cc_tweaked_unicode_astral.dat");
        CraftOsCharset.setPersistencePathForTesting(testConfig);
        CraftOsCharset.resetAstralAliasesForTesting();
    }

    @AfterEach
    void tearDown() {
        CraftOsCharset.resetAstralAliasesForTesting();
    }

    @Test
    void puaCharactersAreNotInternalMarkers() {
        // BMP Private Use Area: U+E000 to U+F8FF
        for (int codepoint = 0xE000; codepoint <= 0xF8FF; codepoint += 64) {
            assertFalse(CraftOsCharset.isInternalMarker(codepoint),
                "PUA codepoint U+" + Integer.toHexString(codepoint) + " should not be an internal marker");
            assertEquals(codepoint, CraftOsCharset.cellToCodepoint((char) codepoint),
                "PUA codepoint should map to itself");
        }
    }

    @Test
    void astralAliasStabilityBeyondMaxCount() {
        int max = CraftOsCharset.MAX_ASTRAL_ALIASES;
        char[] initialCells = new char[max];

        // Allocate up to max
        for (int i = 0; i < max; i++) {
            int codepoint = 0x10000 + i;
            char cell = CraftOsCharset.toAstralCell(codepoint);
            assertNotEquals('\uFFFD', cell);
            initialCells[i] = cell;
            assertEquals(codepoint, CraftOsCharset.cellToCodepoint(cell));
        }

        // Try allocating 100 more beyond limit
        for (int i = 0; i < 100; i++) {
            int extraCodepoint = 0x20000 + i;
            char fallback = CraftOsCharset.toAstralCell(extraCodepoint);
            assertEquals('\uFFFD', fallback, "Overflow astral codepoint should return fallback replacement char");
        }

        // Verify ALL initial mappings are completely unchanged (NO silent reuse!)
        for (int i = 0; i < max; i++) {
            int expectedCodepoint = 0x10000 + i;
            char cell = initialCells[i];
            assertEquals(expectedCodepoint, CraftOsCharset.cellToCodepoint(cell),
                "Initial cell mapping for slot " + i + " must not change after overflow");
        }
    }

    @Test
    void persistenceSaveAndRestore() {
        int[] testCodepoints = {0x1F600, 0x1F680, 0x1F34E, 0x1F44D};
        char[] cells = new char[testCodepoints.length];

        for (int i = 0; i < testCodepoints.length; i++) {
            cells[i] = CraftOsCharset.toAstralCell(testCodepoints[i]);
        }

        CraftOsCharset.flushPersistence();
        assertTrue(Files.exists(testConfig), "Persistence file must exist after flush");

        // Clear memory
        CraftOsCharset.resetAstralAliasesForTesting();
        assertEquals(-1, CraftOsCharset.fromAstralCell(cells[0]));

        // Reload
        CraftOsCharset.loadPersistedAstralAliases();

        // Verify bi-directional consistency
        for (int i = 0; i < testCodepoints.length; i++) {
            assertEquals(cells[i], CraftOsCharset.toAstralCell(testCodepoints[i]),
                "Codepoint to cell mapping should be restored");
            assertEquals(testCodepoints[i], CraftOsCharset.cellToCodepoint(cells[i]),
                "Cell to codepoint mapping should be restored");
        }
    }

    @Test
    void persistenceHandlesCorruptedFileGracefully() throws IOException {
        Files.write(testConfig, new byte[]{0x43, 0x43, 0x55, 0x53, 0x00, 0x00, 0x00, 0x01, 0x7F}); // invalid truncated

        assertDoesNotThrow(() -> CraftOsCharset.loadPersistedAstralAliases());
        // Verify state is clean
        assertEquals(0, CraftOsCharset.fromAstralCell('\uD800') == -1 ? 0 : 1);
    }
}
