package telamin.fluxtion.audit.analyser.analyser.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Review P2 N-b (2026-10-17): the session — not the performer — decides to say a walk binding dropped on load (rule 9). */
class DroppedBindingsStatusTest {

    static final String TARGET = "/work/other/.analyser/project.fluxtion-settings";

    /** ★ Witness: drop sessionBoundary's onProfileApplied — no status is asked for. */
    @Test
    @DisplayName("★ a ProfileApplied that reports dropped bindings is said by sessionBoundary; one with none says nothing more")
    void theNodeSaysIt() {
        FakeSessionAdapter a = new FakeSessionAdapter().withProfile(TARGET);
        SessionDriver d = new SessionDriver(a);
        long op = d.nextOpId();
        d.submit(new SessionEvents.OpenProjectRequested(op, TARGET, TransitionKind.EXPLICIT_SWITCH, "menu"));
        long before = statuses(a).size();
        d.post(new SessionEvents.ProfileApplied(op, TARGET, "other", List.of("walk 'w': step 2's binding was removed")));
        List<String> said = statuses(a);
        assertEquals(before + 1, said.size());
        assertEquals("walk bindings dropped on load: walk 'w': step 2's binding was removed", said.get(said.size() - 1));
        d.post(new SessionEvents.ProfileApplied(op, TARGET, "other", List.of()));
        assertEquals(before + 1, statuses(a).size(), "nothing dropped, nothing more said");
    }

    static List<String> statuses(FakeSessionAdapter a) {
        return a.performed.stream().filter(e -> e instanceof SessionEffects.ShowStatusEffect)
                .map(e -> ((SessionEffects.ShowStatusEffect) e).text()).toList();
    }
}
