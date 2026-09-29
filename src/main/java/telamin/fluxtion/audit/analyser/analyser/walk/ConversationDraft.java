package telamin.fluxtion.audit.analyser.analyser.walk;

import java.util.ArrayList;
import java.util.List;

/**
 * OA-3 (spec §7) — the conversation a person is composing for a walk, before it is saved. Pure: the editor dialog is a
 * surface over this, so the rules that decide what the dialogue IS — which turns may be taken from a live chat, what kind
 * of dialogue results, what leaves the machine — are tested without a frame.
 *
 * <ul>
 *   <li>Nothing is preselected: a turn joins only when the person adds it.</li>
 *   <li>Only a completed live turn can be captured, and only its visible words (the question and the answer); the
 *       hidden context, manifest and system prompt never were visible, so they never become dialogue.</li>
 *   <li>The kind follows what happened: all captured and unedited is {@code recorded}; any captured turn edited, or
 *       captured and written turns mixed, is {@code edited-recording}; none captured is {@code scripted}.</li>
 * </ul>
 */
public final class ConversationDraft {

    /** One turn in the draft: where it came from and whether its words were changed after capture. */
    public record Line(String role, String text, boolean captured, boolean edited) {
        public Line {
            role = role == null ? "user" : role;
            text = text == null ? "" : text;
        }
    }

    private final List<Line> lines = new ArrayList<>();
    private final List<String> through = new ArrayList<>();
    private String author = "";

    /** A draft for a walk of {@code steps} steps, starting from its saved dialogue (or none). */
    public ConversationDraft(WalkSpec walk) {
        WalkSpec.Conversation c = walk.conversation();
        if (c != null && c.supported()) {
            boolean captured = !WalkSpec.SCRIPTED.equals(c.kind());
            for (WalkSpec.Turn t : c.turns()) {
                lines.add(new Line(t.role(), t.text(), captured, WalkSpec.EDITED_RECORDING.equals(c.kind())));
            }
            author = c.author();
        }
        for (WalkSpec.Step s : walk.steps()) {
            int at = c == null || s.through() == null ? -1 : c.indexOf(s.through());
            through.add(at < 0 ? null : "t" + (at + 1));
        }
    }

    public List<Line> lines() {
        return List.copyOf(lines);
    }

    /** Add a turn captured from a COMPLETED live turn, as it was. */
    public void addCaptured(String role, String text) {
        lines.add(new Line(role, text, true, false));
    }

    /** Add a written (simulated) turn. */
    public void addWritten(String role, String text) {
        lines.add(new Line(role, text, false, false));
    }

    /** Change a turn's words; a captured turn becomes an edited one. */
    public void edit(int index, String text) {
        Line l = lines.get(index);
        if (l.text().equals(text)) return;
        lines.set(index, new Line(l.role(), text, l.captured(), l.captured() || l.edited()));
    }

    /** Remove a turn; any step that revealed up to it now reveals up to the one before it. */
    public void remove(int index) {
        lines.remove(index);
        for (int i = 0; i < through.size(); i++) {
            String t = through.get(i);
            if (t == null) continue;
            int n = Integer.parseInt(t.substring(1)) - 1;
            if (n == index) through.set(i, index == 0 ? null : "t" + index);
            else if (n > index) through.set(i, "t" + n);
        }
    }

    /** Step {@code step} reveals the conversation up to turn {@code turn} (0-based), or none with -1. */
    public void reveal(int step, int turn) {
        through.set(step, turn < 0 ? null : "t" + (turn + 1));
    }

    /** Per step, the turn it reveals up to, or null for none yet (so not List.copyOf, which refuses nulls). */
    public List<String> through() {
        return java.util.Collections.unmodifiableList(new ArrayList<>(through));
    }

    public void setAuthor(String author) {
        this.author = author == null ? "" : author.trim();
    }

    /** The kind the saved dialogue will carry, and be labelled with. */
    public String kind() {
        boolean anyCaptured = lines.stream().anyMatch(Line::captured);
        boolean anyWritten = lines.stream().anyMatch(l -> !l.captured());
        boolean anyEdited = lines.stream().anyMatch(Line::edited);
        if (!anyCaptured) return WalkSpec.SCRIPTED;
        if (anyEdited || anyWritten) return WalkSpec.EDITED_RECORDING;
        return WalkSpec.RECORDED;
    }

    /** The conversation to save, or null when the draft has no turns (the walk's dialogue is removed). */
    public WalkSpec.Conversation conversation() {
        if (lines.isEmpty()) return null;
        List<WalkSpec.Turn> turns = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) turns.add(new WalkSpec.Turn("t" + (i + 1), lines.get(i).role(), lines.get(i).text()));
        return new WalkSpec.Conversation(WalkSpec.CONVERSATION_VERSION, kind(), author, turns);
    }

    /**
     * Exactly what leaves the machine when this walk is shared or bundled (§7): the label, the declared author, and every
     * turn's words as they are. Nothing else from the chat goes with it.
     */
    public String exportPreview() {
        WalkSpec.Conversation c = conversation();
        if (c == null) return "No dialogue: nothing from any chat leaves with this walk.";
        StringBuilder out = new StringBuilder(c.label() + (c.author().isBlank() ? "" : " — declared author: " + c.author())
                + "\nThese words leave this machine with the walk when it is shared or bundled (machine paths in them are "
                + "removed on export and named). Nothing else from the chat goes with them: no hidden context, no system "
                + "prompt, no key.\n\n");
        for (WalkSpec.Turn t : c.turns()) out.append(t.id()).append("  ").append(t.role()).append(": ").append(t.text()).append("\n\n");
        return out.toString().strip();
    }
}
