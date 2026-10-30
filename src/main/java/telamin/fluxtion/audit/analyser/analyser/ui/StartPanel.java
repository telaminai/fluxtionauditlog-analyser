package telamin.fluxtion.audit.analyser.analyser.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The full workspace landing page when no investigation is open.
 *
 * <p>It is an empty state, never a first-run modal. Opening a project, log or graph replaces it;
 * Help can bring it back without closing work. Its choices are actual entrances to a workspace,
 * with recent projects close at hand. Only the bundled sample claims to work without a local file,
 * server or API key.
 *
 * <p><b>On the colour.</b> Every accent here is {@link UiTheme#accent()}, which is the blue the
 * topology already paints a hot edge with — the page is tinted, not branded. Colour is spent only
 * where it carries meaning: the primary action is the one filled shape on the page, each heading
 * gets a rule so sections are countable at a glance. Nothing is coloured merely to be coloured, because on the first
 * screen a new user reads, decoration and signal are indistinguishable until they have learnt which
 * is which.
 */
public final class StartPanel extends JPanel {

    /** What the page can ask the app to do. */
    public interface Actions {
        /** Open a bundled demo log, optionally with the graph, and add the demo source root. */
        void openDemo(Path log, boolean withGraph);
        default void openSampleProject() { openDemo(DemoAssets.log(), true); }
        default void openGuidedTour() { openSampleProject(); }

        /** Bring a tab forward — the page hands over, it does not drive. */
        void showTab(String name);

        /** The ordinary Audit log ▸ Open, for someone who arrived with their own log. */
        void openOwnLog();

        /**
         * Project ▸ Settings. Reachable from here because the first-run modal that used to DEMAND it was
         * removed: discoverability was the one thing that gate bought, and it is cheaper to offer than
         * to insist.
         */
        void openSettings();

        /** Manage the builder key in its canonical file; this page never receives the value. */
        void openFluxtionKey();

        /** A locally observed presence fact only — never key validity or a future build's winner. */
        boolean fluxtionKeyPresent();

        /** Return to the active workspace when Help ▸ Start page was raised over one. */
        void backToRecords();
        default void openProjectDesign() { }
        default void openProjectDiagnostics() { }
        default void openProjectTopology() { }
        default void newProject() { }
        default void newProjectFromTemplate() { }
        default void openExperiment() { }
        default void investigateIncident() { }
        default void openGraphml() { }
        default void openRecentProject(String path) { }

        /** #73: reopen a bundle this machine has already verified once. */
        default void openRecentBundle(String path) { }

        /** Forget a recent entry. {@code bundle} distinguishes the two lists; the file is not touched. */
        default void forgetRecent(String path, boolean bundle) { }

        /** Reveal the file in the desktop's file manager. */
        default void revealRecent(String path) { }

        /** Rename the file on disk, keeping the recent entry pointing at it. */
        default void renameRecent(String path, boolean bundle) { }
        default void openExistingProject() { }
        default void restoreSession(long generation) { }
        default void dismissSessionRestore(long generation) { }

        // ---- M74 P2 (§5.8): the prompt's button and its no-AI fallbacks; every fallback is an EXISTING action ----

        /** The prompt's button: the person's act; the session decides the route. */
        default void pressStartPrompt(String entryId, String text) { }

        /** Robot re-walk #3: an action on the "No log is open" line, for held press {@code request}. */
        default void answerStartNoLog(long request,
                                      StartPromptView.NoLogAction choice) { }

        /** A guide page ({@code ""} for the in-app user guide). */
        default void openUserGuide(String page) { }

        /** The start page's own Copy prompt: the composed draft only (r8 F14). */
        default void copyStartPrompt(String entryId, String text) { }

        /** Where Claude Code is installed from. */
        default void installClaudeCode() { }

        /** The resolver's searched places, for "Claude Code was not found". */
        default String claudeSearched() { return ""; }

        /** A dropped prompt pack's words, by the session's reference (session only). */
        default telamin.fluxtion.audit.analyser.analyser.assistant.PromptPack.Pack promptPack(long ref) { return null; }

        /** "← Return to main options": the person's act; the session restores the built-in list. */
        default void returnToMainOptions() { }

        /** P2b: a prompt pack example entry's own Download button. */
        default void downloadExample(int entryIndex) { }

        // ---- M77-2 (spec-ai-setup-wizard.md §6.0): the AI block and line — the person's acts; aiSetup decides ----

        /** The first-run block: a choice picked (nothing is applied). */
        default void setUpAiChoose(telamin.fluxtion.audit.analyser.analyser.session.AiSetupState.Choice choice) { }

        /** The first-run block's Continue. */
        default void setUpAiContinue() { }

        /** The first-run block's Not now. */
        default void setUpAiNotNow() { }

        /** The AI line's Set up AI… / Change…. */
        default void openSetUpAi() { }

        default void onboardBuild() { openSetUpAi(); }
        default void onboardClient() { openClientSetUp(); }

        /**
         * M77-4 (§14.4, OD-77.12): a door about your own AI client — a no-AI fallback's <i>Connect</i> button. It opens
         * Set up AI (at Your own AI client when one is set up); the client dialog is behind its More options….
         */
        default void openClientSetUp() { openSetUpAi(); }

        /** OD-77.5 (M77-3): the first-run block's box under No AI. */
        default void setUpAiAlsoTurnOff(boolean on) { }

        /** OD-77.11 (M77-3): Turn it off, from the AI line. */
        default void setUpAiTurnOffAccess() { }

    }

    private final JPanel recoveryOffer = new JPanel(new BorderLayout(8, 8));
    private final Box recentProjects = Box.createVerticalBox();
    private final JTextArea operationFeedback = Fluid.text("");
    /**
     * M74 UX (owner, 2026-10-17): ONE start-page surface — the prompt leads, and the actions the old cards carried follow
     * as a compact strip in the prompt's style (icon + short label; the old card's words are the tooltip and the
     * accessible name). Every action is the same {@link Actions} method it always was.
     */
    private final JPanel directStrip = new Fluid.Panel();
    /** The recent projects and bundles, side by side when wide, stacked when narrow. */
    private final JPanel recents = new Fluid.Panel();
    private final JComponent[] recentColumns = new JComponent[2];
    private boolean recentsWide;
    private boolean recentsArranged;
    private List<String> lastRecents;
    private final Box recentBundles = Box.createVerticalBox();
    private List<telamin.fluxtion.audit.analyser.analyser.config.AppConfig.RecentBundle> lastBundleRecents;
    private final Actions actions;
    private final Consumer<String> status;
    /** M74 P2: "What would you like to do?" — the prompt, rendering the session's start-page state. */
    private final StartPromptView prompt;
    /** M77-2: the AI block (first run) or the one AI line, under the start box. */
    private final AiSetupBlock aiSetup;
    private final StartPromptView.Host promptHost;

    /** Components whose colour is theme-derived, re-resolved on a theme switch. */
    private final List<Runnable> recolour = new ArrayList<>();

    /** The return row — visible whenever a project, graph, design or log workspace exists. */
    private final JComponent returnRow;

    /** M19.12: the non-gating key offer; mutable only so it follows a save in the one owner dialog. */
    private final ActionChip fluxtionKeyCard;

    public StartPanel(Actions actions, Consumer<String> status) {
        super(new BorderLayout());
        this.actions = actions;
        this.status = status == null ? s -> { } : status;
        this.promptHost = new StartPromptView.Host() {
            @Override public void press(String entryId, String text) {
                actions.pressStartPrompt(entryId, text);
            }
            @Override public void fallback(telamin.fluxtion.audit.analyser.analyser.assistant.AskCatalogue.Fallback f,
                                           String entryId, String text) {
                runFallback(actions, f, entryId, text);
            }
            @Override public String claudeSearched() { return actions.claudeSearched(); }
            @Override public telamin.fluxtion.audit.analyser.analyser.assistant.PromptPack.Pack pack(long ref) {
                return actions.promptPack(ref);
            }
            @Override public void returnToMainOptions() { actions.returnToMainOptions(); }
            @Override public void answerNoLog(long request,
                                              StartPromptView.NoLogAction c) {
                actions.answerStartNoLog(request, c);
            }
            @Override public void downloadExample(int entryIndex) { actions.downloadExample(entryIndex); }
            @Override public void seePackFormat() { actions.openUserGuide("user-guide/start-page/#prompt-packs"); }
        };
        this.prompt = new StartPromptView(promptHost);
        this.aiSetup = new AiSetupBlock(new AiSetupBlock.Host() {
            @Override public void build() { actions.onboardBuild(); }
            @Override public void demo() { actions.openGuidedTour(); }
            @Override public void client() { actions.onboardClient(); }
            @Override public void notNow() { actions.setUpAiNotNow(); }
            @Override public void open() { actions.openSetUpAi(); }
            @Override public void turnOffAccess() { actions.setUpAiTurnOffAccess(); }
        });
        setOpaque(true);

        Box col = Box.createVerticalBox();
        col.setBorder(BorderFactory.createEmptyBorder(22, 26, 22, 26));

        returnRow = returnToRecords();
        col.add(returnRow);
        col.add(hero());
        operationFeedback.setOpaque(true);
        operationFeedback.setBackground(UiTheme.accentWash(0.12f));
        recolour.add(() -> operationFeedback.setBackground(UiTheme.accentWash(0.12f)));
        operationFeedback.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        operationFeedback.setVisible(false);
        col.add(operationFeedback);
        col.add(Box.createVerticalStrut(UiTheme.SPACE_XL));
        // r8 F19 / M74 UX: the cards' actions stay, after the prompt — the no-typing path M71 accepted — as small titled
        // blocks in a responsive grid (owner UI feedback, 2026-10-17: each group's heading ABOVE its buttons, 5 columns
        // wide wrapping to 3, 2 and 1). A configured list can lead with a journey; it cannot hide these
        col.add(heading(telamin.fluxtion.audit.analyser.analyser.assistant.AskCatalogue.text("start.or")));
        directStrip.setLayout(new BlockGrid());
        directStrip.setOpaque(false);
        directStrip.setAlignmentX(LEFT_ALIGNMENT);
        directStrip.getAccessibleContext().setAccessibleName("Open directly");
        directStrip.add(group("Explore DEMO", null,
                chip("Take a guided tour", "Guided tour", "Follow four spotlight stops in a local DEMO project.",
                        actions::openGuidedTour),
                chip("Open sample project", "Sample project",
                        "Explore the same DEMO log, topology and source at your own pace.", actions::openSampleProject)));
        directStrip.add(group("Open", null,
                chip("Open project", "Project", "Use an existing project profile and its saved settings.",
                        actions::openExistingProject),
                chip("Open evidence bundle", "Evidence bundle",
                        "Verify an .fexp and open a disposable copy. No replay is run.", actions::openExperiment),
                chip("Open audit log", "Audit log", "Choose a local audit log to investigate.", actions::openOwnLog),
                chip("Open GraphML", "GraphML", "View a processor topology, with or without a log.",
                        actions::openGraphml)));
        directStrip.add(group("Create", null,
                chip("Create project profile", "Project profile",
                        "Save settings for existing source and processors. No code is generated.", actions::newProject),
                chip("Create from template", "From template", "Choose a starter template and where to install it.",
                        actions::newProjectFromTemplate)));
        fluxtionKeyCard = chip("", "", "", actions::openFluxtionKey);
        refreshFluxtionKeyStatus();
        // M77-4 (§14.4): the Connect an assistant group is gone — the AI block (first run) or the AI line above is the
        // one door, Set up AI…, and its Your own AI client screen says what the group's buttons did
        directStrip.add(group("Settings",
                "Source settings apply to the open project, or to global defaults when no project is open.",
                chip("Settings — source and assistant…", "Settings",
                        "Source settings apply to the open project, or to global defaults when no project is open.",
                        actions::openSettings),
                fluxtionKeyCard));
        col.add(directStrip);
        col.add(Box.createVerticalStrut(UiTheme.SPACE_XL));

        recentProjects.setAlignmentX(LEFT_ALIGNMENT);
        setRecentProjects(List.of());
        recentBundles.setAlignmentX(LEFT_ALIGNMENT);
        setRecentBundles(List.of());
        col.add(heading("Recent"));
        recentColumns[0] = recentColumn("Recent projects", recentProjects);
        recentColumns[1] = recentColumn("Recent evidence bundles", recentBundles);
        recents.setOpaque(false);
        recents.setAlignmentX(LEFT_ALIGNMENT);
        arrangeRecents(false);
        recents.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent event) {
                arrangeRecents(recents.getWidth() >= 900);
            }
        });
        col.add(recents);

        col.add(Box.createVerticalStrut(18));
        col.add(Box.createVerticalGlue());

        JScrollPane scroll = new JScrollPane(Fluid.column(col),
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setOpaque(false);
        scroll.setOpaque(false);
        this.scroll = scroll;
        add(scroll, BorderLayout.CENTER);
        recoveryOffer.setBorder(BorderFactory.createEmptyBorder(8, 18, 8, 18));
        recoveryOffer.setVisible(false);
        add(recoveryOffer, BorderLayout.NORTH);
        applyColours();
    }

    /** M74 P2: render the session's start-page state (and the local agent's, for the full-posture cue). */
    public void renderPrompt(telamin.fluxtion.audit.analyser.analyser.session.StartPageState state,
                             telamin.fluxtion.audit.analyser.analyser.session.LocalAgentState agent) {
        prompt.render(state, agent);
    }

    /** M77-2: the AI block or line, as aiSetup decided it. */
    public void renderAiSetup(telamin.fluxtion.audit.analyser.analyser.session.AiSetupState state) {
        aiSetup.render(state);
        prompt.setAiLineSaysIt(aiSetup.isVisible());
        prompt.setVisible(state == null || !state.firstRun());
    }

    /** Reveal the existing Build prompt without sending or replacing the person's draft. */
    void describeApplication() {
        prompt.chooseBuild();
    }

    AiSetupBlock aiSetupBlock() {
        return aiSetup;
    }

    /** M74 P2b: an example download's state, as exampleDownload decided it. */
    public void renderExample(telamin.fluxtion.audit.analyser.analyser.session.ExampleDownloadState state) {
        prompt.renderExample(state);
    }

    /** The prompt's host: its button and its fallbacks, through this page's one Actions interface. */
    public StartPromptView.Host promptHost() {
        return promptHost;
    }

    /** The button's draft was placed: the box is cleared for the next question. */
    public void promptSent() {
        prompt.clearAfterSend();
    }

    StartPromptView prompt() {
        return prompt;
    }

    /**
     * A no-AI fallback BUTTON runs an existing action (r8 F7); the menu path is only its tooltip. One switch over the
     * fallback kinds — never over the assistant mode, which is the session's (OneDispatchModelTest).
     */
    static void runFallback(Actions actions, telamin.fluxtion.audit.analyser.analyser.assistant.AskCatalogue.Fallback f,
                            String entryId, String text) {
        if ("INSTALL".equals(entryId)) {
            actions.installClaudeCode();
            return;
        }
        switch (f) {
            case ASSISTANT_SETTINGS -> actions.openSettings();
            case CONNECT -> actions.openClientSetUp();
            case COPY_PROMPT -> actions.copyStartPrompt(entryId, text);
            case NEW_PROJECT -> actions.newProject();
            case BUNDLE_GUIDE -> actions.openUserGuide("evidence-bundles/opening/");
            case TEMPLATE -> actions.newProjectFromTemplate();
            case USER_GUIDE -> actions.openUserGuide("");
            case GUIDED_TOUR -> actions.openGuidedTour();
            case SAMPLE_PROJECT -> actions.openSampleProject();
        }
    }

    /** Show a recovery offer without replacing the shared start choices. */
    public void renderProject(java.util.Map<String, Object> context) {
        recoveryOffer.removeAll();
        if (context != null && context.get("restoration") instanceof java.util.Map<?,?> restore) {
            recoveryOffer.add(wrapping(String.valueOf(restore.get("message"))), BorderLayout.CENTER);
            if (Boolean.TRUE.equals(restore.get("available"))) {
                long generation = ((Number) restore.get("generation")).longValue();
                JButton accept = new JButton("Restore last session");
                accept.addActionListener(e -> actions.restoreSession(generation));
                JPanel choices = new JPanel();
                choices.add(accept);
                addAction(choices, "Dismiss", () -> actions.dismissSessionRestore(generation));
                recoveryOffer.add(choices, BorderLayout.EAST);
            }
            recoveryOffer.setVisible(!"idle".equals(restore.get("state")) && !"none".equals(restore.get("state")));
        } else recoveryOffer.setVisible(false);
        recoveryOffer.revalidate(); recoveryOffer.repaint();
        // Project declarations remain in the investigation's Project panel. The start page
        // always offers the same entrances, including Open project and Return to workspace.
    }

    private static void addAction(JPanel into, String label, Runnable action) {
        JButton button = UiText.button(label);
        button.addActionListener(e -> action.run());
        into.add(button);
    }

    /**
     * A recent row: the entry itself, and a {@code …} button carrying the housekeeping. The actions are
     * the panel's to OFFER and the frame's to PERFORM — this class stays reveal-only and never touches a file.
     */
    private JComponent recentRow(JButton entry, String path, boolean bundle) {
        JPanel row = new JPanel(new BorderLayout(4, 0));
        row.setOpaque(false);
        row.setAlignmentX(LEFT_ALIGNMENT);
        JButton more = compact(new JButton("…"));
        more.setToolTipText("Remove from recents, reveal, rename");
        more.setMargin(new java.awt.Insets(0, 6, 0, 6));
        JPopupMenu menu = new JPopupMenu();
        JMenuItem forget = new JMenuItem("Remove from recents");
        forget.addActionListener(e -> actions.forgetRecent(path, bundle));
        JMenuItem reveal = new JMenuItem("Open in " + (isMac() ? "Finder" : "file manager"));
        reveal.addActionListener(e -> actions.revealRecent(path));
        JMenuItem rename = new JMenuItem("Rename…");
        rename.addActionListener(e -> actions.renameRecent(path, bundle));
        menu.add(forget);
        menu.add(reveal);
        menu.add(rename);
        more.addActionListener(e -> menu.show(more, 0, more.getHeight()));
        row.add(entry, BorderLayout.CENTER);
        row.add(more, BorderLayout.EAST);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, entry.getPreferredSize().height));
        return row;
    }

    /** M74 UX: a recent row, compact — a smaller font and a tight margin; its words and its tooltip are unchanged. */
    private static JButton compact(JButton b) {
        Font f = UIManager.getFont("Label.font");
        if (f != null) b.setFont(f.deriveFont(f.getSize2D() - 1f));
        b.setMargin(new Insets(1, 6, 1, 6));
        return b;
    }

    private static boolean isMac() {
        return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("mac");
    }

    /** Recent project paths are machine-local navigation, never copied into a bundle or demo. */
    public void setRecentProjects(List<String> paths) {
        List<String> next = paths == null ? List.of() : List.copyOf(paths);
        if (next.equals(lastRecents)) return;
        lastRecents = next;
        recentProjects.removeAll();
        if (next.isEmpty()) {
            recentProjects.add(body("No recent projects yet. Use Open: Project above to choose a workspace."));
        } else {
            for (String path : next) {
                Path file = Path.of(path);
                Path parent = file.getParent();
                if (parent != null && ".analyser".equals(String.valueOf(parent.getFileName()))) {
                    parent = parent.getParent();
                }
                String title = parent == null || parent.getFileName() == null
                        ? path : parent.getFileName().toString();
                // SF-2: a folder name and a configured path are not the analyser's words — shown as characters
                JButton button = compact(UiText.button(title + " / " + file.getFileName() + "  —  " + path));
                button.setHorizontalAlignment(SwingConstants.LEFT);
                button.setToolTipText(UiText.tooltip(path));
                button.setAlignmentX(LEFT_ALIGNMENT);
                button.setMaximumSize(new Dimension(Integer.MAX_VALUE, button.getPreferredSize().height));
                button.addActionListener(e -> actions.openRecentProject(path));
                recentProjects.add(recentRow(button, path, false));
                recentProjects.add(Box.createVerticalStrut(2));
            }
        }
        recentProjects.revalidate();
        recentProjects.repaint();
    }

    /**
     * #73: the bundles this machine has opened. A recipient had no way to find out what they had been sent —
     * no list verb, no menu entry, and an unpacked working copy showed up only as a recent LOG path with a
     * {@code bundle-<hex>} folder in it. The note is the SENDER's sentence, shown so the list says what each
     * one claims to be before it is opened; it is their words, not a verified fact.
     */
    public void setRecentBundles(List<telamin.fluxtion.audit.analyser.analyser.config.AppConfig.RecentBundle> bundles) {
        var next = bundles == null ? List.<telamin.fluxtion.audit.analyser.analyser.config.AppConfig.RecentBundle>of()
                : List.copyOf(bundles);
        if (next.equals(lastBundleRecents)) return;
        lastBundleRecents = next;
        recentBundles.removeAll();
        if (next.isEmpty()) {
            recentBundles.add(body("No evidence bundles opened yet. Use Open: Evidence bundle above, "
                    + "or drop a .fexp anywhere on this page."));
        } else {
            List<String> names = distinctNames(next.stream().map(
                    telamin.fluxtion.audit.analyser.analyser.config.AppConfig.RecentBundle::path).toList());
            for (int i = 0; i < next.size(); i++) {
                var b = next.get(i);
                Path file = Path.of(b.path());
                boolean present = java.nio.file.Files.isRegularFile(file);
                String label = names.get(i)
                        + (b.notes().isEmpty() ? "" : "  —  " + b.notes())
                        + (present ? "" : "   (file not found)");
                // SF-2: the note is the bundle SENDER's sentence and the name is a file on disk
                JButton button = compact(UiText.button(label));
                button.setHorizontalAlignment(SwingConstants.LEFT);
                button.setToolTipText(UiText.tooltip(b.path() + (b.identity().isEmpty() ? "" : "\n" + b.identity())));
                button.setAlignmentX(LEFT_ALIGNMENT);
                button.setMaximumSize(new Dimension(Integer.MAX_VALUE, button.getPreferredSize().height));
                button.setEnabled(present);
                button.addActionListener(e -> actions.openRecentBundle(b.path()));
                recentBundles.add(recentRow(button, b.path(), true));
                recentBundles.add(Box.createVerticalStrut(2));
            }
        }
        recentBundles.revalidate();
        recentBundles.repaint();
    }

    /**
     * Names that tell the list apart. A bundle is shown by its file name, which is not unique: the same
     * evidence copied into two projects, or two runs both called {@code recorded-run.fexp}, produced rows
     * that were identical down to the sender's note, with only the tooltip to separate them (found in use,
     * 2026-09-30). Each name is widened with its parent directories until no other entry in the list reads
     * the same, so the rows differ by as little as they can and no more.
     */
    static List<String> distinctNames(List<String> paths) {
        List<String> out = new java.util.ArrayList<>(paths.size());
        for (int i = 0; i < paths.size(); i++) {
            Path path = Path.of(paths.get(i));
            int n = path.getNameCount();
            String label = path.toString();
            for (int take = 1; take <= n; take++) {
                String candidate = path.subpath(n - take, n).toString();
                boolean clash = false;
                for (int j = 0; j < paths.size() && !clash; j++) {
                    if (j == i) continue;
                    Path other = Path.of(paths.get(j));
                    int on = other.getNameCount();
                    clash = candidate.equals(on >= take
                            ? other.subpath(on - take, on).toString() : other.toString());
                }
                label = candidate;
                if (!clash) break;
            }
            out.add(label);
        }
        return out;
    }

    /** Refresh after the management dialog closes; the stored value never enters this component. */
    public void refreshFluxtionKeyStatus() {
        boolean present = actions.fluxtionKeyPresent();
        fluxtionKeyCard.setCopy(present ? "Fluxtion key — found" : "Fluxtion key — not found",
                present ? "Fluxtion key ✓" : "Fluxtion key",
                present
                        ? "Available for a future processor regeneration; this analyser does not validate it."
                        : "Needed to regenerate a processor; existing generated projects still run without one.");
    }

    private final JScrollPane scroll;

    /**
     * Show the page from the TOP.
     *
     * <p>Without this a narrow pane opens part-way down. The cards are real buttons, so focus lands on
     * one when the page is first shown, and a scroll pane chases the focused component into view —
     * scrolling the title off the top. The reader then arrives at the middle of the page with no
     * indication that they have missed anything, which is a worse failure than a clipped word: the
     * sentence that says what the app is for is simply not there.
     */
    @Override
    public void addNotify() {
        super.addNotify();
        SwingUtilities.invokeLater(() -> scroll.getViewport().setViewPosition(new Point(0, 0)));
    }

    /** The one tinted band gives a short orientation above the concrete workspace choices. */
    private JComponent hero() {
        JPanel band = new Fluid.Panel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(UiTheme.accentWash(0.10f));
                g.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 10, 10));
                g.setColor(UiTheme.accent());          // the rule that marks the band as the summary
                g.fillRect(0, 3, 3, getHeight() - 6);
                g.dispose();
                super.paintComponent(g0);
            }
        };
        band.setOpaque(false);
        band.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));
        band.setAlignmentX(LEFT_ALIGNMENT);

        Box inner = Box.createVerticalBox();
        inner.setOpaque(false);
        // WRAPS rather than ellipsising: a JLabel would render this as "…see what the system act…",
        // and the one sentence on the page that says what the app is for is the worst possible thing
        // to truncate
        JTextArea title = wrapping(telamin.fluxtion.audit.analyser.analyser.assistant.AskCatalogue.text("start.title"));
        // M74 P2b visual fix: the title's size and weight are re-derived on every theme application, as its colour is.
        // Styled once at construction, it kept the size of whatever theme was in force then, so a page built before (or
        // across) a theme application showed it inconsistently (two P2a captures differed).
        Runnable titleFont = () -> title.setFont(heroTitleFont(title.getFont()));
        titleFont.run();
        recolour.add(titleFont);
        heroTitle = title;
        recolour.add(() -> title.setForeground(UIManager.getColor("Label.foreground")));
        inner.add(title);
        inner.add(Box.createVerticalStrut(6));
        inner.add(body(telamin.fluxtion.audit.analyser.analyser.assistant.AskCatalogue.text("start.lede")));
        inner.add(Box.createVerticalStrut(8));
        prompt.setAlignmentX(LEFT_ALIGNMENT);
        inner.add(prompt);
        inner.add(Box.createVerticalStrut(8));
        aiSetup.setAlignmentX(LEFT_ALIGNMENT);
        inner.add(aiSetup);
        inner.add(Box.createVerticalStrut(8));
        inner.add(body("Open evidence, return to a project, or learn with DEMO. An assistant can drive the same views through the local bridge."));
        inner.add(Box.createVerticalStrut(6));
        inner.add(body("Drop an .fexp bundle, audit log, GraphML, Spring XML or a prompt pack (.md) anywhere on this page. "
                + "Return here from Help."));
        band.add(inner, BorderLayout.CENTER);
        return band;      // Fluid: as wide as offered, as tall as its content currently needs
    }

    /** The hero title's font: the look and feel's label font, bold, 5 points larger — the same in every theme. */
    static Font heroTitleFont(Font fallback) {
        Font tf = UIManager.getFont("Label.font");
        Font base = tf != null ? tf : fallback;
        return base.deriveFont(Font.BOLD, base.getSize2D() + 5f);
    }

    /** For the tests: the hero's title text area. */
    JTextArea heroTitle() {
        return heroTitle;
    }

    private JTextArea heroTitle;

    /**
     * The way back, shown ONLY when there is something to go back to.
     *
     * <p>Raised over a project, graph, design or log (Help ▸ Start page), the page would otherwise
     * be a one-way door. Hidden only when there is no workspace to return to.
     */
    public void showReturnToRecords(boolean workspaceOpen) {
        returnRow.setVisible(workspaceOpen);
        revalidate();
        repaint();
    }

    public void showOperationFeedback(String message) {
        operationFeedback.setText(message == null ? "" : message);
        operationFeedback.setVisible(message != null && !message.isBlank());
        operationFeedback.revalidate();
        revalidate();
        repaint();
    }

    private JComponent returnToRecords() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        p.setOpaque(false);
        p.setAlignmentX(LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        p.add(link("\u2190 Return to workspace", actions::backToRecords));
        p.setVisible(false);        // initial empty state has no workspace to return to
        return p;
    }

    /** A text-weight action: the affordance without the visual claim a button makes. */
    private JButton link(String text, Runnable go) {
        JButton b = UiText.button(text);
        b.setBorderPainted(false);
        b.setContentAreaFilled(false);
        b.setFocusPainted(true);
        b.setMargin(new Insets(0, 0, 0, 0));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addActionListener(e -> go.run());
        recolour.add(() -> b.setForeground(UiTheme.accentText()));
        return b;
    }

    private void open(Path log, boolean withGraph, String tab) {
        try {
            DemoAssets.install();
        } catch (RuntimeException e) {
            // the promise is that these work on first contact; if one cannot, say why rather than
            // leaving a button that silently does nothing
            status.accept("Could not unpack the demo: " + e.getMessage());
            return;
        }
        actions.openDemo(log, withGraph);
        if (tab != null) actions.showTab(tab);
    }

    /** Section heading with a short accent rule. */
    private JComponent heading(String text) {
        JPanel p = new Fluid.Panel(new BorderLayout(8, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.setColor(UiTheme.accent());
                // the rule spans the heading however many lines it wrapped to, so it still reads as
                // one section marker rather than a tick beside the first line
                g.fillRect(0, 1, 3, Math.max(4, getHeight() - 10));
            }
        };
        p.setOpaque(false);
        p.setAlignmentX(LEFT_ALIGNMENT);
        p.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        JTextArea l = wrapping(text);
        Font base = UIManager.getFont("Label.font");
        if (base == null) base = l.getFont();
        l.setFont(base.deriveFont(Font.BOLD, base.getSize2D() + 2f));
        l.setBorder(BorderFactory.createEmptyBorder(0, 11, 0, 0));
        recolour.add(() -> l.setForeground(UIManager.getColor("Label.foreground")));
        p.add(l, BorderLayout.CENTER);
        return p;
    }

    private JComponent body(String text) {
        JTextArea l = wrapping(text);
        l.setFont(UIManager.getFont("Label.font"));
        recolour.add(() -> l.setForeground(UiTheme.mutedForeground()));
        return l;
    }

    /** This page's wrapped text — {@link Fluid#text}, shared with the Reports tab. */
    private static JTextArea wrapping(String text) {
        return Fluid.text(text);
    }

    /**
     * A row of actions that REFLOWS. Two earlier cuts of this both lost cards off the right-hand edge:
     * {@code FlowLayout} keeps each button's preferred width and runs the last one past the boundary,
     * and {@code GridLayout} fixes the count at three, so a narrow pane divides the width into slivers
     * too thin to read. Neither failure is visible on a wide screen, which is why both shipped as far
     * as a screenshot.
     *
     * <p>{@link CardFlow} fits as many cards per row as can be given a legible width and wraps the
     * rest — three across on a wide pane, two then one as it narrows, one per row at the bottom end.
     * The height follows from that, so the section grows downwards instead of the content vanishing
     * sideways.
     */
    private static JComponent row(JComponent... items) {
        JPanel p = new Fluid.Panel(new CardFlow());
        p.setOpaque(false);
        p.setAlignmentX(LEFT_ALIGNMENT);
        for (JComponent c : items) p.add(c);
        return p;
    }

    /**
     * A flow layout that WRAPS and stretches, for equal-width cards.
     *
     * <p>Swing has no such layout: {@code FlowLayout} wraps but never stretches (leaving ragged
     * trailing space), {@code GridLayout} stretches but never wraps (shrinking past legibility), and
     * {@code GridBagLayout} does neither without being told the row count in advance — which is the
     * number that has to change. So the rule is stated directly: no card is ever narrower than
     * {@link #MIN}, and whatever fits shares the width equally.
     */
    static final class CardFlow implements LayoutManager {
        /** The narrowest a card may be and still show a title and a readable line under it. */
        static final int MIN = 232;
        private static final int HGAP = 10;
        private static final int VGAP = 10;

        @Override
        public void addLayoutComponent(String name, Component comp) {
        }

        @Override
        public void removeLayoutComponent(Component comp) {
        }

        private static int perRow(Container parent, int width) {
            Insets in = parent.getInsets();
            int avail = width - in.left - in.right;
            int fit = Math.max(1, (avail + HGAP) / (MIN + HGAP));
            return Math.min(fit, Math.max(1, parent.getComponentCount()));
        }

        private static int rowHeight(Container parent) {
            int h = 0;
            for (Component c : parent.getComponents()) h = Math.max(h, c.getPreferredSize().height);
            return h;
        }

        private static Dimension size(Container parent, int width) {
            int count = parent.getComponentCount();
            Insets in = parent.getInsets();
            if (count == 0) return new Dimension(in.left + in.right, in.top + in.bottom);
            int per = perRow(parent, width);
            int rows = (count + per - 1) / per;
            int h = rowHeight(parent);
            return new Dimension(width, in.top + in.bottom + rows * h + (rows - 1) * VGAP);
        }

        @Override
        public Dimension preferredLayoutSize(Container parent) {
            int w = parent.getWidth();
            if (w > 0) return size(parent, w);

            // Asked BEFORE the first layout, when the real width is not knowable. Answer with a single
            // row at natural widths and nothing else: the enclosing Box takes this height as the
            // maximum it will ever grant, so guessing HIGH here reserves space for a wrap that then
            // does not happen, and the section is followed by a band of blank page that no later
            // revalidation reclaims. Guessing low is self-correcting — the first real layout measures
            // the true width and the row grows if it must.
            Insets in = parent.getInsets();
            int natural = in.left + in.right;
            int h = 0;
            for (Component c : parent.getComponents()) {
                natural += c.getPreferredSize().width + HGAP;
                h = Math.max(h, c.getPreferredSize().height);
            }
            return new Dimension(natural, in.top + in.bottom + h);
        }

        @Override
        public Dimension minimumLayoutSize(Container parent) {
            Insets in = parent.getInsets();
            return size(parent, MIN + in.left + in.right);
        }

        @Override
        public void layoutContainer(Container parent) {
            int count = parent.getComponentCount();
            if (count == 0) return;
            Insets in = parent.getInsets();
            int avail = parent.getWidth() - in.left - in.right;
            int per = perRow(parent, parent.getWidth());
            int h = rowHeight(parent);
            // width of a card in a FULL row — a partial last row keeps the same card width rather than
            // stretching its one survivor across the pane, which would read as a different kind of thing.
            // The outer min matters when the pane is narrower than one legible card: MIN is a floor we
            // would LIKE to hold, not a licence to draw outside the pane, and preferring the floor there
            // reintroduces the exact overflow this layout exists to stop.
            int cw = Math.min(avail, Math.max(MIN, (avail - (per - 1) * HGAP) / per));

            int i = 0;
            int y = in.top;
            while (i < count) {
                int n = Math.min(per, count - i);
                int x = in.left;
                for (int k = 0; k < n; k++, i++) {
                    boolean lastOfFullRow = n == per && k == n - 1;
                    int w = lastOfFullRow ? in.left + avail - x : cw;   // absorb integer-division slack
                    parent.getComponent(i).setBounds(x, y, w, h);
                    x += w + HGAP;
                }
                y += h + VGAP;
            }
        }
    }

    /**
     * A titled block of the strip's actions (owner UI feedback, 2026-10-17): its heading ABOVE its buttons, the buttons
     * left-aligned in one column with even spacing, and an optional muted caption under them.
     */
    private JComponent group(String title, String caption, JComponent... chips) {
        JPanel g = new JPanel(new BlockColumn());
        g.setOpaque(false);
        g.getAccessibleContext().setAccessibleName(title);
        g.add(blockHeading(title));
        for (JComponent c : chips) g.add(c);
        if (caption != null) {
            JTextArea t = Fluid.text(caption);
            Font base = UIManager.getFont("Label.font");
            if (base != null) t.setFont(base.deriveFont(base.getSize2D() - 1f));
            recolour.add(() -> t.setForeground(UiTheme.mutedForeground()));
            t.putClientProperty(BlockColumn.CAPTION, Boolean.TRUE);
            g.add(t);
        }
        return g;
    }

    /** The one heading style of a block — the strip's groups and the recent lists alike (owner UI feedback). */
    private JComponent blockHeading(String title) {
        JLabel l = UiText.label(title);
        Font base = UIManager.getFont("Label.font");
        if (base != null) l.setFont(base.deriveFont(Font.BOLD));
        recolour.add(() -> l.setForeground(UiTheme.mutedForeground()));
        l.setAlignmentX(LEFT_ALIGNMENT);
        return l;
    }

    /**
     * One block's column: the heading, then each button at its own width (never wider than the block) with
     * {@link UiTheme#SPACE_SM} between, then a caption wrapped to the block's width. Its height is computed for the width
     * it is given, so {@link BlockGrid} can size a row before laying it out.
     */
    static final class BlockColumn implements LayoutManager {
        static final String CAPTION = "startPanel.blockCaption";

        @Override public void addLayoutComponent(String name, Component comp) { }

        @Override public void removeLayoutComponent(Component comp) { }

        int heightFor(Container parent, int width) {
            int h = 0;
            for (int i = 0; i < parent.getComponentCount(); i++) {
                Component c = parent.getComponent(i);
                if (i > 0) h += UiTheme.SPACE_SM;
                h += height(c, width);
            }
            return h;
        }

        private static boolean isCaption(Component c) {
            return c instanceof JComponent j && Boolean.TRUE.equals(j.getClientProperty(CAPTION));
        }

        private static int height(Component c, int width) {
            if (isCaption(c)) {
                c.setSize(Math.max(1, width), Short.MAX_VALUE);
                return c.getPreferredSize().height;
            }
            return c.getPreferredSize().height;
        }

        @Override public Dimension preferredLayoutSize(Container parent) {
            int w = 0;
            for (Component c : parent.getComponents()) if (!isCaption(c)) w = Math.max(w, c.getPreferredSize().width);
            int width = parent.getWidth() > 0 ? parent.getWidth() : w;
            return new Dimension(w, heightFor(parent, width));
        }

        @Override public Dimension minimumLayoutSize(Container parent) {
            return preferredLayoutSize(parent);
        }

        @Override public void layoutContainer(Container parent) {
            int width = parent.getWidth();
            int y = 0;
            for (int i = 0; i < parent.getComponentCount(); i++) {
                Component c = parent.getComponent(i);
                if (i > 0) y += UiTheme.SPACE_SM;
                int h = height(c, width);
                int w = isCaption(c) ? width : Math.min(width, c.getPreferredSize().width);
                c.setBounds(0, y, w, h);
                y += h;
            }
        }
    }

    /**
     * The strip's grid (owner UI feedback, 2026-10-17): as many columns as fit a block of at least {@link #MIN_BLOCK}
     * — at most {@link #MAX_COLUMNS} — so five across on a wide page, three, two and then one as it narrows, with
     * {@link UiTheme#SPACE_LG} gutters. Each row is as tall as its tallest block at the column width, so nothing is
     * clipped and nothing overlaps.
     */
    static final class BlockGrid implements LayoutManager {
        static final int MIN_BLOCK = 176;
        static final int MAX_COLUMNS = 5;
        static final int HGAP = UiTheme.SPACE_LG;
        static final int VGAP = UiTheme.SPACE_LG;

        @Override public void addLayoutComponent(String name, Component comp) { }

        @Override public void removeLayoutComponent(Component comp) { }

        static int columns(int avail, int count) {
            int fit = Math.max(1, (avail + HGAP) / (MIN_BLOCK + HGAP));
            return Math.max(1, Math.min(Math.min(MAX_COLUMNS, Math.max(1, count)), fit));
        }

        private static int blockHeight(Component c, int width) {
            if (c instanceof Container k && k.getLayout() instanceof BlockColumn b) return b.heightFor(k, width);
            return c.getPreferredSize().height;
        }

        private static Dimension size(Container parent, int width) {
            Insets in = parent.getInsets();
            int count = parent.getComponentCount();
            if (count == 0) return new Dimension(width, in.top + in.bottom);
            int avail = Math.max(1, width - in.left - in.right);
            int cols = columns(avail, count);
            int cw = Math.max(1, (avail - (cols - 1) * HGAP) / cols);
            int h = in.top + in.bottom;
            for (int i = 0; i < count; i += cols) {
                int row = 0;
                for (int k = i; k < Math.min(count, i + cols); k++) row = Math.max(row, blockHeight(parent.getComponent(k), cw));
                h += row + (i > 0 ? VGAP : 0);
            }
            return new Dimension(width, h);
        }

        @Override public Dimension preferredLayoutSize(Container parent) {
            // before the first layout the width is unknown: answer for a wide page, which is self-correcting (Fluid
            // revalidates when the real width arrives)
            return size(parent, parent.getWidth() > 0 ? parent.getWidth() : MAX_COLUMNS * (MIN_BLOCK + HGAP));
        }

        @Override public Dimension minimumLayoutSize(Container parent) {
            return size(parent, MIN_BLOCK + parent.getInsets().left + parent.getInsets().right);
        }

        @Override public void layoutContainer(Container parent) {
            Insets in = parent.getInsets();
            int count = parent.getComponentCount();
            if (count == 0) return;
            int avail = Math.max(1, parent.getWidth() - in.left - in.right);
            int cols = columns(avail, count);
            int cw = Math.max(1, (avail - (cols - 1) * HGAP) / cols);
            int y = in.top;
            for (int i = 0; i < count; i += cols) {
                int row = 0;
                for (int k = i; k < Math.min(count, i + cols); k++) row = Math.max(row, blockHeight(parent.getComponent(k), cw));
                int x = in.left;
                for (int k = i; k < Math.min(count, i + cols); k++) {
                    parent.getComponent(k).setBounds(x, y, cw, row);
                    x += cw + HGAP;
                }
                y += row + VGAP;
            }
        }
    }

    /** For the tests: the strip's blocks, in order. */
    List<JComponent> directBlocks() {
        List<JComponent> out = new ArrayList<>();
        for (Component c : directStrip.getComponents()) if (c instanceof JComponent j) out.add(j);
        return out;
    }

    /**
     * Review M74 usability §4.4 (WCAG 2.5.3, label in name): a chip's accessible name CONTAINS its visible label, so a
     * person who says "click Other MCP client" reaches it; a title that does not already contain the label is prefixed
     * with it.
     */
    static String accessibleName(String title, String label) {
        String shown = label == null ? "" : label.replace("✓", "").strip();
        String t = title == null ? "" : title;
        if (shown.isEmpty() || t.toLowerCase(java.util.Locale.ROOT).contains(shown.toLowerCase(java.util.Locale.ROOT))) {
            return t;
        }
        return t.isEmpty() ? shown : shown + " — " + t;
    }

    private ActionChip chip(String title, String label, String why, Runnable go) {
        ActionChip c = new ActionChip(title, label, why, go);
        recolour.add(c::repaint);
        return c;
    }

    /** One column of the recent lists: its subheading and its rows. */
    private JComponent recentColumn(String title, JComponent rows) {
        JPanel col = new Fluid.Panel() {
            @Override public Dimension getPreferredSize() {
                // a recent path can be very wide: the columns share the width before their rows are measured
                return new Dimension(0, super.getPreferredSize().height);
            }
        };
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        col.setOpaque(false);
        col.setAlignmentX(LEFT_ALIGNMENT);
        col.setMinimumSize(new Dimension(0, 0));
        col.getAccessibleContext().setAccessibleName(title);
        col.add(blockHeading(title));
        col.add(Box.createVerticalStrut(UiTheme.SPACE_SM));
        col.add(rows);
        return col;
    }

    /** The two recent lists side by side on a wide page, one under the other on a narrow one. */
    private void arrangeRecents(boolean wide) {
        if (recentsArranged && recentsWide == wide) return;
        recentsArranged = true;
        recentsWide = wide;
        recents.removeAll();
        if (wide) {
            recents.setLayout(new GridLayout(1, 2, UiTheme.SPACE_LG, 0));
        } else {
            recents.setLayout(new BoxLayout(recents, BoxLayout.Y_AXIS));
        }
        recents.add(recentColumns[0]);
        if (!wide) recents.add(Box.createVerticalStrut(UiTheme.SPACE_MD));
        recents.add(recentColumns[1]);
        recents.revalidate();
        recents.repaint();
    }

    /** For the tests: the strip's actions, in order. */
    List<ActionChip> directActions() {
        List<ActionChip> out = new ArrayList<>();
        collect(directStrip, out);
        return out;
    }

    private static void collect(Container c, List<ActionChip> out) {
        for (Component k : c.getComponents()) {
            if (k instanceof ActionChip a) out.add(a);
            else if (k instanceof Container inner) collect(inner, out);
        }
    }

    JPanel directStrip() {
        return directStrip;
    }

    JPanel recentsPanel() {
        return recents;
    }

    /**
     * One action of the strip: an icon and a short label, in the prompt's chip style (M74 UX). Drawn rather than styled —
     * FlatLaf owns a JButton's background — but still a JButton, so keyboard activation, focus traversal, the tooltip
     * (the old card's sentence) and the accessible name (the old card's title, which tests and docs name) are the
     * platform's.
     */
    static final class ActionChip extends JButton {
        private static final int H_PAD = 14;
        private String title;
        private String label;
        private String why;

        ActionChip(String title, String label, String why, Runnable go) {
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setCopy(title, label, why);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addActionListener(e -> go.run());
        }

        void setCopy(String title, String label, String why) {
            this.title = title;
            this.label = label;
            this.why = why;
            setToolTipText(why == null || why.isBlank() ? null : UiText.tooltip(why));
            getAccessibleContext().setAccessibleName(accessibleName(title, label));
            getAccessibleContext().setAccessibleDescription(why);
            revalidate();
            repaint();
        }

        String title() {
            return title;
        }

        String label() {
            return label;
        }

        String why() {
            return why;
        }

        private static Font font() {
            Font f = UIManager.getFont("Label.font");
            return f != null ? f : new Font(Font.SANS_SERIF, Font.PLAIN, 12);
        }

        @Override
        public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(font());
            // owner UI feedback (2026-10-17): a proper target — at least TARGET_MIN_HEIGHT tall, one height for every chip
            return new Dimension(H_PAD + 18 + 8 + fm.stringWidth(label == null ? "" : label) + H_PAD,
                    Math.max(UiTheme.TARGET_MIN_HEIGHT + 2, fm.getHeight() + 16));
        }

        @Override
        public Dimension getMaximumSize() {
            return getPreferredSize();
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            boolean hot = getModel().isRollover() || getModel().isPressed();
            boolean focused = isFocusOwner();
            int w = getWidth(), h = getHeight();
            float arc = 2f * UiTheme.CONTROL_RADIUS;
            // the focus ring sits OUTSIDE the shape (an inset of 2 px), so it reads as focus and not as a thicker border
            RoundRectangle2D shape = new RoundRectangle2D.Float(2.5f, 2.5f, w - 5f, h - 5f, arc, arc);
            g.setColor(hot ? UiTheme.accentWash(0.16f) : UiTheme.accentWash(0.06f));
            g.fill(shape);
            // review §4.1: the outline at rest is a control's boundary — 3:1 on the surface (it was 2.06:1)
            g.setColor(hot || focused ? UiTheme.accent() : UiTheme.controlBorder());
            g.draw(shape);
            if (focused) {
                g.setStroke(new BasicStroke(2f));
                g.setColor(UiTheme.accent());
                g.draw(new RoundRectangle2D.Float(1f, 1f, w - 2f, h - 2f, arc + 3f, arc + 3f));
                g.setStroke(new BasicStroke(1f));
            }
            g.setColor(UiTheme.accentText());
            paintGlyph(g, title, H_PAD, (h - 17) / 2);
            g.setFont(font());
            FontMetrics fm = g.getFontMetrics();
            g.drawString(label == null ? "" : label, H_PAD + 18 + 8, (h - fm.getHeight()) / 2 + fm.getAscent());
            g.dispose();
        }

        /** Small vector marks stay crisp at any scale and inherit the chip's current theme ink. */
        static void paintGlyph(Graphics2D g, String title, int x, int y) {
            Graphics2D icon = (Graphics2D) g.create();
            icon.translate(x, y);
            icon.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            String t = title == null ? "" : title;
            if (t.startsWith("Take a guided tour") || t.startsWith("Open sample")) {
                icon.drawOval(1, 1, 15, 15);
                icon.drawLine(7, 5, 12, 8);
                icon.drawLine(12, 8, 7, 11);
                icon.drawLine(7, 11, 7, 5);
            } else if (t.startsWith("Open evidence bundle")) {
                icon.drawRoundRect(2, 1, 13, 15, 2, 2);
                icon.drawLine(5, 5, 11, 5);
                icon.drawLine(5, 9, 12, 9);
                icon.drawLine(5, 12, 9, 12);
            } else if (t.startsWith("Create project profile")) {
                icon.drawRoundRect(1, 1, 14, 14, 2, 2);
                icon.drawLine(8, 4, 8, 12);
                icon.drawLine(4, 8, 12, 8);
            } else if (t.startsWith("Create from template")) {
                icon.drawRoundRect(1, 1, 14, 14, 2, 2);
                icon.drawLine(8, 2, 8, 14);
                icon.drawLine(2, 8, 14, 8);
            } else if (t.startsWith("Open audit")) {
                icon.drawRoundRect(2, 1, 13, 15, 2, 2);
                for (int row : new int[]{5, 8, 11}) icon.drawLine(5, row, 12, row);
            } else if (t.startsWith("Open GraphML")) {
                icon.drawLine(4, 4, 13, 5);
                icon.drawLine(4, 4, 7, 13);
                icon.drawLine(13, 5, 7, 13);
                icon.fillOval(1, 1, 5, 5);
                icon.fillOval(11, 3, 5, 5);
                icon.fillOval(5, 11, 5, 5);
            } else if (t.startsWith("Open project")) {
                icon.drawPolyline(new int[]{1, 1, 6, 8, 16, 16, 1},
                        new int[]{14, 3, 3, 5, 5, 14, 14}, 7);
            } else if (t.startsWith("Settings — source")) {
                icon.drawOval(4, 4, 9, 9);
                for (int k = 0; k < 8; k++) {
                    double a = k * Math.PI / 4;
                    icon.drawLine((int) Math.round(8.5 + 5 * Math.cos(a)), (int) Math.round(8.5 + 5 * Math.sin(a)),
                            (int) Math.round(8.5 + 7.5 * Math.cos(a)), (int) Math.round(8.5 + 7.5 * Math.sin(a)));
                }
            } else {
                icon.drawOval(1, 2, 8, 8);
                icon.drawLine(8, 9, 15, 16);
                icon.drawLine(12, 12, 15, 9);
            }
            icon.dispose();
        }
    }

    /**
     * build → deploy → ANALYSE → commit, with the one stage this app occupies filled and the rest
     * outlined. The point is placement, not process: a reader should see in one glance that the
     * analyser sits AFTER the thing ran and BEFORE the change is made, which is a claim about when it
     * is useful, and a sentence makes that claim far more slowly than a strip does.
     */
    private static final class Lifecycle extends JComponent {
        private static final String[] STAGES = {"build", "deploy", "ANALYSE", "commit"};
        private static final int HERE = 2;

        private static final int CHIP_H = 24;
        private static final int ARROW = 20;

        Lifecycle() {
            setAlignmentX(LEFT_ALIGNMENT);
            addComponentListener(new java.awt.event.ComponentAdapter() {
                private int lastWidth = -1;

                @Override
                public void componentResized(java.awt.event.ComponentEvent e) {
                    if (getWidth() == lastWidth) return;
                    lastWidth = getWidth();
                    revalidate();      // one row or two, depending on how much width we were given
                }
            });
        }

        @Override
        public Dimension getPreferredSize() {
            int w = getWidth() > 0 ? getWidth() : Integer.MAX_VALUE;
            return new Dimension(240, render(null, w));
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }

        @Override
        public Dimension getMinimumSize() {
            return new Dimension(80, getPreferredSize().height);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            render(g, getWidth());
            g.dispose();
        }

        /**
         * Lay the strip out inside {@code width}, painting when {@code g} is non-null and only
         * measuring when it is null.
         *
         * <p>One method for both so the height the layout is told and the height actually drawn cannot
         * drift apart — the way they do when a paint routine wraps and a hard-coded preferred size
         * does not, which shows up as a clipped final chip.
         *
         * @return the height the strip needs at that width
         */
        private int render(Graphics2D g, int width) {
            Font base = UIManager.getFont("Label.font");
            if (base == null) base = getFont();
            if (base == null) base = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
            Font f = base.deriveFont(Font.BOLD, base.getSize2D() - 1f);
            FontMetrics fm = getFontMetrics(f);
            if (g != null) g.setFont(f);

            int x = 0;
            int y = 0;
            int rowH = CHIP_H + 6;
            for (int i = 0; i < STAGES.length; i++) {
                int cw = fm.stringWidth(STAGES[i]) + 20;
                boolean last = i == STAGES.length - 1;
                int need = cw + (last ? 0 : ARROW);
                if (x > 0 && x + need > width) {       // does not fit — carry on underneath
                    x = 0;
                    y += rowH;
                }
                if (g != null) {
                    RoundRectangle2D chip =
                            new RoundRectangle2D.Float(x, y, cw, CHIP_H, CHIP_H, CHIP_H);
                    if (i == HERE) {
                        g.setColor(UiTheme.accent());
                        g.fill(chip);
                        g.setColor(UiTheme.onAccent());
                    } else {
                        g.setColor(UiTheme.mix(UiTheme.surfaceEdge(), UiTheme.accent(), 0.20f));
                        g.draw(chip);
                        g.setColor(UiTheme.mutedForeground());
                    }
                    g.drawString(STAGES[i], x + 10,
                            y + (CHIP_H - fm.getHeight()) / 2 + fm.getAscent());
                }
                x += cw;
                if (!last) {
                    if (g != null) {
                        g.setColor(UiTheme.mutedForeground());
                        int my = y + CHIP_H / 2;
                        g.drawLine(x + 5, my, x + 15, my);
                        g.drawLine(x + 12, my - 3, x + 15, my);
                        g.drawLine(x + 12, my + 3, x + 15, my);
                    }
                    x += ARROW;
                }
            }
            return y + CHIP_H + 4;
        }
    }

    /**
     * Re-resolve every theme-derived colour. Called on construction and whenever the LAF changes:
     * anything set once at build time keeps a Dark-theme colour after a switch to Light, which is the
     * classic Swing theming bug and shows up as unreadable grey-on-grey text.
     */
    private void applyColours() {
        setBackground(UiTheme.surface());
        recolour.forEach(Runnable::run);
        repaint();
    }

    @Override
    public void updateUI() {
        super.updateUI();
        if (recolour != null && !recolour.isEmpty()) SwingUtilities.invokeLater(this::applyColours);
    }
}
