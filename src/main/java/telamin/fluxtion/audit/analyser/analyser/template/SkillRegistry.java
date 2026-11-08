package telamin.fluxtion.audit.analyser.analyser.template;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Skill-registry contract {@code m19-skills/3} (docs/skills/README.md, "Contract m19-skills/3"): the version-selection rule, as a
 * pure function over one skill's parsed {@code versions} list.
 *
 * <p>For a project's effective pins, {@link #select} returns the HIGHEST version that is not yanked, carries evidence,
 * is well-formed and whose {@code compatibility} contains every pin — or nothing. It never returns an incompatible
 * version: anything it cannot read (a malformed version string, range or compatibility block) is treated as
 * incompatible, not guessed at.
 *
 * <p>No consumer reads this yet (spec step 1); {@code CanonicalSkillsTest} uses the same grammar to check the index.
 */
public final class SkillRegistry {

    public static final String CONTRACT = "m19-skills/3";
    public static final Set<String> AUTHORING = Set.of("java", "spring", "any");
    public static final Set<String> HOST = Set.of("mongoose", "embedded", "any");
    public static final Set<String> COMPATIBILITY_KEYS = Set.of("analyser", "authoring", "host", "mongoose", "mongoosePlugins");

    private SkillRegistry() {
    }

    /**
     * A project's effective pins. {@code authoring} is {@code java} or {@code spring}; {@code host} is {@code mongoose} or
     * {@code embedded}. {@code mongoose} and {@code mongoosePlugins} may be null when the project pins none (an embedded
     * host): then only a version whose range is {@code *} matches that axis.
     */
    public record ProjectPins(String analyser, String authoring, String host, String mongoose, String mongoosePlugins) {
        public ProjectPins {
            Objects.requireNonNull(analyser, "analyser");
            Objects.requireNonNull(authoring, "authoring");
            Objects.requireNonNull(host, "host");
        }
    }

