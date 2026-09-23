package com.rinkynooble.scalpel.forge;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.rinkynooble.scalpel.core.RecipeMode;
import com.rinkynooble.scalpel.core.Settings;
import net.minecraftforge.common.ForgeConfigSpec;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * {@code config/scalpel/scalpel.toml}. Registered as a Forge config so config-manager mods can edit it,
 * and also read directly at startup, because Forge loads configs after registration has already happened.
 */
public final class ForgeSettings {
    public static final String FILE_NAME = "scalpel/scalpel.toml";
    public static final ForgeConfigSpec SPEC;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
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
                .defineInList("ingredientMode", Settings.DEFAULTS.ingredientMode().keyword(), List.of("drop", "rewrite"));
        b.pop();

        b.push("reports");
        b.comment("How many levels the 'lost every recipe' section of the report follows. 0 turns it off.")
                .defineInRange("cascadeDepth", Settings.DEFAULTS.cascadeDepth(), 0, 16);
        b.pop();
        SPEC = b.build();
    }

    private ForgeSettings() {
    }

    /** Reads the settings file with plain file IO. Missing file or missing keys give the defaults. */
    public static Settings readEarly(Path configDir, java.util.function.Consumer<String> warn) {
        Path file = configDir.resolve(FILE_NAME);
        Settings d = Settings.DEFAULTS;
        if (!Files.isRegularFile(file)) {
            return d;
        }
        CommentedConfig config;
        try (Reader reader = Files.newBufferedReader(file)) {
            config = TomlFormat.instance().createParser().parse(reader);
        } catch (Exception e) {
            warn.accept("Could not read " + file + " (" + e.getMessage() + "), using default settings.");
            return d;
        }
        RecipeMode mode = RecipeMode.parse(String.valueOf(config.getOrElse("recipes.ingredientMode", d.ingredientMode().keyword())).toLowerCase(Locale.ROOT));
        if (mode == null) {
            warn.accept("recipes.ingredientMode must be drop or rewrite, using " + d.ingredientMode().keyword() + ".");
            mode = d.ingredientMode();
        }
        return new Settings(
                bool(config, "general.dryRun", d.dryRun()),
                bool(config, "general.logFile", d.logFile()),
                bool(config, "registration.allowRemovingVanilla", d.allowRemovingVanilla()),
                bool(config, "registration.protectCriticalIds", d.protectCriticalIds()),
                mode,
                Math.max(0, Math.min(16, number(config, "reports.cascadeDepth", d.cascadeDepth()))));
    }

    private static boolean bool(CommentedConfig config, String path, boolean fallback) {
        Object value = config.get(path);
        return value instanceof Boolean b ? b : fallback;
    }

    private static int number(CommentedConfig config, String path, int fallback) {
        Object value = config.get(path);
        return value instanceof Number n ? n.intValue() : fallback;
    }
}
