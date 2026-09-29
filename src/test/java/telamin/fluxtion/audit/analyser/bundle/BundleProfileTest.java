package telamin.fluxtion.audit.analyser.bundle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Evidence bundle v1, the profile member (spec r3 §4.2, EP-A9). The fixture is a REAL sender profile, written by the
 * analyser during a driven demo run, with keys added that must never travel. What leaves is decided by
 * {@link BundleProfile}, so these checks are the allow-list's regression: families, the left-out chart, and no path.
 */
public class BundleProfileTest {

    static final Path FIXTURE = Path.of("src/test/resources/bundle/sender-project.fluxtion-settings");
    static final String EXTERNAL = "Venue feed latency (external CSV)";

    /** The sender's profile, where a project keeps it, so it loads with its project's anchoring rules. */
    public static Path senderProfile(Path tmp) throws IOException {
        Path p = Files.createDirectories(tmp.resolve("sender-project/.analyser")).resolve("project.fluxtion-settings");
        Files.copy(FIXTURE, p);
        return p;
    }

    static Properties read(Path p) throws IOException {
        Properties props = new Properties();
        props.load(new StringReader(Files.readString(p)));
        return props;
    }

    @Test
    @DisplayName("EP-A9: only charts and focuses, reports and walks, hidden columns leave — no root, runbook, environment or processor")
    void onlyTheAllowListedFamiliesLeave(@TempDir Path tmp) throws Exception {
        Path out = tmp.resolve("profile/project.fluxtion-settings");
        Files.createDirectories(out.getParent());
        Path profile = senderProfile(tmp);
        assertDoesNotThrow(() -> BundleProfile.export(profile, out), "the DEMO profile exports");
        Properties p = read(out);
        Set<String> allowed = Set.of("share", "graph", "focus", "report", "walk", "hiddenColumn");
        for (String key : p.stringPropertyNames()) {
            String family = key.split("\\.")[0];
            assertTrue(allowed.contains(family), "a key outside the allow-list left the machine: " + key);
        }
        assertEquals("1", p.getProperty("walk.count"), "the walk travels");
        assertEquals("1", p.getProperty("report.count"), "the report travels");
        assertEquals("why-the-spread-moved", p.getProperty("walk.0.name"));
    }

    @Test
    @DisplayName("EP-A9: a chart with an external series is left out and named; the walk step and report section on it are named")
    void theExternalChartIsLeftOutAndNamed(@TempDir Path tmp) throws Exception {
        Path out = tmp.resolve("p.fluxtion-settings");
        Path profile = senderProfile(tmp);
        var x = assertDoesNotThrow(() -> BundleProfile.export(profile, out), "the DEMO profile exports");
        Properties p = read(out);
        assertEquals("2", p.getProperty("graph.count"), "three charts in, the external one out: " + p.getProperty("graph.count"));
        assertTrue(p.stringPropertyNames().stream().noneMatch(k -> k.matches("graph\\.\\d+\\.name") && EXTERNAL.equals(p.getProperty(k))),
                "the external chart's definition is not in the profile");
        assertTrue(p.stringPropertyNames().stream().noneMatch(k -> k.contains(".ext.") && k.endsWith(".path")),
                "and neither is its CSV path");
        assertEquals(1, x.leftOut().size(), x.leftOut().toString());
        assertTrue(x.leftOut().get(0).contains("'" + EXTERNAL + "'"), x.leftOut().get(0));
        assertTrue(x.dangling().stream().anyMatch(d -> d.contains("walk 'why-the-spread-moved' step 4")), x.dangling().toString());
        assertTrue(x.dangling().stream().anyMatch(d -> d.contains("report 'breach-0900'")), x.dangling().toString());
    }

    @Test
    @DisplayName("EP-A9: a kept value shaped like a machine path refuses the export, naming the key, and writes nothing")
    void aPathShapedValueRefuses(@TempDir Path tmp) throws Exception {
        Path profile = senderProfile(tmp);
        // a RECORD section on a rolled set names its member file: a path when written absolute
        Files.writeString(profile, Files.readString(profile).replace("report.0.s.1.record=7",
                "report.0.s.1.record=7\nreport.0.s.1.file=/tmp/DEMO/logs/member-2.yaml"));
        Path out = tmp.resolve("p.fluxtion-settings");
        var e = assertThrows(IOException.class, () -> BundleProfile.export(profile, out));
        assertTrue(e.getMessage().contains("report.0.s.1.file"), e.getMessage());
        assertFalse(Files.exists(out), "nothing is written on a refusal");
    }

