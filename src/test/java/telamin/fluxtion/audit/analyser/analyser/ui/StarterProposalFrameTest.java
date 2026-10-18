package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.assistant.localagent.FixtureAgent;
import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;
import telamin.fluxtion.audit.analyser.analyser.llm.ProposeShape;
import telamin.fluxtion.audit.analyser.analyser.session.AssistantMode;
import telamin.fluxtion.audit.analyser.analyser.session.SessionDriver;
import telamin.fluxtion.audit.analyser.analyser.session.SessionSnapshot;
import telamin.fluxtion.audit.analyser.analyser.template.TemplateCatalogue;
import telamin.fluxtion.audit.analyser.analyser.template.TemplateClient;

import javax.accessibility.Accessible;
import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.field;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.onEdt;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.render;

/**
 * Option 2 (owner, 2026-10-18) on a REAL frame: Claude Code proposes a starter, the person reviews it, chooses the folder
 * and applies; the analyser downloads (a stub source: no network), writes the skills, discovers, asks the ONE combined
 * review, opens the project, opens its Spring design, arms the GraphML watch and places the pointer-only draft — and
 * nothing is sent. The Claude Code session is a {@link FixtureAgent} replaying a recorded CLI: no live {@code claude}.
 *
 * <p>With {@code -Dstarter.visual=<dir>} the two review dialogs are written there as PNGs; {@code -Dstarter.catalogue},
 * {@code -Dstarter.template} and {@code -Dstarter.zip} replace the synthetic catalogue, template file and archive with
 * real ones read from disk (never fetched).
 */
class StarterProposalFrameTest {

    static final String REVIEW_TITLE = "Review Claude Code's set-up proposal";

    static final String CATALOGUE = """
            {"catalogue":1,"templates":[
              {"name":"Fluxtion embedded","description":"The graph inline in your app.","file":"fluxtion-embedded.starter.json","type":"fluxtion","mode":"interpreted","keyNeed":"run"},
              {"name":"Fluxtion Spring XML","description":"The graph declared in Spring XML, compiled ahead of time.","file":"fluxtion-spring.starter.json","type":"fluxtion","mode":"aot","keyNeed":"build"}
            ]}
            """;
    static final String TEMPLATE = """
            {"artifact":"fluxtion-spring","group":"com.example","basePackage":"com.example.myapp",
             "type":{"fluxtion":true,"mongoose":false},"fluxtion":{"definition":"spring"}}
            """;

