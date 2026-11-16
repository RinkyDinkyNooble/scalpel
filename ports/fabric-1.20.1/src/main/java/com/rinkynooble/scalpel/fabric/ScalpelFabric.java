package com.rinkynooble.scalpel.fabric;

import com.rinkynooble.scalpel.common.Scalpel;
import com.rinkynooble.scalpel.common.command.ScalpelCommand;
import com.rinkynooble.scalpel.common.content.RemovedItem;
import com.rinkynooble.scalpel.common.data.ServerData;
import com.rinkynooble.scalpel.common.data.TabsAndTrades;
import com.rinkynooble.scalpel.core.SettingsFile;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

/**
 * Fabric entry point. Registration itself is cut in {@code MappedRegistryMixin}, which runs before any mod's initializer
 * (vanilla's own content registers first); this wires the rest: the removed item, commands, trades, the handshake.
 */
public final class ScalpelFabric implements ModInitializer {
    private static boolean tradesWrapped;

    @Override
    public void onInitialize() {
        try {
            // Fabric has no config system that would write it, so the file appears with every setting at its default.
            SettingsFile.writeDefaultsIfMissing(Scalpel.platform().configDir().resolve(SettingsFile.RELATIVE_PATH));
        } catch (IOException e) {
            Scalpel.LOGGER.warn("Could not write the default settings file: {}", e.getMessage());
        }
        ResourceLocation removed = new ResourceLocation(Scalpel.MOD_ID, RemovedItem.PATH);
        FabricPlatform.removedBlock = Registry.register(BuiltInRegistries.BLOCK, removed, RemovedItem.newBlock());
        FabricPlatform.removedItem = Registry.register(BuiltInRegistries.ITEM, removed, RemovedItem.newItem());

        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> ScalpelCommand.register(dispatcher));
        ServerLifecycleEvents.SERVER_STARTING.register(server -> wrapTrades());
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> ServerData.onServerStopping());
        FabricHandshake.registerServer();
    }

    /**
     * Wraps every villager and wandering trader listing once, when the first server starts: Fabric API's trade helper
     * edits vanilla's tables during mod initialization, so by now every mod's trades are in.
     */
    private static synchronized void wrapTrades() {
        if (tradesWrapped) {
            return;
        }
        tradesWrapped = true;
        for (Map.Entry<VillagerProfession, Int2ObjectMap<VillagerTrades.ItemListing[]>> profession : VillagerTrades.TRADES.entrySet()) {
            String source = "villager " + BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession.getKey());
            Int2ObjectMap<VillagerTrades.ItemListing[]> levels = profession.getValue();
            Int2ObjectMap<VillagerTrades.ItemListing[]> result = wrapLevels(levels, level -> source + " level " + level);
            if (result != levels) {
                try {
                    profession.setValue(result);
                } catch (UnsupportedOperationException e) {
                    Scalpel.core().log().warn("Trades of " + source + " are left unfiltered: they are kept in maps that can't be changed.");
                }
            }
        }
        Int2ObjectMap<VillagerTrades.ItemListing[]> wanderer = VillagerTrades.WANDERING_TRADER_TRADES;
        if (wrapLevels(wanderer, level -> level == 2 ? "wandering trader (rare)" : "wandering trader") != wanderer) {
            Scalpel.core().log().warn("Wandering trader trades are left unfiltered: they are kept in a map that can't be changed.");
        }
    }

    /**
     * Wraps one profession's listings per level, in place with {@code put}: mods put their own map types here, and
     * entry.setValue isn't supported by all of them (RebornCore adds an {@code Int2ObjectArrayMap}, whose entries are
     * copies). A map that refuses {@code put} as well is left as it is and a wrapped copy is returned for the caller to
     * swap in.
     */
    private static Int2ObjectMap<VillagerTrades.ItemListing[]> wrapLevels(Int2ObjectMap<VillagerTrades.ItemListing[]> levels,
                                                                          IntFunction<String> source) {
        Int2ObjectMap<VillagerTrades.ItemListing[]> wrapped = new Int2ObjectOpenHashMap<>();
        for (int level : levels.keySet().toIntArray()) {
            wrapped.put(level, wrapped(levels.get(level), source.apply(level)));
        }
        try {
            levels.putAll(wrapped);
            return levels;
        } catch (UnsupportedOperationException e) {
            return wrapped;
        }
    }

    private static VillagerTrades.ItemListing[] wrapped(VillagerTrades.ItemListing[] listings, String source) {
        List<VillagerTrades.ItemListing> list = new ArrayList<>(Arrays.asList(listings));
        TabsAndTrades.wrapAll(list, source);
        return list.toArray(new VillagerTrades.ItemListing[0]);
    }
}
