package com.rinkynooble.scalpel.neoforge;

import com.rinkynooble.scalpel.common.Scalpel;
import com.rinkynooble.scalpel.common.command.ScalpelCommand;
import com.rinkynooble.scalpel.common.data.ServerData;
import com.rinkynooble.scalpel.common.data.TabsAndTrades;
import com.rinkynooble.scalpel.common.registry.RegistryCutter;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;
import net.neoforged.neoforge.event.village.WandererTradesEvent;

import java.util.ArrayList;
import java.util.List;

/** NeoForge events that drive the shared code: data loads, commands, creative tabs and trades. */
final class NeoForgeEvents {
    private NeoForgeEvents() {
    }

    static void register(IEventBus modBus, IEventBus gameBus) {
        // The data load starts at ReloadableServerResources#loadResources (ReloadableServerResourcesMixin): loot tables
        // load before this event since 1.20.5.
        // Listeners apply in order, so the last one added runs after RecipeManager, KubeJS and CraftTweaker.
        gameBus.addListener(EventPriority.LOWEST, (AddReloadListenerEvent event) ->
                event.addListener(ServerData.finalRecipePass(event.getServerResources(), event.getRegistryAccess())));
        gameBus.addListener((TagsUpdatedEvent event) -> {
            if (event.getUpdateCause() == TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD) {
                ServerData.afterDataLoad(event.getRegistryAccess());
            }
        });
        gameBus.addListener((ServerStoppingEvent event) -> ServerData.onServerStopping());
        gameBus.addListener((RegisterCommandsEvent event) -> ScalpelCommand.register(event.getDispatcher()));
        modBus.addListener(EventPriority.LOWEST, NeoForgeEvents::onBuildTab);
        gameBus.addListener(EventPriority.LOWEST, NeoForgeEvents::onVillagerTrades);
        gameBus.addListener(EventPriority.LOWEST, NeoForgeEvents::onWandererTrades);
    }

    private static void onBuildTab(BuildCreativeModeTabContentsEvent event) {
        if (!Scalpel.core().applies()) {
            return;
        }
        List<ItemStack> parent = new ArrayList<>();
        for (ItemStack stack : event.getParentEntries()) {
            if (RegistryCutter.isUnlisted(stack)) {
                parent.add(stack);
            }
        }
        List<ItemStack> search = new ArrayList<>();
        for (ItemStack stack : event.getSearchEntries()) {
            if (RegistryCutter.isUnlisted(stack)) {
                search.add(stack);
            }
        }
        for (ItemStack stack : parent) {
            event.remove(stack, CreativeModeTab.TabVisibility.PARENT_TAB_ONLY);
        }
        for (ItemStack stack : search) {
            event.remove(stack, CreativeModeTab.TabVisibility.SEARCH_TAB_ONLY);
        }
        TabsAndTrades.reportTabEntries(event.getTabKey().location().toString(), Math.max(parent.size(), search.size()));
    }

    private static void onVillagerTrades(VillagerTradesEvent event) {
        String source = "villager " + event.getType();
        Int2ObjectMap<List<VillagerTrades.ItemListing>> trades = event.getTrades();
        for (Int2ObjectMap.Entry<List<VillagerTrades.ItemListing>> level : trades.int2ObjectEntrySet()) {
            // A copy, because another mod's listener may have left an immutable list here.
            List<VillagerTrades.ItemListing> wrapped = new ArrayList<>(level.getValue());
            TabsAndTrades.wrapAll(wrapped, source + " level " + level.getIntKey());
            level.setValue(wrapped);
        }
    }

    private static void onWandererTrades(WandererTradesEvent event) {
        TabsAndTrades.wrapAll(event.getGenericTrades(), "wandering trader");
        TabsAndTrades.wrapAll(event.getRareTrades(), "wandering trader (rare)");
    }
}
