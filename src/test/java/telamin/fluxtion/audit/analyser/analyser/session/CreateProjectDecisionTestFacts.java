package telamin.fluxtion.audit.analyser.analyser.session;

import java.util.List;

/** Test helper: the facts of a create with nothing wrong, for {@code dir} (no offer chosen from). */
final class CreateProjectDecisionTestFacts {

    private CreateProjectDecisionTestFacts() {
    }

    static SessionEvents.CreateFacts facts(String dir) {
        var offer = new SessionEvents.CreateOffer(List.of(new SessionEvents.OfferedRoot(".", dir, false)), List.of(),
                "CAN_CREATE", null, "sha256:demo");
        return new SessionEvents.CreateFacts(dir, true, true, false, null, false, null, null, List.of(), offer, true);
    }
}
