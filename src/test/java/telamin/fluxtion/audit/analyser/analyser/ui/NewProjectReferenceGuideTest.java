package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.config.ReferenceSet;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D-AX1d wired into the New-project flow. Swing is not unit-tested here (rule 4); this covers the model
 * and the write, which is the part that touches a user's repository.
 */
class NewProjectReferenceGuideTest {

    private static NewProjectDiscovery.Selection withGuide(boolean on) {
        return new NewProjectDiscovery.Selection(Set.of(), Set.of(), null, on);
    }

    @Test
    void nothingIsWrittenUnlessTheOfferWasEXPLICITLYaccepted(@TempDir Path root) {
        NewProjectDiscovery.Offer offer = NewProjectDiscovery.discover(root);
        // M35.4: the default selection adopts nothing, so an empty confirmation must leave no file
        assertTrue(NewProjectDiscovery.writeReferenceGuide(offer, NewProjectDiscovery.Selection.empty())
                .isEmpty());
        assertFalse(Files.exists(root.resolve(ReferenceSet.FILE_NAME)),
                "an unconfirmed offer must not write into the project");
    }

    @Test
    void anAcceptedOfferWritesTheGuide(@TempDir Path root) {
        if (ReferenceSet.agreed().isEmpty()) return;
        NewProjectDiscovery.Offer offer = NewProjectDiscovery.discover(root);
        assertEquals(ReferenceSet.Outcome.CAN_CREATE, offer.referenceGuide());
        assertTrue(NewProjectDiscovery.writeReferenceGuide(offer, withGuide(true)).isEmpty());
        assertTrue(Files.exists(root.resolve(ReferenceSet.FILE_NAME)));
    }

    @Test
    void anExistingGuideIsREPORTEDratherThanOverwritten(@TempDir Path root) throws Exception {
        String theirs = "# mine\n";
        Files.writeString(root.resolve(ReferenceSet.FILE_NAME), theirs);
        NewProjectDiscovery.Offer offer = NewProjectDiscovery.discover(root);
        assertEquals(ReferenceSet.Outcome.EXISTS, offer.referenceGuide());

        Optional<String> problem = NewProjectDiscovery.writeReferenceGuide(offer, withGuide(true));
        assertTrue(problem.isPresent(), "the user asked for it and did not get it — say so, do not fail mute");
        assertEquals(theirs, Files.readString(root.resolve(ReferenceSet.FILE_NAME)));
    }

    @Test
    void aFileThatAPPEAREDbetweenTheOfferAndTheWriteIsAlsoRefused(@TempDir Path root) throws Exception {
        if (ReferenceSet.agreed().isEmpty()) return;
        NewProjectDiscovery.Offer offer = NewProjectDiscovery.discover(root);   // said CAN_CREATE
        String theirs = "# written while the dialog was open\n";
        Files.writeString(root.resolve(ReferenceSet.FILE_NAME), theirs);

        assertTrue(NewProjectDiscovery.writeReferenceGuide(offer, withGuide(true)).isPresent());
        assertEquals(theirs, Files.readString(root.resolve(ReferenceSet.FILE_NAME)),
                "the offer is stale by the time the user confirms; re-check, never trust it");
    }

    @Test
    void everyCreateOutcomeMapsToITSOWNmessage(@TempDir Path root) throws Exception {
        // Closure review: my previous attempt at this wrote the file BEFORE calling writeReferenceGuide,
        // so an outer existence check returned first and neither create() nor the diagnostic branch ran —
        // it would have passed with the fix removed. The fix is now structural: create() checks ONCE and
        // returns its reason, so there is no second window and no inference to test. What is left worth
        // testing is that each reason reaches the user as its own message.
        NewProjectDiscovery.Offer offer = NewProjectDiscovery.discover(root);

        if (!ReferenceSet.agreed().isEmpty()) {
            assertEquals(ReferenceSet.Result.WROTE, ReferenceSet.create(root, null));
            assertTrue(Files.exists(root.resolve(ReferenceSet.FILE_NAME)));
            // now it exists, so the SAME call must report existence, not "nothing agreed"
            Optional<String> problem = NewProjectDiscovery.writeReferenceGuide(offer, withGuide(true));
            assertTrue(problem.isPresent() && problem.get().contains("already exists"),
                    "the reason must be the real one: " + problem.orElse("<none>"));
        }

        assertEquals(ReferenceSet.Result.ALREADY_EXISTS,
                ReferenceSet.create(root, null),
                "create is the single source of the reason — a caller must never have to infer it");
    }

    @Test
    void springIsDETECTEDsoTheSpringLinkIsSelected(@TempDir Path root) throws Exception {
        Path xml = root.resolve("src/main/fluxtion/designer/application-context.xml");
        Files.createDirectories(xml.getParent());
        Files.writeString(xml, "<beans/>");
        assertEquals("spring", NewProjectDiscovery.detectKind(root));

        if (ReferenceSet.agreedFor("spring").size() <= ReferenceSet.agreed().size()) return;
        NewProjectDiscovery.writeReferenceGuide(NewProjectDiscovery.discover(root), withGuide(true));
        String written = Files.readString(root.resolve(ReferenceSet.FILE_NAME));
        String springUrl = ReferenceSet.all().stream()
                .filter(r -> r.agreed() && "spring".equals(r.appliesTo())).findFirst().orElseThrow().url();
        assertTrue(written.contains(springUrl), "a Spring project must get the Spring link");
    }

    @Test
    void anOrdinaryProjectIsNOTchargedForTheSpringLink(@TempDir Path root) throws Exception {
        if (ReferenceSet.agreed().isEmpty()) return;
        assertEquals(null, NewProjectDiscovery.detectKind(root), "detection must be conservative");
        NewProjectDiscovery.writeReferenceGuide(NewProjectDiscovery.discover(root), withGuide(true));
        String written = Files.readString(root.resolve(ReferenceSet.FILE_NAME));
        ReferenceSet.all().stream().filter(r -> r.agreed() && r.appliesTo() != null)
                .forEach(r -> assertFalse(written.contains(r.url()),
                        r.id() + " is appliesTo-gated and must not appear in a plain project"));
    }

    @Test
    void anEmptyDirectoryIsStillEMPTY_andTheGuideIsStillOffered(@TempDir Path root) {
        // M19.13: an empty directory is an ordinary empty offer, and `empty()` keeps meaning "the PROJECT
        // held nothing to adopt" — the guide comes from the analyser, not from the directory. My first
        // attempt folded the guide into empty(), which broke that decision AND put the checkbox inside the
        // non-empty branch, so the one case where it helps most never saw it.
        if (ReferenceSet.agreed().isEmpty()) return;
        NewProjectDiscovery.Offer offer = NewProjectDiscovery.discover(root);
        assertTrue(offer.empty(), "nothing was discovered in the directory, so the offer is empty");
        assertEquals(ReferenceSet.Outcome.CAN_CREATE, offer.referenceGuide(),
                "and the guide is still offerable — the dialog shows in both branches");
    }
}
