package com.rinkynooble.scalpel.forge.mixin;

import com.google.gson.JsonElement;
import com.rinkynooble.scalpel.forge.data.CutLoot;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifierManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/**
 * Forge 52 reads the {@code forge:global_loot_modifiers} index in {@code prepare} and keeps only the scanned files it
 * lists, so a modifier file Scalpel dropped is simply absent (no "Could not decode" warning, nothing to skip here, unlike
 * Forge 1.20.1). It then decodes them in the order of the scanned files' hash map, with no priority: Scalpel's own
 * modifier is moved last. The descriptor names the real method, not the generic bridge. LootJS rebuilds the same map at
 * the same point; the higher priority makes this callback run after that one.
 */
@Mixin(value = LootModifierManager.class, remap = false, priority = 1500)
public abstract class LootModifierManagerMixin {
    @Shadow
    private Map<ResourceLocation, IGlobalLootModifier> modifiers;

    @Inject(method = "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
            at = @At("RETURN"))
    private void scalpel$runLast(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler, CallbackInfo ci) {
        modifiers = CutLoot.runLast(modifiers);
    }
}
