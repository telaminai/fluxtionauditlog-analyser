package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.assistant.AskCatalogue;

import javax.swing.*;
import java.awt.*;

/**
 * M74 P2 (spec §5.8): a start-page flow that opened something (S3's bundle, S5's log) has left the start page; with no
 * AI configured its no-AI fallback buttons are offered here instead, in a small window that does not block the
 * workspace. The same buttons as the page: each runs an existing action, the menu path only in its tooltip (r8 F7).
 */
final class StartFallbackWindow {

    private StartFallbackWindow() {
    }

    static void show(Component parent, String entryId, StartPromptView.Host host) {
        var entry = AskCatalogue.byId(entryId).orElse(null);
        java.util.List<AskCatalogue.Fallback> fallbacks = entry == null
                ? java.util.List.of(AskCatalogue.Fallback.COPY_PROMPT) : entry.fallbacks();
        if (fallbacks.isEmpty()) return;
        Window owner = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        JDialog d = new JDialog(owner, "No AI assistant is configured", Dialog.ModalityType.MODELESS);
        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        content.add(Fluid.text(AskCatalogue.text("start.noAi")), BorderLayout.NORTH);
        JPanel buttons = new JPanel(new WrapLayout(FlowLayout.LEFT, 6, 4));
        for (AskCatalogue.Fallback f : fallbacks) {
            String key = "fallback." + switch (f) {
                case ASSISTANT_SETTINGS -> "assistantSettings";
                case CONNECT -> "connect";
                case COPY_PROMPT -> "copyPrompt";
                case NEW_PROJECT -> "newProject";
                case BUNDLE_GUIDE -> "bundleGuide";
                case TEMPLATE -> "template";
                case USER_GUIDE -> "userGuide";
            };
            JButton b = UiText.button(AskCatalogue.text(key));
            b.setToolTipText(UiText.tooltip(AskCatalogue.text(key + ".tip")));
            b.addActionListener(e -> host.fallback(f, entryId, ""));
            buttons.add(b);
        }
        JButton close = UiText.button("Close");
        close.addActionListener(e -> d.dispose());
        buttons.add(close);
        content.add(buttons, BorderLayout.CENTER);
        d.setContentPane(content);
        d.pack();
        d.setLocationRelativeTo(parent);
        d.setVisible(true);
    }
}
