package dev.lukebemish.christen.test;

import net.neoforged.jst.cli.Main;
import net.neoforged.srgutils.IMappingBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

class ImportTests {
    @Test
    void remapImports() throws IOException {
        var classpathSources = List.of(new Utilities.Source(
                "def.ToRemap",
                """
                        package def;
                        
                        public class ToRemap {
                            public static void staticMethod(ToRemap remap) {}

                            public static ToRemap STATIC_FIELD;

                            public static class Inner {}
                        }
                        """
        ));
        var binaryJar = Utilities.createTestBinaries(classpathSources, List.of());

        var sources = List.of(new Utilities.Source(
                "abc.TestClass",
                """
                        package abc;
                        
                        import def.ToRemap;
                        import def.ToRemap.Inner;
                        import static def.ToRemap.staticMethod;
                        import static def.ToRemap.STATIC_FIELD;
                        
                        public class TestClass {}
                        """
        ));
        var sourcesJar = Utilities.createTestSources(sources);

        var mappings = IMappingBuilder.create("source", "target")
                .addClass("def/ToRemap", "ghi/Remapped")
                .method("(Ldef/ToRemap;)V", "staticMethod", "remappedStaticMethod").build()
                .field("STATIC_FIELD", "REMAPPED_STATIC_FIELD").descriptor("Ldef/ToRemap;").build()
                .build()
                .addClass("def/ToRemap$Inner", "ghi/Remapped$RemappedInner").build()
                .build().getMap("source", "target");
        var mappingsFile = Utilities.createTestMappings(mappings);

        var outputFile = Files.createTempFile("christen-test", ".jar");

        Assertions.assertEquals(0, Main.innerMain(
                "--classpath="+binaryJar.toAbsolutePath(),
                "--enable-christen",
                "--christen-mappings="+mappingsFile.toAbsolutePath(),
                sourcesJar.toAbsolutePath().toString(),
                outputFile.toAbsolutePath().toString()
        ));

        Utilities.verifyContents(outputFile, List.of(new Utilities.Source(
                "abc.TestClass",
                """
                        package abc;
                        
                        import ghi.Remapped;
                        import ghi.Remapped.RemappedInner;
                        import static ghi.Remapped.remappedStaticMethod;
                        import static ghi.Remapped.REMAPPED_STATIC_FIELD;
                        
                        public class TestClass {}
                        """
        )));
    }
}
