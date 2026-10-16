package telamin.fluxtion.audit.analyser.analyser.net;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongFunction;

/**
 * M74 r6 §4.2: the hosted bridge's own credential. One token is minted for every spawn of the local agent, held for
 * that generation only, written to nowhere but the per-instance endpoint file (mode 600), and revoked when the
 * generation stops, exits or is replaced. The hosted bridge sends it as {@value ActionServer#HOSTED_HEADER};
 * {@link ActionServer} maps it to {@code {hosted, generation}}.
 *
 * <p><b>The shared REST token is never hosted.</b> Nothing here derives from it, and a request carrying only
 * {@code X-Analyser-Token} is an external client however it presents itself.
 */
public final class HostedCredentials {

    /** Decides nothing: maps a presented credential to its generation and asks the session whether that one is admitted. */
    public interface Gate {
        /** The generation {@code credential} was minted for while it is live, or -1. */
        long generationOf(String credential);

        /** Why calls of {@code generation} are refused now, or null when the session admits them. */
        String refusal(long generation);
    }

    private final SecureRandom random = new SecureRandom();
    private final Map<String, Long> live = new ConcurrentHashMap<>();

    /** A fresh credential for {@code generation}; any earlier one of the same generation is revoked first. */
    public String mint(long generation) {
        revoke(generation);
        byte[] b = new byte[32];
        random.nextBytes(b);
        String token = HexFormat.of().formatHex(b);
        live.put(token, generation);
        return token;
    }

    /** Revoke every credential of {@code generation}. */
    public void revoke(long generation) {
        live.values().removeIf(g -> g == generation);
    }

    public long generationOf(String credential) {
        if (credential == null || credential.isBlank()) return -1;
        Long g = live.get(credential);
        return g == null ? -1 : g;
    }

    /** This store as a gate, with the session's admission decision supplied by the caller (it reads the snapshot). */
    public Gate gate(LongFunction<String> refusal) {
        return new Gate() {
            @Override public long generationOf(String credential) {
                return HostedCredentials.this.generationOf(credential);
            }

            @Override public String refusal(long generation) {
                return refusal.apply(generation);
            }
        };
    }
}
