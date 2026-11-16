package com.rinkynooble.scalpel.forge.client;

import com.rinkynooble.scalpel.common.client.PlaceholderPack;
import com.rinkynooble.scalpel.common.client.Placeholders;
import com.rinkynooble.scalpel.common.client.RedactedEntityRenderer;
import com.rinkynooble.scalpel.common.registry.RegistryCutter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;

/** Client-only wiring on Forge: placeholder models, the redacted entity renderer, and tooltips. */
public final class ScalpelClient {
    private ScalpelClient() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener(ScalpelClient::addPlaceholderPack);
        modBus.addListener(EventPriority.LOWEST, ScalpelClient::registerRenderers);
        MinecraftForge.EVENT_BUS.addListener(ScalpelClient::tooltip);
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
            Pack created = Pack.readMetaAndCreate(PlaceholderPack.ID, Component.literal("Scalpel placeholders"), true,
                    id -> placeholders, PackType.CLIENT_RESOURCES, Pack.Position.TOP, PackSource.BUILT_IN);
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
