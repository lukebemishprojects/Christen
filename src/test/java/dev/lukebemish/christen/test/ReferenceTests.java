package dev.lukebemish.christen.test;

import net.neoforged.jst.cli.Main;
import net.neoforged.srgutils.IMappingBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

class ReferenceTests {
    @Test
    void remapReferences() throws IOException {
        var classpathSources = List.of(new Utilities.Source(
                "def.ToRemap",
                """
                        package def;
                        
                        public class ToRemap {
                            public void instanceMethod(ToRemap remap) {}

                            public static void staticMethod(ToRemap remap) {}

                            public ToRemap INSTANCE_FIELD;

                            public static ToRemap STATIC_FIELD;
                        }
                        """
        ));
        var binaryJar = Utilities.createTestBinaries(classpathSources, List.of());

        var sources = List.of(new Utilities.Source(
                "abc.TestClass",
                """
                        package abc;
                        
                        import def.ToRemap;
                        
                        public class TestClass {
                            public void method() {
                                ToRemap toRemap = new ToRemap();
                                toRemap.instanceMethod(toRemap.INSTANCE_FIELD);
                                ToRemap.staticMethod(ToRemap.STATIC_FIELD);
                                toRemap.INSTANCE_FIELD = null;
                                ToRemap.STATIC_FIELD = null;
                            }
                        }
                        """
        ));
        var sourcesJar = Utilities.createTestSources(sources);

        var mappings = IMappingBuilder.create("source", "target")
                .addClass("def/ToRemap", "ghi/Remapped")
                .method("(Ldef/ToRemap;)V", "instanceMethod", "remappedInstanceMethod").build()
                .method("(Ldef/ToRemap;)V", "staticMethod", "remappedStaticMethod").build()
                .field("INSTANCE_FIELD", "REMAPPED_INSTANCE_FIELD").descriptor("Ldef/ToRemap;").build()
                .field("STATIC_FIELD", "REMAPPED_STATIC_FIELD").descriptor("Ldef/ToRemap;").build()
                .build()
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
                        
                        public class TestClass {
                            public void method() {
                                Remapped toRemap = new Remapped();
                                toRemap.remappedInstanceMethod(toRemap.REMAPPED_INSTANCE_FIELD);
                                Remapped.remappedStaticMethod(Remapped.REMAPPED_STATIC_FIELD);
                                toRemap.REMAPPED_INSTANCE_FIELD = null;
                                Remapped.REMAPPED_STATIC_FIELD = null;
                            }
                        }
                        """
        )));
    }
}
