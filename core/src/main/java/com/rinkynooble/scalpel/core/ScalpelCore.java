package com.rinkynooble.scalpel.core;

import com.rinkynooble.scalpel.core.rules.Rule;
import com.rinkynooble.scalpel.core.rules.RuleParser;
import com.rinkynooble.scalpel.core.rules.RuleSet;
import com.rinkynooble.scalpel.core.rules.RulesLoader;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Loader-independent state for one game session: settings, rules, decisions, log and report.
 * A port creates one instance as early as it can (before the first registry entry) and hands it
 * the game-specific facts it cannot know on its own.
 */
public final class ScalpelCore {
    private final Path rulesFolder;
    private final Path logsFolder;
    private final String modVersion;
    private final String versionLabel;
    private final Settings settings;
    private final ScalpelLog log;
    private final Report report = new Report();
    private final Resolver resolver;
    private volatile List<RuleParser.ParseError> parseErrors;
    private volatile RulesHash hash;
    private volatile String side = "unknown";

    private ScalpelCore(Path rulesFolder, Path logsFolder, String modVersion, String versionLabel, Settings settings,
                        ScalpelLog log, RulesLoader.Loaded loaded) {
        this.rulesFolder = rulesFolder;
        this.logsFolder = logsFolder;
        this.modVersion = modVersion;
        this.versionLabel = versionLabel;
        this.settings = settings;
        this.log = log;
        this.parseErrors = List.copyOf(loaded.errors());
        this.hash = RulesHash.compute(modVersion, loaded.files(), settings);
        this.resolver = new Resolver(loaded.toRuleSet(), settings);
    }

    /**
     * @param rulesFolder  {@code config/scalpel}
     * @param logsFolder   {@code logs}
     * @param modVersion   e.g. {@code 0.1.0}
     * @param versionLabel e.g. {@code 0.1.0 (forge-1.20.1)}
     */
    public static ScalpelCore start(Path rulesFolder, Path logsFolder, String modVersion, String versionLabel,
                                    Settings settings, ScalpelLog.Mirror mirror) {
        ScalpelLog log = new ScalpelLog(settings.logFile() ? logsFolder.resolve("scalpel.log") : null, mirror);
        RulesLoader.Loaded loaded;
        try {
            loaded = RulesLoader.load(rulesFolder);
        } catch (IOException e) {
            log.error("Could not read rules from " + rulesFolder + ": " + e.getMessage() + ". Nothing will be cut.");
            loaded = new RulesLoader.Loaded(List.of(), List.of());
        }
        ScalpelCore core = new ScalpelCore(rulesFolder, logsFolder, modVersion, versionLabel, settings, log, loaded);
        for (RuleParser.ParseError error : loaded.errors()) {
            log.error("Rule skipped, " + error);
        }
        log.info("Scalpel " + versionLabel + ": " + core.rules().rules().size() + " rules from "
                + loaded.files().size() + " files, rules hash " + core.hash.shortForm()
                + (settings.dryRun() ? ". DRY RUN: nothing will be cut." : ""));
        return core;
    }

    public Settings settings() {
        return settings;
    }

    public ScalpelLog log() {
        return log;
    }

    public Report report() {
        return report;
    }

    public Resolver resolver() {
        return resolver;
    }

    public RuleSet rules() {
        return resolver.rules();
    }

    public RulesHash hash() {
        return hash;
    }

    public List<RuleParser.ParseError> parseErrors() {
        return parseErrors;
    }

    public String modVersion() {
        return modVersion;
    }

    public String versionLabel() {
        return versionLabel;
    }

    public String side() {
        return side;
    }

    public void side(String side) {
        this.side = Objects.requireNonNull(side);
    }

    public Path rulesFolder() {
        return rulesFolder;
    }

    public Path reportFile() {
        return logsFolder.resolve("scalpel").resolve("report.txt");
    }

    public Path logsFolder() {
        return logsFolder;
    }

    /** True when cuts should really happen, false in a dry run. */
    public boolean applies() {
        return !settings.dryRun();
    }

    public void writeReport() {
        try {
            report.write(reportFile(), this);
        } catch (IOException e) {
            log.warn("Could not write " + reportFile() + ": " + e.getMessage());
        }
    }

    /** Called when data (recipes, tags, loot, ...) starts loading: data results are rebuilt from scratch. */
    public void beginDataLoad() {
        report.resetData();
        java.util.Set<ContentType> dataTypes = java.util.EnumSet.complementOf(java.util.EnumSet.copyOf(ContentType.REGISTRY_TYPES));
        for (Rule rule : rules().rules()) {
            rule.clearMatches(dataTypes);
        }
    }

    /** Logs a warning once and adds it to the report. */
    public void warn(String message) {
        report.warn(message);
        log.warn(message);
    }

    public record ReloadResult(int rules, int errors, boolean registryRulesChanged) {
    }

    /**
     * Re-reads the rules folder for {@code /scalpel reload}. Data rules take effect on the next data load.
     * Registry rules cannot change without a restart; if they differ from the ones the game started with,
     * the old registry rules stay in force (with their match counts) and the result says so.
     */
    public ReloadResult reloadRules() throws IOException {
        RulesLoader.Loaded loaded = RulesLoader.load(rulesFolder);
        RuleSet fresh = loaded.toRuleSet();
        boolean registryChanged = !registryCanonical(resolver.rules()).equals(registryCanonical(fresh));

        List<Rule> merged = new ArrayList<>();
        for (Rule rule : resolver.rules().rules()) {
            if (rule.types().stream().anyMatch(ContentType::isRegistry)) {
                merged.add(rule);
            }
        }
        for (Rule rule : fresh.rules()) {
            if (rule.types().stream().noneMatch(ContentType::isRegistry)) {
                merged.add(rule);
            }
        }
        resolver.replaceRules(new RuleSet(merged));
        parseErrors = List.copyOf(loaded.errors());
        for (RuleParser.ParseError error : loaded.errors()) {
            log.error("Rule skipped, " + error);
        }
        return new ReloadResult(fresh.rules().size(), loaded.errors().size(), registryChanged);
    }

    private static List<String> registryCanonical(RuleSet set) {
        List<String> lines = new ArrayList<>();
        for (Rule rule : set.rules()) {
            if (rule.types().stream().anyMatch(ContentType::isRegistry)) {
                lines.add(rule.file() + " " + rule.canonical());
            }
        }
        return lines;
    }
}
