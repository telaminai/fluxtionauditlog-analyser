package telamin.fluxtion.audit.analyser.bundle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
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
}
