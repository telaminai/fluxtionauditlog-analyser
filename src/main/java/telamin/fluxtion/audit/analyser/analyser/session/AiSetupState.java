package telamin.fluxtion.audit.analyser.analyser.session;

/**
 * M77 (spec-ai-setup-wizard.md §8.2): <i>Set up AI</i> as {@code aiSetup} decided it — published in the
 * {@link SessionSnapshot}; the wizard, the start page's AI block and line, and the assistant panel's nothing-set-up state
 * render THIS and decide nothing (rule 9).
 *
 * <p>Enums, ids, times and the node's own words only — never a path, a key, raw CLI output, an e-mail or an
 * organisation (S7). A path the person may see (where {@code claude} was found) is a reference into the frame's store,
 * rendered by the view.
 *
 * @param open        the wizard's window is open (one instance)
 * @param firstRun    the start page shows the first-run block (§6.0) instead of the AI line
 * @param origin      why the wizard opened
 * @param step        the screen
 * @param choice      the person's Screen 1 choice in this wizard (or in the first-run block)
 * @param inForce     the choice in force, DERIVED from the settings (§14.1) — what Screen 1 preselects
 * @param posture     the Safe / Full sub-choice on Screen 2a
 * @param fullMode    Full Claude Code's mode on Screen 2a ({@code default}, {@code acceptEdits}, {@code auto},
 *                    {@code plan})
 * @param installed   the <i>Installed</i> check
 * @param signedIn    the <i>Signed in</i> check
 * @param confirming  which confirmation is showing (the view disables the wizard behind it)
 * @param pending     the waiting question (§12), or {@link Pending#NONE}
 * @param finished    the Apply request Finish was pressed for, or 0 (read by {@code startPagePrompt} for placement)
 * @param line        the ONE status line of the wizard's screen — a refusal, a cancelled note — in the node's words
 * @param aiLine      the start page's AI line (§6.0), without its link
 * @param aiLineLink  the AI line's link: {@code "Set up AI…"} or {@code "Change…"}
 * @param panelLine   the assistant panel's one line while nothing can answer from it (§6.6), or ""
 * @param claudeFound the resolver found {@code claude} (a file check; nothing was run)
 * @param codexFound  the resolver found {@code codex} (a file check; nothing was run)
 * @param wizardEntry the first use opens this wizard (M77-2) rather than the profile cards (M77-1)
 * @param applying    an Apply asked for by this wizard is in flight (its result has not arrived)
 * @param suggested   the Summary's one suggested first action (§6.4, §7.3), chosen by the node
 * @param external    Screen 2b (M77-3): the client, external access, the registration and the live check
 */
