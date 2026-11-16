package com.rinkynooble.scalpel.forge;

import com.rinkynooble.scalpel.common.Scalpel;
import com.rinkynooble.scalpel.common.command.ScalpelCommand;
import com.rinkynooble.scalpel.common.data.ServerData;
import com.rinkynooble.scalpel.common.data.TabsAndTrades;
import com.rinkynooble.scalpel.common.registry.RegistryCutter;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.event.village.VillagerTradesEvent;
import net.minecraftforge.event.village.WandererTradesEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;

import java.util.ArrayList;
import java.util.List;

/** Forge events that drive the shared code: data loads, commands, creative tabs and trades. */
final class ForgeEvents {
    private ForgeEvents() {
    }

    static void register(IEventBus modBus, IEventBus forgeBus) {
        forgeBus.addListener(EventPriority.HIGHEST, (AddReloadListenerEvent event) -> ServerData.onDataLoadStart());
        // Listeners apply in order, so the last one added runs after RecipeManager, KubeJS and CraftTweaker.
        forgeBus.addListener(EventPriority.LOWEST, (AddReloadListenerEvent event) ->
                event.addListener(ServerData.finalRecipePass(event.getServerResources(), event.getRegistryAccess())));
        forgeBus.addListener((TagsUpdatedEvent event) -> {
            if (event.getUpdateCause() == TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD) {
                ServerData.afterDataLoad(event.getRegistryAccess());
            }
        });
        forgeBus.addListener((ServerStoppingEvent event) -> ServerData.onServerStopping());
        forgeBus.addListener((RegisterCommandsEvent event) -> ScalpelCommand.register(event.getDispatcher()));
        modBus.addListener(EventPriority.LOWEST, ForgeEvents::onBuildTab);
        forgeBus.addListener(EventPriority.LOWEST, ForgeEvents::onVillagerTrades);
        forgeBus.addListener(EventPriority.LOWEST, ForgeEvents::onWandererTrades);
    }

    private static void onBuildTab(BuildCreativeModeTabContentsEvent event) {
        if (!Scalpel.core().applies()) {
            return;
        }
        List<ItemStack> remove = new ArrayList<>();
        for (var entry : event.getEntries()) {
            if (RegistryCutter.isUnlisted(entry.getKey())) {
                remove.add(entry.getKey());
            }
        }
        for (ItemStack stack : remove) {
            event.getEntries().remove(stack);
        }
        TabsAndTrades.reportTabEntries(event.getTabKey().location().toString(), remove.size());
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
