package com.rinkynooble.scalpel.core.rules;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * One id pattern from a rule: an exact id, a glob ({@code *} and {@code ?}), or a regex written as {@code /.../}.
 * Globs are matched per half, so {@code *} never crosses the {@code :} between namespace and path.
 */
public abstract sealed class IdPattern permits IdPattern.Exact, IdPattern.Glob, IdPattern.Regex {
    private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern PATH = Pattern.compile("[a-z0-9_./-]+");
    private static final Pattern GLOB_NAMESPACE = Pattern.compile("[a-z0-9_.*?-]+");
    private static final Pattern GLOB_PATH = Pattern.compile("[a-z0-9_./*?-]+");
    private static final Pattern REGEX_NAMESPACE_HINT = Pattern.compile("[a-z0-9_-]+");

    private final String source;

    IdPattern(String source) {
        this.source = source;
    }

    /** The pattern as written in the rule file. */
    public String source() {
        return source;
    }

    public abstract boolean matches(String id);

    /** The namespace every match must have, or null when the pattern can match several namespaces. */
    public abstract String namespaceHint();

    /** Parses a pattern token. Throws {@link IllegalArgumentException} with a readable message when it is invalid. */
    public static IdPattern parse(String token) {
        if (token.length() >= 2 && token.startsWith("/") && token.endsWith("/")) {
            String body = token.substring(1, token.length() - 1);
            try {
                return new Regex(token, Pattern.compile(body));
            } catch (PatternSyntaxException e) {
                throw new IllegalArgumentException("invalid regex: " + e.getDescription());
            }
        }
        if (token.startsWith("/")) {
            throw new IllegalArgumentException("a regex must start and end with '/'");
        }
        if (!token.equals(token.toLowerCase(java.util.Locale.ROOT))) {
            throw new IllegalArgumentException("ids are lowercase");
        }
        int colon = token.indexOf(':');
        if (colon < 0) {
            throw new IllegalArgumentException("missing namespace, write it as 'namespace:path' (for vanilla, 'minecraft:" + token + "')");
        }
        if (token.indexOf(':', colon + 1) >= 0) {
            throw new IllegalArgumentException("an id has exactly one ':'");
        }
        String namespace = token.substring(0, colon);
        String path = token.substring(colon + 1);
        boolean wildcard = token.indexOf('*') >= 0 || token.indexOf('?') >= 0;
        if (!wildcard) {
            if (!NAMESPACE.matcher(namespace).matches() || !PATH.matcher(path).matches()) {
                throw new IllegalArgumentException("not a valid id");
            }
            return new Exact(token);
        }
        if (!GLOB_NAMESPACE.matcher(namespace).matches() || !GLOB_PATH.matcher(path).matches()) {
            throw new IllegalArgumentException("not a valid pattern (allowed: a-z 0-9 _ . - / and the wildcards * ?)");
        }
        return new Glob(token, namespace, path);
    }

    @Override
    public String toString() {
        return source;
    }

    public static final class Exact extends IdPattern {
        private final String namespace;

        Exact(String id) {
            super(id);
            this.namespace = id.substring(0, id.indexOf(':'));
        }

        public String id() {
            return source();
        }

        @Override
        public boolean matches(String id) {
            return source().equals(id);
        }

        @Override
        public String namespaceHint() {
            return namespace;
        }
    }

    public static final class Glob extends IdPattern {
        private final String namespaceGlob;
        private final String pathGlob;
        private final String namespaceHint;

        Glob(String source, String namespaceGlob, String pathGlob) {
            super(source);
            this.namespaceGlob = namespaceGlob;
            this.pathGlob = pathGlob;
            boolean literalNamespace = namespaceGlob.indexOf('*') < 0 && namespaceGlob.indexOf('?') < 0;
            this.namespaceHint = literalNamespace ? namespaceGlob : null;
        }

        @Override
        public boolean matches(String id) {
            int colon = id.indexOf(':');
            if (colon < 0) {
                return false;
            }
            return wildcardMatch(namespaceGlob, id, 0, colon) && wildcardMatch(pathGlob, id, colon + 1, id.length());
        }

        @Override
        public String namespaceHint() {
            return namespaceHint;
        }

        /** Iterative wildcard match of {@code glob} against {@code text[from, to)}. Linear in practice. */
        static boolean wildcardMatch(String glob, String text, int from, int to) {
            int g = 0;
            int t = from;
            int starG = -1;
            int starT = -1;
            while (t < to) {
                if (g < glob.length() && (glob.charAt(g) == '?' || glob.charAt(g) == text.charAt(t))) {
                    g++;
                    t++;
                } else if (g < glob.length() && glob.charAt(g) == '*') {
                    starG = g++;
                    starT = t;
                } else if (starG >= 0) {
                    g = starG + 1;
                    t = ++starT;
                } else {
                    return false;
                }
            }
            while (g < glob.length() && glob.charAt(g) == '*') {
                g++;
            }
            return g == glob.length();
        }
    }

    public static final class Regex extends IdPattern {
        private final Pattern pattern;
        private final String namespaceHint;

        Regex(String source, Pattern pattern) {
            super(source);
            this.pattern = pattern;
            this.namespaceHint = hintFor(pattern.pattern());
        }

        @Override
        public boolean matches(String id) {
            return pattern.matcher(id).matches();
        }

        @Override
        public String namespaceHint() {
            return namespaceHint;
        }

        /** A regex such as {@code ^examplemod:.*_eggs$} only ever matches namespace {@code examplemod}. */
        private static String hintFor(String body) {
            String start = body.startsWith("^") ? body.substring(1) : body;
            int colon = start.indexOf(':');
            if (colon <= 0) {
                return null;
            }
            String candidate = start.substring(0, colon);
            return REGEX_NAMESPACE_HINT.matcher(candidate).matches() ? candidate : null;
        }
    }
}