    @Test
    @DisplayName("the export is deterministic (no timestamp, no date line) and never overwrites")
    void deterministicAndNeverOverwrites(@TempDir Path tmp) throws Exception {
        Path profile = senderProfile(tmp);
        Path a = Files.createDirectories(tmp.resolve("a")).resolve("p.fluxtion-settings");
        Path b = Files.createDirectories(tmp.resolve("b")).resolve("p.fluxtion-settings");
        BundleProfile.export(profile, a);
        BundleProfile.export(profile, b);
        assertEquals(Files.readString(a), Files.readString(b), "the same profile exports to the same bytes");
        assertFalse(Files.readString(a).contains("exportedAt"), "no timestamp in a bundle member");
        var e = assertThrows(IOException.class, () -> BundleProfile.export(profile, a));
        assertTrue(e.getMessage().contains("will not overwrite"), e.getMessage());
    }

    @Test
    @DisplayName("with no project open, the person's own settings are the source, and the API key never leaves")
    void ownSettingsAreASourceAndTheKeyStays(@TempDir Path tmp) throws Exception {
        Path config = tmp.resolve("config");
        Files.writeString(config, "apiKey=DEMO-not-a-key\nllmProvider=anthropic\nsourceRoot.count=1\nsourceRoot.0=/tmp/DEMO/src\n"
                + "hiddenColumn.count=1\nhiddenColumn.0=thread\nwalk.count=0\nreport.count=0\ngraph.count=0\n");
        Path out = tmp.resolve("p.fluxtion-settings");
        BundleProfile.export(config, out);
        String text = Files.readString(out);
        assertFalse(text.contains("DEMO-not-a-key"), "the key stays");
        assertFalse(text.contains("sourceRoot"), "the roots stay");
        assertEquals("thread", read(out).getProperty("hiddenColumn.0"), "the view travels");
    }

    /** The fixture with its report narrative (report.0.s.0.text) replaced by {@code text}, escaped as a properties value. */
    static Path withNarrative(Path tmp, String text) throws IOException {
        Path profile = senderProfile(tmp);
        Properties one = new Properties();
        one.setProperty("report.0.s.0.text", text);
        java.io.StringWriter w = new java.io.StringWriter();
        one.store(w, null);
        String line = w.toString().lines().filter(l -> l.startsWith("report.0.s.0.text=")).findFirst().orElseThrow();
        Files.writeString(profile, Files.readString(profile).lines()
                .map(l -> l.startsWith("report.0.s.0.text=") ? line : l).collect(java.util.stream.Collectors.joining("\n")) + "\n");
        return profile;
    }

    static String exportedNarrative(Path tmp, String text, List<String> redactedOut) throws Exception {
        Path profile = withNarrative(tmp, text);
        Path out = tmp.resolve("n-" + Integer.toHexString(text.hashCode()) + ".fluxtion-settings");
        var x = assertDoesNotThrow(() -> BundleProfile.export(profile, out), "prose never refuses the export: " + text);
        redactedOut.addAll(x.redacted());
        return read(out).getProperty("report.0.s.0.text");
    }

    @Test
    @DisplayName("F2: a machine path INSIDE prose is redacted, named, and absent from the bundle — the review's reproduction")
    void anEmbeddedPathIsRedactedAndNamed(@TempDir Path tmp) throws Exception {
        List<String> redacted = new java.util.ArrayList<>();
        String got = exportedNarrative(tmp, "we saw it in /Users/demo-person/private/logs/secret-venue.yaml and moved on", redacted);
        assertFalse(got.contains("demo-person") || got.contains("secret-venue"), "the path does not leave: " + got);
        assertEquals("we saw it in " + BundleProfile.REDACTED + " and moved on", got, "only the path is removed");
        assertEquals(List.of("report.0.s.0.text: /Users/demo-person/private/logs/secret-venue.yaml"), redacted,
                "and the author is told exactly what was removed, from which key");
    }

