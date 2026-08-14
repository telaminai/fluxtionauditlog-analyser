package telamin.fluxtion.audit.analyser.analyser.parse;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The derived grouping/filter key for a record (spec §6). When {@code eventToString} is a Java method
 * signature the dimension is the <b>callback method name</b> (e.g. {@code orderVenueConnected}); the
 * declaring class FQN is also captured (for source resolution). Otherwise the dimension is the raw
 * {@code event} type (e.g. {@code ScheduledTriggerNode}, {@code LifecycleEvent}).
 *
 * @param value         the group/filter key ({@code callback} if present, else {@code event})
 * @param callback      the method name when {@code eventToString} is a signature, else {@code null}
 * @param declaringType FQN of the method's declaring class when a signature, else {@code null}
 */
public record EventDimension(String value, String callback, String declaringType) {

    // public boolean com.a.b.Class.method(arg.Type)   ->  declaringType=com.a.b.Class, method=method
    private static final Pattern METHOD_SIG = Pattern.compile(
            "^(?:public|private|protected)\\s+\\S+\\s+([\\w$.]+)\\.(\\w+)\\((.*)\\)$");

    public static EventDimension derive(String event, String eventToString) {
        if (eventToString != null) {
            Matcher m = METHOD_SIG.matcher(eventToString.strip());
            if (m.matches()) {
                String declaringType = m.group(1);
                String method = m.group(2);
                return new EventDimension(method, method, declaringType);
            }
        }
        return new EventDimension(event == null ? "" : event, null, null);
    }
}
