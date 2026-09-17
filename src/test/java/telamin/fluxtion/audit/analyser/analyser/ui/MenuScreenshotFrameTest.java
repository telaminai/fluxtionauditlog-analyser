package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;

import java.awt.GraphicsEnvironment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.field;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.find;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.onEdt;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.render;

/**
 * {@code screenshot {scope: "menu:File"}} opens the menu for a native capture — and says WHERE each item is,
 * relative to the window it reports, so a caller can point at "New project from template…" rather than describe
 * it. The docs harness rings that item in the tutorial from these numbers; a wrong rectangle would publish an
 * arrow pointing at the wrong command.
 */
class MenuScreenshotFrameTest {

    @Test
    @SuppressWarnings("unchecked")
    void anOpenMenuReportsWhereEachItemIs_insideTheWindow_topToBottom(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        Path exchange = Files.createDirectories(tmp.resolve("exchange"));
        try (AsyncOpenInterleavingFrameTest.Frame f = new AsyncOpenInterleavingFrameTest.Frame(tmp)) {
            onEdt(() -> {
                AppConfig config = (AppConfig) field(f.frame, "config");
                config.assistantExports = true;
                config.assistantExportDir = exchange.toString();
                f.frame.setSize(1300, 850);
                f.frame.setVisible(true);
                f.frame.validate();
            });
            AtomicReference<Map<String, Object>> shot = new AtomicReference<>();
            onEdt(() -> shot.set(render(f.ex, "screenshot", Map.of("path", exchange.resolve("menu.png").toString(), "scope", "menu:File"))));
            try {
                List<Map<String, Object>> items = (List<Map<String, Object>>) find(shot.get(), "menuItems");
                Map<String, Object> window = (Map<String, Object>) find(shot.get(), "windowBounds");
                assertNotNull(items, "an open menu reports its items: " + shot.get());
                Map<String, Object> wanted = items.stream()
                        .filter(i -> String.valueOf(i.get("text")).startsWith("New project from template")).findFirst().orElse(null);
                assertNotNull(wanted, "the item the tutorial points at: " + items.stream().map(i -> i.get("text")).toList());

                int lastY = -1;
                for (Map<String, Object> item : items) {
                    Map<String, Object> b = (Map<String, Object>) item.get("bounds");
                    int x = (int) b.get("x"), y = (int) b.get("y"), w = (int) b.get("width"), h = (int) b.get("height");
                    assertTrue(w > 20 && h > 8, item.get("text") + " has an area: " + b);
                    assertTrue(x >= 0 && y >= 0 && x + w <= (int) window.get("width") && y + h <= (int) window.get("height"),
                            item.get("text") + " is reported RELATIVE TO THE WINDOW, inside it: " + b + " in " + window);
                    assertTrue(y > lastY, "top to bottom, as a person reads the menu: " + item.get("text"));
                    lastY = y;
                }
            } finally {
                onEdt(() -> render(f.ex, "screenshot", Map.of("path", exchange.resolve("close.png").toString(), "scope", "menu:close")));
            }

            AtomicReference<Map<String, Object>> plain = new AtomicReference<>();
            onEdt(() -> plain.set(render(f.ex, "screenshot", Map.of("path", exchange.resolve("plain.png").toString()))));
            assertNull(find(plain.get(), "menuItems"), "an ordinary shot says nothing about menus");
            assertEquals(Boolean.TRUE, plain.get().get("ok"));
        }
    }
}
