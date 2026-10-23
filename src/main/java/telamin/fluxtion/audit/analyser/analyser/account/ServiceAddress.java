package telamin.fluxtion.audit.analyser.analyser.account;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

/**
 * CA-1: which compiler-service addresses the analyser will send a device code or a bearer to. HTTPS anywhere; plain
 * HTTP only to this machine (a local stand-in or a development service), because a bearer sent in clear to another
 * host is a credential handed to the network. No user info, query or fragment; the address is a base, never a page.
 */
public final class ServiceAddress {

    private static final Set<String> LOOPBACK = Set.of("localhost", "127.0.0.1", "[::1]", "::1");

    private ServiceAddress() {
    }

    /** The normalised base (no trailing slash), or an exception whose message says what to change. */
    public static String require(String raw) {
        String reason = refusal(raw);
        if (reason != null) throw new IllegalArgumentException(reason);
        String s = raw.strip();
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }

    /** Why {@code raw} is not a usable service address, or null when it is. */
    public static String refusal(String raw) {
        if (raw == null || raw.isBlank()) return "Enter the compiler service address first, for example https://compiler.example.com";
        URI uri;
        try {
            uri = URI.create(raw.strip());
        } catch (IllegalArgumentException e) {
            return "The service address is not a valid URL";
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (host.isEmpty()) return "The service address needs a host, for example https://compiler.example.com";
        if (uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null)
            return "The service address must be a plain base address, without a user, query or fragment";
        if (scheme.equals("https")) return null;
        if (scheme.equals("http") && LOOPBACK.contains(host)) return null;
        return "The service address must start with https:// (plain http is accepted only for this machine)";
    }

    /**
     * True when {@code url} is a sign-in page the system browser may be asked to open: https with a host, or plain http
     * only on this machine — the same rule as the service address, because the person signs in on that page and a
     * page served in clear from another host hands their sign-in to the network. A query is allowed (the page with the
     * code filled in carries it there); user info is not.
     */
    public static boolean browsable(String url) {
        if (url == null || url.isBlank()) return false;
        try {
            URI uri = URI.create(url.strip());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            if (host.isEmpty() || uri.getRawUserInfo() != null) return false;
            return scheme.equals("https") || (scheme.equals("http") && LOOPBACK.contains(host));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
