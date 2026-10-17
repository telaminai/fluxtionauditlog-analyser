package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.assistant.localagent.LocalAgentTranscript;
import telamin.fluxtion.audit.analyser.analyser.config.WalkPersistenceTestAccess;
import telamin.fluxtion.audit.analyser.analyser.session.AssistantMode;
import telamin.fluxtion.audit.analyser.analyser.session.LocalAgentState;
import telamin.fluxtion.audit.analyser.analyser.walk.ConversationDraft;
import telamin.fluxtion.audit.analyser.analyser.walk.WalkSpec;

import java.io.StringWriter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M74 P2b (spec §6, §10 P2): recording the Claude Code conversation into a walk. Only the person's sent turns and
 * Claude's completed answers are candidates; what is stored — and so what any bundle carries — holds no session id,
 * machine path, account detail, cost or budget, though the store the recording was made from holds all of them.
 */
class ConversationV2RecordingTest {

    static final String FOLDER = "/home/DEMO/work/secret-project";
    static final String SESSION = "0f8e7d6c-session-id";

    /** ★ Witness: carry the header's folder or billing into the Source — the leak assertions fail. */
    @Test
    @DisplayName("★ a v2 recording from a store holding a session id, a path, an account, a cost and a budget carries none of them")
    void nothingButTheWordsAndTheSource() throws Exception {
        LocalAgentTranscript store = new LocalAgentTranscript();
        long header = store.putHeader(new LocalAgentTranscript.Header("claude-sonnet-5", "2.1.282",
                "API key (ANTHROPIC_API_KEY)", "firstParty", List.of(), FOLDER, null, List.of(), false,
                "https://proxy.example:8443"));
        long q = store.add(LocalAgentTranscript.Kind.USER, "Where did DEMO first breach?");
        long ans = store.add(LocalAgentTranscript.Kind.ANSWER, "At record #16 — session " + SESSION.substring(0, 0) + "done.");
        long tool = store.add(LocalAgentTranscript.Kind.ANSWER, "a streaming answer that never completed");
        var entries = List.of(new LocalAgentState.Entry(q, LocalAgentState.USER, 1, "", "", 0),
                new LocalAgentState.Entry(ans, LocalAgentState.ANSWER, 1, "COMPLETED", "", 3),
                new LocalAgentState.Entry(0, LocalAgentState.NOTE, 1, "DENIED", "an action was denied", 0),
                new LocalAgentState.Entry(tool, LocalAgentState.ANSWER, 2, "STREAMING", "", 1));
        var state = new LocalAgentState(AssistantMode.LOCAL_AGENT, 1, 1, "READY", "", "", 0, 1, header, 1, 0, 0, 0,
                4_200_000, 5_000_000, false, false, true, 0, entries, LocalAgentState.Answer.NONE);
        var offer = ConversationEditor.localAgentCandidates(state, store);
        assertEquals(2, offer.turns().size(), "the sent turn and the completed answer only — no note, no streaming entry");
        WalkSpec walk = WalkPersistenceTestAccess.sample("journey");
        ConversationDraft draft = new ConversationDraft(walk);
        var h = store.header(header);
        draft.setSource(new WalkSpec.Source(WalkSpec.CLAUDE_CODE_SOURCE, h.model(), h.cliVersion()));
        for (var t : offer.turns()) draft.addCaptured(t.role(), t.text(), t.truncated());
        var c = draft.conversation();
        assertEquals(2, c.version());
        var stored = WalkPersistenceTestAccess.write(List.of(walk.withConversation(c,
                walk.steps(), walk.updatedAt())));
        StringWriter out = new StringWriter();
        stored.store(out, null);
        String text = out.toString();
        for (String leak : new String[]{FOLDER, "secret-project", SESSION, "ANTHROPIC_API_KEY", "API key", "4.20", "5.00",
                "proxy.example", "firstParty"}) {
            assertFalse(text.contains(leak), "leaked: " + leak);
        }
        assertTrue(text.contains("claude-code") && text.contains("claude-sonnet-5") && text.contains("2.1.282"));
        // and it round-trips as v2
        var back = WalkPersistenceTestAccess.read(stored);
        assertEquals(c, back.get(0).conversation());
    }

    @Test
    @DisplayName("a v1 walk round-trips byte for byte, writing no v2 key")
    void v1RoundTripsByteForByte() throws Exception {
        WalkSpec walk = WalkPersistenceTestAccess.sample("plain");
        ConversationDraft draft = new ConversationDraft(walk);
        draft.addWritten("user", "q");
        var once = WalkPersistenceTestAccess.write(List.of(walk.withConversation(draft.conversation(), walk.steps(),
                walk.updatedAt())));
        var twice = WalkPersistenceTestAccess.write(WalkPersistenceTestAccess.read(once));
        assertEquals(once, twice);
        assertTrue(once.stringPropertyNames().stream().noneMatch(k -> k.contains(".conv.src.") || k.endsWith(".truncated")));
        assertEquals("1", once.getProperty("walk.0.conv.v"));
    }
}
