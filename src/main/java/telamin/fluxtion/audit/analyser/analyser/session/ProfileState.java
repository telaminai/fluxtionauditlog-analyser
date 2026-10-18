package telamin.fluxtion.audit.analyser.analyser.session;

/**
 * M74 UX: the assistant profile as {@code aiSetup} decided it — published in the {@link SessionSnapshot}; the assistant
 * panel's header control and the Assistant ▾ quick switch (M77-4) render THIS and decide nothing. Enums and the node's
 * own words only: never an API key, a model name the person typed, or a path.
 *
 * <p>M77-4 (OD-77.3): the profile cards are gone, and with them whether they were open, why, and the refusal they
 * showed. A refused quick-switch press is the node's ONE line ({@link AiSetupState#line()}), as the wizard's is.
 *
 * @param profile       the profile in force, as the frame last observed it on this machine
 * @param status        owner, 2026-10-18: the ONE line the assistant panel shows when no assistant can answer from it —
 *                      nothing set up, or the in-app assistant with no key — in the node's words; "" otherwise
 * @param confirming    review PR151 F4: Full Claude Code's confirmation is showing for a quick-switch press; nothing is
 *                      applied until it is confirmed
 * @param observed      the frame has reported the profile at least once, so {@code profile} NONE is a fact, not a default
 * @param questionWaits robot walkthrough #1: a question asked before any assistant was chosen waits for the choice
 */
public record ProfileState(AssistantProfile profile, String status, boolean confirming, boolean observed,
                           boolean questionWaits) {

    public static final ProfileState NONE = new ProfileState(AssistantProfile.NONE, "", false, false, false);

    public ProfileState {
        profile = profile == null ? AssistantProfile.NONE : profile;
        status = status == null ? "" : status;
    }

    /** A profile as observed, with nothing else to say (the tests' shorthand). */
    public static ProfileState of(AssistantProfile profile) {
        return new ProfileState(profile, "", false, true, false);
    }

    /** Robot walkthrough #1: the frame reported the profile and none is chosen — a question asked now waits for one. */
    public boolean observedNone() {
        return observed && profile == AssistantProfile.NONE;
    }

    /**
     * Robot re-walk #4 (2026-10-18): the in-app assistant is in force and cannot answer — it has no API key — so the
     * panel's ONE guide line is the node's {@link #status} with Configure provider beside it, and Send cannot be pressed.
     * The node publishes a status for the in-app profile only in that case.
     */
    public boolean inAppNeedsKey() {
        return profile == AssistantProfile.IN_APP && !status.isBlank();
    }

    /** Nothing is chosen and nothing is set up. */
    public boolean unchosen() {
        return profile == AssistantProfile.NONE;
    }
}
