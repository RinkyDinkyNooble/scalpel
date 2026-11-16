package com.rinkynooble.scalpel.neoforge;

import com.rinkynooble.scalpel.core.Settings;
import com.rinkynooble.scalpel.core.SettingsFile;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/**
 * {@code config/scalpel/scalpel.toml}. Registered as a NeoForge config so config-manager mods can edit it,
 * and also read directly at startup by {@link SettingsFile}, because NeoForge loads configs after registration has already happened.
 */
public final class NeoForgeSettings {
    public static final String FILE_NAME = SettingsFile.RELATIVE_PATH;
    public static final ModConfigSpec SPEC;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.comment("Every setting here needs a game restart.").push("general");
        b.comment("Log and report what would be cut, without cutting anything.")
                .define("dryRun", Settings.DEFAULTS.dryRun());
        b.comment("Write Scalpel's own log to logs/scalpel.log.")
                .define("logFile", Settings.DEFAULTS.logFile());
        b.pop();

        b.push("registration");
        b.comment("Let 'remove' rules remove vanilla (minecraft:) content.",
                        "When false, vanilla content matched by a remove rule is redacted instead. Vanilla code holds on to its own content, so removing it can crash.")
                .define("allowRemovingVanilla", Settings.DEFAULTS.allowRemovingVanilla());
        b.comment("Refuse to cut ids the game relies on internally, such as stone, water, fire, portals and item entities.",
                        "Air and the player can never be cut, whatever this says.")
                .define("protectCriticalIds", Settings.DEFAULTS.protectCriticalIds());
        b.pop();

        b.push("recipes");
        b.comment("What happens to recipes that use cut content as an ingredient.",
                        "drop: the recipe is removed.",
                        "rewrite: the cut item is taken out and the rest of the recipe stays, when it still works without it.",
                        "A rule can override this with the 'drop' or 'rewrite' option.")
                .defineInList("ingredientMode", Settings.DEFAULTS.ingredientMode().keyword(), java.util.Arrays.asList("drop", "rewrite")); // not List.of: NeoForge 1.20.1 calls contains(null) on it
        b.pop();

        b.push("reports");
        b.comment("How many levels the 'lost every recipe' section of the report follows. 0 turns it off.")
                .defineInRange("cascadeDepth", Settings.DEFAULTS.cascadeDepth(), 0, 16);
        b.pop();
        SPEC = b.build();
    }

    private NeoForgeSettings() {
    }
}
