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
                        String keyNeed, List<String> tags) {
        public Entry {
            name = required(name, "name");
            description = required(description, "description");
            file = required(file, "file");
            type = text(type);
            mode = text(mode);
            keyNeed = text(keyNeed);
            tags = List.copyOf(tags == null ? List.of() : tags);
            if (file.contains("/") || file.contains("\\") || !file.endsWith(".starter.json")) {
                throw new IllegalArgumentException("unsafe template catalogue file: " + file);
            }
        }

        public boolean tagged(String tag) {
            return tags.stream().anyMatch(t -> t.equalsIgnoreCase(tag));
        }

        public boolean mongooseHosted() {
            return type.equalsIgnoreCase("mongoose") || type.equalsIgnoreCase("hosted");
        }
    }

    /** What the picker should show, including the explicit fallback note required by D-1. */
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
                    optionalString(map, "keyNeed"), tags));
        }
        return new TemplateCatalogue(version, entries);
    }

    /**
     * Prefer the catalogue-owned onboarding tag. Before UP-PG-03 lands, use the specified Mongoose
     * fallback; if even that is empty, show everything with a note instead of an empty dialog.
     */
    public Selection onboarding() {
        List<Entry> tagged = templates.stream().filter(e -> e.tagged("onboarding")).toList();
        if (!tagged.isEmpty()) return new Selection(tagged, "");
        List<Entry> mongoose = templates.stream().filter(Entry::mongooseHosted).toList();
        if (!mongoose.isEmpty()) {
            return new Selection(mongoose,
                    "The catalogue has not tagged its onboarding set yet; showing Mongoose templates.");
        }
        return new Selection(templates,
                "The catalogue has not tagged an onboarding set; showing every template.");
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
