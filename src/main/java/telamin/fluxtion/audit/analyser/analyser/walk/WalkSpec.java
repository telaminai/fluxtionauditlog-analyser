package telamin.fluxtion.audit.analyser.analyser.walk;

import telamin.fluxtion.audit.analyser.analyser.report.LogFingerprint;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * M69 — a saved spotlight walk: a named, ordered list of steps, each restoring a view from a fixed allow-list and
 * lighting up to six targets with their captions ({@code docs/specs/completed/spec-spotlight-walks.md} §1, §3).
 *
 * <p><b>A walk is testimony.</b> It holds pointers and the author's words, never evidence: each target records the
 * {@link Basis} it was written against, so playback can say whether it is still current, known to be historical,
 * or unresolved (§3.5). Its author is declared, never authenticated (§3.2).
 *
 * @param name        the walk's name, following chart-name rules so it is also an address
 * @param title       a display title, or "" to use the name
 * @param author      {@code assistant}, {@code person}, or the text an imported walk carried
 * @param createdAt   ISO instant of creation
 * @param updatedAt   ISO instant of the last change to its steps
 * @param fingerprint the log's coarse fingerprint at save — a mismatch warning, never a basis for "current"
 * @param runBasis    the loaded log files' SHA-256 digests at save, in load order; empty when unknown
 * @param steps       the steps, at least one
 * @param extras      keys under this walk that this version does not understand, carried over on rewrite
 * @param conversation OA-3: the walk's dialogue, or null — a walk without dialogue is exactly what it was before
 */
