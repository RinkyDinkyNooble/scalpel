package com.rinkynooble.scalpel.common.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.rinkynooble.scalpel.core.ContentType;
import com.rinkynooble.scalpel.core.Decision;
import com.rinkynooble.scalpel.core.Resolver;
import com.rinkynooble.scalpel.core.ScalpelCore;
import com.rinkynooble.scalpel.core.json.JsonScrub;
import com.rinkynooble.scalpel.common.Scalpel;
import net.minecraft.resources.Identifier;

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
    /**
     * Where 26.3's vanilla worldgen data holds a block state: a bare block id, or {@code {"id": ..., "properties": ...}}
     * (the {@code {"Name": ...}} of older versions is gone). From a scan of every vanilla 26.3 worldgen file outside
     * features, placed features and processor lists (those are scrubbed or disabled whole): noise settings, surface
     * rules (material_rule), block state providers. Lists of states are left to the scrub (an entry goes).
     */
    private static final Set<String> BLOCK_STATE_KEYS = Set.of("Name", "id", "state", "default_state", "data",
            "default_block", "default_fluid", "result_state", "filler_block", "ore_block", "raw_ore_block");

    private WorldgenFilter() {
    }

    /**
     * @param registry the registry, e.g. {@code minecraft:worldgen/feature}, {@code forge:biome_modifier}
     *                 or a mod's own such as {@code lostcities:buildings}
     * @param element  the entry's id, e.g. {@code minecraft:ore_iron}
     */
    public static JsonElement filter(Identifier registry, Identifier element, JsonElement json) {
        if (!isWorldgen(registry)) {
            return json;
        }
        ScalpelCore core = Scalpel.core();
        Resolver resolver = core.resolver();
        if (json == null || !(hasCut(resolver, ContentType.BLOCK) || hasCut(resolver, ContentType.ENTITY))) {
            return json;
        }
        Predicate<String> isCut = id -> resolver.isCut(ContentType.BLOCK, id) || resolver.isCut(ContentType.ENTITY, id);
        if (!JsonScrub.references(json, isCut)) {
            return json;
        }
        String directory = prefixNamespace(registry);
        String id = element.toString();
        JsonElement work = json.deepCopy();
        String replacement = null;
        JsonScrub.Result result;
        switch (directory) {
            case "worldgen/placed_feature" -> {
                result = new JsonScrub.Result(true, 0, JsonScrub.findReferences(work, isCut));
                replacement = "{\"feature\":{\"type\":\"minecraft:no_op\"},\"placement\":[]}";
            }
            // 26.3 renamed configured features to worldgen/feature, with the config fields at the top level.
            case "worldgen/feature" -> {
                result = JsonScrub.scrub(work, isCut, ESCALATE);
                replacement = "{\"type\":\"minecraft:no_op\"}";
            }
            case "worldgen/processor_list" -> {
                result = JsonScrub.scrub(work, isCut, KEEP);
                replacement = "{\"processors\":[]}";
            }
            case "forge/biome_modifier", "forge/structure_modifier" -> {
                result = JsonScrub.scrub(work, isCut, KEEP);
                replacement = "{\"type\":\"forge:none\"}";
            }
            // NeoForge 26.3 has no "neoforge:none" any more: a modifier that removes nothing from no biome/structure.
            case "neoforge/biome_modifier" -> {
                result = JsonScrub.scrub(work, isCut, KEEP);
                replacement = "{\"type\":\"neoforge:remove_features\",\"biomes\":[],\"features\":[]}";
            }
            case "neoforge/structure_modifier" -> {
                result = JsonScrub.scrub(work, isCut, KEEP);
                replacement = "{\"type\":\"neoforge:remove_spawns\",\"structures\":[],\"entity_types\":[]}";
            }
            default -> {
                // Everything else (biomes, noise settings, carvers, and mods' own registries such as Lost Cities
                // palettes and parts): removing an entry could break the file's own structure, so a cut block
                // becomes air, like in structures. What counts as a block depends on who wrote the format:
                // - vanilla and Forge: only block states (26.3: a bare id or {"id": ...} under the keys in
                //   BLOCK_STATE_KEYS; before 26.3 {"Name": ...}). Other ids point into other registries and
                //   some share a block's id (the warped forest lists the placed feature minecraft:nether_sprouts);
                //   air there would point at nothing and the world would fail to load. scrub() takes those out of
                //   their list instead.
                // - a mod's own: any full id. Bare names are often the mod's own content (Lost Cities'
                //   "variant": "blackstone" is lostcities:blackstone, not the block), so those are left alone and reported.
                boolean vanillaFormat = isVanillaFormat(registry);
                JsonScrub.BareNames bare = vanillaFormat ? JsonScrub.BareNames.VANILLA : JsonScrub.BareNames.IGNORED;
                Predicate<String> atKey = vanillaFormat ? key -> key != null && BLOCK_STATE_KEYS.contains(key) : key -> true;
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
            if (anyRemoved(resolver, result.hits())) {
                core.warn("worldgen " + directory + " " + id + " uses cut " + hits + " and cannot be fixed automatically. "
                        + "The world may fail to load; remove it from the datapack or keep " + hits + ".");
            } else {
                // Redacted ids stay registered (vanilla ones as themselves, modded ones as placeholders), so the file
                // still loads; the match is often only a name (falling_anvil's message_id "anvil").
                core.log().detail("worldgen " + directory + " " + id + " names cut " + hits + ", left as it is: still registered");
            }
            return json;
        }
        DataFilter.change(core, "worldgen disabled", id, directory + ": uses " + hits);
        return core.applies() ? JsonParser.parseString(replacement) : json;
    }

    /** The folder a registry's files live in: {@code worldgen/biome}, or {@code lostcities/buildings} for a mod's own (as Forge's ForgeHooks.prefixNamespace). */
    private static String prefixNamespace(Identifier registry) {
        return registry.getNamespace().equals("minecraft") ? registry.getPath() : registry.getNamespace() + "/" + registry.getPath();
    }

    private static boolean hasCut(Resolver resolver, ContentType type) {
        return !resolver.cutIds(type).isEmpty();
    }

    /** True when one of the ids is really gone from its registry (removed, not redacted). */
    private static boolean anyRemoved(Resolver resolver, Set<String> ids) {
        for (String id : ids) {
            for (ContentType type : new ContentType[]{ContentType.BLOCK, ContentType.ENTITY}) {
                Decision decision = resolver.cutState(type, id);
                if (decision != null && decision.action() == Decision.Action.REMOVE) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Vanilla registries outside {@code worldgen/} that place blocks or name generation settings. */
    private static final Set<String> VANILLA_WORLDGEN = Set.of("dimension", "dimension_type", "block_transformer");

    /**
     * Which registries this filter reads: vanilla's worldgen ones, and every registry of a loader or a mod (biome
     * modifiers, mods' own such as Lost Cities). Since 26.3 every datapack registry file comes through the same
     * reader, recipes, advancements and loot tables included; those (and trades, enchantments, trims) have their own
     * filters, and other vanilla registries (damage types, variants, dialogs, ...) name no block to place. Reading them
     * here only matched names that aren't blocks (a recipe's result item, an advancement's icon).
     */
    private static boolean isWorldgen(Identifier registry) {
        if (!registry.getNamespace().equals("minecraft")) {
            return true;
        }
        String path = registry.getPath();
        return path.startsWith("worldgen/") || VANILLA_WORLDGEN.contains(path);
    }

    /** Vanilla's and the loaders' registries are read by codecs that take a bare name as {@code minecraft:}. */
    private static boolean isVanillaFormat(Identifier registry) {
        String ns = registry.getNamespace();
        return ns.equals("minecraft") || ns.equals("forge") || ns.equals("neoforge") || ns.equals("fabric");
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

}
