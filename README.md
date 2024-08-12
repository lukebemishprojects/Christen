# Christen

Christen is a tool, powered by [JavaSourceTransformer](https://github.com/neoforged/JavaSourceTransformer/), for remapping java source code. It is implemented as a JST
plugin named `christen`. To use, either enable the plugin with both JST and christen on the classpath, or run the
christen fatjar:
```
java -jar christen-x.y.z-all.jar --enable-christen --christen-mappings=mappings-file.tiny input.jar output.jar
```

Christen should be able to read most common mappings formats (anything that [SRGUtils](https://github.com/NeoForge/SRGUtils) can read). For remapping to
work correctly, it is recommended that you feed in the remapping classpath via `--classpath` as an argument to JST.

## Licenses

This tool is licensed under the LGPL 3.0 license.

The standalone executable jar bundles other libraries which do much of the heavy lifting -- notably, NeoForge's
[JavaSourceTransformer](https://github.com/neoforged/JavaSourceTransformer/) and [SRGUtils](https://github.com/NeoForge/SRGUtils),
the [IntelliJ platform](https://github.com/JetBrains/intellij-community), and [ASM](https://asm.ow2.io/). Note that the
standalone jar bundles code from these other projects, available under their own
licenses.