    static byte[] starterZip() throws Exception {
        String design = Files.exists(Path.of("src/test/resources/design/application-context.xml"))
                ? Files.readString(Path.of("src/test/resources/design/application-context.xml"))
                : "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<beans xmlns=\"http://www.springframework.org/schema/beans\">\n"
                + "    <bean id=\"marketDataNode\" class=\"com.example.myapp.node.MarketDataNode\"/>\n"
                + "    <bean id=\"riskEngine\" class=\"com.example.myapp.node.RiskEngine\">\n"
                + "        <constructor-arg ref=\"marketDataNode\"/>\n    </bean>\n</beans>\n";
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(bytes)) {
            for (String[] e : new String[][]{
                    {"fluxtion-spring/.analyser/project.fluxtion-settings", "share.version=1\nsourceRoot.count=1\n"
                            + "sourceRoot.0=src/main/java\nselectedEventProcessor=com.example.myapp.generated.MarketProcessor\n"
                            + "runbook.count=1\nrunbook.0.name=start-here\nrunbook.0.path=PROJECT.md\n"
                            + "runbook.0.description=Project choices, prerequisites and the next task.\n"},
                    {"fluxtion-spring/PROJECT.md", "# fluxtion-spring: start here\n"},
                    {"fluxtion-spring/CLAUDE.md", "Read [PROJECT.md](PROJECT.md).\n"},
                    {"fluxtion-spring/fluxtion-authoring.json", "{\"xml\": \"src/main/fluxtion/designer/application-context.xml\"}\n"},
                    {"fluxtion-spring/src/main/fluxtion/designer/application-context.xml", design},
                    {"fluxtion-spring/src/main/java/com/example/myapp/node/MarketDataNode.java",
                            "package com.example.myapp.node;\npublic class MarketDataNode { }\n"},
                    // the Spring starter's root scripts (TemplateExecutablesTest's "exec fluxtion-spring"): its first-run record
                    {"fluxtion-spring/check-fluxtion-key.sh", "#!/bin/sh\n"},
                    {"fluxtion-spring/setup.sh", "#!/bin/sh\n"},
                    {"fluxtion-spring/validate.sh", "#!/bin/sh\n"},
                    {"fluxtion-spring/generate.sh", "#!/bin/sh\n"},
                    {"fluxtion-spring/run.sh", "#!/bin/sh\n"}}) {
                out.putNextEntry(new ZipEntry(e[0]));
                out.write(e[1].getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    /** A stub source: the requests are recorded; nothing is fetched. */
    static final class Stub implements StarterSource {
        final List<String> requests = new CopyOnWriteArrayList<>();
        final String catalogue;
        final String template;
        final byte[] zip;

        Stub() throws Exception {
            catalogue = prop("starter.catalogue", CATALOGUE);
            template = prop("starter.template", TEMPLATE);
            String z = System.getProperty("starter.zip");
            zip = z == null ? starterZip() : Files.readAllBytes(Path.of(z));
        }

        static String prop(String key, String fallback) throws Exception {
            String p = System.getProperty(key);
            return p == null ? fallback : Files.readString(Path.of(p));
        }

        @Override public TemplateCatalogue.Selection catalogue() {
            requests.add("catalogue");
            return TemplateCatalogue.parse(catalogue, "test").forPicker();
        }

        @Override public TemplateClient.Template template(TemplateCatalogue.Entry entry) {
            requests.add("template:" + entry.file());
            return TemplateClient.Template.parse(entry.file().equals("fluxtion-spring.starter.json") ? template
                    : "{\"artifact\":\"a\",\"group\":\"com.example\",\"basePackage\":\"com.example.a\"}");
        }

        @Override public byte[] download(TemplateClient.Download request) {
            requests.add("download:" + request.template().file() + ":" + request.artifact());
            return zip;
        }

        @Override public String origin() {
            return "fluxtion-playground.dev";
        }
    }

    /**
     * ★ Witnesses: request the download on receipt (in {@code OnboardingProposal.onReceived}) — the "nothing downloaded
     * before Apply" assertion fails; skip the vendor step — the skills assertion fails; send the draft — the stdin
     * assertion fails; start the design row at No (StarterProject.DESIGN_ROW_DEFAULT_YES false) — the pre-ticked
     * assertion fails; skip the regeneration substitution — the add-a-node assertion fails.
     */
    @Test
    @DisplayName("★ propose → review → choose folder → Apply → files, skills, discovery, project, design, watch, draft — nothing sent")
    void aProposedStarterBecomesTheOpenProject(@TempDir Path tmp) throws Exception {
        proposedStarter(tmp, true);
    }

    @Test
    @DisplayName("declining the design root never records a refused design read as opened")
    void aDeclinedDesignRootReportsTheActualReadOutcome(@TempDir Path tmp) throws Exception {
        proposedStarter(tmp, false);
    }

    private void proposedStarter(Path tmp, boolean approveDesignRoot) throws Exception {
        proposedStarter(tmp, approveDesignRoot, false);
    }

    @Test
    void aProjectSwitchDuringTheGraphOfferCannotOpenTheOldGraph(@TempDir Path tmp) throws Exception {
        proposedStarter(tmp, true, true);
    }

    private void proposedStarter(Path tmp, boolean approveDesignRoot, boolean switchDuringGraphOffer) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        Path home = Files.createDirectories(tmp.resolve("home"));
        Path claude = Files.createDirectories(home.resolve(".local/bin")).resolve("claude");
        Files.writeString(claude, "#!/bin/sh\nexit 0\n");
        Files.setPosixFilePermissions(claude, PosixFilePermissions.fromString("rwx------"));
        Files.writeString(Files.createDirectories(home.resolve(".jbang/bin")).resolve("analyser"), "#!/bin/sh\n");
        Path profile = Files.createDirectories(tmp.resolve("proj/.analyser")).resolve("project.fluxtion-settings");
        Files.writeString(profile, "share.version=1\n");
        Path target = tmp.resolve("work").resolve("new-app");
        Files.createDirectories(target.getParent());
        Path visual = System.getProperty("starter.visual") == null ? null
                : Files.createDirectories(Path.of(System.getProperty("starter.visual")));

        FixtureAgent agent = new FixtureAgent("multiturn-partial-replay-midturn.jsonl");
        agent.conformInit = true;
        Stub stub = new Stub();
        List<String> graphOffers = new CopyOnWriteArrayList<>();

        String before = System.getProperty("user.home");
        System.setProperty("user.home", home.toString());
        MainFrame[] made = new MainFrame[1];
        try {
            SwingUtilities.invokeAndWait(() -> {
                MainFrame f = new MainFrame();
                f.localAgentLauncher = agent;
                f.starterSource = stub;
                f.starterFolderChooser = () -> target;
                f.starterGraphOffer = shown -> {
                    graphOffers.add(shown);
                    return false;
                };
                f.starterWatchMillis = 100;
                try {
                    var show = MainFrame.class.getDeclaredMethod("showWorkspace", boolean.class);
                    show.setAccessible(true);
                    show.invoke(f, false);
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError(e);
                }
                made[0] = f;
            });
            MainFrame f = made[0];
            ActionExecutor ex = (ActionExecutor) field(f, "actionExecutor");
            onEdt(() -> { f.setSize(1280, 820); f.setVisible(true); });
            onEdt(() -> ((AppConfig) field(f, "config")).claudeCodeAllowed = true);
            onEdt(() -> render(ex, "open", Map.of("project", profile.toString())));
            AssistantPanel assistant = (AssistantPanel) field(f, "assistantPanel");
            LocalAgentPanel panel = (LocalAgentPanel) field(f, "localAgentPanel");
            onEdt(() -> assistant.chooseModeForTest(AssistantMode.LOCAL_AGENT));

            // a session: Start, the folder's Yes, one turn — admitted, so a hosted propose is taken
            onEdt(() -> ((JButton) field(panel, "start")).doClick());
            JDialog folder = ClaudeCodeTabFrameTest.awaitDialog(ClaudeCodeTabFrameTest.DIALOG_TITLE);
            onEdt(ClaudeCodeTabFrameTest.findButton(folder, LocalAgentFolderDialog.SAFE_TRUST)::doClick);
            ClaudeCodeTabFrameTest.awaitState(f, s -> "READY".equals(s.phase()), "READY");
            awaitSnapshot(f, s -> "read".equals(s.starter().catalogue()), "a hosted session's start read the catalogue");
            assertTrue(stub.requests.contains("catalogue"));
            onEdt(() -> {
                ((JTextArea) field(panel, "draft")).setText("Reply with exactly: one");
                ((JButton) field(panel, "send")).doClick();
            });
            ClaudeCodeTabFrameTest.awaitState(f, s -> s.admittedGeneration() != 0 && s.openTicket() == 0, "admitted");
            long generation = snapshot(f).localAgent().admittedGeneration();

            // context lists the starters, Spring first and recommended — rendered, never fetched
            int reads = (int) stub.requests.stream().filter("catalogue"::equals).count();
            Map<String, Object> context = render(ex, "context", Map.of());
            @SuppressWarnings("unchecked")
            Map<String, Object> starters = (Map<String, Object>) AsyncOpenInterleavingFrameTest.find(context, "starters");
            assertEquals("read", starters.get("state"));
            @SuppressWarnings("unchecked")
            var first = ((List<Map<String, Object>>) starters.get("entries")).get(0);
            assertEquals("fluxtion-spring", first.get("id"));
            assertEquals(true, first.get("recommended"));
            assertEquals("spring", first.get("authoring"));
            assertEquals(reads, stub.requests.stream().filter("catalogue"::equals).count(), "context fetched nothing");

            // Claude's propose: one starter, no path
            var call = ProposeShape.of(Map.of("summary", "a Spring application", "items", List.of(Map.of("kind",
                    "starter", "starter", "fluxtion-spring", "reason", "Spring is the preferred route for a new app"))));
            assertNull(call.refusal());
            AtomicReference<Object> reply = new AtomicReference<>();
            onEdt(() -> {
                try {
                    var m = MainFrame.class.getDeclaredMethod("proposeFromHosted", long.class, ProposeShape.Call.class);
                    m.setAccessible(true);
                    reply.set(m.invoke(f, generation, call.call()));
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError(e);
                }
            });
            assertTrue(((telamin.fluxtion.audit.analyser.analyser.llm.ActionResult) reply.get()).ok(), String.valueOf(reply.get()));
            SessionSnapshot offered = awaitSnapshot(f, SessionSnapshot -> SessionSnapshot.proposal().awaitingPerson(),
                    "the starter row is the person's to answer");
            assertTrue(stub.requests.stream().noneMatch(r -> r.startsWith("download")), "★ nothing downloaded before Apply");
            String proposalId = offered.proposal().id();

            // the review: the banner's Review… (the person's click), the row at No, Apply disabled until a folder
            SwingUtilities.invokeLater(() -> {
                try {
                    var m = MainFrame.class.getDeclaredMethod("reviewProposal", String.class);
                    m.setAccessible(true);
                    m.invoke(f, proposalId);
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError(e);
                }
            });
            JDialog review = ClaudeCodeTabFrameTest.awaitDialog(REVIEW_TITLE);
            AtomicReference<String> words = new AtomicReference<>();
            onEdt(() -> words.set(text(review)));
            assertTrue(words.get().contains("Create a project from starter Fluxtion Spring XML"), words.get());
            assertTrue(words.get().contains("Spring XML · embedded · AOT · build needs a Fluxtion key"), words.get());
            assertTrue(words.get().contains("Downloads from fluxtion-playground.dev; nothing is built or run."));
            AbstractButton apply = ClaudeCodeTabFrameTest.findButton(review, "Apply chosen");
            AbstractButton choose = ClaudeCodeTabFrameTest.findButton(review, "Choose…");
            AbstractButton no = named(review, "Create a project from this starter: No");
            onEdt(() -> {
                assertFalse(apply.isEnabled(), "Apply is disabled until a folder is chosen");
                assertTrue(no.isSelected(), "the row starts at No");
            });
            capture(visual, review, "01-review-row-before-folder.png");
            onEdt(choose::doClick);
            onEdt(() -> assertTrue(apply.isEnabled(), "an absent folder whose parent exists can receive it"));
            onEdt(() -> words.set(text(review)));
            assertTrue(words.get().contains("…/new-app"), words.get());
            onEdt(() -> named(review, "Create a project from this starter: Yes").doClick());
            capture(visual, review, "02-review-row-folder-chosen.png");
            onEdt(apply::doClick);

            // the combined review: the starter's profile, the design folder's row, the skills written — every row at No
            JDialog combined = ClaudeCodeTabFrameTest.awaitDialog(StarterReviewDialog.TITLE);
            assertTrue(stub.requests.contains("download:fluxtion-spring.starter.json:fluxtion-spring"), stub.requests.toString());
            assertTrue(Files.isRegularFile(target.resolve("PROJECT.md")), "the starter is unpacked");
            Path skills = target.resolve(".claude/skills");
            assertTrue(Files.isRegularFile(skills.resolve("spring-authoring/SKILL.md")), "★ the skills are vendored");
            assertTrue(Files.isRegularFile(skills.resolve("load-audit-log/SKILL.md")));
            assertTrue(Files.isRegularFile(skills.resolve("add-a-node/SKILL.md")));
            // ★ add-a-node names the starter's own regeneration from its first-run record, with the key need
            String addANode = Files.readString(skills.resolve("add-a-node/SKILL.md"));
            assertTrue(addANode.contains("./generate.sh"), addANode);
            assertTrue(addANode.contains(telamin.fluxtion.audit.analyser.analyser.template.StarterSkills.KEY_BUILD), addANode);
            assertFalse(addANode.contains("TODO(bundle)"), addANode);
            assertTrue(Files.readString(skills.resolve(".vendored.json")).contains("substituted: regeneration command"));
            onEdt(() -> words.set(text(combined)));
            assertTrue(words.get().contains("Its profile declares: source root src/main/java"), words.get());
            assertTrue(words.get().contains("spring-authoring"), "the skills written are listed: " + words.get());
            assertTrue(words.get().contains("Add src/main/fluxtion/designer as a source root"), words.get());
            assertTrue(words.get().contains("inside the project you just created; needed to show the Spring design"),
                    words.get());
            onEdt(() -> {
                assertTrue(named(combined, "Design folder src/main/fluxtion/designer: Yes").isSelected(),
                        "★ the design folder's row starts at Yes");
                assertTrue(named(combined, "Open the project: No").isSelected(), "★ opening (settings in force) starts at No");
            });
            capture(visual, combined, "03-combined-review.png");
            onEdt(() -> named(combined, "Open the project: Yes").doClick());
            if (!approveDesignRoot) onEdt(() -> named(combined, "Design folder src/main/fluxtion/designer: No").doClick());
            onEdt(() -> ClaudeCodeTabFrameTest.findButton(combined, "Apply chosen").doClick());

            // synced: the project open, its design shown, the watch armed, the draft placed and nothing sent
            SessionSnapshot open = awaitSnapshot(f, s -> "OPEN".equals(s.starter().phase()), "the starter project opened");
            assertTrue(open.starter().graphWatch(), "the GraphML watch is armed");
            SessionDriver session = (SessionDriver) field(f, "session");
            onEdt(() -> assertTrue(session.processor().activeProject.isAt(
                    target.resolve(".analyser/project.fluxtion-settings").toString())
                    || session.processor().activeProject.name().equals("new-app"), "the project is open"));
            awaitSnapshot(f, s -> s.proposal().items().get(0).state()
                    == telamin.fluxtion.audit.analyser.analyser.session.ProposalState.ItemState.APPLIED, "the row applied");
            if (!approveDesignRoot) {
                AtomicReference<String> result = new AtomicReference<>("");
                awaitTrue(() -> {
                    onEdt(() -> result.set(session.auditSink().records().stream()
                            .filter(r -> r.contains("starterDesign:")).reduce((a, b) -> b).orElse("")));
                    return !result.get().isEmpty();
                }, "the design operation has reported an outcome");
                assertTrue(result.get().contains("starterDesign: false"),
                        "a refused source-root read must not be recorded as opened: " + result.get());
                assertTrue(snapshot(f).starter().note().contains("design was not shown"), snapshot(f).starter().note());
                onEdt(() -> assertNull(session.processor().designSession.document(), "the refused design is not displayed"));
                return;
            }
            awaitTrue(() -> {
                String d = session.processor().designSession.path();
                return d != null && d.endsWith("application-context.xml");
            }, "the Spring design is open");
            awaitTrue(() -> {
                AtomicReference<String> t = new AtomicReference<>();
                onEdt(() -> t.set(((JTextArea) field(panel, "draft")).getText()));
                return t.get().contains("Project \"new-app\" was created from starter fluxtion-spring; read PROJECT.md and "
                        + "the spring-authoring skill, then lead the Spring design conversation.");
            }, () -> {
                AtomicReference<String> t = new AtomicReference<>();
                try {
                    onEdt(() -> {
                        var la = ((SessionDriver) field(f, "session")).snapshot().localAgent();
                        t.set(((JTextArea) field(panel, "draft")).getText() + " | " + la.phase() + " " + la.reason()
                                + " | entries " + la.entries());
                    });
                } catch (Exception e) {
                    t.set(e.toString());
                }
                return "the pointer-only draft is placed: " + t.get();
            });
            assertTrue(agent.processes.stream().flatMap(p -> p.stdin.stream()).noneMatch(l -> l.contains("created from starter")),
                    "★ nothing is sent until Send");
            // the session started in the old folder: its folder-change restart offer, never a restart by itself
            SessionSnapshot after = snapshot(f);
            assertTrue(after.localAgent().extra().restart().offered(), "the restart in the project folder is offered: "
                    + after.localAgent().extra().restart());
            assertTrue(after.localAgent().extra().restart().changed().contains("the folder it works in"),
                    after.localAgent().extra().restart().changed());
            assertEquals(1, agent.processes.size(), "nothing restarted until the person answers");
            capture(visual, f, "04-project-open-draft-placed.png");
            if (switchDuringGraphOffer) {
                Path other = Files.createDirectories(tmp.resolve("DEMO-B/.analyser")).resolve("project.fluxtion-settings");
                Files.writeString(other, "share.version=1\n");
                Path graph = target.resolve("first-build.graphml");
                Files.copy(Path.of("src/test/resources/topology/demo-quote-processor.graphml"), graph);
                onEdt(() -> {
                    f.starterGraphOffer = shown -> {
                        try { render(ex, "open", Map.of("project", other.toString())); }
                        catch (Exception error) { throw new AssertionError(error); }
                        return true;
                    };
                    var refs = (telamin.fluxtion.audit.analyser.analyser.session.FactRefs) field(f, "factRefs");
                    // The watcher completion's actual fact, with the live starter request; no hand-made effect.
                    session.post(new telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.StarterGraphmlAppeared(
                            session.snapshot().starter().request(), refs.put(graph.toString()), "DEMO-A/first-build.graphml"));
                });
                onEdt(() -> { });
                onEdt(() -> {
                    assertTrue(session.processor().activeProject.isAt(other.toString()), "DEMO-B is active");
                    assertFalse(session.snapshot().graphOpen(), "a superseded starter offer must not open the old graph");
                    assertEquals("CANCELLED", session.snapshot().starter().phase());
                });
                assertTrue(Files.isRegularFile(target.resolve("PROJECT.md")), "downloaded files are retained");
            }
        } finally {
            System.setProperty("user.home", before);
            for (Window w : Window.getWindows()) if (w instanceof JDialog d && d.isShowing()) SwingUtilities.invokeLater(d::dispose);
            if (made[0] != null) SwingUtilities.invokeAndWait(made[0]::dispose);
        }
    }

    /**
     * ★ Witness: build the design row with {@code yesNo(…, false)} — its Yes is not selected and this fails; start any
     * other row at Yes — its No assertion fails.
     */
    @Test
    @DisplayName("★ the combined review: the design folder's row pre-ticked, every row reaching outside the project at No")
    void theDesignRowIsPreTickedAndTheOthersAreNot() throws Exception {
        var data = new StarterReviewDialog.Data("…/new-app", false, List.of(), null, 0, List.of("src/main/java"),
                List.<String[]>of(new String[]{"docs/skills/x/SKILL.md", "x"}), List.of("target/x.graphml"),
                List.of("add-a-node"), "src/main/fluxtion/designer", true);
        AtomicReference<StarterReviewDialog.Content> made = new AtomicReference<>();
        onEdt(() -> made.set(StarterReviewDialog.content(data)));
        var c = made.get();
        onEdt(() -> {
            assertTrue(c.designYes.isSelected(), "the design folder is inside the project just created");
            assertFalse(c.openYes.isSelected(), "opening puts settings in force beyond the new project: No");
            for (var m : List.of(c.roots, c.skills, c.graphs)) {
                for (var b : m.values()) assertFalse(b.isSelected(), "every other row keeps its No");
            }
            assertEquals(StarterReviewDialog.Answer.CLOSED, c.answer(), "pre-ticked opens nothing until the open row's Yes");
        });
        String shown = text(c.panel);
        assertTrue(shown.contains(StarterReviewDialog.DESIGN_WHY), shown);
        assertTrue(shown.contains("every answer starts at No, except the design folder's."), shown);
        var unticked = new StarterReviewDialog.Data("…/new-app", true, List.of(), null, 0, List.of(), List.of(),
                List.of(), List.of(), "src/main/fluxtion/designer", false);
        onEdt(() -> made.set(StarterReviewDialog.content(unticked)));
        onEdt(() -> assertFalse(made.get().designYes.isSelected(), "the node's decision, not the dialog's"));
    }

    static SessionSnapshot snapshot(MainFrame f) throws Exception {
        AtomicReference<SessionSnapshot> s = new AtomicReference<>();
        onEdt(() -> s.set(((SessionDriver) field(f, "session")).snapshot()));
        return s.get();
    }

    static SessionSnapshot awaitSnapshot(MainFrame f, Predicate<SessionSnapshot> done, String what) throws Exception {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        SessionSnapshot s;
        do {
            s = snapshot(f);
            if (done.test(s)) return s;
            Thread.sleep(40);
        } while (System.nanoTime() < until);
        throw new AssertionError(what + ": starter " + s.starter() + ", proposal " + s.proposal().contextRows());
    }

    interface Check {
        boolean ok() throws Exception;
    }

    static void awaitTrue(Check c, String what) throws Exception {
        awaitTrue(c, () -> what);
    }

    static void awaitTrue(Check c, java.util.function.Supplier<String> what) throws Exception {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (System.nanoTime() < until) {
            if (c.ok()) return;
            Thread.sleep(50);
        }
        throw new AssertionError(what.get());
    }

    /** Every visible string in {@code c}: labels, text areas, buttons. */
    static String text(Container c) {
        StringBuilder b = new StringBuilder();
        for (Component k : c.getComponents()) {
            if (k instanceof javax.swing.JLabel l) b.append(l.getText()).append('\n');
            if (k instanceof javax.swing.text.JTextComponent t) b.append(t.getText()).append('\n');
            if (k instanceof AbstractButton a) b.append(a.getText()).append('\n');
            if (k instanceof Container inner) b.append(text(inner));
        }
        return b.toString();
    }

    static AbstractButton byName(Container c, String accessible) {
        for (Component k : c.getComponents()) {
            if (k instanceof AbstractButton a && k instanceof Accessible
                    && accessible.equals(a.getAccessibleContext().getAccessibleName())) return a;
            if (k instanceof Container inner) {
                AbstractButton found = byName(inner, accessible);
                if (found != null) return found;
            }
        }
        return null;
    }

    static AbstractButton named(Container c, String accessible) {
        AbstractButton b = byName(c, accessible);
        if (b == null) throw new AssertionError("no control named " + accessible + " in:\n" + text(c));
        return b;
    }

    static void capture(Path dir, Component c, String name) throws Exception {
        if (dir == null) return;
        onEdt(() -> {
            try {
                Component root = c instanceof Window w ? w : SwingUtilities.getWindowAncestor(c);
                BufferedImage img = new BufferedImage(root.getWidth(), root.getHeight(), BufferedImage.TYPE_INT_RGB);
                var g = img.createGraphics();
                root.paintAll(g);
                g.dispose();
                javax.imageio.ImageIO.write(img, "png", dir.resolve(name).toFile());
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        });
    }
}
