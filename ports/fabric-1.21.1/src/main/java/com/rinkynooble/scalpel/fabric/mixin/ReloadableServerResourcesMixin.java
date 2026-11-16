package com.rinkynooble.scalpel.fabric.mixin;

import com.rinkynooble.scalpel.common.data.ServerData;
import com.rinkynooble.scalpel.fabric.LootTableId;
import net.minecraft.commands.Commands;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.flag.FeatureFlagSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * A server data load on Fabric and Quilt: its start, and its end once every reload listener is done and tags are
 * bound. Scalpel's final recipe pass runs at that end rather than as a listener of its own, because Quilt's resource
 * loader reorders listeners, so recipes that scripts add could come after it.
 */
@Mixin(ReloadableServerResources.class)
public abstract class ReloadableServerResourcesMixin {
    @Shadow
    public abstract net.minecraft.server.ReloadableServerRegistries.Holder fullRegistries();

    @Inject(method = "loadResources", at = @At("HEAD"))
    private static void scalpel$dataLoadStart(ResourceManager manager, LayeredRegistryAccess<RegistryLayer> registries, FeatureFlagSet features,
                                              Commands.CommandSelection selection, int functionLevel, Executor background, Executor main,
                                              CallbackInfoReturnable<CompletableFuture<ReloadableServerResources>> cir) {
        ServerData.onDataLoadStart();
    }

    @Inject(method = "updateRegistryTags()V", at = @At("HEAD"))
    private void scalpel$finalRecipePass(CallbackInfo ci) {
        RegistryAccess access = fullRegistries().get();
        // Since 1.20.5 loot tables are a registry reloaded with the data; name each one for the generated-loot report
        // (nested tables included, which never pass through a lookup by id).
        access.registry(Registries.LOOT_TABLE).ifPresent(tables -> tables.holders().forEach(holder -> {
            LootTableId table = (LootTableId) holder.value();
            if (table.scalpel$id() == null) {
                table.scalpel$setId(holder.key().location().toString());
            }
        }));
        ServerData.runFinalRecipePass((ReloadableServerResources) (Object) this, access);
    }

    @Inject(method = "updateRegistryTags()V", at = @At("TAIL"))
    private void scalpel$dataLoadDone(CallbackInfo ci) {
        ServerData.afterDataLoad(fullRegistries().get());
    }
}
