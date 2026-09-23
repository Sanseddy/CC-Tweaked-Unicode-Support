package ru.sanseddy.cctweakedunicodesupport.rom;

import dan200.computercraft.api.filesystem.FileOperationException;
import dan200.computercraft.api.filesystem.Mount;
import dan200.computercraft.api.filesystem.MountConstants;
import dan200.computercraft.core.apis.handles.ArrayByteChannel;

import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A read-only mount backed by files on the classpath.
 */
public class ClasspathMount implements Mount {
    private final String basePath;
    private final Set<String> files = new HashSet<>();
    private final Set<String> directories = new HashSet<>();
    private final Map<String, byte[]> contentCache = new ConcurrentHashMap<>();

    public ClasspathMount(String basePath) {
        this.basePath = basePath.endsWith("/") ? basePath.substring(0, basePath.length() - 1) : basePath;
        directories.add("");
    }

    public ClasspathMount addFile(String path) {
        files.add(path);
        var parts = path.split("/");
        var dir = new StringBuilder();
        for (var i = 0; i < parts.length - 1; i++) {
            if (i > 0) dir.append("/");
            dir.append(parts[i]);
            directories.add(dir.toString());
        }
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
        if (!directories.contains(path)) {
            throw new FileOperationException(path, files.contains(path) ? MountConstants.NOT_A_DIRECTORY : MountConstants.NO_SUCH_FILE);
        }
        var prefix = path.isEmpty() ? "" : path + "/";
        var seen = new HashSet<String>();
        for (var file : files) {
            if (file.startsWith(prefix)) {
                var rest = file.substring(prefix.length());
                var slash = rest.indexOf('/');
                var entry = slash < 0 ? rest : rest.substring(0, slash);
                if (!entry.isEmpty() && seen.add(entry)) {
                    contents.add(entry);
                }
            }
        }
        for (var dir : directories) {
            if (dir.startsWith(prefix) && !dir.equals(path)) {
                var rest = dir.substring(prefix.length());
                var slash = rest.indexOf('/');
                var entry = slash < 0 ? rest : rest.substring(0, slash);
                if (!entry.isEmpty() && seen.add(entry)) {
                    contents.add(entry);
                }
            }
        }
    }

    @Override
    public long getSize(String path) throws IOException {
        if (directories.contains(path)) return 0;
        if (!files.contains(path)) throw new FileOperationException(path, MountConstants.NO_SUCH_FILE);
        return read(path).length;
    }

    @Override
    public SeekableByteChannel openForRead(String path) throws IOException {
        if (!files.contains(path)) {
            throw new FileOperationException(path, directories.contains(path) ? MountConstants.NOT_A_FILE : MountConstants.NO_SUCH_FILE);
        }
        return new ArrayByteChannel(read(path));
    }

    private byte[] read(String path) throws IOException {
        var cached = contentCache.get(path);
        if (cached != null) return cached;

        var resource = basePath + "/" + path;
        var stream = ClasspathMount.class.getClassLoader().getResourceAsStream(resource);
        if (stream == null && Thread.currentThread().getContextClassLoader() != null) {
            stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource);
        }
        if (stream == null) {
            stream = ClasspathMount.class.getResourceAsStream("/" + resource);
        }
        if (stream == null) throw new FileOperationException(path, MountConstants.NO_SUCH_FILE);
        try (var in = stream) {
            var bytes = in.readAllBytes();
            contentCache.put(path, bytes);
            return bytes;
        }
    }
}
