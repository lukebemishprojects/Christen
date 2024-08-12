package dev.lukebemish.christen;

import com.intellij.psi.PsiFile;
import net.neoforged.jst.api.Replacements;
import net.neoforged.jst.api.SourceTransformer;
import net.neoforged.jst.api.TransformContext;
import net.neoforged.srgutils.IMappingFile;
import org.jspecify.annotations.Nullable;
import picocli.CommandLine;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

public class ChristenTransformer implements SourceTransformer {
    @Nullable
    @CommandLine.Option(names = "--christen-mappings", description = "The path to the mappings file to remap sources with")
    public Path stubOut;

    private IMappingFile mappings;

    @Override
    public void beforeRun(TransformContext context) {
        try {
            mappings = IMappingFile.load(stubOut.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void visitFile(PsiFile psiFile, Replacements replacements) {
        new ChristenVisitor(mappings, replacements).visitElement(psiFile);
    }
}
