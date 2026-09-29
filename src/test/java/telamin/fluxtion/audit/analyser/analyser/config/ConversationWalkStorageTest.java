package telamin.fluxtion.audit.analyser.analyser.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.walk.WalkSpec;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OA-3 / OA-A12 (spec §6.1): a walk's dialogue and its step bindings take every path a walk takes — the ConfigStore
 * round trip, the project snapshot and the global tier, a project profile, the share export/preview/apply, the bin and
 * rename — and a walk WITHOUT dialogue is stored exactly as before.
 */
class ConversationWalkStorageTest {

    static WalkSpec withDialogue(String name) {
        WalkSpec base = WalkPersistenceTest.sample(name);
        var steps = List.of(base.steps().get(0).withBinding("s1", "t1"), base.steps().get(1).withBinding("s2", "t2"));
        var c = new WalkSpec.Conversation(1, WalkSpec.EDITED_RECORDING, "DEMO author",
                List.of(new WalkSpec.Turn("t1", "user", "Where did the application first log a breach?"),
                        new WalkSpec.Turn("t2", "assistant", "At record 16: its RiskBreachEvent.\nSee the detail pane.")));
        return base.withConversation(c, steps, base.updatedAt());
    }

    private static List<WalkSpec> roundTrip(List<WalkSpec> walks) {
        Properties p = new Properties();
        ConfigStore.writeWalks(p, walks);
        List<WalkSpec> back = new ArrayList<>();
        ConfigStore.readWalks(p, back);
        return back;
    }

    @Test
    @DisplayName("the ConfigStore round trip keeps the turns, their kind, author, and every step's id and reveal")
    void theDialogueRoundTrips() {
        assertEquals(List.of(withDialogue("j")), roundTrip(List.of(withDialogue("j"))));
    }

    @Test
    @DisplayName("a walk without dialogue writes no dialogue or step-id keys: storage is what it was")
    void aWalkWithoutDialogueIsUnchanged() {
        Properties p = new Properties();
        ConfigStore.writeWalks(p, List.of(WalkPersistenceTest.sample("plain")));
        for (String key : p.stringPropertyNames()) {
            assertFalse(key.contains(".conv.") || key.endsWith(".id") || key.endsWith(".through"), key);
        }
        assertEquals(List.of(WalkPersistenceTest.sample("plain")), roundTrip(List.of(WalkPersistenceTest.sample("plain"))));
    }

    @Test
    @DisplayName("a newer dialogue version is kept byte for byte through a read and a write, and is not playable here")
    void aNewerVersionSurvives() {
        Properties p = new Properties();
        ConfigStore.writeWalks(p, List.of(WalkPersistenceTest.sample("future")));
        p.setProperty("walk.0.conv.v", "2");
        p.setProperty("walk.0.conv.kind", "scripted");
        p.setProperty("walk.0.conv.segments.count", "1");
        p.setProperty("walk.0.conv.segments.0.speech", "a future format");
        List<WalkSpec> back = new ArrayList<>();
        ConfigStore.readWalks(p, back);
        assertFalse(back.get(0).conversation().supported());
        Properties again = new Properties();
        ConfigStore.writeWalks(again, back);
        assertEquals(p, again, "every key the newer analyser wrote comes back as it was");
    }

    @Test
    @DisplayName("project tier: snapshot, restore; and a project profile's save and load carry the dialogue")
    void projectAndProfileCarryDialogue(@TempDir Path dir) throws Exception {
        AppConfig c = new AppConfig();
        c.walks.add(withDialogue("mine"));
        ProjectProfile.Snapshot snap = ProjectProfile.snapshot(c);
        ProjectProfile.clearProjectScoped(c);
        ProjectProfile.restore(snap, c);
        assertEquals(List.of(withDialogue("mine")), c.walks);
        Path profile = dir.resolve(".analyser").resolve("project.fluxtion-settings");
        Files.createDirectories(profile.getParent());
        ProjectProfile.save(profile, c, new SettingsShare());
        AppConfig back = new AppConfig();
        assertTrue(ProjectProfile.load(profile, back, new SettingsShare()).loaded());
        assertEquals(List.of(withDialogue("mine")), back.walks);
    }

