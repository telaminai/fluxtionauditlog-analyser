package telamin.fluxtion.audit.analyser.analyser.session;

import telamin.fluxtion.audit.analyser.analyser.session.AiSetupState.Auth;
import telamin.fluxtion.audit.analyser.analyser.session.AiSetupState.Check;
import telamin.fluxtion.audit.analyser.analyser.session.AiSetupState.Choice;
import telamin.fluxtion.audit.analyser.analyser.session.AiSetupState.Plan;

/**
 * M77 (spec-ai-setup-wizard.md §6, §8.1): every sentence of <i>Set up AI</i> — what {@code aiSetup} publishes and the
 * fixed copy the wizard shows — in ONE class, so {@code UserVisibleJargonTest} and {@code FaqSecurityContractTest} read
 * one place, and no surface composes a line of its own (rule 9, ★ T16, T23).
 *
 * <p>The product is named through {@link #PRODUCT} only (OD-77.7, r13-4): "the analyser" until the rename PR.
 */
public final class AiSetupLines {

    private AiSetupLines() {
    }

    /** OD-77.7: the product's name in the wizard's copy. The rename PR (r13-4) changes it here, once. */
    public static final String PRODUCT = "the analyser";

    /** {@link #PRODUCT} at the start of a sentence. */
    public static String productCapitalised() {
        return Character.toUpperCase(PRODUCT.charAt(0)) + PRODUCT.substring(1);
    }

    // ---- the window and its footer ----------------------------------------------------------------------------------

    public static final String TITLE = "Set up AI";
    public static final String QUESTION = "How do you want AI to work with " + PRODUCT + "?";
    public static final String FOOTER = "Nothing is sent to an AI model from this window.";
    public static final String FIRST_RUN_FOOTER = "Nothing is sent or started until you choose.";
    public static final String SET_UP_LINK = "Set up AI…";
    public static final String CHANGE_LINK = "Change…";

    // ---- Screen 1 (§6.1) ---------------------------------------------------------------------------------------------

    public static final String NO_AI_TITLE = "No AI";
    public static final String NO_AI_TEXT = "Everything in " + PRODUCT + " works without AI: records, topology, charts, "
            + "reports, walks and the tour. You can set AI up later from the assistant's mode chip ▸ Set up AI…";
    public static final String CLIENT_TITLE = "Your own AI client connects to " + PRODUCT;
    public static final String CLIENT_SHORT = "Your own AI client connects";
    /** OD-77.18 (r4): "another AI app that can connect to {Product}", not "another MCP client". */
    public static final String CLIENT_TEXT = "Claude Code, Codex or another AI app that can connect to " + PRODUCT
            + " reads records, draws charts and points at evidence here. You keep using it in your terminal.";
    public static final String CLIENT_TURNS_OFF_INSIDE = "This turns off Claude in the panel — choose Both to keep it.";
    public static final String INSIDE_TITLE = "Claude inside " + PRODUCT;
    public static final String INSIDE_TEXT = "The same Claude Code, run by " + PRODUCT + " in its assistant panel. Ask "
            + "from any record, chart or the start page.";
    public static final String BOTH_TITLE = "Both";
    public static final String BOTH_TEXT = "Claude in the panel, and your terminal client can drive " + PRODUCT + " too.";
    public static final String OTHER_OPTIONS_LINK = "Other options: the in-app assistant with your own API key…";
    public static final String FOUND_TOOLTIP = "Found on this machine; nothing was run.";
    public static final String CLAUDE_FOUND = "Claude Code found";
    public static final String CLAUDE_NOT_FOUND = "Claude Code not found";
    public static final String CODEX_FOUND = "Codex found";
    /** The first-run block's "No AI" with external access on (OD-77.5): ticked by default. */
    public static final String ALSO_TURN_OFF_ACCESS = "Also turn off External MCP/REST access";
    public static final String NOT_NOW = "Not now";

    // ---- the primary button: what it does, in its own words (usability fix, 2026-10-18) ------------------------------
    //
    // The scorecard (docs/usability/scorecard.md) measured Continue and Finish as a hesitation each: neither says what
    // pressing does. Each label below says it. Continue and Finish remain only where nothing more exact is true.

    public static final String CONTINUE = "Continue";
    public static final String FINISH = "Finish";
    /** Screen 1 / the first-run block, Claude inside or Both: Continue opens 2a, which runs the two read-only checks. */
    public static final String CHECK_CLAUDE_CODE = "Check Claude Code";
    /** Screen 1 / the first-run block, your own client: Continue opens Screen 2b. */
    public static final String SET_UP_MY_CLIENT = "Set up my client";
    /** Screen 1 / the first-run block, No AI: Off is applied at once (§6.0). */
    public static final String CONTINUE_WITHOUT_AI = "Continue without AI";
    /** 2a for Both: Claude inside is applied, then Screen 2b. */
    public static final String CONTINUE_TO_MY_CLIENT = "Continue to my client";
    /**
     * The last screen's Finish while a question waits (OD-77.2): the window closes, no Summary, and the question is placed
     * in the panel with the focus on Send. Nothing is sent from this window, so the label says the person sends it.
     */
    public static final String FINISH_THEN_SEND = "Finish, then send my question";

