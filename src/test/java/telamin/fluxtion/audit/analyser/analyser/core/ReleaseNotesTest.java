package telamin.fluxtion.audit.analyser.analyser.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ReleaseNotesTest {

    private static final String CHANGELOG = """
            # Changelog

            ## [Unreleased]
            ### Added
            - a pending thing

            ## [1.4.2] - 2026-08-14
            ### Added
            - the new feature
            ### Fixed
            - the old bug

            ## [1.4.1] - 2026-08-01
            ### Fixed
            - something earlier
            """;

    @Test
    void extractsTheRequestedVersionSection() {
        String s = ReleaseNotes.sectionFor("1.4.2", CHANGELOG);
        assertTrue(s.contains("the new feature"));
        assertTrue(s.contains("the old bug"));
        assertFalse(s.contains("a pending thing"), "must not bleed the Unreleased section");
        assertFalse(s.contains("something earlier"), "must stop at the next version heading");
    }

    @Test
    void extractsUnreleased() {
        assertEquals("### Added\n- a pending thing", ReleaseNotes.sectionFor("Unreleased", CHANGELOG));
    }

    @Test
    void unknownVersionIsEmpty() {
        assertEquals("", ReleaseNotes.sectionFor("9.9.9", CHANGELOG));
    }
}
