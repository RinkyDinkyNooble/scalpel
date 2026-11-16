package com.rinkynooble.scalpel.core.json;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipeIngredientsTest {
    private static final Predicate<String> CUT = Set.of("deco:lamp", "minecraft:copper_ingot")::contains;
    private static final Predicate<String> EMPTIED = Set.of("forge:ingots/copper", "forge:ores/quartz")::contains;

    private static JsonObject json(String text) {
        return JsonParser.parseString(text.replace('\'', '"')).getAsJsonObject();
    }

    private static Set<String> dead(String text) {
        return RecipeIngredients.deadTags(json(text), CUT, EMPTIED);
    }

    @Test
    void taczGunSmithTable() {
        // tacz:ammo/12g, shortened. Each material is an ingredient of its own.
        String recipe = "{'type':'tacz:gun_smith_table_crafting','materials':["
                + "{'item':{'tag':'forge:ingots/copper'},'count':15},{'item':{'tag':'forge:gunpowder'},'count':6}],"
                + "'result':{'type':'ammo','id':'tacz:12g','count':18}}";
        assertEquals(Set.of("forge:ingots/copper"), dead(recipe));
    }

    @Test
    void mekanismItemStackIngredient() {
        assertEquals(Set.of("forge:ores/quartz"), dead("{'type':'mekanism:enriching','input':{'ingredient':{'tag':'forge:ores/quartz'}},"
                + "'output':{'count':6,'item':'minecraft:quartz'}}"));
        assertTrue(dead("{'type':'mekanism:enriching','input':{'ingredient':{'tag':'forge:dusts/quartz'}},'output':{'item':'minecraft:quartz'}}").isEmpty());
    }

    @Test
    void alternativesNeedEveryOneDead() {
        assertTrue(dead("{'type':'mod:press','ingredient':[{'tag':'forge:ingots/copper'},{'item':'minecraft:iron_ingot'}]}").isEmpty());
        assertEquals(Set.of("forge:ingots/copper"),
                dead("{'type':'mod:press','ingredient':[{'tag':'forge:ingots/copper'},{'item':'minecraft:copper_ingot'}]}"));
    }

    @Test
    void pluralKeyListsSeparateIngredients() {
        assertEquals(Set.of("forge:ingots/copper"),
                dead("{'type':'mod:mixing','ingredients':[{'tag':'forge:ingots/copper'},{'item':'minecraft:iron_ingot'}]}"));
    }

    @Test
    void onlyCutItemsAreLeftToTheFileFilter() {
        assertTrue(dead("{'type':'mod:press','ingredient':{'item':'deco:lamp'}}").isEmpty());
    }

    @Test
    void fluidsConditionsAndBareTags() {
        assertTrue(dead("{'type':'mekanism:rotary','fluidInput':{'tag':'forge:ores/quartz','amount':100}}").isEmpty(),
                "a tag with an amount is a fluid or chemical");
        assertTrue(dead("{'type':'mod:press','conditions':[{'type':'forge:tag_empty','tag':'forge:ores/quartz'}],"
                + "'ingredient':{'item':'minecraft:iron_ingot'}}").isEmpty(), "conditions are not ingredients");
        assertEquals(Set.of("forge:ores/quartz"), dead("{'type':'mod:press','ingredient':{'tag':'#forge:ores/quartz'}}"));
        Predicate<String> logs = "minecraft:logs"::equals;
        assertEquals(Set.of("minecraft:logs"), RecipeIngredients.deadTags(json("{'ingredient':{'tag':'logs'}}"), CUT, logs));
    }

    private static Set<String> deadStrings(String text) {
        return RecipeIngredients.deadTags(json(text), CUT, EMPTIED, true);
    }

    @Test
    void stringIngredients1212() {
        // 1.21.2+: a tag ingredient is the string "#ns:tag", alternatives an array of ids and tags.
        assertEquals(Set.of("forge:ores/quartz"), deadStrings("{'type':'mod:press','ingredient':'#forge:ores/quartz','result':{'id':'minecraft:quartz'}}"));
        assertTrue(deadStrings("{'type':'mod:press','ingredient':'#forge:dusts/quartz','result':{'id':'minecraft:quartz'}}").isEmpty());
        // One ingredient with a live alternative stays.
        assertTrue(deadStrings("{'type':'mod:press','ingredient':['#forge:ores/quartz','minecraft:stone']}").isEmpty());
        assertEquals(Set.of("forge:ores/quartz"), deadStrings("{'type':'mod:press','ingredient':['#forge:ores/quartz','minecraft:copper_ingot']}"));
        // Under a plural key each value is its own ingredient.
        assertEquals(Set.of("forge:ingots/copper"), deadStrings("{'type':'mod:mix','ingredients':['#forge:ingots/copper','minecraft:stone']}"));
        // Conditions name tags but aren't ingredients.
        assertTrue(deadStrings("{'type':'mod:mix','neoforge:conditions':[],'conditions':['#forge:ingots/copper'],'ingredient':'minecraft:stone'}").isEmpty());
    }

    @Test
    void stringValuesOffByDefault() {
        // Older lines read objects only, so a string tag is not an ingredient there.
        assertTrue(dead("{'type':'mod:press','ingredient':'#forge:ores/quartz'}").isEmpty());
    }
}
