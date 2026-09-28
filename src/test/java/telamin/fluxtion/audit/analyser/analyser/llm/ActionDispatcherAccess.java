package telamin.fluxtion.audit.analyser.analyser.llm;

import java.util.Map;

/** Test access to the dispatcher's package-private read-identity policy. */
public final class ActionDispatcherAccess {
    private ActionDispatcherAccess() {
    }

    public static boolean readsRecords(String action, Map<String, Object> params) {
        return ActionDispatcher.readsRecords(action, params);
    }
}
