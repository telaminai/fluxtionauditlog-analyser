package telamin.fluxtion.audit.analyser.analyser.ui;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.KeyStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * M79-3 (spec-first-minute §8.1): the evidence chip at the top of the Context column, and its details on click. It RENDERS
 * {@link EvidenceChipLines} and decides nothing: the state is the snapshot's, the words are EvidenceChipLines'. Before
 * the details open it reports the moment ({@code beforeDetails}), so the session can observe the file again; the
 * popover then shows what was published.
 */
final class EvidenceChipView extends JPanel {

    private final Runnable beforeDetails;
    private EvidenceChipLines lines;
    private Chip chip;
    private JPopupMenu details;

    EvidenceChipView(Runnable beforeDetails) {
        super(new FlowLayout(FlowLayout.LEFT, 0, 0));
        this.beforeDetails = beforeDetails == null ? () -> { } : beforeDetails;
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(UiTheme.GAP / 2, UiTheme.GAP, UiTheme.GAP / 2, UiTheme.GAP));
        setVisible(false);
    }

    /** Show {@code next}, or hide the chip for null (no log open). Call on the EDT. */
    void render(EvidenceChipLines next) {
        if (java.util.Objects.equals(next, lines) && chip != null && next != null) return;
        lines = next;
        removeAll();
        chip = null;
        if (next == null) {
            setVisible(false);
            revalidate();
            return;
        }
        chip = new Chip(next.chip(), next.state() == EvidenceChipLines.State.CHECKED, edge(next.state()));
        if (next.state() == EvidenceChipLines.State.CHANGED || next.state() == EvidenceChipLines.State.UNKNOWN) {
            chip.setForeground(UiTheme.warnForeground());
        }
        chip.setFocusable(true);
        chip.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        chip.getAccessibleContext().setAccessibleDescription(next.summary());
        chip.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                showDetails();
            }
        });
        for (KeyStroke k : new KeyStroke[]{KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0)}) {
            chip.getInputMap(JComponent.WHEN_FOCUSED).put(k, "evidence-details");
        }
        chip.getActionMap().put("evidence-details", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                showDetails();
            }
        });
        add(chip);
        setVisible(true);
        revalidate();
        repaint();
    }

    EvidenceChipLines lines() {
        return lines;
    }

    Chip chip() {
        return chip;
    }

    /** The details as last opened (null before the first), for tests. */
    JPopupMenu details() {
        return details;
    }

    /** The details: the summary, then each row's words as the surface that owns them states them. */
    void showDetails() {
        beforeDetails.run();                       // the session may observe again; render() has the published result
        if (lines == null || chip == null || !chip.isShowing()) return;
        details = new JPopupMenu();
        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(UiTheme.pad());
        body.setOpaque(false);
        int width = 320;
        body.add(line(lines.summary(), width).bold());
        for (EvidenceChipLines.Line l : lines.details()) body.add(line(l.label() + ": " + l.words(), width));
        details.add(body);
        details.getAccessibleContext().setAccessibleName("Evidence details");
        details.show(chip, 0, chip.getHeight());
    }

    private static WrapLine line(String text, int width) {
        WrapLine w = new WrapLine(text);
        w.setSize(width, Short.MAX_VALUE);
        w.setAlignmentX(LEFT_ALIGNMENT);
        Dimension d = w.getPreferredSize();
        w.setPreferredSize(new Dimension(width, Math.max(d.height, w.getFontMetrics(w.getFont()).getHeight())));
        w.setBorder(BorderFactory.createEmptyBorder(1, 0, 1, 0));
        return w;
    }

    private static Color edge(EvidenceChipLines.State state) {
        return switch (state) {
            case CHANGED, UNKNOWN -> UiTheme.warnForeground();
            case CHECKED -> UiTheme.accent();
            case AS_READ -> UiTheme.controlBorder();
        };
    }
}
