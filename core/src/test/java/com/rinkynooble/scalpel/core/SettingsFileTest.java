package com.rinkynooble.scalpel.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsFileTest {
    /** What Forge's config spec writes, with a few values changed. */
    private static final String FORGE_WRITTEN = String.join("\n",
            "[general]",
            "\t#Every setting here needs a game restart.",
            "\t#Log and report what would be cut, without cutting anything.",
            "\tdryRun = true",
            "\t#Write Scalpel's own log to logs/scalpel.log.",
            "\tlogFile = false",
            "",
            "[registration]",
            "\tallowRemovingVanilla = true",
            "\tprotectCriticalIds = false # inline comment",
            "",
            "[recipes]",
            "\t#Allowed Values: drop, rewrite",
            "\tingredientMode = \"rewrite\"",
            "",
            "[reports]",
            "\t#Range: 0 ~ 16",
            "\tcascadeDepth = 5",
            "");

    @Test
    void readsWhatForgeWrites(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("scalpel.toml");
        Files.writeString(file, FORGE_WRITTEN);
        assertEquals(new Settings(true, false, true, false, RecipeMode.REWRITE, 5), SettingsFile.read(file, w -> { }));
    }

    @Test
    void missingFileAndBadValuesFallBack(@TempDir Path dir) throws IOException {
        assertEquals(Settings.DEFAULTS, SettingsFile.read(dir.resolve("none.toml"), w -> { }));
        Path file = dir.resolve("scalpel.toml");
        Files.writeString(file, "[general]\ndryRun = maybe\n[recipes]\ningredientMode = \"sideways\"\n[reports]\ncascadeDepth = 99\n");
        List<String> warnings = new ArrayList<>();
        Settings read = SettingsFile.read(file, warnings::add);
        assertFalse(read.dryRun());
        assertEquals(RecipeMode.DROP, read.ingredientMode());
        assertEquals(16, read.cascadeDepth());
        assertEquals(1, warnings.size());
    }

    @Test
    void writtenDefaultsReadBackAsDefaults(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("scalpel").resolve("scalpel.toml");
        SettingsFile.writeDefaultsIfMissing(file);
        assertTrue(Files.isRegularFile(file));
        assertEquals(Settings.DEFAULTS, SettingsFile.read(file, w -> { }));
        Files.writeString(file, "[general]\ndryRun = true\n");
        SettingsFile.writeDefaultsIfMissing(file);
        assertTrue(SettingsFile.read(file, w -> { }).dryRun(), "an existing file is never overwritten");
    }
}
