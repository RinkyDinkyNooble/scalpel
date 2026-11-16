package com.rinkynooble.scalpel.fabric.mixin;

import com.rinkynooble.scalpel.common.data.ServerData;
import net.minecraft.commands.Commands;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.flag.FeatureFlagSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * A server data load on Fabric and Quilt: its start, and its end once every reload listener is done and tags are
 * bound. Scalpel's final recipe pass runs at that end rather than as a listener of its own, because Quilt's resource
 * loader reorders listeners, so recipes that scripts add (KubeJS) could come after it.
 */
@Mixin(ReloadableServerResources.class)
public abstract class ReloadableServerResourcesMixin {
    @Inject(method = "loadResources", at = @At("HEAD"))
    private static void scalpel$dataLoadStart(ResourceManager manager, RegistryAccess.Frozen registryAccess, FeatureFlagSet features,
                                              Commands.CommandSelection selection, int functionLevel, Executor background, Executor main,
                                              CallbackInfoReturnable<CompletableFuture<ReloadableServerResources>> cir) {
        ServerData.onDataLoadStart();
    }

    @Inject(method = "updateRegistryTags(Lnet/minecraft/core/RegistryAccess;)V", at = @At("HEAD"))
    private void scalpel$finalRecipePass(RegistryAccess registryAccess, CallbackInfo ci) {
        ServerData.runFinalRecipePass((ReloadableServerResources) (Object) this, registryAccess);
    }

    @Inject(method = "updateRegistryTags(Lnet/minecraft/core/RegistryAccess;)V", at = @At("TAIL"))
    private void scalpel$dataLoadDone(RegistryAccess registryAccess, CallbackInfo ci) {
        ServerData.afterDataLoad(registryAccess);
    }
}
