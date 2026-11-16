package com.rinkynooble.scalpel.common.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.rinkynooble.scalpel.common.Scalpel;
import com.rinkynooble.scalpel.core.ContentType;
import com.rinkynooble.scalpel.core.Decision;
import com.rinkynooble.scalpel.core.Resolver;
import com.rinkynooble.scalpel.core.ScalpelCore;
import com.rinkynooble.scalpel.core.json.JsonScrub;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The files of a datapack registry, before any is read. Some registries can't lose a reference inside a file, only the
 * whole entry: a trim material is made from one item, a trim pattern comes from one template item. When that item was
 * removed, the file would fail to load and the world with it ("Failed to load datapacks"), so the entry is left out.
 * Enchantments are decided here too ({@link CutEnchantments}).
 */
public final class RegistryFiles {
    /** Registries whose entries stand for one item, so an entry naming a removed item goes. */
    private static final Set<ResourceKey<? extends Registry<?>>> ONE_ITEM = Set.of(Registries.TRIM_MATERIAL, Registries.TRIM_PATTERN);

    private RegistryFiles() {
    }

    public static Map<ResourceLocation, Resource> beforeLoad(ResourceKey<? extends Registry<?>> registry, FileToIdConverter converter,
                                                             Map<ResourceLocation, Resource> files) {
        if (registry.equals(Registries.ENCHANTMENT)) {
            return CutEnchantments.beforeRegistryLoad(registry, converter, files);
        }
        if (!ONE_ITEM.contains(registry)) {
            return files;
        }
        ScalpelCore core = Scalpel.core();
        Resolver resolver = core.resolver();
        Predicate<String> removedItem = id -> {
            Decision decision = resolver.cutState(ContentType.ITEM, id);
            return decision != null && decision.action() == Decision.Action.REMOVE;
        };
        if (resolver.cutEntries(ContentType.ITEM).values().stream().noneMatch(d -> d.action() == Decision.Action.REMOVE)) {
            return files;
        }
        Map<ResourceLocation, Resource> kept = null;
        for (Map.Entry<ResourceLocation, Resource> file : files.entrySet()) {
            JsonElement json;
            try (Reader reader = file.getValue().openAsReader()) {
                json = JsonParser.parseReader(reader);
            } catch (Exception e) {
                continue; // The game reports a file it can't read; nothing for Scalpel to decide.
            }
            Set<String> hits = JsonScrub.findReferences(json, removedItem);
            if (hits.isEmpty()) {
                continue;
            }
            String id = converter.fileToId(file.getKey()).toString();
            DataFilter.change(core, "worldgen disabled", id, registry.location().getPath() + ": made from removed " + String.join(", ", hits));
            if (core.applies()) {
                if (kept == null) {
                    kept = new LinkedHashMap<>(files);
                }
                kept.remove(file.getKey());
            }
        }
        return kept == null ? files : kept;
    }
}
