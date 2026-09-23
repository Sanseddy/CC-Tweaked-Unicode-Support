package ru.sanseddy.cctweakedunicodesupport.rom;

import dan200.computercraft.api.filesystem.FileOperationException;
import dan200.computercraft.api.filesystem.Mount;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RomOverlayMountTest {

    @Test
    void overlayTakesPrecedenceOverBase() throws IOException {
        var base = new ClasspathMount("data/computercraft/lua/rom")
            .addFile("apis/fs.lua");
        var overlay = new ClasspathMount("data/computercraft/lua/rom")
            .addFile("apis/window.lua");

        var combined = new RomOverlayMount(base, overlay);

        assertTrue(combined.exists("apis/window.lua"));
        assertTrue(combined.exists("apis/fs.lua"));
        assertFalse(combined.exists("apis/nonexistent.lua"));

        assertTrue(combined.isDirectory("apis"));
        assertFalse(combined.isDirectory("apis/window.lua"));

        var list = new ArrayList<String>();
        combined.list("apis", list);
        assertTrue(list.contains("window.lua"));
        assertTrue(list.contains("fs.lua"));

        assertTrue(combined.getSize("apis/window.lua") > 0);
        try (var channel = combined.openForRead("apis/window.lua")) {
            var buf = ByteBuffer.allocate((int) channel.size());
            channel.read(buf);
            var content = new String(buf.array(), StandardCharsets.UTF_8);
            assertTrue(content.contains("setGraphicsMode"));
            assertTrue(content.contains("text_sub"));
        }
    }

    private static class MemoryMount implements Mount {
        private final List<String> files = new ArrayList<>();
        private final List<String> directories = new ArrayList<>();

        MemoryMount addFile(String f) {
            files.add(f);
            return this;
        }

        MemoryMount addDir(String d) {
            directories.add(d);
            return this;
        }

        @Override
        public boolean exists(String path) {
            return files.contains(path) || directories.contains(path);
        }

        @Override
        public boolean isDirectory(String path) {
            return directories.contains(path);
        }

        @Override
        public void list(String path, List<String> contents) throws IOException {
            if (!exists(path)) throw new FileOperationException(path, "No such file");
            if (!isDirectory(path)) throw new FileOperationException(path, "Not a directory");
            if (path.isEmpty()) {
                contents.addAll(files);
                contents.addAll(directories);
            }
        }

        @Override
        public long getSize(String path) throws IOException {
            if (!exists(path)) throw new FileOperationException(path, "No such file");
            return 42;
        }

        @Override
        public SeekableByteChannel openForRead(String path) throws IOException {
            if (!exists(path)) throw new FileOperationException(path, "No such file");
            if (isDirectory(path)) throw new FileOperationException(path, "Not a file");
            return null;
        }

        @Override
        public BasicFileAttributes getAttributes(String path) throws IOException {
            if (!exists(path)) throw new FileOperationException(path, "No such file");
            return null;
        }
    }

    @Test
    void overlayFileShadowsDirectoryInBase() throws IOException {
        var base = new MemoryMount().addDir("conflict");
        var overlay = new MemoryMount().addFile("conflict");
        var combined = new RomOverlayMount(base, overlay);

        assertTrue(combined.exists("conflict"));
        assertFalse(combined.isDirectory("conflict"), "Overlay file should shadow base directory");
        assertEquals(42, combined.getSize("conflict"));
    }

    @Test
    void overlayDirectoryShadowsFileInBase() throws IOException {
        var base = new MemoryMount().addFile("conflict");
        var overlay = new MemoryMount().addDir("conflict");
        var combined = new RomOverlayMount(base, overlay);

        assertTrue(combined.exists("conflict"));
        assertTrue(combined.isDirectory("conflict"), "Overlay directory should shadow base file");
    }

    @Test
    void listThrowsOnNonexistentOrFile() {
        var base = new MemoryMount().addFile("file.txt");
        var overlay = new MemoryMount();
        var combined = new RomOverlayMount(base, overlay);

        assertThrows(FileOperationException.class, () -> combined.list("missing", new ArrayList<>()));
        assertThrows(FileOperationException.class, () -> combined.list("file.txt", new ArrayList<>()));
    }

    @Test
    void listDeduplicatesCommonEntries() throws IOException {
        var base = new MemoryMount().addDir("").addFile("common.lua").addFile("base_only.lua");
        var overlay = new MemoryMount().addDir("").addFile("common.lua").addFile("overlay_only.lua");
        var combined = new RomOverlayMount(base, overlay);

        var list = new ArrayList<String>();
        combined.list("", list);

        assertEquals(1, Collections.frequency(list, "common.lua"), "common.lua must not be duplicated");
        assertTrue(list.contains("base_only.lua"));
        assertTrue(list.contains("overlay_only.lua"));
    }
}
