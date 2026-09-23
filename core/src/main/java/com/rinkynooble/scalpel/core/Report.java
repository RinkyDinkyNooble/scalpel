package com.rinkynooble.scalpel.core;

import com.rinkynooble.scalpel.core.rules.Rule;
import com.rinkynooble.scalpel.core.rules.RuleParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Collects everything Scalpel did and writes {@code logs/scalpel/report.txt}, grouped by mod. */
public final class Report {
    /** A registry entry that was cut (or would be, in a dry run). {@code owner} is the mod that registered it. */
    public record CutEntry(ContentType type, String id, String owner, Decision decision) {
    }

    /** A data change: a dropped recipe, a removed loot entry, a fixed worldgen file, and so on. */
    public record Change(String category, String target, String detail) {
    }

    private final Collection<CutEntry> cuts = new ConcurrentLinkedQueue<>();
    private final Collection<CutEntry> protectedHits = new ConcurrentLinkedQueue<>();
    private final Set<String> warnings = Collections.synchronizedSet(new LinkedHashSet<>());
    private final Set<Change> changes = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Set<ContentType> evaluated = Collections.synchronizedSet(EnumSet.noneOf(ContentType.class));
    private volatile List<Set<String>> cascade = List.of();
    private volatile int cascadeDepth;

    public void cut(ContentType type, String id, String owner, Decision decision) {
        cuts.add(new CutEntry(type, id, owner, decision));
    }

    public void protectedHit(ContentType type, String id, String owner, Decision decision) {
        protectedHits.add(new CutEntry(type, id, owner, decision));
    }

    public void warn(String warning) {
        warnings.add(warning);
    }

    public void change(String category, String target, String detail) {
        changes.add(new Change(category, target, detail));
    }

    /** Marks that ids of these types have been checked, so rules for them can be reported as unmatched. */
    public void evaluated(Set<ContentType> types) {
        evaluated.addAll(types);
    }

    /**
     * Called before data reloads: data changes are rebuilt from scratch. Changes that only happen once
     * (worldgen, structures, trades, creative tabs) are kept.
     */
    public void resetData() {
        changes.removeIf(c -> !(c.category().startsWith("worldgen") || c.category().startsWith("structure")
                || c.category().startsWith("trade") || c.category().startsWith("creative tab")));
        cascade = List.of();
    }

    public void cascade(List<Set<String>> levels, int depth) {
        this.cascade = List.copyOf(levels);
        this.cascadeDepth = depth;
    }

    public Collection<CutEntry> cuts() {
        return Collections.unmodifiableCollection(cuts);
    }

    public Collection<Change> changes() {
        return Collections.unmodifiableCollection(changes);
    }

    public List<Rule> unmatchedRules(List<Rule> rules) {
        List<Rule> unmatched = new ArrayList<>();
        for (Rule rule : rules) {
            boolean checked = false;
            for (ContentType type : rule.types()) {
                checked |= evaluated.contains(type);
            }
            if (checked && rule.matchCount() == 0) {
                unmatched.add(rule);
            }
        }
        return unmatched;
    }

    public void write(Path file, ScalpelCore core) throws IOException {
        StringBuilder out = new StringBuilder();
        header(out, core);
        rules(out, core);
        registry(out, core.settings().dryRun());
        data(out);
        cascadeSection(out);
        Files.createDirectories(file.getParent());
        Files.writeString(file, out.toString(), StandardCharsets.UTF_8);
    }

