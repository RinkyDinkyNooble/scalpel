package com.rinkynooble.scalpel.neoforge.mixin;

import com.google.gson.JsonElement;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.rinkynooble.scalpel.common.data.DataFilter;
import com.rinkynooble.scalpel.neoforge.data.CutLoot;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifierManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Map;

/**
 * NeoForge builds its list of global loot modifiers from the {@code neoforge:global_loot_modifiers} index in the data
 * packs. Two changes to that list: ids whose file Scalpel dropped are left out (otherwise NeoForge would try to decode
 * a file that isn't there), and Scalpel's own modifier is moved last, since NeoForge has no priority, only the index
 * order. LootJS rebuilds the same map at the same point; the higher priority makes this callback run after that one.
 */
@Mixin(value = LootModifierManager.class, priority = 1500)
public abstract class LootModifierManagerMixin {
    @Shadow(remap = false)
    private Map<ResourceLocation, IGlobalLootModifier> registeredLootModifiers;

    /** The one {@code List.add} in {@code prepare} adds an id from the index. Optional: if it misses, NeoForge only warns. */
    @WrapOperation(method = "prepare(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)Ljava/util/Map;",
            at = @At(value = "INVOKE", target = "Ljava/util/List;add(Ljava/lang/Object;)Z"), require = 0)
    private boolean scalpel$skipDropped(List<Object> locations, Object location, Operation<Boolean> original) {
        if (location instanceof ResourceLocation id && DataFilter.isDroppedLootModifier(id)) {
            return false;
        }
        return original.call(locations, location);
    }

    @Inject(method = "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
            at = @At("RETURN"))
    private void scalpel$runLast(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler, CallbackInfo ci) {
        registeredLootModifiers = CutLoot.runLast(registeredLootModifiers);
    }
}
