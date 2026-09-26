package telamin.fluxtion.audit.analyser.analyser.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.parse.FollowIdentity;
import telamin.fluxtion.audit.analyser.analyser.parse.ReadThroughIdentity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** M68.5 (D-E6): the dispatcher observes the file before any record-reading verb is served. */
class ActionDispatcherReadIdentityTest {

    private static ActionDispatcher dispatcher(ReadThroughIdentity identity) {
        RenderExecutor render = new RenderExecutor() {
            @Override public ActionResult render(String action, Map<String, Object> params) {
                return ActionResult.ok(action, "result", Map.of("served", true));
            }
            @Override public ReadThroughIdentity readIdentity() {
                return identity;
            }
        };
        return new ActionDispatcher(false, null, () -> { throw new IllegalStateException("no log loaded"); },
                row -> null, row -> null, render);
    }

    private static final ReadThroughIdentity SUSPENDED = new ReadThroughIdentity(FollowIdentity.Verdict.UNVERIFIED,
            "the opened file changed in place", false);
    private static final ReadThroughIdentity RETAINED = new ReadThroughIdentity(FollowIdentity.Verdict.REPLACEMENT,
            "a different file now has this path", true);

    @Test
    @DisplayName("a record-reading verb is refused while reads are suspended, and says why")
    void suspendedRefuses() {
        // witness: the dispatcher's suspendsReads() check removed
        for (String verb : new String[]{"series", "coverage", "goto"}) {
            ActionResult r = dispatcher(SUSPENDED).dispatch(Map.of("action", verb, "params", Map.of()));
            assertFalse(r.ok(), verb);
            assertTrue(String.valueOf(r.toMap()).contains("changed in place"), verb + ": " + r.toMap());
        }
    }

    @Test
    @DisplayName("a verb that does not read records is not refused")
    void viewVerbsAreNotRefused() {
        ActionResult r = dispatcher(SUSPENDED).dispatch(Map.of("action", "context", "params", Map.of()));
        assertTrue(r.ok(), r.toMap().toString());
    }

    @Test
    @DisplayName("R3: a rolled set whose mapped member changed in place is refused here, naming the member")
    void aRolledSetsChangedMemberIsRefused(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        // independent review R3: the set inherited a null identity, so this guard never saw a member's change
        String record = "eventLogRecord:\n  event: Tick\n  logTime: 1\n  nodeLogs:\n    - rootNode: {v: 1}\n---\n";
        var a = dir.resolve("a.yaml");
        var b = dir.resolve("b.yaml");
        java.nio.file.Files.writeString(a, record);
        java.nio.file.Files.writeString(b, record);
        try (var rolled = telamin.fluxtion.audit.analyser.analyser.parse.RolledLogStore.open(java.util.List.of(a, b), 0)) {
            var before = java.nio.file.Files.getLastModifiedTime(b);
            java.nio.file.Files.writeString(b, record.replace("v: 1", "v: 9"));
            java.nio.file.Files.setLastModifiedTime(b, java.nio.file.attribute.FileTime.fromMillis(before.toMillis() + 2000));
            ActionResult r = dispatcher(rolled.readThroughIdentity()).dispatch(Map.of("action", "series", "params", Map.of()));
            assertFalse(r.ok(), r.toMap().toString());
            assertTrue(String.valueOf(r.toMap()).contains("member b.yaml"), r.toMap().toString());
        }
    }

    @Test
    @DisplayName("retained superseded content is served, labelled")
    void retainedIsLabelled() {
        ActionResult r = dispatcher(RETAINED).dispatch(Map.of("action", "series", "params", Map.of()));
        assertTrue(r.ok());
        assertEquals("a different file now has this path", r.payload().get("identityNote"));
    }
}
