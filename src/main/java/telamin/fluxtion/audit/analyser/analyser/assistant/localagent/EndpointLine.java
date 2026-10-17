package telamin.fluxtion.audit.analyser.analyser.assistant.localagent;

import java.net.URI;
import java.util.Locale;

/**
 * M74 P2b (§5.2 the endpoint line, [R S6]): what the analyser can say about where Claude Code's requests go — only the
 * {@code scheme://host[:port]} of the {@code ANTHROPIC_BASE_URL} it passed to the child. Never a path, a query, a
 * fragment or user information (a proxy URL often carries a key in one of them). PURE.
 */
public final class EndpointLine {

    private EndpointLine() {
    }

    /** {@code https://proxy.example:8443} for {@code https://u:p@proxy.example:8443/v1?k=x}; null when unset or unreadable. */
    public static String of(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) return null;
        URI u;
        try {
            u = URI.create(baseUrl.strip());
        } catch (IllegalArgumentException e) {
            return "an address the analyser could not read";
        }
        String scheme = u.getScheme();
        String host = u.getHost();
        if (scheme == null || host == null) return "an address the analyser could not read";
        return scheme.toLowerCase(Locale.ROOT) + "://" + host.toLowerCase(Locale.ROOT) + (u.getPort() == -1 ? "" : ":" + u.getPort());
    }
}
