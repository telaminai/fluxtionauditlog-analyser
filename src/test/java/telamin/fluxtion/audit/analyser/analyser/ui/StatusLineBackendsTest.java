package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.session.view.StatusLineView;
import telamin.fluxtion.audit.analyser.analyser.session.view.ViewBackend;
import telamin.fluxtion.audit.analyser.analyser.session.view.ViewBackends;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * View-model spike (2026-09-27): the words the status line is drawn in, from the session's view, with no frame. And
 * several backends registered for the one element, each handed the same view.
 */
class StatusLineBackendsTest {

    private static StatusLineView view(boolean following) {
        return new StatusLineView(3, following, "/logs/run-1/f.yaml", "DEMO", 25, 0L, 300_000L, false, 2,
                "UNSEPARATED", 1, 0, null, null);
    }

    @Test
    @DisplayName("W2 at the text: the Follow line carries the provenance, the order warning and the producer warning")
    void theFollowLineIsTheLoadLineWithFollowOn() {
        String load = MainFrame.statusLineText(view(false));
        String follow = MainFrame.statusLineText(view(true));
        assertTrue(follow.startsWith("Following DEMO  (f.yaml) · 25 records · "), follow);
        for (String part : new String[]{"time-order violations (2)", "suspected missing record separators",
                "1 trailing record pending"}) {
            assertTrue(load.contains(part), () -> "load: " + load);
            assertTrue(follow.contains(part), () -> "follow: " + follow);
        }
    }

    @Test
    @DisplayName("A read failure and a reopen reason are stated after everything else")
    void failuresAreAppended() {
        StatusLineView v = new StatusLineView(3, true, "/f.yaml", null, 25, null, null, true, 0, null, 0, 0,
                "disk went away", "the file was replaced");
        String line = MainFrame.statusLineText(v);
        assertTrue(line.startsWith("Following f.yaml · 25 records · no timestamps  ·  complete"), line);
        assertTrue(line.endsWith("  ·  ⚠ Follow read failed: disk went away  ·  ⚠ the file was replaced"), line);
    }

    /** An HTML backend: typography differs, the words do not — it draws the one composer's text. */
    private static String html(StatusLineView v) {
        String text = MainFrame.statusLineText(v).replace("&", "&amp;").replace("<", "&lt;");
        return "<div class=\"status" + (v.following() ? " following" : "") + "\">" + text + "</div>";
    }

    @Test
    @DisplayName("Every registered backend is handed the same view, and the answer names each in order")
    void backendsRegisterAndAllDraw() {
        List<String> text = new ArrayList<>();
        List<String> page = new ArrayList<>();
        ViewBackends<StatusLineView> backends = new ViewBackends<StatusLineView>()
                .register(backend("text", v -> text.add(MainFrame.statusLineText(v))))
                .register(backend("html", v -> page.add(html(v))));

        List<String> drew = backends.render(view(true));

        assertEquals(List.of("text", "html"), drew);
        assertEquals(1, text.size());
        assertTrue(page.get(0).startsWith("<div class=\"status following\">"), page.get(0));
        assertTrue(page.get(0).contains(text.get(0).replace("&", "&amp;").replace("<", "&lt;")),
                "two backends of one view cannot disagree about the log — only about the markup");
    }

    private static ViewBackend<StatusLineView> backend(String name, java.util.function.Consumer<StatusLineView> draw) {
        return new ViewBackend<>() {
            @Override public String name() { return name; }
            @Override public void render(StatusLineView view) { draw.accept(view); }
        };
    }
}
