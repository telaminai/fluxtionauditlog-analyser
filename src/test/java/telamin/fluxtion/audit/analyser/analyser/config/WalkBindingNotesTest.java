package telamin.fluxtion.audit.analyser.analyser.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.walk.WalkSpec;

import java.io.StringWriter;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Review P2 N-b (2026-10-17): a walk binding dropped on load is carried with the config that was read — never in a static
 * buffer the performer drains — so the session can decide to say it (sessionBoundary, on ProfileApplied).
 */
class WalkBindingNotesTest {

    /** ★ Witness: read share walks with no notes list (the 2-argument readWalks) — the target holds no note. */
    @Test
    @DisplayName("★ a profile's dangling binding lands on the loaded config's own notes, and nowhere global")
    void notesRideWithTheConfig() throws Exception {
        WalkSpec base = WalkPersistenceTest.sample("w");
        var src = new WalkSpec.Source(WalkSpec.CLAUDE_CODE_SOURCE, "m", "2.1.282");
        var c = new WalkSpec.Conversation(2, WalkSpec.RECORDED, "", List.of(new WalkSpec.Turn("t1", "user", "q")), src);
        var steps = List.of(base.steps().get(0).withBinding("s1", "t1"), base.steps().get(1).withBinding("s2", "t9"));
        Properties p = new Properties();
        ConfigStore.writeWalks(p, List.of(base.withConversation(c, steps, base.updatedAt())));
        StringWriter w = new StringWriter();
        p.store(w, null);

        AppConfig target = new AppConfig();
        SettingsShare share = new SettingsShare();
        var plan = share.preview(w.toString(), target);
        share.apply(plan, Set.of(SettingsShare.Category.REPORTS), target);
        assertEquals(1, target.walkBindingNotes.size(), String.valueOf(target.walkBindingNotes));
        assertTrue(target.walkBindingNotes.get(0).startsWith("walk 'w': step 2's conversation binding"));
        AppConfig other = new AppConfig();
        assertTrue(other.walkBindingNotes.isEmpty(), "another config holds nothing of it");
    }
}
