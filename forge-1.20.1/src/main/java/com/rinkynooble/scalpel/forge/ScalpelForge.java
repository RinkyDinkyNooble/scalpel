package com.rinkynooble.scalpel.forge;

import com.rinkynooble.scalpel.core.ContentType;
import com.rinkynooble.scalpel.core.Decision;
import com.rinkynooble.scalpel.core.ScalpelCore;
import com.rinkynooble.scalpel.core.rules.Rule;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import java.util.List;

@Mod(Scalpel.MOD_ID)
public final class ScalpelForge {
    public ScalpelForge() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ForgeSettings.SPEC, ForgeSettings.FILE_NAME);
        modBus.addListener(this::commonSetup);
        Scalpel.core();
    }

    /** Registration is over by now: summarise it and write the first report. */
    private void commonSetup(FMLCommonSetupEvent event) {
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
        core.log().info(summary + ". Report: " + core.reportFile());
        List<Rule> unmatched = core.report().unmatchedRules(core.rules().rules());
        for (Rule rule : unmatched) {
            if (rule.types().stream().allMatch(ContentType::isRegistry)) {
                core.warn("rule matched nothing: " + rule.canonical() + " (" + rule.location() + ")");
            }
        }
        core.writeReport();
    }
}
