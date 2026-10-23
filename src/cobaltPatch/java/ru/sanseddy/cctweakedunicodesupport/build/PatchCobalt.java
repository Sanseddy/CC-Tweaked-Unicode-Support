package ru.sanseddy.cctweakedunicodesupport.build;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

public final class PatchCobalt {
    private static final String OPERATION_HELPER = "org/squiddev/cobalt/OperationHelper.class";
    private static final String UNICODE_LENGTH = "org/squiddev/cobalt/UnicodeLength.class";
    private static final long FIXED_TIME = 315532800000L;

    private PatchCobalt() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 2) throw new IllegalArgumentException("Expected input and output Cobalt jars");

        var input = Path.of(args[0]);
        var output = Path.of(args[1]);
        Files.createDirectories(output.getParent());

        try (var jar = new JarFile(input.toFile());
             var out = new JarOutputStream(Files.newOutputStream(output))) {
            var entries = jar.stream()
                .filter(entry -> !entry.isDirectory())
                .sorted(Comparator.comparing(JarEntry::getName))
                .toList();

            var written = new HashSet<String>();
            for (var entry : entries) {
                byte[] bytes;
                try (var stream = jar.getInputStream(entry)) {
                    bytes = stream.readAllBytes();
                }

                if (entry.getName().equals(OPERATION_HELPER)) bytes = patchOperationHelper(bytes);
                writeEntry(out, entry.getName(), bytes);
                written.add(entry.getName());
            }

            if (!written.contains(OPERATION_HELPER)) {
                throw new IllegalStateException("Cobalt does not contain " + OPERATION_HELPER);
            }

            try (var stream = requiredResource("/" + UNICODE_LENGTH)) {
                writeEntry(out, UNICODE_LENGTH, stream.readAllBytes());
            }
        }
    }

    private static byte[] patchOperationHelper(byte[] bytes) {
        var reader = new ClassReader(bytes);
        var writer = new ClassWriter(reader, 0);
        var patches = new int[1];

        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                var visitor = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (!name.equals("length") || !descriptor.equals("(Lorg/squiddev/cobalt/LuaState;Lorg/squiddev/cobalt/LuaValue;)Lorg/squiddev/cobalt/LuaValue;")) {
                    return visitor;
                }

                return new MethodVisitor(Opcodes.ASM9, visitor) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                        if (opcode == Opcodes.INVOKEVIRTUAL && owner.equals("org/squiddev/cobalt/LuaString")
                            && name.equals("length") && descriptor.equals("()I")) {
                            patches[0]++;
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, "org/squiddev/cobalt/UnicodeLength", "length", "(Lorg/squiddev/cobalt/LuaString;)I", false);
                        } else {
                            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                        }
                    }
                };
            }
        }, 0);

        if (patches[0] != 1) throw new IllegalStateException("Expected one Cobalt string-length call, patched " + patches[0]);
        return writer.toByteArray();
    }

    private static InputStream requiredResource(String name) {
        var stream = PatchCobalt.class.getResourceAsStream(name);
        if (stream == null) throw new IllegalStateException("Missing build resource " + name);
        return stream;
    }

    private static void writeEntry(JarOutputStream out, String name, byte[] bytes) throws IOException {
        var entry = new JarEntry(name);
        entry.setTime(FIXED_TIME);
        out.putNextEntry(entry);
        out.write(bytes);
        out.closeEntry();
    }
}
