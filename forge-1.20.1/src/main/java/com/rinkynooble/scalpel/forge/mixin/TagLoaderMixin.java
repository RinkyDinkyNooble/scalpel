package com.rinkynooble.scalpel.forge.mixin;

import com.rinkynooble.scalpel.forge.data.TagFilter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagLoader;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Mixin(TagLoader.class)
public abstract class TagLoaderMixin<T> {
    @Shadow
    @Final
    private String directory;

    /** Entries are removed right after the files are read, before the tag is built. */
    @Inject(method = "load", at = @At("RETURN"))
    private void scalpel$afterLoad(ResourceManager manager,
                                   CallbackInfoReturnable<Map<ResourceLocation, List<TagLoader.EntryWithSource>>> cir) {
        TagFilter.afterLoad(directory, cir.getReturnValue());
    }

    /** Placeholders are removed again from the built tags, in case something added them back. */
    @Inject(method = "build(Ljava/util/Map;)Ljava/util/Map;", at = @At("RETURN"))
    private void scalpel$afterBuild(Map<ResourceLocation, List<TagLoader.EntryWithSource>> entries,
                                    CallbackInfoReturnable<Map<ResourceLocation, Collection<T>>> cir) {
        TagFilter.afterBuild(directory, cir.getReturnValue());
    }
}
