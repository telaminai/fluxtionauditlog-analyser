package telamin.fluxtion.audit.analyser.analyser.llm;

import java.util.ArrayList;
import java.util.List;

/**
 * Extracts assistant actions from a chat reply (spec-assistant-actions §5.1.2): the bodies of fenced code
 * blocks tagged <b>exactly</b> {@code analyser-action}. Any other fence — prose, a normal ```json block,
 * or an illustrative ```analyser-action-example — is ignored, so a model can show an example without the
 * executor running it.
 */
public final class ActionParser {

    private static final String TAG = "analyser-action";

    private ActionParser() {
    }

    /** The JSON bodies of every exact-tag {@code analyser-action} block, in order (possibly empty). */
    public static List<String> extract(String reply) {
        List<String> out = new ArrayList<>();
        if (reply == null) return out;
        String[] lines = reply.split("\n", -1);
        int i = 0;
        while (i < lines.length) {
            String t = lines[i].trim();
            if (t.startsWith("```")) {
                String info = t.substring(3).trim();     // the fence info string (e.g. "analyser-action")
                i++;
                StringBuilder body = new StringBuilder();
                while (i < lines.length && !lines[i].trim().startsWith("```")) {
                    body.append(lines[i]).append('\n');
                    i++;
                }
                // NB: an unclosed fence at end-of-reply (truncated response) still yields its partial
                // body; that's intentional — the dispatcher turns the truncated JSON into a structured
                // ok:false that feeds back, which beats silently dropping it. Do not "fix" into silence.
                if (i < lines.length) i++;               // consume the closing fence
                if (info.equals(TAG)) {                  // exact tag only — never "-example" or "json"
                    String json = body.toString().trim();
                    if (!json.isEmpty()) out.add(json);
                }
            } else {
                i++;
            }
        }
        return out;
    }
}
