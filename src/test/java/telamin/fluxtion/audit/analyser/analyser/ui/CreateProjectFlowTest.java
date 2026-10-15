package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;
import telamin.fluxtion.audit.analyser.analyser.config.ConfigStore;
import telamin.fluxtion.audit.analyser.analyser.config.ProjectProfile;
import telamin.fluxtion.audit.analyser.analyser.config.ReferenceSet;
import telamin.fluxtion.audit.analyser.analyser.config.SettingsShare;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M73 (spec-create-project.md §7) — the filesystem half of a create, headless: what is observed, what is written, and
 * what is never written. Every create here goes through the real generated session processor ({@link CreateHarness}),
 * so a refusal is the node's, as in the product.
 */
class CreateProjectFlowTest {

    @TempDir Path tmp;

    /** A small project: a root that is itself a source root, a module source root, two skills. */
    private static Path project(Path at) throws IOException {
        Files.createDirectories(at.resolve("com/acme"));               // "." looks like a source root
        Files.createDirectories(at.resolve("app/src/main/java/com/acme/demo"));
        skill(at, "skills/alpha/SKILL.md", "alpha", "Open this project's demo log.");
        skill(at, "skills/beta/SKILL.md", "beta", "Replay the demo.");
        return at.toRealPath();
    }

    private static void skill(Path root, String rel, String name, String description) throws IOException {
        Path f = root.resolve(rel);
        Files.createDirectories(f.getParent());
        Files.writeString(f, "---\nname: " + name + "\ndescription: " + description + "\n---\n");
    }

    private static SessionEvents.CreateRequest socketRequest(Path dir, AppConfig config, Map<String, Object> create) {
        var offer = NewProjectDiscovery.discover(dir);
        return new SessionEvents.CreateRequest(CreateProjectObservation.facts(dir, offer, config),
                CreateProjectObservation.fromSocket(create));
    }

    private static SessionEvents.CreateRequest menuRequest(Path dir, AppConfig config, Set<Path> roots, Set<String> skills,
                                                           boolean guide) {
        var offer = NewProjectDiscovery.discover(dir);
        return new SessionEvents.CreateRequest(CreateProjectObservation.facts(dir, offer, config),
                CreateProjectObservation.fromMenu(offer, new NewProjectDiscovery.Selection(roots, skills, null, guide), dir));
    }

    private static AppConfig allowed() {
        AppConfig c = new AppConfig();
        c.assistantCreateProjects = true;
        return c;
    }

    /** The profile text without its creation nonce — the one line two creates may differ by. */
    private static String withoutNonce(Path profile) throws IOException {
        return String.join("\n", Files.readAllLines(profile).stream()
                .filter(l -> !l.startsWith(ProjectProfile.NONCE_KEY + "=")).toList());
    }

