package com.rinkynooble.scalpel.common.mixin;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A tag file entry's id and whether it names another tag (Forge has getters for these, vanilla doesn't). */
@Mixin(TagEntry.class)
public interface TagEntryAccessor {
    @Accessor("id")
    ResourceLocation scalpel$id();

    @Accessor("tag")
    boolean scalpel$isTag();
}
