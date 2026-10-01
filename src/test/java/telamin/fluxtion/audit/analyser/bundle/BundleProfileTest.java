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
                // Java's \w is ASCII-only unless the pattern says otherwise; these half-redacted, which
                // is worse than not redacting because the author is told the path was removed.
                "/home/d\u00e9mo/logs/quote.yaml", "~jos\u00e9/logs/quote.yaml",
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
    @DisplayName("a path written against a non-ASCII letter is still redacted, and only the path is")
    void aPathAdjacentToNonAsciiProseIsRedacted(@TempDir Path tmp) throws Exception {
        // Making \w Unicode-aware widened the negative lookbehinds too, so a path touching a CJK or
        // Cyrillic character stopped matching AT ALL: it exported whole and Export.redacted() named
        // nothing, which is worse than the half-redaction the flag was added to fix. CJK prose has no
        // inter-word spaces, so this is the ordinary way to write it.
        record Case(String prose, String expected) { }
        for (Case c : List.of(
                // Owner decision (#87): delimit the path when its ending touches ambiguous prose.
                new Case("\u30ed\u30b0\u306f\"/Users/demo/logs/q.yaml\"\u306b\u3042\u308a\u307e\u3059",
                        "\u30ed\u30b0\u306f\"" + BundleProfile.REDACTED + "\"\u306b\u3042\u308a\u307e\u3059"),
                new Case("\u65e5\u5fd7/Users/demo/logs/q.yaml", "\u65e5\u5fd7" + BundleProfile.REDACTED),
                new Case("\u0444\u0430\u0439\u043b/Users/demo/logs/q.yaml",
                        "\u0444\u0430\u0439\u043b" + BundleProfile.REDACTED),
                new Case("\u30ed\u30b0\u306f~demo/logs/q.yaml", "\u30ed\u30b0\u306f" + BundleProfile.REDACTED),
                new Case("\u30ed\u30b0\u306ffile:///Users/demo/q.yaml",
                        "\u30ed\u30b0\u306f" + BundleProfile.REDACTED))) {
            List<String> redacted = new java.util.ArrayList<>();
            Path dir = Files.createDirectories(tmp.resolve(Integer.toHexString(c.prose().hashCode())));
            assertEquals(c.expected(), exportedNarrative(dir, c.prose(), redacted), "redacted: " + c.prose());
            assertEquals(1, redacted.size(), "and NAMED, so the author is told: " + c.prose());
        }

        // The deliberate cost of that: a LATIN accented letter counts as a word character, so a path written
        // against one is treated exactly as "abc/Users/x" is — not a path. That is what keeps ordinary
        // accented prose like "cafe/the/lait" (with accents) out of the redactor, which matters far more,
        // because Latin scripts put spaces around their paths and CJK does not.
        List<String> untouched = new java.util.ArrayList<>();
        Path dir = Files.createDirectories(tmp.resolve("latin-adjacent"));
        assertEquals("\u00e9/Users/demo/q.yaml",
                exportedNarrative(dir, "\u00e9/Users/demo/q.yaml", untouched),
                "a path against a LATIN letter is not redacted -- the accepted cost");
        assertEquals(List.of(), untouched);
    }

    @Test
    @DisplayName("F2 mirror: ordinary writing passes untouched — %, colons, ratios, and/or, URLs, relative paths, C: alone")
    void ordinaryProsePassesUntouched(@TempDir Path tmp) throws Exception {
        var checks = new java.util.ArrayList<org.junit.jupiter.api.function.Executable>();
        for (String prose : List.of(
                "the formula \"~1/price²\" holds; and '~2/3' of cycles",
                "run \"/status\" to check",
                "a protocol-relative \"//cdn.example/lib.js\" link",
                "~5% of records carried a spread above 0.004",
                // found in a SHIPPED bundle 2026-09-29: "~1/price" was redacted to the marker, so the
                // chart explanation a recipient reads lost the very formula it was explaining.
                "absolute spread is ~1/price\u00b2 smaller, but as a fraction of price it should match",
                "roughly ~2/3 of the cycles requoted",
                // ACCEPTED RESIDUAL, pinned so the trade-off is visible rather than folklore: a purely
                // numeric user with one extensionless segment is indistinguishable from a ratio, and the
                // ratio case ("~1/price") has a real bundle behind it. So "~123/secret" is NOT redacted in
                // prose and CAN leak a home path. WHOLE_PATH still refuses it as a whole value. Anyone
                // narrowing this must break "~1/price" to do it — decide deliberately, not by accident.
                "the run under ~123/secret finished",
                "at 09:00: the spread widened; ratio 3:1 bid to ask",
                "note: this is a bare colon in a sentence",
                "the file: demo-quote-audit.yaml, read whole",
                "and/or the risk limit; 1/2 of the records; bid/ask per quotePublisher.spread/bid",
                "the drive letter C: on its own, or the string \"C:\\\" quoted in an explanation",
                "see https://fluxtion-playground.dev/fluxtion-golden-path.md for the model",
                "the uat logs live under logs/uat/quote-service-uat.yaml in the project",
                "dated 28/09/2026, window 09:00:00.090 to 09:00:00.360")) {
            checks.add(() -> {
                List<String> redacted = new java.util.ArrayList<>();
                Path dir = Files.createDirectories(tmp.resolve(Integer.toHexString(prose.hashCode())));
                assertEquals(prose, exportedNarrative(dir, prose, redacted), "ordinary writing is left alone");
                assertEquals(List.of(), redacted, "and nothing is reported as redacted: " + prose);
            });
        }
        assertAll("ordinaryProseKeepsItsExemptions", checks);
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

    // ---- #79: every alphabet, and the prose that must survive --------------------------------------

    /**
     * The half-redaction is the thing to fear: a reported redaction that still carries the path tells
     * the author it was removed when it was not. Each of these leaked a username before #79 — the
     * last one as NFD (`e` + U+0301), which is how macOS routinely stores a filename, so it is not
     * exotic input.
     */
    @org.junit.jupiter.params.ParameterizedTest(name = "{1} is redacted whole")
    @org.junit.jupiter.params.provider.CsvSource(delimiter = '|', value = {
            "/home/d\u00e9mo/logs/x.yaml            | d\u00e9mo",
            "/home/nguy\u1ec5n/logs/x.yaml          | nguy\u1ec5n",
            "/home/\u0434\u043c\u0438\u0442\u0440\u0438\u0439/logs/x.yaml | \u0434\u043c\u0438\u0442\u0440\u0438\u0439",
            "/Users/\u738b/logs/x.yaml              | \u738b",
            "/home/de\u0301mo/logs/x.yaml           | de\u0301mo",
    })
    void aUsernameInAnyAlphabetIsRedactedWhole(String path, String user) {
        String out = BundleProfile.EMBEDDED_PATH.matcher("seen in " + path + " today")
                .replaceAll("\u2039path removed\u203a");

        assertFalse(out.contains(user), "theUsernameSurvivedAReportedRedaction: " + out);
        assertEquals("seen in \u2039path removed\u203a today", out, "andTheProseAroundItIsIntact");
    }

    @org.junit.jupiter.params.ParameterizedTest(name = "{0} is refused as a whole value")
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "~\u0434\u043c\u0438\u0442\u0440\u0438\u0439/x.yaml", "~de\u0301mo/x.yaml", "~nguy\u1ec5n/x", "~jose/x", "~\u738b/x",
            "C:\\Users\\jos\u00e9\\logs\\x.yaml",
    })
    void aPathValuedKeyIsRefusedInAnyAlphabet(String value) {
        assertTrue(BundleProfile.WHOLE_PATH.matcher(value).matches(),
                "aPathVALUEDKeyWouldHaveBEENEXPORTED: " + value);
    }

    /**
     * The other half of the bargain, and the reason the lookbehind is NOT plain ASCII: narrowing it
     * that far (tried while fixing #79) made ordinary accented prose read as a path and destroyed it.
     */
    @org.junit.jupiter.params.ParameterizedTest(name = "{0} is left alone")
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "caf\u00e9/menu/items", "\u00c9t\u00e9/Hiver/Printemps", "\u00d7/sec/min", "~5%", "~1/price\u00b2",
            "~2/3", "and/or", "https://host/path/x", "logs/uat/x.yaml",
    })
    void proseThatMerelyContainsASlashIsNotAPath(String text) {
        assertEquals(text, BundleProfile.EMBEDDED_PATH.matcher(text).replaceAll("\u2039path removed\u203a"),
                "ordinaryProseWasRedacted");
    }

    @org.junit.jupiter.api.Test
    @DisplayName("#79: a path written against CJK prose redacts AND stops where the prose resumes")
    void cjkAdjacencyRedactsAndStops() {
        String out = BundleProfile.EMBEDDED_PATH
                .matcher("\u30ed\u30b0\u306f/Users/x/q.yaml \u306b\u3042\u308a\u307e\u3059")
                .replaceAll("\u2039path removed\u203a");

        assertEquals("\u30ed\u30b0\u306f\u2039path removed\u203a \u306b\u3042\u308a\u307e\u3059", out,
                "widening the LOOKBEHIND made this stop matching at all and export whole, reporting nothing");
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "/Users/DEMO王", "/home/DEMO/机密.yaml", "/DEMO/q.yamlにあります",
            "~/DEMO/q.yamlにあります", "~DEMO/q.yamlにあります", "~123/DEMO/q.yamlにあります",
            "~123/q.yamlにあります", "file:///DEMO/q.yamlにあります",
            "C:\\DEMO\\q.yamlにあります", "\\\\DEMO\\share\\q.yamlにあります"
    })
    @DisplayName("#87 R1/R2: ambiguous unquoted endings refuse before creating an export")
    void ambiguousUnquotedEndingsRefuseWithoutWriting(String path, @TempDir Path tmp) throws Exception {
        Path input = withNarrative(tmp, "ログは" + path);
        Path out = tmp.resolve("out.fluxtion-settings");
        IOException refusal = assertThrows(IOException.class, () -> BundleProfile.export(input, out),
                "ambiguousEndRefused: do not export a partial path or swallow prose: " + path);
        assertTrue(refusal.getMessage().contains("report.0.s.0.text"), "refusal names the affected key");
        assertTrue(refusal.getMessage().contains("quote the complete path"), "refusal explains how to resolve ambiguity");
        assertFalse(Files.exists(out), "an ambiguous ending creates no partial export");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "/Users/DEMO王", "/home/DEMO/机密.yaml", "~/DEMO/機密", "~DEMO/機密", "file:///DEMO/機密",
            "C:\\DEMO\\機密", "\\\\DEMO\\share\\機密"
    })
    @DisplayName("#87 R1: explicitly quoted Unicode paths are removed whole through the exporter")
    void quotedUnicodePathsAreRemovedWhole(String path, @TempDir Path tmp) throws Exception {
        int fixture = 0;
        for (String quotes : List.of("\"\"", "''", "``", "“”", "「」", "『』", "‘’")) {
            var removed = new java.util.ArrayList<String>();
            Path dir = Files.createDirectories(tmp.resolve("quoted-" + fixture++));
            String prefix = "ログは" + quotes.charAt(0);
            String separator = quotes.equals("''") || quotes.equals("‘’") ? " " : "";
            String suffix = quotes.charAt(1) + separator + "にあります";
            assertEquals(prefix + BundleProfile.REDACTED + suffix,
                    exportedNarrative(dir, prefix + path + suffix, removed),
                    "quotedPathRemovedWhole: the delimiter identifies the entire Unicode path: " + path);
            assertEquals(List.of("report.0.s.0.text: " + path), removed,
                    "the reported removal is exactly the path, without quotes or prose");
        }
    }

    static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> quotedProseCases() {
        String mark = BundleProfile.REDACTED;
        return java.util.stream.Stream.of(
                org.junit.jupiter.params.provider.Arguments.of(
                        "ログは\"/Users/DEMO/q.yaml にあります。詳細は\"設定\"を参照",
                        "ログは\"" + mark + " にあります。詳細は\"設定\"を参照", List.of("/Users/DEMO/q.yaml")),
                org.junit.jupiter.params.provider.Arguments.of(
                        "it's under '/Users/DEMO/x isn't it' fine",
                        "it's under '" + mark + " isn't it' fine", List.of("/Users/DEMO/x")),
                org.junit.jupiter.params.provider.Arguments.of(
                        "it’s under ‘/Users/DEMO/x isn’t it’ fine",
                        "it’s under ‘" + mark + " isn’t it’ fine", List.of("/Users/DEMO/x")),
                org.junit.jupiter.params.provider.Arguments.of(
                        "the log said \"/Users/DEMO/a.yaml (No such file or directory)\" and stopped",
                        "the log said \"" + mark + " (No such file or directory)\" and stopped", List.of("/Users/DEMO/a.yaml")),
                org.junit.jupiter.params.provider.Arguments.of(
                        "see \"/Users/DEMO/a.yaml /Users/DEMO/b.yaml\" today",
                        "see \"" + mark + " " + mark + "\" today", List.of("/Users/DEMO/a.yaml", "/Users/DEMO/b.yaml")),
                org.junit.jupiter.params.provider.Arguments.of(
                        "see \"/Users/DEMO/a.yaml 'aside'\" today",
                        "see \"" + mark + " 'aside'\" today", List.of("/Users/DEMO/a.yaml")),
                org.junit.jupiter.params.provider.Arguments.of(
                        "see \"/Users/DEMO/a.yaml！tail\" today",
                        "see \"" + mark + "！tail\" today", List.of("/Users/DEMO/a.yaml")));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource("quotedProseCases")
    void aQuotedSpanEndsAtThePath(String narrative, String expected, List<String> paths, @TempDir Path tmp) throws Exception {
        var removed = new java.util.ArrayList<String>();
        assertEquals(expected, exportedNarrative(tmp, narrative, removed),
                "quotedSpanEndsAtPath: surrounding prose must survive an unreliable quoted boundary");
        assertEquals(paths.stream().map(path -> "report.0.s.0.text: " + path).toList(), removed,
                "quotedRemovalIsExact: do not report sentence text as part of a removed path");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "/Users/DEMO/my logs/a.yaml", "~/DEMO/my logs/a.yaml", "~DEMO/my logs/a.yaml",
            "~123/my logs/a.yaml", "file:///DEMO/my logs/a.yaml", "C:\\DEMO\\my logs\\a.yaml",
            "\\\\DEMO\\share\\my logs\\a.yaml"
    })
    void aQuotedPathCanStillContainSpaces(String path, @TempDir Path tmp) throws Exception {
        var removed = new java.util.ArrayList<String>();
        assertEquals("see \"" + BundleProfile.REDACTED + "\" today",
                exportedNarrative(tmp, "see \"" + path + "\" today", removed), "a bounded quoted path may contain spaces");
        assertEquals(List.of("report.0.s.0.text: " + path), removed, "spaces within a quoted path are removed too");
    }

    @Test
    void theRefusalRecommendsAWorkingDelimiter(@TempDir Path tmp) throws Exception {
        String path = "/Users/DEMO/機密";
        Path input = withNarrative(tmp, "ログは" + path + "にあります");
        IOException refusal = assertThrows(IOException.class,
                () -> BundleProfile.export(input, tmp.resolve("refused.fluxtion-settings")), "ambiguous prose refuses");
        assertTrue(refusal.getMessage().contains("double quotes"), "refusal names a supported delimiter: double quotes");
        var removed = new java.util.ArrayList<String>();
        Path recovered = Files.createDirectories(tmp.resolve("recovered"));
        assertEquals("ログは\"" + BundleProfile.REDACTED + "\"にあります",
                exportedNarrative(recovered, "ログは\"" + path + "\"にあります", removed),
                "the spelling recommended by the refusal succeeds and keeps the prose");
        assertEquals(List.of("report.0.s.0.text: " + path), removed, "recovery removes precisely the path");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"ລາວ", "ខ្មែរ", "မြန်မာ"})
    void theUnquotedScriptLimitIsDisclosed(String suffix, @TempDir Path tmp) throws Exception {
        var removed = new java.util.ArrayList<String>();
        String path = "/Users/DEMO/q.yaml";
        assertEquals("ログは" + BundleProfile.REDACTED,
                exportedNarrative(tmp, "ログは" + path + suffix, removed),
                "documented limit: Lao, Khmer and Myanmar suffixes remain part of the unquoted path candidate");
        assertEquals(List.of("report.0.s.0.text: " + path + suffix), removed,
                "disclosure must state that these suffixes are currently removed, not preserved or refused");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"''", "‘’"})
    void aSingleQuotedUnicodePathNeedsASeparator(String quotes, @TempDir Path tmp) throws Exception {
        String path = "/Users/DEMO/機密";
        Path input = withNarrative(tmp, "ログは" + quotes.charAt(0) + path + quotes.charAt(1) + "にあります");
        IOException refusal = assertThrows(IOException.class,
                () -> BundleProfile.export(input, tmp.resolve("refused.fluxtion-settings")),
                "singleQuoteBeforeLetterIsNotABoundary: fall back to ambiguous-ending refusal");
        assertTrue(refusal.getMessage().contains("double quotes"), "the refusal offers a delimiter usable beside prose");
        assertFalse(Files.exists(tmp.resolve("refused.fluxtion-settings")), "an untrusted single-quote boundary writes nothing");
    }

}
