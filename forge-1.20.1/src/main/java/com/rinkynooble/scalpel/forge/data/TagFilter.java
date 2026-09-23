package com.rinkynooble.scalpel.forge.data;

import com.rinkynooble.scalpel.core.ContentType;
import com.rinkynooble.scalpel.core.Decision;
import com.rinkynooble.scalpel.core.Resolver;
import com.rinkynooble.scalpel.core.ScalpelCore;
import com.rinkynooble.scalpel.forge.Scalpel;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagLoader;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tags hold many entries per file, so they are cleaned entry by entry. Entries are removed right after the files
 * are read (a removed id would otherwise make the tag, and every recipe using it, fail to load), and placeholders
 * are removed again from the built tags in case a script added them back.
 *
 * <p>Tags that end up empty because of cuts are remembered: a recipe whose ingredient is such a tag can no longer
 * be crafted, and the final recipe pass rewrites or drops it.
 */
public final class TagFilter {
    /** Per tag directory: tags that lost entries to cuts, and which tags each tag includes. */
    private static final Map<String, Set<String>> touched = new ConcurrentHashMap<>();
    private static final Map<String, Map<String, List<String>>> includes = new ConcurrentHashMap<>();
    private static final Set<String> emptiedItemTags = ConcurrentHashMap.newKeySet();
    /** Item tag id to the cut item ids it lost, so a recipe using the tag follows those items' recipe option. */
    private static final Map<String, Set<String>> cutMembers = new ConcurrentHashMap<>();

    private TagFilter() {
    }

    static void reset() {
        touched.clear();
        includes.clear();
        emptiedItemTags.clear();
        cutMembers.clear();
    }

    /** The cut items an emptied item tag lost (directly or through tags it includes). Empty when unknown. */
    public static Set<String> cutMembers(String tagId) {
        return cutMembers.getOrDefault(tagId, Set.of());
    }

    /** Item tags that are empty only because of what was cut. */
    public static Set<String> emptiedItemTags() {
        return emptiedItemTags;
    }

    static ContentType typeFor(String directory) {
        return switch (directory) {
            case "tags/items" -> ContentType.ITEM;
            case "tags/blocks" -> ContentType.BLOCK;
            case "tags/entity_types" -> ContentType.ENTITY;
            default -> null;
        };
    }

    public static void afterLoad(String directory, Map<ResourceLocation, List<TagLoader.EntryWithSource>> tags) {
        ScalpelCore core = Scalpel.core();
        Resolver resolver = core.resolver();
        ContentType type = typeFor(directory);
        boolean scrub = type != null && !resolver.cutIds(type).isEmpty();
        boolean rules = resolver.rules().hasRulesFor(ContentType.TAG);
        if (scrub || rules) {
            Set<String> lost = touched.computeIfAbsent(directory, k -> ConcurrentHashMap.newKeySet());
            Map<String, List<String>> refs = includes.computeIfAbsent(directory, k -> new ConcurrentHashMap<>());
            for (Map.Entry<ResourceLocation, List<TagLoader.EntryWithSource>> tag : tags.entrySet()) {
                String tagId = tag.getKey().toString();
                List<TagLoader.EntryWithSource> entries = tag.getValue();
                List<String> included = new ArrayList<>();
                for (TagLoader.EntryWithSource entry : entries) {
                    if (entry.entry().isTag()) {
                        included.add(entry.entry().getId().toString());
                    }
                }
                refs.put(tagId, included);
                if (rules) {
                    Decision decision = resolver.decide(ContentType.TAG, tagId);
                    if (decision.action() == Decision.Action.REMOVE) {
                        DataFilter.change(core, "tag emptied by rule", tagId, directory + ", " + decision.rule().location());
                        if (type == ContentType.ITEM) {
                            emptiedItemTags.add(tagId);
                        }
                        if (core.applies()) {
                            entries.clear();
                        }
                        continue;
                    }
                }
                if (!scrub) {
                    continue;
                }
                List<String> removed = new ArrayList<>();
                for (TagLoader.EntryWithSource entry : entries) {
                    String id = entry.entry().getId().toString();
                    if (!entry.entry().isTag() && resolver.isCut(type, id)) {
                        removed.add(id);
                    }
                }
                if (!removed.isEmpty()) {
                    lost.add(tagId);
                    if (type == ContentType.ITEM) {
                        cutMembers.computeIfAbsent(tagId, k -> ConcurrentHashMap.newKeySet()).addAll(removed);
                    }
                    DataFilter.change(core, "tag entries removed", tagId, directory + ": " + String.join(", ", removed));
                    if (core.applies()) {
                        entries.removeIf(e -> !e.entry().isTag() && removed.contains(e.entry().getId().toString()));
                    }
                }
            }
        }
        core.report().evaluated(Set.of(ContentType.TAG));
    }

