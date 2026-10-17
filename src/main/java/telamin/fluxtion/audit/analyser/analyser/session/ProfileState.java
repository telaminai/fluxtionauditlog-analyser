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
 */
public record ProfileState(AssistantProfile profile, boolean open, String why, long request, String refusal,
                           boolean offered) {

    public static final String ASKED = "ASKED";
    public static final String FIRST_USE = "FIRST_USE";

    public static final ProfileState NONE = new ProfileState(AssistantProfile.NONE, false, "", 0, "", false);

    public ProfileState {
        profile = profile == null ? AssistantProfile.NONE : profile;
        why = why == null ? "" : why;
        refusal = refusal == null ? "" : refusal;
    }

    /** No card was chosen and nothing is set up. */
    public boolean unchosen() {
        return profile == AssistantProfile.NONE;
    }
}
