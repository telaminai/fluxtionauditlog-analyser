package telamin.fluxtion.audit.analyser.bundle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;
import telamin.fluxtion.audit.analyser.analyser.config.ConfigStore;
import telamin.fluxtion.audit.analyser.analyser.walk.WalkSpec;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OA-3 / OA-A13 (spec §7): what a bundle's profile carries of a walk's dialogue. The existing path policy covers every new
 * field — a machine path in a turn is removed and named — and nothing that was never dialogue (a key, a system prompt,
 * hidden context) can reach it, because a walk holds only turns a person selected or wrote. Synthetic DEMO values only.
 */
class ConversationBundleProfileTest {

    private static final String KEY = "sk-DEMO-not-a-real-key-000000";

    static WalkSpec journey() {
        var step = new WalkSpec.Step("the breach", WalkSpec.View.NONE, List.of(new WalkSpec.Target("status", "", null)), "s1", "t2");
        var c = new WalkSpec.Conversation(1, WalkSpec.SCRIPTED, "", List.of(
                new WalkSpec.Turn("t1", "user", "Where is it? I saved it under /Users/demo-person/private/breach.yaml"),
                new WalkSpec.Turn("t2", "assistant", "At the DEMO record shown here.")));
        return new WalkSpec("journey", "", "person", "", "", null, List.of(), List.of(step), Map.of(), c);
    }

    @Test
    @DisplayName("a machine path inside a turn is removed and named; the dialogue and its bindings otherwise travel")
    void aPathInATurnIsRedacted(@TempDir Path dir) throws Exception {
        AppConfig c = new AppConfig();
        c.walks.add(journey());
        c.apiKey = KEY;                                   // present on the sender's machine
        Path settings = dir.resolve("config");
        new ConfigStore(settings).save(c, null);
        Path out = dir.resolve("profile.fluxtion-settings");
        BundleProfile.Export export = BundleProfile.export(settings, out);
        String bytes = Files.readString(out);
        assertFalse(bytes.contains("/Users/demo-person"), "the machine path never leaves");
        assertTrue(bytes.contains(BundleProfile.REDACTED.replace("\u2039", "\\u2039").replace("\u203a", "\\u203a"))
                || bytes.contains(BundleProfile.REDACTED), "it is replaced where it stood");
        assertTrue(export.redacted().stream().anyMatch(r -> r.contains("conv.t.0.text") && r.contains("/Users/demo-person")),
                "and the author is told exactly which turn lost what: " + export.redacted());
        assertFalse(bytes.contains(KEY), "the provider key never leaves in a bundle");
        assertTrue(bytes.contains("At the DEMO record shown here."), "the rest of the dialogue travels");
        assertTrue(bytes.contains("s.0.through=t2"), "and so does the step binding");
    }

    @Test
    @DisplayName("an excerpt keeps a dialogue walk's bindings and SAYS its words were not re-based")
    void anExcerptDisclosesUnrebasedWords(@TempDir Path dir) throws Exception {
        AppConfig c = new AppConfig();
        c.walks.add(journey());
        Path settings = dir.resolve("config");
        new ConfigStore(settings).save(c, null);
        Path out = dir.resolve("profile.fluxtion-settings");
        BundleProfile.Export export = BundleProfile.export(settings, out, new BundleProfile.Rebase(5, 20, List.of(), null));
        assertTrue(export.dangling().stream().anyMatch(d -> d.contains("walk 'journey'") && d.contains("not re-based")),
                export.dangling().toString());
        assertTrue(Files.readString(out).contains("s.0.through=t2"));
    }
}
