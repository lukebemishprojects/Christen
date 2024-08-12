package dev.lukebemish.christen.test;

import net.neoforged.jst.cli.Main;
import net.neoforged.srgutils.IMappingBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

class TypeParametersTest {
    @Test
    void remapParameterizedType() throws IOException {
        var classpathSources = List.of(new Utilities.Source(
                "def.ToRemap1",
                """
                        package def;
                        
                        public class ToRemap1<T> {}
                        """
        ), new Utilities.Source(
                "def.ToRemap2",
                """
                        package def;
                        
                        public class ToRemap2 {}
                        """
        ));
        var binaryJar = Utilities.createTestBinaries(classpathSources, List.of());

        var sources = List.of(new Utilities.Source(
                "abc.TestClass",
                """
                        package abc;
                        
                        import def.ToRemap1;
                        import def.ToRemap2;
                        
                        public class TestClass extends ToRemap1<ToRemap2> {}
                        """
        ));
        var sourcesJar = Utilities.createTestSources(sources);

        var mappings = IMappingBuilder.create("source", "target")
                .addClass("def/ToRemap1", "ghi/Remapped1").build()
                .addClass("def/ToRemap2", "ghi/Remapped2").build()
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
                        
                        import ghi.Remapped1;
                        import ghi.Remapped2;
                        
                        public class TestClass extends Remapped1<Remapped2> {}
                        """
        )));
    }
}
