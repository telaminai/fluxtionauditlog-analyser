package telamin.fluxtion.audit.analyser.analyser.account;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * CA-1: the device credential a compiler account granted this analyser, and the service it belongs to.
 *
 * <p>The bearer value is readable only inside this package ({@link #bearer()} is package-private), so the Swing
 * dialog, {@code context}, profiles, reports and bundles cannot reach it by construction: the only code that sees it
 * is the HTTP client that presents it and the store that keeps it. {@link #toString()} never includes it.
 *
 * <p>The credential carries its OWN service address. Changing the configured address later never sends this bearer
 * to a different host: usage and revoke always go to the service that issued it.
 */
public final class CompilerCredential {

    /**
     * Identifiers are path segments and display text; nothing outside this shape is accepted from the service. The
     * first character is a letter or digit, so an id can never be {@code .} or {@code ..} and turn Revoke's
     * {@code DELETE /v1/credentials/<id>} into a request for a different path.
     */
    static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}");

    private final String service;
    private final String credentialId;
    private final String accountId;
    private final String bearer;

    CompilerCredential(String service, String credentialId, String accountId, String bearer) {
        this.service = ServiceAddress.require(service);
        if (credentialId == null || !IDENTIFIER.matcher(credentialId).matches())
            throw new IllegalArgumentException("the service returned an unusable credential id");
        if (accountId == null || !IDENTIFIER.matcher(accountId).matches())
            throw new IllegalArgumentException("the service returned an unusable account id");
        if (bearer == null || bearer.isBlank() || bearer.chars().anyMatch(c -> c <= ' ' || c > '~'))
            throw new IllegalArgumentException("the service returned an unusable credential");
        this.credentialId = credentialId;
        this.accountId = accountId;
        this.bearer = bearer;
    }

    /** The base address of the service that issued this credential. */
    public String service() {
        return service;
    }

    /** The credential's public identifier (what Revoke names); not a secret. */
    public String credentialId() {
        return credentialId;
    }

    public String accountId() {
        return accountId;
    }

    /** The secret. Package-private on purpose: see the class comment. */
    String bearer() {
        return bearer;
    }

    @Override
    public String toString() {
        return "CompilerCredential[service=" + service + ", credentialId=" + credentialId + ", accountId=" + accountId
                + ", bearer=<redacted>]";
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof CompilerCredential c && service.equals(c.service) && credentialId.equals(c.credentialId)
                && accountId.equals(c.accountId) && bearer.equals(c.bearer);
    }

    @Override
    public int hashCode() {
        return Objects.hash(service, credentialId, accountId);
    }

    /**
     * The stored form: one Base64 line. Base64 because a platform store's own command syntax (the macOS tool's
     * interactive mode tokenises its input) must never have to quote a value; it is encoding, not protection.
     */
    String encode() {
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, String> e : fields().entrySet()) b.append(e.getKey()).append('=').append(e.getValue()).append('\n');
        return Base64.getEncoder().encodeToString(b.toString().getBytes(StandardCharsets.UTF_8));
    }

    private Map<String, String> fields() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("v", "1");
        m.put("service", service);
        m.put("credentialId", credentialId);
        m.put("accountId", accountId);
        m.put("bearer", bearer);
        return m;
    }

    /** The inverse of {@link #encode()}; a damaged or foreign value is an exception whose message names no content. */
    static CompilerCredential decode(String stored) {
        if (stored == null) throw new IllegalArgumentException("no stored credential");
        String text;
        try {
            text = new String(Base64.getDecoder().decode(stored.strip()), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("the stored compiler credential is damaged");
        }
        Map<String, String> m = new LinkedHashMap<>();
        for (String line : text.split("\n")) {
            int eq = line.indexOf('=');
            if (eq > 0) m.put(line.substring(0, eq), line.substring(eq + 1));
        }
        if (!"1".equals(m.get("v"))) throw new IllegalArgumentException("the stored compiler credential is damaged");
        try {
            return new CompilerCredential(m.get("service"), m.get("credentialId"), m.get("accountId"), m.get("bearer"));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("the stored compiler credential is damaged");
        }
    }
}
