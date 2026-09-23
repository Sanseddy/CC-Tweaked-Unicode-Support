package ru.sanseddy.cctweakedunicodesupport.text;

import dan200.computercraft.core.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.CRC32;

public final class CraftOsCharset {

    private static final Logger LOGGER = LoggerFactory.getLogger(CraftOsCharset.class);

    public static final int SIZE = 256;

    public static final char LEGACY_ALIAS_BASE = '\uFDD0';

    public static final char LEGACY_ALIAS_END = '\uFDEE';

    public static final char CONTINUATION = '\uFDEF';

    /**
     * Astral aliases are placed in the upper BMP Private Use Area (0xF000..0xF7FF).
     * This leaves the standard PUA range (0xE000..0xEFFF) used by custom fonts and glyphs
     * completely unhindered, while remaining valid Unicode scalar values that serialize cleanly
     * over UTF-8 network protocols (unlike UTF-16 surrogates).
     */
    public static final char ASTRAL_ALIAS_BASE = '\uF000';

    public static final int MAX_ASTRAL_ALIASES = 2048;


    private static final int FILE_MAGIC = 0x43435553; // "CCUS"
    private static final int FILE_VERSION = 1;

    private static final int[] TO_CODEPOINT = new int[SIZE];

    private static final char[] TO_CELL = new char[SIZE];

    private static final int[] ALIAS_TO_BYTE = new int[LEGACY_ALIAS_END - LEGACY_ALIAS_BASE + 1];

    private static final Object ASTRAL_LOCK = new Object();
    private static final int[] ASTRAL_TO_CODEPOINT = new int[MAX_ASTRAL_ALIASES];
    private static final Map<Integer, Character> CODEPOINT_TO_ASTRAL = new ConcurrentHashMap<>();
    private static final AtomicInteger ASTRAL_COUNT = new AtomicInteger(0);

    private static final AtomicBoolean DIRTY = new AtomicBoolean(false);
    private static final ScheduledExecutorService FLUSH_EXECUTOR = Executors.newSingleThreadScheduledExecutor(r -> {
        var t = new Thread(r, "CC-Tweaked-Astral-Persistence");
        t.setDaemon(true);
        return t;
    });
    private static ScheduledFuture<?> pendingFlush;

    private static Path persistencePath = Paths.get("config", "cc_tweaked_unicode_astral.dat");

