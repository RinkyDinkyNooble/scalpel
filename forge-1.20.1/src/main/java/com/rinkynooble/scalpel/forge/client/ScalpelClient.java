package com.rinkynooble.scalpel.forge.client;

import com.rinkynooble.scalpel.forge.content.Redacted;
import com.rinkynooble.scalpel.forge.content.RemovedItem;
import com.rinkynooble.scalpel.forge.registry.RegistryCutter;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Client-only wiring: placeholder models, the redacted entity renderer, and tooltips. */
public final class ScalpelClient {
    private static PlaceholderPack pack;

    private ScalpelClient() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener(ScalpelClient::addPlaceholderPack);
        modBus.addListener(EventPriority.LOWEST, ScalpelClient::registerRenderers);
        MinecraftForge.EVENT_BUS.addListener(ScalpelClient::tooltip);
    }

    private static synchronized PlaceholderPack pack() {
        if (pack == null) {
            pack = new PlaceholderPack();
        }
        return pack;
    }

    private static void addPlaceholderPack(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) {
            return;
        }
        event.addRepositorySource(consumer -> {
            PlaceholderPack placeholders = pack();
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

    /**
     * Keeps only Scalpel's blockstate file for placeholder blocks. Otherwise the original mod's blockstate file loads
     * too and logs a warning for every variant the one-state placeholder does not have.
     */
    public static <T> Map<ResourceLocation, List<T>> filterBlockstates(Map<ResourceLocation, List<T>> stacks,
                                                                       java.util.function.Function<T, String> source) {
        PlaceholderPack placeholders = pack;
        if (placeholders == null || placeholders.isEmpty()) {
            return stacks;
        }
        Map<ResourceLocation, List<T>> filtered = null;
        for (Map.Entry<ResourceLocation, List<T>> entry : stacks.entrySet()) {
            if (!placeholders.providesBlockstate(entry.getKey()) || entry.getValue().size() < 2) {
                continue;
            }
            if (filtered == null) {
                filtered = new HashMap<>(stacks);
            }
            List<T> ours = new ArrayList<>();
            for (T json : entry.getValue()) {
                if (PlaceholderPack.ID.equals(source.apply(json))) {
                    ours.add(json);
                }
            }
            filtered.put(entry.getKey(), ours);
        }
        return filtered == null ? stacks : filtered;
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
        Item item = event.getItemStack().getItem();
        if (item instanceof Redacted) {
            return;
        }
        if (RemovedItem.ID.equals(RegistryCutter.itemId(item))) {
            event.getToolTip().add(Component.translatable("tooltip.scalpel.removed").withStyle(ChatFormatting.GRAY));
        } else if (RegistryCutter.isCutItem(item)) {
            event.getToolTip().add(Component.translatable("tooltip.scalpel.hidden").withStyle(ChatFormatting.GRAY));
        }
    }
}
