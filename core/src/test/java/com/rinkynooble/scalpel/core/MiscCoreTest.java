package com.rinkynooble.scalpel.core;

import com.rinkynooble.scalpel.core.rules.RuleParser;
import com.rinkynooble.scalpel.core.rules.RulesLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MiscCoreTest {
    @Test
    void cascadeFollowsLevels() {
        // plank <- log (log recipe dropped). stick <- plank. torch <- stick + coal, or stick + charcoal.
        List<Cascade.RecipeInfo> current = List.of(
                new Cascade.RecipeInfo("stick", Set.of("m:stick"), List.of(Set.of("m:plank"))),
                new Cascade.RecipeInfo("torch", Set.of("m:torch"), List.of(Set.of("m:stick"), Set.of("m:coal", "m:charcoal"))),
                new Cascade.RecipeInfo("lamp", Set.of("m:lamp"), List.of(Set.of("m:torch"), Set.of()))
        );
        List<Set<String>> levels = Cascade.compute(current, Set.of("m:plank", "m:cut_thing"), Set.of("m:cut_thing"), 5);
        assertEquals(List.of(Set.of("m:plank"), Set.of("m:stick"), Set.of("m:torch"), Set.of("m:lamp")), levels);

        assertEquals(2, Cascade.compute(current, Set.of("m:plank"), Set.of(), 2).size());
        assertTrue(Cascade.compute(current, Set.of("m:plank"), Set.of(), 0).isEmpty());
    }

    @Test
    void cascadeIgnoresItemsWithOtherRecipes() {
        List<Cascade.RecipeInfo> current = List.of(
                new Cascade.RecipeInfo("plank_alt", Set.of("m:plank"), List.of(Set.of("m:bamboo"))));
        assertTrue(Cascade.compute(current, Set.of("m:plank"), Set.of(), 3).isEmpty());
    }

    @Test
    void idIndexSuggestsByFullIdAndPath() {
        IdIndex index = new IdIndex(List.of("minecraft:stone", "minecraft:stick", "examplemod:ruby", "examplemod:ruby_block", "other:stone_bricks"));
        assertEquals(List.of("examplemod:ruby", "examplemod:ruby_block"), index.suggest("examplemod:r", 10));
        assertEquals(List.of("minecraft:stick", "minecraft:stone", "other:stone_bricks"), index.suggest("st", 10));
        assertEquals(List.of("minecraft:stick"), index.suggest("st", 1));
        assertEquals(List.of("examplemod:ruby", "examplemod:ruby_block"), index.suggest("ex", 10));
        assertTrue(index.suggest("zzz", 10).isEmpty());
    }

    @Test
    void idIndexStaysFastAtModpackScale() {
        List<String> ids = new java.util.ArrayList<>();
        for (int mod = 0; mod < 300; mod++) {
            for (int item = 0; item < 250; item++) {
                ids.add("mod" + mod + ":thing_" + item + "_block");
            }
        }
        long buildStart = System.nanoTime();
        IdIndex index = new IdIndex(ids);
        long buildMs = (System.nanoTime() - buildStart) / 1_000_000;
        assertEquals(75_000, index.size());

        long start = System.nanoTime();
        for (int i = 0; i < 1_000; i++) {
            index.suggest("mod1" + (i % 10), 50);
            index.suggest("thing_1" + (i % 10), 50);
        }
        long perLookupMicros = (System.nanoTime() - start) / 2_000 / 1_000;
        assertTrue(perLookupMicros < 2_000, "suggestion took " + perLookupMicros + "us");
        assertTrue(buildMs < 5_000, "index build took " + buildMs + "ms");
    }

    @Test
    void hashIgnoresCommentsButNotRulesOrSettings() {
        RulesLoader.RulesFile a = new RulesLoader.RulesFile("a.rules", RuleParser.parse("a.rules", List.of("# hello", "remove item x:*")).rules());
        RulesLoader.RulesFile b = new RulesLoader.RulesFile("a.rules", RuleParser.parse("a.rules", List.of("remove   item x:*   # different comment")).rules());
        RulesLoader.RulesFile c = new RulesLoader.RulesFile("a.rules", RuleParser.parse("a.rules", List.of("redact item x:*")).rules());
        String ha = RulesHash.compute("1", List.of(a), Settings.DEFAULTS).full();
        assertEquals(ha, RulesHash.compute("1", List.of(b), Settings.DEFAULTS).full());
        assertNotEquals(ha, RulesHash.compute("1", List.of(c), Settings.DEFAULTS).full());
        assertNotEquals(ha, RulesHash.compute("2", List.of(a), Settings.DEFAULTS).full());
        Settings dry = new Settings(true, true, false, true, RecipeMode.DROP, 3);
        assertNotEquals(ha, RulesHash.compute("1", List.of(a), dry).full());
        Settings rewrite = new Settings(false, true, false, true, RecipeMode.REWRITE, 3);
        assertEquals(ha, RulesHash.compute("1", List.of(a), rewrite).full());
    }

    @Test
    void loaderCreatesExampleAndReadsFilesInOrder(@TempDir Path temp) throws IOException {
        Path folder = temp.resolve("scalpel");
        RulesLoader.Loaded first = RulesLoader.load(folder);
        assertTrue(Files.exists(folder.resolve("example.rules")));
        assertTrue(first.toRuleSet().isEmpty(), "the example must not cut anything");
        assertTrue(first.errors().isEmpty());

        Files.writeString(folder.resolve("b.rules"), "remove item b:*\nbogus line here\n");
        Files.writeString(folder.resolve("a.rules"), "﻿keep item b:x\n");
        Files.writeString(folder.resolve("notes.txt"), "remove item ignored:*\n");
        RulesLoader.Loaded loaded = RulesLoader.load(folder);
        assertEquals(List.of("a.rules", "b.rules", "example.rules"), loaded.files().stream().map(RulesLoader.RulesFile::name).toList());
        assertEquals(2, loaded.toRuleSet().rules().size());
        assertEquals(1, loaded.errors().size());
        assertEquals("b.rules:2", loaded.errors().get(0).location());
    }

    @Test
    void coreStartsAndWritesReport(@TempDir Path temp) throws IOException {
        Path rules = temp.resolve("config/scalpel");
        Files.createDirectories(rules);
        Files.writeString(rules.resolve("t.rules"), "redact item deco:*\nremove item typo:nothing\n");
        StringBuilder mirrored = new StringBuilder();
        ScalpelLog.Mirror mirror = new ScalpelLog.Mirror() {
            public void info(String m) { mirrored.append("I ").append(m).append('\n'); }
            public void warn(String m) { mirrored.append("W ").append(m).append('\n'); }
            public void error(String m) { mirrored.append("E ").append(m).append('\n'); }
        };
        ScalpelCore core = ScalpelCore.start(rules, temp.resolve("logs"), "0.1.0", "0.1.0 (test)", Settings.DEFAULTS, mirror);
        Decision d = core.resolver().decide(ContentType.ITEM, "deco:lamp");
        core.resolver().markCut(ContentType.ITEM, "deco:lamp", d);
        core.report().cut(ContentType.ITEM, "deco:lamp", "deco", d);
        core.report().evaluated(ContentType.REGISTRY_TYPES);
        core.writeReport();

        String report = Files.readString(core.reportFile());
        assertTrue(report.contains("[deco] 1"), report);
        assertTrue(report.contains("Unmatched rules (1)"), report);
        assertTrue(report.contains("t.rules:2"), report);
        assertTrue(Files.readString(temp.resolve("logs/scalpel.log")).contains("2 rules from 1 files"));
        assertTrue(mirrored.toString().contains("rules hash"));
    }
}
