package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.assistant.localagent.FixtureAgent;
import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;
import telamin.fluxtion.audit.analyser.analyser.session.AssistantMode;
import telamin.fluxtion.audit.analyser.analyser.session.AssistantProfile;
import telamin.fluxtion.audit.analyser.analyser.session.LocalAgentState;
import telamin.fluxtion.audit.analyser.analyser.session.SessionDriver;
import telamin.fluxtion.audit.analyser.analyser.ui.Usability.Finder;
import telamin.fluxtion.audit.analyser.analyser.ui.Usability.Located;
import telamin.fluxtion.audit.analyser.analyser.ui.Usability.Run;
import telamin.fluxtion.audit.analyser.analyser.ui.Usability.TaskFailed;

import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.field;
import static telamin.fluxtion.audit.analyser.analyser.ui.Usability.button;
import static telamin.fluxtion.audit.analyser.analyser.ui.Usability.buttonInWindow;
import static telamin.fluxtion.audit.analyser.analyser.ui.Usability.onEdt;

/**
 * The usability scorecard's battery: eight scripted tasks and three key states, each in a REAL {@link MainFrame} at
 * 1200×800 under its own isolated {@code user.home}, with a stub {@code claude} on the home's PATH and the test suite's
 * {@link FixtureAgent} in place of Claude Code (recorded streams; no live prompt is ever sent). Run by
 * {@code tools/usability/run.py} against the built jar and by {@link UsabilityBudgetFrameTest} in CI.
 *
 * <p>Preconditions (a log open, an assistant chosen, a filter set) are set the quickest way, including the action
 * socket's own executor; they are NOT counted. Every counted act is a Swing event on a control found by what it shows.
 */
final class UsabilityTasks {

    static final int WIDTH = 1200;
    static final int HEIGHT = 800;

    /** The words a person brings to the assistant tasks: the task's own verbs and nouns. */
    static final List<String> ASK_WORDS = List.of("ask", "send", "question", "answer");

    /** A demo prompt pack, written for task 8. */
    static final String PACK = """
            # DEMO triage
            Asked first.

            1. **Quiet nodes** — Which nodes never logged, and why is silence not proof?
            2. **Spread** — Where does the spread jump?
            """;

    private UsabilityTasks() {
    }

    // ---- a frame under its own home --------------------------------------------------------------------------------------

    /** One analyser window: a fresh home, the stubs, the fixture agent; the start page or the workspace. */
    static final class Session implements AutoCloseable {
        final Path dir;
        final Path home;
        final MainFrame frame;
        final ActionExecutor ex;
        final FixtureAgent agent;
        private final String priorHome;

        Session(Path dir, FixtureAgent agent, boolean startPage) throws Exception {
            this.dir = Files.createDirectories(dir);
            this.agent = agent;
            home = Files.createDirectories(dir.resolve("home"));
            // the analyser's own search finds this claude; it is never run (the fixture agent answers)
            Path claude = Files.createDirectories(home.resolve(".local/bin")).resolve("claude");
            Files.writeString(claude, "#!/bin/sh\nexit 0\n");
            Files.setPosixFilePermissions(claude, PosixFilePermissions.fromString("rwx------"));
            // an installed launcher that really starts this build's bridge, under this home (task 6 checks it)
            Path launcher = Files.createDirectories(home.resolve(".jbang/bin")).resolve("analyser");
            Files.writeString(launcher, "#!/bin/sh\nexec '" + Path.of(System.getProperty("java.home"), "bin", "java")
                    + "' '-Duser.home=" + home + "' -cp '" + System.getProperty("java.class.path")
                    + "' telamin.fluxtion.audit.analyser.Main \"$@\"\n");
            Files.setPosixFilePermissions(launcher, PosixFilePermissions.fromString("rwx------"));
            priorHome = System.getProperty("user.home");
            System.setProperty("user.home", home.toString());
            AtomicReference<MainFrame> made = new AtomicReference<>();
            SwingUtilities.invokeAndWait(() -> {
                ThemeManager.apply(new AppConfig().theme);   // the app's default theme, as Main applies it
                MainFrame f = new MainFrame();
                f.localAgentLauncher = agent;
                if (!startPage) workspace(f);
                made.set(f);
            });
            frame = made.get();
            ex = (ActionExecutor) field(frame, "actionExecutor");
            onEdt(() -> { frame.setBounds(40, 40, WIDTH, HEIGHT); frame.setVisible(true); });
            Usability.settle();
        }

