package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.filter.FilterState;
import telamin.fluxtion.audit.analyser.analyser.llm.ActionResult;
import telamin.fluxtion.audit.analyser.analyser.llm.AppControl;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * §H feedback 17 at the verb: a bad {@code sections} is refused before the app is asked anything, an absent one
 * reaches the unchanged full {@code context()}, and a valid one reaches the projecting overload. Headless.
 */
class ContextSectionsVerbTest {

    private final List<String> calls = new ArrayList<>();
    private final Map<String, Object> full = Map.of("menus", Map.of(), "project", Map.of("active", false));

    private ActionExecutor executor() {
        AppControl app = (AppControl) Proxy.newProxyInstance(AppControl.class.getClassLoader(),
                new Class<?>[]{AppControl.class}, (proxy, m, args) -> {
                    calls.add(m.getName() + "/" + m.getParameterCount());
                    if (m.getName().equals("context") && m.getParameterCount() == 0) {
                        return ActionResult.ok("context", "context", full);
                    }
                    if (m.isDefault()) return InvocationHandler.invokeDefault(proxy, m, args);
                    return null;
                });
        var filter = new FilterState();
        var ex = new ActionExecutor(() -> null, () -> filter, new GraphTabs(), new LogTablePanel(), (r, n, f, k) -> { });
        ex.bind(null, app);
        return ex;
    }

    @Test
    void anUnknownOrEmptySelectionIsRefusedBeforeTheAppIsAskedAnything() {
        var ex = executor();
        ActionResult unknown = ex.render("context", Map.of("sections", List.of("menus", "everything")));
        assertFalse(unknown.ok());
        assertTrue(unknown.error().contains("[everything]"), unknown.error());
        ActionResult empty = ex.render("context", Map.of("sections", List.of()));
        assertFalse(empty.ok());
        assertEquals(List.of(), calls, "a refused selection reads nothing and changes nothing");
    }

    @Test
    void noSectionsIsTheFullContext_andASelectionReachesTheProjection() {
        var ex = executor();
        ActionResult all = ex.render("context", Map.of());
        assertSame(full, all.payload(), "the default is the full payload, untouched");
        assertEquals(List.of("context/0"), calls);
        calls.clear();
        ActionResult menus = ex.render("context", Map.of("sections", List.of("menus")));
        assertEquals(List.of("menus", "scope"), List.copyOf(menus.payload().keySet()));
        assertEquals("context/1", calls.get(0), "the selection reaches context(Selection)");
    }
}
