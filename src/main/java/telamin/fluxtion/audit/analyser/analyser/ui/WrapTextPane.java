package telamin.fluxtion.audit.analyser.analyser.ui;

import javax.swing.JTextPane;
import javax.swing.text.AbstractDocument;
import javax.swing.text.BoxView;
import javax.swing.text.ComponentView;
import javax.swing.text.Element;
import javax.swing.text.IconView;
import javax.swing.text.LabelView;
import javax.swing.text.ParagraphView;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledEditorKit;
import javax.swing.text.View;
import javax.swing.text.ViewFactory;
import java.awt.Dimension;
import java.util.function.BooleanSupplier;

/**
 * A {@link JTextPane} whose line-wrap can be toggled at runtime. The {@code StyledEditorKit} default
 * always wraps, so a custom kit whose paragraph view lays out at full width (no wrap) or the allotted
 * width (wrap) is the only reliable way. Shared by the record-detail and source views.
 */
public final class WrapTextPane extends JTextPane {
    private boolean wrap;

    /** @param wrap initial wrap state */
    public WrapTextPane(boolean wrap) {
        this.wrap = wrap;
        setEditorKit(new WrapEditorKit(() -> this.wrap));
    }

    public void setWrap(boolean wrap) {
        this.wrap = wrap;
    }

    public boolean isWrap() {
        return wrap;
    }

    /**
     * Wrapping always tracks the viewport. <b>Not</b> wrapping tracks it too whenever the text is
     * narrower than the viewport — otherwise the pane sizes to its longest line and everything to the
     * right of that is the scroll pane's background rather than the editor's, so a short file (or an
     * empty one) reads as a narrow strip floating in an unrelated panel. Long lines still exceed the
     * viewport and scroll horizontally, which is the point of turning wrap off.
     */
    @Override
    public boolean getScrollableTracksViewportWidth() {
        if (wrap) return true;
        return fillsLessThanViewport(true);
    }

    /** Same reasoning vertically: a short document should not leave a band of foreign background below it. */
    @Override
    public boolean getScrollableTracksViewportHeight() {
        return fillsLessThanViewport(false);
    }

    private boolean fillsLessThanViewport(boolean horizontal) {
        if (!(getParent() instanceof javax.swing.JViewport viewport)) return false;
        Dimension preferred = getUI().getPreferredSize(this);
        return horizontal
                ? preferred.width <= viewport.getWidth()
                : preferred.height <= viewport.getHeight();
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(0, 0);   // don't force an enclosing split pane to a large minimum
    }

    /** StyledEditorKit whose paragraph view honours a live wrap flag. */
    private static final class WrapEditorKit extends StyledEditorKit {
        private final ViewFactory factory;

        WrapEditorKit(BooleanSupplier wrap) {
            this.factory = elem -> {
                String kind = elem.getName();
                if (kind != null) {
                    switch (kind) {
                        case AbstractDocument.ContentElementName: return new LabelView(elem);
                        case AbstractDocument.ParagraphElementName: return new NoWrapAwareParagraphView(elem, wrap);
                        case AbstractDocument.SectionElementName: return new BoxView(elem, View.Y_AXIS);
                        case StyleConstants.ComponentElementName: return new ComponentView(elem);
                        case StyleConstants.IconElementName: return new IconView(elem);
                    }
                }
                return new LabelView(elem);
            };
        }

        @Override
        public ViewFactory getViewFactory() {
            return factory;
        }
    }

    /** Paragraph view that lays out at (effectively) unbounded width when not wrapping. */
    private static final class NoWrapAwareParagraphView extends ParagraphView {
        private final BooleanSupplier wrap;

        NoWrapAwareParagraphView(Element elem, BooleanSupplier wrap) {
            super(elem);
            this.wrap = wrap;
        }

        @Override
        public void layout(int width, int height) {
            super.layout(wrap.getAsBoolean() ? width : Short.MAX_VALUE, height);
        }

        @Override
        public float getMinimumSpan(int axis) {
            return wrap.getAsBoolean() ? super.getMinimumSpan(axis) : super.getPreferredSpan(axis);
        }
    }
}
