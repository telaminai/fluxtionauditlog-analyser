package telamin.fluxtion.audit.analyser.analyser.template;

import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.template.SkillRegistry.ProjectPins;
import telamin.fluxtion.audit.analyser.analyser.template.SkillRegistry.Range;
import telamin.fluxtion.audit.analyser.analyser.template.SkillRegistry.Semver;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The m19-skills/3 selection rule: highest compatible, non-yanked version with evidence, or none. */
class SkillRegistryTest {

    private static final ProjectPins SPRING_MONGOOSE = new ProjectPins("1.41.2", "spring", "mongoose", "1.1.3", "1.1.2");
    private static final ProjectPins JAVA_EMBEDDED = new ProjectPins("1.41.2", "java", "embedded", null, null);

    private static Map<String, Object> version(String v, String analyser, String authoring, String host,
                                               String mongoose, String plugins) {
        Map<String, Object> compat = new HashMap<>(Map.of("analyser", analyser, "authoring", authoring, "host", host,
                "mongoose", mongoose, "mongoosePlugins", plugins));
        Map<String, Object> version = new HashMap<>();
        version.put("version", v);
        version.put("compatibility", compat);
        version.put("evidence", gate(v));
        return version;
    }

    private static Map<String, Object> gate(String v) {
        return Map.of("kind", "gate", "label", "round " + v, "date", "2026-11-08",
                "tests", Map.of("label", "suite", "passed", 12.0, "total", 12.0),
                "benchmark", Map.of("label", "2-run round", "passed", 4.0, "total", 4.0));
    }

    private static Map<String, Object> any(String v) {
        return version(v, "1.12.0", "any", "any", "*", "*");
    }

    private static String selected(List<Map<String, Object>> versions, ProjectPins pins) {
        return SkillRegistry.select(versions, pins).map(v -> (String) v.get("version")).orElse(null);
    }

    @Test
    void theHighestCompatibleVersionWins_bySemverPrecedenceNotListOrder() {
        assertEquals("1.10.0", selected(List.of(any("1.2.0"), any("1.10.0"), any("1.9.9")), SPRING_MONGOOSE));
        assertEquals("2.0.0", selected(List.of(any("2.0.0-rc.1"), any("2.0.0"), any("1.0.0")), SPRING_MONGOOSE));
        assertEquals("2.0.0-rc.11", selected(List.of(any("2.0.0-rc.2"), any("2.0.0-rc.11")), SPRING_MONGOOSE));
    }

    @Test
    void aNewerIncompatibleVersionIsPassedOverForAnOlderCompatibleOne() {
        List<Map<String, Object>> versions = List.of(
                version("1.0.0", "1.12.0", "any", "mongoose", ">=1.1.0 <1.2.0", ">=1.1.0 <1.2.0"),
                version("2.0.0", "1.12.0", "any", "mongoose", ">=1.2.0", ">=1.1.0"),          // newer Mongoose only
                version("3.0.0", "1.50.0", "any", "mongoose", "*", "*"),                       // newer analyser only
                version("4.0.0", "1.12.0", "java", "mongoose", "*", "*"),                      // other authoring
                version("5.0.0", "1.12.0", "any", "embedded", "*", "*"),                       // other host
                version("6.0.0", "1.12.0", "any", "mongoose", ">=1.1.3", ">=1.1.3"));          // plugins axis alone
        assertEquals("1.0.0", selected(versions, SPRING_MONGOOSE));
    }

    @Test
    void mongooseAndPluginsAreSeparateAxes() {
        // same numbers on both axes would be the bug: a range written for one must not be read against the other
        Map<String, Object> v = version("1.0.0", "1.12.0", "any", "any", "=1.1.3", "=1.1.2");
        assertTrue(SkillRegistry.compatible(v, SPRING_MONGOOSE));
        assertFalse(SkillRegistry.compatible(v, new ProjectPins("1.41.2", "spring", "mongoose", "1.1.2", "1.1.3")));
    }

    @Test
    void noCompatibleVersionSelectsNothing() {
        List<Map<String, Object>> versions = List.of(version("1.0.0", "1.12.0", "spring", "any", "*", "*"),
                version("2.0.0", "1.12.0", "any", "mongoose", "*", "*"));
        assertEquals(Optional.empty(), SkillRegistry.select(versions, JAVA_EMBEDDED));
        assertEquals(Optional.empty(), SkillRegistry.select(List.of(), SPRING_MONGOOSE));
    }

