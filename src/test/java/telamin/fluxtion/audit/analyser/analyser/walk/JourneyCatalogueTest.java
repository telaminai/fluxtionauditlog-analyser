package telamin.fluxtion.audit.analyser.analyser.walk;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;
import telamin.fluxtion.audit.analyser.analyser.config.ProjectProfile;
import telamin.fluxtion.audit.analyser.analyser.config.SettingsShare;
import telamin.fluxtion.audit.analyser.analyser.config.WalkBin;
import telamin.fluxtion.audit.analyser.analyser.parse.LogStore;
import telamin.fluxtion.audit.analyser.analyser.parse.LogStores;
import telamin.fluxtion.audit.analyser.bundle.EvidenceBundle;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OA-5 / OA-A18 (spec §8): the DEMO journey's catalogue page describes the ACTUAL bytes it links to, and every fact its
 * dialogue states is re-derived here from the bundled log — so a regenerated fixture, an edited page or a swapped bundle
 * that no longer agree fail by name. Headless; reads only what is committed.
 */
class JourneyCatalogueTest {

    static final Path BUNDLE = Path.of("docs/site/assets/journeys/find-the-first-recorded-breach.fexp");
    static final Path PAGE = Path.of("docs/site/journeys/find-the-first-recorded-breach.md");
    static final String WALK = "DEMO first breach";

    @Test
    @DisplayName("the page states the bundle's actual size, sha256 and identity, and the bundle verifies")
    void thePageDescribesTheActualBytes() throws Exception {
        byte[] bytes = Files.readAllBytes(BUNDLE);
        String page = Files.readString(PAGE);
        String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        assertTrue(page.contains(String.format("%,d bytes", bytes.length)), "the size stated is the file's: " + bytes.length);
        assertTrue(page.contains("sha256 `" + sha + "`"), "the sha256 stated is the file's: " + sha);
        EvidenceBundle.Verification v = EvidenceBundle.verify(BUNDLE);
        assertTrue(v.ok(), v.refusal());
        assertTrue(page.contains("`" + v.identity() + "`"), "the identity stated is the bundle's: " + v.identity());
        assertTrue(page.contains("No provider needed for this demonstration"));
        try (ZipFile z = new ZipFile(BUNDLE.toFile())) {
            assertTrue(z.stream().noneMatch(e -> e.getName().startsWith("replay/")), "no replay inputs: nothing to run");
        }
        assertTrue(page.contains("carries no replay inputs, and opening or playing it never runs anything"));
    }

    @Test
    @DisplayName("every fact in the dialogue is re-derived from the bundled log: counts, the first logged breach, the last")
    void theDialogueIsTrueOfTheBundledLog(@TempDir Path tmp) throws Exception {
        EvidenceBundle.Unpacked u = EvidenceBundle.unpack(BUNDLE, tmp);
        assertTrue(u.verification().ok(), u.verification().refusal());
        Path copy = u.workingCopy();
        Path log;
        try (var files = Files.list(copy.resolve("log"))) {
            log = files.findFirst().orElseThrow();
        }
        int total, breaches = 0, first = -1, last = -1;
        java.util.Map<String, Integer> counts = new java.util.TreeMap<>();
        try (LogStore store = LogStores.open(log, 256)) {
            total = store.size();
            for (int i = 0; i < total; i++) {
                String event = store.index().event(i);
                if (event == null) continue;
                counts.merge(event, 1, Integer::sum);
                if (event.equals("RiskBreachEvent")) {
                    breaches++;
                    if (first < 0) first = i;
                    last = i;
                }
            }
        }
        AppConfig c = new AppConfig();
        assertTrue(ProjectProfile.load(copy.resolve("profile/project.fluxtion-settings"), c, new SettingsShare()).loaded());
        WalkSpec journey = WalkBin.find(c.walks, WALK);
        assertNotNull(journey, "the bundle carries the journey");
        assertEquals(WalkSpec.SCRIPTED, journey.conversation().kind(), "labelled as what it is");
        String words = String.join("\n", journey.conversation().turns().stream().map(WalkSpec.Turn::text).toList());
        assertTrue(words.contains(total + " records"), "the total: " + total);
        for (var e : counts.entrySet()) assertTrue(words.contains(e.getKey() + " " + e.getValue()), "the count of " + e.getKey());
        assertTrue(words.contains("At record " + first + ": the first RiskBreachEvent"), "the first logged breach: " + first);
        assertTrue(words.contains("of " + breaches + " (the last is record " + last + ")"), "how many, and the last");
        // the step that makes the claim points at that very record, and at the chart that marks it
        WalkSpec.Step claim = journey.steps().stream().filter(s -> "t4".equals(s.through())).findFirst().orElseThrow();
        final String firstRow = "records:row:" + first;
        assertTrue(claim.targets().stream().anyMatch(t -> t.target().equals(firstRow)), claim.targets().toString());
        assertEquals(first, claim.view().record());
        assertTrue(c.savedGraphs.stream().anyMatch(g -> g.name().equals(claim.view().graph())), "its chart travels as a saved definition");
    }

    @Test
    @DisplayName("the page's transcript is the bundled dialogue, word for word, in order, attributed as scripted")
    void thePageTranscriptIsTheBundledDialogue(@TempDir Path tmp) throws Exception {
        EvidenceBundle.Unpacked u = EvidenceBundle.unpack(BUNDLE, tmp);
        AppConfig c = new AppConfig();
        ProjectProfile.load(u.workingCopy().resolve("profile/project.fluxtion-settings"), c, new SettingsShare());
        List<WalkSpec.Turn> turns = WalkBin.find(c.walks, WALK).conversation().turns();
        String page = Files.readString(PAGE);
        int at = 0;
        for (WalkSpec.Turn t : turns) {
            String who = "user".equals(t.role()) ? "**Question (scripted):** " : "**Answer (scripted — not a live model):** ";
            int found = page.indexOf(who + t.text(), at);
            assertTrue(found >= 0, "the page carries turn " + t.id() + " as bundled, after the one before");
            at = found;
        }
        Matcher m = Pattern.compile("\\*\\*Steps\\*\\* \\| (\\d+) \\|").matcher(page);
        assertTrue(m.find());
        assertEquals(WalkBin.find(c.walks, WALK).steps().size(), Integer.parseInt(m.group(1)), "the step count stated");
    }
}
