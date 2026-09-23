package com.rinkynooble.scalpel.core.rules;

import com.rinkynooble.scalpel.core.ContentType;
import com.rinkynooble.scalpel.core.RecipeMode;
import com.rinkynooble.scalpel.core.Verb;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleParserTest {
    private static RuleParser.Result parse(String... lines) {
        return RuleParser.parse("test.rules", List.of(lines));
    }

    @Test
    void parsesAllParts() {
        RuleParser.Result result = parse("redact any examplemod:* rewrite");
        assertTrue(result.errors().isEmpty());
        Rule rule = result.rules().get(0);
        assertEquals(Verb.REDACT, rule.verb());
        assertEquals(EnumSet.of(ContentType.ITEM, ContentType.BLOCK, ContentType.ENTITY), rule.types());
        assertEquals(RecipeMode.REWRITE, rule.recipeMode());
        assertEquals("test.rules:1", rule.location());
        assertEquals("redact any examplemod:* rewrite", rule.canonical());
    }

    @Test
    void skipsBlankLinesAndComments() {
        RuleParser.Result result = parse("", "   ", "# a comment", "keep item a:b   # trailing comment", "\tremove  block\ta:*");
        assertTrue(result.errors().isEmpty());
        assertEquals(2, result.rules().size());
        assertEquals(4, result.rules().get(0).line());
        assertEquals("remove block a:*", result.rules().get(1).canonical());
    }

    @Test
    void tagIdWithHashIsNotAComment() {
        RuleParser.Result result = parse("remove tag #forge:ores/tin", "remove tag forge:ingots/tin # comment");
        assertTrue(result.errors().isEmpty());
        assertEquals("forge:ores/tin", result.rules().get(0).pattern().source());
        assertEquals(2, result.rules().size());
    }

    @Test
    void reportsErrorsWithLocation() {
        RuleParser.Result result = parse(
                "remvoe item a:b",
                "remove thing a:b",
                "redact recipe a:b",
                "remove item Examplemod:b",
                "remove item a:b sideways",
                "keep item a:b rewrite",
                "remove recipe a:b rewrite",
                "remove item a:b rewrite drop",
                "remove item",
                "remove item #a:b");
        assertEquals(0, result.rules().size());
        assertEquals(10, result.errors().size());
        assertEquals("test.rules:1", result.errors().get(0).location());
        assertTrue(result.errors().get(0).message().contains("unknown verb"));
        assertTrue(result.errors().get(2).message().contains("only items, blocks and entities"));
    }

    @Test
    void sameOptionTwiceIsFine() {
        Rule rule = RuleParser.parseLine("f", 1, "remove item a:b drop drop");
        assertEquals(RecipeMode.DROP, rule.recipeMode());
    }

    @Test
    void blankLineGivesNull() {
        assertNull(RuleParser.parseLine("f", 1, "  # only a comment"));
    }
}