    @Test
    @DisplayName("F2: every machine-path shape is redacted in prose — home, Windows, UNC, file URI, a sentence-leading path")
    void everyMachinePathShapeIsRedacted(@TempDir Path tmp) throws Exception {
        // ~7dev / ~123: a digit-leading username is legal and was silently un-redacted for one commit on
        // 2026-09-29 by a fix aimed at "~1/price". Found by review, not by this suite, which had only
        // letter-leading cases.
        for (String path : List.of("~/logs/demo/quote.yaml", "~demo/logs/quote.yaml",
                "~7dev/logs/quote.yaml", "~123/secret/quote.yaml", "~123/quote.yaml",
                "C:\\Users\\demo\\logs\\q.yaml",
                "D:/data/demo/q.yaml", "\\\\fileserver\\demo\\q.yaml", "file:///tmp/DEMO/q.yaml", "/etc/demo/q.yaml")) {
            List<String> redacted = new java.util.ArrayList<>();
            Path dir = Files.createDirectories(tmp.resolve(Integer.toHexString(path.hashCode())));
            String got = exportedNarrative(dir, "see " + path + ", then the chart", redacted);
            assertEquals("see " + BundleProfile.REDACTED + ", then the chart", got, "redacted: " + path);
            assertEquals(1, redacted.size(), path + " -> " + redacted);
        }
        List<String> stop = new java.util.ArrayList<>();
        Path d2 = Files.createDirectories(tmp.resolve("stop"));
        assertEquals("it was in " + BundleProfile.REDACTED + ".", exportedNarrative(d2, "it was in /var/demo/q.yaml.", stop),
                "a sentence's full stop stays");
        assertEquals(List.of("report.0.s.0.text: /var/demo/q.yaml"), stop);
        List<String> leading = new java.util.ArrayList<>();
        Path dir = Files.createDirectories(tmp.resolve("leading"));
        assertEquals(BundleProfile.REDACTED + " held the log", exportedNarrative(dir, "/var/demo/logs held the log", leading),
                "prose that STARTS with a path is prose, redacted, not refused");
    }

    @Test
    @DisplayName("F2 mirror: ordinary writing passes untouched — %, colons, ratios, and/or, URLs, relative paths, C: alone")
    void ordinaryProsePassesUntouched(@TempDir Path tmp) throws Exception {
        for (String prose : List.of(
                "~5% of records carried a spread above 0.004",
                // found in a SHIPPED bundle 2026-09-29: "~1/price" was redacted to the marker, so the
                // chart explanation a recipient reads lost the very formula it was explaining.
                "absolute spread is ~1/price\u00b2 smaller, but as a fraction of price it should match",
                "roughly ~2/3 of the cycles requoted",
                "at 09:00: the spread widened; ratio 3:1 bid to ask",
                "note: this is a bare colon in a sentence",
                "the file: demo-quote-audit.yaml, read whole",
                "and/or the risk limit; 1/2 of the records; bid/ask per quotePublisher.spread/bid",
                "the drive letter C: on its own, or the string \"C:\\\" quoted in an explanation",
                "see https://fluxtion-playground.dev/fluxtion-golden-path.md for the model",
                "the uat logs live under logs/uat/quote-service-uat.yaml in the project",
                "dated 28/09/2026, window 09:00:00.090 to 09:00:00.360")) {
            List<String> redacted = new java.util.ArrayList<>();
            Path dir = Files.createDirectories(tmp.resolve(Integer.toHexString(prose.hashCode())));
            assertEquals(prose, exportedNarrative(dir, prose, redacted), "ordinary writing is left alone");
            assertEquals(List.of(), redacted, "and nothing is reported as redacted: " + prose);
        }
    }

    @Test
    @DisplayName("F2: a redacted profile is still a profile — it loads, with its report and walk")
    void aRedactedProfileStillLoads(@TempDir Path tmp) throws Exception {
        Path profile = withNarrative(tmp, "we saw it in /Users/demo-person/logs/q.yaml");
        Path out = Files.createDirectories(tmp.resolve("bundle/profile")).resolve("project.fluxtion-settings");
        BundleProfile.export(profile, out);
        var c = new telamin.fluxtion.audit.analyser.analyser.config.AppConfig();
        var loaded = telamin.fluxtion.audit.analyser.analyser.config.ProjectProfile.load(out, c,
                new telamin.fluxtion.audit.analyser.analyser.config.SettingsShare());
        assertTrue(loaded.loaded(), loaded.message());
        assertEquals(1, c.reports.size());
        assertEquals(1, c.walks.size());
        assertTrue(c.reports.get(0).sections().stream().anyMatch(s -> ("we saw it in " + BundleProfile.REDACTED).equals(s.text())),
                "the recipient reads the redaction in the report itself");
    }
}
