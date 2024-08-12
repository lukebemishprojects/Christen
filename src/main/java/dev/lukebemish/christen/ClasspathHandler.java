package dev.lukebemish.christen;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ClasspathHandler implements AutoCloseable {
    private final Path[] paths;

    private final List<FileSystem> fileSystems = new ArrayList<>();

    private final Map<String, List<String>> inheritance = new ConcurrentHashMap<>();
    private final Map<String, List<String>> packageCompletions = new ConcurrentHashMap<>();

    public ClasspathHandler(Path[] files) throws IOException {
        this.paths = new Path[files.length];

        try {
            for (int i = 0; i < files.length; i++) {
                if (Files.isDirectory(files[i])) {
                    this.paths[i] = files[i];
                } else {
                    var fileSystem = FileSystems.newFileSystem(files[i]);
                    this.fileSystems.add(fileSystem);
                    this.paths[i] = fileSystem.getPath("/");
                }
            }
        } catch (IOException e) {
            this.close();
            throw e;
        }
    }

    public List<String> getStarCompletions(String packageName) {
        if (packageName.contains(".")) {
            throw new IllegalArgumentException(packageName + " is not a valid package; should use '/', not '.'");
        }
        var existing = packageCompletions.get(packageName);
        if (existing != null) {
            return existing;
        }
        var completions = new ArrayList<String>();
        for (Path root : paths) {
            Path relative = root.resolve(packageName);
            if (Files.isDirectory(relative)) {
                try (var files = Files.list(relative)) {
                    files.forEach(p -> {
                        var fileName = p.getFileName().toString();
                        if (fileName.endsWith(".class")) {
                            completions.add(fileName.substring(0, fileName.length() - 6));
                        }
                    });
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        }
        var list = List.copyOf(completions);
        packageCompletions.put(packageName, list);
        return list;
    }

    public List<String> getParents(String type) {
        if (type.contains(".")) {
            throw new IllegalArgumentException(type + " is not a valid class; should use '/', not '.'");
        }
        var existing = inheritance.get(type);
        if (existing != null) {
            return existing;
        }
        for (Path root : paths) {
            Path relative = root.resolve(type + ".class");
            if (Files.isRegularFile(relative)) {
                List<String> superTypes = new ArrayList<>();
                try (var input = Files.newInputStream(relative)) {
                    ClassReader reader = new ClassReader(input);
                    reader.accept(new ClassVisitor(Opcodes.ASM9) {
                        @Override
                        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                            super.visit(version, access, name, signature, superName, interfaces);
                            superTypes.add(superName);
                            superTypes.addAll(Arrays.asList(interfaces));
                        }
                    }, 0);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                var list = List.copyOf(superTypes);
                inheritance.put(type, list);
                break;
            }
        }
        return List.of();
    }

    @Override
    public void close() throws IOException {
        var exceptions = new ArrayList<IOException>();
        for (var fileSystem : this.fileSystems) {
            try {
                fileSystem.close();
            } catch (IOException e) {
                exceptions.add(e);
            }
        }
        if (!exceptions.isEmpty()) {
            var exception = new IOException("Failed to close file systems");
            for (var e : exceptions) {
                exception.addSuppressed(e);
            }
            throw exception;
        }
    }
}
