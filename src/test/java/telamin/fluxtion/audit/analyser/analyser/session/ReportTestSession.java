package telamin.fluxtion.audit.analyser.analyser.session;

/** Real generated session, with a simulated CLI and real frame report/project effects. No process or provider. */
public final class ReportTestSession {
    private ReportTestSession() { }

    public static SessionDriver create(SessionDriver.Adapter frame) {
        return create(frame, true);
    }

    public static SessionDriver create(SessionDriver.Adapter frame, boolean full) {
        FakeSessionAdapter fake = new FakeSessionAdapter();
        SessionDriver driver = new SessionDriver(effect -> switch (effect) {
            case SessionEffects.StartLocalAgentEffect e -> fake.perform(e);
            case SessionEffects.PollLocalAgentStatusEffect e -> fake.perform(e);
            case SessionEffects.SendLocalAgentTurnEffect e -> fake.perform(e);
            case SessionEffects.StopLocalAgentEffect e -> fake.perform(e);
            case SessionEffects.ServeHostedBridgeEffect e -> fake.perform(e);
            case SessionEffects.ReleaseHostedBridgeEffect e -> fake.perform(e);
            case SessionEffects.ConfigureActionListenerEffect e -> fake.perform(e);
            case SessionEffects.ChooseAssistantModeEffect e -> fake.perform(e);
            case SessionEffects.OfferLocalAgentRestartEffect e -> fake.perform(e);
            case SessionEffects.RecomposeLocalAgentDraftsEffect e -> fake.perform(e);
            default -> frame.perform(effect);
        });
        driver.post(new SessionEvents.AssistantModeChosen(AssistantMode.LOCAL_AGENT, "test"));
        if (!full) {
            LocalAgentPermissionTest.ready(driver, fake);
            return driver;
        }
        var mode = LocalAgentPosture.FullMode.DEFAULT;
        var start = LocalAgentFullPostureTest.startFull(driver, fake, mode);
        var turn = LocalAgentSessionTest.send(driver, fake, 10, LocalAgentFullPostureTest.full(mode));
        driver.post(LocalAgentFullPostureTest.fullInit(start, turn.ticket(), "default", java.util.List.of(), "connected"));
        org.junit.jupiter.api.Assertions.assertEquals(start.generation(), driver.snapshot().localAgent().admittedGeneration());
        return driver;
    }
}
