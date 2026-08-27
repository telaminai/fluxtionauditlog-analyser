package telamin.fluxtion.audit.analyser.analyser.mcp;

import telamin.fluxtion.audit.analyser.analyser.llm.ActionDispatcher;
import telamin.fluxtion.audit.analyser.analyser.llm.ActionResult;
import telamin.fluxtion.audit.analyser.analyser.net.ActionServer;
import telamin.fluxtion.audit.analyser.analyser.net.RestEndpointFile;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;
import telamin.fluxtion.audit.analyser.analyser.parse.Samples;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The M42.1 child-process loopback check: bridge protocol, current endpoint and read-only context. */
class McpConnectionProbeTest {

    @TempDir
    Path dir;

    private ActionServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop();
    }

    @Test
    void actualBridgeChildReachesTheTokenedRestServerWithReadOnlyContext() throws Exception {
        RestEndpointFile endpoint = startServer(false);
        McpConnectionProbe.Result result = new McpConnectionProbe(actualBridge(), endpoint).probe();

        assertEquals(McpConnectionProbe.Status.VERIFIED, result.status(), result.detail());
        assertEquals(McpConnectionProbe.Era.MODERN, result.era());
    }

    @Test
    void fallsBackToLegacyWhenTheBridgeDoesNotSpeakModernDiscovery() throws Exception {
        RestEndpointFile endpoint = ownLiveEndpoint();
        McpConnectionProbe.Result result = new McpConnectionProbe(helperBridge("legacy"), endpoint).probe();

        assertEquals(McpConnectionProbe.Status.VERIFIED, result.status(), result.detail());
        assertEquals(McpConnectionProbe.Era.LEGACY, result.era());
    }

    @Test
    void usesModernWhenTheBridgeAdvertisesIt() throws Exception {
        RestEndpointFile endpoint = ownLiveEndpoint();
        McpConnectionProbe.Result result = new McpConnectionProbe(helperBridge("modern"), endpoint).probe();

        assertEquals(McpConnectionProbe.Status.VERIFIED, result.status(), result.detail());
        assertEquals(McpConnectionProbe.Era.MODERN, result.era());
    }

    @Test
    void alsoFallsBackWhenModernDiscoveryReportsAnUnsupportedVersion() throws Exception {
        RestEndpointFile endpoint = ownLiveEndpoint();
        McpConnectionProbe.Result result = new McpConnectionProbe(helperBridge("unsupported-modern"), endpoint).probe();

        assertEquals(McpConnectionProbe.Status.VERIFIED, result.status(), result.detail());
        assertEquals(McpConnectionProbe.Era.LEGACY, result.era());
    }

    @Test
    void endpointOwnedByAnotherLiveInstanceNeverLaunchesTheBridge() {
        RestEndpointFile.Endpoint other = new RestEndpointFile.Endpoint("http://127.0.0.1:1", "not-used", 17, null);
        McpConnectionProbe probe = new McpConnectionProbe(actualBridge(), () -> other, endpoint -> true,
                () -> 42L, command -> fail("must not launch a bridge for another analyser"), Duration.ofSeconds(1));

        assertEquals(McpConnectionProbe.Status.OTHER_INSTANCE, probe.probe().status());
    }

    @Test
    void absentEndpointIsRestOffWithoutLaunchingAnything() {
        McpConnectionProbe probe = new McpConnectionProbe(actualBridge(), () -> null, endpoint -> true,
                () -> 42L, command -> fail("must not launch without a local endpoint"), Duration.ofSeconds(1));

        assertEquals(McpConnectionProbe.Status.REST_OFF, probe.probe().status());
    }

    @Test
    void successfulContextIsDowngradedWhenAnotherInstanceWinsTheEndpointRace() throws Exception {
        RestEndpointFile.Endpoint ours = new RestEndpointFile.Endpoint("http://127.0.0.1:1", "not-used", 42, null);
        RestEndpointFile.Endpoint other = new RestEndpointFile.Endpoint("http://127.0.0.1:2", "not-used", 17, null);
        ArrayDeque<RestEndpointFile.Endpoint> endpoints = new ArrayDeque<>(List.of(ours, other));
        McpConnectionProbe probe = new McpConnectionProbe(helperBridge("modern"), endpoints::removeFirst,
                endpoint -> true, () -> 42L, command -> new ProcessBuilder(command).start(), Duration.ofSeconds(5));

        assertEquals(McpConnectionProbe.Status.OTHER_INSTANCE, probe.probe().status());
    }

    @Test
    void missingBridgeExecutableIsReportedAsLaunchFailed() throws Exception {
        RestEndpointFile endpoint = ownLiveEndpoint();
        McpLaunchCommand missing = McpLaunchCommand.of(List.of(dir.resolve("does-not-exist").toString()));

        assertEquals(McpConnectionProbe.Status.LAUNCH_FAILED, new McpConnectionProbe(missing, endpoint).probe().status());
    }

    @Test
    void malformedBridgeOutputIsReportedAsProtocolFailed() throws Exception {
        RestEndpointFile endpoint = ownLiveEndpoint();

        assertEquals(McpConnectionProbe.Status.PROTOCOL_FAILED,
                new McpConnectionProbe(helperBridge("garbage"), endpoint).probe().status());
    }

    @Test
    void bridgeUnreachableCodeMapsToRestOff() throws Exception {
        RestEndpointFile endpoint = ownLiveEndpoint();
        endpoint.write("http://127.0.0.1:1", "test-token"); // our process is alive, but no server listens there

        McpConnectionProbe.Result result = new McpConnectionProbe(actualBridge(), endpoint).probe();
        assertEquals(McpConnectionProbe.Status.REST_OFF, result.status(), result.detail());
    }

    @Test
    void contextToolFailureIsNotMistakenForATransportFailure() throws Exception {
        RestEndpointFile endpoint = startServer(true);
        McpConnectionProbe.Result result = new McpConnectionProbe(actualBridge(), endpoint).probe();

        assertEquals(McpConnectionProbe.Status.ACTION_FAILED, result.status(), result.detail());
    }

    private RestEndpointFile startServer(boolean failContext) throws IOException {
        HeapLogStore store = new HeapLogStore(Samples.sample());
        ActionDispatcher dispatcher = new ActionDispatcher(false, null, () -> store.index().snapshot(), store::rawText,
                (action, params) -> failContext ? ActionResult.error("context deliberately failed")
                        : ActionResult.ok(action, "context", Map.of("fresh", true)));
        RestEndpointFile endpoint = endpointFile();
        server = new ActionServer(dispatcher, "probe-token", 20, 100.0, endpoint);
        server.start();
        return endpoint;
    }

    private RestEndpointFile ownLiveEndpoint() throws IOException {
        RestEndpointFile endpoint = endpointFile();
        endpoint.write("http://127.0.0.1:1", "test-token");
        return endpoint;
    }

    private RestEndpointFile endpointFile() {
        return new RestEndpointFile(dir.resolve(".fluxtion-analyser").resolve("rest-endpoint"));
    }

    private McpLaunchCommand actualBridge() {
        return McpLaunchCommand.of(List.of(javaExecutable(), "-Duser.home=" + dir.toAbsolutePath(), "-cp", System.getProperty("java.class.path"),
                McpBridge.class.getName()));
    }

    private static McpLaunchCommand helperBridge(String era) {
        return McpLaunchCommand.of(List.of(javaExecutable(), "-cp", System.getProperty("java.class.path"),
                ProbeTestBridgeMain.class.getName(), era));
    }

    private static String javaExecutable() {
        return Path.of(System.getProperty("java.home"), "bin", "java").toString();
    }
}
