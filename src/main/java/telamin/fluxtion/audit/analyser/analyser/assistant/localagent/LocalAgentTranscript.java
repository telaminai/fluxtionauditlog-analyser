package telamin.fluxtion.audit.analyser.analyser.assistant.localagent;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The words of the local agent's conversation, by reference (spec §4.1 "a bounded store, the AssistantTranscript
 * pattern"). {@code localAgentSession} decides which references are in the conversation; this keeps what each says.
 * Unlike the in-app transcript an answer's entry is REPLACED as it streams, so the provisional text and the final text
 * are one entry, never two.
 *
 * <p><b>Bounded</b>: at most {@link #MAX_ENTRIES} entries and {@link #MAX_CHARS} characters; the oldest text is
 * dropped first and reads as {@link #TRIMMED}. Memory only: nothing here is written to a project, a setting, the
 * session audit record or a bundle. It never holds account data: the header record has no field for it.
 */
public final class LocalAgentTranscript {

    public static final int MAX_ENTRIES = 2_000;
    public static final int MAX_CHARS = 2_000_000;
    public static final String TRIMMED = "[trimmed: the conversation store is bounded]";

    public enum Kind { USER, ANSWER, DETAIL, STDERR }

    /**
     * The tab's header details (§5.2): the model, the CLI version, the subscription type or API-key source, the API
     * provider, and the built-in commands the slash table does not classify. Never an e-mail or an organisation.
     */
    public record Header(String model, String cliVersion, String billing, String apiProvider, List<String> unclassified,
                         String folder, String configuredModel) {
        public Header {
            unclassified = List.copyOf(unclassified == null ? List.of() : unclassified);
        }

        public Header(String model, String cliVersion, String billing, String apiProvider, List<String> unclassified,
                      String folder) {
            this(model, cliVersion, billing, apiProvider, unclassified, folder, null);
        }

        /** {@code folder} is the session's OWN launch directory, as the person should read it (review S5). */
        public Header withInit(String model, String cliVersion) {
            return new Header(model == null ? this.model : model, cliVersion == null ? this.cliVersion : cliVersion,
                    billing, apiProvider, unclassified, folder, configuredModel);
        }
    }

    private record Text(Kind kind, String text) { }

    private final AtomicLong next = new AtomicLong(1);
    private final LinkedHashMap<Long, Text> texts = new LinkedHashMap<>();
    private final Map<Long, Header> headers = new LinkedHashMap<>();
    private long chars;

    public synchronized long add(Kind kind, String text) {
        long id = next.getAndIncrement();
        String t = text == null ? "" : text;
        texts.put(id, new Text(kind, t));
        chars += t.length();
        bound();
        return id;
    }

    /** Replace the words of {@code id} (a streaming answer); a trimmed or unknown id is left alone. */
    public synchronized void replace(long id, String text) {
        Text was = texts.get(id);
        if (was == null) return;
        String t = text == null ? "" : text;
        chars += t.length() - was.text().length();
        texts.put(id, new Text(was.kind(), t));
        bound();
    }

    public synchronized String text(long id) {
        Text t = texts.get(id);
        if (t == null) return id > 0 && id < next.get() ? TRIMMED : "";
        return t.text();
    }

    public synchronized long putHeader(Header header) {
        long id = next.getAndIncrement();
        headers.put(id, header);
        while (headers.size() > 16) headers.remove(headers.keySet().iterator().next());
        return id;
    }

    public synchronized Header header(long id) {
        return headers.get(id);
    }

    public synchronized int size() {
        return texts.size();
    }

    /** Every stored word, for the tests that prove what never arrives here. */
    public synchronized String dump() {
        StringBuilder sb = new StringBuilder();
        for (Text t : texts.values()) sb.append(t.text()).append('\n');
        for (Header h : headers.values()) sb.append(h).append('\n');
        return sb.toString();
    }

    private void bound() {
        Iterator<Map.Entry<Long, Text>> it = texts.entrySet().iterator();
        while ((texts.size() > MAX_ENTRIES || chars > MAX_CHARS) && it.hasNext()) {
            Map.Entry<Long, Text> oldest = it.next();
            chars -= oldest.getValue().text().length();
            it.remove();
        }
    }
}
