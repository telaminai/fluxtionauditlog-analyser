package telamin.fluxtion.audit.analyser.analyser.assistant.localagent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/** Holistic review B4 (2026-10-17): a hosted call that stops waiting tells the session so; one answered in time does not. */
class HostedAdmissionTest {

    /** ★ Witness: return TIMEOUT_TEXT without posting HostedCallAbandoned — the session hears nothing. */
    @Test
    @DisplayName("★ a call not answered in time is refused AND reported abandoned; nothing is reported for an answered one")
    void aTimedOutCallIsReported() {
        List<Object> posted = new CopyOnWriteArrayList<>();
        HostedAdmission slow = new HostedAdmission(posted::add, 50);
        assertEquals(HostedAdmission.TIMEOUT_TEXT, slow.present(3, "report", "d", "t"));
        assertEquals(2, posted.size(), String.valueOf(posted));
        var presented = (SessionEvents.HostedCallPresented) posted.get(0);
        assertEquals(new SessionEvents.HostedCallAbandoned(presented.call(), 3), posted.get(1));

        List<Object> answered = new CopyOnWriteArrayList<>();
        HostedAdmission[] h = new HostedAdmission[1];
        h[0] = new HostedAdmission(f -> {
            answered.add(f);
            if (f instanceof SessionEvents.HostedCallPresented p) {
                h[0].answer(new telamin.fluxtion.audit.analyser.analyser.session.SessionEffects.AnswerHostedCallEffect(0, p.call(),
                        null));
            }
        }, 5_000);
        assertNull(h[0].present(3, "report", "d", "t"));
        assertEquals(1, answered.size(), "answered in time: nothing abandoned");
    }
}