    @Test
    void anUnpinnedMongooseMatchesOnlyAnyRange() {
        assertTrue(SkillRegistry.compatible(any("1.0.0"), JAVA_EMBEDDED));
        assertFalse(SkillRegistry.compatible(version("1.0.0", "1.12.0", "any", "any", ">=0.0.0", "*"), JAVA_EMBEDDED));
    }

    @Test
    void yankedAndEvidenceFreeVersionsAreNeverSelected() {
        Map<String, Object> yanked = any("3.0.0");
        yanked.put("yanked", true);
        Map<String, Object> noEvidence = any("2.0.0");
        noEvidence.remove("evidence");
        Map<String, Object> blankEvidence = any("1.5.0");
        Map<String, Object> blank = new HashMap<>(gate("1.5.0"));
        blank.put("label", " ");
        blankEvidence.put("evidence", blank);
        List<Map<String, Object>> versions = List.of(any("1.0.0"), yanked, noEvidence, blankEvidence);
        assertEquals("1.0.0", selected(versions, SPRING_MONGOOSE));
        assertTrue(SkillRegistry.notSelectableReason(noEvidence).contains("no evidence, so not selectable"));
        assertTrue(SkillRegistry.notSelectableReason(yanked).contains("yanked"));
        assertNotNull(SkillRegistry.notSelectableReason(blankEvidence));
        Map<String, Object> notYanked = any("4.0.0");
        notYanked.put("yanked", false);
        assertNull(SkillRegistry.notSelectableReason(notYanked));
        assertEquals("4.0.0", selected(List.of(any("1.0.0"), notYanked), SPRING_MONGOOSE));
        Map<String, Object> oddYank = any("5.0.0");
        oddYank.put("yanked", "true");
        assertEquals("1.0.0", selected(List.of(any("1.0.0"), oddYank), SPRING_MONGOOSE), "a non-boolean yank is not trusted");
    }

    @Test
    @SuppressWarnings("unchecked")
    void anythingUnreadableIsTreatedAsIncompatible_neverGuessed() {
        List<Map<String, Object>> broken = new ArrayList<>();
        broken.add(any("1.0"));                                                       // not semver
        broken.add(any("v2.0.0"));
        broken.add(version("3.0.0", "1.12", "any", "any", "*", "*"));                 // analyser not semver
        broken.add(version("4.0.0", "1.12.0", "kotlin", "any", "*", "*"));            // unknown authoring
        broken.add(version("5.0.0", "1.12.0", "any", "cloud", "*", "*"));             // unknown host
        broken.add(version("6.0.0", "1.12.0", "any", "any", "^1.1.0", "*"));          // unsupported range syntax
        broken.add(version("7.0.0", "1.12.0", "any", "any", "*", ">=1.1.0 || <1.0.0"));
        Map<String, Object> extraKey = any("8.0.0");
        ((Map<String, Object>) extraKey.get("compatibility")).put("fluxtion", "*");
        broken.add(extraKey);
        Map<String, Object> missingKey = any("9.0.0");
        ((Map<String, Object>) missingKey.get("compatibility")).remove("mongoosePlugins");
        broken.add(missingKey);
        for (Map<String, Object> v : broken) {
            assertNotNull(SkillRegistry.notSelectableReason(v), "must be refused: " + v);
            assertEquals(Optional.empty(), SkillRegistry.select(List.of(v), SPRING_MONGOOSE), "selected: " + v);
        }
        assertFalse(SkillRegistry.compatible(any("1.0.0"), new ProjectPins("1.41", "spring", "mongoose", "1.1.3", "1.1.2")),
                "an unreadable project pin matches nothing it would have to be compared against");
    }

    // ---- the guarantee, against an INDEPENDENT oracle ----
    // Ranges are built from (operator, point) pairs over an ordered list of version points, and the oracle decides
    // containment by comparing positions in that list: it shares no code with SkillRegistry (no Semver, no Range, no
    // compatible()), so a bug in the rule's range or axis logic cannot also be in its referee. Version numbers are
    // shuffled against list order, and a share of versions are yanked, evidence-free or carry evidence of no known
    // kind, so "highest", "never yanked" and "never without evidence" are each tested, not assumed.

