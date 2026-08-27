package telamin.fluxtion.audit.analyser.analyser.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * M37 — what is in force, as five sections of rows, built from the {@code context} payload and nothing
 * else (spec D-L1). This class is pure: no Swing, no MainFrame. It reads the same map the action socket
 * hands an agent, so the human at the canvas and the agent at the socket see one set of facts, and when
 * a fact is missing here the fix is to add it to {@code context} — which improves the agent too.
 *
 * <p>Every section has an empty state that is a sentence saying what would fill it (D-L2). A blank row is
 * a question the user has to go and answer somewhere else, which is the complaint the panel exists for.
 *
 * <p>{@link #KEYS_READ} names every context key this class touches, dotted; a test reads MainFrame's
 * source and proves each one is put. Add to it when you read a new key.
 */
public record ProjectModel(List<Section> sections) {

    /** A row: what it is, where it is (a path, copyable; may be null), where it came from, and how it should read. */
    public record Row(String primary, String secondary, String path, String provenance, Tone tone, Target target) { }

    public enum Tone { NORMAL, MUTED, WARN }

    /** Where a row's "go to" leads — navigation only (D-L3). */
    public enum Target { NONE, TOPOLOGY, SOURCE, SETTINGS_SOURCE, SETTINGS_PROCESSORS, SETTINGS_ASSISTANT, PROJECT, REPORTS }

    public record Section(String title, List<Row> rows) { }

    public static final Set<String> KEYS_READ = Set.of(
            "project.active", "project.name", "project.settings", "project.root",
            "log.path", "log.openedFrom", "log.records", "log.openedBy", "provenance", "files",
            "graphPairing.graph", "graphPairing.graphSource", "graphPairing.graphPath", "graphPairing.applies",
            "graphPairing.declaredByGraph", "graphPairing.loggedNodes", "graphPairing.verdict",
            "graphPairing.sourceGraphOffered", "graphPairing.sourceGraphNote",
            "graphPairing.auditLogging", "graphPairing.auditLoggingNote",
            "processors.class", "processors.selected", "processors.source", "processors.from",
            "source.rootTiers.path", "source.rootTiers.tier",
            "exports.enabled", "exports.dir", "reports.name", "reports.title", "reports.sections", "reports.from",
            "runbooks.name", "runbooks.path", "runbooks.resolved", "runbooks.exists", "runbooks.from",
            "vocabulary.path", "vocabulary.resolved", "vocabulary.exists", "vocabulary.from",
            "provenanceSource", "environments.name", "environments.provenance", "environments.logDir", "environments.default",
            "analyses.name", "analyses.rationale", "analyses.parameters", "analyses.steps", "analyses.from",
            "reportDestinations.name", "reportDestinations.location", "reportDestinations.kind", "reportDestinations.from",
            "source.rootTiers.form", "source.workspaceRoot", "source.workspaceDir");

    public static final String PROJECT = "Project", LOG = "Audit log", GRAPH = "Graph",
            PROCESSORS = "Event processors", ROOTS = "Source roots", REPORTS = "Reports", ANALYSES = "Analyses";

    @SuppressWarnings("unchecked")
    public static ProjectModel from(Map<String, Object> ctx) {
        if (ctx == null) ctx = Map.of();
        List<Section> out = new ArrayList<>();

        // ---- project ---------------------------------------------------------------------------------
        Map<String, Object> proj = map(ctx.get("project"));
        List<Row> rows = new ArrayList<>();
        if (Boolean.TRUE.equals(proj.get("active"))) {
            // the root is a PATH: abbreviated here (D-L8), because the second line otherwise wraps the full thing
            rows.add(new Row(str(proj.get("name")), abbreviate(str(proj.get("root"))), str(proj.get("settings")),
                    "project settings in force", Tone.NORMAL, Target.PROJECT));
        } else {
            rows.add(new Row("No project", "using your own settings (~/.fluxtion-analyser)", null, null,
                    Tone.MUTED, Target.NONE));
        }
        // M38.1 D-C7: a runbook POINTER is a visible row — "deploy runbook: ops/deploy.md · project". Copy and
        // Show act on where it lands on this machine; what is drawn is the pointer as the profile holds it.
        for (Object o : list(ctx.get("runbooks"))) {
            Map<String, Object> r = map(o);
            boolean known = r.get("exists") != null;
            boolean exists = Boolean.TRUE.equals(r.get("exists"));
            // the pointer goes on the WRAPPING line, where it is read whole; the eliding first line holds the name
            rows.add(new Row(r.get("name") + " runbook",
                    r.get("path") + (known && !exists ? " — file NOT found under the project root" : " — a pointer into the repository, never contents"),
                    str(r.get("resolved")), str(r.get("from")), known && !exists ? Tone.WARN : Tone.NORMAL, Target.NONE));
        }
        // M38.2: the glossary pointer — the same shape as a runbook row, because it is the same kind of thing
        Map<String, Object> vocab = map(ctx.get("vocabulary"));
        if (vocab.get("path") != null) {
            boolean known = vocab.get("exists") != null, exists = Boolean.TRUE.equals(vocab.get("exists"));
            rows.add(new Row("vocabulary", vocab.get("path") + (known && !exists
                    ? " — file NOT found under the project root"
                    : " — the domain glossary; its text is served to the assistant and in `context`"),
                    str(vocab.get("resolved")), str(vocab.get("from")), known && !exists ? Tone.WARN : Tone.NORMAL, Target.NONE));
        }
        // M38.3: the environments the project declares — one row each, the default marked
        for (Object o : list(ctx.get("environments"))) {
            Map<String, Object> e = map(o);
            String detail = "stamps “" + e.get("provenance") + "”"
                    + (e.get("logDir") != null ? " on logs under " + e.get("logDir") : "")
                    + (Boolean.TRUE.equals(e.get("default")) ? " · default when nothing else applies" : "");
            rows.add(new Row("environment " + e.get("name"), detail, null, "project", Tone.NORMAL, Target.NONE));
        }
        out.add(new Section(PROJECT, rows));

        // ---- audit log -------------------------------------------------------------------------------
        Map<String, Object> log = map(ctx.get("log"));
        rows = new ArrayList<>();
        if (log.isEmpty()) {
            rows.add(new Row("No log loaded", "File ▸ Open, drag a file in, or open {path} from the socket",
                    null, null, Tone.MUTED, Target.NONE));
        } else {
            // Review C2: the ORIGIN the user named is the row — `s3://bucket/key`, not the temp file it was
            // fetched to. `path` is the local copy; for a local open the two are the same file.
            String local = str(log.get("path"));
            String origin = str(log.get("openedFrom"));
            String shown = origin != null ? origin : local;
            boolean remote = origin != null && local != null && !origin.equals(local);
            StringBuilder detail = new StringBuilder();
            if (log.get("records") != null) detail.append(log.get("records")).append(" records");
            if (remote) detail.append(detail.isEmpty() ? "" : " · ").append("fetched to a local copy");
            String prov = "opened by " + (log.get("openedBy") == null ? "you" : log.get("openedBy"));
            if (ctx.get("provenance") != null) {
                prov += " · from " + ctx.get("provenance");
                // M38.3: declared, never inferred — and the row says by whom, or which environment supplied it
                if (ctx.get("provenanceSource") != null) prov += " (" + ctx.get("provenanceSource") + ")";
            }
            rows.add(new Row(fileName(shown), detail.toString(), shown, prov, Tone.NORMAL, Target.NONE));
            List<Object> files = list(ctx.get("files"));
            if (files.size() > 1) {
                // members are display names, in load order — the set's directory is the row above
                for (Object f : files) {
                    rows.add(new Row(fileName(str(f)), "member of the rolled set", null, null, Tone.MUTED, Target.NONE));
                }
            }
        }
        out.add(new Section(LOG, rows));

        // ---- graph -----------------------------------------------------------------------------------
        Map<String, Object> pair = map(ctx.get("graphPairing"));
        rows = new ArrayList<>();
        if (pair.get("graph") == null) {
            rows.add(new Row("No graph", "File ▸ Open topology, or a reader may supply one with its log",
                    null, null, Tone.MUTED, Target.NONE));
        } else {
            String src = str(pair.get("graphSource"));
            String prov = switch (src == null ? "" : src) {
                case "OPENED" -> "opened by you";
                case "READER_DECLARED" -> "supplied by the reader (declared)";
                case "READER_INFERRED" -> "supplied by the reader (INFERRED)";
                default -> src;
            };
            String verdict;
            Tone tone = Tone.NORMAL;
            if (pair.get("applies") == null) {
                verdict = log.isEmpty() ? "no log to pair with" : "not yet paired";
                tone = Tone.MUTED;
            } else if (Boolean.TRUE.equals(pair.get("applies"))) {
                verdict = "applies — " + pair.get("declaredByGraph") + "/" + pair.get("loggedNodes")
                        + " logged nodes declared by the graph";
            } else {
                verdict = "⚠ does not fit this log — " + pair.get("verdict");
                tone = Tone.WARN;
            }
            rows.add(new Row(str(pair.get("graph")), verdict, str(pair.get("graphPath")), prov, tone, Target.TOPOLOGY));
            // M40 (review F2): the human surface the CHANGELOG and the docs page already promised and
            // this milestone had not built. A processor with no audit logging installed writes nothing
            // at all, so this outranks the pairing verdict above it — pairing a log that will never
            // exist is a question about nothing.
            if ("not_enabled".equals(str(pair.get("auditLogging")))) {
                rows.add(new Row("⚠ audit logging NOT installed",
                        "this processor writes no audit log at all — addEventAudit() is missing from the "
                                + "graph builder", null, "read from the graph", Tone.WARN, Target.NONE));
            }
            if (pair.get("sourceGraphOffered") != null) {
                rows.add(new Row(str(pair.get("sourceGraphOffered")), "the reader's graph — not shown: opened beats supplied",
                        null, "supplied by the reader", Tone.MUTED, Target.NONE));
            }
        }
        // Review N1: the reader TRIED to supply a graph and could not — sourceGraphNote says why (M34 review F2)
        if (pair.get("sourceGraphNote") != null) {
            rows.add(new Row("No graph from the reader", str(pair.get("sourceGraphNote")), null, null, Tone.MUTED, Target.NONE));
        }
        out.add(new Section(GRAPH, rows));

        // ---- processors ------------------------------------------------------------------------------
        rows = new ArrayList<>();
        for (Object o : list(ctx.get("processors"))) {
            Map<String, Object> p = map(o);
            boolean selected = Boolean.TRUE.equals(p.get("selected"));
            boolean found = "found".equals(p.get("source"));
            String detail = (selected ? "selected · " : "") + (found ? "source found" : "source NOT found under any root");
            // the class name leads and the package is the second line's tail — a 40-character FQN at the
            // west column's width is a row of "com.acme.demo.generated.DemoQuoteProces…" with the name cut off
            String fqn = str(p.get("class"));
            int dot = fqn == null ? -1 : fqn.lastIndexOf('.');
            String simple = dot < 0 ? fqn : fqn.substring(dot + 1);
            if (dot > 0) detail += " · " + fqn.substring(0, dot);
            rows.add(new Row(simple, detail, null, str(p.get("from")),
                    found ? (selected ? Tone.NORMAL : Tone.MUTED) : Tone.WARN, found ? Target.SOURCE : Target.SETTINGS_PROCESSORS));
        }
        if (rows.isEmpty()) {
            rows.add(new Row("No event processors", "Settings ▸ Event processor, or open a log and one is inferred",
                    null, null, Tone.MUTED, Target.SETTINGS_PROCESSORS));
        }
        out.add(new Section(PROCESSORS, rows));

        // ---- roots -----------------------------------------------------------------------------------
        rows = new ArrayList<>();
        Map<String, Object> source = map(ctx.get("source"));
        boolean inProject = Boolean.TRUE.equals(proj.get("active"));
        for (Object o : list(source.get("rootTiers"))) {
            Map<String, Object> r = map(o);
            String tier = str(r.get("tier"));
            String form = str(r.get("form"));
            // M38.6 D-C9: the stored form is the badge. Under a project, "absolute" means this profile is
            // correct on this machine and no other; "~" means correct for this person and no other.
            boolean notPortable = inProject && form != null && (form.equals("absolute") || form.equals("~"))
                    && !(tier != null && tier.startsWith("demo"));
            String detail = form == null ? null : "stored as " + form
                    + (notPortable ? " — this profile will not resolve it on a colleague's machine; declare a workspace anchor or move it under the project" : "");
            rows.add(new Row(str(r.get("path")), detail, str(r.get("path")), tier,
                    tier != null && tier.startsWith("demo") ? Tone.MUTED : notPortable ? Tone.WARN : Tone.NORMAL, Target.SETTINGS_SOURCE));
        }
        if (source.get("workspaceRoot") != null) {
            rows.add(new Row("workspace anchor " + source.get("workspaceRoot"),
                    "roots under it are stored relative to the project (with '..'), so a sibling checkout travels",
                    str(source.get("workspaceDir")), "project", Tone.MUTED, Target.NONE));
        }
        if (rows.isEmpty()) {
            rows.add(new Row("No source roots", "Settings ▸ Source — without one, no log line can reach its code",
                    null, null, Tone.MUTED, Target.SETTINGS_SOURCE));
        }
        out.add(new Section(ROOTS, rows));

        // ---- reports (M37.6): where files leave, and what the project has saved -----------------------
        rows = new ArrayList<>();
        Map<String, Object> exports = map(ctx.get("exports"));
        if (Boolean.TRUE.equals(exports.get("enabled")) && exports.get("dir") != null) {
            rows.add(new Row("Exports to " + fileName(str(exports.get("dir"))), "screenshots, PDF/CSV exports and rendered reports land here",
                    str(exports.get("dir")), "own settings", Tone.NORMAL, Target.SETTINGS_ASSISTANT));
        } else {
            rows.add(new Row("File exchange off", "Settings ▸ Assistant — until it is on, an agent's screenshot, export and report writes are refused",
                    null, null, Tone.MUTED, Target.SETTINGS_ASSISTANT));
        }
        List<Object> reps = list(ctx.get("reports"));
        for (Object o : reps) {
            Map<String, Object> r = map(o);
            Object n = r.get("sections");
            String detail = (n == null ? "0" : n) + " section" + ("1".equals(String.valueOf(n)) ? "" : "s") + " · saved report";
            rows.add(new Row(str(r.get("title") != null ? r.get("title") : r.get("name")), detail, null, str(r.get("from")),
                    Tone.NORMAL, Target.REPORTS));
        }
        if (reps.isEmpty()) {
            rows.add(new Row("No saved reports", "Reports tab ▸ New report, or report {…} from the socket",
                    null, null, Tone.MUTED, Target.REPORTS));
        }
        // M38.5: where reports are published — a place the publisher acts on; the analyser only states it.
        // Copy gives the location; Show only for a directory that exists on this machine.
        for (Object o : list(ctx.get("reportDestinations"))) {
            Map<String, Object> d = map(o);
            String loc = str(d.get("location"));
            rows.add(new Row("publish to " + d.get("name"), loc + " · " + d.get("kind") + " · the analyser states it; the publisher acts",
                    loc, str(d.get("from")), Tone.NORMAL, Target.NONE));
        }
        out.add(new Section(REPORTS, rows));

        // ---- analyses (M38.4): the offer, stated. Recall lives in File ▸ Run analysis and open {analysis} —
        // not here, because a button that runs verbs would change what the app shows (D-L3) ------------------
        rows = new ArrayList<>();
        for (Object o : list(ctx.get("analyses"))) {
            Map<String, Object> a = map(o);
            List<Object> params = list(a.get("parameters"));
            String detail = (a.get("rationale") == null || str(a.get("rationale")).isBlank() ? "" : a.get("rationale") + " · ")
                    + list(a.get("steps")).size() + " step" + (list(a.get("steps")).size() == 1 ? "" : "s")
                    + (params.isEmpty() ? "" : " · needs " + params.stream().map(p -> str(map(p).get("name"))).toList())
                    + " · File ▸ Run analysis";
            rows.add(new Row(str(a.get("name")), detail, null, str(a.get("from")), Tone.NORMAL, Target.NONE));
        }
        if (rows.isEmpty()) {
            rows.add(new Row("No saved analyses", "declare one in the project profile (analysis.N.*) — a named sequence of analyser "
                    + "verbs with its reason; recall it from File ▸ Run analysis or open {analysis}", null, null, Tone.MUTED, Target.NONE));
        }
        out.add(new Section(ANALYSES, rows));
        return new ProjectModel(List.copyOf(out));
    }

    public Section section(String title) {
        for (Section s : sections) if (s.title().equals(title)) return s;
        throw new IllegalArgumentException(title);
    }

    // ---- helpers: a map from the socket is loosely typed; read it defensively --------------------

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object o) {
        return o instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Object o) {
        return o instanceof List<?> l ? (List<Object>) l : List.of();
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    /**
     * Review C4 — support screenshot this application, and this is its most path-dense surface. What is
     * DRAWN is abbreviated: {@code $HOME} becomes {@code ~}, and a long path keeps its head and its last
     * two segments with the middle elided. The full value stays behind Copy and the tooltip.
     */
    public static String abbreviate(String path) {
        return abbreviate(path, System.getProperty("user.home"), 44);
    }

    static String abbreviate(String path, String home, int max) {
        if (path == null) return null;
        String p = path;
        if (home != null && !home.isBlank() && p.startsWith(home)) p = "~" + p.substring(home.length());
        if (p.length() <= max) return p;
        String sep = p.contains("/") ? "/" : "\\";
        String[] parts = p.split(java.util.regex.Pattern.quote(sep));
        if (parts.length < 4) return p.substring(0, Math.max(1, max - 1)) + "…";
        String tail = parts[parts.length - 2] + sep + parts[parts.length - 1];
        String head = parts[0].isEmpty() ? sep : parts[0] + sep;
        if (head.length() + tail.length() + 1 > max) return "…" + p.substring(p.length() - (max - 1));
        return head + "…" + sep + tail;
    }

    static String fileName(String path) {
        if (path == null) return "?";
        int i = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return i >= 0 && i < path.length() - 1 ? path.substring(i + 1) : path;
    }
}
