package com.rinkynooble.scalpel.core;

import com.rinkynooble.scalpel.core.Decision.Action;
import com.rinkynooble.scalpel.core.rules.Rule;
import com.rinkynooble.scalpel.core.rules.RuleSet;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Turns rules into decisions and remembers what was cut, so linked content (block items, spawn eggs)
 * and the data filters can follow.
 *
 * <p>Precedence: keep, then redact, then remove. Then protection, then the vanilla guard
 * ({@code remove} on a {@code minecraft:} id becomes {@code redact} unless allowed).
 */
public final class Resolver {
    private volatile RuleSet rules;
    private final Settings settings;
    private final Map<ContentType, Map<String, Decision>> cut = new EnumMap<>(ContentType.class);

    public Resolver(RuleSet rules, Settings settings) {
        this.rules = rules;
        this.settings = settings;
        for (ContentType type : ContentType.REGISTRY_TYPES) {
            cut.put(type, new ConcurrentHashMap<>());
        }
    }

    public RuleSet rules() {
        return rules;
    }

    public Settings settings() {
        return settings;
    }

    /** Swaps the rules used for new decisions. Decisions already made (and cut state) are kept. */
    void replaceRules(RuleSet rules) {
        this.rules = rules;
    }

    /** Decides and counts the match towards the rule's statistics. */
    public Decision decide(ContentType type, String id) {
        return resolve(type, id, true);
    }

    /** Decides without counting, for commands and previews. */
    public Decision peek(ContentType type, String id) {
        return resolve(type, id, false);
    }

    private Decision resolve(ContentType type, String id, boolean record) {
        List<Rule> matched = rules.matching(type, id, record);
        if (matched.isEmpty()) {
            return Decision.NONE;
        }
        Rule winner = matched.get(0);
        for (Rule rule : matched) {
            if (rule.verb().ordinal() < winner.verb().ordinal()) {
                winner = rule;
            }
        }
        return guard(type, id, winner, matched, null);
    }

    private Decision guard(ContentType type, String id, Rule rule, List<Rule> matched, String note) {
        return guard(type, id, rule.verb(), rule, matched, note);
    }

    private Decision guard(ContentType type, String id, Verb verb, Rule rule, List<Rule> matched, String note) {
        if (verb == Verb.KEEP) {
            return new Decision(Action.KEEP, rule, matched, note);
        }
        if (!type.isRegistry()) {
            return new Decision(Action.REMOVE, rule, matched, note);
        }
        Protection.Level level = Protection.of(type, id);
        if (level == Protection.Level.HARD) {
            return new Decision(Action.PROTECTED, rule, matched, join(note, "always protected"));
        }
        if (level == Protection.Level.SOFT && settings.protectCriticalIds()) {
            return new Decision(Action.PROTECTED, rule, matched, join(note, "protected, see protectCriticalIds"));
        }
        if (verb == Verb.REMOVE && id.startsWith("minecraft:") && !settings.allowRemovingVanilla()) {
            return new Decision(Action.REDACT, rule, matched, join(note, "vanilla ids are redacted, not removed, see allowRemovingVanilla"));
        }
        return new Decision(verb == Verb.REDACT ? Action.REDACT : Action.REMOVE, rule, matched, note);
    }

    private static String join(String a, String b) {
        return a == null ? b : a + "; " + b;
    }

    /** A block follows its own rules first, then any item rule for the same id. */
    public Decision decideBlock(String id) {
        Decision own = decide(ContentType.BLOCK, id);
        if (own.action() != Action.NONE) {
            return own;
        }
        Decision item = peek(ContentType.ITEM, id);
        if (item.isCut()) {
            return guard(ContentType.BLOCK, id, item.rule(), item.matched(), "linked: item rule for the same id");
        }
        return own;
    }

    /**
     * An item follows its own rules first. Otherwise it is cut along with a block it places or an entity it spawns.
     *
     * @param placesBlocks ids of blocks this item places (empty if it is not a block item)
     * @param spawnsEntity id of the entity a spawn egg spawns, or null
     */
    public Decision decideItem(String id, Collection<String> placesBlocks, String spawnsEntity) {
        Decision own = decide(ContentType.ITEM, id);
        if (own.action() != Action.NONE) {
            return own;
        }
        for (String blockId : placesBlocks) {
            Decision block = cut.get(ContentType.BLOCK).get(blockId);
            if (block != null) {
                return guard(ContentType.ITEM, id, block.action() == Action.REDACT ? Verb.REDACT : Verb.REMOVE,
                        block.rule(), block.matched(), "linked: places block " + blockId);
            }
        }
        if (spawnsEntity != null) {
            // Items register before entity types, so the entity may not have been decided yet.
            Decision entity = cut.get(ContentType.ENTITY).get(spawnsEntity);
            if (entity == null) {
                Decision pending = peek(ContentType.ENTITY, spawnsEntity);
                entity = pending.isCut() ? pending : null;
            }
            if (entity != null) {
                return guard(ContentType.ITEM, id, entity.action() == Action.REDACT ? Verb.REDACT : Verb.REMOVE,
                        entity.rule(), entity.matched(), "linked: spawn egg for " + spawnsEntity);
            }
        }
        return own;
    }

    /**
     * A cut that comes from a link the port found itself (for example a block item that places the shared
     * {@code scalpel:removed} block). Protection and the vanilla guard still apply.
     */
    public Decision decideLinked(ContentType type, String id, Action action, String note) {
        Decision own = decide(type, id);
        if (own.action() != Action.NONE) {
            return own;
        }
        return guard(type, id, action == Action.REDACT ? Verb.REDACT : Verb.REMOVE, null, List.of(), note);
    }

    public Decision decideEntity(String id) {
        return decide(ContentType.ENTITY, id);
    }

    /** Records that {@code id} was cut (or would be, in a dry run). */
    public void markCut(ContentType type, String id, Decision decision) {
        if (decision.isCut()) {
            cut.get(type).put(id, decision);
        }
    }

    /** The cut decision for {@code id}, or null if it was not cut. */
    public Decision cutState(ContentType type, String id) {
        Map<String, Decision> map = cut.get(type);
        return map == null ? null : map.get(id);
    }

    public boolean isCut(ContentType type, String id) {
        return cutState(type, id) != null;
    }

    public Map<String, Decision> cutEntries(ContentType type) {
        return Collections.unmodifiableMap(cut.get(type));
    }

    public Set<String> cutIds(ContentType type) {
        return Collections.unmodifiableSet(cut.get(type).keySet());
    }

    /** True if any registry type has {@code id} cut. Data filters use this for recipes, loot and advancements. */
    public boolean isCutAnywhere(String id) {
        for (Map<String, Decision> map : cut.values()) {
            if (map.containsKey(id)) {
                return true;
            }
        }
        return false;
    }
}