        private static void workspace(MainFrame f) {
            try {
                var show = MainFrame.class.getDeclaredMethod("showWorkspace", boolean.class);
                show.setAccessible(true);
                show.invoke(f, false);
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        }

        SessionDriver session() {
            return (SessionDriver) field(frame, "session");
        }

        AssistantPanel assistant() {
            return (AssistantPanel) field(frame, "assistantPanel");
        }

        LocalAgentPanel claude() {
            return (LocalAgentPanel) field(frame, "localAgentPanel");
        }

        StartPanel startPanel() {
            return (StartPanel) field(frame, "startPanel");
        }

        JTable table() {
            return ((LogTablePanel) field(frame, "tablePanel")).table();
        }

        AppConfig config() {
            return (AppConfig) field(frame, "config");
        }

        /** Precondition: the demo log and its graph, opened through the action socket's executor. */
        void openDemo() throws Exception {
            DemoAssets.install();
            Path log = DemoAssets.log();
            Path graphml = DemoAssets.graphml();
            onEdt(() -> {
                var r = ex.render("open", new LinkedHashMap<>(Map.of("log", log.toString(), "graphml", graphml.toString())));
                if (!r.ok()) throw new AssertionError("open " + r.toMap());
            });
            Usability.await("the demo log is open", () -> rows() > 0);
            Usability.settle();
        }

        int rows() {
            int[] n = new int[1];
            try {
                onEdt(() -> n[0] = table().getRowCount());
            } catch (Exception e) {
                return 0;
            }
            return n[0];
        }

        /** Precondition: the profile cards open, a profile chosen and applied, by the program (not counted). */
        void apply(AssistantProfile p) throws Exception {
            if (!ProfileOverlayFrameTest.profiles(frame).open()) onEdt(() -> assistant().profileButton().doClick());
            ProfileOverlayFrameTest.await(frame, s -> s.open(), "the cards are open");
            onEdt(() -> {
                assistant().profileOverlay().card(p).doClick();
                assistant().profileOverlay().applyButton().doClick();
            });
            ProfileOverlayFrameTest.await(frame, s -> !s.open() && s.profile() == p, p + " applied");
            Usability.settle();
        }

        /** Precondition: Claude Code started and ready (the folder confirmed if asked). */
        void startClaudeCode() throws Exception {
            onEdt(() -> claude().startButton().doClick());
            long until = System.currentTimeMillis() + 3_000;
            while (System.currentTimeMillis() < until) {
                AtomicReference<Located> trust = new AtomicReference<>();
                onEdt(() -> trust.set(buttonInWindow(ClaudeCodeTabFrameTest.DIALOG_TITLE, LocalAgentFolderDialog.SAFE_TRUST).find()));
                if (trust.get() != null) {
                    onEdt(() -> ((javax.swing.AbstractButton) trust.get().component()).doClick());
                    break;
                }
                if ("READY".equals(ClaudeCodeTabFrameTest.state(frame).phase())) break;
                Thread.sleep(50);
            }
            ClaudeCodeTabFrameTest.awaitState(frame, s -> "READY".equals(s.phase()), "Claude Code is ready");
            Usability.settle();
        }

        void selectTab(String title) throws Exception {
            onEdt(() -> {
                JTabbedPane tabs = (JTabbedPane) field(frame, "sideTabs");
                tabs.setSelectedIndex(tabs.indexOfTab(title));
            });
            Usability.settle();
        }

        @Override
        public void close() throws Exception {
            SwingUtilities.invokeAndWait(() -> {
                for (Window w : Window.getWindows()) {
                    if (w != frame && (w.getOwner() == frame || w instanceof java.awt.Dialog && w.isShowing())) w.dispose();
                }
                frame.dispose();
            });
            // a closed frame's late work (a debounced project write, a session capture) still resolves user.home: keep
            // it on THIS session's home until that work has run, so it can never land in the next task's fresh home
            Thread.sleep(1_500);
            Usability.settle();
            System.setProperty("user.home", priorHome);
        }
    }

    // ---- fixtures ------------------------------------------------------------------------------------------------------------

    /** A recorded Claude Code stream that answers each question (the M74 P0 multi-turn recording). */
    static FixtureAgent answering() throws Exception {
        FixtureAgent a = new FixtureAgent("multiturn-partial-replay-midturn.jsonl");
        a.conformInit = true;
        return a;
    }

    /**
     * The recorded P3 turn whose report write the person ALLOWS (p3-0-default-real, its "allow" turn), with the probe's
     * init lines: the request the approval dialog asks about, and the stream that follows an allow.
     */
    static FixtureAgent approving(Path dir) throws Exception {
        List<String> lines = Files.readAllLines(Path.of("docs/specs/evidence/m74-p3/fixtures/p3-0-default-real.jsonl"));
        List<String> head = new ArrayList<>();
        List<List<String>> turns = new ArrayList<>();
        List<String> current = null;
        for (String l : lines) {
            if (l.isBlank()) continue;
            boolean started = l.contains("\"command_lifecycle\"") && l.contains("\"started\"");
            boolean ended = l.contains("\"command_lifecycle\"") && (l.contains("\"completed\"") || l.contains("\"cancelled\""));
            if (current == null && started) current = new ArrayList<>(List.of(l));
            else if (current != null) {
                current.add(l);
                if (ended) { turns.add(current); current = null; }
            } else if (turns.isEmpty() && l.contains("\"control_response\"")) head.add(l);
        }
        List<String> allow = turns.stream().filter(t -> String.join("\n", t).contains("p3-zero-allow")).findFirst()
                .orElseThrow(() -> new AssertionError("the P3 recording has no allow turn"));
        List<String> out = new ArrayList<>(head);
        out.addAll(allow);
        Path f = Files.createDirectories(dir).resolve("approve-report.jsonl");
        Files.write(f, out);
        var ctor = FixtureAgent.class.getDeclaredConstructor(Path.class, String.class);
        ctor.setAccessible(true);
        FixtureAgent a = ctor.newInstance(dir, f.getFileName().toString());
        a.conformInit = true;
        return a;
    }

