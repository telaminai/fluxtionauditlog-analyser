package telamin.fluxtion.audit.analyser.analyser.session;

import telamin.fluxtion.audit.analyser.analyser.session.AiSetupState.Choice;
import telamin.fluxtion.audit.analyser.analyser.session.AiSetupState.Client;
import telamin.fluxtion.audit.analyser.analyser.session.AiSetupState.Posture;

/**
 * M77 (spec-ai-setup-wizard.md §14.1, §7.2, §8.5): the pure mappings {@code aiSetup} calls — the choice in force DERIVED
 * from the keys M74 and M42 already use, the profile a Screen 1 choice writes, and the assistant mode a session starts
 * in. They decide nothing about WHEN; the node does.
 */
public final class AiSetupDerived {

    private AiSetupDerived() {
    }

    /**
     * §14.1: the choice in force from the profile in force and {@code assistant.setup.client}. External access alone is
     * NOT a client choice (review R1-5): {@code --rest} and the capture harness leave it on with nobody having chosen.
     */
    public static Choice choice(AssistantProfile profile, Client client) {
        boolean withClient = client != null && client != Client.NONE;
        AssistantProfile p = profile == null ? AssistantProfile.NONE : profile;
        return switch (p) {
            case NONE -> withClient ? Choice.CLIENT : Choice.NONE;
            case OFF -> withClient ? Choice.CLIENT : Choice.NO_AI;
            case SAFE, FULL -> withClient ? Choice.BOTH : Choice.INSIDE;
            case IN_APP -> Choice.IN_APP;
        };
    }

    /**
     * §7.2: the profile a choice applies. Claude inside (and Both) is Safe analysis or Full by the sub-choice; No AI and
     * your own client only turn the panel off; Other options is the in-app assistant. {@link Choice#NONE} applies nothing.
     */
    public static AssistantProfile profileFor(Choice choice, Posture posture) {
        if (choice == null) return AssistantProfile.NONE;
        return switch (choice) {
            case NONE -> AssistantProfile.NONE;
            case NO_AI, CLIENT -> AssistantProfile.OFF;
            case INSIDE, BOTH -> posture == Posture.FULL ? AssistantProfile.FULL : AssistantProfile.SAFE;
            case IN_APP -> AssistantProfile.IN_APP;
        };
    }

    /** The posture Screen 2a starts from: the profile in force when it is Full, otherwise Safe analysis. */
    public static Posture postureOf(AssistantProfile profile) {
        return profile == AssistantProfile.FULL ? Posture.FULL : Posture.SAFE;
    }

    /**
     * §8.5 (review R1-3, ★ T21): the assistant mode a new session starts in is derived from the profile in force, for
     * EVERY profile — not only when the remembered mode says Claude Code — so the remembered mode can never contradict
     * the profile, and an upgrader whose stored key reads as In-app keeps answering.
     */
    public static AssistantMode startupMode(AssistantProfile inForce) {
        return (inForce == null ? AssistantProfile.NONE : inForce).mode();
    }
}
