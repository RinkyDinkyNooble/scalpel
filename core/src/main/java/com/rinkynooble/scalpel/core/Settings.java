package com.rinkynooble.scalpel.core;

/**
 * Values from {@code config/scalpel/scalpel.toml}. The port reads the file; core only sees this record.
 *
 * @param dryRun               log and report what would be cut, change nothing
 * @param logFile              write {@code logs/scalpel.log}
 * @param allowRemovingVanilla let {@code remove} apply to {@code minecraft:} ids instead of downgrading to redact
 * @param protectCriticalIds   refuse to cut ids the game resolves internally
 * @param ingredientMode       what happens to recipes that use cut content, unless a rule says otherwise
 * @param cascadeDepth         how many levels the "lost every recipe" report follows (0 turns it off)
 */
public record Settings(
        boolean dryRun,
        boolean logFile,
        boolean allowRemovingVanilla,
        boolean protectCriticalIds,
        RecipeMode ingredientMode,
        int cascadeDepth) {

    public static final Settings DEFAULTS = new Settings(false, true, false, true, RecipeMode.DROP, 3);

    /** The part of the settings that changes what gets registered. Client and server must agree on it. */
    public String registrationFingerprint() {
        return "dryRun=" + dryRun + ";allowRemovingVanilla=" + allowRemovingVanilla + ";protectCriticalIds=" + protectCriticalIds;
    }
}
