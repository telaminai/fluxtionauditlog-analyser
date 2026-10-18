package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.assistant.localagent.LocalAgentTranscript;
import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;
import telamin.fluxtion.audit.analyser.analyser.config.SecretFolders;
import telamin.fluxtion.audit.analyser.analyser.config.SettingsShare;
import telamin.fluxtion.audit.analyser.analyser.config.SourceRootPolicy;
import telamin.fluxtion.audit.analyser.analyser.config.SourceRootPolicy.Caller;
import telamin.fluxtion.audit.analyser.analyser.filter.FilterState;
import telamin.fluxtion.audit.analyser.analyser.llm.ActionDispatcher;
import telamin.fluxtion.audit.analyser.analyser.llm.ActionResult;
import telamin.fluxtion.audit.analyser.analyser.llm.AppControl;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Owner decision 2026-10-18 (P5, source-root confinement). Tier 1, every caller: never the home folder, an ancestor of
 * it, or a credential folder ({@code ~/.ssh}, {@code ~/.aws}, {@code ~/.m2}, {@code ~/.config}, {@code ~/.gnupg}), a folder
 * inside one, or one containing one — links resolved. Tier 2, assistants only: outside the open project — the in-app
 * assistant is refused, Claude Code's dialog says "outside this project". External clients get tier 1 only. Run under a
 * made-up home folder ({@code user.home} set for the test and put back).
 */
class SourceRootConfinementTest {

    @TempDir
    Path tmp;
    private Path home;
    private Path project;
    private Path elsewhere;
    private String realHome;

    @BeforeEach
    void up() throws Exception {
        home = Files.createDirectories(tmp.resolve("home/u"));
        for (String c : SecretFolders.CREDENTIAL_FOLDERS) Files.createDirectories(home.resolve(c).resolve("inner"));
        project = Files.createDirectories(home.resolve("work/proj/src/main/java"));
        elsewhere = Files.createDirectories(tmp.resolve("libs/vendor-src"));
        realHome = System.getProperty("user.home");
        System.setProperty("user.home", home.toString());
    }

    @AfterEach
    void down() {
        System.setProperty("user.home", realHome);
    }

    private Path projectRoot() {
        return home.resolve("work/proj");
    }

    /** ★ Witness: drop a name from SecretFolders.CREDENTIAL_FOLDERS, or the HOME_OR_ANCESTOR test — its row fails. */
    @Test
    @DisplayName("★ tier 1 refuses, for an external caller: home, an ancestor, each credential folder, inside one, and a link to one")
    void tierOneForEveryCaller() throws Exception {
        List<Path> refused = new ArrayList<>(List.of(home, home.getParent(), tmp, Path.of("/")));
        for (String c : SecretFolders.CREDENTIAL_FOLDERS) {
            refused.add(home.resolve(c));
            if (!".m2".equals(c)) refused.add(home.resolve(c).resolve("inner"));   // below ~/.m2 is allowed (next test)
        }
        Path link = Files.createSymbolicLink(tmp.resolve("innocent-looking"), home.resolve(".ssh"));
        refused.add(link);
        refused.add(link.resolve("inner"));
        if (Files.isDirectory(home.resolve(".SSH"))) refused.add(home.resolve(".SSH"));   // a case-insensitive disk
        for (Path p : refused) {
            for (Caller caller : Caller.values()) {
                var v = SourceRootPolicy.decide(p, home, projectRoot(), caller);
                assertFalse(v.allowed(), caller + " " + p);
                assertTrue(v.reason() == SourceRootPolicy.Reason.HOME_FOLDER
                        || v.reason() == SourceRootPolicy.Reason.CREDENTIAL_FOLDER, p + " " + v);
            }
        }
        assertEquals(SourceRootPolicy.Reason.HOME_FOLDER, SourceRootPolicy.tier1(home, home));
        assertEquals(SourceRootPolicy.Reason.CREDENTIAL_FOLDER, SourceRootPolicy.tier1(link, home), "the link is resolved");
        assertTrue(SourceRootPolicy.words(SourceRootPolicy.Reason.CREDENTIAL_FOLDER, link).contains("credential folder"));
    }

