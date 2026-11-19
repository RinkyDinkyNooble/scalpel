package com.rinkynooble.scalpel.neoforge.mixin;

import com.rinkynooble.scalpel.common.Scalpel;
import com.rinkynooble.scalpel.neoforge.data.CutLoot;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifierManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Puts Scalpel's own global loot modifier after every other one. NeoForge changed the manager inside the 26.1 line
 * (see {@link CutLoot}): the betas up to 26.1.2.19 keep an index-ordered {@code registeredLootModifiers} map, 26.1.2.20+
 * a {@code sortedModifiers} list sorted by priority. {@code apply(Map, ResourceManager, ProfilerFiller)} has the same
 * descriptor in both, so one injector serves both, and the fields are found by name. The handler is merged into
 * {@code LootModifierManager}, so the reflection reads its own private fields. Priority 1500: after LootJS-style
 * callbacks at the same point.
 */
@Mixin(value = LootModifierManager.class, priority = 1500)
public abstract class LootModifierManagerMixin {
    /** Betas: a modifier file Scalpel dropped but the index still lists comes through as null; leave it out. */
    @Inject(method = "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
            at = @At("HEAD"))
    private void scalpel$skipDropped(Map<Identifier, ?> files, ResourceManager manager, ProfilerFiller profiler, CallbackInfo ci) {
        try {
            files.values().removeIf(Objects::isNull);
        } catch (UnsupportedOperationException e) {
            // An immutable map has no nulls to leave out.
        }
    }

    @Inject(method = "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
            at = @At("RETURN"))
    @SuppressWarnings("unchecked")
    private void scalpel$runLast(Map<Identifier, ?> files, ResourceManager manager, ProfilerFiller profiler, CallbackInfo ci) {
        try {
            Field registered = LootModifierManager.class.getDeclaredField("registeredLootModifiers");
            Map<Identifier, IGlobalLootModifier> byId = (Map<Identifier, IGlobalLootModifier>) registered.get(this);
            Field sorted = null;
            for (Field field : LootModifierManager.class.getDeclaredFields()) {
                if (field.getName().equals("sortedModifiers")) {
                    sorted = field;
                }
            }
            if (sorted != null) {
                sorted.set(this, CutLoot.runLast(byId, (List<IGlobalLootModifier>) sorted.get(this)));
            } else {
                registered.set(this, CutLoot.runLastIndexed(byId));
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            Scalpel.core().warn("could not put loot modifier " + CutLoot.ID + " last (" + e + "); cut items that loot functions"
                    + " create can still drop");
        }
    }
}
