package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;
import telamin.fluxtion.audit.analyser.analyser.filter.FilterState;
import telamin.fluxtion.audit.analyser.analyser.llm.ActionResult;
import telamin.fluxtion.audit.analyser.analyser.llm.AppControl;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Review of M70 (R4): {@code report {bundle: {replay}}} names a file the analyser READS, so it is confined as every
 * other verb read is (D-F4): inside the exchange directory, or picked by the person this session. A path outside is
 * refused before the app is asked, so nothing is opened, nor even tested for existence (whose two different refusals
 * would otherwise tell an agent whether a file exists anywhere on the machine).
 */
class ReplayConfinementTest {

    /** The paths the app was asked to capture with, as replay; anything else it is asked answers nothing. */
    private final List<String> asked = new ArrayList<>();

    private ActionExecutor executor(Path exchange) {
        var ex = new ActionExecutor(() -> null, FilterState::new, new GraphTabs(), new LogTablePanel(), (r, n, f, k) -> { });
        AppConfig cfg = new AppConfig();
        cfg.assistantExports = true;
        cfg.assistantExportDir = exchange.toString();
        ex.bindExportPolicy(() -> cfg);
        AppControl app = (AppControl) Proxy.newProxyInstance(AppControl.class.getClassLoader(), new Class<?>[]{AppControl.class},
                (proxy, m, args) -> {
                    if (m.getName().equals("captureBundle") && args.length == 5) {
                        asked.add((String) args[4]);
                        return ActionResult.ok("report", "bundle", Map.of("phase", "WRITING"));
                    }
                    Class<?> r = m.getReturnType();
                    return r == boolean.class ? Boolean.FALSE : r == int.class ? 0 : r == long.class ? 0L : null;
                });
        ex.bind(null, app);
        return ex;
    }

    /** The verb, which must ANSWER: a refusal is a result, and a throw is a defect of its own. */
    private static ActionResult ask(ActionExecutor ex, String replay) {
        try {
            return ex.render("report", Map.of("bundle", Map.of("path", "run.fexp", "replay", replay)));
        } catch (RuntimeException e) {
            throw new AssertionError("report {bundle: {replay}} must refuse, not throw: " + e, e);
        }
    }

    @Test
    void aReplayOutsideTheExchangeDirectoryIsRefusedBeforeAnythingReadsIt(@TempDir Path tmp) throws Exception {
        Path exchange = Files.createDirectories(tmp.resolve("exchange"));
        Path outside = Files.writeString(tmp.resolve("elsewhere.replay.yaml"), "---\n");
        var ex = executor(exchange);

        var r = ask(ex, outside.toString());
        assertFalse(r.ok(), "outside the exchange directory: refused");
        assertTrue(r.error().startsWith("bundle 'replay': "), r.error());
        assertEquals(List.of(), asked, "the app was never asked, so the file was never opened");

        // and a path that does not exist gets the SAME refusal: the answer says nothing about what is on disk
        var absent = ask(ex, tmp.resolve("absent.replay.yaml").toString());
        assertEquals(r.error().replace(outside.toString(), "X"), absent.error().replace(tmp.resolve("absent.replay.yaml").toString(), "X"));
    }

    @Test
    void witnessAReplayInsideIsResolvedThereAndPassedOn(@TempDir Path tmp) throws Exception {
        Path exchange = Files.createDirectories(tmp.resolve("exchange"));
        Files.writeString(exchange.resolve("run.replay.yaml"), "---\n");
        var ex = executor(exchange);
        var r = ask(ex, "run.replay.yaml");                       // relative: against the exchange directory, as 'path' is
        assertTrue(r.ok(), String.valueOf(r.error()));
        assertEquals(List.of(exchange.toRealPath().resolve("run.replay.yaml").toString()), asked);
    }
}
