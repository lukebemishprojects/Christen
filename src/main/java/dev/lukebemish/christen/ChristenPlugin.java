package dev.lukebemish.christen;

import com.google.auto.service.AutoService;
import net.neoforged.jst.api.SourceTransformer;
import net.neoforged.jst.api.SourceTransformerPlugin;

@AutoService(SourceTransformerPlugin.class)
public class ChristenPlugin implements SourceTransformerPlugin {
    @Override
    public String getName() {
        return "christen";
    }

    @Override
    public SourceTransformer createTransformer() {
        return new ChristenTransformer();
    }
}
