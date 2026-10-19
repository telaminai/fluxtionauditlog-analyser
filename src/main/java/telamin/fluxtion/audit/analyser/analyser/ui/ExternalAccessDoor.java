package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AccessOrigin;

import java.util.function.BiConsumer;

/**
 * M77-3 (spec-ai-setup-wizard.md §9.2, review R1-7/R1-8): how a door that is not the frame — Settings ▸ Assistant on
 * save, the client dialog — asks for External MCP/REST access. It reports the person's ask; {@code actionListener}
 * decides (on: the confirmation first; off: at once). No door writes {@code assistant.rest} itself
 * ({@code OneDispatchModelTest}).
 */
final class ExternalAccessDoor {

    private ExternalAccessDoor() {
    }

    private static volatile BiConsumer<Boolean, AccessOrigin> asker;

    /** The frame installs the one route: a fact posted to the session. */
    static void install(BiConsumer<Boolean, AccessOrigin> route) {
        asker = route;
    }

    /** A door's ask; false when no session can hear it (the door then changes nothing). */
    static boolean request(boolean on, AccessOrigin origin) {
        BiConsumer<Boolean, AccessOrigin> a = asker;
        if (a == null) return false;
        a.accept(on, origin);
        return true;
    }
}
