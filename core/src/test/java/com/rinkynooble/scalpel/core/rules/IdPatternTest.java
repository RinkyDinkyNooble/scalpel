package com.rinkynooble.scalpel.core.rules;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdPatternTest {
    @Test
    void exactMatchesOnlyItself() {
        IdPattern p = IdPattern.parse("examplemod:ruby");
        assertInstanceOf(IdPattern.Exact.class, p);
        assertTrue(p.matches("examplemod:ruby"));
        assertFalse(p.matches("examplemod:ruby_block"));
        assertEquals("examplemod", p.namespaceHint());
    }

    @Test
    void globStarAndQuestionMark() {
        IdPattern p = IdPattern.parse("examplemod:*_eggs");
        assertTrue(p.matches("examplemod:chicken_eggs"));
        assertTrue(p.matches("examplemod:_eggs"));
        assertFalse(p.matches("examplemod:chicken_egg"));
        assertFalse(p.matches("othermod:chicken_eggs"));
        assertEquals("examplemod", p.namespaceHint());

        IdPattern q = IdPattern.parse("examplemod:ore_?");
        assertTrue(q.matches("examplemod:ore_1"));
        assertFalse(q.matches("examplemod:ore_12"));
    }

    @Test
    void globAcrossNamespacesAndPaths() {
        IdPattern p = IdPattern.parse("*:*_slab");
        assertTrue(p.matches("minecraft:oak_slab"));
        assertTrue(p.matches("betterblockz:zeno/stone_slab"));
        assertFalse(p.matches("minecraft:oak_slab_top"));
        assertNull(p.namespaceHint());

        IdPattern all = IdPattern.parse("examplemod:*");
        assertTrue(all.matches("examplemod:a/b/c"));
        assertFalse(all.matches("examplemodx:a"));
    }

    @Test
    void starNeverCrossesTheColon() {
        IdPattern p = IdPattern.parse("example*:ruby");
        assertTrue(p.matches("examplemod:ruby"));
        assertFalse(p.matches("examplemod:x:ruby"));
    }

    @Test
    void globBacktracks() {
        IdPattern p = IdPattern.parse("m:*a*b*c");
        assertTrue(p.matches("m:xaxbxc"));
        assertTrue(p.matches("m:abcabc"));
        assertFalse(p.matches("m:acb"));
    }

    @Test
    void regexIsFullMatch() {
        IdPattern p = IdPattern.parse("/^examplemod:[a-z0-9_]*_eggs$/");
        assertInstanceOf(IdPattern.Regex.class, p);
        assertTrue(p.matches("examplemod:big_eggs"));
        assertFalse(p.matches("examplemod:big_eggs_x"));
        assertEquals("examplemod", p.namespaceHint());

        IdPattern unanchored = IdPattern.parse("/examplemod:.*/");
        assertTrue(unanchored.matches("examplemod:anything"));
        assertFalse(unanchored.matches("xexamplemod:anything"));
    }

    @Test
    void regexHintIsConservative() {
        assertNull(IdPattern.parse("/(a|b):c/").namespaceHint());
        assertNull(IdPattern.parse("/a.b:c/").namespaceHint());
        assertNull(IdPattern.parse("/.*:c/").namespaceHint());
    }

    @Test
    void rejectsBadPatterns() {
        assertThrows(IllegalArgumentException.class, () -> IdPattern.parse("ruby"));
        assertThrows(IllegalArgumentException.class, () -> IdPattern.parse("Examplemod:ruby"));
        assertThrows(IllegalArgumentException.class, () -> IdPattern.parse("a:b:c"));
        assertThrows(IllegalArgumentException.class, () -> IdPattern.parse("a:b c"));
        assertThrows(IllegalArgumentException.class, () -> IdPattern.parse("/unclosed"));
        assertThrows(IllegalArgumentException.class, () -> IdPattern.parse("/[/"));
        assertThrows(IllegalArgumentException.class, () -> IdPattern.parse("a:b+"));
    }
}
