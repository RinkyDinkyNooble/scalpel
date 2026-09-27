package com.rinkynooble.scalpel.forge.mixin;

import com.google.gson.JsonElement;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.rinkynooble.scalpel.forge.data.CutLoot;
import com.rinkynooble.scalpel.forge.data.DataFilter;
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

import java.util.List;
import java.util.Map;

/**
 * Forge builds its list of global loot modifiers from the {@code forge:global_loot_modifiers} index in the data packs.
 * Two changes to that list: ids whose file Scalpel dropped are left out (otherwise Forge warns that it could not
 * decode each one), and Scalpel's own modifier is moved last, since Forge has no priority, only the index order.
 * <p>
 * {@code apply} overrides a vanilla method from a Forge class, which the mapping processor can't resolve, so both the
 * development name and the SRG name are listed with remapping off. The descriptor skips the generic bridge method.
 * LootJS rebuilds the same map through a HashMap at the same point, which loses the order; the higher priority makes
 * this callback run after that one.
 */
@Mixin(value = LootModifierManager.class, remap = false, priority = 1500)
public abstract class LootModifierManagerMixin {
    @Shadow
    private Map<ResourceLocation, IGlobalLootModifier> registeredLootModifiers;

    /** The one {@code List.add} in {@code apply} adds an id from the index. Optional: if it misses, Forge only warns. */
    @WrapOperation(method = {
            "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
            "m_5787_(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V"
    }, at = @At(value = "INVOKE", target = "Ljava/util/List;add(Ljava/lang/Object;)Z"), require = 0)
    private boolean scalpel$skipDropped(List<Object> locations, Object location, Operation<Boolean> original) {
        if (location instanceof ResourceLocation id && DataFilter.isDroppedLootModifier(id)) {
            return false;
        }
        return original.call(locations, location);
    }

    @Inject(method = {
            "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
            "m_5787_(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V"
    }, at = @At("RETURN"))
    private void scalpel$runLast(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler, CallbackInfo ci) {
        registeredLootModifiers = CutLoot.runLast(registeredLootModifiers);
    }
}
