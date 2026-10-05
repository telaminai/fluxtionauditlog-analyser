package telamin.fluxtion.audit.analyser.analyser.session;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

/** Cheap source-boundary pins alongside the executable frame/replay witnesses (rule 8). */
class Issue84BoundaryContractTest {
    private static String source(String file) throws Exception {
        return Files.readString(Path.of("src/main/java/telamin/fluxtion/audit/analyser/analyser/" + file + ".java"));
    }

    @Test void provenanceRequiresOperationAndContentAndAnchorRestorationIsNotAnEdit() throws Exception {
        String provenance = source("session/node/OpenBundle");
        assertTrue(provenance.contains("event.opId() == pendingOperation && verifiedContentLoaded"), "operationAndContentOwnProvenance");
        assertTrue(source("session/node/SessionBoundary").contains("inFlightBundle.profileDigest()"), "verifiedContentTravelsToTheLoader");
        assertTrue(source("session/node/BundleAnchor").contains("event.remembered()"), "restorationPreservesUnavailableRoots");
        // whitespace-normalised: the call moved one nesting level deeper when the settings apply
        // was wrapped in an EDT hop (#83). The property asserted is unchanged -- the restore still
        // sits immediately inside the GRAPHS branch.
        assertTrue(source("ui/MainFrame").replaceAll("\\s+", " ")
                .contains("Category.GRAPHS)) { restoreGraphDefinitions"), "importUpdatesTheLiveCharts");
    }

    @Test void sourcePreparationAndDisplayAcceptanceHaveOneOwner() throws Exception {
        assertFalse(source("ui/WalkPresenter").contains("canPrepareSource"), "noSynchronousAvailabilityRead");
        String playback = source("session/node/WalkPlayback");
        String prepared = playback.substring(playback.indexOf("boolean onWalkStepPrepared"), playback.indexOf("boolean onWalkTargetsLit"));
        assertFalse(prepared.contains("accepted = step"), "preparationCannotAcceptADialogueStep");
        assertTrue(playback.contains("boolean onWalkViewChanged"), "nativeNavigationReachesTheNode");
        assertTrue(source("ui/MainFrame").contains("walkPlayback.permitsSourceApplication(walkTicket)"), "applicationAsksTheNodeAboutOwnership");
    }

    @Test void offersAndFallbacksCarryTheirOriginAndJavaCaptionsDiscloseTheirLimit() throws Exception {
        assertTrue(source("session/SessionEvents").contains("ProjectAudience audience"), "audienceIsAnOperationFact");
        assertTrue(source("session/node/ProjectReopenOffer").contains("audience.offersAllowed()"), "permissionIsDecidedInTheNode");
        assertTrue(source("config/ProjectReopen").contains("Origin logOrigin, Origin topologyOrigin"), "originsAreIndependent");
        assertTrue(source("walk/WalkResolver").contains("saved source revision not compared"), "captionDisclosesItsSourceRevisionLimit");
        assertTrue(Files.readString(Path.of("docs/site/user-guide/assistant.md")).contains("CURRENT does not"), "humanGuideDeclaresTheChosenContract");
    }
}
