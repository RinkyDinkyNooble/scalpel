package com.rinkynooble.scalpel.forge.mixin;

import com.rinkynooble.scalpel.common.data.ServerData;
import net.minecraft.commands.Commands;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.flag.FeatureFlagSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * The start of a server data load. Since 1.20.5 loot tables load first, in {@code ReloadableServerRegistries.reload},
 * and Forge fires {@code AddReloadListenerEvent} only after that, so starting there (as on Forge 1.20.1) reset the
 * report after the loot tables were filtered and lost their entries (case 11-vanilla-data).
 */
@Mixin(ReloadableServerResources.class)
public abstract class ReloadableServerResourcesMixin {
    @Inject(method = "loadResources", at = @At("HEAD"))
    private static void scalpel$dataLoadStart(ResourceManager manager, LayeredRegistryAccess<RegistryLayer> registries, FeatureFlagSet features,
                                              Commands.CommandSelection selection, int functionLevel, Executor background, Executor main,
                                              CallbackInfoReturnable<CompletableFuture<ReloadableServerResources>> cir) {
        ServerData.onDataLoadStart();
    }
}