    // ---- what success looks like ------------------------------------------------------------------------------------------

    /** The answer is on screen: a finished answer entry and a transcript showing it. */
    static boolean answerOnScreen(Session s) throws Exception {
        LocalAgentState st = ClaudeCodeTabFrameTest.state(s.frame);
        boolean answered = st.entries().stream().anyMatch(e -> LocalAgentState.ANSWER.equals(e.kind())
                && !"STREAMING".equals(e.status()));
        if (!answered) return false;
        boolean[] shown = new boolean[1];
        onEdt(() -> shown[0] = all(s.frame.getRootPane(), TranscriptView.class).stream().anyMatch(t -> t.isShowing()
                && Usability.onScreen(t) && !t.getText().isBlank())
                || allWindows(TranscriptView.class).stream().anyMatch(t -> t.isShowing() && Usability.onScreen(t)
                && !t.getText().isBlank()));
        return shown[0];
    }

    static <T> List<T> all(Container root, Class<T> type) {
        return UnifiedAssistantFrameTest.all(root, type);
    }

    static <T> List<T> allWindows(Class<T> type) {
        List<T> out = new ArrayList<>();
        for (Window w : Usability.windows()) out.addAll(all(w, type));
        return out;
    }

    /** After Send: confirm the folder if asked, wait for the answer, and go back to it if a spotlight moved the canvas. */
    static void untilAnswered(Run run, Session s) throws Exception {
        run.clickIfShowing("confirm the folder", buttonInWindow(ClaudeCodeTabFrameTest.DIALOG_TITLE, LocalAgentFolderDialog.SAFE_TRUST),
                1_500);
        Usability.await("Claude Code answered", () -> {
            try {
                return ClaudeCodeTabFrameTest.state(s.frame).entries().stream().anyMatch(e ->
                        LocalAgentState.ANSWER.equals(e.kind()) && !"STREAMING".equals(e.status()));
            } catch (Exception e) {
                return false;
            }
        });
        Usability.settle();
        if (!answerOnScreen(s)) run.clickIfShowing("back to the answer", button("Back to the answer"), 1_000);
    }

    // ---- the eight tasks ------------------------------------------------------------------------------------------------------

    /** A task: its id, its name, its key words, and its script. */
    record Task(String id, String name, List<String> keyWords, Script script) {
    }

    @FunctionalInterface
    interface Script {
        /** Runs the task in {@code dir}; returns whether it succeeded (a {@link TaskFailed} is a failure too). */
        boolean run(Run run, Path dir) throws Exception;
    }

    static List<Task> tasks() {
        List<Task> t = new ArrayList<>();
        t.add(new Task("T1", "Fresh start to the first answer", ASK_WORDS, UsabilityTasks::freshStartToFirstAnswer));
        t.add(new Task("T2", "Right-click Ask about a record", ASK_WORDS, UsabilityTasks::askAboutARecord));
        t.add(new Task("T3", "Approve a report write", List.of("approve", "allow"), UsabilityTasks::approveAReportWrite));
        t.add(new Task("T4", "Find out why records are missing (a type filter on, the list hidden)",
                List.of("filter", "hidden", "missing"), UsabilityTasks::whyAreRecordsMissing));
        t.add(new Task("T5", "Switch Safe → Full → Safe", List.of("safe", "full"), UsabilityTasks::safeFullSafe));
        t.add(new Task("T6", "Set up an external client", List.of("client", "connect", "mcp"), UsabilityTasks::externalClient));
        t.add(new Task("T7", "Open the sample", List.of("sample"), UsabilityTasks::openTheSample));
        t.add(new Task("T8", "Load a prompt pack", List.of("prompt pack"), UsabilityTasks::loadAPromptPack));
        return t;
    }

    /**
     * Tasks with a correctness criterion the product does not meet yet, or that need what the battery cannot script:
     * run and recorded, never budgeted (docs/usability/scorecard.md § Targets).
     */
    static List<Task> targetTasks() {
        List<Task> t = new ArrayList<>();
        t.add(new Task("T4c", "T4 correct: the cue and the assistant's context name the hidden filter",
                List.of("filter", "hidden", "missing"), UsabilityTasks::whyAreRecordsMissingCorrect));
        return t;
    }

