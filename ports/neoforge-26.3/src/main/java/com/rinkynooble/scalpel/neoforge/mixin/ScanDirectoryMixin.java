package com.rinkynooble.scalpel.neoforge.mixin;

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
 * Loot modifier files are listed here before anything parses them. NeoForge 26.3 moves the JSON listener's file loop
 * out of {@code prepare} into its own static {@code scanDirectory} (its loot modifier manager and animation loader use
 * it), so the common hook on {@code prepare} finds nothing on NeoForge and this one takes its place. Recipes,
 * advancements and loot tables are datapack registries since 26.3 and are filtered in {@code RegistryFiles}.
 */
@Mixin(SimpleJsonResourceReloadListener.class)
public abstract class ScanDirectoryMixin {
    @WrapOperation(method = "scanDirectory(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/resources/FileToIdConverter;Lcom/mojang/serialization/DynamicOps;Lcom/mojang/serialization/Codec;Ljava/util/Map;)Ljava/util/Map;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/resources/FileToIdConverter;listMatchingResources(Lnet/minecraft/server/packs/resources/ResourceManager;)Ljava/util/Map;"))
    private static Map<Identifier, Resource> scalpel$filter(FileToIdConverter lister, ResourceManager manager,
                                                            Operation<Map<Identifier, Resource>> original) {
        return DataFilter.filterFiles(lister, original.call(lister, manager));
    }
}
