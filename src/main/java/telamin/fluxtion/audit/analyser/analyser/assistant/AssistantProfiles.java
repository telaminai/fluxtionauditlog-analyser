package telamin.fluxtion.audit.analyser.analyser.assistant;

import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;
import telamin.fluxtion.audit.analyser.analyser.session.AssistantProfile;
import telamin.fluxtion.audit.analyser.analyser.session.LocalAgentPosture;

/**
 * M74 UX (owner, 2026-10-17): what each assistant profile WRITES — pure, headless, and the only place that maps a card
 * to settings. The overlay's Apply performer calls {@link #apply}; it writes exactly the machine-tier fields
 * {@code Settings ▸ Assistant} (and its LLM page) holds, so the consent the frame then reports, and the command line the
 * adapter then builds, are the ones those settings have always produced. Nothing here decides whether a profile may be
 * applied now — that is {@code assistantProfile}'s.
 *
 * <p>{@link #inForce} is the frame's OBSERVATION of the profile on this machine, reported as a fact: the stored choice
 * when it still matches the switches, otherwise what the switches amount to — so a person who set Claude Code up in
 * Settings before profiles existed is never asked to choose again.
 */
public final class AssistantProfiles {

    private AssistantProfiles() {
    }

    /**
     * The overlay's details for the chosen card: only the ones that card shows are read.
     *
     * @param fullMode    Full Claude Code's mode ({@code default}, {@code acceptEdits}, {@code auto}, {@code plan})
     * @param claudeModel Claude Code's model (Safe analysis and Full), blank to keep the current one
     * @param provider    the in-app provider ({@code anthropic}, {@code openai})
     * @param model       the in-app model, blank for the provider's default
     * @param apiKey      the in-app API key, blank to keep the stored one
     */
    public record Details(String fullMode, String claudeModel, String provider, String model, String apiKey) {
        public static final Details NONE = new Details(null, null, null, null, null);
    }

    /**
     * Write {@code profile} into {@code c}: the Claude Code switches, the full posture's mode and Claude Code's model, the
     * in-app provider, model and key, the remembered mode, and the stored profile. Machine tier, every one of them.
     */
    public static void apply(AssistantProfile profile, Details d, AppConfig c) {
        Details x = d == null ? Details.NONE : d;
        switch (profile) {
            case SAFE -> {
                c.claudeCodeAllowed = true;
                c.claudeCodeFull = false;
                claudeModel(x, c);
            }
            case FULL -> {
                c.claudeCodeAllowed = true;
                c.claudeCodeFull = true;
                c.claudeCodeFullMode = LocalAgentPosture.FullMode.parse(x.fullMode())
                        .orElse(LocalAgentPosture.FullMode.DEFAULT).cli();
                c.claudeCodeFullModeReadAsDefault = false;
                claudeModel(x, c);
            }
            case IN_APP -> {
                c.claudeCodeAllowed = false;
                c.claudeCodeFull = false;
                if (x.provider() != null && !x.provider().isBlank()) c.llmProvider = x.provider().trim();
                if (x.model() != null) c.llmModel = x.model().trim();
                if (x.apiKey() != null && !x.apiKey().isBlank()) c.apiKey = x.apiKey().trim();
            }
            case OFF -> {
                c.claudeCodeAllowed = false;
                c.claudeCodeFull = false;
            }
            case NONE -> throw new IllegalArgumentException("a profile is one of the four cards");
        }
        c.claudeCodeLastMode = profile.mode() == telamin.fluxtion.audit.analyser.analyser.session.AssistantMode.LOCAL_AGENT
                ? "LOCAL_AGENT" : "IN_APP";
        c.assistantProfile = profile.stored();
    }

    private static void claudeModel(Details x, AppConfig c) {
        String m = x.claudeModel() == null ? "" : x.claudeModel().trim();
        if (!m.isEmpty() && telamin.fluxtion.audit.analyser.analyser.assistant.localagent.ClaudeCodeProvider.validModel(m)) {
            c.claudeCodeModel = m;
        }
    }

    /**
     * Whether the in-app assistant has what it needs to answer: a key. (M74 UX: the start page also counted an
     * {@code ollama} provider as able to answer with no key, but no such provider exists — {@code LlmClient} refuses an
     * unknown provider by name since P2 — so the page promised an answer the assistant then refused.)
     */
    public static boolean inAppReady(AppConfig c) {
        return c.apiKey != null && !c.apiKey.isBlank();
    }

    /**
     * The profile in force, as observed: the stored card while the switches still say the same thing; otherwise what
     * the switches amount to (Claude Code on → Safe analysis or Full by the full switch; in-app ready → In-app); Off
     * stays Off while nothing is switched on; {@link AssistantProfile#NONE} when nothing is set up and nothing chosen.
     */
    public static AssistantProfile inForce(AppConfig c) {
        AssistantProfile stored = AssistantProfile.parse(c.assistantProfile);
        AssistantProfile switches = claudeCodeSwitches(c);
        if (stored == AssistantProfile.OFF) return switches != null ? switches : AssistantProfile.OFF;
        if (stored == AssistantProfile.IN_APP && inAppReady(c) && !"LOCAL_AGENT".equals(c.claudeCodeLastMode)) {
            return AssistantProfile.IN_APP;
        }
        if (switches != null && ("LOCAL_AGENT".equals(c.claudeCodeLastMode) || !inAppReady(c))) return switches;
        if (inAppReady(c)) return AssistantProfile.IN_APP;
        if (switches != null) return switches;
        return stored == AssistantProfile.SAFE || stored == AssistantProfile.FULL ? AssistantProfile.OFF : stored;
    }

    /** The one mapping from the Claude Code switches to a card: on → Safe analysis or Full by the full switch; off → null. */
    static AssistantProfile claudeCodeSwitches(AppConfig c) {
        return c.claudeCodeAllowed ? (c.claudeCodeFull ? AssistantProfile.FULL : AssistantProfile.SAFE) : null;
    }

    /**
     * Review P3 N1 (2026-10-17): the card a Settings change amounts to, stored — the ONE mapping Settings ▸ Assistant and
     * {@link #inForce} share: Claude Code on → Safe or Full; else a key → In-app; else Off once a card was ever chosen, and
     * not chosen ({@code null}) otherwise.
     */
    public static String storedFromSwitches(AppConfig c) {
        AssistantProfile switches = claudeCodeSwitches(c);
        if (switches != null) return switches.stored();
        if (inAppReady(c)) return AssistantProfile.IN_APP.stored();
        return AssistantProfile.parse(c.assistantProfile) == AssistantProfile.NONE ? null : AssistantProfile.OFF.stored();
    }

    /** Off: the in-app assistant does not answer even with a key stored (the key is kept; nothing is sent). */
    public static boolean off(AppConfig c) {
        return inForce(c) == AssistantProfile.OFF;
    }

    /** The header chip's words for the mode in force under {@code profile}: "Claude Code · your plan", "In-app · API key". */
    public static String modeChip(AssistantProfile profile) {
        return switch (profile) {
            case SAFE -> "Claude Code · your plan";
            case FULL -> "Full Claude Code · your plan";
            case IN_APP -> "In-app · API key";
            case OFF -> "Off · no AI";
            case NONE -> "Choose how the assistant works";
        };
    }
}