    /** The authoring tasks that run with a stub assistant against this build, deterministically: budgeted. */
    static List<Task> authoringTasks() {
        List<Task> t = new ArrayList<>();
        t.add(new Task("A2", "Teach-me diamond pack: the example downloaded, its log and graph paired",
                List.of("teaching", "example", "download", "open", "log"), UsabilityTasks::teachMeDiamond));
        return t;
    }

    /** Runs one task to its result; never throws for a failed task. */
    static Run run(Task task, Path dir) throws Exception {
        Run run = new Run(task.id(), task.name(), task.keyWords());
        try {
            boolean ok = task.script().run(run, dir);
            run.end(ok, ok ? null : "the end state was not reached");
        } catch (TaskFailed e) {
            run.end(false, e.getMessage());
        }
        return run;
    }

    /** T1: the start page, a question, Enter; the page offers the sample; the cards; Send; the answer on screen. */
    static boolean freshStartToFirstAnswer(Run run, Path dir) throws Exception {
        try (Session s = new Session(dir, answering(), true)) {
            StartPromptFrameTest.session(s.frame);
            run.begin();
            StartPromptView prompt = s.startPanel().prompt();
            Finder box = Usability.component(prompt::box, () -> StartPromptView.PLACEHOLDER);
            run.type("type a question in the start page's box", box, "What happened in this log?");
            run.key("press Enter", Usability.component(prompt::box, () -> prompt.keyHintLabel().getText()), KeyEvent.VK_ENTER);
            // a build that holds a no-log question offers the sample first; an older one goes straight to the cards
            run.clickIfShowing("open the sample the page offers", button("Open the sample"), 1_500);
            Usability.await("the cards ask", () -> ProfileOverlayFrameTest.profiles(s.frame).open());
            run.click("apply the default assistant", button("Apply"));
            UnifiedAssistantFrameTest.awaitMode(s.frame, AssistantMode.LOCAL_AGENT);
            run.click("send the placed question", button("Send"));
            untilAnswered(run, s);
            return run.endScreen(answerOnScreen(s));
        }
    }

    /** T2: the demo log open; right-click the breach record; Ask; the cards; Send; the answer on screen. */
    static boolean askAboutARecord(Run run, Path dir) throws Exception {
        try (Session s = new Session(dir, answering(), false)) {
            s.openDemo();
            run.begin();
            run.rightClick("right-click the RiskBreachEvent record", Usability.tableRow(s::table, () -> {
                JTable t = s.table();
                for (int r = 0; r < t.getRowCount(); r++) {
                    for (int c = 0; c < t.getColumnCount(); c++) {
                        if ("RiskBreachEvent".equals(String.valueOf(t.getValueAt(r, c)))) return r;
                    }
                }
                return -1;
            }));
            // the result must be about the RIGHT record: the selection the Ask is about is the breach record, by digest
            boolean[] right = new boolean[1];
            onEdt(() -> right[0] = Usability.rightRecordSelected(s.frame,
                    Usability.expectedRecordDigest(s.frame, raw -> raw.contains("RiskBreachEvent"))));
            if (!right[0]) run.notes.add("the record asked about is not the RiskBreachEvent record");
            run.click("choose Ask: What happened here?", button("What happened here?"));
            ProfileOverlayFrameTest.await(s.frame, x -> x.open(), "the cards are open");
            run.click("apply the default assistant", button("Apply"));
            UnifiedAssistantFrameTest.awaitMode(s.frame, AssistantMode.LOCAL_AGENT);
            run.click("send the placed question", button("Send"));
            untilAnswered(run, s);
            return run.endScreen(answerOnScreen(s) && right[0]);
        }
    }

    /** T3: Claude Code (Safe) asks to write a report; the person approves it once. */
    static boolean approveAReportWrite(Run run, Path dir) throws Exception {
        try (Session s = new Session(dir, approving(dir.resolve("fixture")), false)) {
            s.openDemo();
            onEdt(() -> {
                s.config().assistantExports = true;         // the recorded call writes a report: file exchange is on
                try {
                    var changed = MainFrame.class.getDeclaredMethod("onConfigChanged");
                    changed.setAccessible(true);
                    changed.invoke(s.frame);
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError(e);
                }
            });
            s.apply(AssistantProfile.SAFE);
            s.startClaudeCode();
            onEdt(() -> {
                s.claude().composerArea().setText("Write the DEMO report");
                s.claude().sendButton().doClick();
            });
            LocalAgentPermissionDialog dialog = LocalAgentPermissionFrameTest.awaitPermission();
            run.begin();
            Finder allow = buttonInWindow(LocalAgentPermissionFrameTest.DIALOG, LocalAgentPermissionDialog.ALLOW);
            run.awaitEnabled("Allow once", allow);
            run.click("allow the report write once", allow);
            Usability.await("the CLI was told allow", () -> s.agent.last().stdin.stream()
                    .anyMatch(l -> l.contains("\"behavior\":\"allow\"")));
            Usability.await("the dialog closed", () -> !dialog.isDisplayable());
            return run.endScreen(true);
        }
    }

