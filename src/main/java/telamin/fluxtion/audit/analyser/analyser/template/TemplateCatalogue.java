package telamin.fluxtion.audit.analyser.analyser.template;

import telamin.fluxtion.audit.analyser.analyser.llm.Json;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** The versioned playground starter catalogue, reduced to facts the analyser actually renders. */
public record TemplateCatalogue(int version, List<Entry> templates) {

    public static final int SUPPORTED_VERSION = 1;

    public TemplateCatalogue {
        templates = List.copyOf(templates == null ? List.of() : templates);
    }

    public record Entry(String name, String description, String file, String type, String mode,
                        String keyNeed, String regenerationKeyNeed, List<String> agentBootstrap, List<String> tags) {
        public Entry {
            name = required(name, "name");
            description = required(description, "description");
            file = required(file, "file");
            type = text(type);
            mode = text(mode);
            keyNeed = text(keyNeed);
            regenerationKeyNeed = text(regenerationKeyNeed);
            // Null means absent; an empty list is an explicit declaration of no bootstrap files.
            agentBootstrap = agentBootstrap == null ? null : List.copyOf(agentBootstrap);
            if (agentBootstrap != null) for (String path : agentBootstrap) {
                if (path == null || path.isBlank() || path.startsWith("/") || path.contains("\\")
                        || path.contains(":") || java.util.Arrays.asList(path.split("/")).contains(".."))
                    throw new IllegalArgumentException("unsafe agent bootstrap path: " + path);
            }
            tags = List.copyOf(tags == null ? List.of() : tags);
            if (file.contains("/") || file.contains("\\") || !file.endsWith(".starter.json")) {
                throw new IllegalArgumentException("unsafe template catalogue file: " + file);
            }
        }

        public Entry(String name, String description, String file, String type, String mode,
                     String keyNeed, List<String> tags) {
            this(name, description, file, type, mode, keyNeed, "", null, tags);
        }

        public boolean recommended() { return tagged("onboarding"); }
        public String displayName() { return name + (recommended() ? " — Recommended starting point" : ""); }

        /** Catalogue testimony, independent of template type, mode, and recommendation. */
        public String disclosure() {
            String bootstrap = agentBootstrap == null ? "not declared"
                    : agentBootstrap.isEmpty() ? "explicitly none" : String.join(", ", agentBootstrap);
            return description + "\n\n" + keyDisclosure("Build key", keyNeed)
                    + "\n" + keyDisclosure("Regeneration key", regenerationKeyNeed)
                    + "\nAgent entry files: " + bootstrap
                    + "\n\nDeclarations describe the default download; verify the files in the generated project."
                    + (recommended() ? " A recommendation alone does not promise a walkthrough or keyless generation." : "");
        }
        private static String keyDisclosure(String label, String value) {
            return label + ": " + switch (value.toLowerCase(java.util.Locale.ROOT)) {
                case "" -> "not declared";
                case "none" -> "none required (catalogue declaration)";
                case "build" -> "required at build (catalogue declaration)";
                case "run" -> "required at runtime (catalogue declaration)";
                default -> "unrecognised catalogue value '" + value + "'";
            };
        }

        public boolean tagged(String tag) {
            return tags.stream().anyMatch(t -> t.equalsIgnoreCase(tag));
        }

    }

    /** What the picker should show, including whether recommendations were declared. */
    public record Selection(List<Entry> entries, String note) {
        public Selection {
            entries = List.copyOf(entries == null ? List.of() : entries);
            note = text(note);
        }
    }

    @SuppressWarnings("unchecked")
    public static TemplateCatalogue parse(String json, String analyserVersion) {
        Object parsed;
        try {
            parsed = Json.parse(json);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("invalid template catalogue JSON: " + e.getMessage(), e);
        }
        if (!(parsed instanceof Map<?, ?> root)) {
            throw new IllegalArgumentException("template catalogue must be a JSON object");
        }
        Object rawVersion = root.get("catalogue");
        if (!(rawVersion instanceof Number number) || number.doubleValue() != Math.rint(number.doubleValue())) {
            throw unsupported(rawVersion, analyserVersion);
        }
        int version = number.intValue();
        if (version != SUPPORTED_VERSION) throw unsupported(version, analyserVersion);
        Object rawTemplates = root.get("templates");
        if (!(rawTemplates instanceof List<?> list)) {
            throw new IllegalArgumentException("template catalogue has no templates array");
        }
        List<Entry> entries = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                throw new IllegalArgumentException("template catalogue entry must be an object");
            }
            List<String> tags = new ArrayList<>();
            Object rawTags = map.get("tags");
            if (rawTags instanceof List<?> values) {
                for (Object value : values) {
                    if (value instanceof String s && !s.isBlank()) tags.add(s.strip());
                }
            } else if (rawTags != null) {
                throw new IllegalArgumentException("template tags must be an array");
            }
            entries.add(new Entry(asString(map, "name"), asString(map, "description"),
                    asString(map, "file"), optionalString(map, "type"), optionalString(map, "mode"),
                    optionalString(map, "keyNeed"), optionalString(map, "regenerationKeyNeed"), bootstrapPaths(map), tags));
        }
        return new TemplateCatalogue(version, entries);
    }

    /** All entries remain reachable; the catalogue owns recommendation labels, not visibility. */
    public Selection forPicker() {
        return new Selection(templates, templates.stream().anyMatch(Entry::recommended)
                ? "All templates — Recommended starting points are marked."
                : "All templates — the catalogue has not declared recommended starting points.");
    }

    private static List<String> bootstrapPaths(Map<?,?> map) {
        if (!map.containsKey("agentBootstrap")) return null;
        if (!(map.get("agentBootstrap") instanceof List<?> values))
            throw new IllegalArgumentException("template agentBootstrap must be an array");
        List<String> paths = new ArrayList<>();
        for (Object value : values) {
            if (!(value instanceof String path)) throw new IllegalArgumentException("agentBootstrap entries must be paths");
            paths.add(path.strip());
        }
        return paths;
    }

    private static IllegalArgumentException unsupported(Object value, String analyserVersion) {
        String found = value == null ? "missing" : String.valueOf(value);
        return new IllegalArgumentException("template catalogue version " + found
                + " is not supported by analyser " + text(analyserVersion)
                + " (supports catalogue " + SUPPORTED_VERSION + ")");
    }

    private static String asString(Map<?, ?> map, String key) {
        Object value = map.get(key);
        return value instanceof String s ? s : null;
    }

    private static String optionalString(Map<?, ?> map, String key) {
        Object value = map.get(key);
        return value instanceof String s ? s : "";
    }

    private static String required(String value, String field) {
        String out = text(value);
        if (out.isEmpty()) throw new IllegalArgumentException("template catalogue entry has no " + field);
        return out;
    }

    private static String text(String value) {
        return value == null ? "" : value.strip();
    }
}
