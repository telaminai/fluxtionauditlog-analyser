package telamin.fluxtion.audit.analyser.analyser.session.node;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.session.LocalAgentPosture;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEffects;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.HostedCallKind;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ToolClass;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Review PR151 F2 (2026-10-18): the single-use grant is bound to the turn's TICKET (OD-74.17) — checked in
 * {@code LocalAgentPermission.consume}. Through the real processor that comparison cannot be reached: the grant is
 * dropped by the same {@code LocalAgentTurnEnded} that closes its ticket, before the next one opens, so
 * {@code LocalAgentPermissionTest#aGrantAdmitsExactlyOneMatchingCall}'s "another ticket" step passes with the
 * comparison deleted (a mutation of {@code consume} survived every test). This test holds the comparison itself: the
 * node, alone, reads a session whose open ticket moved on without the turn-end reaching it.
 *
 * <p>Witness: drop {@code Long.parseLong(g[2]) != ticket} from {@code consume} — the next ticket's call is admitted.
 */
class LocalAgentPermissionTicketTest {

    /** The session as the permission node reads it: one admitted, running generation and an open ticket a test moves. */
    static final class Session extends LocalAgentSession {
        long ticket = 1;

        Session() {
            super(null, new EffectQueue());
        }

        @Override public long currentGeneration() { return 1; }
        @Override public long currentTicket() { return ticket; }
        @Override public boolean processRunning() { return true; }
        @Override public boolean stopping() { return false; }
        @Override public LocalAgentPosture currentPosture() { return LocalAgentPosture.ANALYSER_ASSISTANT; }
        @Override public long admittedGeneration() { return 1; }
        @Override public void permissionNoted(long ticket, String status, String detail) { }
    }

    /** The effects the node asks for, in order. */
    static final class Effects extends EffectQueue {
        final List<SessionEffects> requested = new ArrayList<>();

        @Override public void request(SessionEffects effect) {
            requested.add(effect);
        }

        String verdict(long call) {
            for (SessionEffects e : requested) {
                if (e instanceof SessionEffects.AnswerHostedCallEffect a && a.call() == call) return a.refusal();
            }
            throw new AssertionError("no verdict for call " + call);
        }
    }

    @Test
    @DisplayName("★ a grant is refused to a call made under another ticket, and admits the same call under its own")
    void aGrantIsBoundToItsTicket() {
        Session session = new Session();
        Effects effects = new Effects();
        LocalAgentPermission node = new LocalAgentPermission(session, effects);

        node.onRequested(new SessionEvents.LocalAgentPermissionRequested(1, 1, 101, ToolClass.HOSTED_DESTRUCTIVE,
                "report", "input-digest", "tool-use-digest", true, HostedCallKind.CHANGES));
        node.onChosen(new SessionEvents.LocalAgentPermissionChosen(101, true));
        assertEquals(1, node.state().liveGrants(), "Allow once recorded a grant for ticket 1");

        session.ticket = 2;
        node.onHostedCall(new SessionEvents.HostedCallPresented(1, 1, "report", "input-digest", "tool-use-digest"));
        assertNotNull(effects.verdict(1), "the identical call under ticket 2 is not the call approved under ticket 1");
        assertEquals(1, node.state().liveGrants(), "the refused call consumed nothing");

        session.ticket = 1;
        node.onHostedCall(new SessionEvents.HostedCallPresented(2, 1, "report", "input-digest", "tool-use-digest"));
        assertNull(effects.verdict(2), "control: under its own ticket the grant admits the call");
    }
}
