package com.rinkynooble.scalpel.forge.command;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.rinkynooble.scalpel.core.ContentType;
import com.rinkynooble.scalpel.core.Decision;
import com.rinkynooble.scalpel.core.IdIndex;
import com.rinkynooble.scalpel.core.Resolver;
import com.rinkynooble.scalpel.core.ScalpelCore;
import com.rinkynooble.scalpel.core.json.JsonScrub;
import com.rinkynooble.scalpel.core.rules.IdPattern;
import com.rinkynooble.scalpel.core.rules.Rule;
import com.rinkynooble.scalpel.forge.Scalpel;
import com.rinkynooble.scalpel.forge.registry.RegistryCutter;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.commands.ReloadCommand;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;

/**
 * {@code /scalpel find|test|explain|reload|report}. Operators only (permission level 2).
 * Long results go to a file in {@code logs/scalpel/}; chat gets the counts and the first few lines.
 */
public final class ScalpelCommand {
    private static final int CHAT_LINES = 10;
    private static final List<String> TYPE_WORDS = List.of("item", "block", "entity", "any", "recipe", "loot", "advancement", "tag");
    /** Data folders scanned by {@code find}. Worldgen and Forge's modifiers are under these prefixes. */
    private static final List<String> FIND_FOLDERS = List.of("recipes", "loot_tables", "advancements", "tags", "loot_modifiers", "worldgen", "forge");