    /**
     * ★ Owner correction 2026-10-18: ~/.m2 holds settings.xml and settings-security.xml at its top level only, so
     * ~/.m2/repository (source jars, poms) is an allowed root for every caller while ~/.m2 itself, and a link to it, is
     * refused. Witness: the earlier, broader rule (drop ".m2" from SecretFolders.TOP_LEVEL_ONLY) — the repository rows
     * fail.
     */
    @Test
    @DisplayName("★ ~/.m2/repository is allowed for every caller; ~/.m2 itself (or a link to it) is refused; ~/.config/x is not allowed")
    void mavenRepositoryIsAllowed() throws Exception {
        Path repo = Files.createDirectories(home.resolve(".m2/repository/com/acme/demo"));
        Files.writeString(home.resolve(".m2/settings.xml"), "<settings/>");
        for (Caller caller : List.of(Caller.PERSON, Caller.EXTERNAL)) {
            assertTrue(SourceRootPolicy.decide(home.resolve(".m2/repository"), home, null, caller).allowed(), caller.name());
            assertTrue(SourceRootPolicy.decide(repo, home, null, caller).allowed(), caller.name());
        }
        // assistants: tier 1 lets it through; tier 2 is only about the project
        assertNull(SourceRootPolicy.tier1(home.resolve(".m2/repository"), home));
        assertTrue(SourceRootPolicy.decide(home.resolve(".m2/repository"), home, home.resolve(".m2/repository"),
                Caller.IN_APP_ASSISTANT).allowed());
        assertTrue(SourceRootPolicy.decide(repo, home, projectRoot(), Caller.HOSTED_CLAUDE_CODE).allowed());
        assertEquals(SourceRootPolicy.Reason.CREDENTIAL_FOLDER, SourceRootPolicy.tier1(home.resolve(".m2"), home));
        assertEquals(SourceRootPolicy.Reason.CREDENTIAL_FOLDER, SourceRootPolicy.tier1(home.resolve(".m2/repository/.."), home));
        Path toM2 = Files.createSymbolicLink(tmp.resolve("maven-home"), home.resolve(".m2"));
        assertEquals(SourceRootPolicy.Reason.CREDENTIAL_FOLDER, SourceRootPolicy.tier1(toM2, home), "a link to ~/.m2");
        assertEquals(SourceRootPolicy.Reason.CREDENTIAL_FOLDER, SourceRootPolicy.tier1(home.resolve(".config/inner"), home),
                "~/.config subfolders hold app credentials");
        assertEquals(SourceRootPolicy.Reason.CREDENTIAL_FOLDER, SourceRootPolicy.tier1(home.resolve(".ssh/inner"), home));
        // no allowed root can reach ~/.m2/settings*.xml: it sits directly in ~/.m2, which is never a root
        for (Path root : List.of(home.resolve(".m2/repository"), repo)) {
            assertFalse(SecretFolders.within(home.resolve(".m2/settings.xml"), SecretFolders.canonical(root)), root.toString());
        }
    }