    private static final List<String> POINTS = List.of("1.0.0", "1.1.0", "1.1.2", "1.1.3", "1.2.0");
    private static final List<String> ANALYSERS = List.of("1.12.0", "1.41.2", "1.42.0");
    private static final List<List<String[]>> RANGES = List.of(
            List.of(),                                                           // *
            List.<String[]>of(new String[]{">=", "1.1.0"}),
            List.<String[]>of(new String[]{"<", "1.1.3"}),
            List.of(new String[]{">=", "1.1.2"}, new String[]{"<", "1.2.0"}),
            List.<String[]>of(new String[]{"=", "1.1.3"}),
            List.<String[]>of(new String[]{">", "1.1.3"}),
            List.of(new String[]{"<=", "1.1.2"}, new String[]{">", "1.0.0"}));

    private static String render(List<String[]> range) {
        return range.isEmpty() ? "*" : String.join(" ", range.stream().map(c -> c[0] + c[1]).toList());
    }

    private static boolean oracleContains(List<String[]> range, String pin) {
        if (range.isEmpty()) return true;
        if (pin == null) return false;
        int at = POINTS.indexOf(pin);
        for (String[] c : range) {
            int d = Integer.compare(at, POINTS.indexOf(c[1]));
            boolean ok = switch (c[0]) {
                case ">=" -> d >= 0;
                case ">" -> d > 0;
                case "<=" -> d <= 0;
                case "<" -> d < 0;
                default -> d == 0;
            };
            if (!ok) return false;
        }
        return true;
    }

    private record Case(Map<String, Object> version, int rank, boolean selectable, String analyserMin, String authoring,
                        String host, List<String[]> mongoose, List<String[]> plugins) {
        boolean oracleCompatible(ProjectPins pins) {
            return ANALYSERS.indexOf(pins.analyser()) >= ANALYSERS.indexOf(analyserMin)
                    && (authoring.equals("any") || authoring.equals(pins.authoring()))
                    && (host.equals("any") || host.equals(pins.host()))
                    && oracleContains(mongoose, pins.mongoose()) && oracleContains(plugins, pins.mongoosePlugins());
        }
    }

    static List<Case> grid(long seed, int sparse) {
        java.util.Random random = new java.util.Random(seed);
        List<Case> cases = new ArrayList<>();
        for (String authoring : List.of("any", "spring", "java"))
            for (String host : List.of("any", "mongoose", "embedded"))
                for (List<String[]> m : RANGES)
                    for (List<String[]> p : RANGES)
                        for (String analyser : ANALYSERS)
                            if (random.nextInt(sparse) == 0)   // sparse grids leave some projects with no version
                                cases.add(new Case(null, 0, true, analyser, authoring, host, m, p));
        List<Integer> ranks = new ArrayList<>(java.util.stream.IntStream.range(0, cases.size()).boxed().toList());
        java.util.Collections.shuffle(ranks, random);
        List<Case> out = new ArrayList<>();
        for (int i = 0; i < cases.size(); i++) {
            Case c = cases.get(i);
            int rank = ranks.get(i);
            Map<String, Object> v = version(rank + ".0.0", c.analyserMin(), c.authoring(), c.host(),
                    render(c.mongoose()), render(c.plugins()));
            boolean selectable = true;
            switch (random.nextInt(8)) {
                case 0 -> { v.put("yanked", true); selectable = false; }
                case 1 -> { v.remove("evidence"); selectable = false; }
                case 2 -> { v.put("evidence", Map.of("label", "trust me", "date", "2026-11-08")); selectable = false; }
                case 3 -> v.put("yanked", false);
                default -> { }
            }
            out.add(new Case(v, rank, selectable, c.analyserMin(), c.authoring(), c.host(), c.mongoose(), c.plugins()));
        }
        java.util.Collections.shuffle(out, random);
        return out;
    }

    static List<ProjectPins> everyPins() {
        List<String> pinned = new ArrayList<>(POINTS);
        pinned.add(null);
        List<ProjectPins> all = new ArrayList<>();
        for (String analyser : List.of("1.12.0", "1.41.2"))
            for (String authoring : List.of("spring", "java"))
                for (String host : List.of("mongoose", "embedded"))
                    for (String m : pinned)
                        for (String p : pinned)
                            all.add(new ProjectPins(analyser, authoring, host, m, p));
        return all;
    }

