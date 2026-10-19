package telamin.fluxtion.audit.analyser.analyser.ui.setup;

import telamin.fluxtion.audit.analyser.analyser.session.AiSetupLines;
import telamin.fluxtion.audit.analyser.analyser.session.AssistantProfile;
import telamin.fluxtion.audit.analyser.analyser.session.ProfileState;
import telamin.fluxtion.audit.analyser.analyser.ui.UiText;

import javax.swing.AbstractButton;
import javax.swing.ButtonGroup;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * M77-4 (spec-ai-setup-wizard.md §14.3, OD-77.3): the Assistant ▾ quick switch — the profile cards' replacement.
 *
 * <blockquote>✓ Safe analysis · ◆ Full Claude Code… · In-app assistant · Off · ─── · Set up AI…</blockquote>
 *
 * <p><b>Rule 9.</b> It RENDERS {@link ProfileState} — the profile in force, and whether Full's confirmation is showing —
 * and REPORTS a press through its {@link Host}: a profile as {@code AssistantProfileChosen(request, p, origin=MENU)}, or
 * <i>Set up AI…</i> as {@code AiSetupRequested}. It decides nothing (review R1-24): whether a press applies, asks Full's
 * confirmation first, is refused mid-turn, or opens the wizard at the screen that can finish it (Claude inside with
 * {@code claude} not found; the in-app assistant with no key) is {@code aiSetup}'s.
 *
 * <p><b>Reusable.</b> One instance per host control; {@link #items} is the pure model (no Swing), {@link #menu} builds
 * the popup from it, and {@link #attach} hangs it on any button. The assistant panel's header control hosts it today;
 * M78 D2 makes the panel's single mode chip this same switch — attach it to that chip and remove nothing else.
 */
public final class QuickSwitch {

    /** What a press reports. The frame turns each into a session fact; nothing else happens here. */
    public interface Host {
        /** A profile item was pressed: {@code request} is this switch's own id, never reused. */
        void choose(long request, AssistantProfile profile);

        /** <i>Set up AI…</i> was pressed. */
        void setUpAi();
    }

    /** The profiles the switch offers, in the order it shows them (§14.3). */
    public static final List<AssistantProfile> ORDER = List.of(AssistantProfile.SAFE, AssistantProfile.FULL,
            AssistantProfile.IN_APP, AssistantProfile.OFF);

    /** A row of the menu: a profile, the separator, or Set up AI…. */
    public enum Kind { PROFILE, SEPARATOR, SET_UP }

    /**
     * One row, as data: what it says, its tooltip, whether it is the profile in force ({@code selected}), and whether it
     * can be pressed now. {@code profile} is null for the separator and Set up AI….
     */
    public record Item(Kind kind, AssistantProfile profile, String label, String tooltip, boolean selected,
                       boolean enabled) {
        /** The accessible name: the label, and "in force" for the selected profile (never colour or a tick alone). */
        public String accessibleName() {
            return selected ? label + ", in force" : label;
        }
    }

    /**
     * The rows for the published state. Pure: no Swing, no clock. A profile is selected only once the frame has reported
     * one ({@link ProfileState#observed()}); every row is disabled while Full's confirmation is showing, as the window
     * behind an application-modal dialog would be.
     */
    public static List<Item> items(ProfileState state) {
        ProfileState s = state == null ? ProfileState.NONE : state;
        boolean enabled = !s.confirming();
        List<Item> out = new ArrayList<>();
        for (AssistantProfile p : ORDER) {
            boolean inForce = s.observed() && s.profile() == p;
            out.add(new Item(Kind.PROFILE, p, AiSetupLines.quickLabel(p, inForce), AiSetupLines.quickTip(p), inForce,
                    enabled));
        }
        out.add(new Item(Kind.SEPARATOR, null, "", "", false, false));
        out.add(new Item(Kind.SET_UP, null, AiSetupLines.SET_UP_LINK, AiSetupLines.QUICK_SET_UP_TIP, false, enabled));
        return out;
    }

    private final Host host;
    private long nextRequest;
    private JPopupMenu shown;

    public QuickSwitch(Host host) {
        this.host = Objects.requireNonNull(host, "host");
    }

    /** The popup for {@code state}: a radio group for the profiles, then Set up AI…. A press only reports. */
    public JPopupMenu menu(ProfileState state) {
        JPopupMenu menu = new JPopupMenu();
        menu.getAccessibleContext().setAccessibleName(AiSetupLines.QUICK_SWITCH_NAME);
        ButtonGroup group = new ButtonGroup();
        for (Item it : items(state)) {
            switch (it.kind()) {
                case SEPARATOR -> menu.addSeparator();
                case PROFILE -> {
                    JRadioButtonMenuItem m = UiText.radioMenuItem(it.label());
                    m.setSelected(it.selected());
                    m.putClientProperty(PROFILE_KEY, it.profile());
                    dress(m, it);
                    AssistantProfile p = it.profile();
                    m.addActionListener(e -> host.choose(++nextRequest, p));
                    group.add(m);
                    menu.add(m);
                }
                case SET_UP -> {
                    JMenuItem m = UiText.menuItem(it.label());
                    m.putClientProperty(SET_UP_KEY, Boolean.TRUE);
                    dress(m, it);
                    m.addActionListener(e -> host.setUpAi());
                    menu.add(m);
                }
            }
        }
        return menu;
    }

    /**
     * Show the menu under {@code invoker}, rendered from the state as it is now. An invoker that is not on screen (a
     * press on a control in a hidden tab) builds the menu without showing it: a popup needs a place to appear.
     */
    public JPopupMenu show(Component invoker, ProfileState state) {
        shown = menu(state);
        if (invoker.isShowing()) shown.show(invoker, 0, invoker.getHeight());
        return shown;
    }

    /** Hang the switch on a button: a press shows the menu for {@code state}'s current value. */
    public void attach(AbstractButton button, Supplier<ProfileState> state) {
        button.addActionListener(e -> show(button, state.get()));
    }

    /** The menu last shown (for the frame tests), or null. */
    public JPopupMenu lastShown() {
        return shown;
    }

    /** The client property a profile row carries (its {@link AssistantProfile}). */
    public static final String PROFILE_KEY = "quickSwitch.profile";
    /** The client property the Set up AI… row carries. */
    public static final String SET_UP_KEY = "quickSwitch.setUp";

    private static void dress(JComponent m, Item it) {
        m.setToolTipText(UiText.tooltip(it.tooltip()));
        m.setEnabled(it.enabled());
        m.getAccessibleContext().setAccessibleName(it.accessibleName());
        m.getAccessibleContext().setAccessibleDescription(it.tooltip());
    }
}
