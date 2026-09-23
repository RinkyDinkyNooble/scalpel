package com.rinkynooble.scalpel.core.rules;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** Reads every {@code *.rules} file in the rules folder, alphabetically. */
public final class RulesLoader {
    public static final String EXTENSION = ".rules";

    private RulesLoader() {
    }

    public record RulesFile(String name, List<Rule> rules) {
    }

    public record Loaded(List<RulesFile> files, List<RuleParser.ParseError> errors) {
        public RuleSet toRuleSet() {
            List<Rule> all = new ArrayList<>();
            for (RulesFile file : files) {
                all.addAll(file.rules());
            }
            return new RuleSet(all);
        }
    }

    /**
     * Loads the folder. If the folder does not exist it is created along with a commented-out example file,
     * so a first launch never cuts anything.
     */
    public static Loaded load(Path folder) throws IOException {
        if (!Files.isDirectory(folder)) {
            Files.createDirectories(folder);
            writeExample(folder.resolve("example" + EXTENSION));
        }
        List<Path> paths;
        try (Stream<Path> stream = Files.list(folder)) {
            paths = stream
                    .filter(p -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(EXTENSION))
                    .sorted()
                    .toList();
        }
        List<RulesFile> files = new ArrayList<>();
        List<RuleParser.ParseError> errors = new ArrayList<>();
        for (Path path : paths) {
            String name = path.getFileName().toString();
            List<String> lines = readLines(path);
            RuleParser.Result result = RuleParser.parse(name, lines);
            files.add(new RulesFile(name, result.rules()));
            errors.addAll(result.errors());
        }
        return new Loaded(files, errors);
    }

    private static List<String> readLines(Path path) throws IOException {
        String text = Files.readString(path, StandardCharsets.UTF_8);
        if (text.startsWith("﻿")) {
            text = text.substring(1);
        }
        return text.lines().toList();
    }

    private static void writeExample(Path file) throws IOException {
        try (InputStream in = RulesLoader.class.getResourceAsStream("/scalpel/example.rules")) {
            if (in != null) {
                Files.write(file, in.readAllBytes());
            }
        }
    }
}
