package com.rinkynooble.scalpel.forge.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.rinkynooble.scalpel.core.ContentType;
import com.rinkynooble.scalpel.core.Resolver;
import com.rinkynooble.scalpel.core.ScalpelCore;
import com.rinkynooble.scalpel.core.json.JsonScrub;
import com.rinkynooble.scalpel.forge.Scalpel;
import net.minecraft.resources.ResourceLocation;

import java.util.Set;
import java.util.function.Predicate;

/**
 * World generation files that name a cut block or entity would fail to load, and a world with a broken worldgen
 * registry does not open. This takes the reference out (one ore target, one spawn entry), or when that is not
 * possible, replaces the file with something that does nothing. Every change is logged so it can be fixed at the source.
 */
public final class WorldgenFilter {
    private static final JsonScrub.Policy ESCALATE = new JsonScrub.Policy(JsonScrub.EmptyArrays.ESCALATE, Set.of());
    private static final JsonScrub.Policy KEEP = new JsonScrub.Policy(JsonScrub.EmptyArrays.KEEP, Set.of());

    private WorldgenFilter() {
    }

    /**
     * @param directory the registry folder, e.g. {@code worldgen/configured_feature} or {@code forge/biome_modifier}
     * @param file      the file's resource location, e.g. {@code minecraft:worldgen/configured_feature/ore_iron.json}
     */
    public static JsonElement filter(String directory, ResourceLocation file, JsonElement json) {
        ScalpelCore core = Scalpel.core();
        Resolver resolver = core.resolver();
        if (json == null || !(hasCut(resolver, ContentType.BLOCK) || hasCut(resolver, ContentType.ENTITY))) {
            return json;
        }
        Predicate<String> isCut = id -> resolver.isCut(ContentType.BLOCK, id) || resolver.isCut(ContentType.ENTITY, id);
        if (!JsonScrub.references(json, isCut)) {
            return json;
        }
        String id = idOf(directory, file);
        JsonElement work = json.deepCopy();
        String replacement = null;
        JsonScrub.Result result;
        switch (directory) {
            case "worldgen/placed_feature" -> {
                result = new JsonScrub.Result(true, 0, JsonScrub.findReferences(work, isCut));
                replacement = "{\"feature\":{\"type\":\"minecraft:no_op\",\"config\":{}},\"placement\":[]}";
            }
            case "worldgen/configured_feature" -> {
                result = JsonScrub.scrub(work, isCut, ESCALATE);
                replacement = "{\"type\":\"minecraft:no_op\",\"config\":{}}";
            }
            case "worldgen/processor_list" -> {
                result = JsonScrub.scrub(work, isCut, KEEP);
                replacement = "{\"processors\":[]}";
            }
            case "forge/biome_modifier", "forge/structure_modifier" -> {
                result = JsonScrub.scrub(work, isCut, KEEP);
                replacement = "{\"type\":\"forge:none\"}";
            }
            default -> result = JsonScrub.scrub(work, isCut, KEEP);
        }
        String hits = String.join(", ", result.hits());
        if (!result.dropWhole()) {
            DataFilter.change(core, "worldgen entries removed", id, directory + ": " + result.removed() + " for " + hits);
            return core.applies() ? work : json;
        }
        if (replacement == null) {
            core.warn("worldgen " + directory + " " + id + " uses cut " + hits + " and cannot be fixed automatically. "
                    + "The world may fail to load; remove it from the datapack or keep " + hits + ".");
            return json;
        }
        DataFilter.change(core, "worldgen disabled", id, directory + ": uses " + hits);
        return core.applies() ? JsonParser.parseString(replacement) : json;
    }

    private static boolean hasCut(Resolver resolver, ContentType type) {
        return !resolver.cutIds(type).isEmpty();
    }

    private static String idOf(String directory, ResourceLocation file) {
        String path = file.getPath();
        String prefix = directory + "/";
        if (path.startsWith(prefix)) {
            path = path.substring(prefix.length());
        }
        if (path.endsWith(".json")) {
            path = path.substring(0, path.length() - 5);
        }
        return file.getNamespace() + ":" + path;
    }
}