    /** What select must return for these pins, decided without SkillRegistry: the oracle's highest rank, or null. */
    static String expected(List<Case> cases, ProjectPins pins) {
        return cases.stream().filter(c -> c.selectable() && c.oracleCompatible(pins))
                .max(java.util.Comparator.comparingInt(Case::rank)).map(c -> (String) c.version().get("version")).orElse(null);
    }

    /** The guarantee itself: across a grid of versions and pins, select returns exactly the oracle's choice. */
    @Test
    void selectionNeverReturnsAnIncompatibleVersion() {
        int some = 0, none = 0;
        for (int sparse : List.of(1, 4, 20))
            for (long seed : List.of(1L, 2L, 3L)) {
                List<Case> cases = grid(seed, sparse);
                List<Map<String, Object>> versions = cases.stream().map(Case::version).toList();
                for (ProjectPins pins : everyPins()) {
                    String want = expected(cases, pins);
                    assertEquals(want, selected(versions, pins), "sparse " + sparse + " seed " + seed + " " + pins);
                    if (want == null) none++; else some++;
                }
                assertTrue(cases.stream().anyMatch(c -> !c.selectable()) && cases.stream().anyMatch(Case::selectable));
            }
        assertTrue(some > 100 && none > 100, "the grids must reach both outcomes: " + some + " selected, " + none + " none");
    }

    /**
     * Negative control for the oracle test: a rule that ignores yanked, picks by list order, or drops one axis must
     * disagree with the oracle somewhere on the grid. If any of these "rules" agreed everywhere, the test above could
     * not tell a broken select from a working one.
     */
    @Test
    void theOracleGridCatchesABrokenRule() {
        List<Case> cases = grid(1L, 1);
        List<Map<String, Object>> versions = cases.stream().map(Case::version).toList();
        java.util.function.BiFunction<java.util.function.Predicate<Case>, java.util.function.BinaryOperator<Case>, Boolean>
                caught = (keep, pick) -> everyPins().stream().anyMatch(pins -> !java.util.Objects.equals(
                expected(cases, pins),
                cases.stream().filter(c -> keep.test(c) && c.oracleCompatible(pins)).reduce(pick)
                        .map(c -> (String) c.version().get("version")).orElse(null)));
        java.util.function.BinaryOperator<Case> highest = (a, b) -> a.rank() >= b.rank() ? a : b;
        assertTrue(caught.apply(c -> true, highest), "ignoring yanked/evidence must be caught");
        assertTrue(caught.apply(Case::selectable, (a, b) -> b), "last-in-list-order must be caught");
        assertTrue(everyPins().stream().anyMatch(pins -> !java.util.Objects.equals(expected(cases, pins),
                        cases.stream().filter(c -> c.selectable() && new Case(c.version(), c.rank(), true, c.analyserMin(),
                                        c.authoring(), c.host(), c.mongoose(), List.of()).oracleCompatible(pins))
                                .reduce(highest).map(c -> (String) c.version().get("version")).orElse(null))),
                "ignoring the plugins axis must be caught");
        assertEquals(expected(cases, everyPins().get(0)), selected(versions, everyPins().get(0)));
    }

    @Test
    void aPreReleasePinIsOnlyInsideARangeThatNamesThatPreRelease() {
        Range line = Range.parse(">=1.1.3 <1.2.0");
        assertFalse(line.contains("1.2.0-SNAPSHOT"), "a 1.2 development build is not the tested 1.1 line");
        assertFalse(line.contains("1.1.4-rc.1"));
        assertFalse(Range.parse(">=1.0.43").contains("1.0.44-SNAPSHOT"));
        assertTrue(Range.parse(">=1.2.0-rc.1 <1.3.0").contains("1.2.0-rc.2"), "a range naming the pre-release admits it");
        assertFalse(Range.parse(">=1.2.0-rc.1 <1.3.0").contains("1.2.1-rc.1"), "but only for that MAJOR.MINOR.PATCH");
        assertTrue(Range.parse("*").contains("1.2.0-SNAPSHOT"), "* is no constraint at all");
        assertTrue(line.contains("1.1.4") && line.contains("1.1.3+build.7"), "build metadata is not a pre-release");
        Map<String, Object> v = version("1.0.0", "1.12.0", "any", "any", ">=1.1.3 <1.2.0", "*");
        assertFalse(SkillRegistry.compatible(v, new ProjectPins("1.41.2", "spring", "mongoose", "1.2.0-SNAPSHOT", "1.1.2")));
        assertTrue(SkillRegistry.compatible(v, new ProjectPins("1.42.0-SNAPSHOT", "spring", "mongoose", "1.1.3", "1.1.2")),
                "the analyser axis is a minimum, and a development analyser above it qualifies");
    }