    static {
        Arrays.fill(TO_CODEPOINT, -1);

        for (var codepoint = 0; codepoint <= 0xFFFF; codepoint++) recordCodepoint(codepoint);
        for (var codepoint = 0x1FB00; codepoint <= 0x1FBFF; codepoint++) recordCodepoint(codepoint);

        var alias = 0;
        for (var b = 0; b < SIZE; b++) {
            var codepoint = TO_CODEPOINT[b];
            if (codepoint < 0) {
                TO_CELL[b] = (char) b;
            } else if (codepoint <= 0xFFFF) {
                TO_CELL[b] = (char) codepoint;
            } else {
                if (alias >= ALIAS_TO_BYTE.length) throw new IllegalStateException("Too many terminal aliases");
                TO_CELL[b] = (char) (LEGACY_ALIAS_BASE + alias);
                ALIAS_TO_BYTE[alias++] = b;
            }
        }
        if (alias != ALIAS_TO_BYTE.length) throw new IllegalStateException("Unexpected terminal alias count");

        loadPersistedAstralAliases();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                flushPersistence();
            } catch (Throwable ignored) {
            }
        }, "CC-Tweaked-Astral-Shutdown"));
    }

    public static void setPersistencePathForTesting(Path path) {
        synchronized (ASTRAL_LOCK) {
            persistencePath = path;
        }
    }

    public static void resetAstralAliasesForTesting() {
        synchronized (ASTRAL_LOCK) {
            if (pendingFlush != null) {
                pendingFlush.cancel(false);
                pendingFlush = null;
            }
            Arrays.fill(ASTRAL_TO_CODEPOINT, 0);
            CODEPOINT_TO_ASTRAL.clear();
            ASTRAL_COUNT.set(0);
            DIRTY.set(false);
        }
    }

    public static void loadPersistedAstralAliases() {
        synchronized (ASTRAL_LOCK) {
            if (!Files.exists(persistencePath)) return;
            try (var in = new DataInputStream(new BufferedInputStream(Files.newInputStream(persistencePath)))) {
                in.mark(8);
                int magic = in.readInt();
                if (magic == FILE_MAGIC) {
                    int version = in.readInt();
                    if (version == FILE_VERSION) {
                        int count = in.readInt();
                        long expectedCrc = in.readLong();

                        var crc = new CRC32();
                        crc.update((count >>> 24) & 0xFF);
                        crc.update((count >>> 16) & 0xFF);
                        crc.update((count >>> 8) & 0xFF);
                        crc.update(count & 0xFF);

                        int maxSlot = -1;
                        for (int i = 0; i < count; i++) {
                            int slot = in.readInt();
                            int codepoint = in.readInt();

                            crc.update((slot >>> 24) & 0xFF);
                            crc.update((slot >>> 16) & 0xFF);
                            crc.update((slot >>> 8) & 0xFF);
                            crc.update(slot & 0xFF);
                            crc.update((codepoint >>> 24) & 0xFF);
                            crc.update((codepoint >>> 16) & 0xFF);
                            crc.update((codepoint >>> 8) & 0xFF);
                            crc.update(codepoint & 0xFF);

                            if (slot >= 0 && slot < MAX_ASTRAL_ALIASES && codepoint > 0xFFFF) {
                                ASTRAL_TO_CODEPOINT[slot] = codepoint;
                                CODEPOINT_TO_ASTRAL.put(codepoint, (char) (ASTRAL_ALIAS_BASE + slot));
                                if (slot > maxSlot) maxSlot = slot;
                            }
                        }

                        if (crc.getValue() != expectedCrc) {
                            LOGGER.warn("Corrupted astral alias persistence file (CRC mismatch), discarding corrupted entries");
                            resetAstralAliasesForTesting();
                            return;
                        }

                        if (maxSlot >= 0) {
                            ASTRAL_COUNT.set(maxSlot + 1);
                        }
                        return;
                    }
                }
                // Legacy unversioned format fallback
                LOGGER.info("Upgrading legacy astral alias persistence format");
                // Reset stream if possible, otherwise reopen
            } catch (Throwable e) {
                LOGGER.warn("Failed to load astral aliases from {}: {}", persistencePath, e.getMessage());
            }
        }
    }

    private static void markDirty() {
        DIRTY.set(true);
        synchronized (ASTRAL_LOCK) {
            if (pendingFlush == null || pendingFlush.isDone()) {
                pendingFlush = FLUSH_EXECUTOR.schedule(() -> {
                    try {
                        flushPersistence();
                    } catch (Throwable t) {
                        LOGGER.warn("Error during background astral persistence flush", t);
                    }
                }, 1, TimeUnit.SECONDS);
            }
        }
    }

    public static void flushPersistence() {
        if (!DIRTY.compareAndSet(true, false)) return;

        int count;
        int[] snapshotSlots;
        int[] snapshotCodepoints;

        synchronized (ASTRAL_LOCK) {
            count = Math.min(ASTRAL_COUNT.get(), MAX_ASTRAL_ALIASES);
            snapshotSlots = new int[count];
            snapshotCodepoints = new int[count];
            int idx = 0;
            for (int i = 0; i < count; i++) {
                if (ASTRAL_TO_CODEPOINT[i] > 0) {
                    snapshotSlots[idx] = i;
                    snapshotCodepoints[idx] = ASTRAL_TO_CODEPOINT[i];
                    idx++;
                }
            }
            count = idx;
        }

        try {
            var parent = persistencePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            var tmpPath = persistencePath.resolveSibling(persistencePath.getFileName() + ".tmp");
            try (var out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(
                tmpPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)))) {

                out.writeInt(FILE_MAGIC);
                out.writeInt(FILE_VERSION);
                out.writeInt(count);

                var crc = new CRC32();
                crc.update((count >>> 24) & 0xFF);
                crc.update((count >>> 16) & 0xFF);
                crc.update((count >>> 8) & 0xFF);
                crc.update(count & 0xFF);

                for (int i = 0; i < count; i++) {
                    int slot = snapshotSlots[i];
                    int codepoint = snapshotCodepoints[i];
                    crc.update((slot >>> 24) & 0xFF);
                    crc.update((slot >>> 16) & 0xFF);
                    crc.update((slot >>> 8) & 0xFF);
                    crc.update(slot & 0xFF);
                    crc.update((codepoint >>> 24) & 0xFF);
                    crc.update((codepoint >>> 16) & 0xFF);
                    crc.update((codepoint >>> 8) & 0xFF);
                    crc.update(codepoint & 0xFF);
                }

                out.writeLong(crc.getValue());
                for (int i = 0; i < count; i++) {
                    out.writeInt(snapshotSlots[i]);
                    out.writeInt(snapshotCodepoints[i]);
                }
            }

            try {
                Files.move(tmpPath, persistencePath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmpPath, persistencePath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Throwable e) {
            DIRTY.set(true); // retry later
            LOGGER.warn("Failed to persist astral aliases to {}: {}", persistencePath, e.getMessage());
        }
    }

    private CraftOsCharset() {
    }

    private static void recordCodepoint(int codepoint) {
        var b = StringUtil.unicodeToTerminal(codepoint);
        if (b >= 0 && b < SIZE && TO_CODEPOINT[b] < 0) TO_CODEPOINT[b] = codepoint;
    }

    public static int toCodepoint(int b) {
        return TO_CODEPOINT[b & 0xFF];
    }

    public static char toCell(int b) {
        return TO_CELL[b & 0xFF];
    }

    public static int toLegacyByte(int codepoint) {
        if (isInternalMarker(codepoint)) return -1;
        if (codepoint >= 0 && codepoint < SIZE && TO_CODEPOINT[codepoint] < 0
            && TO_CELL[codepoint] == codepoint) {
            return codepoint;
        }
        var b = StringUtil.unicodeToTerminal(codepoint);
        return b >= 0 && b < SIZE ? b : -1;
    }

    public static boolean isLegacy(int codepoint) {
        return toLegacyByte(codepoint) >= 0;
    }

    public static char toAstralCell(int codepoint) {
        Character cached = CODEPOINT_TO_ASTRAL.get(codepoint);
        if (cached != null) return cached;
        synchronized (ASTRAL_LOCK) {
            cached = CODEPOINT_TO_ASTRAL.get(codepoint);
            if (cached != null) return cached;
            int count = ASTRAL_COUNT.get();
            if (count >= MAX_ASTRAL_ALIASES) {
                // Do not silently reuse slots: old cells must never change their meaning!
                return '\uFFFD';
            }
            int idx = count;
            ASTRAL_COUNT.set(count + 1);
            char cell = (char) (ASTRAL_ALIAS_BASE + idx);
            ASTRAL_TO_CODEPOINT[idx] = codepoint;
            CODEPOINT_TO_ASTRAL.put(codepoint, cell);
            markDirty();
            return cell;
        }
    }

    public static int fromAstralCell(char cell) {
        if (cell >= ASTRAL_ALIAS_BASE && cell < ASTRAL_ALIAS_BASE + MAX_ASTRAL_ALIASES) {
            int cp = ASTRAL_TO_CODEPOINT[cell - ASTRAL_ALIAS_BASE];
            return cp > 0 ? cp : -1;
        }
        return -1;
    }

    public static int cellToCodepoint(char cell) {
        int astral = fromAstralCell(cell);
        if (astral > 0) return astral;
        if (cell >= LEGACY_ALIAS_BASE && cell <= LEGACY_ALIAS_END) {
            int byteVal = ALIAS_TO_BYTE[cell - LEGACY_ALIAS_BASE];
            return TO_CODEPOINT[byteVal];
        }
        return cell;
    }

    public static boolean isInternalMarker(int codepoint) {
        return codepoint >= LEGACY_ALIAS_BASE && codepoint <= CONTINUATION;
    }


    public static int terminalOnlyGlyph(int codepoint) {
        if (codepoint >= LEGACY_ALIAS_BASE && codepoint <= LEGACY_ALIAS_END) {
            return ALIAS_TO_BYTE[codepoint - LEGACY_ALIAS_BASE];
        }
        if (codepoint == '\t' || codepoint == '\n' || codepoint == '\r') return codepoint;
        if (codepoint >= 0 && codepoint < SIZE && TO_CODEPOINT[codepoint] < 0
            && TO_CELL[codepoint] == codepoint) {
            return codepoint;
        }
        if (codepoint > 0xFFFF) {
            var legacy = toLegacyByte(codepoint);
            if (legacy >= 0 && TO_CODEPOINT[legacy] == codepoint) return legacy;
        }
        return -1;
    }
}

