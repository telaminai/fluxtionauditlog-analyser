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
