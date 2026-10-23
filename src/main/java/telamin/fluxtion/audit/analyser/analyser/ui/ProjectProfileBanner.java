package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;
import telamin.fluxtion.audit.analyser.analyser.session.ProfileOnDiskState;

import javax.swing.*;
import java.awt.*;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * #177: the open profile changed on disk. A non-modal banner above the workspace — short words and three buttons,
 * because a sentence nobody reads is not a choice. It renders what {@code profileOnDisk} decided and reports the click;
 * it decides nothing, and reads only the snapshot's {@link ProfileOnDiskState}.
 */
final class ProjectProfileBanner extends JPanel {
    private final Consumer<SessionEvents.ProjectProfileChoice> answered;
    private final JLabel text = new JLabel();
    final JButton reload = new JButton("Reload");
    final JButton keep = new JButton("Keep mine");
    final JButton notNow = new JButton("Not now");
    private ProfileOnDiskState rendered;

    ProjectProfileBanner(Consumer<SessionEvents.ProjectProfileChoice> answered) {
        super(new FlowLayout(FlowLayout.LEADING, 6, 3));
        this.answered = answered;
        setName("projectProfileChanged");
        reload.setName("projectProfileReload");
        keep.setName("projectProfileKeep");
        notNow.setName("projectProfileNotNow");
        reload.setToolTipText("Use the file's settings; edits not yet saved here are discarded");
        keep.setToolTipText("Write this window's settings over the file");
        notNow.setToolTipText("Leave both; this window's edits are not saved until you choose");
        reload.addActionListener(e -> answer(SessionEvents.ProfileChoice.RELOAD));
        keep.addActionListener(e -> answer(SessionEvents.ProfileChoice.KEEP_MINE));
        notNow.addActionListener(e -> answer(SessionEvents.ProfileChoice.LATER));
        add(text);
        add(reload);
        add(keep);
        add(notNow);
        setVisible(false);
    }

    private void answer(SessionEvents.ProfileChoice choice) {
        if (rendered != null) answered.accept(new SessionEvents.ProjectProfileChoice(rendered.profileKey(), choice, "person"));
    }

    void render(ProfileOnDiskState banner) {
        if (Objects.equals(rendered, banner)) return;
        rendered = banner;
        if (banner != null) {
            text.setText(banner.saveRefused() ? "Project settings changed on disk. Your edits are not saved."
                    : "Project settings changed on disk.");
        }
        setVisible(banner != null);
        revalidate();
        repaint();
    }

    String text() {
        return text.getText();
    }
}
