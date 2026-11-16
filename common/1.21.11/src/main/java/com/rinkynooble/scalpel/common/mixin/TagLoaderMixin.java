package com.rinkynooble.scalpel.common.mixin;

import com.rinkynooble.scalpel.common.data.ServerData;
import com.rinkynooble.scalpel.common.data.TagFilter;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagLoader;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Map;

@Mixin(TagLoader.class)
public abstract class TagLoaderMixin<T> {
    @Shadow
    @Final
    private String directory;

    /**
     * A server data load starts here (1.21.2+): the tags of the existing registries load first, before worldgen
     * registries and before every reload listener, both at world load and on /reload.
     */
    @Inject(method = "loadTagsForExistingRegistries", at = @At("HEAD"))
    private static void scalpel$dataLoadStart(ResourceManager manager, RegistryAccess registries,
                                              CallbackInfoReturnable<List<Registry.PendingTags<?>>> cir) {
        ServerData.onDataLoadStart();
    }

    /** Entries are removed right after the files are read, before the tag is built. */
    @Inject(method = "load", at = @At("RETURN"))
    private void scalpel$afterLoad(ResourceManager manager,
                                   CallbackInfoReturnable<Map<Identifier, List<TagLoader.EntryWithSource>>> cir) {
        TagFilter.afterLoad(directory, cir.getReturnValue());
    }

    /** Placeholders are removed again from the built tags, in case something added them back. */
    @Inject(method = "build(Ljava/util/Map;)Ljava/util/Map;", at = @At("RETURN"))
    private void scalpel$afterBuild(Map<Identifier, List<TagLoader.EntryWithSource>> entries,
                                    CallbackInfoReturnable<Map<Identifier, List<T>>> cir) {
        TagFilter.afterBuild(directory, cir.getReturnValue());
    }
}
