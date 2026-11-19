package com.rinkynooble.scalpel.forge;

import com.rinkynooble.scalpel.common.Scalpel;
import com.rinkynooble.scalpel.common.command.ScalpelCommand;
import com.rinkynooble.scalpel.common.data.ServerData;
import com.rinkynooble.scalpel.common.data.TabsAndTrades;
import com.rinkynooble.scalpel.common.registry.RegistryCutter;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.listener.Priority;

import java.util.ArrayList;
import java.util.List;

/**
 * Forge events that drive the shared code: commands, creative tabs, server stop. Data load start, the final recipe
 * pass and the end of a data load are vanilla hooks in common/26.1 (as since 1.21.2), and villager trades are data
 * since 26.1, so neither needs a Forge event here.
 */
final class ForgeEvents {
    private ForgeEvents() {
    }

    static void register() {
        ServerStoppingEvent.BUS.addListener(event -> ServerData.onServerStopping());
        RegisterCommandsEvent.BUS.addListener(event -> ScalpelCommand.register(event.getDispatcher()));
        BuildCreativeModeTabContentsEvent.BUS.addListener(Priority.LOWEST, ForgeEvents::onBuildTab);
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
        TabsAndTrades.reportTabEntries(event.getTabKey().identifier().toString(), remove.size());
    }
}
