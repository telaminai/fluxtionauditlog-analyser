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
        version.put("evidence", Map.of("label", "round " + v, "date", "2026-11-08"));
        return version;
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
        blankEvidence.put("evidence", Map.of("label", " ", "date", "2026-11-08"));
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

    /** The guarantee itself: across a grid of versions and pins, whatever is selected is compatible and the highest. */
    @Test
    void selectionNeverReturnsAnIncompatibleVersion() {
        List<String> ranges = List.of("*", ">=1.1.0", "<1.1.3", ">=1.1.2 <1.2.0", "=1.1.3", ">1.1.3");
        List<Map<String, Object>> versions = new ArrayList<>();
        int n = 0;
        for (String authoring : List.of("any", "spring", "java"))
            for (String host : List.of("any", "mongoose", "embedded"))
                for (String m : ranges)
                    for (String p : ranges)
                        versions.add(version("1." + (n++) + ".0", n % 2 == 0 ? "1.12.0" : "1.42.0", authoring, host, m, p));
        for (String authoring : List.of("spring", "java"))
            for (String host : List.of("mongoose", "embedded"))
                for (String m : List.of("1.1.0", "1.1.2", "1.1.3", "1.2.0"))
                    for (String p : List.of("1.1.0", "1.1.2", "1.1.3", "1.2.0")) {
                        ProjectPins pins = new ProjectPins("1.41.2", authoring, host, m, p);
                        Optional<Map<String, Object>> got = SkillRegistry.select(versions, pins);
                        List<Map<String, Object>> compatible = versions.stream()
                                .filter(v -> SkillRegistry.compatible(v, pins)).toList();
                        assertEquals(compatible.isEmpty(), got.isEmpty(), pins.toString());
                        got.ifPresent(v -> {
                            assertTrue(SkillRegistry.compatible(v, pins), pins + " got " + v);
                            Semver chosen = Semver.parse((String) v.get("version"));
                            for (Map<String, Object> c : compatible)
                                assertTrue(Semver.parse((String) c.get("version")).compareTo(chosen) <= 0,
                                        pins + ": " + c.get("version") + " outranks " + chosen);
                        });
                    }
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
