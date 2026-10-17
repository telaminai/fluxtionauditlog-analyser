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

    public enum Kind { USER, ANSWER, DETAIL, STDERR,
        /** P2b's proposal words (Claude's summary, an item's reason, path or class name) and a chosen folder (R6). */
        PROPOSAL, PATH }

    /**
     * P3 (§5.7): one {@code can_use_tool} request's words, for the dialog — the tool's name as the CLI sent it, its MCP
     * server's name (null for a built-in), the display name the server gave it, and the input EXACTLY as the request
     * carried it. Never in a fact: facts name this by reference.
     */
    public record Permission(String toolName, String serverName, String displayName, String inputRaw) {
    }

    /**
     * The tab's header details (§5.2): the model, the CLI version, the subscription type or API-key source, the API
     * provider, and the built-in commands the slash table does not classify. Never an e-mail or an organisation.
     * {@code pathAdded}: in the full posture, the directories appended to the child's {@code PATH} (OD-74.11), as shown
     * ({@code ~/.local/bin}); empty in the default posture.
     */
    public record Header(String model, String cliVersion, String billing, String apiProvider, List<String> unclassified,
                         String folder, String configuredModel, List<String> pathAdded, boolean scratchForProject,
                         String endpoint) {
        public Header {
            unclassified = List.copyOf(unclassified == null ? List.of() : unclassified);
            pathAdded = List.copyOf(pathAdded == null ? List.of() : pathAdded);
        }

        /** The P2a shape: no endpoint observed. {@code endpoint} (P2b, §5.2) is {@code scheme://host[:port]} only. */
        public Header(String model, String cliVersion, String billing, String apiProvider, List<String> unclassified,
                      String folder, String configuredModel, List<String> pathAdded, boolean scratchForProject) {
            this(model, cliVersion, billing, apiProvider, unclassified, folder, configuredModel, pathAdded,
                    scratchForProject, null);
        }

        /** P1b's shape: not the scratch folder standing in for a project (OD-74.22). */
        public Header(String model, String cliVersion, String billing, String apiProvider, List<String> unclassified,
                      String folder, String configuredModel, List<String> pathAdded) {
            this(model, cliVersion, billing, apiProvider, unclassified, folder, configuredModel, pathAdded, false);
        }

        public Header(String model, String cliVersion, String billing, String apiProvider, List<String> unclassified,
                      String folder, String configuredModel) {
            this(model, cliVersion, billing, apiProvider, unclassified, folder, configuredModel, List.of());
        }

        public Header(String model, String cliVersion, String billing, String apiProvider, List<String> unclassified,
                      String folder) {
            this(model, cliVersion, billing, apiProvider, unclassified, folder, null);
        }

        /** {@code folder} is the session's OWN launch directory, as the person should read it (review S5). */
        public Header withInit(String model, String cliVersion) {
            return new Header(model == null ? this.model : model, cliVersion == null ? this.cliVersion : cliVersion,
                    billing, apiProvider, unclassified, folder, configuredModel, pathAdded, scratchForProject, endpoint);
        }
    }

    private record Text(Kind kind, String text) { }

    private final AtomicLong next = new AtomicLong(1);
    private final LinkedHashMap<Long, Text> texts = new LinkedHashMap<>();
    private final Map<Long, Header> headers = new LinkedHashMap<>();
    private final Map<Long, Permission> permissions = new LinkedHashMap<>();
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

    /** P3: keep a request's words; at most 64 are kept (one dialog at a time, and the CLI asks one at a time, P3-6). */
    public synchronized long putPermission(Permission p) {
        long id = next.getAndIncrement();
        permissions.put(id, p);
        while (permissions.size() > 64) permissions.remove(permissions.keySet().iterator().next());
        return id;
    }

    public synchronized Permission permission(long id) {
        return permissions.get(id);
    }

    public synchronized int size() {
        return texts.size();
    }

    /** Every stored word, for the tests that prove what never arrives here. */
    public synchronized String dump() {
        StringBuilder sb = new StringBuilder();
        for (Text t : texts.values()) sb.append(t.text()).append('\n');
        for (Header h : headers.values()) sb.append(h).append('\n');
        for (Permission p : permissions.values()) sb.append(p).append('\n');
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
