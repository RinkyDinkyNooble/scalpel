package com.rinkynooble.scalpel.core.json;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonScrubTest {
    private static final Predicate<String> CUT = Set.of("deco:lamp", "minecraft:dead_bush", "mobs:blob")::contains;
    private static final JsonScrub.Policy LOOT = new JsonScrub.Policy(JsonScrub.EmptyArrays.KEEP, Set.of("conditions", "functions"));
    private static final JsonScrub.Policy FEATURE = new JsonScrub.Policy(JsonScrub.EmptyArrays.ESCALATE, Set.of());

    private static JsonObject json(String text) {
        return JsonParser.parseString(text.replace('\'', '"')).getAsJsonObject();
    }

    @Test
    void readsIdsLikeTheGame() {
        assertEquals("minecraft:stone", JsonScrub.asId("stone"));
        assertEquals("deco:lamp", JsonScrub.asId("deco:lamp"));
        assertNull(JsonScrub.asId("#forge:ores"));
        assertNull(JsonScrub.asId("Hello World"));
    }

    @Test
    void readsBlockStateAndNbtStrings() {
        assertEquals("deco:lamp", JsonScrub.asId("deco:lamp[lit=true]"));
        assertEquals("minecraft:dead_bush", JsonScrub.asId("dead_bush[age=1]"));
        assertEquals("deco:lamp", JsonScrub.asId("deco:lamp{Damage:3}"));
        assertNull(JsonScrub.asId("[1,2]"));
        assertTrue(JsonScrub.references(json("{'block':'deco:lamp[lit=true]'}"), CUT));
    }

    @Test
    void replacesReferencesInPlace() {
        JsonObject palette = json("{'palette':[{'char':'a','block':'deco:lamp[lit=true]'},{'char':'b','block':'minecraft:stone'}],"
                + "'blocks':['deco:lamp','minecraft:dirt'],'mobs':{'mobs:blob':1}}");
        Set<String> replaced = JsonScrub.replaceReferences(palette, CUT, "minecraft:air");
        assertEquals(Set.of("deco:lamp"), replaced);
        assertEquals("minecraft:air", palette.getAsJsonArray("palette").get(0).getAsJsonObject().get("block").getAsString());
        assertEquals("minecraft:stone", palette.getAsJsonArray("palette").get(1).getAsJsonObject().get("block").getAsString());
        assertEquals("minecraft:air", palette.getAsJsonArray("blocks").get(0).getAsString());
        assertTrue(palette.getAsJsonObject("mobs").has("mobs:blob"), "keys are left for scrub()");
    }

    @Test
    void bareNamesCanBeIgnored() {
        assertNull(JsonScrub.asId("dead_bush", JsonScrub.BareNames.IGNORED));
        assertNull(JsonScrub.asId("dead_bush[age=1]", JsonScrub.BareNames.IGNORED));
        assertEquals("deco:lamp", JsonScrub.asId("deco:lamp[lit=true]", JsonScrub.BareNames.IGNORED));
        assertEquals("minecraft:dead_bush", JsonScrub.asId("minecraft:dead_bush", JsonScrub.BareNames.IGNORED));
    }

    @Test
    void modDataKeepsItsOwnBareNames() {
        // Lost Cities' building7: "variant": "blackstone" is its own variant, "damaged" is a real block.
        String text = "{'palette':[{'char':'#','variant':'dead_bush','damaged':'minecraft:dead_bush'},{'char':'l','block':'deco:lamp'}]}";
        JsonObject vanillaRead = json(text);
        assertEquals(Set.of("minecraft:dead_bush", "deco:lamp"), JsonScrub.replaceReferences(vanillaRead, CUT, "minecraft:air"));
        assertEquals("minecraft:air", vanillaRead.getAsJsonArray("palette").get(0).getAsJsonObject().get("variant").getAsString());

        JsonObject modRead = json(text);
        Set<String> replaced = JsonScrub.replaceReferences(modRead, CUT, "minecraft:air", JsonScrub.BareNames.IGNORED, key -> true);
        assertEquals(Set.of("minecraft:dead_bush", "deco:lamp"), replaced);
        JsonObject hash = modRead.getAsJsonArray("palette").get(0).getAsJsonObject();
        assertEquals("dead_bush", hash.get("variant").getAsString());
        assertEquals("minecraft:air", hash.get("damaged").getAsString());
        assertEquals("minecraft:air", modRead.getAsJsonArray("palette").get(1).getAsJsonObject().get("block").getAsString());
        assertEquals(Set.of("minecraft:dead_bush"), JsonScrub.findBareReferences(modRead, CUT));
    }

    @Test
    void vanillaFormatOnlyReplacesBlockStates() {
        // The warped forest lists the placed feature minecraft:nether_sprouts, which shares the block's id. Air there
        // would point at a feature that doesn't exist; only block states ({"Name": ...}) become air, scrub() does the rest.
        JsonObject file = json("{'features':[[],['minecraft:dead_bush','minecraft:ore_iron']],"
                + "'default_block':{'Name':'minecraft:dead_bush'},"
                + "'surface_rule':{'type':'minecraft:block','result_state':{'Name':'deco:lamp','Properties':{'lit':'true'}}}}");
        Set<String> replaced = JsonScrub.replaceReferences(file, CUT, "minecraft:air", JsonScrub.BareNames.VANILLA, "Name"::equals);
        assertEquals(Set.of("minecraft:dead_bush", "deco:lamp"), replaced);
        assertEquals("minecraft:air", file.getAsJsonObject("default_block").get("Name").getAsString());
        assertEquals("minecraft:air", file.getAsJsonObject("surface_rule").getAsJsonObject("result_state").get("Name").getAsString());
        assertEquals("minecraft:dead_bush", file.getAsJsonArray("features").get(1).getAsJsonArray().get(0).getAsString());

        JsonScrub.Result result = JsonScrub.scrub(file, CUT, LOOT);
        assertFalse(result.dropWhole());
        assertEquals(1, result.removed());
        assertEquals("minecraft:ore_iron", file.getAsJsonArray("features").get(1).getAsJsonArray().get(0).getAsString());
    }

    @Test
    void scrubCanIgnoreBareNames() {
        JsonObject spawns = json("{'mobs':[{'type':'dead_bush'},{'type':'minecraft:dead_bush'},{'type':'mobs:blob'}]}");
        JsonScrub.Result result = JsonScrub.scrub(spawns, CUT, LOOT, JsonScrub.BareNames.IGNORED);
        assertEquals(2, result.removed());
        assertEquals(1, spawns.getAsJsonArray("mobs").size());
        assertEquals("dead_bush", spawns.getAsJsonArray("mobs").get(0).getAsJsonObject().get("type").getAsString());
        assertTrue(JsonScrub.findBareReferences(json("{'a':'deco:lamp','b':'#dead_bush','c':'stone'}"), CUT).isEmpty());
    }

    @Test
    void findsBareVanillaNames() {
        assertEquals(Set.of("minecraft:dead_bush"), JsonScrub.findReferences(json("{'item':'dead_bush'}"), CUT));
        assertFalse(JsonScrub.references(json("{'tag':'#deco:lamp'}"), CUT));
    }

    @Test
    void removesOneLootEntry() {
        JsonObject table = json("{'pools':[{'rolls':1,'entries':["
                + "{'type':'minecraft:item','name':'deco:lamp'},"
                + "{'type':'minecraft:item','name':'deco:chair'}]}]}");
        JsonScrub.Result result = JsonScrub.scrub(table, CUT, LOOT);
        assertFalse(result.dropWhole());
        assertEquals(1, result.removed());
        assertEquals(1, table.getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries").size());
    }

    @Test
    void conditionTakesItsEntryWithIt() {
        JsonObject table = json("{'pools':[{'rolls':1,'entries':["
                + "{'type':'minecraft:item','name':'deco:chair','conditions':[{'condition':'minecraft:block_state_property','block':'deco:lamp'}]},"
                + "{'type':'minecraft:item','name':'deco:table'}]}]}");
        JsonScrub.scrub(table, CUT, LOOT);
        JsonElement entries = table.getAsJsonArray("pools").get(0).getAsJsonObject().get("entries");
        assertEquals(1, entries.getAsJsonArray().size());
        assertEquals("deco:table", entries.getAsJsonArray().get(0).getAsJsonObject().get("name").getAsString());
    }

    @Test
    void emptyLootPoolStays() {
        JsonObject table = json("{'pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'deco:lamp'}]}]}");
        JsonScrub.Result result = JsonScrub.scrub(table, CUT, LOOT);
        assertFalse(result.dropWhole());
        assertEquals(1, table.getAsJsonArray("pools").size());
    }

    @Test
    void oreTargetIsRemovedAndEmptyTargetsEscalate() {
        JsonObject ore = json("{'type':'minecraft:ore','config':{'size':9,'targets':["
                + "{'target':{'predicate_type':'minecraft:tag_match','tag':'minecraft:stone_ore_replaceables'},'state':{'Name':'deco:lamp'}},"
                + "{'target':{'predicate_type':'minecraft:tag_match','tag':'minecraft:deepslate_ore_replaceables'},'state':{'Name':'deco:chair'}}]}}");
        JsonScrub.Result result = JsonScrub.scrub(ore, CUT, FEATURE);
        assertFalse(result.dropWhole());
        assertEquals(1, ore.getAsJsonObject("config").getAsJsonArray("targets").size());

        JsonObject single = json("{'type':'minecraft:ore','config':{'targets':[{'state':{'Name':'deco:lamp'}}]}}");
        assertTrue(JsonScrub.scrub(single, CUT, FEATURE).dropWhole());
    }

    @Test
    void referenceOutsideAnyArrayDropsWhole() {
        JsonObject feature = json("{'type':'minecraft:simple_block','config':{'to_place':{'type':'minecraft:simple_state_provider','state':{'Name':'deco:lamp'}}}}");
        assertTrue(JsonScrub.scrub(feature, CUT, FEATURE).dropWhole());
    }

    @Test
    void cutIdAsObjectKeyIsRemoved() {
        JsonObject biome = json("{'spawn_costs':{'mobs:blob':{'energy_budget':0.1,'charge':0.7},'minecraft:zombie':{'energy_budget':0.1,'charge':0.7}},"
                + "'spawners':{'monster':[{'type':'mobs:blob','weight':10,'minCount':1,'maxCount':2}]}}");
        JsonScrub.Result result = JsonScrub.scrub(biome, CUT, LOOT);
        assertFalse(result.dropWhole());
        assertEquals(2, result.removed());
        assertFalse(biome.getAsJsonObject("spawn_costs").has("mobs:blob"));
        assertTrue(biome.getAsJsonObject("spawn_costs").has("minecraft:zombie"));
        assertEquals(0, biome.getAsJsonObject("spawners").getAsJsonArray("monster").size());
    }

    @Test
    void untouchedWhenNothingIsCut() {
        JsonObject table = json("{'pools':[{'entries':[{'type':'minecraft:item','name':'deco:chair'}]}]}");
        JsonScrub.Result result = JsonScrub.scrub(table, CUT, LOOT);
        assertFalse(result.changed());
    }
}