    /** T4: one event type unchecked and the checklist hidden; the person finds the cause on screen. */
    static boolean whyAreRecordsMissing(Run run, Path dir) throws Exception {
        try (Session s = new Session(dir, answering(), false)) {
            s.openDemo();
            // precondition: RiskBreachEvent unticked, then the checklist hidden (both by the program)
            onEdt(() -> {
                for (JCheckBox c : all(s.frame.getRootPane(), JCheckBox.class)) {
                    if (c.getText() != null && c.getText().startsWith("RiskBreachEvent") && c.isSelected()) c.doClick();
                }
            });
            Usability.settle();
            javax.swing.JToggleButton toggle = (javax.swing.JToggleButton) field(s.frame, "eventTypesToggle");
            onEdt(() -> { if (toggle.isSelected()) toggle.doClick(); });
            Usability.settle();
            run.begin();
            run.click("show the event-type checklist", button("Event types"));
            Finder unticked = () -> {
                for (JCheckBox c : all(s.frame.getRootPane(), JCheckBox.class)) {
                    if (c.isShowing() && !c.isSelected() && c.getText() != null && c.getText().startsWith("RiskBreachEvent")) {
                        return new Located(c, Usability.label(c));
                    }
                }
                return null;
            };
            run.look("find the unticked type", unticked);
            if (!onScreen(unticked)) {
                // a build whose checklist can open with no rows: the person drags the divider below it down
                run.dragDivider("drag the checklist's divider down", () -> checklistSplit(s), 120);
                run.look("find the unticked type", unticked);
            }
            boolean[] cause = new boolean[1];
            onEdt(() -> cause[0] = all(s.frame.getRootPane(), JCheckBox.class).stream().anyMatch(c -> c.isShowing()
                    && Usability.onScreen(c) && !c.isSelected() && c.getText() != null && c.getText().startsWith("RiskBreachEvent")));
            return run.endScreen(cause[0]);
        }
    }

    /** The hidden type the T4 tasks set up. */
    static final String HIDDEN_TYPE = "RiskBreachEvent";

    /**
     * T4c, the RESULT of T4: with the type unticked and the checklist hidden, (1) something on screen names the hidden
     * filter — one visible text that says "hidden" and names the event-type filter or the type — and (2) the context an
     * assistant reads
     * ({@code context}, as the socket serves it) has a {@code hidden} entry naming the type. "Nothing is missing" — no
     * cue, or no such entry — is a FAIL. No act is needed when the product says it; the check reads the first screen.
     */
    static boolean whyAreRecordsMissingCorrect(Run run, Path dir) throws Exception {
        try (Session s = new Session(dir, answering(), false)) {
            s.openDemo();
            onEdt(() -> {
                for (JCheckBox c : all(s.frame.getRootPane(), JCheckBox.class)) {
                    if (c.getText() != null && c.getText().startsWith(HIDDEN_TYPE) && c.isSelected()) c.doClick();
                }
            });
            Usability.settle();
            javax.swing.JToggleButton toggle = (javax.swing.JToggleButton) field(s.frame, "eventTypesToggle");
            onEdt(() -> { if (toggle.isSelected()) toggle.doClick(); });
            Usability.settle();
            run.begin();
            boolean[] cue = new boolean[1];
            onEdt(() -> cue[0] = Usability.chromeOnScreen().stream().map(x -> x.text().toLowerCase(java.util.Locale.ROOT))
                    .anyMatch(x -> x.contains("hidden") && (x.contains("event type") || x.contains(HIDDEN_TYPE.toLowerCase(java.util.Locale.ROOT)))));
            if (!cue[0]) run.notes.add("nothing on screen says records are hidden by the event-type filter");
            AtomicReference<Object> ctx = new AtomicReference<>();
            onEdt(() -> ctx.set(s.ex.render("context", new LinkedHashMap<>()).toMap()));
            boolean named = hiddenNames(ctx.get(), HIDDEN_TYPE, false);
            if (!named) run.notes.add("the assistant's context has no 'hidden' entry naming " + HIDDEN_TYPE);
            return run.endScreen(cue[0] && named);
        }
    }

    /** Whether a {@code hidden} key anywhere in a context map names {@code type}. */
    static boolean hiddenNames(Object node, String type, boolean underHidden) {
        if (node instanceof Map<?, ?> m) {
            for (var e : m.entrySet()) {
                if (hiddenNames(e.getValue(), type, underHidden || "hidden".equals(String.valueOf(e.getKey())))) return true;
            }
            return false;
        }
        if (node instanceof java.util.Collection<?> c) {
            for (Object o : c) if (hiddenNames(o, type, underHidden)) return true;
            return false;
        }
        return underHidden && String.valueOf(node).contains(type);
    }

    /** The Basic-track pack's entry 1, as the authoring walk dropped it (the published diamond, pinned by commit). */
    static final String DIAMOND_COMMIT = "dad9b4382099a9ff26d9fe624f01fbbc80f67c50";

