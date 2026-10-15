package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;
import telamin.fluxtion.audit.analyser.analyser.config.ProjectSession;
import telamin.fluxtion.audit.analyser.analyser.config.SettingsShare;
import telamin.fluxtion.audit.analyser.analyser.session.SessionDriver;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEffects;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;
import telamin.fluxtion.audit.analyser.analyser.session.TransitionKind;

import java.nio.file.Path;

/**
 * M73 — a headless stand-in for {@code MainFrame}'s effect performer, for the create effects only: the SAME calls the
 * frame's {@code CreateProfileEffect} arm makes ({@code project.createNew} with {@code CreateProjectObservation.plan},
 * then the guide), against a real {@link ProjectSession} and the real generated processor. Everything else is
 * answered plainly. The frame's arm is held to these calls by {@code OneDispatchModelTest}.
 */
final class CreateHarness implements SessionDriver.Adapter {

    final AppConfig config;
    final ProjectSession project;
    final SessionDriver driver;
    String warning;
    String status;
    String openExisting;
    ProjectSession.Created created;
    String guide;

    CreateHarness(AppConfig config) {
        this.config = config;
        this.project = new ProjectSession(config, new SettingsShare(), () -> { });
        this.driver = new SessionDriver(this);
    }

    /** Submit a create as the menu or the socket would, and say whether it was performed. */
    boolean create(Path dir, SessionEvents.CreateRequest request, boolean person) {
        warning = null;
        created = null;
        driver.submit(new SessionEvents.OpenProjectRequested(driver.nextOpId(),
                telamin.fluxtion.audit.analyser.analyser.config.ProjectProfile.pathFor(dir).toString(),
                TransitionKind.CREATE, person ? "menu" : "socket", null,
                new SessionEvents.ProjectAudience(person ? SessionEvents.OperationOrigin.PERSON
                        : SessionEvents.OperationOrigin.MACHINE, person), request));
        return created != null && warning == null;
    }

    @Override
    public SessionEvents.Result perform(SessionEffects effect) throws Exception {
        long opId = effect.opId();
        if (effect instanceof SessionEffects.CreateProfileEffect e) {
            created = project.createNew(Path.of(e.profilePath()), CreateProjectObservation.plan(e.approved()));
            guide = e.approved() != null && e.approved().referenceGuide()
                    ? NewProjectDiscovery.writeReferenceGuide(Path.of(e.approved().root()), e.approved().projectKind())
                    : "NOT_REQUESTED";
            return new SessionEvents.ProfileLoaded(opId, e.profilePath(), true, project.activeName(), 0, null);
        }
        if (effect instanceof SessionEffects.LoadProfileEffect e) {
            var r = project.open(Path.of(e.profilePath()));
            return new SessionEvents.ProfileLoaded(opId, e.profilePath(), r.loaded(),
                    r.loaded() ? project.activeName() : null, 0, r.message());
        }
        if (effect instanceof SessionEffects.ApplyProfileEffect e) {
            return new SessionEvents.ProfileApplied(opId, e.profilePath(), e.name());
        }
        if (effect instanceof SessionEffects.ShowWarningEffect e) {
            warning = e.text();
            return new SessionEvents.StatusShown(opId, "showWarning");
        }
        if (effect instanceof SessionEffects.ShowStatusEffect e) {
            status = e.text();
            return new SessionEvents.StatusShown(opId, "showStatus");
        }
        if (effect instanceof SessionEffects.OfferOpenExistingEffect e) {
            openExisting = e.profilePath();
            return new SessionEvents.StatusShown(opId, "offerOpenExisting");
        }
        if (effect instanceof SessionEffects.CloseLogEffect) return new SessionEvents.LogClosed(opId);
        if (effect instanceof SessionEffects.CloseGraphEffect) return new SessionEvents.GraphClosed(opId);
        return new SessionEvents.StatusShown(opId, SessionDriver.name(effect));
    }
}