    private static void header(StringBuilder out, ScalpelCore core) {
        out.append("Scalpel report\n==============\n");
        out.append("Version:  ").append(core.versionLabel()).append('\n');
        out.append("Side:     ").append(core.side()).append('\n');
        out.append("Written:  ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append('\n');
        if (core.settings().dryRun()) {
            out.append("Mode:     DRY RUN, nothing was cut. Everything below is what would happen.\n");
        }
        out.append("Settings: ").append(describe(core.settings())).append('\n');
        out.append("Rules hash: ").append(core.hash().full()).append('\n');
        for (Map.Entry<String, String> entry : core.hash().perFile().entrySet()) {
            out.append("  ").append(entry.getValue()).append("  ").append(entry.getKey()).append('\n');
        }
        out.append("Client and server must show the same rules hash.\n\n");
    }

    private void rules(StringBuilder out, ScalpelCore core) {
        List<RuleParser.ParseError> errors = core.parseErrors();
        if (!errors.isEmpty()) {
            out.append("Rule errors (").append(errors.size()).append("). These lines were skipped.\n");
            for (RuleParser.ParseError error : errors) {
                out.append("  ").append(error).append('\n');
            }
            out.append('\n');
        }
        List<Rule> rules = core.rules().rules();
        out.append("Rules (").append(rules.size()).append(")\n");
        for (Rule rule : rules) {
            out.append("  ").append(pad(rule.location(), 24)).append(pad(rule.canonical(), 48));
            List<String> counts = new ArrayList<>();
            for (ContentType type : rule.types()) {
                if (evaluated.contains(type)) {
                    counts.add(type.keyword() + " " + rule.matchCount(type));
                }
            }
            out.append(counts.isEmpty() ? "not checked yet" : String.join(", ", counts)).append('\n');
        }
        List<Rule> unmatched = unmatchedRules(rules);
        if (!unmatched.isEmpty()) {
            out.append("\nUnmatched rules (").append(unmatched.size())
                    .append("). These matched nothing: check for typos, or ids a mod update renamed.\n");
            for (Rule rule : unmatched) {
                out.append("  ").append(pad(rule.location(), 24)).append(rule.canonical()).append('\n');
            }
        }
        if (!warnings.isEmpty()) {
            out.append("\nWarnings (").append(warnings.size()).append(")\n");
            synchronized (warnings) {
                for (String warning : warnings) {
                    out.append("  ").append(warning).append('\n');
                }
            }
        }
        out.append('\n');
    }

    private void registry(StringBuilder out, boolean dryRun) {
        out.append("Registry\n--------\n");
        if (cuts.isEmpty()) {
            out.append("Nothing was cut.\n\n");
        } else {
            for (ContentType type : ContentType.REGISTRY_TYPES) {
                long redacted = cuts.stream().filter(c -> c.type() == type && c.decision().action() == Decision.Action.REDACT).count();
                long removed = cuts.stream().filter(c -> c.type() == type && c.decision().action() == Decision.Action.REMOVE).count();
                out.append(pad(plural(type) + ":", 11)).append(redacted).append(" redacted, ").append(removed).append(" removed\n");
            }
            if (dryRun) {
                out.append("(dry run: would be)\n");
            }
            Map<String, List<CutEntry>> byMod = new TreeMap<>();
            for (CutEntry entry : cuts) {
                byMod.computeIfAbsent(entry.owner(), k -> new ArrayList<>()).add(entry);
            }
            for (Map.Entry<String, List<CutEntry>> mod : byMod.entrySet()) {
                List<CutEntry> entries = mod.getValue();
                entries.sort(Comparator.comparing((CutEntry e) -> e.type()).thenComparing(CutEntry::id));
                out.append("\n[").append(mod.getKey()).append("] ").append(entries.size()).append('\n');
                for (CutEntry entry : entries) {
                    out.append("  ").append(pad(entry.type().keyword(), 7)).append(pad(entry.id(), 56))
                            .append(entry.decision().describe()).append('\n');
                }
            }
            out.append('\n');
        }
        if (!protectedHits.isEmpty()) {
            out.append("Protected (").append(protectedHits.size()).append("). A rule matched these, but they are not cut.\n");
            for (CutEntry entry : protectedHits) {
                out.append("  ").append(pad(entry.type().keyword(), 7)).append(pad(entry.id(), 56))
                        .append(entry.decision().describe()).append('\n');
            }
            out.append('\n');
        }
    }

    private void data(StringBuilder out) {
        out.append("Data (from the last data load)\n------------------------------\n");
        if (changes.isEmpty()) {
            out.append(evaluated.contains(ContentType.RECIPE) ? "No data changes.\n\n" : "Data has not loaded on this side.\n\n");
            return;
        }
        Map<String, Integer> totals = new TreeMap<>();
        Map<String, List<Change>> byMod = new TreeMap<>();
        for (Change change : changes) {
            totals.merge(change.category(), 1, Integer::sum);
            byMod.computeIfAbsent(namespace(change.target()), k -> new ArrayList<>()).add(change);
        }
        for (Map.Entry<String, Integer> total : totals.entrySet()) {
            out.append("  ").append(pad(total.getKey() + ":", 32)).append(total.getValue()).append('\n');
        }
        for (Map.Entry<String, List<Change>> mod : byMod.entrySet()) {
            List<Change> list = mod.getValue();
            list.sort(Comparator.comparing(Change::category).thenComparing(Change::target));
            out.append("\n[").append(mod.getKey()).append("] ").append(list.size()).append('\n');
            for (Change change : list) {
                out.append("  ").append(pad(change.category(), 28)).append(pad(change.target(), 56))
                        .append(change.detail() == null ? "" : change.detail()).append('\n');
            }
        }
        out.append('\n');
    }

    private void cascadeSection(StringBuilder out) {
        if (cascade.isEmpty()) {
            return;
        }
        out.append("Lost every recipe (report only, nothing here was cut)\n-----------------------------------------------------\n");
        for (int i = 0; i < cascade.size(); i++) {
            Set<String> level = cascade.get(i);
            out.append("Level ").append(i + 1).append(" (").append(level.size()).append(")")
                    .append(i == 0 ? ": its recipes used or made cut content\n" : ": every recipe needs something from the level above\n");
            for (String id : level) {
                out.append("  ").append(id).append('\n');
            }
        }
        if (cascade.size() == cascadeDepth) {
            out.append("Stopped at cascadeDepth = ").append(cascadeDepth).append(".\n");
        }
        out.append('\n');
    }

    public static String plural(ContentType type) {
        return switch (type) {
            case ENTITY -> "entities";
            case LOOT -> "loot tables";
            default -> type.keyword() + "s";
        };
    }

    private static String describe(Settings s) {
        return "dryRun=" + s.dryRun() + ", logFile=" + s.logFile() + ", allowRemovingVanilla=" + s.allowRemovingVanilla()
                + ", protectCriticalIds=" + s.protectCriticalIds() + ", ingredientMode=" + s.ingredientMode().keyword()
                + ", cascadeDepth=" + s.cascadeDepth();
    }

    private static String namespace(String id) {
        int colon = id.indexOf(':');
        return colon > 0 ? id.substring(0, colon) : "minecraft";
    }

    private static String pad(String text, int width) {
        if (text.length() >= width) {
            return text + "  ";
        }
        return text + " ".repeat(width - text.length());
    }
}