    @Test
    @DisplayName("on macOS a credential folder named in another case is that folder")
    void caseIsCanonicalOnMac() {
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getProperty("os.name", "").toLowerCase().startsWith("mac"));
        assertEquals(SourceRootPolicy.Reason.CREDENTIAL_FOLDER, SourceRootPolicy.tier1(home.resolve(".SSH"), home));
        assertEquals(SourceRootPolicy.Reason.HOME_FOLDER,
                SourceRootPolicy.tier1(Path.of(home.toString().toUpperCase()), home));
    }

    /** ★ Witness: allow IN_APP_ASSISTANT outside the project in SourceRootPolicy.decide — the refusal assertion fails. */
    @Test
    @DisplayName("★ tier 2: the in-app assistant is refused outside the project; Claude Code is allowed and told; external is tier 1 only")
    void tierTwoForAssistants() {
        var inApp = SourceRootPolicy.decide(elsewhere, home, projectRoot(), Caller.IN_APP_ASSISTANT);
        assertFalse(inApp.allowed());
        assertEquals(SourceRootPolicy.Reason.OUTSIDE_PROJECT, inApp.reason());
        assertTrue(inApp.words().contains("add this folder yourself in Settings ▸ Sources"), inApp.words());
        var hosted = SourceRootPolicy.decide(elsewhere, home, projectRoot(), Caller.HOSTED_CLAUDE_CODE);
        assertTrue(hosted.allowed(), "the person's call, in the approval dialog");
        assertTrue(hosted.outsideProject());
        var external = SourceRootPolicy.decide(elsewhere, home, projectRoot(), Caller.EXTERNAL);
        assertTrue(external.allowed(), "no regression for an external client's outside, non-credential folder");
        assertFalse(external.outsideProject(), "tier 2 is the assistants' only");
        assertTrue(SourceRootPolicy.decide(project, home, projectRoot(), Caller.IN_APP_ASSISTANT).allowed(),
                "inside the project, the in-app assistant may add it");
        assertFalse(SourceRootPolicy.decide(project, home, null, Caller.IN_APP_ASSISTANT).allowed(),
                "with no project open, every folder is outside it");
    }

    /** The verb, through the real executor, for each caller as the plumbing names it. */
    private ActionResult sourceRootAdd(Path dir, Fake app) {
        HeapLogStore store = new HeapLogStore("");
        GraphTabs tabs = new GraphTabs();
        FilterState filter = new FilterState();
        tabs.bind(store, filter);
        ActionExecutor ex = new ActionExecutor(() -> store, () -> filter, tabs, new LogTablePanel(), (r, n, f, k) -> { });
        ex.bind(null, app);
        return ex.render("source_root", Map.of("add", List.of(dir.toString())));
    }

    /**
     * ★ Witnesses: in {@code ActionExecutor.doSourceRoot}, skip the policy — the external .ssh add reaches the app; in
     * {@code ActionExecutor.caller()}, ignore the assistant origin — the in-app outside add is admitted.
     */
    @Test
    @DisplayName("★ the source_root verb: external .ssh refused, external outside-project folder added, in-app outside refused")
    void theVerb() {
        Fake app = new Fake(projectRoot());
        ActionResult ssh = sourceRootAdd(home.resolve(".ssh"), app);
        assertFalse(ssh.ok());
        assertTrue(ssh.error().contains("CREDENTIAL_FOLDER"), ssh.error());
        assertTrue(app.added.isEmpty(), "nothing reached the settings");
        ActionResult lib = sourceRootAdd(elsewhere, app);
        assertTrue(lib.ok(), lib::toString);
        assertEquals(List.of(elsewhere.toString()), app.added, "an external client's outside folder still works");

        ActionExecutor.bindAssistantOrigin(new SessionEvents.AssistantActionOrigin(1, 1));
        try {
            assertEquals(Caller.IN_APP_ASSISTANT, ActionExecutor.caller());
            ActionResult inApp = sourceRootAdd(elsewhere.resolve("..").normalize(), app);
            assertFalse(inApp.ok());
            assertTrue(inApp.error().contains("Settings ▸ Sources"), inApp.error());
        } finally {
            ActionExecutor.bindAssistantOrigin(null);
        }
        assertEquals(Caller.EXTERNAL, ActionExecutor.caller());
    }

    /** ★ Witness: never set the hosted mark in ActionDispatcher.dispatch — the hosted call reads as EXTERNAL. */
    @Test
    @DisplayName("★ the caller comes from the existing plumbing: the hosted credential's dispatch, else an assistant origin, else external")
    void theCallerIsKnown() {
        List<Caller> seen = new ArrayList<>();
        ActionDispatcher d = new ActionDispatcher(false, null, () -> null, null, (action, params) -> {
            seen.add(ActionExecutor.caller());
            return ActionResult.ok(action, "result", Map.of());
        });
        d.dispatchHosted("{\"action\":\"source_root\",\"params\":{}}", 4);
        d.dispatch("{\"action\":\"source_root\",\"params\":{}}");
        assertEquals(List.of(Caller.HOSTED_CLAUDE_CODE, Caller.EXTERNAL), seen);
        assertFalse(ActionDispatcher.servingHosted(), "the mark ends with the call");
    }

    /** ★ Witness: drop sourceRootWhere from PermissionText's source_root sentence — "outside this project" is missing. */
    @Test
    @DisplayName("★ Claude Code's approval dialog says a folder is outside this project — and that a secret folder is refused")
    void theDialogSays() {
        var outside = PermissionText.of(new LocalAgentTranscript.Permission(
                        "mcp__fluxtion-analyser-hosted__analyser_source_root", "fluxtion-analyser-hosted", "x",
                        "{\"add\":[\"" + elsewhere + "\"]}"),
                SessionEvents.ToolClass.HOSTED_DESTRUCTIVE, "source_root", projectRoot());
        assertTrue(outside.title().endsWith("— outside this project"), outside.title());
        var inside = PermissionText.of(new LocalAgentTranscript.Permission(
                        "mcp__fluxtion-analyser-hosted__analyser_source_root", "fluxtion-analyser-hosted", "x",
                        "{\"add\":[\"" + project + "\"]}"),
                SessionEvents.ToolClass.HOSTED_DESTRUCTIVE, "source_root", projectRoot());
        assertFalse(inside.title().contains("outside"), inside.title());
        var secret = PermissionText.of(new LocalAgentTranscript.Permission(
                        "mcp__fluxtion-analyser-hosted__analyser_source_root", "fluxtion-analyser-hosted", "x",
                        "{\"add\":[\"" + home.resolve(".aws") + "\"]}"),
                SessionEvents.ToolClass.HOSTED_DESTRUCTIVE, "source_root", projectRoot());
        assertTrue(secret.title().contains("refused whatever you answer"), secret.title());
    }

    /**
     * ★ Profiles and proposals cannot sneak a tier-1 root in. Witnesses: drop keepAllowed from SettingsShare.apply — the
     * profile's .ssh root lands in the config; drop the SECRET_FOLDER case in ProposalPreview — the home item reads as a
     * directory.
     */
    @Test
    @DisplayName("★ a profile, a shared settings file and a proposal cannot carry a tier-1 root in")
    void profilesAndProposalsCannotSneakOneIn() {
        AppConfig src = new AppConfig();
        src.sourceRoots.addAll(List.of(home.resolve(".ssh").toString(), home.toString(), elsewhere.toString()));
        SettingsShare share = new SettingsShare(home.toString());
        String text = share.export(src, Set.of(SettingsShare.Category.SOURCE_ROOTS));
        AppConfig target = new AppConfig();
        share.apply(share.preview(text, target), Set.of(SettingsShare.Category.SOURCE_ROOTS), target);
        assertEquals(List.of(elsewhere.toString()), target.sourceRoots, "only the ordinary folder came in");

        // a set-up folder that is the home folder: its items are "inside" it, and still refused
        var items = ProposalPreview.check(home, List.of(new ProposalPreview.Item("1", "sourceRoot", ".", null),
                new ProposalPreview.Item("2", "sourceRoot", ".ssh", null)), x -> false);
        assertEquals("SECRET_FOLDER", items.get(0).refusal(), items.toString());
        assertEquals("SECRET_FOLDER", items.get(1).refusal(), items.toString());
        var ok = ProposalPreview.check(projectRoot(), List.of(new ProposalPreview.Item("3", "sourceRoot", "src/main/java",
                null)), x -> false);
        assertNull(ok.get(0).refusal(), ok.toString());
    }

    @Test
    @DisplayName("the loader's filter keeps order and drops only tier-1 roots")
    void keepAllowed() {
        List<String> refused = new ArrayList<>();
        var kept = SourceRootPolicy.keepAllowed(List.of(project.toString(), home.resolve(".m2").toString(),
                elsewhere.toString()), home, refused);
        assertEquals(List.of(project.toString(), elsewhere.toString()), kept);
        assertEquals(List.of(home.resolve(".m2").toString()), refused);
    }

    /** The app side of source_root: records what the verb added. */
    private static final class Fake implements AppControl {
        final List<String> added = new ArrayList<>();
        final Path root;

        Fake(Path root) {
            this.root = root;
        }

        @Override public Path projectRoot() { return root; }
        @Override public ActionResult openLog(String path) { return ActionResult.error("no"); }
        @Override public ActionResult openGraphml(String path) { return ActionResult.error("no"); }
        @Override public ActionResult selectProcessor(String fqn) { return ActionResult.error("no"); }
        @Override public List<String> sourceRoots() { return List.copyOf(added); }
        @Override public boolean addSourceRoot(String path) { return added.add(path); }
        @Override public boolean removeSourceRoot(String path) { return added.remove(path); }
        @Override public ActionResult screenshot(String path, String scope) { return ActionResult.error("no"); }
        @Override public ActionResult context() { return ActionResult.error("no"); }
        @Override public boolean showTab(String name) { return false; }
        @Override public ActionResult exportFinding(String path, Integer recordIndex, String title, String graph,
                                                    boolean withTopology) {
            return ActionResult.error("no");
        }
    }
}
