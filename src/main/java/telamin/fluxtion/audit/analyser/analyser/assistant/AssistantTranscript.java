package telamin.fluxtion.audit.analyser.analyser.assistant;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The words of the onboard assistant's conversations, by id (OA-1, spec §5: "large transcript bodies may be an immutable
 * append-only store referenced by snapshot IDs"). The {@code assistantLoop} node decides which ids are in a conversation,
 * in what order and with what status; this only keeps what each id says.
 *
 * <p>Append-only and memory-only (§3: chat text is not persisted in this delivery). Nothing here is written to a project,
 * a setting, the session audit record or a bundle; a walk's dialogue is a separate, explicitly selected copy (OA-3).
 */
public final class AssistantTranscript {

    /** What an entry is, for rendering and for composing a provider message. */
    public enum Kind { USER, PROMPT, ANSWER, ACTION, RESULT }

    public record Text(Kind kind, String text) { }

    private final AtomicLong next = new AtomicLong(1);
    private final ConcurrentHashMap<Long, Text> texts = new ConcurrentHashMap<>();

    /** Store {@code text} and return its id (never 0). Thread-safe. */
    public long add(Kind kind, String text) {
        long id = next.getAndIncrement();
        texts.put(id, new Text(kind, text == null ? "" : text));
        return id;
    }

    /** The text stored under {@code id}, or "" when there is none. */
    public String text(long id) {
        Text t = texts.get(id);
        return t == null ? "" : t.text();
    }

    public Kind kind(long id) {
        Text t = texts.get(id);
        return t == null ? null : t.kind();
    }

    /** How many entries have ever been stored (for tests and budgets). */
    public int size() {
        return texts.size();
    }
}