    /** The first-run block's and Screen 1's primary for {@code c}: what Continue does for that choice. */
    public static String choiceProceedLabel(Choice c) {
        return switch (c == null ? Choice.NONE : c) {
            case INSIDE, BOTH -> CHECK_CLAUDE_CODE;
            case CLIENT -> SET_UP_MY_CLIENT;
            case NO_AI -> CONTINUE_WITHOUT_AI;
            case IN_APP, NONE -> CONTINUE;
        };
    }

    /** The wizard's primary button on the screen {@code s} shows — rendered from the snapshot, decided nowhere else. */
    public static String primaryLabel(AiSetupState s) {
        AiSetupState st = s == null ? AiSetupState.IDLE : s;
        // a question waits and THIS press is the one that places it (§12): only a screen that applies an assistant
        boolean placing = st.pending().waiting();
        return switch (st.step()) {
            case CHOICE -> choiceProceedLabel(st.choice());
            case INSIDE -> st.posture() == AiSetupState.Posture.FULL ? CONFIRM_FULL_BUTTON
                    : st.choice() == Choice.BOTH ? CONTINUE_TO_MY_CLIENT
                    : placing ? FINISH_THEN_SEND : FINISH;
            case OTHER -> placing ? FINISH_THEN_SEND : FINISH;
            case SUMMARY -> DONE;
            case CLIENT -> st.external().registration() == AiSetupState.Registration.NO_LAUNCHER ? FINISH_WITHOUT_CLIENT
                    : st.external().live().verdict() == AiSetupState.LiveVerdict.WAITING
                    || st.external().live().verdict() == AiSetupState.LiveVerdict.WAITING_LONG ? FINISH_WITHOUT_CHECKING
                    // your own client alone turns the panel off, so nothing is placed; Both places it here
                    : placing && st.choice() == Choice.BOTH ? FINISH_THEN_SEND : FINISH;
        };
    }

    /** Screen 1's one-line sentence for a choice, as the first-run block shows it under the selection. */
    public static String sentenceFor(Choice c) {
        return switch (c == null ? Choice.NONE : c) {
            case NO_AI -> NO_AI_TEXT;
            case CLIENT -> CLIENT_TEXT;
            case INSIDE -> INSIDE_TEXT;
            case BOTH -> BOTH_TEXT;
            case IN_APP, NONE -> "";
        };
    }

    // ---- Where data goes: the option diagram (§6.1a, M77-5) ---------------------------------------------------------
    // Every word FlowDiagram draws or speaks. No drawn string says MCP, REST or CLI (★ T28); the four sent lines are
    // pinned verbatim in FaqSecurityContractTest, because each one fixes an overclaim the prototype made.

    /** The picture's accessible name. */
    public static final String DIAGRAM_NAME = "Where data goes";
    /** The machine boundary's caption. */
    public static final String DIAGRAM_THIS_COMPUTER = "This computer";
    /** The product node's label; below 400 px the short one. */
    public static final String DIAGRAM_PRODUCT = productCapitalised();
    public static final String DIAGRAM_PRODUCT_SHORT = shortName(PRODUCT);
    public static final String DIAGRAM_CLAUDE = "Claude";
    public static final String DIAGRAM_CLAUDE_SAFE = "in the panel · Safe";
    public static final String DIAGRAM_CLAUDE_FULL = "in the panel · Full";
    public static final String DIAGRAM_CLAUDE_SAFE_SHORT = "Safe";
    public static final String DIAGRAM_CLAUDE_FULL_SHORT = "Full";
    /** The tag on the Claude pair when Full is drawn (amber, caution). */
    public static final String DIAGRAM_FULL_TAG = "Full";
    public static final String DIAGRAM_CLIENT = "Your AI client";
    public static final String DIAGRAM_CLIENT_SHORT = "AI client";
    public static final String DIAGRAM_CLAUDE_SERVICE = "Anthropic";
    public static final String DIAGRAM_CLIENT_SERVICE = "Its AI service";
    public static final String DIAGRAM_CLIENT_SERVICE_SHORT = "AI service";
    public static final String DIAGRAM_NO_AI = "No AI";

    /** The chips on the arrows (§6.1a): mouse, target, clipboard, toggle, padlock, window, page. */
    public static final String DIAGRAM_ASK = "Ask";
    public static final String DIAGRAM_POINTS = "points";
    public static final String DIAGRAM_YOU_COPY = "you copy";
    public static final String DIAGRAM_OFF = "off";
    public static final String DIAGRAM_CLOSED = "closed";
    public static final String DIAGRAM_ANOTHER_WINDOW = "another window";
    public static final String DIAGRAM_ANOTHER_WINDOW_SHORT = "elsewhere";
    public static final String DIAGRAM_WHAT_IT_READS = "what it reads";
    public static final String DIAGRAM_WHAT_IT_READS_SHORT = "reads";
    public static final String DIAGRAM_OWN_TOOLS = "+ your own tools";
    public static final String DIAGRAM_OWN_TOOLS_SHORT = "+ tools";

