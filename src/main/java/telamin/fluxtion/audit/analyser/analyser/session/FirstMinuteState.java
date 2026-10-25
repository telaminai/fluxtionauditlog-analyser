package telamin.fluxtion.audit.analyser.analyser.session;

import java.util.List;

/** The first-contact surface and recovery qualification, decided by firstMinute, not by frame timing. */
public record FirstMinuteState(String phase, String domain, List<String> doors, String callout, String reason,
                               boolean bundled) {
    public static final FirstMinuteState NONE = new FirstMinuteState("NONE", "generic", List.of(), "", "", false);
    public FirstMinuteState { doors = List.copyOf(doors); }
}
