package dev.xyat.itemcontrol.item.mixin;

import dev.xyat.itemcontrol.item.util.ItemTagEdits;
import net.minecraft.tags.TagLoader;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

/** Applies the configured item tag additions and removals to the item tags right after they are built. */
@Mixin(TagLoader.class)
public abstract class ItemTagLoaderMixin {
    @Shadow
    @Final
    private String directory;

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Inject(method = "build(Ljava/util/Map;)Ljava/util/Map;", at = @At("RETURN"), cancellable = true)
    private void itemcontrol$editItemTags(Map entries, CallbackInfoReturnable<Map> cir) {
        // "tags/items" up to 1.20.1, "tags/item" from 1.21.
        if (directory == null || !(directory.equals("tags/items") || directory.equals("tags/item"))) return;
        cir.setReturnValue(ItemTagEdits.apply(cir.getReturnValue()));
    }
}
