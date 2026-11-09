package telamin.fluxtion.audit.analyser.analyser.mcp;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.llm.Json;
import telamin.fluxtion.audit.analyser.analyser.net.RestEndpointFile;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
class CompilerSignInToolsTest {
    @TempDir Path dir;
    @Test void listsSessionToolsAndRefusesMalformedArgumentsWithoutAnAppOrCredentialRead() {
        McpBridge b=new McpBridge(new RestEndpointFile(dir.resolve("absent")));
        String list=b.handle("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}");
        assertTrue(list.contains("compiler_sign_in_start")); assertTrue(list.contains("compiler_sign_in_status"));
        String reply=b.handle("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\",\"params\":{\"name\":\"compiler_sign_in_start\",\"arguments\":{\"compilerOrigin\":42}}}");
        assertEquals("usage_error",Json.at(Json.parse(reply),"result","structuredContent","event"));
        assertNull(b.signIns,"invalid arguments must not initialize a store");
    }
}
