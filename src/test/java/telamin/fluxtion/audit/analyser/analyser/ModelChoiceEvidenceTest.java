package telamin.fluxtion.audit.analyser.analyser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.llm.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The docs site's <i>Model choice</i> table may claim only what its committed run evidence shows (M72 L7).
 *
 * <h2>What is checked</h2>
 * The table in {@code docs/site/spring-authoring-getting-started.md} is parsed. Every row has one status from a
 * closed set — {@code meets acceptance} (with any {@code open} rungs), {@code advisory}, {@code negative},
 * {@code unmeasured} — and every row but {@code unmeasured} names an evidence folder and the field it reads. Runs
 * are selected from {@code <folder>/results/*}{@code /score.json} by {@code model} id; {@code -VOID-} runs and score
 * files with no {@code model} are excluded and listed on stdout. Then the row's check is applied:
 * <ul>
 *   <li><b>meets acceptance</b>: every claimed rung has {@code PASS} in at least 2 of 3 runs; every {@code open}
 *       rung is below that (an open rung that now passes is a stale claim); every rung in the evidence is stated;</li>
 *   <li><b>advisory</b>: no rung where the model passed and the <b>default</b> row's model is below 2 of 3;</li>
 *   <li><b>negative</b>: the field is false in every run of the model.</li>
 * </ul>
 *
 * <h2>Why a digest</h2>
 * The score files are not sealed by the evidence folders' {@code MANIFEST*.sha256} files — those seal predictions,
 * tasks and harness inputs before the runs. So {@code docs/specs/model-choice-evidence.sha256} holds one sha256 per
 * score file in every cited folder, and a mismatch fails: editing evidence then fails this test unless the digest is
 * changed in the same reviewed diff. Regenerate it with
 * {@code shasum -a 256 docs/handoff/evidence/<folder>/results/*}{@code /score.json} for each cited folder.
 *
 * <p>The witness tests below run the same check over a temporary copy and must find each wrong result: two
 * {@code PASS} values flipped on a claimed rung (red on the digest, then — with the digest regenerated — red on the
 * claim), a stale {@code open} rung, and a status outside the set. {@code -DmodelChoice.root=<dir>} points the main
 * test at another copy of the repository.
 */
class ModelChoiceEvidenceTest {

    static final Path PAGE = Path.of("docs/site/spring-authoring-getting-started.md");
    static final Path DIGEST = Path.of("docs/specs/model-choice-evidence.sha256");
    static final String HEADING = "### Model choice";
    static final Set<String> STATUSES = Set.of("meets acceptance", "advisory", "negative", "unmeasured");
    static final Set<String> QUALIFIERS = Set.of("open");

    private static final Pattern BACKTICK = Pattern.compile("`([^`]+)`");
    private static final Pattern MODEL_ID = Pattern.compile("`(claude-[a-z0-9-]+)`");
    private static final Pattern RUNGS = Pattern.compile("^\\s*on rungs?\\s+([0-9][0-9\\s,–-]*(?:and\\s+[0-9]+)?)");

    record Row(String label, String modelId, String status, boolean isDefault, Set<Integer> claimed, Set<Integer> open,
               String folder, String field, boolean perRung) {
    }

    record Run(String name, String model, Integer rung, Object json) {
    }

    static final class Report {
        final List<String> failures = new ArrayList<>();
        final List<String> excluded = new ArrayList<>();
        final List<Row> rows = new ArrayList<>();
    }

    // ------------------------------------------------------------------------------------------------- the check

    static Report check(Path root) throws IOException {
        Report report = new Report();
        List<Row> rows = parseTable(Files.readString(root.resolve(PAGE)), report);
        report.rows.addAll(rows);
        String defaultModel = rows.stream().filter(Row::isDefault).map(Row::modelId).findFirst().orElse(null);
        if (rows.stream().filter(Row::isDefault).count() != 1) {
            report.failures.add("exactly one row must be marked **default**");
        }
        Set<String> folders = new TreeSet<>();
        for (Row row : rows) {
            if (row.status() == null || row.status().equals("unmeasured")) continue;   // exempt: may cite nothing
            if (row.folder() == null || row.field() == null || row.modelId() == null) {
                report.failures.add(row.label() + ": a " + row.status() + " row must name its model id, evidence folder and field");
                continue;
            }
            folders.add(row.folder());
            List<Run> runs = runs(root, row.folder(), report);
            List<Run> mine = runs.stream().filter(r -> row.modelId().equals(r.model())).toList();
            if (mine.isEmpty()) {
                report.failures.add(row.label() + ": no runs of " + row.modelId() + " in " + row.folder());
                continue;
            }
            switch (row.status()) {
                case "meets acceptance" -> checkAcceptance(row, mine, report);
                case "advisory" -> checkAdvisory(row, runs, defaultModel, report);
                case "negative" -> {
                    for (Run r : mine) {
                        Object v = field(r.json(), row.field());
                        if (!Boolean.FALSE.equals(v)) {
                            report.failures.add(row.label() + ": negative claim does not hold: " + row.field() + " = " + v
                                    + " in " + r.name());
                        }
                    }
                }
                default -> report.failures.add(row.label() + ": status outside the set: " + row.status());
            }
        }
        checkDigest(root, folders, report);
        return report;
    }

    private static void checkAcceptance(Row row, List<Run> mine, Report report) {
        if (!row.perRung() || !row.field().equals("PASS")) {
            report.failures.add(row.label() + ": meets acceptance reads PASS per rung, not " + row.field());
            return;
        }
        Map<Integer, int[]> byRung = rates(mine, row.field(), report, row.label());
        for (var e : byRung.entrySet()) {
            int rung = e.getKey(), pass = e.getValue()[0], n = e.getValue()[1];
            boolean meets = pass * 3 >= n * 2;
            if (row.claimed().contains(rung) && !meets) {
                report.failures.add(row.label() + ": claims rung " + rung + " but PASS is " + pass + "/" + n + " (below 2/3)");
            } else if (row.open().contains(rung) && meets) {
                report.failures.add(row.label() + ": rung " + rung + " is stated open but PASS is " + pass + "/" + n
                        + " — a stale claim");
            } else if (!row.claimed().contains(rung) && !row.open().contains(rung)) {
                report.failures.add(row.label() + ": rung " + rung + " is in the evidence but the row does not state it");
            }
        }
        Set<Integer> stated = new TreeSet<>(row.claimed());
        stated.addAll(row.open());
        stated.removeAll(byRung.keySet());
        if (!stated.isEmpty()) report.failures.add(row.label() + ": states rungs with no evidence: " + stated);
    }

    private static void checkAdvisory(Row row, List<Run> runs, String defaultModel, Report report) {
        if (defaultModel == null) return;   // already a failure
        Map<Integer, int[]> mine = rates(runs.stream().filter(r -> row.modelId().equals(r.model())).toList(),
                row.field(), report, row.label());
        Map<Integer, int[]> dflt = rates(runs.stream().filter(r -> defaultModel.equals(r.model())).toList(),
                row.field(), report, row.label());
        for (var e : mine.entrySet()) {
            int[] d = dflt.get(e.getKey());
            boolean passed = e.getValue()[0] > 0;
            boolean defaultBelow = d == null || d[0] * 3 < d[1] * 2;
            if (passed && defaultBelow) {
                report.failures.add(row.label() + ": advisory claim does not hold: rung " + e.getKey() + " passed with "
                        + row.modelId() + " while " + defaultModel + " is " + (d == null ? "unmeasured" : d[0] + "/" + d[1]));
            }
        }
    }

    private static Map<Integer, int[]> rates(List<Run> runs, String field, Report report, String label) {
        Map<Integer, int[]> byRung = new TreeMap<>();
        for (Run r : runs) {
            if (r.rung() == null) {
                report.failures.add(label + ": " + r.name() + " has no rung, so PASS per rung cannot be read");
                continue;
            }
            int[] c = byRung.computeIfAbsent(r.rung(), k -> new int[2]);
            if (Boolean.TRUE.equals(field(r.json(), field))) c[0]++;
            c[1]++;
        }
        return byRung;
    }

    static Object field(Object json, String dotted) {
        Object v = json;
        for (String part : dotted.split("\\.")) {
            if (!(v instanceof Map<?, ?> m)) return null;
            v = m.get(part);
        }
        return v;
    }

    static List<Run> runs(Path root, String folder, Report report) throws IOException {
        List<Run> runs = new ArrayList<>();
        Path results = root.resolve(folder).resolve("results");
        if (!Files.isDirectory(results)) {
            report.failures.add(folder + ": no results/ directory");
            return runs;
        }
        try (Stream<Path> dirs = Files.list(results)) {
            for (Path dir : dirs.filter(Files::isDirectory).sorted().toList()) {
                String name = dir.getFileName().toString();
                Path score = dir.resolve("score.json");
                if (name.contains("-VOID")) {
                    report.excluded.add(folder + "/" + name + " (VOID)");
                    continue;
                }
                if (!Files.isRegularFile(score) || Files.size(score) == 0) {
                    report.excluded.add(folder + "/" + name + " (no score.json)");
                    continue;
                }
                Object json;
                try {
                    json = Json.parse(Files.readString(score));
                } catch (RuntimeException e) {
                    report.excluded.add(folder + "/" + name + " (unparseable score.json)");
                    continue;
                }
                Object model = field(json, "model");
                if (!(model instanceof String m) || m.isBlank()) {
                    report.excluded.add(folder + "/" + name + " (no model)");
                    continue;
                }
                Object rung = field(json, "rung");
                runs.add(new Run(name, m, rung instanceof Number n ? n.intValue() : null, json));
            }
        }
        return runs;
    }

    // ------------------------------------------------------------------------------------------------- digest

    static Map<String, String> digests(Path root, Set<String> folders) throws IOException {
        Map<String, String> out = new TreeMap<>();
        for (String folder : folders) {
            Path results = root.resolve(folder).resolve("results");
            if (!Files.isDirectory(results)) continue;
            try (Stream<Path> dirs = Files.list(results)) {
                for (Path dir : dirs.filter(Files::isDirectory).sorted().toList()) {
                    Path score = dir.resolve("score.json");
                    if (Files.isRegularFile(score)) {
                        out.put(folder + "/results/" + dir.getFileName() + "/score.json", sha256(Files.readAllBytes(score)));
                    }
                }
            }
        }
        return out;
    }

    static String digestFile(Map<String, String> digests) {
        StringBuilder sb = new StringBuilder();
        digests.forEach((path, sha) -> sb.append(sha).append("  ").append(path).append('\n'));
        return sb.toString();
    }

    private static void checkDigest(Path root, Set<String> folders, Report report) throws IOException {
        Path file = root.resolve(DIGEST);
        if (!Files.isRegularFile(file)) {
            report.failures.add("missing digest file " + DIGEST);
            return;
        }
        Map<String, String> recorded = new LinkedHashMap<>();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (line.isBlank() || line.startsWith("#")) continue;
            String[] parts = line.trim().split("\\s+", 2);
            if (parts.length == 2) recorded.put(parts[1].trim(), parts[0].trim());
        }
        Map<String, String> actual = digests(root, folders);
        for (var e : actual.entrySet()) {
            String sha = recorded.get(e.getKey());
            if (sha == null) {
                report.failures.add("digest: " + e.getKey() + " is cited but not in " + DIGEST + " (expected line: "
                        + e.getValue() + "  " + e.getKey() + ")");
            } else if (!sha.equals(e.getValue())) {
                report.failures.add("digest: " + e.getKey() + " changed since " + DIGEST + " was written (now "
                        + e.getValue() + ")");
            }
        }
        for (String path : recorded.keySet()) {
            if (!actual.containsKey(path)) report.failures.add("digest: stale entry " + path + " (no such cited score file)");
        }
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // ------------------------------------------------------------------------------------------------- parsing

    static List<Row> parseTable(String page, Report report) {
        List<Row> rows = new ArrayList<>();
        int at = page.indexOf("\n" + HEADING + "\n");
        if (at < 0) {
            report.failures.add("no '" + HEADING + "' section in " + PAGE);
            return rows;
        }
        List<String> table = new ArrayList<>();
        for (String line : page.substring(at + HEADING.length() + 2).split("\n", -1)) {
            if (line.startsWith("#")) break;
            if (line.startsWith("|")) table.add(line);
            else if (!table.isEmpty()) break;
        }
        if (table.size() < 3) {
            report.failures.add("the Model choice section has no table");
            return rows;
        }
        List<String> header = cells(table.get(0)).stream().map(String::toLowerCase).toList();
        int iModel = header.indexOf("model"), iStatus = header.indexOf("status"), iFolder = header.indexOf("evidence folder"),
                iField = header.indexOf("field"), iCheck = header.indexOf("the test checks");
        if (Math.min(Math.min(iModel, iStatus), Math.min(Math.min(iFolder, iField), iCheck)) < 0) {
            report.failures.add("table header must be: model | status | evidence folder | field | the test checks; was " + header);
            return rows;
        }
        for (String line : table.subList(2, table.size())) {
            List<String> c = cells(line);
            if (c.size() != header.size()) {
                report.failures.add("malformed row: " + line);
                continue;
            }
            String label = c.get(iModel).replaceAll("\\s*\\(.*", "").trim();
            Matcher id = MODEL_ID.matcher(c.get(iModel));
            String status = null;
            Set<Integer> claimed = new LinkedHashSet<>(), open = new LinkedHashSet<>();
            Matcher t = BACKTICK.matcher(c.get(iStatus));
            while (t.find()) {
                String token = t.group(1);
                Set<Integer> rungs = rungsAfter(c.get(iStatus).substring(t.end()));
                if (STATUSES.contains(token)) {
                    if (status != null) report.failures.add(label + ": more than one status (" + status + ", " + token + ")");
                    status = token;
                    claimed.addAll(rungs);
                } else if (QUALIFIERS.contains(token)) {
                    open.addAll(rungs);
                } else {
                    report.failures.add(label + ": status outside the closed set: `" + token + "`");
                }
            }
            if (status == null) report.failures.add(label + ": no status from " + STATUSES);
            if ("meets acceptance".equals(status) && claimed.isEmpty()) {
                report.failures.add(label + ": meets acceptance must name its rungs (\"on rungs 3–5\")");
            }
            if (!open.isEmpty() && !"meets acceptance".equals(status)) {
                report.failures.add(label + ": `open` rungs belong only to a meets acceptance row");
            }
            Matcher folder = BACKTICK.matcher(c.get(iFolder));
            Matcher field = BACKTICK.matcher(c.get(iField));
            rows.add(new Row(label, id.find() ? id.group(1) : null, status, c.get(iStatus).contains("**default**"),
                    claimed, open, folder.find() ? folder.group(1) : null, field.find() ? field.group(1) : null,
                    c.get(iField).contains("per rung")));
        }
        return rows;
    }

    /** "on rungs 3–5", "on rung 2", "on rungs 2 and 4", "on rungs 2, 3" — the text right after a status token. */
    static Set<Integer> rungsAfter(String text) {
        Set<Integer> out = new LinkedHashSet<>();
        Matcher m = RUNGS.matcher(text);
        if (!m.find()) return out;
        for (String part : m.group(1).split(",|\\band\\b")) {
            String p = part.trim();
            if (p.isEmpty()) continue;
            String[] range = p.split("\\s*[–-]\\s*");
            int from = Integer.parseInt(range[0].trim());
            int to = range.length > 1 && !range[1].isBlank() ? Integer.parseInt(range[1].trim()) : from;
            for (int i = from; i <= to; i++) out.add(i);
        }
        return out;
    }

    private static List<String> cells(String line) {
        String s = line.trim();
        if (s.startsWith("|")) s = s.substring(1);
        if (s.endsWith("|")) s = s.substring(0, s.length() - 1);
        List<String> out = new ArrayList<>();
        for (String c : s.split("\\|", -1)) out.add(c.trim());
        return out;
    }

    static Path root() {
        return Path.of(System.getProperty("modelChoice.root", ""));
    }

    // ------------------------------------------------------------------------------------------------- tests

    @Test
    void modelChoiceTableMatchesItsEvidence() throws IOException {
        Report report = check(root());
        System.out.println("ModelChoiceEvidenceTest: " + report.rows.size() + " rows; excluded runs: " + report.excluded);
        assertTrue(report.rows.size() >= 4, "found only " + report.rows.size() + " rows — has the table moved? "
                + "A check that reads nothing passes for the wrong reason.");
        assertTrue(report.failures.isEmpty(), "Model choice table vs evidence:\n  " + String.join("\n  ", report.failures));
    }

    @Test
    void excludedRunsAreListedNotRead() throws IOException {
        Report report = check(root());
        assertTrue(report.excluded.stream().anyMatch(e -> e.contains("-VOID")), "a VOID run must be excluded and listed");
        assertTrue(report.excluded.stream().anyMatch(e -> e.endsWith("(no model)")), "a model-less score file must be listed");
    }

    // ---- witnesses: each wrong result must turn the check red

    private static final String BASELINE = "docs/handoff/evidence/authoring-ladder-baseline-2026-10-13";

    /** A temporary copy holding the page, the digest and every cited score file — nothing else is read. */
    private static Path copy(Path tmp) throws IOException {
        Path src = root();
        for (Path p : List.of(PAGE, DIGEST)) {
            Files.createDirectories(tmp.resolve(p).getParent());
            Files.copy(src.resolve(p), tmp.resolve(p));
        }
        Report probe = new Report();
        for (Row row : parseTable(Files.readString(src.resolve(PAGE)), probe)) {
            if (row.folder() == null) continue;
            Path results = src.resolve(row.folder()).resolve("results");
            try (Stream<Path> dirs = Files.list(results)) {
                for (Path dir : dirs.filter(Files::isDirectory).toList()) {
                    Path target = tmp.resolve(row.folder()).resolve("results").resolve(dir.getFileName().toString());
                    Files.createDirectories(target);
                    if (Files.isRegularFile(dir.resolve("score.json"))) {
                        Files.copy(dir.resolve("score.json"), target.resolve("score.json"));
                    }
                }
            }
        }
        return tmp;
    }

    private static void setPass(Path root, String run, boolean value) throws IOException {
        Path f = root.resolve(BASELINE).resolve("results").resolve(run).resolve("score.json");
        String s = Files.readString(f);
        String flipped = s.replaceAll("\"PASS\":\\s*" + !value, "\"PASS\": " + value);
        assertFalse(s.equals(flipped), run + " did not carry PASS " + !value);
        Files.writeString(f, flipped);
    }

    private static void regenerateDigest(Path root) throws IOException {
        Report r = new Report();
        Set<String> folders = new TreeSet<>();
        for (Row row : parseTable(Files.readString(root.resolve(PAGE)), r)) {
            if (row.folder() != null && !"unmeasured".equals(row.status())) folders.add(row.folder());
        }
        Files.writeString(root.resolve(DIGEST), digestFile(digests(root, folders)));
    }

    @Test
    void flippedPassOnAClaimedRungFailsOnTheDigestThenOnTheClaim(@TempDir Path tmp) throws IOException {
        Path root = copy(tmp);
        assertTrue(check(root).failures.isEmpty(), "the copy must start green: " + check(root).failures);
        setPass(root, "base-r3-h55-1", false);
        setPass(root, "base-r3-h55-2", false);
        List<String> first = check(root).failures;
        assertTrue(first.stream().anyMatch(f -> f.startsWith("digest:") && f.contains("base-r3-h55-1")), first.toString());

        regenerateDigest(root);
        List<String> second = check(root).failures;
        assertTrue(second.stream().noneMatch(f -> f.startsWith("digest:")), second.toString());
        assertTrue(second.stream().anyMatch(f -> f.contains("claims rung 3") && f.contains("1/3")), second.toString());
    }

    @Test
    void aStaleOpenRungFails(@TempDir Path tmp) throws IOException {
        Path root = copy(tmp);
        for (String run : List.of("base-r2-h55-1", "base-r2-h55-2", "base-r2-h55-3")) {
            Path f = root.resolve(BASELINE).resolve("results").resolve(run).resolve("score.json");
            Files.writeString(f, Files.readString(f).replaceAll("\"PASS\":\\s*false", "\"PASS\": true"));
        }
        regenerateDigest(root);
        List<String> failures = check(root).failures;
        assertTrue(failures.stream().anyMatch(f -> f.contains("rung 2 is stated open") && f.contains("stale")), failures.toString());
    }

    @Test
    void aStatusOutsideTheSetFails(@TempDir Path tmp) throws IOException {
        Path root = copy(tmp);
        Path page = root.resolve(PAGE);
        Files.writeString(page, Files.readString(page).replace("`advisory`: not recommended", "`recommended`: not advised"));
        List<String> failures = check(root).failures;
        assertTrue(failures.stream().anyMatch(f -> f.contains("status outside the closed set: `recommended`")), failures.toString());
    }

    @Test
    void aRowThatCitesNoFolderFails(@TempDir Path tmp) throws IOException {
        Path root = copy(tmp);
        Path page = root.resolve(PAGE);
        Files.writeString(page, Files.readString(page).replace("`docs/handoff/evidence/g14-model-grid-2026-10-11`", "—"));
        List<String> failures = check(root).failures;
        assertTrue(failures.stream().anyMatch(f -> f.contains("must name its model id, evidence folder and field")), failures.toString());
    }
}
