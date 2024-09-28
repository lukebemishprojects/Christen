package dev.lukebemish.christen.cli;

import dev.lukebemish.christen.ChristenTransformer;
import net.neoforged.jst.api.SourceTransformer;
import net.neoforged.jst.cli.Main;
import picocli.CommandLine;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Callable;

public class ChristenMain implements Callable<Integer> {
    @CommandLine.ArgGroup(exclusive = false, multiplicity = "1")
    ChristenTransformer plugin;

    @CommandLine.ArgGroup(exclusive = false, multiplicity = "1..*")
    List<Main> subcommands;

    private static final MethodHandle ENABLED_TRANSFORMERS;

    static {
        try {
            ENABLED_TRANSFORMERS = MethodHandles.lookup().findGetter(Main.class, "enabledTransformers", HashSet.class);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) {
        var commandLine = new CommandLine(new ChristenMain());
        commandLine.setCaseInsensitiveEnumValuesAllowed(true);
        System.exit(commandLine.execute(args));
    }

    @SuppressWarnings("unchecked")
    @Override
    public Integer call() throws Exception {
        for (var main : subcommands) {
            HashSet<SourceTransformer> enabledTransformers;
            try {
                enabledTransformers = (HashSet<SourceTransformer>) ENABLED_TRANSFORMERS.invoke(main);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
            enabledTransformers.add(plugin);
            var result = main.call();
            if (!result.equals(0)) {
                return result;
            }
        }
        return 0;
    }
}
