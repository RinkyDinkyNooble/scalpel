package com.rinkynooble.scalpel.neoforge;

import com.rinkynooble.scalpel.common.Scalpel;
import com.rinkynooble.scalpel.common.command.ScalpelCommand;
import com.rinkynooble.scalpel.common.data.ServerData;
import com.rinkynooble.scalpel.common.data.TabsAndTrades;
import com.rinkynooble.scalpel.common.registry.RegistryCutter;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.util.ArrayList;
import java.util.List;

/** NeoForge events that drive the shared code: commands, creative tabs and the report at shutdown. */
final class NeoForgeEvents {
    private NeoForgeEvents() {
    }

    static void register(IEventBus modBus, IEventBus gameBus) {
        // Data load start, final recipe pass and end are vanilla hooks in common/26.1 (TagLoaderMixin,
        // RecipeManagerMixin): tags load before every reload listener, and finalizeRecipeLoading runs after all of them.
        // Villager trades are data since 26.1 (no trade events): DataFilter filters them as their registry loads.
        gameBus.addListener((ServerStoppingEvent event) -> ServerData.onServerStopping());
        gameBus.addListener((RegisterCommandsEvent event) -> ScalpelCommand.register(event.getDispatcher()));
        modBus.addListener(EventPriority.LOWEST, NeoForgeEvents::onBuildTab);
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
        TabsAndTrades.reportTabEntries(event.getTabKey().identifier().toString(), Math.max(parent.size(), search.size()));
    }
}