    /** Takes redacted placeholders out of built tags, whatever put them there, and works out which tags cuts emptied. */
    public static <T> void afterBuild(String directory, Map<ResourceLocation, Collection<T>> built) {
        ContentType type = typeFor(directory);
        ScalpelCore core = Scalpel.core();
        if (type == null || core.resolver().cutIds(type).isEmpty()) {
            return;
        }
        Resolver resolver = core.resolver();
        Set<String> lost = touched.computeIfAbsent(directory, k -> ConcurrentHashMap.newKeySet());
        for (Map.Entry<ResourceLocation, Collection<T>> tag : built.entrySet()) {
            Collection<T> values = tag.getValue();
            List<T> kept = new ArrayList<>(values.size());
            for (T value : values) {
                if (!isCut(resolver, type, value)) {
                    kept.add(value);
                } else if (type == ContentType.ITEM && value instanceof Holder<?> holder) {
                    holder.unwrapKey().ifPresent(k -> cutMembers.computeIfAbsent(tag.getKey().toString(),
                            x -> ConcurrentHashMap.newKeySet()).add(k.location().toString()));
                }
            }
            if (kept.size() != values.size()) {
                lost.add(tag.getKey().toString());
                if (core.applies()) {
                    tag.setValue(kept);
                }
            }
        }
        if (type == ContentType.ITEM) {
            findEmptied(core, directory, built, lost);
        }
    }

    /** A tag counts as emptied by cuts when it is empty now and it lost entries, or includes a tag that was emptied. */
    private static <T> void findEmptied(ScalpelCore core, String directory, Map<ResourceLocation, Collection<T>> built, Set<String> lost) {
        Map<String, List<String>> refs = includes.getOrDefault(directory, Map.of());
        Set<String> empty = new HashSet<>();
        for (Map.Entry<ResourceLocation, Collection<T>> tag : built.entrySet()) {
            String tagId = tag.getKey().toString();
            boolean nowEmpty = tag.getValue().isEmpty()
                    || (!core.applies() && tag.getValue().stream().allMatch(v -> isCut(core.resolver(), ContentType.ITEM, v)));
            if (nowEmpty) {
                empty.add(tagId);
            }
        }
        Set<String> emptied = new HashSet<>();
        boolean grew = true;
        while (grew) {
            grew = false;
            for (String tagId : empty) {
                if (emptied.contains(tagId)) {
                    continue;
                }
                boolean cause = lost.contains(tagId);
                for (String included : refs.getOrDefault(tagId, List.of())) {
                    if (emptied.contains(included)) {
                        cause = true;
                        cutMembers.computeIfAbsent(tagId, k -> ConcurrentHashMap.newKeySet()).addAll(cutMembers(included));
                    }
                }
                if (cause) {
                    emptied.add(tagId);
                    grew = true;
                }
            }
        }
        emptiedItemTags.addAll(emptied);
        for (String tagId : emptied) {
            DataFilter.change(core, "tag emptied by cuts", tagId, directory + ": recipes using it are rewritten or dropped");
        }
    }

    private static boolean isCut(Resolver resolver, ContentType type, Object value) {
        if (value instanceof Holder<?> holder) {
            return holder.unwrapKey().map(k -> resolver.isCut(type, k.location().toString())).orElse(false);
        }
        return false;
    }
}