    /** The four sent lines, verbatim (§6.1a), and Full's suffix to the Claude-inside line. */
    public static final String DIAGRAM_SENT_NO_AI = "Nothing is sent to an AI.";
    public static final String DIAGRAM_SENT_CLIENT = "Sent to AI: what your client reads, sent by your client.";
    public static final String DIAGRAM_SENT_INSIDE = "Sent to AI: your question + what Claude reads, to Anthropic.";
    public static final String DIAGRAM_SENT_FULL_SUFFIX = "; your own tools may send more.";
    public static final String DIAGRAM_SENT_BOTH = "Sent to AI: Claude to Anthropic; your client to its own service.";

    /** Claude-inside's sent line: Full replaces the closing full stop with its suffix. */
    public static String diagramSentInside(boolean full) {
        return full ? DIAGRAM_SENT_INSIDE.substring(0, DIAGRAM_SENT_INSIDE.length() - 1) + DIAGRAM_SENT_FULL_SUFFIX
                : DIAGRAM_SENT_INSIDE;
    }

    /** The summary the text equivalent starts with: who drives whom. */
    public static final String DIAGRAM_SUMMARY_NO_AI = "No AI. " + productCapitalised() + " works on its own.";
    public static final String DIAGRAM_SUMMARY_CLIENT = "One way: your AI client works " + PRODUCT + "; " + PRODUCT
            + " cannot reach your client.";
    public static final String DIAGRAM_SUMMARY_INSIDE = "Two way: " + PRODUCT + " sends your right-click questions to "
            + "Claude, and Claude works " + PRODUCT + " back.";
    public static final String DIAGRAM_SUMMARY_BOTH = "Two way with Claude in the panel; one way from your AI client.";

    /** Each arrow's spoken sentence (the text equivalent, ★ T26). */
    public static final String DIAGRAM_SAY_ASK = "A right-click Ask on a record, node or chart goes straight to Claude in "
            + "the panel.";
    public static final String DIAGRAM_SAY_CLAUDE_POINTS = "Claude reads the open log and points at things on the "
            + "screen; " + PRODUCT + " asks you before it changes what is open.";
    public static final String DIAGRAM_SAY_CLAUDE_POINTS_FULL = "Claude reads the open log and points at things on the "
            + "screen, with your own Claude Code settings.";
    public static final String DIAGRAM_SAY_CLAUDE_SENDS = "Claude sends your question and what it reads to Anthropic.";
    public static final String DIAGRAM_SAY_CLAUDE_SENDS_FULL = "Claude sends your question and what it reads to "
            + "Anthropic. In Full, your own tools and servers can send data elsewhere too.";
    public static final String DIAGRAM_SAY_CLIENT_POINTS = "Your AI client reads the open log and points at things on "
            + "the screen.";
    public static final String DIAGRAM_SAY_CLIENT_OFF = "Your AI client cannot reach " + PRODUCT + " until you allow "
            + "it.";
    public static final String DIAGRAM_SAY_CLIENT_CLOSED = "Your AI client cannot reach " + PRODUCT + " while the Full "
            + "Claude session in the panel runs.";
    public static final String DIAGRAM_SAY_CLIENT_OTHER_WINDOW = "Another " + PRODUCT.replaceFirst("^the ", "")
            + " window holds the connection, so your AI client reaches that window, not this one.";
    public static final String DIAGRAM_SAY_YOU_COPY = "A right-click does not reach your AI client: you copy the "
            + "question and paste it there yourself.";
    public static final String DIAGRAM_SAY_CLIENT_SENDS = "Your AI client sends what it reads, with your questions, to "
            + "its own AI service.";

    /**
     * OD-77.16: where Claude is billed, from the sign-in check's method — or null, naming nothing, when the check has not
     * said how Claude Code signs in.
     */
    public static String diagramBill(Auth a) {
        if (a == null) return null;
        if (a.verdict() != AiSetupState.SignedIn.SIGNED_IN && a.verdict() != AiSetupState.SignedIn.KEY) return null;
        return switch (a.method()) {
            case CLAUDE_AI -> "your Claude plan";
            case API_KEY, API_KEY_HELPER -> "your API key";
            case OTHER -> "your cloud provider";
            case NONE -> null;
        };
    }

    /** The spoken half of the bill: "Claude is billed to your Claude plan." */
    public static String diagramSayBill(String bill) {
        return "Claude is billed to " + bill + ".";
    }

    /** OD-77.17: Claude Code's own usage data is a link to the FAQ, not words on the picture. */
    public static final String DIAGRAM_USAGE_LINK = "What Claude Code itself sends…";
    /** The FAQ entry the link opens, relative to the docs site (FaqSecurityContractTest pins the heading). */
    public static final String DIAGRAM_USAGE_PAGE = "faq/#what-does-claude-code-itself-send-to-anthropic";