    @Test
    void evidenceMustBeAGateOrTheV2PinToBeSelectable() {
        String revision = "cb7a741f7551c0bb979536de4be55cd8056710d2";
        assertNull(SkillRegistry.evidenceProblem(gate("1.0.0"), revision));
        Map<String, Object> pin = Map.of("kind", "m19-skills/2-pin", "label", "pinned by /2", "date", "2026-11-07",
                "index", "m19-skills/2", "revision", revision);
        assertNull(SkillRegistry.evidenceProblem(pin, revision));
        assertNotNull(SkillRegistry.evidenceProblem(pin, "0".repeat(40)), "a /2 pin of another revision is not this version's");
        assertNotNull(SkillRegistry.evidenceProblem(Map.of("label", "x", "date", "2026-11-08"), revision), "no kind");
        assertNotNull(SkillRegistry.evidenceProblem(Map.of("kind", "trust-me", "label", "x", "date", "2026-11-08"), revision));
        assertNotNull(SkillRegistry.evidenceProblem(Map.of("kind", "gate", "label", "x", "date", "2026-11-08"), revision),
                "a gate with no runs");
        Map<String, Object> overCount = new HashMap<>(gate("1.0.0"));
        overCount.put("tests", Map.of("label", "suite", "passed", 13.0, "total", 12.0));
        assertNotNull(SkillRegistry.evidenceProblem(overCount, revision), "more passes than runs");
        Map<String, Object> badDate = new HashMap<>(gate("1.0.0"));
        badDate.put("date", "8 Nov");
        assertNotNull(SkillRegistry.evidenceProblem(badDate, revision));
        Map<String, Object> noKind = any("2.0.0");
        noKind.put("evidence", Map.of("label", "x", "date", "2026-11-08"));
        assertEquals("1.0.0", selected(List.of(any("1.0.0"), noKind), SPRING_MONGOOSE), "label and date alone are not evidence");
    }

    @Test
    void semverAndRangeGrammar() {
        for (String ok : List.of("0.0.0", "1.2.3", "1.2.3-rc.1", "1.2.3-0.alpha", "1.2.3+build.5", "10.20.30-x-y.1+b"))
            assertNotNull(Semver.tryParse(ok), ok);
        for (String bad : List.of("1.2", "01.2.3", "1.2.3-", "1.2.3-01", "v1.2.3", " 1.2.3", "1.2.3.4", ""))
            assertNull(Semver.tryParse(bad), bad);
        assertEquals(0, Semver.parse("1.0.0+a").compareTo(Semver.parse("1.0.0+b")), "build metadata has no precedence");
        assertTrue(Semver.parse("1.0.0-alpha").compareTo(Semver.parse("1.0.0-alpha.1")) < 0);
        assertTrue(Semver.parse("1.0.0-alpha.beta").compareTo(Semver.parse("1.0.0-beta")) < 0);
        assertTrue(Semver.parse("1.0.0-1").compareTo(Semver.parse("1.0.0-alpha")) < 0);

        for (String ok : List.of("*", ">=1.1.3", ">=1.1.3 <1.2.0", "=1.0.44", "<=2.0.0 >1.0.0"))
            assertNotNull(Range.tryParse(ok), ok);
        for (String bad : List.of("", " ", "1.1.3", ">= 1.1.3", ">=1.1.3  <1.2.0", "^1.1.3", "~1.1", ">=1.1", "* ", "x"))
            assertNull(Range.tryParse(bad), bad);
        Range r = Range.parse(">=1.1.3 <1.2.0");
        assertTrue(r.contains("1.1.3") && r.contains("1.1.99"));
        assertFalse(r.contains("1.1.2") || r.contains("1.2.0") || r.contains(null) || r.contains("junk"));
        assertTrue(Range.parse("*").contains(null));
    }
}
