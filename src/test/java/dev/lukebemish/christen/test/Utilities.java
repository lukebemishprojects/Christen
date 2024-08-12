package dev.lukebemish.christen.test;

import net.neoforged.srgutils.IMappingFile;
import org.junit.jupiter.api.Assertions;

import javax.tools.JavaCompiler;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class Utilities {
    private Utilities() {}

    public static Path createTestMappings(IMappingFile mappings) throws IOException {
        Path path = Files.createTempFile("christen-test", ".tiny");
        mappings.write(path, IMappingFile.Format.TINY, false);
        return path;
    }

    public static void verifyContents(Path jarPath, List<Source> sources) throws IOException {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            for (var source : sources) {
                var entry = jar.getJarEntry(source.clazz.replace('.', '/') + ".java");
                Assertions.assertNotNull(entry, "Missing source file for " + source.clazz);
                try (InputStream is = jar.getInputStream(entry)) {
                    byte[] bytes = is.readAllBytes();
                    String actual = new String(bytes, StandardCharsets.UTF_8);
                    Assertions.assertEquals(source.source, actual);
                }
            }
        }
    }

    public static final class Source extends SimpleJavaFileObject {
        private final String clazz;
        private final String source;

        public Source(String clazz, String source) {
            super(URI.create("string:///" + clazz.replace('.','/') + Kind.SOURCE.extension), Kind.SOURCE);
            this.clazz = clazz;
            this.source = source;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return source;
        }

        @Override
        public InputStream openInputStream() {
            return new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8));
        }
    }

    public static Path createTestSources(List<Source> sources) throws IOException {
        var path = Files.createTempFile("christen-test", ".jar");
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(path), manifest)) {
            for (var source : sources) {
                jar.putNextEntry(new JarEntry(source.clazz.replace('.', '/') + ".java"));
                jar.write(source.source.getBytes(StandardCharsets.UTF_8));
                jar.closeEntry();
            }
        }
        return path;
    }

    public static Path createTestBinaries(List<Source> sources, List<Path> classpath) throws IOException {
        Path classDir = Files.createTempDirectory("christen-test");

        List<String> options = new ArrayList<>();
        options.add("-classpath");
        options.add(classpath.stream().map(p -> p.toAbsolutePath().toString()).collect(Collectors.joining(File.pathSeparator)));
        options.add("-d");
        options.add(classDir.toAbsolutePath().toString());

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null)) {
            compiler.getTask(null, fileManager, null, options, null, sources).call();
        }

        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        Path jarPath = Files.createTempFile("christen-test", ".jar");
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(jarPath), manifest);
             Stream<Path> files = Files.walk(classDir)) {
            files.forEach(p -> {
                if (Files.isRegularFile(p)) {
                    try {
                        jar.putNextEntry(new JarEntry(classDir.relativize(p).toString()));
                        jar.write(Files.readAllBytes(p));
                        jar.closeEntry();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        }
        return jarPath;
    }
}
