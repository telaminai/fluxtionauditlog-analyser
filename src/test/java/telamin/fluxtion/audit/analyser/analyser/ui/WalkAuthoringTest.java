package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;
import telamin.fluxtion.audit.analyser.analyser.filter.FilterState;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;
import telamin.fluxtion.audit.analyser.analyser.parse.LogStore;
import telamin.fluxtion.audit.analyser.analyser.parse.Samples;
import telamin.fluxtion.audit.analyser.analyser.report.LogFingerprint;
import telamin.fluxtion.audit.analyser.analyser.walk.WalkIdentity;
import telamin.fluxtion.audit.analyser.analyser.walk.WalkSpec;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M69 S3 (spec-spotlight-walks.md §3.5, §3.7): the one save path. A client points and the analyser digests; a save
 * read under one log is refused if another was opened before it was stored; what cannot be saved is named.
 */
class WalkAuthoringTest {

    static final class Rig implements WalkAuthoring.Frame {
        final AppConfig config = new AppConfig();
        final LogStore store = new HeapLogStore(Samples.sample());
        final FilterState filter = new FilterState();
        final GraphTabs graphs = new GraphTabs();
        final TopologyPanel topology = new TopologyPanel();
        final List<SpotlightOverlay.Lit> lit = new ArrayList<>();
        long generation = 1;
        int persisted;

        Rig() {
            graphs.bind(store, filter);
        }

        public AppConfig config() { return config; }
        public LogStore store() { return store; }
        public FilterState filter() { return filter; }
        public GraphTabs graphs() { return graphs; }
        public TopologyPanel topology() { return topology; }
        public String selectedTabWord() { return "summary"; }
        public int selectedRecord() { return 2; }
        public List<SpotlightOverlay.Lit> lit() { return lit; }
        public List<String> runBasisNow() { return List.of("sha256:run"); }
        public LogFingerprint fingerprint() { return null; }
        public long generation() { return generation; }
        public void persist() { persisted++; }
    }

    @Test
    @DisplayName("bind computes the record digest from the log itself — a client cannot supply one")
    void bindDigestsTheRecord() {
        Rig rig = new Rig();
        var step = new WalkSpec.Step("", WalkSpec.View.NONE, List.of(new WalkSpec.Target("records:row:1", "c", null)));
        WalkSpec.Step bound = new WalkAuthoring(rig).bind(step);
        assertEquals(WalkIdentity.recordDigest(rig.store.rawText(1)), bound.targets().get(0).basis().digest());
        assertEquals("HeapLogStore", bound.targets().get(0).basis().representation());
    }

    @Test
    @DisplayName("§3.5: a save read under one log is refused when another log opened before it was stored")
    void aSaveAcrossAGenerationIsRefused() {
        Rig rig = new Rig();
        WalkAuthoring a = new WalkAuthoring(rig);
        var step = a.bind(new WalkSpec.Step("", WalkSpec.View.NONE, List.of(new WalkSpec.Target("status", "", null))));
        rig.generation = 2;                                  // another log opened while the name dialog was up
        String refused = a.save("w", "", List.of(step), WalkSpec.AUTHOR_PERSON, 1);
        assertNotNull(refused, "the steps were read against the previous log");
        assertTrue(refused.contains("another log was opened"), refused);
        assertTrue(rig.config.walks.isEmpty(), "and nothing was saved");
        assertEquals(0, rig.persisted);
    }

    @Test
    @DisplayName("a save stores the walk, persists once, and keeps a replaced walk's author and creation time")
    void aSaveStoresAndPersists() {
        Rig rig = new Rig();
        WalkAuthoring a = new WalkAuthoring(rig);
        var step = a.bind(new WalkSpec.Step("", WalkSpec.View.NONE, List.of(new WalkSpec.Target("status", "", null))));
        assertNull(a.save("w", "Title", List.of(step), WalkSpec.AUTHOR_PERSON, 1));
        assertEquals(1, rig.persisted);
        WalkSpec first = rig.config.walks.get(0);
        assertEquals(List.of("sha256:run"), first.runBasis());
        assertNull(a.save("w", "", List.of(step, step), WalkSpec.AUTHOR_ASSISTANT, 1));
        WalkSpec second = rig.config.walks.get(0);
        assertEquals(1, rig.config.walks.size(), "replace by name");
        assertEquals(WalkSpec.AUTHOR_PERSON, second.author(), "a replace does not re-attribute the walk");
        assertEquals(first.createdAt(), second.createdAt());
        assertEquals("Title", second.title(), "an empty title keeps the old one");
    }

    @Test
    @DisplayName("names and steps are validated before anything is stored")
    void refusals() {
        Rig rig = new Rig();
        WalkAuthoring a = new WalkAuthoring(rig);
        var ok = a.bind(new WalkSpec.Step("", WalkSpec.View.NONE, List.of(new WalkSpec.Target("status", "", null))));
        assertNotNull(a.save("a:b", "", List.of(ok), WalkSpec.AUTHOR_PERSON, 1), "a name an address cannot carry");
        assertNotNull(a.save("", "", List.of(ok), WalkSpec.AUTHOR_PERSON, 1));
        assertNotNull(a.save("w", "", List.of(), WalkSpec.AUTHOR_PERSON, 1));
        var menu = new WalkSpec.Step("", WalkSpec.View.NONE, List.of(new WalkSpec.Target("menu:Audit log", "", null)));
        assertNotNull(a.save("w", "", List.of(menu), WalkSpec.AUTHOR_PERSON, 1), "a menu target is not walkable");
        assertTrue(rig.config.walks.isEmpty());
    }

    @Test
    @DisplayName("a capture keeps what is walkable, names what is not, and states the view it saw")
    void aCaptureNamesWhatItCannotSave() {
        Rig rig = new Rig();
        rig.lit.add(new SpotlightOverlay.Lit(1, "records:row:2", new java.awt.Rectangle(1, 1, 2, 2), "the fill"));
        rig.lit.add(new SpotlightOverlay.Lit(2, "menu:Audit log", new java.awt.Rectangle(1, 1, 2, 2), null));
        WalkAuthoring.Capture c = new WalkAuthoring(rig).capture();
        assertEquals(List.of("records:row:2"), c.step().targets().stream().map(WalkSpec.Target::target).toList());
        assertEquals("the fill", c.step().targets().get(0).caption());
        assertEquals(1, c.notSaved().size());
        assertTrue(c.notSaved().get(0).contains("menu:Audit log"), c.notSaved().toString());
        assertEquals("summary", c.step().view().tab());
        assertEquals(2, c.step().view().record());
        assertNotNull(c.step().view().filter(), "the filter is captured whole");
        assertEquals(1, c.generation());
        assertFalse(c.step().targets().get(0).basis().digest().isBlank(), "and bound to the record's text");
    }
}