public record WalkSpec(String name, String title, String author, String createdAt, String updatedAt,
                       LogFingerprint fingerprint, List<String> runBasis, List<Step> steps,
                       Map<String, String> extras, Conversation conversation) {

    /** A walk without dialogue — every walk before OA-3, and most walks after it. */
    public WalkSpec(String name, String title, String author, String createdAt, String updatedAt,
                    LogFingerprint fingerprint, List<String> runBasis, List<Step> steps, Map<String, String> extras) {
        this(name, title, author, createdAt, updatedAt, fingerprint, runBasis, steps, extras, null);
    }

    /** At most this many steps: a walk is an argument, not a transcript. */
    public static final int MAX_STEPS = 50;

    public static final String AUTHOR_ASSISTANT = "assistant";
    public static final String AUTHOR_PERSON = "person";

    public WalkSpec {
        name = name == null ? "" : name.trim();
        title = title == null ? "" : title.trim();
        author = author == null || author.isBlank() ? AUTHOR_ASSISTANT : author.trim();
        createdAt = createdAt == null ? "" : createdAt;
        updatedAt = updatedAt == null ? createdAt : updatedAt;
        runBasis = List.copyOf(runBasis == null ? List.of() : runBasis);
        steps = List.copyOf(steps == null ? List.of() : steps);
        extras = Map.copyOf(extras == null ? Map.of() : extras);
    }

    /** The title shown, falling back to the name. */
    public String displayTitle() {
        return title.isBlank() ? name : title;
    }

    /** The same walk under a new name; its steps and bases are kept. */
    public WalkSpec renamed(String to) {
        return new WalkSpec(to, title, author, createdAt, updatedAt, fingerprint, runBasis, steps, extras, conversation);
    }

    /** How the strip names the author (§3.2): declared, and never "you". */
    public String authorLabel() {
        return switch (author) {
            case AUTHOR_ASSISTANT -> "by the assistant";
            case AUTHOR_PERSON -> "by a person";
            default -> "by " + author + " (declared)";
        };
    }

    // ---- steps ------------------------------------------------------------------------------------------------

    /**
     * One stop: the view it restores, its targets, and an optional sentence for the step as a whole.
     *
     * @param id      OA-3: a stable id, kept through rename, reorder and replace, so dialogue binds to THIS step and not
     *                to a position; null when the walk has no dialogue
     * @param through OA-3: the id of the last conversation turn visible at this step, or null (no dialogue revealed yet)
     */
    public record Step(String caption, View view, List<Target> targets, String id, String through) {
        public Step {
            caption = caption == null ? "" : caption.trim();
            view = view == null ? View.NONE : view;
            targets = List.copyOf(targets == null ? List.of() : targets);
            id = id == null || id.isBlank() ? null : id.trim();
            through = through == null || through.isBlank() ? null : through.trim();
        }

        public Step(String caption, View view, List<Target> targets) {
            this(caption, view, targets, null, null);
        }

        /** The same step with its dialogue binding replaced. */
        public Step withBinding(String id, String through) {
            return new Step(caption, view, targets, id, through);
        }
    }

    // ---- OA-3: dialogue (spec-onboard-assistant-journeys.md §6.1) -------------------------------------------------

    /** Kinds of dialogue: written as a script, captured from a live chat, or captured and then edited. */
    public static final String SCRIPTED = "scripted", RECORDED = "recorded", EDITED_RECORDING = "edited-recording";

    /** The one conversation schema version this build reads and writes. */
    public static final int CONVERSATION_VERSION = 1;

    /**
     * A walk's dialogue: an ordered list of turns, each attributed by role. It is TESTIMONY, as a caption is: shown beside
     * what the analyser actually shows, never executed and never presented as a live model's output. {@code author} is
     * declared, never authenticated.
     *
     * @param version   the schema version; a version this build does not know is kept for round trips but never played
     * @param kind      {@link #SCRIPTED}, {@link #RECORDED} or {@link #EDITED_RECORDING}
     * @param author    who says they wrote or recorded it, as declared; "" when unstated
     * @param turns     the turns, in order; empty for an unsupported version
     */
    public record Conversation(int version, String kind, String author, List<Turn> turns) {
        public Conversation {
            kind = kind == null ? "" : kind.trim();
            author = author == null ? "" : author.trim();
            turns = List.copyOf(turns == null ? List.of() : turns);
        }

        public boolean supported() {
            return version == CONVERSATION_VERSION;
        }

        /** The index of turn {@code id}, or -1. */
        public int indexOf(String id) {
            for (int i = 0; i < turns.size(); i++) if (turns.get(i).id().equals(id)) return i;
            return -1;
        }

        /** How the header names this dialogue: never "live". */
        public String label() {
            return switch (kind) {
                case RECORDED -> "Recorded conversation";
                case EDITED_RECORDING -> "Edited recorded conversation";
                default -> "Simulated conversation";
            };
        }
    }

    /** One turn: its stable id, {@code user} or {@code assistant}, and its plain text. */
    public record Turn(String id, String role, String text) {
        public Turn {
            id = id == null ? "" : id.trim();
            role = role == null ? "" : role.trim();
            text = text == null ? "" : text;
        }
    }

    /**
     * The view a step restores — ONLY these fields (§3.3). A null field leaves that part of the view as it is; the
     * filter, when present, is always complete.
     */
    public record View(String tab, Filter filter, Integer record, String graph, FocusRef focus) {
        public static final View NONE = new View(null, null, null, null, null);

        public View {
            tab = blankToNull(tab);
            graph = blankToNull(graph);
        }

        public boolean isEmpty() {
            return tab == null && filter == null && record == null && graph == null && focus == null;
        }
    }

    /**
     * A complete filter (§3.3): every field stated. {@code dimensions} null means all; {@code groupMode} is
     * {@code DIMENSION} or {@code RAW_EVENT}, applied before the dimensions.
     */
    public record Filter(Long from, Long to, String groupMode, List<String> dimensions, String text) {
        public static final Filter ALL = new Filter(null, null, "DIMENSION", null, "");

        public Filter {
            groupMode = groupMode == null || groupMode.isBlank() ? "DIMENSION" : groupMode.trim();
            dimensions = dimensions == null ? null : List.copyOf(new java.util.TreeSet<>(dimensions));
            text = text == null ? "" : text;
        }
    }

    /** A named topology focus, bound to its definition's digest (§3.5): a new definition under the name is not it. */
    public record FocusRef(String name, String digest) {
        public FocusRef {
            name = name == null ? "" : name.trim();
            digest = digest == null ? "" : digest.trim();
        }
    }

    /** One lit thing: a spotlight address, its caption, and what it was written against. */
    public record Target(String target, String caption, Basis basis) {
        public Target {
            target = target == null ? "" : target.trim();
            caption = caption == null ? "" : caption.trim();
            basis = basis == null ? Basis.NONE : basis;
        }
    }

    /**
     * What a target was written against (§3.5). {@code kind}: {@code record} (a record's raw-text digest),
     * {@code chart} (the chart definition's digest, qualified by the walk's run basis), {@code graph} (the topology
     * graph's digest), or {@code none} (a UI region with nothing to be stale against). An empty {@code digest} means
     * the basis was unknown at save, and stays unknown.
     *
     * @param representation for a record: the store kind whose {@code rawText} was hashed, so a digest taken over a
     *                       different representation is never compared as if it were the same text
     */
    public record Basis(String kind, String digest, String representation) {
        public static final Basis NONE = new Basis("none", "", "");

        public Basis {
            kind = kind == null || kind.isBlank() ? "none" : kind.trim();
            digest = digest == null ? "" : digest.trim();
            representation = representation == null ? "" : representation.trim();
        }

        public boolean known() {
            return !digest.isEmpty();
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /** Every target in step order, for listing. */
    public List<Target> allTargets() {
        List<Target> out = new ArrayList<>();
        for (Step s : steps) out.addAll(s.targets());
        return out;
    }

    /** A copy with the steps replaced, the update time set, and everything else kept. */
    public WalkSpec withSteps(List<Step> newSteps, String now) {
        return new WalkSpec(name, title, author, createdAt, now, fingerprint, runBasis, newSteps, extras, conversation);
    }

    /** OA-3: the same walk and evidence with its dialogue and step bindings replaced; its bases are NOT rebound. */
    public WalkSpec withConversation(Conversation dialogue, List<Step> rebound, String now) {
        return new WalkSpec(name, title, author, createdAt, now, fingerprint, runBasis, rebound, extras, dialogue);
    }
}
