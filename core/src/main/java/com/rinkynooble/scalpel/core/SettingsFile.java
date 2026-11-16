package com.rinkynooble.scalpel.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Reads {@code config/scalpel/scalpel.toml} with plain file IO, before any loader config system is up (Forge loads its
 * configs after registration, Fabric has none). Only the flat form the file uses: {@code [section]} headers and
 * {@code key = value} lines with booleans, whole numbers and quoted strings. Missing file or keys give the defaults.
 */
public final class SettingsFile {
    /** Relative to the game's config folder. */
    public static final String RELATIVE_PATH = "scalpel/scalpel.toml";

    private SettingsFile() {
    }

    public static Settings read(Path file, Consumer<String> warn) {
        Settings d = Settings.DEFAULTS;
        if (!Files.isRegularFile(file)) {
            return d;
        }
        Map<String, String> values;
        try {
            values = parse(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            warn.accept("Could not read " + file + " (" + e.getMessage() + "), using default settings.");
            return d;
        }
        String modeText = values.get("recipes.ingredientMode");
        RecipeMode mode = modeText == null ? d.ingredientMode() : RecipeMode.parse(unquote(modeText).toLowerCase(Locale.ROOT));
        if (mode == null) {
            warn.accept("recipes.ingredientMode must be drop or rewrite, using " + d.ingredientMode().keyword() + ".");
            mode = d.ingredientMode();
        }
        return new Settings(
                bool(values, "general.dryRun", d.dryRun()),
                bool(values, "general.logFile", d.logFile()),
                bool(values, "registration.allowRemovingVanilla", d.allowRemovingVanilla()),
                bool(values, "registration.protectCriticalIds", d.protectCriticalIds()),
                mode,
                Math.max(0, Math.min(16, number(values, "reports.cascadeDepth", d.cascadeDepth()))));
    }

    /** {@code section.key} to the raw value text, with comments removed. */
    static Map<String, String> parse(String text) {
        Map<String, String> values = new HashMap<>();
        String section = "";
        for (String raw : text.split("\\R")) {
            String line = stripComment(raw).strip();
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith("[") && line.endsWith("]")) {
                section = line.substring(1, line.length() - 1).strip();
                continue;
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = line.substring(0, eq).strip();
            values.put(section.isEmpty() ? key : section + "." + key, line.substring(eq + 1).strip());
        }
        return values;
    }

    /** Cuts a {@code #} comment, unless the {@code #} is inside a quoted string. */
    private static String stripComment(String line) {
        char quote = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                }
            } else if (c == '"' || c == '\'') {
                quote = c;
            } else if (c == '#') {
                return line.substring(0, i);
            }
        }
        return line;
    }

    private static String unquote(String value) {
        boolean doubleQuoted = value.startsWith("\"") && value.endsWith("\"");
        boolean singleQuoted = value.startsWith("'") && value.endsWith("'");
        if (value.length() >= 2 && (doubleQuoted || singleQuoted)) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static boolean bool(Map<String, String> values, String key, boolean fallback) {
        String value = values.get(key);
        if ("true".equals(value)) {
            return true;
        }
        return "false".equals(value) ? false : fallback;
    }

    private static int number(Map<String, String> values, String key, int fallback) {
        String value = values.get(key);
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.replace("_", ""));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /**
     * Writes the file with every setting at its default, for loaders that have no config system to do it (Fabric).
     * The layout and comments match what Forge writes from its config spec. Does nothing when the file exists.
     */
    public static void writeDefaultsIfMissing(Path file) throws IOException {
        if (Files.exists(file)) {
            return;
        }
        Settings d = Settings.DEFAULTS;
        String text = String.join("\n",
                "[general]",
                "\t#Every setting here needs a game restart.",
                "\t#Log and report what would be cut, without cutting anything.",
                "\tdryRun = " + d.dryRun(),
                "\t#Write Scalpel's own log to logs/scalpel.log.",
                "\tlogFile = " + d.logFile(),
                "",
                "[registration]",
                "\t#Let 'remove' rules remove vanilla (minecraft:) content.",
                "\t#When false, vanilla content matched by a remove rule is redacted instead. Vanilla code holds on to its own content, so removing it can crash.",
                "\tallowRemovingVanilla = " + d.allowRemovingVanilla(),
                "\t#Refuse to cut ids the game relies on internally, such as stone, water, fire, portals and item entities.",
                "\t#Air and the player can never be cut, whatever this says.",
                "\tprotectCriticalIds = " + d.protectCriticalIds(),
                "",
                "[recipes]",
                "\t#What happens to recipes that use cut content as an ingredient.",
                "\t#drop: the recipe is removed.",
                "\t#rewrite: the cut item is taken out and the rest of the recipe stays, when it still works without it.",
                "\t#A rule can override this with the 'drop' or 'rewrite' option.",
                "\t#Allowed Values: drop, rewrite",
                "\tingredientMode = \"" + d.ingredientMode().keyword() + "\"",
                "",
                "[reports]",
                "\t#How many levels the 'lost every recipe' section of the report follows. 0 turns it off.",
                "\t#Range: 0 ~ 16",
                "\tcascadeDepth = " + d.cascadeDepth(),
                "",
                "");
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }
}
