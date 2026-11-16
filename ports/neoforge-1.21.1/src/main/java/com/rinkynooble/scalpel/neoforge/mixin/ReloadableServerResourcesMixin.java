package com.rinkynooble.scalpel.neoforge.mixin;

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
 * A data load starts here. Since 1.20.5 loot tables load first ({@code ReloadableServerRegistries}), before NeoForge
 * asks for reload listeners, so starting at {@code AddReloadListenerEvent} would forget what the loot filter did.
 */
@Mixin(ReloadableServerResources.class)
public abstract class ReloadableServerResourcesMixin {
    @Inject(method = "loadResources", at = @At("HEAD"))
    private static void scalpel$dataLoadStart(ResourceManager manager, LayeredRegistryAccess<RegistryLayer> registries, FeatureFlagSet features,
                                              Commands.CommandSelection selection, int permissionLevel, Executor background, Executor main,
                                              CallbackInfoReturnable<CompletableFuture<ReloadableServerResources>> cir) {
        ServerData.onDataLoadStart();
    }
}
