package com.rinkynooble.scalpel.fabric;

import com.rinkynooble.scalpel.common.Scalpel;
import com.rinkynooble.scalpel.common.command.ScalpelCommand;
import com.rinkynooble.scalpel.common.content.RemovedItem;
import com.rinkynooble.scalpel.common.data.ServerData;
import com.rinkynooble.scalpel.core.SettingsFile;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import java.io.IOException;

/**
 * Fabric entry point. Registration itself is cut in {@code MappedRegistryMixin}, which runs before any mod's initializer
 * (vanilla's own content registers first); this wires the rest: the removed item, commands, the handshake. Villager
 * trades are data since 26.1, so the shared data filter handles them; there is nothing to wrap here.
 */
public final class ScalpelFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        try {
            // Fabric has no config system that would write it, so the file appears with every setting at its default.
            SettingsFile.writeDefaultsIfMissing(Scalpel.platform().configDir().resolve(SettingsFile.RELATIVE_PATH));
        } catch (IOException e) {
            Scalpel.LOGGER.warn("Could not write the default settings file: {}", e.getMessage());
        }
        Identifier removed = Identifier.fromNamespaceAndPath(Scalpel.MOD_ID, RemovedItem.PATH);
        FabricPlatform.removedBlock = Registry.register(BuiltInRegistries.BLOCK, removed, RemovedItem.newBlock());
        FabricPlatform.removedItem = Registry.register(BuiltInRegistries.ITEM, removed, RemovedItem.newItem());

        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> ScalpelCommand.register(dispatcher));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> ServerData.onServerStopping());
        FabricHandshake.registerServer();
    }
}
