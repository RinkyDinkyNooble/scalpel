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
