package telamin.fluxtion.audit.analyser.analyser.assistant.localagent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.session.AssistantMode;
import telamin.fluxtion.audit.analyser.analyser.session.LocalAgentPosture;
import telamin.fluxtion.audit.analyser.analyser.session.SessionDriver;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEffects;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M74 review findings 3 and 9: the consent fact as the machine observes it, computed by the pure
 * {@link LocalAgentConsents#of} the frame now calls — a planted folder whose files change, the scratch folder, the
 * posture reference.
 */
class LocalAgentConsentsTest {

    @TempDir
    Path tmp;

    Path home() throws Exception {
        return Files.createDirectories(tmp.resolve("home"));
    }

    Path planted() throws Exception {
        Path dir = Files.createDirectories(tmp.resolve("work/proj/.claude")).getParent();
        Files.writeString(dir.resolve(".claude/settings.json"), "{\"hooks\":{\"SessionStart\":[{\"hooks\":[{}]}]}}");
        return dir;
    }

    static LocalAgentConsents.Settings settings(boolean full, List<String> trusted, List<String> trustedFull) {
        return new LocalAgentConsents.Settings(true, full, "acceptEdits", trusted, trustedFull, null);
    }

    static SessionEvents.LocalAgentConsent of(Path dir, Path scratch, Path home, LocalAgentConsents.Settings s) {
        return LocalAgentConsents.of(dir, scratch, home, s, true, p -> false);
    }

    /**
     * ★ Witnesses: {@code fullConfirmed = confirmed} (the default posture's trust confirms the full one — the second
     * assertion fails); the digest of another folder ({@code scratch} instead of {@code dir} — the first fails); the ref
     * computed with {@code ANALYSER_ASSISTANT} whatever the switch (the ref assertion fails).
     */
    @Test
    @DisplayName("★ the consent of a planted folder: full trust bound to its files' digest, separate from the default trust; the ref follows the posture")
    void aPlantedFolder() throws Exception {
        Path dir = planted();
        Path home = home();
        String real = dir.toRealPath().toString();
        String digest = FullFolderTrust.digest(dir);
        var c = of(dir, null, home, settings(true, List.of(), List.of(FullFolderTrust.record(real, digest))));
        assertTrue(c.full() && c.fullFolderConfirmed(), "the full record of this content confirms it");
        assertEquals(LocalAgentPosture.FullMode.ACCEPT_EDITS, c.fullMode());

        var onlyDefault = of(dir, null, home, settings(true, List.of(real), List.of()));
        assertTrue(onlyDefault.folderConfirmed());
        assertFalse(onlyDefault.fullFolderConfirmed(), "a default-posture record never confirms the full posture");

        Files.writeString(dir.resolve(".mcp.json"), "{\"mcpServers\":{\"added\":{}}}");
        var changed = of(dir, null, home, settings(true, List.of(), List.of(FullFolderTrust.record(real, digest))));
        assertFalse(changed.fullFolderConfirmed(), "a file changed since the trust was recorded: asked again");

        var def = of(dir, null, home, settings(false, List.of(real), List.of()));
        assertEquals(LocalAgentPosture.full(LocalAgentPosture.FullMode.ACCEPT_EDITS)
                        .ref(real, ClaudeCodeProvider.FULL_SYSTEM_NOTE), c.postureRef(), "the full posture's own ref");
        assertEquals(LocalAgentPosture.ANALYSER_ASSISTANT.ref(real, ClaudeCodeProvider.SYSTEM_NOTE), def.postureRef());
        assertNotEquals(c.postureRef(), def.postureRef());
        assertEquals(0, c.budgetMicros(), "no spend limit set: 0");
    }

    /**
     * ★ Review finding 3. Witness: the scratch folder confirmed whatever it holds (the P1b frame's {@code scratch ||}) —
     * the planted scratch folder is confirmed and Start runs its hook unasked.
     */
    @Test
    @DisplayName("★ the scratch folder counts as confirmed for full Claude Code only while it holds none of the three files")
    void theScratchFolder() throws Exception {
        Path scratch = Files.createDirectories(tmp.resolve("scratch"));
        Path home = home();
        assertTrue(of(scratch, scratch, home, settings(true, List.of(), List.of())).fullFolderConfirmed(),
                "an empty scratch folder holds nothing to run");
        Files.createDirectories(scratch.resolve(".claude"));
        Files.writeString(scratch.resolve(".claude/settings.json"), "{\"hooks\":{\"SessionStart\":[{\"hooks\":[{}]}]}}");
        var planted = of(scratch, scratch, home, settings(true, List.of(), List.of()));
        assertFalse(planted.fullFolderConfirmed(), "a hook planted in the scratch folder is asked about");
        assertTrue(planted.folderConfirmed(), "the default posture runs no hooks: its scratch rule is unchanged");

        // and the node turns that into the full dialog, never a Start
        List<SessionEffects> effects = new ArrayList<>();
        SessionDriver d = new SessionDriver(e -> {
            effects.add(e);
            return new SessionEvents.LocalAgentEffectStarted(e.opId(), 0, "test");
        });
        d.post(new SessionEvents.AssistantModeChosen(AssistantMode.LOCAL_AGENT, "test"));
        d.post(new SessionEvents.LocalAgentStartRequested(1, planted, false));
        assertEquals("CONFIRM_FOLDER", d.snapshot().localAgent().verdict());
        assertTrue(effects.stream().anyMatch(e -> e instanceof SessionEffects.ConfirmLocalAgentFolderEffect f && f.full()));
        assertTrue(effects.stream().noneMatch(SessionEffects.StartLocalAgentEffect.class::isInstance));
    }
}
