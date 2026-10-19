package telamin.fluxtion.audit.analyser.analyser.llm;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The outcome of one assistant action (spec-assistant-actions §4). Query verbs carry a {@code result};
 * render verbs echo what was {@code applied}; failures carry an {@code error} — and per review #3 an
 * error is fed back to the model exactly like a result so it can self-correct.
 */
public record ActionResult(boolean ok, String action, String payloadKey, Map<String, Object> payload, String error,
                           ReportReceipt reportReceipt) {

    /** Internal transport evidence, deliberately absent from toMap/toJson and the external API. */
    public record ReportReceipt(long generation, String ownerKey, String revision) { }

    public ActionResult(boolean ok, String action, String payloadKey, Map<String, Object> payload, String error) {
        this(ok, action, payloadKey, payload, error, null);
    }

    public ActionResult withReportReceipt(ReportReceipt receipt) {
        return new ActionResult(ok, action, payloadKey, payload, error, receipt);
    }

    public static ActionResult ok(String action, String key, Map<String, Object> payload) {
        return new ActionResult(true, action, key, payload, null);
    }

    public static ActionResult error(String message) {
        return new ActionResult(false, null, null, null, message);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", ok);
        if (ok) {
            m.put("action", action);
            m.put(payloadKey, payload);
        } else {
            m.put("error", error);
        }
        return m;
    }

    public String toJson() {
        return Json.write(toMap());
    }
}
