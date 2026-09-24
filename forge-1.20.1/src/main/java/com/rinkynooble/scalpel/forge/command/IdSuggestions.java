package com.rinkynooble.scalpel.forge.command;

import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.rinkynooble.scalpel.core.ContentType;
import com.rinkynooble.scalpel.core.Decision;
import com.rinkynooble.scalpel.core.IdIndex;
import com.rinkynooble.scalpel.forge.Scalpel;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.loot.LootDataType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Sorted id indexes for command suggestions and pattern tests. Registry ids never change after startup, so their
 * index is built once. Data ids (recipes, loot tables, advancements, tags) are rebuilt after a data reload.
 * A lookup is a binary search plus a short scan, capped at {@link #LIMIT} results.
 */
public final class IdSuggestions {
    static final int LIMIT = 50;

    private static final Map<ContentType, IdIndex> indexes = new EnumMap<>(ContentType.class);
    private static IdIndex anyRegistry;

    private IdSuggestions() {
    }

    /** Forget data indexes; called when data reloads. */
    public static synchronized void dataChanged() {
        indexes.keySet().removeIf(type -> !type.isRegistry());
    }

    static synchronized IdIndex index(MinecraftServer server, ContentType type) {
        return indexes.computeIfAbsent(type, t -> new IdIndex(ids(server, t)));
    }

    /** Items, blocks and entities together, including removed ids, for {@code find} and {@code explain}. */
    static synchronized IdIndex anyRegistry(MinecraftServer server) {
        if (anyRegistry == null) {
            List<String> all = new ArrayList<>();
            for (ContentType type : ContentType.REGISTRY_TYPES) {
                all.addAll(index(server, type).all());
            }
            anyRegistry = new IdIndex(all);
        }
        return anyRegistry;
    }

    static Collection<String> ids(MinecraftServer server, ContentType type) {
        List<String> ids = new ArrayList<>();
        switch (type) {
            case ITEM -> addKeys(ids, BuiltInRegistries.ITEM);
            case BLOCK -> addKeys(ids, BuiltInRegistries.BLOCK);
            case ENTITY -> addKeys(ids, BuiltInRegistries.ENTITY_TYPE);
            case RECIPE -> server.getRecipeManager().getRecipeIds().forEach(id -> ids.add(id.toString()));
            case LOOT -> server.getLootData().getKeys(LootDataType.TABLE).forEach(id -> ids.add(id.toString()));
            case ADVANCEMENT -> server.getAdvancements().getAllAdvancements().forEach(a -> ids.add(a.getId().toString()));
            case TAG -> {
                BuiltInRegistries.ITEM.getTagNames().forEach(t -> ids.add(t.location().toString()));
                BuiltInRegistries.BLOCK.getTagNames().forEach(t -> ids.add(t.location().toString()));
                BuiltInRegistries.ENTITY_TYPE.getTagNames().forEach(t -> ids.add(t.location().toString()));
            }
        }
        if (type.isRegistry()) {
            // Removed ids are not registered, but they are exactly what people look up.
            for (Map.Entry<String, Decision> cut : Scalpel.core().resolver().cutEntries(type).entrySet()) {
                if (cut.getValue().action() == Decision.Action.REMOVE) {
                    ids.add(cut.getKey());
                }
            }
        }
        return ids;
    }

    private static void addKeys(List<String> ids, Registry<?> registry) {
        for (ResourceLocation key : registry.keySet()) {
            ids.add(key.toString());
        }
    }

    static SuggestionProvider<CommandSourceStack> registryIds() {
        return (context, builder) -> suggest(anyRegistry(context.getSource().getServer()), builder);
    }

    static SuggestionProvider<CommandSourceStack> idsOf(ContentType type) {
        return (context, builder) -> suggest(index(context.getSource().getServer(), type), builder);
    }

    static CompletableFuture<Suggestions> suggest(IdIndex index, SuggestionsBuilder builder) {
        String typed = builder.getRemaining();
        for (String id : index.suggest(typed, LIMIT)) {
            builder.suggest(id);
        }
        return builder.buildFuture();
    }
}
