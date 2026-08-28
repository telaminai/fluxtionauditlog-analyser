package telamin.fluxtion.audit.analyser.analyser.mcp;

import telamin.fluxtion.audit.analyser.analyser.net.RestEndpointFile;

/**
 * The local analyser fact shown by the MCP setup surface (M42.2).
 *
 * <p>This is intentionally narrower than a bridge probe: it says whether <em>this</em> Swing process
 * owns a live local transport. Client registration and a bridge command are separate facts, so a green
 * local state never gets misrepresented as "an AI client is connected".
 */
public final class McpSetupState {

    public enum LocalStatus {
        OFF,
        STARTING,
        READY,
        OTHER_INSTANCE
    }

    public record LocalReadiness(LocalStatus status, String detail) {
        /** Only this state may safely launch a bridge probe against the well-known endpoint. */
        public boolean canProbe() {
            return status == LocalStatus.READY;
        }
    }

    private McpSetupState() {
    }

    /**
     * Classify the endpoint without returning its URL or token. The boolean argument keeps the policy
     * pure and testable; production obtains it through {@link RestEndpointFile.Endpoint#alive()}.
     */
    public static LocalReadiness classify(boolean transportEnabled, RestEndpointFile.Endpoint endpoint,
                                          boolean endpointAlive, long thisPid) {
        if (!transportEnabled) {
            return new LocalReadiness(LocalStatus.OFF, "Local transport is off in this analyser window.");
        }
        if (endpoint == null || !endpointAlive) {
            return new LocalReadiness(LocalStatus.STARTING,
                    "Local transport is enabled, but this analyser has not published a live endpoint yet.");
        }
        if (endpoint.pid() != thisPid) {
            return new LocalReadiness(LocalStatus.OTHER_INSTANCE,
                    "Another analyser window owns the current local MCP endpoint.");
        }
        return new LocalReadiness(LocalStatus.READY, "Analyser ready for MCP in this window.");
    }

    /**
     * Whether this window should RE-PUBLISH its endpoint. True only when the transport is enabled, this
     * window's server is actually listening, and the well-known file names no live owner ({@code STARTING}).
     *
     * <p>Found by the owner's eyeball run of {@code tools/verify-m43.py} (2026-08-28): two windows, the
     * second takes the endpoint (the first correctly reads "MCP elsewhere"), the second CLOSES and deletes
     * the file — and the first window said "MCP starting" for ever, because nothing ever wrote its
     * endpoint again. Its server had been listening the whole time; only the pointer was gone. A live
     * owner is never displaced: {@code OTHER_INSTANCE} returns false here, so two windows cannot fight
     * over the file — the newcomer keeps it until it goes away.
     */
    public static boolean shouldReclaim(LocalReadiness readiness, boolean serverListening) {
        return serverListening && readiness != null && readiness.status() == LocalStatus.STARTING;
    }
}
