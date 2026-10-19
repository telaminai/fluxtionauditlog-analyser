import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;

/** Offline fixture producer: use the current Java records, never an invented permission-field schema. */
public class RecordPermissionFacts {
    public static void main(String[] args) {
        boolean[] allows = {true, false, true, true};
        boolean[] written = {true, true, true, false};
        for (int i = 0; i < allows.length; i++) {
            long request = i + 9;
            var by = i == 2 ? SessionEvents.PermissionBy.PRE_ALLOWED : SessionEvents.PermissionBy.PERSON;
            emit(new SessionEvents.LocalAgentPermissionRequested(1, 2, request,
                    SessionEvents.ToolClass.BUILT_IN, "Read", "DEMO-input", null, true));
            emit(new SessionEvents.LocalAgentPermissionAnswered(i + 1, 1, 2, request,
                    SessionEvents.ToolClass.BUILT_IN, "Read", allows[i], by, written[i]));
        }
    }

    private static void emit(Object fact) {
        System.out.println("---\neventLogRecord:\n    event: " + fact.getClass().getSimpleName()
                + "\n    eventToString: " + fact + "\n    nodeLogs:\n---");
    }
}