    private ScalpelCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("scalpel")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("find")
                        .then(Commands.argument("id", ResourceLocationArgument.id())
                                .suggests(IdSuggestions.registryIds())
                                .executes(ctx -> find(ctx.getSource(), ResourceLocationArgument.getId(ctx, "id")))))
                .then(Commands.literal("test")
                        .then(Commands.argument("type", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(TYPE_WORDS, builder))
                                .then(Commands.argument("pattern", StringArgumentType.greedyString())
                                        .suggests(ScalpelCommand::suggestPattern)
                                        .executes(ctx -> test(ctx.getSource(), StringArgumentType.getString(ctx, "type"),
                                                StringArgumentType.getString(ctx, "pattern"))))))
                .then(Commands.literal("explain")
                        .then(Commands.argument("id", ResourceLocationArgument.id())
                                .suggests(IdSuggestions.registryIds())
                                .executes(ctx -> explain(ctx.getSource(), ResourceLocationArgument.getId(ctx, "id")))))
                .then(Commands.literal("reload").executes(ctx -> reload(ctx.getSource())))
                .then(Commands.literal("report").executes(ctx -> report(ctx.getSource()))));
    }

    // ------------------------------------------------------------------ test

    private static CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestPattern(
            CommandContext<CommandSourceStack> ctx, com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        Set<ContentType> types = ContentType.parse(StringArgumentType.getString(ctx, "type"));
        if (types == null) {
            return builder.buildFuture();
        }
        MinecraftServer server = ctx.getSource().getServer();
        IdIndex index = types.size() > 1 ? IdSuggestions.anyRegistry(server) : IdSuggestions.index(server, types.iterator().next());
        return IdSuggestions.suggest(index, builder);
    }

    private static int test(CommandSourceStack source, String typeWord, String patternText) {
        Set<ContentType> types = ContentType.parse(typeWord);
        if (types == null) {
            source.sendFailure(Component.literal("Unknown type '" + typeWord + "'. Use one of: " + String.join(", ", TYPE_WORDS)));
            return 0;
        }
        String text = patternText.strip();
        if (types.contains(ContentType.TAG) && text.startsWith("#")) {
            text = text.substring(1);
        }
        IdPattern pattern;
        try {
            pattern = IdPattern.parse(text);
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal("Bad pattern: " + e.getMessage()));
            return 0;
        }
        Resolver resolver = Scalpel.core().resolver();
        MinecraftServer server = source.getServer();
        List<String> lines = new ArrayList<>();
        List<String> counts = new ArrayList<>();
        int total = 0;
        for (ContentType type : types) {
            List<String> matches = new ArrayList<>();
            for (String id : IdSuggestions.index(server, type).all()) {
                if (pattern.matches(id)) {
                    matches.add(id);
                }
            }
            total += matches.size();
            counts.add(matches.size() + " " + (matches.size() == 1 ? type.keyword() : com.rinkynooble.scalpel.core.Report.plural(type)));
            if (types.size() > 1) {
                lines.add(com.rinkynooble.scalpel.core.Report.plural(type) + ":");
            }
            for (String id : matches) {
                Decision state = type.isRegistry() ? resolver.cutState(type, id) : null;
                lines.add("  " + id + (state == null ? "" : "  (" + stateWord(state) + ")"));
            }
        }
        Path file = writeResult("test.txt", "/scalpel test " + typeWord + " " + patternText, lines);
        String summary = String.join(", ", counts);
        source.sendSuccess(() -> Component.literal(patternText + " matches " + summary + "."), false);
        sendFirst(source, lines);
        source.sendSuccess(() -> Component.literal("Full list: " + file).withStyle(ChatFormatting.GRAY), false);
        return total;
    }

    // ------------------------------------------------------------------ explain

    private static int explain(CommandSourceStack source, ResourceLocation location) {
        String id = location.toString();
        Resolver resolver = Scalpel.core().resolver();
        List<String> lines = new ArrayList<>();
        for (ContentType type : ContentType.values()) {
            Decision state = type.isRegistry() ? resolver.cutState(type, id) : null;
            Decision now = resolver.peek(type, id);
            if (type.isRegistry() && state == null && !registered(type, location)) {
                continue;
            }
            if (state == null && now.matched().isEmpty()) {
                continue;
            }
            Decision shown = state != null ? state : now;
            String word = state != null ? stateWord(state) : switch (now.action()) {
                case KEEP -> "kept";
                case PROTECTED -> "protected, not cut";
                case REMOVE -> "removed";
                default -> "no change";
            };
            StringBuilder line = new StringBuilder(type.keyword()).append(": ").append(word);
            if (shown.rule() != null) {
                line.append(" by ").append(shown.rule().location());
            }
            if (shown.note() != null) {
                line.append(" (").append(shown.note()).append(')');
            }
            lines.add(line.toString());
            for (Rule rule : now.matched()) {
                lines.add("  matching rule " + rule.location() + ": " + rule.canonical());
            }
        }
        if (lines.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No rule matches " + id + "."), false);
            return 0;
        }
        source.sendSuccess(() -> Component.literal(id + ":"), false);
        for (String line : lines) {
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return lines.size();
    }

    private static boolean registered(ContentType type, ResourceLocation id) {
        return switch (type) {
            case ITEM -> BuiltInRegistries.ITEM.containsKey(id);
            case BLOCK -> BuiltInRegistries.BLOCK.containsKey(id);
            case ENTITY -> BuiltInRegistries.ENTITY_TYPE.containsKey(id);
            default -> false;
        };
    }

    private static String stateWord(Decision state) {
        if (state.action() == Decision.Action.REMOVE) {
            return "removed";
        }
        return state.note() != null && state.note().contains("vanilla: hidden") ? "hidden" : "redacted";
    }

    // ------------------------------------------------------------------ find

    private static int find(CommandSourceStack source, ResourceLocation location) {
        String id = location.toString();
        MinecraftServer server = source.getServer();
        Map<String, List<String>> found = new LinkedHashMap<>();
        liveRecipes(server, id, found);
        liveTags(id, found);
        source.sendSuccess(() -> Component.literal("Looking for " + id + " in loaded recipes and tags, data files, and the config and script folders..."), false);
        ResourceManager resources = server.getResourceManager();
        CompletableFuture.supplyAsync(() -> scanFiles(resources, id), Util.backgroundExecutor())
                .whenCompleteAsync((files, error) -> {
                    if (error != null) {
                        source.sendFailure(Component.literal("Could not read data files: " + error.getMessage()));
                        return;
                    }
                    found.putAll(files);
                    List<String> lines = new ArrayList<>();
                    int total = 0;
                    for (Map.Entry<String, List<String>> group : found.entrySet()) {
                        lines.add(group.getKey() + ": " + group.getValue().size());
                        total += group.getValue().size();
                        for (String entry : group.getValue()) {
                            lines.add("  " + entry);
                        }
                    }
                    Path file = writeResult("find-" + id.replace(':', '_').replace('/', '_') + ".txt", "/scalpel find " + id, lines);
                    int count = total;
                    source.sendSuccess(() -> Component.literal(id + " is referenced " + count + " time" + (count == 1 ? "" : "s") + "."), false);
                    sendFirst(source, lines);
                    source.sendSuccess(() -> Component.literal("Full list: " + file).withStyle(ChatFormatting.GRAY), false);
                }, server);
        return 1;
    }

    /** Recipes loaded right now that make or use the id (after Scalpel's own filtering). */
    private static void liveRecipes(MinecraftServer server, String id, Map<String, List<String>> found) {
        for (Recipe<?> recipe : server.getRecipeManager().getRecipes()) {
            try {
                ItemStack result = recipe.getResultItem(server.registryAccess());
                if (!result.isEmpty() && RegistryCutter.itemId(result.getItem()).equals(id)) {
                    found.computeIfAbsent("loaded recipes that make it", k -> new ArrayList<>()).add(recipe.getId().toString());
                    continue;
                }
                for (Ingredient ingredient : recipe.getIngredients()) {
                    boolean uses = false;
                    for (ItemStack stack : ingredient.getItems()) {
                        uses |= RegistryCutter.itemId(stack.getItem()).equals(id);
                    }
                    if (uses) {
                        found.computeIfAbsent("loaded recipes that use it", k -> new ArrayList<>()).add(recipe.getId().toString());
                        break;
                    }
                }
            } catch (RuntimeException e) {
                // A recipe that cannot report its result or ingredients is skipped.
            }
        }
    }

    /** Tags that currently contain the id. */
    private static void liveTags(String id, Map<String, List<String>> found) {
        ResourceLocation location = new ResourceLocation(id);
        addTags(BuiltInRegistries.ITEM, location, "item tags", found);
        addTags(BuiltInRegistries.BLOCK, location, "block tags", found);
        addTags(BuiltInRegistries.ENTITY_TYPE, location, "entity tags", found);
    }

    private static <T> void addTags(Registry<T> registry, ResourceLocation id, String label, Map<String, List<String>> found) {
        registry.getHolder(ResourceKey.create(registry.key(), id)).ifPresent((Holder.Reference<T> holder) ->
                holder.tags().forEach(tag -> found.computeIfAbsent(label, k -> new ArrayList<>()).add("#" + tag.location())));
    }

    /**
     * Data files that mention the id, read from the packs as they are on disk (before Scalpel filters them), plus the
     * pack's own config and script folders. This is what matters when deciding whether an id can be removed rather
     * than redacted: anything listed here will look the id up.
     */
    private static Map<String, List<String>> scanFiles(ResourceManager resources, String id) {
        Map<String, List<String>> found = new TreeMap<>();
        String bare = id.startsWith("minecraft:") ? "\"" + id.substring("minecraft:".length()) : null;
        Map<ResourceLocation, Resource> files;
        try {
            files = resources.listResources("", path -> path.getPath().endsWith(".json"));
        } catch (RuntimeException e) {
            files = Map.of();
        }
        if (files.isEmpty()) {
            files = new java.util.HashMap<>();
            for (String folder : FIND_FOLDERS) {
                files.putAll(resources.listResources(folder, path -> path.getPath().endsWith(".json")));
            }
        }
        for (Map.Entry<ResourceLocation, Resource> file : files.entrySet()) {
            String text;
            try (Reader reader = file.getValue().openAsReader()) {
                text = readAll(reader);
            } catch (IOException e) {
                continue;
            }
            if (!text.contains(id) && (bare == null || !text.contains(bare))) {
                continue;
            }
            JsonElement json;
            try {
                json = JsonParser.parseString(text);
            } catch (RuntimeException e) {
                continue;
            }
            if (JsonScrub.references(json, id::equals)) {
                found.computeIfAbsent("data: " + group(file.getKey().getPath()), k -> new ArrayList<>()).add(file.getKey().toString());
            }
        }
        scanDisk(id, found);
        return found;
    }

    /** {@code worldgen/configured_feature/x.json} groups as {@code worldgen/configured_feature}, {@code recipes/x.json} as {@code recipes}. */
    private static String group(String path) {
        int first = path.indexOf('/');
        if (first < 0) {
            return path;
        }
        int second = path.indexOf('/', first + 1);
        return second < 0 ? path.substring(0, first) : path.substring(0, second);
    }

    private static final Set<String> TEXT_EXTENSIONS = Set.of("json", "json5", "toml", "cfg", "txt", "js", "zs", "snbt", "properties", "yml", "yaml", "mcfunction");
    private static final List<String> DISK_FOLDERS = List.of("config", "defaultconfigs", "kubejs", "scripts");

    /** Config and script files in the game folder that mention the id, with line numbers. Scalpel's own folder is skipped. */
    private static void scanDisk(String id, Map<String, List<String>> found) {
        Path game = net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get();
        Path own = Scalpel.core().rulesFolder();
        for (String folder : DISK_FOLDERS) {
            Path root = game.resolve(folder);
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (java.util.stream.Stream<Path> walk = Files.walk(root)) {
                walk.filter(Files::isRegularFile)
                        .filter(p -> !p.startsWith(own))
                        .filter(p -> TEXT_EXTENSIONS.contains(extension(p)))
                        .forEach(p -> scanTextFile(game, p, id, found));
            } catch (IOException | java.io.UncheckedIOException e) {
                // A folder that cannot be walked is skipped.
            }
        }
    }

    private static void scanTextFile(Path game, Path file, String id, Map<String, List<String>> found) {
        try {
            if (Files.size(file) > 8_000_000) {
                return;
            }
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            List<Integer> hits = new ArrayList<>();
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).contains(id)) {
                    hits.add(i + 1);
                }
            }
            if (!hits.isEmpty()) {
                String rel = game.relativize(file).toString().replace(java.io.File.separatorChar, '/');
                String top = rel.substring(0, rel.indexOf('/') < 0 ? rel.length() : rel.indexOf('/'));
                found.computeIfAbsent(top + " files", k -> new ArrayList<>())
                        .add(rel + " (line" + (hits.size() == 1 ? " " : "s ") + joinFirst(hits, 5) + ")");
            }
        } catch (IOException | RuntimeException e) {
            // Unreadable or not UTF-8: skipped.
        }
    }

    private static String joinFirst(List<Integer> numbers, int max) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < numbers.size() && i < max; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(numbers.get(i));
        }
        if (numbers.size() > max) {
            sb.append(", ...");
        }
        return sb.toString();
    }

    private static String extension(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String readAll(Reader reader) throws IOException {
        StringBuilder sb = new StringBuilder();
        char[] buffer = new char[8192];
        int n;
        while ((n = reader.read(buffer)) > 0) {
            sb.append(buffer, 0, n);
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ reload, report

    private static int reload(CommandSourceStack source) {
        ScalpelCore core = Scalpel.core();
        ScalpelCore.ReloadResult result;
        try {
            result = core.reloadRules();
        } catch (IOException e) {
            source.sendFailure(Component.literal("Could not read the rules folder: " + e.getMessage()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Read " + result.rules() + " rules" + (result.errors() > 0 ? " (" + result.errors() + " lines have errors, see the report)" : "")
                + ". Reloading data so recipe, loot, advancement and tag rules take effect."), true);
        if (result.registryRulesChanged()) {
            source.sendSuccess(() -> Component.literal("Item, block and entity rules changed. Those only apply after a restart, and every player needs the same rules files.")
                    .withStyle(ChatFormatting.YELLOW), true);
        }
        MinecraftServer server = source.getServer();
        ReloadCommand.reloadPacks(server.getPackRepository().getSelectedIds(), source);
        return result.rules();
    }

    private static int report(CommandSourceStack source) {
        ScalpelCore core = Scalpel.core();
        core.writeReport();
        source.sendSuccess(() -> Component.literal("Report written to " + core.reportFile()), false);
        return 1;
    }

    // ------------------------------------------------------------------ output

    private static void sendFirst(CommandSourceStack source, List<String> lines) {
        int shown = 0;
        for (String line : lines) {
            if (shown++ >= CHAT_LINES) {
                int rest = lines.size() - CHAT_LINES;
                source.sendSuccess(() -> Component.literal("  ... " + rest + " more").withStyle(ChatFormatting.GRAY), false);
                return;
            }
            source.sendSuccess(() -> Component.literal(line), false);
        }
    }

    private static Path writeResult(String name, String title, List<String> lines) {
        Path file = Scalpel.core().logsFolder().resolve("scalpel").resolve(name.toLowerCase(Locale.ROOT));
        try {
            Files.createDirectories(file.getParent());
            List<String> out = new ArrayList<>();
            out.add(title);
            out.add("");
            out.addAll(lines);
            Files.write(file, out, StandardCharsets.UTF_8);
        } catch (IOException e) {
            Scalpel.core().log().warn("Could not write " + file + ": " + e.getMessage());
        }
        return file;
    }
}
