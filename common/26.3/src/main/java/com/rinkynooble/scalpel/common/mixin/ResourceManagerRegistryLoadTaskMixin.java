package com.rinkynooble.scalpel.common.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.rinkynooble.scalpel.common.data.RegistryFiles;
import net.minecraft.core.Registry;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceManagerRegistryLoadTask;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * Datapack registries (worldgen, enchantments, villager trades since 26.1, recipes, advancements and loot tables since
 * 26.3, mods' own): the list of a registry's files
 * is seen before any is read, so removed enchantments, trades that name cut content and trim materials made from a
 * removed item are never loaded. 26.1 loads every registry as its own task, in parallel; the first step of a task is
 * {@code supplyAsync(() -> lister.listMatchingResources(resourceManager))}, and that supplier is wrapped here.
 */
@Mixin(ResourceManagerRegistryLoadTask.class)
public abstract class ResourceManagerRegistryLoadTaskMixin {
    @Shadow
    @Final
    private ResourceManager resourceManager;

    @SuppressWarnings("unchecked")
    @WrapOperation(method = "load",
            at = @At(value = "INVOKE", target = "Ljava/util/concurrent/CompletableFuture;supplyAsync(Ljava/util/function/Supplier;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture<?> scalpel$listFiles(Supplier<?> listFiles, Executor executor, Operation<CompletableFuture<?>> original,
                                                   @Local FileToIdConverter lister) {
        ResourceKey<? extends Registry<?>> registry = ((RegistryLoadTaskAccessor) (Object) this).scalpel$registryKey();
        ResourceManager manager = resourceManager;
        Supplier<Map<Identifier, Resource>> filtered =
                () -> RegistryFiles.beforeLoad(registry, lister, (Map<Identifier, Resource>) listFiles.get(), manager);
        return original.call(filtered, executor);
    }
}