    static final String DIAMOND_PACK = "# What is Fluxtion?\n\nBasic track. Entry 1 is the published diamond, pinned by commit.\n\n"
            + "1. **Teaching example: the diamond** — example: https://github.com/telaminai/fluxtion-diamond/archive/"
            + DIAMOND_COMMIT + ".zip\n"
            + "2. **What is Fluxtion, in plain words?** — Using this project and the records on screen, explain an event,"
            + " an event handler and a trigger.\n";

    /**
     * The archive the recording transport serves for the pinned address: a project laid out as the diamond is (a README,
     * a source root, the generated GraphML, a shipped sample log), with the DEMO fixture's log and graph inside, so the
     * battery needs no network and commits no third-party code. What is measured is the analyser's route, not the bytes.
     */
    static byte[] diamondLikeArchive() throws Exception {
        String root = "fluxtion-diamond-" + DIAMOND_COMMIT + "/";
        var bytes = new java.io.ByteArrayOutputStream();
        try (var out = new java.util.zip.ZipOutputStream(bytes)) {
            Map<String, byte[]> files = new LinkedHashMap<>();
            files.put("README.md", "# DEMO teaching example\n".getBytes());
            files.put("src/main/java/com/acme/demo/Readme.java", "package com.acme.demo;\n\nfinal class Readme {\n}\n".getBytes());
            files.put("src/main/resources/com/acme/demo/generated/DemoQuoteProcessor.graphml",
                    Files.readAllBytes(Path.of("src/test/resources/topology/demo-quote-processor.graphml")));
            files.put("sample/demo-quote-audit.yaml", Files.readAllBytes(Path.of("src/test/resources/topology/demo-quote-audit.yaml")));
            for (var e : files.entrySet()) {
                out.putNextEntry(new java.util.zip.ZipEntry(root + e.getKey()));
                out.write(e.getValue());
                out.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    /**
     * A2: the teach-me pack dropped on the start page; its teaching example downloaded (a recording transport: no
     * network), unpacked and adopted as a project; its shipped sample log opened from the toolbar's file chooser, and
     * the log paired with the project's graph. Milestones: acts to the project, acts to the evidence; typed paths;
     * terminal hand-offs (none: the example ships its log, so nothing is built).
     */
    static boolean teachMeDiamond(Run run, Path dir) throws Exception {
        try (Session s = new Session(dir, answering(), true)) {
            StartPromptFrameTest.session(s.frame);
            byte[] zip = diamondLikeArchive();
            onEdt(() -> s.frame.exampleClient = new telamin.fluxtion.audit.analyser.analyser.assistant.ExampleArchiveClient(
                    (uri, max) -> uri.getHost().equals("github.com")
                            ? new telamin.fluxtion.audit.analyser.analyser.assistant.ExampleArchiveClient.Reply(302,
                            "https://codeload.github.com/telaminai/fluxtion-diamond/zip/" + DIAMOND_COMMIT, new byte[0])
                            : new telamin.fluxtion.audit.analyser.analyser.assistant.ExampleArchiveClient.Reply(200, null, zip)));
            Path pack = Files.writeString(dir.resolve("what-is-fluxtion.md"), DIAMOND_PACK);
            run.begin();
            StartPanel page = s.startPanel();
            run.drop("drop the teach-me pack on the start page", Usability.component(() -> page, () -> {
                for (Usability.Shown sh : Usability.chrome(page)) if (sh.text().contains("prompt pack")) return sh.text();
                return "";
            }), pack);
            Usability.await("the pack was read", () -> s.frame.promptPacksRead >= 1);
            run.click("choose the teaching example", Usability.listRow(() -> page.prompt().promptList(), "Teaching example"));
            run.click("download it", Usability.anyButton("Download example", "Download"));
            run.click("confirm the download", buttonInWindow(ExampleDownloadDialog.TITLE, "Download"));
            // the topology is offered unselected ("at most one opens now"): the person picks it, then OK
            run.click("choose the project's graph", Usability.firstInWindow("New project — what should be added?",
                    javax.swing.JRadioButton.class));
            run.click("adopt the project", buttonInWindow("New project — what should be added?", "OK"));
            Usability.await("the project is open", () -> {
                try {
                    boolean[] open = new boolean[1];
                    onEdt(() -> open[0] = s.frame.getTitle().contains("fluxtion-diamond"));
                    return open[0];
                } catch (Exception e) {
                    return false;
                }
            });
            run.mark("project");
            run.click("open a log", Usability.buttonExactly("Open"));
            Path log = dir.resolve("home/fluxtion-diamond-" + DIAMOND_COMMIT.substring(0, 7) + "/sample/demo-quote-audit.yaml");
            run.typePath("type the sample log's path", Usability.textFieldInWindow("Open"), log.toString());
            run.click("open it", buttonInWindow("Open", "Open"));
            run.clickIfShowing("load the project's settings", buttonInWindow("Load this project?", "Yes"), 2_000);
            Usability.await("the log is open", () -> s.rows() > 0);
            Usability.settle();
            run.mark("evidence");
            var snap = new AtomicReference<telamin.fluxtion.audit.analyser.analyser.session.SessionSnapshot>();
            onEdt(() -> snap.set(s.session().snapshot()));
            var pairing = snap.get().publishedPairing();
            boolean paired = snap.get().logOpen() && snap.get().graphOpen() && pairing != null && pairing.applies();
            if (!paired) run.notes.add("log and graph not paired: " + (pairing == null ? "no verdict" : pairing.reason()));
            return run.endScreen(paired);
        }
    }

    /** T5: Safe analysis in force; turn on Full (its consent shown in full), then back to Safe. */
    static boolean safeFullSafe(Run run, Path dir) throws Exception {
        try (Session s = new Session(dir, answering(), false)) {
            s.openDemo();
            s.apply(AssistantProfile.SAFE);
            s.selectTab("Analyser assistant");
            run.begin();
            run.click("open the assistant chooser", button("Safe analysis ▾"));
            ProfileOverlayFrameTest.await(s.frame, x -> x.open(), "the cards are open");
            run.click("choose the Full Claude Code card", button("Full Claude Code"));
            run.click("turn Full on", Usability.anyButton("Turn on Full", "Turn on full Claude Code"));
            run.click("accept the Full consent", buttonInWindow(ConfigPanel.FULL_POSTURE_TITLE, "Turn on full Claude Code"));
            ProfileOverlayFrameTest.await(s.frame, x -> !x.open() && x.profile() == AssistantProfile.FULL, "Full applied");
            run.click("open the assistant chooser", button("Full Claude Code ▾"));
            ProfileOverlayFrameTest.await(s.frame, x -> x.open(), "the cards are open again");
            run.click("choose the Safe analysis card", button("Safe analysis"));
            run.click("apply Safe analysis", button("Apply"));
            ProfileOverlayFrameTest.await(s.frame, x -> !x.open() && x.profile() == AssistantProfile.SAFE, "Safe applied");
            return run.endScreen(!s.config().claudeCodeFull);
        }
    }

    /** T6: from the start page, the generic MCP client set-up: access on, the bridge checked. */
    static boolean externalClient(Run run, Path dir) throws Exception {
        try (Session s = new Session(dir, answering(), true)) {
            StartPromptFrameTest.session(s.frame);
            run.begin();
            run.click("choose Other MCP client", button("Other MCP client"));
            run.click("turn on External MCP/REST access", button("Turn on External MCP/REST access"));
            run.click("confirm", buttonInWindow("Turn on External MCP/REST access", "OK"));
            run.awaitEnabled("Check connection", button("Check connection"));
            run.click("check the connection", button("Check connection"));
            Usability.await("the bridge check reports", () -> !bridgeLine().contains("checking"));
            return run.endScreen(bridgeLine().startsWith("Bridge works now"));
        }
    }

    /** Whether a finder's control is on screen now (its centre visible). */
    static boolean onScreen(Finder f) throws Exception {
        boolean[] on = new boolean[1];
        onEdt(() -> {
            Located l = f.find();
            if (l == null || !(l.component() instanceof JComponent j)) return;
            java.awt.Rectangle a = l.area();
            on[0] = j.isShowing() && j.getVisibleRect().contains(a.x + a.width / 2, a.y + a.height / 2);
        });
        return on[0];
    }

    /** The split pane whose top holds the event-type checklist. */
    static javax.swing.JSplitPane checklistSplit(Session s) {
        Component filter = (Component) field(s.frame, "eventFilterPanel");
        for (Container c = filter.getParent(); c != null; c = c.getParent()) {
            if (c instanceof javax.swing.JSplitPane sp && sp.getTopComponent() != null
                    && SwingUtilities.isDescendingFrom(filter, sp.getTopComponent())) return sp;
        }
        return null;
    }

    /** The McpSetupDialog's bridge status, as shown. */
    static String bridgeLine() {
        AtomicReference<String> out = new AtomicReference<>("");
        try {
            onEdt(() -> {
                for (Window w : Usability.windows()) {
                    if (!(w instanceof McpSetupDialog)) continue;
                    for (Usability.Shown sh : Usability.chrome(w)) {
                        if (sh.text().startsWith("Bridge")) out.set(sh.text());
                    }
                }
            });
        } catch (Exception e) {
            return "";
        }
        return out.get();
    }

    /** T7: from the start page, the sample project, its records on screen. */
    static boolean openTheSample(Run run, Path dir) throws Exception {
        try (Session s = new Session(dir, answering(), true)) {
            StartPromptFrameTest.session(s.frame);
            run.begin();
            run.click("open the sample project", button("Sample project"));
            StartPromptFrameTest.awaitDemo(s.frame);
            Usability.await("the records show", () -> s.rows() > 0);
            Usability.settle();
            boolean[] shown = new boolean[1];
            onEdt(() -> shown[0] = s.table().isShowing() && Usability.onScreen(s.table()));
            return run.endScreen(shown[0]);
        }
    }

    /** T8: a prompt pack dropped on the start page; its entries replace the list. */
    static boolean loadAPromptPack(Run run, Path dir) throws Exception {
        try (Session s = new Session(dir, answering(), true)) {
            StartPromptFrameTest.session(s.frame);
            Path pack = Files.writeString(dir.resolve("triage.md"), PACK);
            run.begin();
            StartPanel page = s.startPanel();
            // the drop zone is the page; what invites the drop is the line that names a prompt pack, if it is on screen
            run.drop("drop the pack on the start page", Usability.component(() -> page, () -> {
                for (Usability.Shown sh : Usability.chrome(page)) if (sh.text().contains("prompt pack")) return sh.text();
                return "";
            }), pack);
            Usability.await("the pack was read", () -> s.frame.promptPacksRead >= 1);
            Usability.settle();
            boolean[] ok = new boolean[1];
            onEdt(() -> ok[0] = page.prompt().packTitle().isShowing() && "DEMO triage".equals(page.prompt().packTitle().getText()));
            return run.endScreen(ok[0]);
        }
    }

    // ---- the three key states --------------------------------------------------------------------------------------------------

    /** A key state's measure: chrome words and clipped strings in its region and in the whole window. */
    record StateMeasure(String id, String name, String region, int words, int controls, List<String> clipped,
                        int windowWords, List<String> windowClipped, String window, List<String> counted) {
        Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id);
            m.put("state", name);
            m.put("region", region);
            m.put("words", words);
            m.put("controls", controls);
            m.put("clippedCount", clipped.size());
            m.put("clipped", clipped);
            m.put("windowWords", windowWords);
            m.put("windowClippedCount", windowClipped.size());
            m.put("windowClipped", windowClipped);
            m.put("window", window);
            m.put("counted", counted);
            return m;
        }
    }

    static StateMeasure measure(String id, String name, String region, Component root) throws Exception {
        Usability.settle();
        Usability.shot(id);
        AtomicReference<StateMeasure> out = new AtomicReference<>();
        onEdt(() -> {
            List<Usability.Shown> shown = Usability.chrome(root);
            List<String> clipped = Usability.clipped(root);
            Window w = SwingUtilities.getWindowAncestor(root);
            List<String> winClipped = Usability.clipped(((javax.swing.RootPaneContainer) w).getRootPane());
            out.set(new StateMeasure(id, name, region, Usability.chromeWords(shown), Usability.controls(root), clipped,
                    Usability.chromeWords(Usability.chrome(w)), winClipped,
                    w instanceof java.awt.Frame fr ? fr.getTitle() : w.getClass().getSimpleName(),
                    shown.stream().map(x -> x.words() + " · " + Usability.abbrev(x.text())).toList()));
        });
        return out.get();
    }

    /** S1: the start page of a fresh home. */
    static StateMeasure startPage(Path dir) throws Exception {
        return startPage(dir, s -> { });
    }

    /** S1, with {@code change} applied to the open page first (the budget test's witness adds a label). */
    static StateMeasure startPage(Path dir, java.util.function.Consumer<Session> change) throws Exception {
        try (Session s = new Session(dir, answering(), true)) {
            StartPromptFrameTest.session(s.frame);
            onEdt(() -> change.accept(s));
            return measure("S1", "Start page (fresh home)", "the start page", s.startPanel());
        }
    }

    /** S2: Safe analysis applied, Claude Code started and Ready, the assistant docked (M78's "Ready, Safe analysis"). */
    static StateMeasure assistantReady(Path dir) throws Exception {
        try (Session s = new Session(dir, answering(), false)) {
            s.openDemo();
            s.apply(AssistantProfile.SAFE);
            s.selectTab("Analyser assistant");
            s.startClaudeCode();
            s.selectTab("Analyser assistant");
            return measure("S2", "Assistant Ready (Safe analysis, docked)", "the assistant panel", s.assistant());
        }
    }

    /** S3: the demo log open, the Records area with its event-type checklist shown. */
    static StateMeasure recordsWithList(Path dir) throws Exception {
        try (Session s = new Session(dir, answering(), false)) {
            s.openDemo();
            javax.swing.JToggleButton toggle = (javax.swing.JToggleButton) field(s.frame, "eventTypesToggle");
            onEdt(() -> { if (!toggle.isSelected()) toggle.doClick(); });
            Component records = recordsRegion(s);
            return measure("S3", "Records with the event-type list shown", "the Records section", records);
        }
    }

    /** The Records section: the nearest container holding both the records table and the event-type checklist. */
    static Component recordsRegion(Session s) throws Exception {
        AtomicReference<Component> out = new AtomicReference<>();
        onEdt(() -> {
            // the container titled "Records…" (its search row, the checklist and the table)
            Component table = (Component) field(s.frame, "tablePanel");
            Container c = table.getParent();
            while (c != null && !(c instanceof JComponent j && Usability.borderTitle(j.getBorder()).startsWith("Records"))) {
                c = c.getParent();
            }
            if (c == null) throw new AssertionError("no Records section around the table");
            out.set(c);
        });
        return out.get();
    }
}
