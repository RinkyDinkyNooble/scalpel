package com.rinkynooble.scalpel.core;

import com.rinkynooble.scalpel.core.Decision.Action;
import com.rinkynooble.scalpel.core.rules.RuleParser;
import com.rinkynooble.scalpel.core.rules.RuleSet;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResolverTest {
    private static Resolver resolver(Settings settings, String... lines) {
        RuleParser.Result result = RuleParser.parse("t.rules", List.of(lines));
        assertTrue(result.errors().isEmpty(), result.errors().toString());
        return new Resolver(new RuleSet(result.rules()), settings);
    }

    private static Resolver resolver(String... lines) {
        return resolver(Settings.DEFAULTS, lines);
    }

    @Test
    void keepBeatsRedactBeatsRemove() {
        Resolver r = resolver(
                "remove block palette:*",
                "redact block palette:red_*",
                "keep block palette:red_bricks");
        assertEquals(Action.REMOVE, r.decide(ContentType.BLOCK, "palette:blue_bricks").action());
        assertEquals(Action.REDACT, r.decide(ContentType.BLOCK, "palette:red_stone").action());
        assertEquals(Action.KEEP, r.decide(ContentType.BLOCK, "palette:red_bricks").action());
        assertEquals(Action.NONE, r.decide(ContentType.BLOCK, "other:red_bricks").action());
    }

    @Test
    void keepWinsRegardlessOfFileOrder() {
        Resolver r = resolver("keep item a:b", "remove item a:*");
        assertEquals(Action.KEEP, r.decide(ContentType.ITEM, "a:b").action());
    }

    @Test
    void typesAreSeparate() {
        Resolver r = resolver("remove item a:x");
        assertEquals(Action.REMOVE, r.decide(ContentType.ITEM, "a:x").action());
        assertEquals(Action.NONE, r.decide(ContentType.ENTITY, "a:x").action());
    }

    @Test
    void vanillaRemoveBecomesRedact() {
        Resolver r = resolver("remove item minecraft:dead_bush");
        Decision d = r.decide(ContentType.ITEM, "minecraft:dead_bush");
        assertEquals(Action.REDACT, d.action());
        assertNotNull(d.note());

        Settings allow = new Settings(false, true, true, true, RecipeMode.DROP, 3);
        assertEquals(Action.REMOVE, resolver(allow, "remove item minecraft:dead_bush").decide(ContentType.ITEM, "minecraft:dead_bush").action());
    }

    @Test
    void protection() {
        Resolver r = resolver("redact block minecraft:*", "remove entity *:*");
        assertEquals(Action.PROTECTED, r.decide(ContentType.BLOCK, "minecraft:air").action());
        assertEquals(Action.PROTECTED, r.decide(ContentType.BLOCK, "minecraft:stone").action());
        assertEquals(Action.REDACT, r.decide(ContentType.BLOCK, "minecraft:dead_bush").action());
        assertEquals(Action.PROTECTED, r.decide(ContentType.ENTITY, "minecraft:player").action());
        assertEquals(Action.PROTECTED, r.decide(ContentType.ENTITY, "scalpel:redacted").action());

        Settings unlocked = new Settings(false, true, false, false, RecipeMode.DROP, 3);
        Resolver u = resolver(unlocked, "redact block minecraft:*");
        assertEquals(Action.REDACT, u.decide(ContentType.BLOCK, "minecraft:stone").action());
        assertEquals(Action.PROTECTED, u.decide(ContentType.BLOCK, "minecraft:air").action());
    }

    @Test
    void itemRuleCutsBlockWithSameId() {
        Resolver r = resolver("redact item deco:lamp");
        Decision block = r.decideBlock("deco:lamp");
        assertEquals(Action.REDACT, block.action());
        assertTrue(block.note().contains("linked"));
        assertEquals(Action.NONE, r.decideBlock("deco:other").action());
    }

    @Test
    void blockOwnRuleWinsOverItemRule() {
        Resolver r = resolver("remove item deco:lamp", "keep block deco:lamp");
        assertEquals(Action.KEEP, r.decideBlock("deco:lamp").action());
    }

    @Test
    void blockItemFollowsItsBlock() {
        Resolver r = resolver("remove block deco:*");
        Decision block = r.decideBlock("deco:lamp");
        r.markCut(ContentType.BLOCK, "deco:lamp", block);
        Decision item = r.decideItem("deco:lamp_item", List.of("deco:lamp"), null);
        assertEquals(Action.REMOVE, item.action());
        assertTrue(item.note().contains("deco:lamp"));
        assertEquals(Action.NONE, r.decideItem("deco:other_item", List.of("deco:kept"), null).action());
    }

    @Test
    void spawnEggFollowsItsEntity() {
        Resolver r = resolver("redact entity mobs:blob");
        r.markCut(ContentType.ENTITY, "mobs:blob", r.decideEntity("mobs:blob"));
        assertEquals(Action.REDACT, r.decideItem("mobs:blob_spawn_egg", List.of(), "mobs:blob").action());
    }

    @Test
    void spawnEggFollowsEntityDecidedLater() {
        // Forge registers items before entity types.
        Resolver r = resolver("remove entity mobs:blob");
        assertEquals(Action.REMOVE, r.decideItem("mobs:blob_spawn_egg", List.of(), "mobs:blob").action());
        assertEquals(Action.NONE, r.decideItem("mobs:other_egg", List.of(), "mobs:other").action());
    }

    @Test
    void linkedVanillaItemIsStillGuarded() {
        Settings allow = new Settings(false, true, true, true, RecipeMode.DROP, 3);
        Resolver r = resolver(allow, "remove block minecraft:dead_bush");
        r.markCut(ContentType.BLOCK, "minecraft:dead_bush", r.decideBlock("minecraft:dead_bush"));
        assertEquals(Action.REMOVE, r.decideItem("minecraft:dead_bush", List.of("minecraft:dead_bush"), null).action());

        Resolver strict = resolver("redact item minecraft:stone");
        assertEquals(Action.PROTECTED, strict.decideBlock("minecraft:stone").action());
    }

    @Test
    void dataTypesOnlyRemove() {
        Resolver r = resolver("remove recipe minecraft:*_boat", "keep recipe minecraft:oak_boat");
        assertEquals(Action.REMOVE, r.decide(ContentType.RECIPE, "minecraft:birch_boat").action());
        assertEquals(Action.KEEP, r.decide(ContentType.RECIPE, "minecraft:oak_boat").action());
    }

    @Test
    void countsMatchesOnlyWhenRecording() {
        Resolver r = resolver("remove item a:*");
        r.peek(ContentType.ITEM, "a:1");
        assertEquals(0, r.rules().rules().get(0).matchCount());
        r.decide(ContentType.ITEM, "a:1");
        r.decide(ContentType.ITEM, "a:1");
        r.decide(ContentType.ITEM, "a:2");
        assertEquals(2, r.rules().rules().get(0).matchCount());
    }

    @Test
    void recipeModeFallsBackToSetting() {
        Resolver r = resolver("remove item a:x rewrite", "remove item a:y");
        assertEquals(RecipeMode.REWRITE, r.decide(ContentType.ITEM, "a:x").recipeMode(Settings.DEFAULTS));
        assertEquals(RecipeMode.DROP, r.decide(ContentType.ITEM, "a:y").recipeMode(Settings.DEFAULTS));
    }
}