    private static List<String> tree(Path root) throws IOException {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.map(p -> root.relativize(p).toString()).sorted().toList();
        }
    }

    // ---- ★ parity: one adoption --------------------------------------------------------------------------

    /**
     * ★ Witness (run 2026-10-15): make {@code SessionBoundary.approve} iterate the caller's selection instead of the
     * offer, and the socket's reversed selection writes different bytes from the menu's.
     */
    @Test
    @DisplayName("★ menu-shaped and socket-shaped selections give byte-identical profiles (modulo the nonce)")
    void menuAndSocketAdoptIdentically() throws Exception {
        Path a = project(tmp.resolve("menu"));
        Path b = project(tmp.resolve("socket"));
        var menu = new CreateHarness(allowed());
        assertTrue(menu.create(a, menuRequest(a, menu.config, Set.of(a, a.resolve("app/src/main/java")),
                Set.of("skills/alpha/SKILL.md", "skills/beta/SKILL.md"), false), true), () -> menu.warning);
        var socket = new CreateHarness(allowed());
        assertTrue(socket.create(b, socketRequest(b, socket.config, Map.of(
                "sourceRoots", List.of("app/src/main/java", "."),
                "skills", List.of("skills/beta/SKILL.md", "skills/alpha/SKILL.md"))), false), () -> socket.warning);
        String menuText = withoutNonce(ProjectProfile.pathFor(a));
        assertEquals(menuText, withoutNonce(ProjectProfile.pathFor(b)), "one adoption, whichever surface asked");
        assertTrue(menuText.contains("skills/alpha/SKILL.md"), menuText);
        assertEquals(List.of(a.toString(), a.resolve("app/src/main/java").toString()), menu.config.sourceRoots,
                "G3: offer order, not the caller's order");
    }

    @Test
    @DisplayName("G3: two runs with the selection named in different orders write the same profile")
    void rootOrderIsDeterministic() throws Exception {
        Path a = project(tmp.resolve("one"));
        Path b = project(tmp.resolve("two"));
        var first = new CreateHarness(allowed());
        first.create(a, socketRequest(a, first.config, Map.of("sourceRoots", List.of(".", "app/src/main/java"))), false);
        var second = new CreateHarness(allowed());
        second.create(b, socketRequest(b, second.config, Map.of("sourceRoots", List.of("app/src/main/java", "."))), false);
        assertEquals(withoutNonce(ProjectProfile.pathFor(a)), withoutNonce(ProjectProfile.pathFor(b)));
    }

    // ---- ★ G2: a failed write changes nothing --------------------------------------------------------------

    /**
     * ★ Witnesses (run 2026-10-15): remove the {@code ProjectProfile.restore(before, config)} in
     * {@code ProjectSession.createNew}'s catch and "the config equals the snapshot" fails; make
     * {@code SessionBoundary.onEffectFailed} return early and "the caller sees an error" fails.
     */
    @Test
    @DisplayName("★ G2: a failed write with project A active leaves A's file and the config exactly as they were")
    void aFailedCreateLeavesTheActiveProjectAlone() throws Exception {
        AppConfig config = new AppConfig();
        var h = new CreateHarness(config);
        Path a = project(tmp.resolve("a"));
        assertTrue(h.create(a, menuRequest(a, config, Set.of(a), Set.of(), false), true), () -> h.warning);
        Path aFile = ProjectProfile.pathFor(a);
        byte[] aBytes = Files.readAllBytes(aFile);
        var before = ProjectProfile.snapshot(config);

        Path b = Files.createDirectories(tmp.resolve("b")).toRealPath();
        Files.writeString(b.resolve(".analyser"), "a FILE where the directory goes");   // the write must fail
        assertFalse(h.create(b, menuRequest(b, config, Set.of(), Set.of(), false), true),
                "a person is not refused by the .analyser check, so the WRITE fails");
        assertNotNull(h.warning, "the caller sees an error, not success");
        assertTrue(h.warning.startsWith("Could not create the project"), h.warning);
        assertEquals(aFile, h.project.activeFile(), "activeFile is unchanged");
        assertEquals(before, ProjectProfile.snapshot(config), "the config equals the snapshot");
        assertFalse(h.project.isDirty(), "and nothing is waiting to be written over A");
        h.project.flush();
        assertArrayEquals(aBytes, Files.readAllBytes(aFile), "A's profile is byte-identical");

        // inFlight was cleared: the next create proceeds
        Path c = project(tmp.resolve("c"));
        assertTrue(h.create(c, menuRequest(c, config, Set.of(), Set.of(), false), true), () -> h.warning);
    }

    @Test
    @DisplayName("★ G2: with no project open, a failed create leaves the person's own settings unchanged")
    void aFailedCreateLeavesOwnSettingsAlone() throws Exception {
        AppConfig config = new AppConfig();
        config.sourceRoots.add("/work/own/src");
        var h = new CreateHarness(config);
        var before = ProjectProfile.snapshot(config);
        Path b = Files.createDirectories(tmp.resolve("b")).toRealPath();
        Files.writeString(b.resolve(".analyser"), "not a directory");
        assertFalse(h.create(b, menuRequest(b, config, Set.of(), Set.of(), false), true));
        assertNull(h.project.activeFile());
        assertEquals(before, ProjectProfile.snapshot(config), "the global config is unchanged");
        assertNull(h.project.globalTier(), "no project, so the live config is what a save writes — and it is untouched");
    }

    // ---- ★ race: the profile appears after observation --------------------------------------------------------

    /** ★ Witness (run 2026-10-15): open with CREATE + TRUNCATE_EXISTING in ProjectProfile.createNew and the write succeeds over it. */
    @Test
    @DisplayName("★ race: a profile that appears between observation and write is refused by CREATE_NEW, untouched")
    void aProfileThatAppearsIsNeverOverwritten() throws Exception {
        AppConfig config = new AppConfig();
        var h = new CreateHarness(config);
        Path a = project(tmp.resolve("a"));
        h.create(a, menuRequest(a, config, Set.of(), Set.of(), false), true);
        byte[] aBytes = Files.readAllBytes(ProjectProfile.pathFor(a));

        Path b = project(tmp.resolve("b"));
        var request = socketRequest(b, allowed(), Map.of());                 // observed: no profile yet
        Path appeared = ProjectProfile.pathFor(b);
        Files.createDirectories(appeared.getParent());
        Files.writeString(appeared, "sourceRoot.0=someone-else\n");            // ... then one appears
        assertFalse(h.create(b, request, false));
        assertTrue(h.warning.startsWith("Could not create the project"), h.warning);
        assertEquals("sourceRoot.0=someone-else\n", Files.readString(appeared), "the appeared profile is untouched");
        assertArrayEquals(aBytes, Files.readAllBytes(ProjectProfile.pathFor(a)), "and A is untouched");
        assertEquals(ProjectProfile.pathFor(a), h.project.activeFile());
    }

    // ---- links --------------------------------------------------------------------------------------------

    @Test
    void aSymlinkedAnalyserIsRefusedForTheAssistant() throws Exception {
        Path b = project(tmp.resolve("b"));
        Path elsewhere = Files.createDirectories(tmp.resolve("elsewhere"));
        Files.createSymbolicLink(b.resolve(".analyser"), elsewhere);
        var facts = CreateProjectObservation.facts(b, NewProjectDiscovery.discover(b), allowed());
        assertEquals("is a symbolic link", facts.analyserProblem());
        var h = new CreateHarness(allowed());
        assertFalse(h.create(b, socketRequest(b, h.config, Map.of()), false));
        assertTrue(h.warning.contains(".analyser is a symbolic link"), h.warning);
        assertEquals(List.of(), tree(elsewhere).stream().filter(s -> !s.isEmpty()).toList(), "nothing written through it");
    }

    @Test
    void aDanglingProfileLinkCountsAsExisting() throws Exception {
        Path b = project(tmp.resolve("b"));
        Files.createDirectories(b.resolve(".analyser"));
        Path target = tmp.resolve("nowhere/settings");
        Files.createSymbolicLink(ProjectProfile.pathFor(b), target);
        var facts = CreateProjectObservation.facts(b, NewProjectDiscovery.discover(b), allowed());
        assertTrue(facts.profileExists(), "links are not followed: writing through a dangling link is what a create must never do");
        var h = new CreateHarness(allowed());
        assertFalse(h.create(b, socketRequest(b, h.config, Map.of()), false));
        assertFalse(Files.exists(target), "nothing was written through the link");
    }

    @Test
    void aSourceRootLinkingOutsideIsMarkedAndNeverAdopted() throws Exception {
        Path b = project(tmp.resolve("b"));
        Path outside = Files.createDirectories(tmp.resolve("outside/src/main/java"));
        Files.createSymbolicLink(b.resolve("linked"), tmp.resolve("outside"));
        var offer = NewProjectDiscovery.discover(b);
        var facts = CreateProjectObservation.facts(b, offer, allowed());
        var linked = facts.offer().sourceRoots().stream().filter(r -> r.relative().equals("linked/src/main/java"))
                .findFirst().orElseThrow();
        assertTrue(linked.outside());
        Object listed = CreateProjectObservation.preview(offer, facts).get("sourceRoots");
        assertTrue(listed.toString().contains("outside=true"), "the preview lists it with outside: true — " + listed);
        var h = new CreateHarness(allowed());
        assertFalse(h.create(b, socketRequest(b, h.config, Map.of("sourceRoots", List.of("linked/src/main/java"))), false));
        assertTrue(h.warning.contains("resolves outside"), h.warning);
    }

    // ---- review M1: discovery reads only under the chosen directory ----------------------------------------------

    /**
     * Witness (run 2026-10-15): pass {@code null} instead of the directory as {@code within} in
     * {@code NewProjectDiscovery.discover} and all three linked graphs are offered.
     */
    @Test
    @DisplayName("M1: a .graphml reached through a link outside the directory is never read or offered")
    void graphmlOutsideTheDirectoryIsNeverRead() throws Exception {
        Path b = project(tmp.resolve("b"));
        Path inside = b.resolve("app/src/main/java/inside.graphml");
        Files.writeString(inside, GRAPHML);
        // a linked module whose src/main/java holds a graph
        Path mod = Files.createDirectories(tmp.resolve("outside-module/src/main/java"));
        Files.writeString(mod.resolve("module.graphml"), GRAPHML);
        Files.createSymbolicLink(b.resolve("linked"), tmp.resolve("outside-module"));
        // a linked src/main/resources
        Path res = Files.createDirectories(tmp.resolve("outside-resources"));
        Files.writeString(res.resolve("resources.graphml"), GRAPHML);
        Files.createDirectories(b.resolve("src/main"));
        Files.createSymbolicLink(b.resolve("src/main/resources"), res);
        // a linked .graphml file inside a real root
        Path loose = tmp.resolve("loose.graphml");
        Files.writeString(loose, GRAPHML);
        Files.createSymbolicLink(b.resolve("app/src/main/java/loose.graphml"), loose);

        var offer = NewProjectDiscovery.discover(b);
        Map<String, Object> preview = CreateProjectObservation.preview(offer,
                CreateProjectObservation.facts(b, offer, new AppConfig()));
        String graphs = String.valueOf(preview.get("graphs"));
        assertTrue(graphs.contains("inside.graphml"), "a graph inside the directory is still offered: " + graphs);
        assertFalse(graphs.contains("module.graphml"), "a linked module's graph is not read: " + graphs);
        assertFalse(graphs.contains("resources.graphml"), "a linked src/main/resources is not walked: " + graphs);
        assertFalse(graphs.contains("loose.graphml"), "a linked .graphml file is not parsed: " + graphs);
        assertEquals(1, offer.graphs().candidates().size());
    }

    private static final String GRAPHML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <graphml xmlns="http://graphml.graphdrawing.org/xmlns" xmlns:jGraph="http://www.jgraph.com/">
              <key id="vertex_label" for="node" attr.name="nodeData" attr.type="string"/>
              <graph edgedefault="undirected">
                <node id="risk"><data key="vertex_label"><jGraph:ShapeNode>
                  <jGraph:label text="id:risk&#10;class:com.acme.demo.RiskNode"/>
                  <jGraph:Style properties="NODE"/>
                </jGraph:ShapeNode></data></node>
              </graph>
            </graphml>
            """;

    // ---- review item 5: the echo names roots as the preview did ------------------------------------------------

    /** Witness (run 2026-10-15): put {@code adopted.sourceRoots()} back in {@code createdEcho} and the absolute paths return. */
    @Test
    @DisplayName("the created echo's adopted sourceRoots are project-relative, \".\" for the directory itself")
    void theEchoNamesRootsAsThePreviewDid() throws Exception {
        Path b = project(tmp.resolve("b"));
        var h = new CreateHarness(allowed());
        assertTrue(h.create(b, socketRequest(b, h.config, Map.of("sourceRoots", List.of("app/src/main/java", "."))), false),
                () -> h.warning);
        Map<String, Object> echo = MainFrame.createdEcho(ProjectProfile.pathFor(b).toString(), h.created.adopted(), h.guide);
        @SuppressWarnings("unchecked") Map<String, Object> adopted = (Map<String, Object>) echo.get("adopted");
        assertEquals(List.of(".", "app/src/main/java"), adopted.get("sourceRoots"));
    }

    // ---- the preview ----------------------------------------------------------------------------------------

    @Test
    @DisplayName("the preview writes nothing and selects nothing")
    void thePreviewWritesNothing() throws Exception {
        Path b = project(tmp.resolve("b"));
        List<String> before = tree(b);
        var offer = NewProjectDiscovery.discover(b);
        Map<String, Object> preview = CreateProjectObservation.preview(offer, CreateProjectObservation.facts(b, offer, new AppConfig()));
        assertEquals(before, tree(b), "the tree listing is identical");
        assertEquals(b.toString(), preview.get("dir"));
        assertEquals(false, preview.get("profileExists"));
        assertEquals("plain", preview.get("projectKind"));
        assertEquals(List.of(".", "app/src/main/java"), preview.get("sourceRoots"));
        assertEquals(false, preview.get("createProjectsSwitch"), "the raw switch, off by default");
        assertTrue(String.valueOf(preview.get("offerDigest")).startsWith("sha256:"));
        assertTrue(String.valueOf(preview.get("next")).contains("sourceRoots: [...]"), "nothing is pre-selected");
        assertFalse(preview.containsKey("selected"));
        assertTrue(String.valueOf(preview.get("writes")).startsWith("nothing"));
    }

    @Test
    void aPreviewReportsAnExistingProfile_andNamesNamedProfiles() throws Exception {
        Path b = project(tmp.resolve("b"));
        Files.createDirectories(b.resolve(".analyser"));
        Files.writeString(b.resolve(".analyser/project.ws-feed.fluxtion-settings"), "");
        var offer = NewProjectDiscovery.discover(b);
        var facts = CreateProjectObservation.facts(b, offer, new AppConfig());
        assertFalse(facts.profileExists(), "a named profile is not the canonical one");
        assertEquals(List.of("project.ws-feed.fluxtion-settings"), facts.namedProfiles());
        Files.writeString(ProjectProfile.pathFor(b), "");
        Map<String, Object> preview = CreateProjectObservation.preview(offer, CreateProjectObservation.facts(b, offer, new AppConfig()));
        assertEquals(true, preview.get("profileExists"), "reported, not refused: the preview decides nothing");
        assertEquals("open {project: \"" + b + "\"}", preview.get("next"));
    }

    @Test
    void theDigestFollowsTheOffer() throws Exception {
        Path b = project(tmp.resolve("b"));
        var first = CreateProjectObservation.facts(b, NewProjectDiscovery.discover(b), allowed()).offer().digest();
        assertEquals(first, CreateProjectObservation.facts(b, NewProjectDiscovery.discover(b), allowed()).offer().digest());
        skill(b, "skills/gamma/SKILL.md", "gamma", "A third.");
        var h = new CreateHarness(allowed());
        assertFalse(h.create(b, socketRequest(b, h.config, Map.of("offerDigest", first)), false));
        assertTrue(h.warning.contains("the offer changed since your preview"), h.warning);
        assertFalse(Files.exists(ProjectProfile.pathFor(b)));
    }

    // ---- honest echo ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("duplicate skill names are reported as not adopted; an existing guide is reported EXISTS")
    void theEchoIsHonest() throws Exception {
        Path b = project(tmp.resolve("b"));
        skill(b, "more/alpha/SKILL.md", "alpha", "The same name again.");
        var h = new CreateHarness(allowed());
        var request = socketRequest(b, h.config, Map.of("skills", List.of("more/alpha/SKILL.md", "skills/alpha/SKILL.md")));
        assertTrue(h.create(b, request, false), () -> h.warning);
        var report = h.created.adopted();
        assertEquals(1, report.skills().size(), "one 'alpha' adopted");
        assertEquals(1, report.skillsNotAdopted().size());
        assertEquals("duplicate name 'alpha'", report.skillsNotAdopted().get(0).why());
        assertEquals(b.resolve(report.skills().get(0).path()).toString(), report.skills().get(0).resolved());

        Path c = project(tmp.resolve("c"));
        var guided = new CreateHarness(allowed());
        var offered = socketRequest(c, guided.config, Map.of("referenceGuide", true));
        assertEquals("CAN_CREATE", offered.facts().offer().referenceGuide(), "the guide is offered here");
        Files.writeString(c.resolve(ReferenceSet.FILE_NAME), "the author's own\n");   // appears after the offer
        assertTrue(guided.create(c, offered, false), () -> guided.warning);
        assertEquals("EXISTS", guided.guide, "a guide that appeared is reported, never overwritten");
        assertEquals("the author's own\n", Files.readString(c.resolve(ReferenceSet.FILE_NAME)));
        assertTrue(Files.exists(ProjectProfile.pathFor(c)), "and the create itself stands [R r2-2]");
    }

    // ---- ★ the switch is machine-only ----------------------------------------------------------------------------

    /**
     * ★ Witness (run 2026-10-15): read {@code assistant.createProjects} in {@code ProjectProfile.load} and the
     * repository-supplied value turns the switch on.
     */
    @Test
    @DisplayName("★ a profile, a settings share and a bundle carrying assistant.createProjects=true leave it off")
    void theSwitchIsMachineOnly() throws Exception {
        Path b = Files.createDirectories(tmp.resolve("repo/.analyser"));
        Path profile = b.resolve("project.fluxtion-settings");
        Files.writeString(profile, "assistant.createProjects=true\nsourceRoot.0=src/main/java\n");

        AppConfig fromProfile = new AppConfig();
        ProjectProfile.load(profile, fromProfile, new SettingsShare());
        assertFalse(fromProfile.assistantCreateProjects, "a cloned repository's profile (and a bundle's, which is one) cannot turn it on");

        AppConfig fromShare = new AppConfig();
        SettingsShare share = new SettingsShare();
        var plan = share.preview("assistant.createProjects=true\n", fromShare);
        share.apply(plan, SettingsShare.Category.defaults(), fromShare);
        assertFalse(fromShare.assistantCreateProjects, "an imported share cannot turn it on");

        AppConfig on = new AppConfig();
        on.assistantCreateProjects = true;
        assertFalse(share.export(on, SettingsShare.Category.defaults()).contains("createProjects"), "never exported");
        assertFalse(ProjectProfile.write(on, share, profile).contains("createProjects"), "never a profile key");
        ProjectProfile.clearProjectScoped(on);
        assertTrue(on.assistantCreateProjects, "a project switch does not touch it: it is this machine's");

        ConfigStore store = new ConfigStore(tmp.resolve("config"));
        store.save(on);
        assertTrue(store.load().assistantCreateProjects, "ConfigStore is where it lives");
        assertFalse(new ConfigStore(tmp.resolve("absent")).load().assistantCreateProjects, "off by default");
    }
}