public record AiSetupState(boolean open, boolean firstRun, Origin origin, Step step, Choice choice, Choice inForce,
                           Posture posture, String fullMode, Check installed, Auth signedIn, Confirming confirming,
                           Pending pending, long finished, String line, String aiLine, String aiLineLink,
                           String panelLine, boolean claudeFound, boolean codexFound, boolean wizardEntry,
                           boolean applying, Suggested suggested, External external) {

    /**
     * The Summary's one suggested first action (§6.4). Nothing it does sends anything: it opens the sample, starts the
     * guided tour, or only says where to ask.
     */
    public enum Suggested { NONE, OPEN_SAMPLE, RIGHT_CLICK, TOUR, COPY_QUESTION }

    /** Why the wizard opened (§8.2). */
    public enum Origin { NONE, FIRST_RUN, MENU, START_LINE, SEND, ASK, SETTINGS }

    /** The screen (§6). */
    public enum Step { CHOICE, INSIDE, CLIENT, OTHER, SUMMARY }

    /** Screen 1's choice, and the derived choice in force (§14.1). */
    public enum Choice { NONE, NO_AI, CLIENT, INSIDE, BOTH, IN_APP }

    /** Screen 2a's sub-choice. */
    public enum Posture { SAFE, FULL }

    /** Which confirmation is showing. */
    public enum Confirming { NONE, FULL, EXTERNAL, REGISTER }

    /** The external client the person set up ({@code assistant.setup.client}); {@link #NONE} when none was. */
    public enum Client {
        NONE, CLAUDE_CODE, CODEX, GENERIC;

        /** The stored value, or null for {@link #NONE}. */
        public String stored() {
            return this == NONE ? null : name();
        }

        /** A stored value read back: anything not a client's name is {@link #NONE}. */
        public static Client parse(String stored) {
            if (stored == null) return NONE;
            for (Client c : values()) if (c != NONE && c.name().equals(stored.trim())) return c;
            return NONE;
        }
    }

    /** The checks a person can run again (§8.3). */
    public enum CheckKind { INSTALLED, SIGNED_IN, REGISTRATION, LIVE, READINESS }

    /** <i>Installed</i>: {@code claude --version}. */
    public enum Installed { UNCHECKED, RUNNING, FOUND, NOT_FOUND, DID_NOT_RUN }

    /**
     * <i>Signed in</i>: {@code claude auth status --json}. {@link #KEY} is an API key or a key helper: {@code auth status}
     * does not validate a key (verified 2026-10-18, §9), so it is never called "signed in".
     */
    public enum SignedIn { UNCHECKED, RUNNING, SIGNED_IN, KEY, NOT_SIGNED_IN, UNKNOWN }

    /** How Claude Code authenticates, from {@code authMethod} (§8.6). */
    public enum Method { NONE, CLAUDE_AI, API_KEY, API_KEY_HELPER, OTHER }

    /** The plan, from a fixed list (§8.6); anything else is {@link #OTHER}, never echoed. */
    public enum Plan {
        NONE, PRO, MAX, TEAM, ENTERPRISE, OTHER;

        public static Plan parse(String s) {
            if (s == null || s.isBlank()) return NONE;
            return switch (s.trim().toLowerCase(java.util.Locale.ROOT)) {
                case "pro" -> PRO;
                case "max" -> MAX;
                case "team" -> TEAM;
                case "enterprise" -> ENTERPRISE;
                default -> OTHER;
            };
        }
    }

    /** Where an API key comes from, from {@code apiKeySource}; never the raw value. */
    public enum KeySource { NONE, ANTHROPIC_API_KEY, API_KEY_HELPER, OTHER }

    /** The <i>Installed</i> check: its verdict, the version's digits, and where {@code claude} is (by reference). */
    public record Check(Installed verdict, String version, long pathRef) {
        public static final Check UNCHECKED = new Check(Installed.UNCHECKED, "", 0);

        public Check {
            verdict = verdict == null ? Installed.UNCHECKED : verdict;
            version = version == null ? "" : version;
        }
    }

    /** The <i>Signed in</i> check: its verdict, the method, the plan and where a key comes from. */
    public record Auth(SignedIn verdict, Method method, Plan plan, KeySource keySource) {
        public static final Auth UNCHECKED = new Auth(SignedIn.UNCHECKED, Method.NONE, Plan.NONE, KeySource.NONE);

        public Auth {
            verdict = verdict == null ? SignedIn.UNCHECKED : verdict;
            method = method == null ? Method.NONE : method;
            plan = plan == null ? Plan.NONE : plan;
            keySource = keySource == null ? KeySource.NONE : keySource;
        }
    }

    /** What kind of question waits (§12). */
    public enum PendingKind { NONE, START_BOX, ASK_DRAFT }

    /** The waiting question: its kind and its reference into the frame's store (never its words). */
    public record Pending(PendingKind kind, long ref) {
        public static final Pending NONE = new Pending(PendingKind.NONE, 0);

        public Pending {
            kind = kind == null ? PendingKind.NONE : kind;
        }

        public boolean waiting() {
            return kind != PendingKind.NONE;
        }
    }

    /**
     * External MCP/REST access as Screen 2b and the AI line show it (§8.2, review R1-18): from {@code actionListener}'s
     * state and the readiness poll.
     */
    public enum Access { OFF, CONFIRMING, ON, CLOSED_FOR_SESSION, OTHER_INSTANCE }

    /** Screen 2b part 3: what is known of the client's registration. */
    public enum Registration { UNCHECKED, RUNNING, PRESENT, ABSENT, UNKNOWN, NO_LAUNCHER, COPIED, REGISTERED, FAILED }

    /** Screen 2b part 4: the live check (§9.4). */
    public enum LiveVerdict { IDLE, WAITING, WAITING_LONG, SEEN, OTHER_INSTANCE, ACCESS_OFF }

    /**
     * The live check: its verdict; when a client was seen (epoch millis) and its self-reported name by reference; and
     * whether a Full Claude Code session ran in the panel while it was checked (the check cannot tell them apart).
     */
    public record Live(LiveVerdict verdict, long at, long labelRef, boolean fullSessionSeen) {
        public static final Live IDLE = new Live(LiveVerdict.IDLE, 0, 0, false);

        public Live {
            verdict = verdict == null ? LiveVerdict.IDLE : verdict;
        }
    }

    /**
     * Screen 2b (M77-3) and the external-access parts of Screen 1 and the closing line.
     *
     * @param client           part 1's client
     * @param access           part 2: external access now
     * @param registration     part 3
     * @param live             part 4
     * @param launcher         this copy can be registered (a supported launcher was found)
     * @param offerTurnOff     OD-77.11: this wizard turned access on and was cancelled — the closing line offers Turn it off
     * @param alsoTurnOff      OD-77.5: the box under No AI is ticked
     * @param offerAlsoTurnOff OD-77.5: the box is shown (No AI chosen while access is on)
     */
    public record External(Client client, Access access, Registration registration, Live live, boolean launcher,
                           boolean offerTurnOff, boolean alsoTurnOff, boolean offerAlsoTurnOff) {
        public static final External NONE = new External(Client.CLAUDE_CODE, Access.OFF, Registration.UNCHECKED,
                Live.IDLE, true, false, true, false);

        public External {
            client = client == null || client == Client.NONE ? Client.CLAUDE_CODE : client;
            access = access == null ? Access.OFF : access;
            registration = registration == null ? Registration.UNCHECKED : registration;
            live = live == null ? Live.IDLE : live;
        }

        /** Part 3 is done: the client knows how to start the bridge (or the person copied it to run it themselves). */
        public boolean registered() {
            return registration == Registration.PRESENT || registration == Registration.REGISTERED
                    || registration == Registration.COPIED;
        }
    }

    public AiSetupState {
        origin = origin == null ? Origin.NONE : origin;
        step = step == null ? Step.CHOICE : step;
        choice = choice == null ? Choice.NONE : choice;
        inForce = inForce == null ? Choice.NONE : inForce;
        posture = posture == null ? Posture.SAFE : posture;
        fullMode = fullMode == null ? "default" : fullMode;
        installed = installed == null ? Check.UNCHECKED : installed;
        signedIn = signedIn == null ? Auth.UNCHECKED : signedIn;
        confirming = confirming == null ? Confirming.NONE : confirming;
        pending = pending == null ? Pending.NONE : pending;
        line = line == null ? "" : line;
        aiLine = aiLine == null ? "" : aiLine;
        aiLineLink = aiLineLink == null ? "" : aiLineLink;
        panelLine = panelLine == null ? "" : panelLine;
        suggested = suggested == null ? Suggested.NONE : suggested;
        external = external == null ? External.NONE : external;
    }

    public static final AiSetupState IDLE = new AiSetupState(false, false, Origin.NONE, Step.CHOICE, Choice.NONE,
            Choice.NONE, Posture.SAFE, "default", Check.UNCHECKED, Auth.UNCHECKED, Confirming.NONE, Pending.NONE, 0, "",
            "", "", "", false, false, false, false, Suggested.NONE, External.NONE);

    /** Whether the primary button of Screen 2a may be pressed: <i>Installed</i> passed (§6.2). */
    public boolean insideMayProceed() {
        return installed.verdict() == Installed.FOUND;
    }

    /** Screen 2b's Finish may be pressed: part 2 is on, or this copy cannot be registered (Finish without a client). */
    public boolean clientMayFinish() {
        return !applying && confirming == Confirming.NONE && (external.access() != Access.OFF
                && external.access() != Access.CONFIRMING || external.registration() == Registration.NO_LAUNCHER);
    }

    /** Nothing is set up: the derived choice is {@link Choice#NONE}. */
    public boolean nothingSetUp() {
        return inForce == Choice.NONE;
    }
}
