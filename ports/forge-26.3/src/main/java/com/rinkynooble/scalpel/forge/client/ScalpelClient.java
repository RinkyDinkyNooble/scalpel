package com.rinkynooble.scalpel.forge.client;

import com.rinkynooble.scalpel.common.client.PlaceholderPack;
import com.rinkynooble.scalpel.common.client.Placeholders;
import com.rinkynooble.scalpel.common.client.RedactedEntityRenderer;
import com.rinkynooble.scalpel.common.registry.RegistryCutter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackMetadataResources;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.listener.Priority;

import java.util.stream.Stream;

/** Client-only wiring on Forge: placeholder models, the redacted entity renderer, and tooltips. */
public final class ScalpelClient {
    /** Required and at the top: always on, above every other pack. */
    private static final PackSelectionConfig ALWAYS_ON_TOP = new PackSelectionConfig(true, Pack.Position.TOP, false);

    private ScalpelClient() {
    }

    public static void init() {
        AddPackFindersEvent.BUS.addListener(ScalpelClient::addPlaceholderPack);
        // Posted once and then destroyed (SelfDestructing), so this has to be added during mod construction.
        EntityRenderersEvent.RegisterRenderers.BUS.addListener(Priority.LOWEST, ScalpelClient::registerRenderers);
        ItemTooltipEvent.BUS.addListener(ScalpelClient::tooltip);
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
            // 26.3: a supplier opens the metadata, then a stream of resource packs.
            Pack.ResourcesSupplier resources = new Pack.ResourcesSupplier() {
                @Override
                public PackMetadataResources openMetadata(PackLocationInfo location) {
                    return placeholders;
                }

                @Override
                public Stream<PackResources> openResources(PackLocationInfo location, Pack.Metadata metadata) {
                    return Stream.of(placeholders);
                }
            };
            Pack created = Pack.readMetaAndCreate(PlaceholderPack.LOCATION, resources, PackType.CLIENT_RESOURCES, ALWAYS_ON_TOP);
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