    /** The highest selectable version compatible with {@code pins}, or empty: never an incompatible one. */
    public static Optional<Map<String, Object>> select(List<Map<String, Object>> versions, ProjectPins pins) {
        Map<String, Object> best = null;
        Semver bestVersion = null;
        for (Map<String, Object> candidate : versions) {
            if (notSelectableReason(candidate) != null || !compatible(candidate, pins)) continue;
            Semver version = Semver.parse((String) candidate.get("version"));
            if (bestVersion == null || version.compareTo(bestVersion) > 0) {
                best = candidate;
                bestVersion = version;
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * Why this version can never be selected, whatever the pins — or null when it can be. A version without evidence is
     * not selectable (spec §3.4 step 4); neither is a yanked one or one whose fields cannot be read.
     */
    public static String notSelectableReason(Map<String, Object> version) {
        if (!(version.get("version") instanceof String v) || Semver.tryParse(v) == null)
            return "version is not valid semver: " + version.get("version");
        Object yanked = version.get("yanked");
        if (yanked != null && !(yanked instanceof Boolean)) return v + ": yanked must be true or false";
        if (Boolean.TRUE.equals(yanked)) return v + ": yanked";
        if (!(version.get("evidence") instanceof Map<?, ?> evidence)) return v + ": no evidence, so not selectable";
        if (!(evidence.get("label") instanceof String label) || label.isBlank()
                || !(evidence.get("date") instanceof String date) || date.isBlank())
            return v + ": evidence has no label and date, so not selectable";
        String compatibility = compatibilityProblem(version.get("compatibility"));
        if (compatibility != null) return v + ": " + compatibility;
        return null;
    }

    /** What is wrong with a {@code compatibility} block, or null when it is well-formed. */
    public static String compatibilityProblem(Object block) {
        if (!(block instanceof Map<?, ?> c)) return "compatibility is missing";
        if (!c.keySet().equals(COMPATIBILITY_KEYS))
            return "compatibility must have exactly " + COMPATIBILITY_KEYS + ", has " + c.keySet();
        if (!(c.get("analyser") instanceof String a) || Semver.tryParse(a) == null)
            return "compatibility.analyser is not a semver minimum: " + c.get("analyser");
        if (!(c.get("authoring") instanceof String au) || !AUTHORING.contains(au))
            return "compatibility.authoring must be one of " + AUTHORING + ": " + c.get("authoring");
        if (!(c.get("host") instanceof String h) || !HOST.contains(h))
            return "compatibility.host must be one of " + HOST + ": " + c.get("host");
        for (String axis : List.of("mongoose", "mongoosePlugins"))
            if (!(c.get(axis) instanceof String r) || Range.tryParse(r) == null)
                return "compatibility." + axis + " is not a version range: " + c.get(axis);
        return null;
    }

    /** True when every pin lies inside the version's compatibility. A block it cannot read is not compatible. */
    public static boolean compatible(Map<String, Object> version, ProjectPins pins) {
        Object block = version.get("compatibility");
        if (compatibilityProblem(block) != null) return false;
        Map<?, ?> c = (Map<?, ?>) block;
        Semver analyser = Semver.tryParse(pins.analyser());
        if (analyser == null || analyser.compareTo(Semver.parse((String) c.get("analyser"))) < 0) return false;
        if (!axisMatches((String) c.get("authoring"), pins.authoring())) return false;
        if (!axisMatches((String) c.get("host"), pins.host())) return false;
        return Range.parse((String) c.get("mongoose")).contains(pins.mongoose())
                && Range.parse((String) c.get("mongoosePlugins")).contains(pins.mongoosePlugins());
    }

    private static boolean axisMatches(String declared, String pinned) {
        return "any".equals(declared) || declared.equals(pinned);
    }

    /** Semantic Versioning 2.0.0: MAJOR.MINOR.PATCH[-prerelease][+build], compared by its precedence rules. */
    public record Semver(long major, long minor, long patch, List<String> prerelease) implements Comparable<Semver> {
        private static final Pattern SHAPE = Pattern.compile(
                "(0|[1-9]\\d{0,17})\\.(0|[1-9]\\d{0,17})\\.(0|[1-9]\\d{0,17})"
                        + "(?:-((?:0|[1-9]\\d*|\\d*[A-Za-z-][0-9A-Za-z-]*)(?:\\.(?:0|[1-9]\\d*|\\d*[A-Za-z-][0-9A-Za-z-]*))*))?"
                        + "(?:\\+([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?");

        public static Semver tryParse(String text) {
            if (text == null) return null;
            Matcher m = SHAPE.matcher(text);
            if (!m.matches()) return null;
            return new Semver(Long.parseLong(m.group(1)), Long.parseLong(m.group(2)), Long.parseLong(m.group(3)),
                    m.group(4) == null ? List.of() : List.of(m.group(4).split("\\.")));
        }

        public static Semver parse(String text) {
            Semver v = tryParse(text);
            if (v == null) throw new IllegalArgumentException("not semver: " + text);
            return v;
        }

        @Override
        public int compareTo(Semver o) {
            int c = Long.compare(major, o.major);
            if (c == 0) c = Long.compare(minor, o.minor);
            if (c == 0) c = Long.compare(patch, o.patch);
            if (c != 0) return c;
            if (prerelease.isEmpty() || o.prerelease.isEmpty())   // a release outranks its prereleases
                return Boolean.compare(prerelease.isEmpty(), o.prerelease.isEmpty());
            for (int i = 0; i < Math.min(prerelease.size(), o.prerelease.size()); i++) {
                String a = prerelease.get(i), b = o.prerelease.get(i);
                boolean an = a.chars().allMatch(Character::isDigit), bn = b.chars().allMatch(Character::isDigit);
                c = an && bn ? new java.math.BigInteger(a).compareTo(new java.math.BigInteger(b))
                        : an != bn ? (an ? -1 : 1) : a.compareTo(b);
                if (c != 0) return c;
            }
            return Integer.compare(prerelease.size(), o.prerelease.size());
        }
    }

    /**
     * A version range: {@code *} (any version, or none pinned), or one or more space-separated comparators that must all
     * hold, each {@code >=}, {@code >}, {@code <=}, {@code <} or {@code =} followed by a semver ({@code >=1.1.3 <1.2.0}).
     * There is deliberately no {@code ||}, caret or tilde: a range a reader has to work out is a range a reader gets
     * wrong.
     */
    public record Range(List<String> operators, List<Semver> bounds) {
        private static final Pattern COMPARATOR = Pattern.compile("(>=|<=|>|<|=)(\\S+)");

        public static Range tryParse(String text) {
            if (text == null) return null;
            if (text.equals("*")) return new Range(List.of(), List.of());
            if (text.isBlank() || !text.equals(text.strip())) return null;
            List<String> ops = new ArrayList<>();
            List<Semver> bounds = new ArrayList<>();
            for (String part : text.split(" ")) {
                Matcher m = COMPARATOR.matcher(part);
                if (!m.matches()) return null;
                Semver bound = Semver.tryParse(m.group(2));
                if (bound == null) return null;
                ops.add(m.group(1));
                bounds.add(bound);
            }
            return new Range(List.copyOf(ops), List.copyOf(bounds));
        }

        public static Range parse(String text) {
            Range r = tryParse(text);
            if (r == null) throw new IllegalArgumentException("not a version range: " + text);
            return r;
        }

        public boolean any() {
            return operators.isEmpty();
        }

        /** {@code *} contains everything, an unpinned (null) version included; any other range needs a semver inside it. */
        public boolean contains(String pinned) {
            if (any()) return true;
            Semver v = Semver.tryParse(pinned);
            if (v == null) return false;
            for (int i = 0; i < operators.size(); i++) {
                int c = v.compareTo(bounds.get(i));
                boolean ok = switch (operators.get(i)) {
                    case ">=" -> c >= 0;
                    case ">" -> c > 0;
                    case "<=" -> c <= 0;
                    case "<" -> c < 0;
                    default -> c == 0;
                };
                if (!ok) return false;
            }
            return true;
        }
    }
}
