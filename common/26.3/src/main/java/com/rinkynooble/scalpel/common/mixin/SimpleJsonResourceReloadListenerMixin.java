package com.rinkynooble.scalpel.common.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.rinkynooble.scalpel.common.data.DataFilter;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Map;

/**
 * Loot modifier files (a loader's JSON reload listener) are listed here before anything parses them. Since 26.3
 * recipes, advancements and loot tables are datapack registries ({@code RegistryFiles}), so vanilla no longer reads
 * any server data through this class; the hook stays for a loader whose loot modifiers still do. Vanilla 26.3 reads
 * the files inside {@code prepare}. Optional: NeoForge moves the loop into its own static {@code scanDirectory}
 * (hooked by the NeoForge port), so {@code prepare} has no such call there.
 */
@Mixin(SimpleJsonResourceReloadListener.class)
public abstract class SimpleJsonResourceReloadListenerMixin {
    @WrapOperation(method = "prepare(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)Ljava/util/Map;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/resources/FileToIdConverter;listMatchingResources(Lnet/minecraft/server/packs/resources/ResourceManager;)Ljava/util/Map;"),
            require = 0)
    private Map<Identifier, Resource> scalpel$filter(FileToIdConverter lister, ResourceManager manager,
                                                     Operation<Map<Identifier, Resource>> original) {
        return DataFilter.filterFiles(lister, original.call(lister, manager));
    }
}
