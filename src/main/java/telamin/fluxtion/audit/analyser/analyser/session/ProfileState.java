package telamin.fluxtion.audit.analyser.analyser.session;

/**
 * M74 UX: the assistant profile as {@code assistantProfile} decided it — published in the {@link SessionSnapshot}; the
 * assistant panel's header chip and the profile overlay render THIS and decide nothing. Enums, ids and the node's own
 * words only: never an API key, a model name the person typed, or a path.
 *
 * @param profile the profile in force, as the frame last observed it on this machine
 * @param open    the overlay is open
 * @param why     why it is open: {@link #ASKED} (the header's button) or {@link #FIRST_USE} (the first Start or question
 *                with no profile chosen); "" when closed
 * @param request the Apply request the node last answered (0 for none)
 * @param refusal why that Apply was refused, in the node's words, or ""
 * @param offered the overlay already opened by itself once in this session (it never does again)
 * @param status  owner, 2026-10-18: the ONE line the assistant panel shows when no assistant can answer from it —
 *                nothing chosen, or the in-app assistant with no key — in the node's words; "" otherwise (the panel's
 *                own lines say what the mode in force cannot do)
 * @param confirming review PR151 F4: Full Claude Code's confirmation is showing for Apply {@code request}; nothing is
 *                applied until it is confirmed
 * @param observed the frame has reported the profile at least once, so {@code profile} NONE is a fact, not a default
 * @param questionWaits robot walkthrough #1: a question asked before any assistant was chosen waits for the choice
 */
public record ProfileState(AssistantProfile profile, boolean open, String why, long request, String refusal,
                           boolean offered, String status, boolean confirming, boolean observed, boolean questionWaits) {

    public static final String ASKED = "ASKED";
    public static final String FIRST_USE = "FIRST_USE";

    public static final ProfileState NONE = new ProfileState(AssistantProfile.NONE, false, "", 0, "", false, "", false);

    /** The shape before walkthrough #1: not observed, no question waiting. */
    public ProfileState(AssistantProfile profile, boolean open, String why, long request, String refusal, boolean offered,
                        String status, boolean confirming) {
        this(profile, open, why, request, refusal, offered, status, confirming, false, false);
    }

    public ProfileState {
        profile = profile == null ? AssistantProfile.NONE : profile;
        why = why == null ? "" : why;
        refusal = refusal == null ? "" : refusal;
        status = status == null ? "" : status;
    }

    public ProfileState(AssistantProfile profile, boolean open, String why, long request, String refusal, boolean offered) {
        this(profile, open, why, request, refusal, offered, "", false);
    }

    /** Robot walkthrough #1: the frame reported the profile and none is chosen — a question asked now waits for one. */
    public boolean observedNone() {
        return observed && profile == AssistantProfile.NONE;
    }

    /** No card was chosen and nothing is set up. */
    public boolean unchosen() {
        return profile == AssistantProfile.NONE;
    }
}
