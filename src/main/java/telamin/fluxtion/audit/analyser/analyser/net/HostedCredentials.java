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

        /**
         * Why a call of {@code generation} is refused now, or null when the session admits it. Review M6: an
         * implementation asks the SESSION, on its thread and in its order (a fact, then the node's verdict as an
         * effect, awaited with a timeout); it never decides from a published snapshot on the HTTP thread.
         */
        String admit(long generation);

        /** Scorecard R1: as {@link #admit(long)}, naming the call's verb (the hosted vocabulary's, or null). */
        default String admit(long generation, String verb) {
            return admit(generation);
        }

        /**
         * M74 P3 (OD-74.17): a hosted DESTRUCTIVE call — admitted only by consuming the person's single-use grant for this
         * exact call, which the session decides on its thread. Null admits; the default refuses (no session can grant).
         */
        default String present(long generation, String verb, String inputDigest, String toolUseIdDigest) {
            return "`" + verb + "` needs your approval in the analyser for this exact call; none was given";
        }

        /** Scorecard R1: the server refused a hosted call itself (a code: GRAPH_DELETE, NOT_LISTED, BAD_CREDENTIAL). */
        default void refused(long generation, String verb, String reason) {
        }
    }

    /** What the session answers for the hosted bridge — implemented by the session's correlator (HostedAdmission). */
    public interface Decider {
        String admit(long generation, String verb);

        String present(long generation, String verb, String inputDigest, String toolUseIdDigest);

        void refused(long generation, String verb, String reason);
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

    /** This store as a gate whose every decision is the session's ({@link Decider}). */
    public Gate gate(Decider decider) {
        return new Gate() {
            @Override public long generationOf(String credential) {
                return HostedCredentials.this.generationOf(credential);
            }

            @Override public String admit(long generation) {
                return decider.admit(generation, null);
            }

            @Override public String admit(long generation, String verb) {
                return decider.admit(generation, verb);
            }

            @Override public String present(long generation, String verb, String inputDigest, String toolUseIdDigest) {
                return decider.present(generation, verb, inputDigest, toolUseIdDigest);
            }

            @Override public void refused(long generation, String verb, String reason) {
                decider.refused(generation, verb, reason);
            }
        };
    }

    /** This store as a gate, with the session's admission decision supplied by the caller. */
    public Gate gate(LongFunction<String> refusal) {
        return new Gate() {
            @Override public long generationOf(String credential) {
                return HostedCredentials.this.generationOf(credential);
            }

            @Override public String admit(long generation) {
                return refusal.apply(generation);
            }
        };
    }
}
