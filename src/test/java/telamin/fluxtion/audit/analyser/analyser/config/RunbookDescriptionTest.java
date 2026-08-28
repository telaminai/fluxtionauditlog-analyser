package telamin.fluxtion.audit.analyser.analyser.config;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M43.2 (was M38.8) — {@code runbook.N.description}: the line a model chooses by.
 *
 * <p>The decision under test is D-AI5. A skill-shaped runbook carries the same sentence in its
 * frontmatter, and it would be easy to read it from there at serve time — but that would INFER the fact,
 * and {@code context} would then change whenever someone edited a file, with nobody declaring it. So it
 * is stored, and the file may only ever PREFILL a dialog a person confirms.
 */
class RunbookDescriptionTest {

    private static Properties written(Map<String, Runbooks.Pointer> in) {
        Properties p = new Properties();
        ConfigStore.writeRunbooks(p, in);
        return p;
    }

    private static Map<String, Runbooks.Pointer> readBack(Properties p) {
        Map<String, Runbooks.Pointer> out = new LinkedHashMap<>();
        ConfigStore.readRunbooks(p, out);
        return out;
    }

    @Test
    void aDescriptionRoundTripsWithItsPointer() {
        var in = Map.of("restart", new Runbooks.Pointer("ops/restart.md",
                "Restart the quote service after a config change; what to check first."));
        Properties p = written(in);
        assertEquals("Restart the quote service after a config change; what to check first.",
                p.getProperty("runbook.0.description"));
        assertEquals(in, readBack(p));
    }

    @Test
    void aRunbookWrittenBeforeThisSliceStillLoads_theDescriptionIsOptional() {
        // the whole point of documenting the convention early: nothing needs rewriting
        Properties p = new Properties();
        p.setProperty("runbook.count", "1");
        p.setProperty("runbook.0.name", "deploy");
        p.setProperty("runbook.0.path", "ops/deploy.md");

        var out = readBack(p);
        assertEquals(Runbooks.Pointer.of("ops/deploy.md"), out.get("deploy"));
        assertNull(out.get("deploy").description());
    }

    @Test
    void anAbsentDescriptionWritesNoKeyAtAll() {
        // an empty string in the profile would be a declaration of nothing; absence is the honest form
        Properties p = written(Map.of("deploy", Runbooks.Pointer.of("ops/deploy.md")));
        assertFalse(p.stringPropertyNames().contains("runbook.0.description"));
    }

    @Test
    void blankIsNormalisedToAbsent_soTwoWaysOfSayingNothingAreOneThing() {
        assertNull(new Runbooks.Pointer("ops/x.md", "   ").description());
        assertNull(new Runbooks.Pointer("ops/x.md", "").description());
    }

    // ---- the gate: a description is a declaration and is bounded like one -------------------------

    @Test
    void aMultiLineDescriptionIsRefused_thatWouldBeTheRUNBOOK() {
        var bad = Runbooks.refuseDescription("runbook 'x'", "step one\nstep two");
        assertTrue(bad.isPresent());
        assertTrue(bad.get().contains("one line"), bad.get());
        assertTrue(bad.get().contains("stores no instructions"),
                "say WHY: the analyser holds a pointer, never the steps — " + bad.get());
    }

    @Test
    void anOverlongDescriptionIsRefused() {
        var bad = Runbooks.refuseDescription("runbook 'x'", "d".repeat(Runbooks.MAX_DESCRIPTION + 1));
        assertTrue(bad.isPresent());
        assertTrue(bad.get().contains("not the runbook itself"), bad.get());
    }

    @Test
    void anAbsentDescriptionIsNotAnError() {
        assertTrue(Runbooks.refuseDescription("runbook 'x'", null).isEmpty());
        assertTrue(Runbooks.refuseDescription("runbook 'x'", "  ").isEmpty());
    }

    @Test
    void aRefusedDescriptionCostsTheDESCRIPTION_neverThePointer() {
        // the trade that matters: a malformed one-line summary must not lose a good pointer, because the
        // pointer is the thing that does the work and the description is a convenience on top of it
        Properties p = written(Map.of("deploy",
                new Runbooks.Pointer("ops/deploy.md", "line one\nline two")));
        assertEquals("ops/deploy.md", p.getProperty("runbook.0.path"), "the pointer survives");
        assertNull(p.getProperty("runbook.0.description"), "the bad description does not");
    }

    @Test
    void aRefusedDescriptionOnTheWayINisNAMED_notSilentlyDropped() {
        Properties p = new Properties();
        p.setProperty("runbook.count", "1");
        p.setProperty("runbook.0.name", "deploy");
        p.setProperty("runbook.0.path", "ops/deploy.md");
        p.setProperty("runbook.0.description", "step one\nstep two");

        Map<String, Runbooks.Pointer> out = new LinkedHashMap<>();
        var refused = ConfigStore.readRunbooks(p, out);
        assertEquals(1, refused.size(), refused.toString());
        assertTrue(refused.get(0).contains("one line"), refused.toString());
        assertEquals(Runbooks.Pointer.of("ops/deploy.md"), out.get("deploy"),
                "the pointer still loads, without the description it could not accept");
    }

    // ---- D-AI8: the share label names what travels ------------------------------------------------

    @Test
    void theShareLabelNamesTheDescriptionAsCargo() {
        String label = SettingsShare.Category.RUNBOOKS.label;
        assertTrue(label.contains("description"),
                "a person reads the checkbox to know what leaves; the description now travels too: " + label);
        assertTrue(label.contains("never their contents"),
                "and the old promise must survive the rewording: " + label);
        assertFalse(SettingsShare.Category.RUNBOOKS.defaultOn,
                "still off by default — adding cargo never widens the default");
    }
}