    @Test
    @DisplayName("the global tier: a machine save keeps the no-project walk's dialogue")
    void theGlobalTierCarriesDialogue(@TempDir Path dir) throws Exception {
        AppConfig c = new AppConfig();
        c.walks.add(withDialogue("global"));
        ConfigStore store = new ConfigStore(dir.resolve("config"));
        store.save(c, null);
        assertEquals(List.of(withDialogue("global")), store.load().walks);
    }

    @Test
    @DisplayName("sharing: the dialogue travels, and the consent summary says it carries labelled, unverified dialogue")
    void sharingCarriesAndNamesTheDialogue() {
        SettingsShare share = new SettingsShare();
        AppConfig sender = new AppConfig();
        sender.walks.add(withDialogue("shared"));
        String text = share.export(sender, EnumSet.of(SettingsShare.Category.REPORTS));
        AppConfig receiver = new AppConfig();
        var plan = share.preview(text, receiver);
        String summary = plan.summary().get(SettingsShare.Category.REPORTS);
        assertTrue(summary.contains("carrying dialogue (2 turns"), summary);
        share.apply(plan, EnumSet.of(SettingsShare.Category.REPORTS), receiver);
        assertEquals(List.of(withDialogue("shared")), receiver.walks);
    }

    @Test
    @DisplayName("sharing: a walk whose dialogue binds a step to a missing turn is REFUSED by name, never partly installed")
    void malformedSharedDialogueIsRefused() {
        SettingsShare share = new SettingsShare();
        AppConfig sender = new AppConfig();
        sender.walks.add(withDialogue("bad"));
        sender.walks.add(WalkPersistenceTest.sample("good"));
        String text = share.export(sender, EnumSet.of(SettingsShare.Category.REPORTS)).replace("s.1.through=t2", "s.1.through=t9");
        AppConfig receiver = new AppConfig();
        var plan = share.preview(text, receiver);
        assertTrue(plan.summary().get(SettingsShare.Category.REPORTS).contains("1 REFUSED: 'bad'"),
                plan.summary().get(SettingsShare.Category.REPORTS));
        share.apply(plan, EnumSet.of(SettingsShare.Category.REPORTS), receiver);
        assertEquals(List.of("good"), receiver.walks.stream().map(WalkSpec::name).toList(), "only the valid walk arrived");
    }

    @Test
    @DisplayName("the bin and rename: delete, restore and rename keep the turns, ids and reveals")
    void binAndRenameKeepTheDialogue(@TempDir Path dir) throws Exception {
        AppConfig c = new AppConfig();
        c.walks.add(withDialogue("a"));
        assertNotNull(WalkBin.delete(c, "a", "now"));
        ConfigStore store = new ConfigStore(dir.resolve("config"));
        store.save(c, null);
        AppConfig back = store.load();
        assertNull(WalkBin.restore(back, "a"));
        assertEquals(List.of(withDialogue("a")), back.walks, "restored from the machine's bin with its dialogue");
        assertNull(WalkBin.rename(back, "a", "b"));
        WalkSpec renamed = back.walks.get(0);
        assertEquals(withDialogue("a").conversation(), renamed.conversation());
        assertEquals(List.of("s1", "s2"), renamed.steps().stream().map(WalkSpec.Step::id).toList(), "rename keeps ids");
    }

    @Test
    @DisplayName("Map.of() default: the nine-field constructor still means 'no dialogue'")
    void theOldConstructorMeansNoDialogue() {
        WalkSpec w = new WalkSpec("x", "", "person", "", "", null, List.of(), List.of(), Map.of());
        assertNull(w.conversation());
    }
}
