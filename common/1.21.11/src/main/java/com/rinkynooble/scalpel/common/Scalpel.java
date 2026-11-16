package com.rinkynooble.scalpel.common;

import com.rinkynooble.scalpel.core.ContentType;
import com.rinkynooble.scalpel.core.Decision;
import com.rinkynooble.scalpel.core.ScalpelCore;
import com.rinkynooble.scalpel.core.ScalpelLog;
import com.rinkynooble.scalpel.core.Settings;
import com.rinkynooble.scalpel.core.SettingsFile;
import com.rinkynooble.scalpel.core.rules.Rule;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

/** Holds the one {@link ScalpelCore} for this game. Created on first use, which is the first registry entry. */
public final class Scalpel {
    public static final String MOD_ID = "scalpel";
    public static final Logger LOGGER = LogManager.getLogger("Scalpel");

    private static volatile Platform platform;
    private static volatile ScalpelCore core;

    private Scalpel() {
    }

    public static Platform platform() {
        Platform p = platform;
        if (p == null) {
            synchronized (Scalpel.class) {
                p = platform;
                if (p == null) {
                    p = platform = ServiceLoader.load(Platform.class, Platform.class.getClassLoader()).findFirst()
                            .orElseThrow(() -> new IllegalStateException("Scalpel: no Platform for this loader"));
                }
            }
        }
        return p;
    }

    public static ScalpelCore core() {
        ScalpelCore c = core;
        if (c == null) {
            synchronized (Scalpel.class) {
                c = core;
                if (c == null) {
                    c = core = start();
                }
            }
        }
        return c;
    }

    private static ScalpelCore start() {
        ScalpelLog.Mirror mirror = new ScalpelLog.Mirror() {
            @Override
            public void info(String message) {
                LOGGER.info(message);
            }

            @Override
            public void warn(String message) {
                LOGGER.warn(message);
            }

            @Override
            public void error(String message) {
                LOGGER.error(message);
            }
        };
        Platform p = platform();
        Settings settings = SettingsFile.read(p.configDir().resolve(SettingsFile.RELATIVE_PATH), LOGGER::warn);
        String version = p.modVersion();
        ScalpelCore started = ScalpelCore.start(
                p.configDir().resolve(MOD_ID),
                p.gameDir().resolve("logs"),
                version,
                version + " (" + p.port() + ")",
                settings,
                mirror);
        started.side(p.isClient() ? "client" : "dedicated server");
        return started;
    }

    /** Registration is over: summarise it and write the first report. Each port calls this once, at its setup stage. */
    public static void afterRegistration() {
        ScalpelCore core = Scalpel.core();
        core.report().evaluated(ContentType.REGISTRY_TYPES);
        StringBuilder summary = new StringBuilder(core.settings().dryRun() ? "Dry run, would cut:" : "Cut:");
        for (ContentType type : ContentType.REGISTRY_TYPES) {
            long redacted = core.resolver().cutEntries(type).values().stream().filter(d -> d.action() == Decision.Action.REDACT).count();
            long removed = core.resolver().cutEntries(type).values().stream().filter(d -> d.action() == Decision.Action.REMOVE).count();
            summary.append(' ').append(com.rinkynooble.scalpel.core.Report.plural(type)).append(' ').append(redacted).append(" redacted/")
                    .append(removed).append(" removed,");
        }
        summary.setLength(summary.length() - 1);
        List<String> hidden = new ArrayList<>();
        for (ContentType type : ContentType.REGISTRY_TYPES) {
            int count = core.resolver().hiddenEntries(type).size();
            if (count > 0) {
                hidden.add(count + " " + (count == 1 ? type.keyword() : com.rinkynooble.scalpel.core.Report.plural(type)));
            }
        }
        if (!hidden.isEmpty()) {
            summary.append(core.settings().dryRun() ? ". Would hide: " : ". Hidden: ").append(String.join(", ", hidden));
        }
        core.log().info(summary + ". Report: " + core.reportFile());
        List<Rule> unmatched = core.report().unmatchedRules(core.rules().rules());
        for (Rule rule : unmatched) {
            // Enchantment rules are decided when a world loads (enchantments are data since 1.21): ServerData warns.
            if (rule.types().stream().allMatch(ContentType::isRegistry) && !rule.types().contains(ContentType.ENCHANTMENT)) {
                core.warn("rule matched nothing: " + rule.canonical() + " (" + rule.location() + ")");
            }
        }
        core.writeReport();
    }
}
