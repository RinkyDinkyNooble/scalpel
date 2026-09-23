package com.rinkynooble.scalpel.core.rules;

import com.rinkynooble.scalpel.core.ContentType;
import com.rinkynooble.scalpel.core.RecipeMode;
import com.rinkynooble.scalpel.core.Verb;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Parses rules files. One rule per line: {@code <verb> <type> <pattern> [options]}.
 * A token starting with {@code #} starts a comment, except a tag id written as {@code #namespace:path} in the pattern slot.
 */
public final class RuleParser {
    private RuleParser() {
    }

    public record ParseError(String file, int line, String text, String message) {
        public String location() {
            return file + ":" + line;
        }

        @Override
        public String toString() {
            return location() + ": " + message + " -> " + text.strip();
        }
    }

    public record Result(List<Rule> rules, List<ParseError> errors) {
    }

    public static Result parse(String file, List<String> lines) {
        List<Rule> rules = new ArrayList<>();
        List<ParseError> errors = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String text = lines.get(i);
            int lineNumber = i + 1;
            try {
                Rule rule = parseLine(file, lineNumber, text);
                if (rule != null) {
                    rules.add(rule);
                }
            } catch (IllegalArgumentException e) {
                errors.add(new ParseError(file, lineNumber, text, e.getMessage()));
            }
        }
        return new Result(rules, errors);
    }

    /** Returns null for blank and comment lines. Throws {@link IllegalArgumentException} for invalid ones. */
    static Rule parseLine(String file, int lineNumber, String text) {
        List<String> tokens = tokenize(text);
        if (tokens.isEmpty()) {
            return null;
        }
        if (tokens.size() < 3) {
            throw new IllegalArgumentException("expected '<verb> <type> <pattern>'");
        }

        Verb verb = Verb.parse(tokens.get(0));
        if (verb == null) {
            throw new IllegalArgumentException("unknown verb '" + tokens.get(0) + "' (use keep, redact or remove)");
        }

        String typeKeyword = tokens.get(1);
        Set<ContentType> types = ContentType.parse(typeKeyword);
        if (types == null) {
            throw new IllegalArgumentException("unknown type '" + typeKeyword + "' (use item, block, entity, any, recipe, loot, advancement or tag)");
        }
        boolean registry = types.iterator().next().isRegistry();
        if (verb == Verb.REDACT && !registry) {
            throw new IllegalArgumentException("only items, blocks and entities can be redacted; use 'remove " + typeKeyword + "'");
        }

        String patternToken = tokens.get(2);
        if (patternToken.startsWith("#")) {
            if (!types.contains(ContentType.TAG)) {
                throw new IllegalArgumentException("'#' is only used for tags");
            }
            patternToken = patternToken.substring(1);
        }
        IdPattern pattern = IdPattern.parse(patternToken);

        RecipeMode recipeMode = null;
        for (int t = 3; t < tokens.size(); t++) {
            String option = tokens.get(t);
            RecipeMode mode = RecipeMode.parse(option);
            if (mode == null) {
                throw new IllegalArgumentException("unknown option '" + option + "' (options: rewrite, drop)");
            }
            if (verb == Verb.KEEP || !(types.contains(ContentType.ITEM) || types.contains(ContentType.BLOCK))) {
                throw new IllegalArgumentException("'" + option + "' only applies to redact/remove rules for items, blocks or any");
            }
            if (recipeMode != null && recipeMode != mode) {
                throw new IllegalArgumentException("choose either rewrite or drop, not both");
            }
            recipeMode = mode;
        }

        return new Rule(verb, typeKeyword, types, pattern, recipeMode, file, lineNumber);
    }

    /** Splits on whitespace and stops at a comment. A {@code #} in the pattern slot followed by an id is a tag, not a comment. */
    static List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        for (String raw : text.strip().split("\\s+")) {
            if (raw.isEmpty()) {
                continue;
            }
            if (raw.startsWith("#")) {
                boolean tagInPatternSlot = tokens.size() == 2 && raw.length() > 1 && raw.indexOf(':') > 1;
                if (!tagInPatternSlot) {
                    break;
                }
            }
            tokens.add(raw);
        }
        return tokens;
    }
}
