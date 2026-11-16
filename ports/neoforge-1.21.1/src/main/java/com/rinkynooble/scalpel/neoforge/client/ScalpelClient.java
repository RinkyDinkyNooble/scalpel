package com.rinkynooble.scalpel.neoforge.client;

import com.rinkynooble.scalpel.common.client.PlaceholderPack;
import com.rinkynooble.scalpel.common.client.Placeholders;
import com.rinkynooble.scalpel.common.client.RedactedEntityRenderer;
import com.rinkynooble.scalpel.common.registry.RegistryCutter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Client-only wiring on NeoForge: placeholder models, the redacted entity renderer, and tooltips. */
public final class ScalpelClient {
    private static final PackSelectionConfig ALWAYS_ON_TOP = new PackSelectionConfig(true, Pack.Position.TOP, true);

    private ScalpelClient() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener(ScalpelClient::addPlaceholderPack);
        modBus.addListener(EventPriority.LOWEST, ScalpelClient::registerRenderers);
        NeoForge.EVENT_BUS.addListener(ScalpelClient::tooltip);
    }

    private static void addPlaceholderPack(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) {
            return;
        }
        event.addRepositorySource(consumer -> {
            PlaceholderPack placeholders = Placeholders.pack();
            if (placeholders.isEmpty()) {
                return;
            }
            Pack created = Pack.readMetaAndCreate(PlaceholderPack.LOCATION, new Pack.ResourcesSupplier() {
                @Override
                public PackResources openPrimary(PackLocationInfo location) {
                    return placeholders;
                }

                @Override
                public PackResources openFull(PackLocationInfo location, Pack.Metadata metadata) {
                    return placeholders;
                }
            }, PackType.CLIENT_RESOURCES, ALWAYS_ON_TOP);
            if (created != null) {
                consumer.accept(created);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            if (RegistryCutter.isPlaceholderEntityType(type)) {
                event.registerEntityRenderer((EntityType<Entity>) type, RedactedEntityRenderer::new);
            }
        }
    }

    private static void tooltip(ItemTooltipEvent event) {
        Component line = Placeholders.tooltipLine(event.getItemStack().getItem());
        if (line != null) {
            event.getToolTip().add(line);
        }
    }
}
