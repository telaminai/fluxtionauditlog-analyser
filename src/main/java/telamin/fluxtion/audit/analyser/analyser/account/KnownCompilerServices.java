package telamin.fluxtion.audit.analyser.analyser.account;

import java.util.List;

/**
 * The hosted compiler services the analyser offers by name in *AI ▸ Compiler account…*, so a first-time user is not
 * left with an empty drop-down and a placeholder. Production first, then staging (owner, 2026-11-03).
 *
 * <p>Both are the public, Cloudflare-proxied addresses from the compiler's own operations runbook
 * ({@code fluxtion-compiler/docs/operations/OPERATIONS.md}). Never the direct Railway origins: those refuse
 * {@code /auth} and {@code /v1} without the edge secret. Offering an address chooses nothing: the field stays empty
 * until the person picks or types one.
 */
public final class KnownCompilerServices {

    /** One named service: what the drop-down calls it, and the base address it fills the field with. */
    public record Known(String name, String address) { }

    public static final String PRODUCTION = "https://compiler.fluxtion-playground.dev";
    public static final String STAGING = "https://compiler-staging.fluxtion-playground.dev";

    /** In the order the drop-down offers them. */
    public static final List<Known> ALL = List.of(new Known("Production", PRODUCTION), new Known("Staging", STAGING));

    private KnownCompilerServices() {
    }

    /** The name of a known service at {@code address} (trailing slashes ignored), or null for any other address. */
    public static String name(String address) {
        if (address == null) return null;
        String a = address.strip();
        while (a.endsWith("/")) a = a.substring(0, a.length() - 1);
        for (Known k : ALL) if (k.address().equalsIgnoreCase(a)) return k.name();
        return null;
    }

    /** How the drop-down shows {@code address}: "Production — https://…" for a known service, the address otherwise. */
    public static String describe(String address) {
        String n = name(address);
        return n == null ? address : n + " — " + address;
    }
}
