package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.assistant.AskCatalogue;
import javax.swing.*;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.text.JTextComponent;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** A local snapshot of the code on screen. Never reads a file or sends a model request. */
final class CodeExplainMenu {
    static final int MAX_CODE_CHARS = AskCatalogue.MAX_CODE_CHARS;
    record Context(String source, String language) { }
    private CodeExplainMenu() { }

    static void install(JTextComponent text, Supplier<Context> context,
                        Consumer<String> copy, Consumer<String> ask) {
        JPopupMenu popup = new JPopupMenu();
        popup.addPopupMenuListener(new PopupMenuListener() {
            @Override public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                populate(popup, text, context.get(), copy, ask);
            }
            @Override public void popupMenuWillBecomeInvisible(PopupMenuEvent e) { }
            @Override public void popupMenuCanceled(PopupMenuEvent e) { }
        });
        text.setComponentPopupMenu(popup);
    }

    static void populate(JPopupMenu popup, JTextComponent text, Context context,
                         Consumer<String> copy, Consumer<String> ask) {
        popup.removeAll();
        JMenu explain = UiText.menu(AskCatalogue.text("CODE_EXPLAIN.label"));
        explain.setName("codeExplain");
        String prompt = context == null ? null
                : prompt(text.getText(), text.getSelectionStart(), text.getSelectionEnd(), context);
        explain.setEnabled(prompt != null);
        JMenuItem copyItem = UiText.menuItem(AskCatalogue.text("CODE_EXPLAIN.copy"));
        copyItem.setName("codeExplainCopy");
        copyItem.setToolTipText(UiText.tooltip(AskCatalogue.text("CODE_EXPLAIN.copyTooltip")));
        copyItem.addActionListener(e -> { if (prompt != null) copy.accept(prompt); });
        JMenuItem askItem = UiText.menuItem(AskCatalogue.text("CODE_EXPLAIN.ask"));
        askItem.setName("codeExplainAsk");
        askItem.setToolTipText(UiText.tooltip(AskCatalogue.text("CODE_EXPLAIN.askTooltip")));
        askItem.addActionListener(e -> { if (prompt != null) ask.accept(prompt); });
        explain.add(copyItem);
        explain.add(askItem);
        popup.add(explain);
        popup.addSeparator();
        JMenuItem copyText = new JMenuItem("Copy");
        copyText.setEnabled(text.getSelectionStart() != text.getSelectionEnd());
        copyText.addActionListener(e -> text.copy());
        popup.add(copyText);
        JMenuItem selectAll = new JMenuItem("Select all");
        selectAll.setEnabled(!text.getText().isEmpty());
        selectAll.addActionListener(e -> text.selectAll());
        popup.add(selectAll);
    }

    static String prompt(String displayed, int start, int end, Context context) {
        var draft = AskCatalogue.composeCodeExplanation(displayed, start, end, context.source(), context.language());
        return draft == null ? null : draft.text();
    }
}
