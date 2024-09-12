package dev.lukebemish.christen;

import com.intellij.psi.PsiFile;
import net.neoforged.jst.api.Replacements;
import net.neoforged.jst.api.SourceTransformer;
import net.neoforged.jst.api.TransformContext;
import net.neoforged.srgutils.IMappingFile;
import picocli.CommandLine;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

public class ChristenTransformer implements SourceTransformer {
    @CommandLine.Option(names = "--christen-mappings", description = "The path to the mappings file to remap sources with", required = true)
    public Path mappingsIn;

    private IMappingFile mappings;

    @Override
    public void beforeRun(TransformContext context) {
        try {
            mappings = IMappingFile.load(mappingsIn.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void visitFile(PsiFile psiFile, Replacements replacements) {
        new ChristenVisitor(mappings, replacements).visitElement(psiFile);
    }
}
