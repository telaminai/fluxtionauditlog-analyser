package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;
import telamin.fluxtion.audit.analyser.analyser.config.GraphSpec;
import telamin.fluxtion.audit.analyser.analyser.config.ProjectProfile;
import telamin.fluxtion.audit.analyser.analyser.config.SettingsShare;
import telamin.fluxtion.audit.analyser.analyser.config.WalkBin;
import telamin.fluxtion.audit.analyser.analyser.walk.WalkSpec;

import java.nio.file.Path;

/**
 * M74 P2 (spec §5.8, r8 F13): the start page's S1 — the DEMO journey "Find the first recorded breach", shipped in the jar
 * with the demo set under its OWN name, so a returning user whose sample profile already holds the four-step
 * {@link DemoTour} still gets it. Its conversation is SCRIPTED and labelled <i>Simulated conversation</i>; it is never
 * presented as recorded or live. Played through the ordinary walk path: no model, no key, no network, nothing sent.
 */
final class DemoJourney {

    /** The walk's name — never {@link DemoTour#NAME}, which a returning user's profile already holds. */
    static final String NAME = "DEMO first breach";

    private DemoJourney() {
    }

    /** The journey's walk and its chart, read from the unpacked demo set (never executed). */
    record Parts(WalkSpec walk, GraphSpec chart) {
    }

    static Parts load(Path profileFragment) {
        AppConfig c = new AppConfig();
        if (!ProjectProfile.load(profileFragment, c, new SettingsShare("")).loaded()) return null;
        WalkSpec walk = WalkBin.find(c.walks, NAME);
        GraphSpec chart = walk == null || walk.steps().isEmpty() ? null : c.savedGraphs.stream()
                .filter(g -> walk.steps().stream().anyMatch(s -> s.view() != null && g.name().equals(s.view().graph())))
                .findFirst().orElse(null);
        return walk == null ? null : new Parts(walk, chart);
    }
}
