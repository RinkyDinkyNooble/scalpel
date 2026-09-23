package com.rinkynooble.scalpel.core.json;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RecipeRewriterTest {
    private static final Predicate<String> CUT = Set.of("deco:lamp", "minecraft:dead_bush")::contains;

    private static JsonObject json(String text) {
        return JsonParser.parseString(text.replace('\'', '"')).getAsJsonObject();
    }

    @Test
    void shapedSlotBecomesBlank() {
        JsonObject recipe = json("{'type':'minecraft:crafting_shaped','pattern':['LSL','SSS'],"
                + "'key':{'L':{'item':'deco:lamp'},'S':{'item':'minecraft:stick'}},'result':{'item':'deco:chair'}}");
        assertEquals(RecipeRewriter.Outcome.REWRITTEN, RecipeRewriter.rewrite(recipe, CUT));
        assertEquals(" S ", recipe.getAsJsonArray("pattern").get(0).getAsString());
        assertFalse(recipe.getAsJsonObject("key").has("L"));
    }

    @Test
    void shapedAlternativeIsTrimmed() {
        JsonObject recipe = json("{'type':'crafting_shaped','pattern':['X'],"
                + "'key':{'X':[{'item':'deco:lamp'},{'item':'minecraft:torch'}]},'result':{'item':'deco:chair'}}");
        assertEquals(RecipeRewriter.Outcome.REWRITTEN, RecipeRewriter.rewrite(recipe, CUT));
        assertEquals(1, recipe.getAsJsonObject("key").getAsJsonArray("X").size());
        assertEquals("X", recipe.getAsJsonArray("pattern").get(0).getAsString());
    }

    @Test
    void shapedWithOnlyCutIngredientsIsDropped() {
        JsonObject recipe = json("{'type':'minecraft:crafting_shaped','pattern':['LL'],'key':{'L':{'item':'deco:lamp'}},'result':{'item':'deco:chair'}}");
        assertEquals(RecipeRewriter.Outcome.DROP, RecipeRewriter.rewrite(recipe, CUT));
    }

    @Test
    void shapelessIngredientIsRemoved() {
        JsonObject recipe = json("{'type':'minecraft:crafting_shapeless','ingredients':[{'item':'dead_bush'},{'item':'minecraft:stick'}],'result':{'item':'minecraft:torch'}}");
        assertEquals(RecipeRewriter.Outcome.REWRITTEN, RecipeRewriter.rewrite(recipe, CUT));
        assertEquals(1, recipe.getAsJsonArray("ingredients").size());
    }

    @Test
    void cutResultIsAlwaysDropped() {
        JsonObject recipe = json("{'type':'minecraft:crafting_shapeless','ingredients':[{'item':'minecraft:stick'}],'result':{'item':'deco:lamp'}}");
        assertEquals(RecipeRewriter.Outcome.DROP, RecipeRewriter.rewrite(recipe, CUT));
    }

    @Test
    void singleInputRecipeIsDropped() {
        JsonObject recipe = json("{'type':'minecraft:smelting','ingredient':{'item':'deco:lamp'},'result':'minecraft:glass','experience':0.1}");
        assertEquals(RecipeRewriter.Outcome.DROP, RecipeRewriter.rewrite(recipe, CUT));
    }

    @Test
    void modRecipeListsAreTrimmed() {
        JsonObject mixing = json("{'type':'create:mixing','ingredients':[{'item':'deco:lamp'},{'item':'minecraft:sand'}],"
                + "'results':[{'item':'minecraft:glass'},{'item':'dead_bush','chance':0.1}]}");
        assertEquals(RecipeRewriter.Outcome.REWRITTEN, RecipeRewriter.rewrite(mixing, CUT));
        assertEquals(1, mixing.getAsJsonArray("ingredients").size());
        assertEquals(1, mixing.getAsJsonArray("results").size());

        JsonObject onlyCut = json("{'type':'create:milling','ingredients':[{'item':'deco:lamp'}],'results':[{'item':'minecraft:sand'}]}");
        assertEquals(RecipeRewriter.Outcome.DROP, RecipeRewriter.rewrite(onlyCut, CUT));
    }

    @Test
    void recipeConditionedOnCutItemIsDropped() {
        JsonObject recipe = json("{'type':'minecraft:crafting_shapeless','conditions':[{'type':'forge:item_exists','item':'deco:lamp'}],"
                + "'ingredients':[{'item':'minecraft:stick'}],'result':{'item':'minecraft:torch'}}");
        assertEquals(RecipeRewriter.Outcome.DROP, RecipeRewriter.rewrite(recipe, CUT));
    }

    @Test
    void unrelatedRecipeIsUnchanged() {
        JsonObject recipe = json("{'type':'minecraft:crafting_shapeless','ingredients':[{'item':'minecraft:stick'}],'result':{'item':'minecraft:torch'}}");
        assertEquals(RecipeRewriter.Outcome.UNCHANGED, RecipeRewriter.rewrite(recipe, CUT));
    }
}
