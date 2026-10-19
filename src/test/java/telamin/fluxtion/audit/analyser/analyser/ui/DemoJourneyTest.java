package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;
import telamin.fluxtion.audit.analyser.analyser.config.ProjectProfile;
import telamin.fluxtion.audit.analyser.analyser.config.SettingsShare;
import telamin.fluxtion.audit.analyser.analyser.config.WalkBin;
import telamin.fluxtion.audit.analyser.analyser.walk.WalkSpec;
import telamin.fluxtion.audit.analyser.bundle.EvidenceBundle;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M74 P2 (spec §5.8, r8 F13, §10): S1 plays the DEMO journey "Find the first recorded breach" — shipped IN THE JAR with
 * the demo set, under its own name, equal to the site bundle's walk and bound to the very bytes the bundle carries; its
 * conversation is scripted and labelled so.
 */
class DemoJourneyTest {

    static final Path BUNDLE = Path.of("docs/site/assets/journeys/find-the-first-recorded-breach.fexp");

    static byte[] resource(String name) throws Exception {
        try (InputStream in = DemoAssets.class.getResourceAsStream("/demo/" + name)) {
            assertNotNull(in, "the jar carries " + name);
            return in.readAllBytes();
        }
    }

    /**
     * ★ Witness: save the journey under {@code DemoTour.NAME} — a returning user's profile already holds that name, the
     * name assertion fails (and the four-step tour would be replaced by, or would hide, the journey).
     */
    @Test
    @DisplayName("★ the jar's walk equals the site bundle's, on the same log bytes, under its own name, labelled scripted")
    void theJarCarriesTheJourney(@TempDir Path tmp) throws Exception {
        Path fragment = Files.write(tmp.resolve("first-breach.fluxtion-settings"), resource("journey/first-breach.fluxtion-settings"));
        var parts = DemoJourney.load(fragment);
        assertNotNull(parts, "the fragment reads");
        assertNotEquals(DemoTour.NAME, DemoJourney.NAME, "its own name: a sample profile holding the tour still gets it");
        assertEquals("DEMO first breach", parts.walk().name());
        assertEquals(WalkSpec.SCRIPTED, parts.walk().conversation().kind());
        assertEquals("Simulated conversation", parts.walk().conversation().label(), "never presented as recorded or live");
        assertNotNull(parts.chart(), "its chart travels with it");

        EvidenceBundle.Unpacked u = EvidenceBundle.unpack(BUNDLE, tmp.resolve("unpacked"));
        assertTrue(u.verification().ok(), u.verification().refusal());
        AppConfig site = new AppConfig();
        assertTrue(ProjectProfile.load(u.workingCopy().resolve("profile/project.fluxtion-settings"), site,
                new SettingsShare("")).loaded());
        WalkSpec siteWalk = WalkBin.find(site.walks, DemoJourney.NAME);
        assertEquals(siteWalk.steps(), parts.walk().steps(), "the same steps");
        assertEquals(siteWalk.conversation(), parts.walk().conversation(), "the same scripted conversation");
        assertEquals(siteWalk.fingerprint(), parts.walk().fingerprint(), "bound to the same log");
        assertEquals(siteWalk.runBasis(), parts.walk().runBasis());
        assertEquals(site.savedGraphs.stream().filter(g -> g.name().equals(parts.chart().name())).findFirst().orElseThrow(),
                parts.chart(), "the same chart");

        Path siteLog;
        try (var files = Files.list(u.workingCopy().resolve("log"))) {
            siteLog = files.findFirst().orElseThrow();
        }
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        assertEquals(HexFormat.of().formatHex(sha.digest(Files.readAllBytes(siteLog))),
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(resource("journey/demo-quote-series.yaml"))),
                "the jar's log is the bundle's, byte for byte");
        assertEquals("demo-quote-series.yaml", DemoAssets.journeyLog().getFileName().toString(),
                "the file name the walk's fingerprint names");
        assertTrue(DemoAssets.files().contains("journey/demo-quote-series.yaml"));
    }
}
