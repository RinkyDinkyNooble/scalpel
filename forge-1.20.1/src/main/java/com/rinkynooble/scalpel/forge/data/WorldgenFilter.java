package com.rinkynooble.scalpel.forge.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.rinkynooble.scalpel.core.ContentType;
import com.rinkynooble.scalpel.core.Resolver;
import com.rinkynooble.scalpel.core.ScalpelCore;
import com.rinkynooble.scalpel.core.json.JsonScrub;
import com.rinkynooble.scalpel.forge.Scalpel;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.ForgeHooks;

import java.util.ArrayList;
import java.util.List;
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
     * @param registry the registry, e.g. {@code minecraft:worldgen/configured_feature}, {@code forge:biome_modifier}
     *                 or a mod's own such as {@code lostcities:buildings}
     * @param file     the file's resource location, e.g. {@code minecraft:worldgen/configured_feature/ore_iron.json}
     */
    public static JsonElement filter(ResourceLocation registry, ResourceLocation file, JsonElement json) {
        ScalpelCore core = Scalpel.core();
        Resolver resolver = core.resolver();
        if (json == null || !(hasCut(resolver, ContentType.BLOCK) || hasCut(resolver, ContentType.ENTITY))) {
            return json;
        }
        Predicate<String> isCut = id -> resolver.isCut(ContentType.BLOCK, id) || resolver.isCut(ContentType.ENTITY, id);
        if (!JsonScrub.references(json, isCut)) {
            return json;
        }
        String directory = ForgeHooks.prefixNamespace(registry);
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
            default -> {
                // Everything else (biomes, noise settings, carvers, and mods' own registries such as Lost Cities
                // palettes and parts): removing an entry could break the file's own structure, so a cut block
                // becomes air, like in structures. What counts as a block depends on who wrote the format:
                // - vanilla and Forge: only block states ({"Name": ...}). Other ids point into other registries and
                //   some share a block's id (the warped forest lists the placed feature minecraft:nether_sprouts);
                //   air there would point at nothing and the world would fail to load. scrub() takes those out of
                //   their list instead.
                // - a mod's own: any full id. Bare names are often the mod's own content (Lost Cities'
                //   "variant": "blackstone" is lostcities:blackstone, not the block), so those are left alone and reported.
                boolean vanillaFormat = isVanillaFormat(registry);
                JsonScrub.BareNames bare = vanillaFormat ? JsonScrub.BareNames.VANILLA : JsonScrub.BareNames.IGNORED;
                Predicate<String> atKey = vanillaFormat ? "Name"::equals : key -> true;
                Set<String> toAir = JsonScrub.replaceReferences(work, ref -> resolver.isCut(ContentType.BLOCK, ref), "minecraft:air", bare, atKey);
                if (!toAir.isEmpty()) {
                    DataFilter.change(core, "worldgen blocks replaced with air", id, directory + ": " + String.join(", ", toAir));
                }
                if (bare == JsonScrub.BareNames.IGNORED) {
                    Set<String> leftAlone = JsonScrub.findBareReferences(work, isCut);
                    if (!leftAlone.isEmpty()) {
                        DataFilter.change(core, "worldgen names left alone", id, directory + ": " + describeBare(leftAlone));
                    }
                }
                result = JsonScrub.scrub(work, isCut, KEEP, bare);
                if (!result.changed()) {
                    return core.applies() ? work : json;
                }
            }
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

    /** Vanilla's and Forge's registries are read by codecs that take a bare name as {@code minecraft:}. */
    private static boolean isVanillaFormat(ResourceLocation registry) {
        return registry.getNamespace().equals("minecraft") || registry.getNamespace().equals("forge");
    }

    /** {@code "blackstone" has no mod id, may be this mod's own name}, for the report. */
    private static String describeBare(Set<String> ids) {
        List<String> names = new ArrayList<>();
        for (String id : ids) {
            names.add("\"" + id.substring(id.indexOf(':') + 1) + "\"");
        }
        return String.join(", ", names) + (names.size() == 1
                ? " has no mod id, may be this mod's own name"
                : " have no mod id, may be this mod's own names");
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
