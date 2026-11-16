package com.rinkynooble.scalpel.fabric.mixin;

import net.minecraft.world.entity.npc.VillagerTrades;
import org.apache.commons.lang3.tuple.Pair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * The wandering trader's trades under the trade rebalance experiment: a list of (listings, how many to pick) pairs,
 * immutable in vanilla, so the wrapped copy replaces the whole list.
 */
@Mixin(VillagerTrades.class)
public interface VillagerTradesAccessor {
    @Accessor("EXPERIMENTAL_WANDERING_TRADER_TRADES")
    static List<Pair<VillagerTrades.ItemListing[], Integer>> scalpel$experimentalWanderer() {
        throw new AssertionError();
    }

    @Accessor("EXPERIMENTAL_WANDERING_TRADER_TRADES")
    @Mutable
    static void scalpel$setExperimentalWanderer(List<Pair<VillagerTrades.ItemListing[], Integer>> trades) {
        throw new AssertionError();
    }
}
