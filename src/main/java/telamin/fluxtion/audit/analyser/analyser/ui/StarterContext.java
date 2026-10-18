package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.session.LocalAgentPosture;
import telamin.fluxtion.audit.analyser.analyser.session.StarterState;
import telamin.fluxtion.audit.analyser.analyser.template.Starters;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Option 2: {@code context.starters} — RENDERED from {@code starterProject}'s state and the frame's copy of what was read;
 * it fetches nothing. {@code {state: read|not-read|unreachable, entries: [{id, name, description, authoring, host,
 * recommended, keyNeed}]}}, Spring first. Before anything asked for the catalogue the state is {@code not-read} and there
 * are no entries: reading it is the menu's, a hosted session's start or the start page's, never a {@code context} call's.
 */
final class StarterContext {

    private StarterContext() {
    }

    static Map<String, Object> render(StarterState s, List<Starters.Entry> read) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("state", s == null ? StarterState.NOT_READ : s.catalogue());
        List<Map<String, Object>> entries = new ArrayList<>();
        if (s != null && read != null && !StarterState.NOT_READ.equals(s.catalogue())) {
            for (StarterState.Entry e : s.entries()) {
                Starters.Entry r = byDigest(read, e.idDigest());
                if (r == null) continue;
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", r.id());
                m.put("name", r.name());
                m.put("description", r.description());
                m.put("authoring", r.authoring());
                m.put("host", r.host());
                m.put("recommended", e.recommended());
                m.put("keyNeed", r.keyNeed());
                entries.add(m);
            }
        }
        out.put("entries", entries);
        return out;
    }

    static Starters.Entry byDigest(List<Starters.Entry> read, String digest) {
        if (read == null || digest == null) return null;
        for (Starters.Entry r : read) if (digest.equals(LocalAgentPosture.digest(r.id()))) return r;
        return null;
    }
}