    /** "the analyser" → "Analyser": the product's short label, from {@link #PRODUCT}. */
    private static String shortName(String product) {
        String s = product.startsWith("the ") ? product.substring(4) : product;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // ---- Screen 2a (§6.2) --------------------------------------------------------------------------------------------

    public static final String INSIDE_STEP_BOTH = "Step 2 of 3 · Claude inside " + PRODUCT;
    public static final String CHECKS_INTRO = "Checking runs claude --version and claude auth status on this machine. No "
            + "question and no evidence is sent.";
    public static final String INSTALLED_RUNNING = "Looking for Claude Code…";
    public static final String SIGNED_IN_RUNNING = "Checking whether Claude Code is signed in…";
    public static final String NOT_FOUND = "Claude Code was not found.";
    public static final String HOW_TO_INSTALL = "How to install…";
    public static final String CHECK_AGAIN = "Check again";
    public static final String COPY_COMMAND = "Copy command";
    public static final String LOGIN_COMMAND = "claude auth login";
    public static final String NOT_SIGNED_IN = "Claude Code is not signed in. In a terminal run " + LOGIN_COMMAND
            + ", then Check again.";
    public static final String NOT_SIGNED_IN_CONTINUE = "You can continue; Claude Code will ask you to sign in when it "
            + "starts.";
    public static final String COULD_NOT_TELL = "Could not tell whether Claude Code is signed in. You can continue; Start "
            + "checks again.";
    public static final String STILL_CHECKING = "still checking — Start checks again";
    public static final String POSTURE_QUESTION = "How much of your Claude Code set-up should it use?";
    public static final String SAFE_TITLE = "Safe analysis (recommended)";
    public static final String SAFE_LINE_1 = "Reads your evidence through " + PRODUCT + "; asks you before it changes what "
            + "is open or writes a file.";
    public static final String SAFE_LINE_2 = "No shell, no file edits, no web. Your skills, hooks and MCP servers are not "
            + "loaded — safe with logs you did not write.";
    /**
     * Authoring walk (2026-10-18, shots 03/15): the wizard's line while a Build question waits — said plainly, because
     * the recommended Safe analysis cannot do what was asked.
     */
    public static final String BUILD_NEEDS_FULL = "Building an application needs Full Claude Code: Safe analysis reads and "
            + "explains your evidence but cannot write code. Full Claude Code in acceptEdits mode is recommended here — "
            + "you confirm it before anything is applied.";
    /**
     * Owner follow-up (2026-10-18): what {@code acceptEdits} means, under the mode choice — Claude Code accepts its own
     * file edits in the folder; Bash (mvn, regen) still reaches the analyser's approval dialog, Deny its default.
     */
    public static final String ACCEPT_EDITS_NOTE = "Claude edits files in this project without asking; commands still ask "
            + "you.";
    /** The mode a Build question preselects: Claude Code's own {@code acceptEdits} (never bypassPermissions). */
    public static final String BUILD_FULL_MODE = "acceptEdits";
    public static final String FULL_TITLE = "Full Claude Code";
    public static final String FULL_LINE = "Your own settings, skills, hooks, MCP servers and tools, as in your terminal.";
    public static final String CONFIRM_FULL_BUTTON = "Continue — confirm Full…";
    public static final String FOLDER_NOTE = "If you work in your own project folder, Claude Code asks once before it "
            + "starts there.";
    /** The honesty line (FaqSecurityContractTest pins it on every surface): the constant itself, by reference. */
    public static final String SENDS = telamin.fluxtion.audit.analyser.analyser.assistant.AssistantProfiles.CLAUDE_CODE_SENDS;
    public static final String IN_APP_SENDS =
            telamin.fluxtion.audit.analyser.analyser.assistant.AssistantProfiles.IN_APP_SENDS;

    /** The <i>Installed</i> row's words (the path, when found, is the view's to render from the reference). */
    public static String installedText(Check c) {
        return switch (c.verdict()) {
            case UNCHECKED -> "";
            case RUNNING -> INSTALLED_RUNNING;
            case FOUND -> "Claude Code " + (c.version().isEmpty() ? "" : c.version() + " ") + "is installed.";
            case NOT_FOUND -> NOT_FOUND;
            case DID_NOT_RUN -> "Claude Code was found but did not answer --version. Try running it once in a terminal.";
        };
    }

    /** The <i>Signed in</i> row's words: never "signed in" for an API key or a key helper (§9, ★ T7). */
    public static String signedInText(Auth a) {
        return switch (a.verdict()) {
            case UNCHECKED -> "";
            case RUNNING -> SIGNED_IN_RUNNING;
            case SIGNED_IN -> "Signed in with your Claude account (" + planWords(a.plan()) + ").";
            case KEY -> a.method() == AiSetupState.Method.API_KEY_HELPER
                    ? "Uses an API key helper from your Claude Code settings. Claude Code runs it when it starts."
                    : "Uses an API key" + (a.keySource() == AiSetupState.KeySource.ANTHROPIC_API_KEY
                    ? " (ANTHROPIC_API_KEY)" : "") + ". Claude Code checks the key when it starts.";
            case NOT_SIGNED_IN -> NOT_SIGNED_IN;
            case UNKNOWN -> COULD_NOT_TELL;
        };
    }

    /** §8.6: a fixed list; anything else is "your Claude plan", never echoed. */
    public static String planWords(Plan p) {
        return switch (p == null ? Plan.NONE : p) {
            case PRO -> "Pro plan";
            case MAX -> "Max plan";
            case TEAM -> "Team plan";
            case ENTERPRISE -> "Enterprise plan";
            case OTHER, NONE -> "your Claude plan";
        };
    }

    /** An accessible label for a check row: one sentence, glyph AND word (§15). */
    public static String accessible(String check, String words) {
        return check + ": " + (words == null || words.isEmpty() ? "not checked yet" : words);
    }

    // ---- Other options (§6.5) ----------------------------------------------------------------------------------------

    public static final String OTHER_TITLE = "The in-app assistant (your own API key)";
    public static final String KEY_NOTE = "Stored on this machine only (in cleartext), never in a project, a share or a "
            + "bundle.";
    public static final String CLAUDE_NEEDS_NO_KEY = "Claude Code is installed and needs no API key — Back to use it "
            + "instead.";
    public static final String ADD_A_KEY = "Add a key, or choose another option.";

    // ---- Screen 2b, your own client (§6.3, M77-3) ----------------------------------------------------------------------

    public static final String CLIENT_STEP_BOTH = "Step 3 of 3 · your own client";
    public static final String CLIENT_SCREEN_TITLE = "Your own AI client";
    public static final String PART_WHICH = "1. Which client?";
    public static final String CLIENT_CLAUDE_CODE = "Claude Code";
    public static final String CLIENT_CODEX = "Codex";
    public static final String CLIENT_GENERIC = "Another MCP client";
    /** Claude Desktop is covered by the generic choice, with today's sentence (no extension is offered). */
    public static final String GENERIC_COVERS_DESKTOP = "This covers Claude Desktop too: no extension is offered for it, "
            + "so add the copied configuration instead.";

    public static final String PART_REACH = "2. Let your client reach this window.";
    public static final String ACCESS_SWITCH = "Let programs on this computer reach this window";
    /** The setting's own name, as Settings and the AI menu spell it, so either can be found from here. */
    public static final String ACCESS_SETTING_NAME = "External MCP/REST access";
    public static final String ACCESS_LINE = "Only programs on this computer that have this run's key can connect. "
            + "Nothing leaves this computer.";
    public static final String ACCESS_ON = "✓ On.";
    public static final String ACCESS_CONFIRMING = "Waiting for your answer…";
    public static final String ACCESS_CLOSED = "Closed while the Full Claude Code session in the panel runs — it opens "
            + "again when that session ends.";

    public static final String PART_REGISTER = "3. Register " + PRODUCT + " with your client.";
    public static final String REGISTER_INTRO = "Your client needs to know how to start " + PRODUCT + "'s bridge. This "
            + "is the exact command:";
    public static final String SHOW_FULL_COMMAND = "Show the full command";
    public static final String SHOW_LESS = "Show less";
    public static final String COPY = "Copy";
    public static final String REGISTER_FOR_ME = "Register for me…";
    public static final String REPLACE_REGISTRATION = "Replace…";
    public static final String RUN_IT_MYSELF = "I'll run it myself";
    public static final String CHECK_REGISTRATION = "Check registration";
    public static final String GENERIC_INTRO = "Add it to your client's MCP settings, then restart the client.";
    public static final String COPY_CONFIGURATION = "Copy configuration";
    public static final String SAVE_SNIPPET = "Save snippet…";
    public static final String NO_LAUNCHER = "✗ This copy of " + PRODUCT + " cannot be registered: it was not started "
            + "with java -jar or the JBang install.";
    public static final String FINISH_WITHOUT_CLIENT = "Finish without a client";

    /** Part 3's line for what is known of the registration ("" when nothing is). */
    public static String registrationText(AiSetupState.Registration r) {
        return switch (r == null ? AiSetupState.Registration.UNCHECKED : r) {
            case UNCHECKED -> "";
            case RUNNING -> "⏳ Asking your client…";
            case PRESENT -> "✓ Already registered";
            case ABSENT -> "✗ Not registered yet";
            case UNKNOWN -> "? Could not tell — your client's configuration was not changed.";
            case NO_LAUNCHER -> NO_LAUNCHER;
            case COPIED -> "✓ Copied — run it in a terminal";
            case REGISTERED -> "✓ Registered (for this copy of " + PRODUCT + ")";
            case FAILED -> "✗ Your client did not accept the registration. Copy the command and run it in a terminal to "
                    + "see why.";
        };
    }

    public static final String PART_LIVE = "4. Can your client see " + PRODUCT + "?";
    public static final String LIVE_ASK = "Start a new session of your client and ask it:";
    /** The question the person asks their client: a read-only tool call, the earliest honest proof (§9.4). */
    public static final String LIVE_QUESTION = "Use analyser_context and tell me what is open.";
    public static final String LIVE_WAITING = "⏳ Waiting…";
    public static final String COPY_THE_QUESTION = "Copy the question";
    public static final String LIVE_NOT_YET = "Do parts 2 and 3 first; then this waits for your client.";
    public static final String LIVE_TIP_RESTART = "Restart your client after registering — in Claude Code, /mcp should "
            + "list fluxtion-analyser.";
    public static final String LIVE_TIP_ACCESS = "Check that External MCP/REST access is on (part 2).";
    public static final String LIVE_OTHER_INSTANCE = "✗ Another " + PRODUCT + " window owns the client connection, so "
            + "your client is reading that window's log. Close it, or set up from it.";
    public static final String LIVE_ACCESS_OFF = "✗ Access is off, so your client cannot reach this window.";
    public static final String LIVE_NOT_REGISTERED = "✗ Your client has no registration named fluxtion-analyser.";
    public static final String LIVE_FULL_SESSION = "A Full Claude Code session ran in the panel while this was checked; "
            + "this check cannot tell them apart.";
    public static final String FINISH_WITHOUT_CHECKING = "Finish without checking";
    public static final String MORE_OPTIONS = "More options…";

    /**
     * Part 2's Check connection (usability fix, 2026-10-18): on Screen 2b itself, under the switch, where the scorecard
     * found it behind More options… below the fold. It starts the bridge a client is registered with and asks it what is
     * open — the client dialog's check (M42.1). With access off it asks for access first, through the same confirmation.
     */
    public static final String CHECK_CONNECTION = "Check connection";
    public static final String CHECK_CONNECTION_TIP = "Starts the bridge your client will start and asks it what is open "
            + "here. Nothing is sent to an AI model.";
    /** Under the button while access is off: the press asks for access first. */
    public static final String CHECK_CONNECTION_ASKS = "Checking asks to turn access on first.";

    /** Part 2's line for the bridge check ("" before it ran). */
    public static String bridgeText(AiSetupState.Bridge b) {
        return switch (b == null ? AiSetupState.Bridge.UNCHECKED : b) {
            case UNCHECKED -> "";
            case CHECKING -> "⏳ Checking the bridge…";
            case VERIFIED -> "✓ Bridge works now: the command your client will run reached this window.";
            case REST_OFF -> "✗ Access is off, so the bridge cannot reach this window.";
            case OTHER_INSTANCE -> "✗ Another " + PRODUCT + " window owns the client connection; check from that window.";
            case LAUNCH_FAILED -> "✗ The bridge command did not start.";
            case PROTOCOL_FAILED -> "✗ The bridge started but did not answer in time. A cold start can be slow — check "
                    + "again once.";
            case ACTION_FAILED -> "✗ The bridge reached this window, but reading what is open failed.";
        };
    }

    /** ✓ part 4: honest words — a client with this window's key reached this window, not which client (§9.4). */
    public static String liveSeen(String hhmm) {
        return "✓ A client reached this window through the bridge at " + hhmm + ".";
    }

    /** OD-77.8: the client's self-reported name, labelled as its claim. */
    public static String selfReported(String label) {
        return label == null || label.isBlank() ? "" : "It calls itself \"" + label + "\".";
    }

    /** The client's name in a sentence. */
    public static String clientName(AiSetupState.Client c) {
        return switch (c == null ? AiSetupState.Client.NONE : c) {
            case CLAUDE_CODE -> CLIENT_CLAUDE_CODE;
            case CODEX -> CLIENT_CODEX;
            case GENERIC, NONE -> "your MCP client";
        };
    }

    // ---- the two confirmations (§9.2, §9.3), under the SafeChoice rule -----------------------------------------------

    public static final String ACCESS_CONFIRM_TITLE = "Turn on External MCP/REST access?";
    public static final String ACCESS_CONFIRM_TEXT = "An MCP client or a REST caller on this machine that has this run's "
            + "token can then read the open log and change what " + PRODUCT + " shows. The endpoint listens on this machine "
            + "only (127.0.0.1) and the token is new each run. A Full Claude Code session in the panel can use it too (it "
            + "can read the token file in your home folder).";
    public static final String ACCESS_CONFIRM_TURN_ON = "Turn on access";
    public static final String CANCEL = "Cancel";

    public static String registerTitle(AiSetupState.Client c) {
        return "Register " + PRODUCT + " with " + clientName(c);
    }

    public static final String REGISTER_RUNS = "This runs, on this machine:";
    public static final String REPLACE_RUNS = "This runs, on this machine, in this order (the old registration is "
            + "removed, then the new one added):";

    public static String registerAfter(AiSetupState.Client c) {
        return c == AiSetupState.Client.CODEX
                ? "It adds fluxtion-analyser to your Codex settings. " + productCapitalised() + " does not read or edit "
                + "that file itself."
                : "It adds fluxtion-analyser to your user-level Claude Code settings. " + productCapitalised()
                + " does not read or edit that file itself.";
    }

    public static final String REGISTER_BUTTON = "Register";
    public static final String REPLACE_BUTTON = "Replace";

    /** OD-77.11: the closing line after a Cancel, when this wizard turned access on. */
    public static final String ACCESS_STILL_ON = "External access is still on — Turn it off";
    /** The same words before the one-click button, where the button itself says "Turn it off". */
    public static final String ACCESS_STILL_ON_LEAD = "External access is still on —";
    public static final String TURN_IT_OFF = "Turn it off";

    // ---- the Summary (§6.4) ------------------------------------------------------------------------------------------

    public static final String SUMMARY_TITLE = "You're set up";
    public static final String SUMMARY_CHANGE = "Change any of this: the assistant's mode chip ▸ Set up AI…. Settings ▸ Assistant "
            + "keeps every option.";
    public static final String DONE = "Done";
    /** §6.4's suggested first actions. Nothing they do sends anything. */
    public static final String SUGGEST_SAMPLE = "Open the sample project";
    public static final String SUGGEST_TOUR = "Take the guided tour";
    public static final String RIGHT_CLICK_TO_ASK = "Right-click any record and choose Ask… to ask about it.";
    public static final String SUGGEST_COPY_QUESTION = "Copy a first question";
    /** §6.4: the first question for your own client — copied, never sent. */
    public static final String FIRST_QUESTION = "Use analyser_context, then tell me what happened in the open log.";
    public static final String ASK_IN_TERMINAL = "Ask it in your terminal.";

    /** The Summary's line for your own client: the client, the registration and the live check. */
    public static String clientSummary(AiSetupState.External e, String hhmm) {
        String reg = switch (e.registration()) {
            case PRESENT -> "registered";
            case REGISTERED -> "registered (for this copy of " + PRODUCT + ")";
            case COPIED -> "command copied";
            case NO_LAUNCHER -> "not registered (this copy cannot be)";
            default -> "registration not checked";
        };
        String live = e.live().verdict() == AiSetupState.LiveVerdict.SEEN && hhmm != null
                ? "connected at " + hhmm : "connection not checked";
        String name = e.client() == AiSetupState.Client.GENERIC ? CLIENT_GENERIC : clientName(e.client());
        return "Your own client: " + name + " · " + reg + " · " + live;
    }
    public static final String NEW_CONVERSATION = "Your next question starts a new conversation.";

    /** The Summary's line for what is in force. */
    public static String summaryLine(Choice inForce, AssistantProfile profile, Check installed, Auth auth) {
        String inside = "Claude inside " + PRODUCT + ": " + (profile == AssistantProfile.FULL ? "Full Claude Code"
                : "Safe analysis") + (installed.version().isEmpty() ? "" : " · Claude Code " + installed.version())
                + " · " + switch (auth.verdict()) {
                    case SIGNED_IN -> "signed in";
                    case KEY -> "uses an API key";
                    case NOT_SIGNED_IN -> "not signed in yet";
                    default -> "sign-in not checked";
                };
        return switch (inForce == null ? Choice.NONE : inForce) {
            case INSIDE, BOTH -> inside;
            case NO_AI -> "No AI";
            case IN_APP -> "In-app assistant · your API key";
            case CLIENT -> "Your own client";
            case NONE -> "Nothing is set up";
        };
    }

    /** OD-77.2: the panel's one line when Finish placed a waiting question instead of showing the Summary. */
    public static String placedLine(AssistantProfile profile) {
        String what = switch (profile == null ? AssistantProfile.NONE : profile) {
            case SAFE -> "Claude inside " + PRODUCT + " · Safe analysis";
            case FULL -> "Claude inside " + PRODUCT + " · Full Claude Code";
            case IN_APP -> "the in-app assistant";
            default -> "";
        };
        return "Set up: " + what + ". Your question is below — press Send.";
    }

    // ---- the Assistant ▾ quick switch (§14.3, M77-4) -----------------------------------------------------------------
    // Rendered from ProfileState by ui/setup/QuickSwitch; a press is AssistantProfileChosen(origin=MENU), and aiSetup
    // decides — the menu decides nothing (review R1-24).

    /** The quick switch's accessible name. */
    public static final String QUICK_SWITCH_NAME = "How the assistant works";
    /** The glyph Full Claude Code carries everywhere it is in force (the tab title, the badge), and in the menu. */
    public static final String FULL_GLYPH = "◆";
    /**
     * The quick switch's tooltips carry what the four cards' lines carried (review P3+UX S3, holistic B6): what each
     * profile does, and the "not local-only" sentence where something is sent — pinned in FaqSecurityContractTest.
     */
    public static final String QUICK_SAFE_TIP = "Claude Code reads your evidence; it changes what is open or writes a "
            + "file only after you approve each call. No shell — safe with logs you did not write. "
            + telamin.fluxtion.audit.analyser.analyser.assistant.AssistantProfiles.CARD_SENDS + "Anthropic.";
    public static final String QUICK_FULL_TIP = "Your own Claude Code set-up, skills and tools. When Claude Code asks for "
            + "approval, " + PRODUCT + " asks you. You confirm a warning before it is turned on. "
            + telamin.fluxtion.audit.analyser.analyser.assistant.AssistantProfiles.CARD_SENDS + "Anthropic.";
    public static final String QUICK_IN_APP_TIP = productCapitalised() + "'s own assistant, with your API key and model "
            + "(with no key stored, Set up AI asks for one). "
            + telamin.fluxtion.audit.analyser.analyser.assistant.AssistantProfiles.CARD_SENDS + "the provider you chose.";
    /** Review B6: Off is about the assistant IN the product; an external MCP or REST client is another matter. */
    public static final String QUICK_OFF_TIP = "No assistant in " + PRODUCT + ". Everything else still works; external "
            + "MCP and REST clients are not affected.";
    public static final String QUICK_SET_UP_TIP = "Choose how AI works with " + PRODUCT + ", step by step.";
    /** A quick-switch press with no profile (none to choose). */
    public static final String CHOOSE_FIRST = "Choose how AI works first — Set up AI…";

    /** The quick switch's label for a profile: Full carries its glyph, and "…" while choosing it asks first. */
    public static String quickLabel(AssistantProfile p, boolean inForce) {
        AssistantProfile q = p == null ? AssistantProfile.NONE : p;
        if (q == AssistantProfile.FULL) return FULL_GLYPH + " " + q.title() + (inForce ? "" : "…");
        return q.title();
    }

    /** The quick switch's tooltip for a profile. */
    public static String quickTip(AssistantProfile p) {
        return switch (p == null ? AssistantProfile.NONE : p) {
            case SAFE -> QUICK_SAFE_TIP;
            case FULL -> QUICK_FULL_TIP;
            case IN_APP -> QUICK_IN_APP_TIP;
            case OFF -> QUICK_OFF_TIP;
            case NONE -> QUICK_SET_UP_TIP;
        };
    }

    // ---- the node's status lines (§6.0, §6.6, §7.4) -----------------------------------------------------------------

    /** The panel's empty state (§6.6): one line, and the Set up AI… button beside it. */
    public static final String PANEL_NOTHING_SET_UP = "No AI set up yet.";
    /** The chip while nothing is set up (robot #8). */
    public static final String CHIP_NOTHING_SET_UP = "No assistant set up";
    /** The Ask items' cue while nothing is set up (review R1-23). */
    public static final String ASK_CUE = " (set up AI first)";
    public static final String ALREADY_CHECKING = "Already checking.";
    public static final String CANCELLED_ASK = "Set up was cancelled — ask again from the record when you're ready.";
    public static final String CANCELLED_BOX = "Set up was cancelled, so your question was not placed — it is still in "
            + "the box";
    public static final String CANNOT_ANSWER_BOX = "No assistant in the panel can answer this — your question is still "
            + "in the box.";
    public static final String CANNOT_ANSWER_ASK = "No assistant in the panel can answer this — ask it again once one is "
            + "set up.";
    public static final String QUESTION_WAITS = "Your question waits, and is placed for the assistant you set up — "
            + "nothing is sent.";

    /** The start page's AI line (§6.0) — with the readiness: another window may hold the client connection. */
    public static String aiLine(Choice inForce, AssistantProfile profile, boolean claudeFound, boolean inAppCanAnswer,
                                boolean externalOn, boolean otherInstance) {
        if (externalOn && otherInstance) {
            return aiLine(inForce, profile, claudeFound, inAppCanAnswer, false)
                    + " · another " + PRODUCT + " window holds the client connection";
        }
        return aiLine(inForce, profile, claudeFound, inAppCanAnswer, externalOn);
    }

    /** The start page's AI line (§6.0), without its link. */
    public static String aiLine(Choice inForce, AssistantProfile profile, boolean claudeFound, boolean inAppCanAnswer,
                                boolean externalOn) {
        String base = switch (inForce == null ? Choice.NONE : inForce) {
            case NONE -> claudeFound ? "AI: not set up · Claude Code found" : "AI: not set up";
            case NO_AI -> "AI: off";
            case CLIENT -> "AI: your own client";
            case INSIDE, BOTH -> "AI: Claude inside " + PRODUCT + (profile == AssistantProfile.FULL
                    ? " (Full Claude Code)" : " (Safe analysis)");
            case IN_APP -> inAppCanAnswer ? "AI: the in-app assistant (your API key)"
                    : "AI: the in-app assistant (your API key) — no key yet";
        };
        return base + (externalOn ? " · your own client can connect" : "");
    }

    /** The AI line's link: Set up AI… while nothing (or Off) is in force, Change… otherwise. */
    public static String aiLineLink(Choice inForce) {
        return inForce == null || inForce == Choice.NONE || inForce == Choice.NO_AI ? SET_UP_LINK : CHANGE_LINK;
    }
}
