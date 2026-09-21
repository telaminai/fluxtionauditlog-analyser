package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.graph.ChartNotes;
import java.awt.FontMetrics;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Deterministic annotation geometry, shared by painting and spotlight measurement. */
final class ChartAnnotationLayout {
    record Pin(int first, int last, List<Integer> columns, Rectangle bounds) {
        String label() { return first == last ? String.valueOf(first) : first + "–" + last; }
    }
    static List<Pin> pins(Map<Integer, List<ChartNotes.Note>> columns, int x, int width, int y, FontMetrics fm) {
        var out = new ArrayList<Pin>();
        int number = 0;
        for (var entry : columns.entrySet()) {
            int first = number + 1;
            number += entry.getValue().size();
            var anchors = new ArrayList<Integer>(); anchors.add(entry.getKey());
            Pin pin = pin(first, number, anchors, x, width, y, fm);
            while (!out.isEmpty()) {
                Pin previous = out.getLast();
                var padded = new Rectangle(previous.bounds()); padded.grow(4, 0);
                if (!padded.intersects(pin.bounds())) break;
                out.removeLast();
                anchors.addAll(0, previous.columns());
                pin = pin(previous.first(), number, anchors, x, width, y, fm);
            }
            out.add(pin);
        }
        return List.copyOf(out);
    }
    private static Pin pin(int first, int last, List<Integer> columns, int x, int width, int y, FontMetrics fm) {
        String label = first == last ? String.valueOf(first) : first + "–" + last;
        int w = Math.min(Math.max(18, fm.stringWidth(label) + 10), Math.max(1, width));
        int center = x + (columns.getFirst() + columns.getLast()) / 2;
        int left = Math.max(x, Math.min(x + width - w, center - w / 2));
        return new Pin(first, last, List.copyOf(columns), new Rectangle(left, y, w, 18));
    }
    static List<String> wrap(List<String> lines, FontMetrics fm, int width) {
        var out = new ArrayList<String>();
        for (String line : lines) {
            String remaining = line;
            while (fm.stringWidth(remaining) > width && remaining.length() > 1) {
                int end = remaining.length();
                while (end > 1 && fm.stringWidth(remaining.substring(0, end)) > width) end--;
                int space = remaining.lastIndexOf(' ', end);
                if (space > 0) end = space;
                out.add(remaining.substring(0, end));
                remaining = remaining.substring(end).stripLeading();
            }
            out.add(remaining);
        }
        return out;
    }
}
