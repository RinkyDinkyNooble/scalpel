package com.rinkynooble.scalpel.core.json;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LootEnchantmentsTest {
    private static final Predicate<String> REMOVED = Set.of("mod:gone", "minecraft:silk_touch")::contains;

    private static JsonElement json(String text) {
        return JsonParser.parseString(text);
    }

    private static void assertStrips(String before, String after, String... hits) {
        JsonElement root = json(before);
        assertEquals(Set.of(hits), LootEnchantments.strip(root, REMOVED));
        assertEquals(json(after), root);
    }

    @Test
    void setEnchantmentsLosesOnlyTheRemovedOne() {
        assertStrips("{\"functions\":[{\"function\":\"minecraft:set_enchantments\",\"enchantments\":{\"mod:gone\":1,\"mod:kept\":2}}]}",
                "{\"functions\":[{\"function\":\"minecraft:set_enchantments\",\"enchantments\":{\"mod:kept\":2}}]}", "mod:gone");
    }

    @Test
    void setEnchantmentsWithNothingLeftGoes() {
        assertStrips("{\"functions\":[{\"function\":\"minecraft:set_enchantments\",\"enchantments\":{\"mod:gone\":1}}]}",
                "{\"functions\":[]}", "mod:gone");
    }

    @Test
    void enchantRandomlyKeepsTheOthers() {
        assertStrips("{\"functions\":[{\"function\":\"minecraft:enchant_randomly\",\"enchantments\":[\"mod:gone\",\"mod:kept\"]}]}",
                "{\"functions\":[{\"function\":\"minecraft:enchant_randomly\",\"enchantments\":[\"mod:kept\"]}]}", "mod:gone");
    }

    /** An empty list means every enchantment, which isn't what the file asked for. */
    @Test
    void enchantRandomlyWithNothingLeftGoes() {
        assertStrips("{\"functions\":[{\"function\":\"minecraft:enchant_randomly\",\"enchantments\":[\"mod:gone\"]}]}",
                "{\"functions\":[]}", "mod:gone");
    }

    @Test
    void enchantRandomlyOverEverythingIsLeftAlone() {
        String text = "{\"functions\":[{\"function\":\"minecraft:enchant_randomly\"}]}";
        assertStrips(text, text);
    }

    @Test
    void applyBonusGoes() {
        assertStrips("{\"functions\":[{\"function\":\"minecraft:apply_bonus\",\"enchantment\":\"mod:gone\",\"formula\":\"minecraft:ore_drops\"},{\"function\":\"minecraft:explosion_decay\"}]}",
                "{\"functions\":[{\"function\":\"minecraft:explosion_decay\"}]}", "mod:gone");
    }

    @Test
    void tableBonusBecomesItsFirstChance() {
        assertStrips("{\"conditions\":[{\"condition\":\"minecraft:table_bonus\",\"enchantment\":\"mod:gone\",\"chances\":[0.25,0.5]}]}",
                "{\"conditions\":[{\"condition\":\"minecraft:random_chance\",\"chance\":0.25}]}", "mod:gone");
    }

    @Test
    void matchToolNeverPasses() {
        assertStrips("{\"conditions\":[{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"enchantments\":[{\"enchantment\":\"minecraft:silk_touch\",\"levels\":{\"min\":1}}]}}]}",
                "{\"conditions\":[{\"condition\":\"minecraft:random_chance\",\"chance\":0.0}]}", "minecraft:silk_touch");
    }

    /** Vanilla's "not silk touch" branch must then always pass, so the condition inside is rewritten, not the inverted one. */
    @Test
    void invertedConditionKeepsItsMeaning() {
        assertStrips("{\"condition\":\"minecraft:inverted\",\"term\":{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"enchantments\":[{\"enchantment\":\"minecraft:silk_touch\"}]}}}",
                "{\"condition\":\"minecraft:inverted\",\"term\":{\"condition\":\"minecraft:random_chance\",\"chance\":0.0}}", "minecraft:silk_touch");
    }

    @Test
    void otherFilesAreUntouched() {
        String text = "{\"pools\":[{\"entries\":[{\"type\":\"minecraft:item\",\"name\":\"minecraft:stone\",\"conditions\":[{\"condition\":\"minecraft:survives_explosion\"}]}]}]}";
        assertStrips(text, text);
    }
}
