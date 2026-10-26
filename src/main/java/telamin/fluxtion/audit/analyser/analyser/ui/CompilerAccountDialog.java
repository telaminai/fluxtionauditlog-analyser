package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.account.AccountDetails;
import telamin.fluxtion.audit.analyser.analyser.account.CompilerAccountClient;
import telamin.fluxtion.audit.analyser.analyser.account.CompilerAccountClient.Device;
import telamin.fluxtion.audit.analyser.analyser.account.CompilerAccountClient.Me;
import telamin.fluxtion.audit.analyser.analyser.account.CompilerAccountClient.DeviceStart;
import telamin.fluxtion.audit.analyser.analyser.account.CompilerAccountClient.Outcome;
import telamin.fluxtion.audit.analyser.analyser.account.CompilerAccountClient.Usage;
import telamin.fluxtion.audit.analyser.analyser.account.CompilerCredential;
import telamin.fluxtion.audit.analyser.analyser.account.CredentialStore;
import telamin.fluxtion.audit.analyser.analyser.account.CredentialStores;
import telamin.fluxtion.audit.analyser.analyser.account.ServiceAddress;
import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;
import telamin.fluxtion.audit.analyser.analyser.core.Background;
import telamin.fluxtion.audit.analyser.analyser.core.ReleaseNotes;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.plaf.basic.BasicComboBoxEditor;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.net.URI;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * CA-1 — AI ▸ Compiler account…: connect this analyser to a compiler account by device sign-in; show the account (type,
 * role, plan), its credits, this device and the account's other devices; copy a support summary; disconnect, or revoke
 * this device or another one the person picks.
 *
 * <p><b>Reads, and revokes only on request.</b> The only calls are the device sign-in the person approves in their
 * browser, reads of the account, usage and device list, and the revoke of a credential the person chose
 * ({@link CompilerAccountClient}).
 *
 * <p><b>Secrets.</b> The device code and the credential are package-private to {@code account}; this dialog cannot
 * read either, so it can neither display nor log them. It shows the user code, which is meant to be read.
 *
 * <p><b>Rule 9.</b> Like {@link FluxtionKeyDialog}, this is dialog-local: the connection is a fact of the OS credential
 * store, read when the dialog opens, and the sign-in is an interaction that lives and dies with the dialog (closing it
 * cancels). No other surface renders the account and {@code context} does not carry it, so there is no second reader to
 * drift. Showing it anywhere else is the point at which it becomes session state: a node, an effect and a fact.
 */
public final class CompilerAccountDialog extends JDialog {

    static final String TITLE = "Compiler account";
    static final String CONNECT = "Connect compiler account";
    static final String REFRESH = "Refresh";
    static final String DISCONNECT = "Disconnect";
    static final String REVOKE = "Revoke this device";
    static final String CANCEL_SIGN_IN = "Cancel sign-in";
    static final String OPEN_AGAIN = "Open browser again";
    static final String COPY_DETAILS = "Copy details";
    static final String REVOKE_SELECTED = "Revoke selected";
    static final String THIS_MACHINE = " (this machine)";
    static final String DELETION_NOTE = "Deleting the account is not available in the app yet; ask the service's support.";
    static final List<String> ACCOUNT_ROWS = List.of("Account", "Type", "Your role", "Plan", "Status");
    static final List<String> CREDIT_CAPTIONS = List.of("Remaining", "Used", "In progress", "Generations", "Failures");
    static final List<String> DEVICE_ROWS = List.of("Name", "Credential");
    /**
     * A device's creation time, to the minute, so devices made the same day can be told apart. The zone is UTC and the
     * column header says so, which leaves the width to the Name column (owner, 2026-10-26).
     */
    private static final DateTimeFormatter CREATED = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC);
    /** A credential id longer than this is shown cut, with "…", and whole in the cell's tooltip. */
    static final int SHORT_ID = 8;
    static final int ID_COLUMN = 1;

    private final AppConfig config;
    private final Runnable saveConfig;
    private final CredentialStore store;
    private final CompilerAccountClient client;
    private final Consumer<URI> browser;

    private final JLabel connection = UiText.label(" ");
    private final JLabel storage = UiText.label(" ");
    private final JTextField address = new JTextField(30);
    /** The address as an editable drop-down: {@link #address} is its editor, the suggestions its items. */
    private final JComboBox<String> addressBox = new JComboBox<>();
    private final JLabel codeLabel = UiText.label(" ");
    private final JLabel codeHint = UiText.label(" ");
    private final JLabel codeExpiry = UiText.label(" ");
    private final JPanel signIn = new JPanel();
    private final JPanel accountPanel = new JPanel();
    /** Value labels of the Account rows, in {@link #ACCOUNT_ROWS} order. */
    private final JLabel[] accountValues = values(ACCOUNT_ROWS.size());
    /** Figures of the Credits row, in {@link #CREDIT_CAPTIONS} order; the first, Remaining, is set larger. */
    private final JLabel[] creditValues = values(CREDIT_CAPTIONS.size());
    private final JLabel resetLine = UiText.label(" ");
    /** Value labels of the This device rows, in {@link #DEVICE_ROWS} order. */
    private final JLabel[] deviceValues = values(DEVICE_ROWS.size());
    private final DefaultTableModel deviceModel = new DefaultTableModel(new Object[]{"Name", "ID", "Kind", "Created (UTC)"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable deviceTable = UiText.plainCells(new JTable(deviceModel) {
        @Override public String getToolTipText(java.awt.event.MouseEvent e) {
            int row = rowAtPoint(e.getPoint());
            int column = columnAtPoint(e.getPoint());
            if (row < 0 || row >= shown.size() || convertColumnIndexToModel(column) != ID_COLUMN) return null;
            return UiText.inert(shown.get(convertRowIndexToModel(row)).credentialId());   // the whole id, to tell rows apart
        }
    });
    private final JButton revokeSelected = UiText.button(REVOKE_SELECTED);
    private final JButton copyDetails = UiText.button(COPY_DETAILS);
    private final JLabel message = UiText.label(" ");
    private final JButton connect = UiText.button(CONNECT);
    private final JButton refresh = UiText.button(REFRESH);
    private final JButton disconnect = UiText.button(DISCONNECT);
    private final JButton revoke = UiText.button(REVOKE);
    private final JButton cancelSignIn = UiText.button(CANCEL_SIGN_IN);
    private final JButton openAgain = UiText.button(OPEN_AGAIN);
    private final JButton close = UiText.button("Close");

    private CompilerCredential connected;           // public identifiers only reach this class; never the bearer
    private DeviceStart pending;
    private AtomicBoolean cancelled = new AtomicBoolean();
    private Future<?> signInTask;
    private boolean busy;
    private Usage usage;                            // the last reads, for Copy details; null until read
    private Me me;
    private final List<Device> shown = new ArrayList<>();
    /** Asks the person to confirm (title, question); a frame test answers it without a nested modal. */
    java.util.function.BiPredicate<String, String> confirm = (title, question) -> JOptionPane.showConfirmDialog(this,
            UiText.inert(question), UiText.inert(title), JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.OK_OPTION;
    /** Where Copy details puts its text; a frame test reads it without the system clipboard. */
    Consumer<String> clipboard = text -> Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);

    CompilerAccountDialog(Window owner, AppConfig config, Runnable saveConfig, CredentialStore store,
                          CompilerAccountClient client, Consumer<URI> browser) {
        super(owner, TITLE, ModalityType.APPLICATION_MODAL);
        this.config = config;
        this.saveConfig = saveConfig;
        this.store = store;
        this.client = client;
        this.browser = browser;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setContentPane(content());
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent e) {
                cancelSignIn();
            }
        });
        render();
        pack();
        setMinimumSize(new Dimension(640, getPreferredSize().height));
        setLocationRelativeTo(owner);
        loadStored();
    }

    /** Opens the one owner of the compiler-account connection, with this machine's credential store. */
    public static void show(Window owner, AppConfig config, Runnable saveConfig) {
        new CompilerAccountDialog(owner, config, saveConfig, CredentialStores.forThisMachine(),
                CompilerAccountClient.standard(), CompilerAccountDialog::browse).setVisible(true);
    }

    private static void browse(URI uri) {
        try {
            java.awt.Desktop.getDesktop().browse(uri);
        } catch (Exception ignored) {
            // the address and code stay on screen, so the person can open it themselves
        }
    }

    private JPanel content() {
        int inset = 16;
        JPanel root = new JPanel(new BorderLayout(0, UiTheme.SPACE_MD));
        root.setBorder(BorderFactory.createEmptyBorder(inset, inset, inset, inset));

        // north: where the connection stands, the address, and the sign-in while one is in progress
        JPanel north = new JPanel();
        north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
        connection.setFont(connection.getFont().deriveFont(Font.BOLD));
        add(north, connection);
        north.add(Box.createVerticalStrut(UiTheme.SPACE_XS));
        UiTheme.status(storage);
        add(north, storage);
        north.add(Box.createVerticalStrut(UiTheme.SPACE_MD));
        JPanel addressRow = new JPanel(new BorderLayout(UiTheme.SPACE_SM, 0));
        addressRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        addressRow.add(muted(UiText.label("Service address")), BorderLayout.WEST);
        addressBox.setEditable(true);
        addressBox.setEditor(new BasicComboBoxEditor() {
            @Override protected JTextField createEditorComponent() {
                address.setBorder(null);
                return address;
            }
        });
        UiText.plainCells(addressBox);
        offer(suggestions(config, null));
        address.setText(config.compilerServiceUrl == null ? "" : config.compilerServiceUrl);
        address.putClientProperty("JTextField.placeholderText", "https://compiler.example.com");
        address.setToolTipText("The compiler service's base address; the list offers the addresses this machine connected with");
        addressRow.add(addressBox, BorderLayout.CENTER);
        addressRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, addressBox.getPreferredSize().height));
        north.add(addressRow);

        signIn.setLayout(new BoxLayout(signIn, BoxLayout.Y_AXIS));
        signIn.setAlignmentX(Component.LEFT_ALIGNMENT);
        signIn.setBorder(BorderFactory.createEmptyBorder(UiTheme.SPACE_MD, 0, 0, 0));
        codeLabel.setFont(codeLabel.getFont().deriveFont(Font.BOLD, codeLabel.getFont().getSize2D() * 1.6f));
        add(signIn, codeLabel);
        add(signIn, codeHint);
        UiTheme.status(codeExpiry);
        add(signIn, codeExpiry);
        signIn.add(Box.createVerticalStrut(UiTheme.SPACE_SM));
        JPanel signInButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        signInButtons.setAlignmentX(Component.LEFT_ALIGNMENT);
        openAgain.addActionListener(e -> {
            if (pending != null) openBrowser(pending);
        });
        cancelSignIn.addActionListener(e -> {
            cancelSignIn();
            message.setText("Sign-in cancelled. Nothing was stored.");
            render();
        });
        signInButtons.add(openAgain);
        signInButtons.add(Box.createHorizontalStrut(UiTheme.SPACE_SM));
        signInButtons.add(cancelSignIn);
        signIn.add(signInButtons);
        north.add(signIn);
        root.add(north, BorderLayout.NORTH);

        // centre: the account, in sections of aligned label/value rows; the device table takes any extra height
        accountPanel.setLayout(new GridBagLayout());
        Rows rows = new Rows(accountPanel);
        rows.heading("Account", false);
        for (int i = 0; i < ACCOUNT_ROWS.size(); i++) rows.pair(ACCOUNT_ROWS.get(i), accountValues[i]);
        rows.heading("Credits", true);
        rows.full(creditFigures(), 0);
        UiTheme.status(resetLine);
        rows.full(resetLine, 0);
        rows.heading("This device", true);
        for (int i = 0; i < DEVICE_ROWS.size(); i++) rows.pair(DEVICE_ROWS.get(i), deviceValues[i]);
        rows.heading("Your devices", true);
        deviceTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        deviceTable.setFillsViewportHeight(true);
        deviceTable.setShowVerticalLines(false);
        deviceTable.setShowHorizontalLines(true);
        deviceTable.setIntercellSpacing(new Dimension(0, 1));
        deviceTable.setRowHeight(deviceTable.getFontMetrics(deviceTable.getFont()).getHeight() + 10);
        deviceTable.getTableHeader().setReorderingAllowed(false);
        deviceTable.getTableHeader().setForeground(UiTheme.mutedForeground());
        deviceTable.getColumnModel().getColumn(0).setCellRenderer(new NameRenderer());
        // ID, Kind and Created are fixed at what their content needs; Name takes all the rest, so a machine's whole
        // name and its "(this machine)" tag fit at the default width (owner, 2026-10-26)
        java.awt.FontMetrics fm = deviceTable.getFontMetrics(deviceTable.getFont());
        fixColumn(1, fm.stringWidth("cr741a19…") + 12);
        fixColumn(2, fm.stringWidth("Automation") + 12);
        fixColumn(3, Math.max(fm.stringWidth("2026-10-26 11:10"), fm.stringWidth("Created (UTC)")) + 12);
        deviceTable.getColumnModel().getColumn(0).setPreferredWidth(360);
        deviceTable.getSelectionModel().addListSelectionListener(e -> render());
        JScrollPane devicesScroll = new JScrollPane(deviceTable);
        devicesScroll.setPreferredSize(new Dimension(520, deviceTable.getRowHeight() * 3 + deviceTable.getTableHeader().getPreferredSize().height + 4));
        devicesScroll.setMinimumSize(new Dimension(200, deviceTable.getRowHeight() * 2 + deviceTable.getTableHeader().getPreferredSize().height + 4));
        rows.full(devicesScroll, 1);
        JPanel deviceButtons = new JPanel(new BorderLayout(UiTheme.SPACE_MD, 0));
        revokeSelected.setToolTipText("Ask the service to stop accepting the selected device's credential");
        revokeSelected.addActionListener(e -> revokeSelected());
        deviceButtons.add(revokeSelected, BorderLayout.WEST);
        JLabel deletion = UiText.label(DELETION_NOTE);
        UiTheme.status(deletion);
        deviceButtons.add(deletion, BorderLayout.CENTER);
        rows.full(deviceButtons, 0);
        root.add(accountPanel, BorderLayout.CENTER);

        // south: the message line, then the actions — reads on the left, endings on the right, Close the default
        JPanel south = new JPanel(new BorderLayout(0, UiTheme.SPACE_SM));
        UiTheme.status(message);
        south.add(message, BorderLayout.NORTH);
        connect.addActionListener(e -> startSignIn());
        refresh.setToolTipText("Read the plan and credits again; nothing is changed at the service");
        refresh.addActionListener(e -> refreshUsage());
        copyDetails.setToolTipText("Copy a plain-text summary for a support request; it never includes the credential");
        copyDetails.addActionListener(e -> copyDetails());
        disconnect.setToolTipText("Delete the credential from this machine; the service still accepts it until revoked");
        disconnect.addActionListener(e -> disconnect());
        revoke.setToolTipText("Ask the service to stop accepting this device's credential, then delete it here");
        revoke.addActionListener(e -> revoke());
        close.addActionListener(e -> dispose());
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        for (JButton b : List.of(connect, refresh, copyDetails)) spaced(left, b, left.getComponentCount() > 0);
        for (JButton b : List.of(disconnect, revoke, close)) spaced(right, b, right.getComponentCount() > 0);
        JPanel buttons = new JPanel(new BorderLayout(UiTheme.SPACE_MD, 0));
        buttons.add(left, BorderLayout.WEST);
        buttons.add(right, BorderLayout.EAST);
        south.add(buttons, BorderLayout.SOUTH);
        root.add(south, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(close);
        return root;
    }

    private void fixColumn(int index, int width) {
        javax.swing.table.TableColumn c = deviceTable.getColumnModel().getColumn(index);
        c.setMinWidth(width);
        c.setMaxWidth(width);
        c.setPreferredWidth(width);
    }

    private static void spaced(JPanel group, JButton b, boolean gap) {
        if (gap) group.add(Box.createHorizontalStrut(UiTheme.SPACE_SM));
        group.add(b);
    }

    /** The Credits row: each figure over its muted caption, Remaining first and set larger. */
    private JPanel creditFigures() {
        JPanel row = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.WEST;
        for (int i = 0; i < CREDIT_CAPTIONS.size(); i++) {
            JLabel v = creditValues[i];
            Font f = v.getFont();
            v.setFont(i == 0 ? f.deriveFont(Font.BOLD, f.getSize2D() * 1.5f) : f.deriveFont(Font.BOLD));
            c.gridx = i;
            c.gridy = 0;
            c.anchor = GridBagConstraints.SOUTHWEST;
            c.insets = new Insets(0, 0, 0, UiTheme.SPACE_LG + (i == 0 ? UiTheme.SPACE_SM : 0));
            row.add(v, c);
            c.gridy = 1;
            c.anchor = GridBagConstraints.NORTHWEST;
            row.add(UiText.plain(muted(UiText.label(CREDIT_CAPTIONS.get(i)))), c);
        }
        c.gridx = CREDIT_CAPTIONS.size();
        c.weightx = 1;
        row.add(Box.createHorizontalGlue(), c);
        return row;
    }

    /** Sections of aligned rows: a small heading, then muted labels in one column and values in the next. */
    private static final class Rows {
        private final JPanel panel;
        private int y;

        Rows(JPanel panel) {
            this.panel = panel;
        }

        void heading(String text, boolean gapAbove) {
            JLabel l = UiText.label(text);
            l.setFont(l.getFont().deriveFont(Font.BOLD));
            JPanel h = new JPanel(new BorderLayout(UiTheme.SPACE_SM, 0));
            h.add(l, BorderLayout.WEST);
            JSeparator rule = new JSeparator();
            JPanel ruleBox = new JPanel(new GridBagLayout());   // centres the rule on the heading's text
            GridBagConstraints rc = new GridBagConstraints();
            rc.weightx = 1;
            rc.fill = GridBagConstraints.HORIZONTAL;
            ruleBox.add(rule, rc);
            h.add(ruleBox, BorderLayout.CENTER);
            GridBagConstraints c = constraints(0, 2);
            c.fill = GridBagConstraints.HORIZONTAL;
            c.insets = new Insets(gapAbove ? UiTheme.SPACE_MD + UiTheme.SPACE_XS : 0, 0, UiTheme.SPACE_SM, 0);
            panel.add(h, c);
        }

        void pair(String label, JLabel value) {
            GridBagConstraints c = constraints(0, 1);
            c.insets = new Insets(0, 0, UiTheme.SPACE_XS + 2, UiTheme.SPACE_LG);
            panel.add(muted(UiText.label(label)), c);
            c = constraints(1, 1);
            c.weightx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.insets = new Insets(0, 0, UiTheme.SPACE_XS + 2, 0);
            panel.add(value, c);
            y++;
        }

        void full(JComponent component, double weighty) {
            GridBagConstraints c = constraints(0, 2);
            c.weightx = 1;
            c.weighty = weighty;
            c.fill = weighty > 0 ? GridBagConstraints.BOTH : GridBagConstraints.HORIZONTAL;
            c.insets = new Insets(0, 0, UiTheme.SPACE_SM - 2, 0);
            panel.add(component, c);
        }

        private GridBagConstraints constraints(int x, int width) {
            GridBagConstraints c = new GridBagConstraints();
            c.gridx = x;
            c.gridy = width == 2 ? y++ : y;
            c.gridwidth = width;
            c.anchor = GridBagConstraints.WEST;
            return c;
        }
    }

    /** The Name column: this machine's row shows its name in bold and "(this machine)" as a muted tag. */
    private final class NameRenderer extends javax.swing.table.DefaultTableCellRenderer {
        // one line always: the name takes what is left and shortens with "…"; the tag keeps its width
        private final JPanel cell = new JPanel(new BorderLayout());
        private final JLabel name = UiText.label(" ");
        private final JLabel tag = UiText.label(" ");

        NameRenderer() {
            cell.add(name, BorderLayout.CENTER);
            cell.add(tag, BorderLayout.EAST);
            UiText.plain(this);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focus, int row, int column) {
            Component base = super.getTableCellRendererComponent(table, value, selected, focus, row, column);
            String text = String.valueOf(value);
            if (!text.endsWith(THIS_MACHINE)) return base;
            name.setText(UiText.inert(text.substring(0, text.length() - THIS_MACHINE.length())));
            name.setFont(table.getFont().deriveFont(Font.BOLD));
            name.setForeground(base.getForeground());
            tag.setText(UiText.inert(THIS_MACHINE));
            tag.setFont(table.getFont());
            tag.setForeground(selected ? base.getForeground() : UiTheme.mutedForeground());
            cell.setBackground(base.getBackground());
            cell.setOpaque(true);
            if (base instanceof JComponent j) cell.setBorder(j.getBorder());
            return cell;
        }
    }

    private static JLabel[] values(int n) {
        JLabel[] out = new JLabel[n];
        for (int i = 0; i < n; i++) out[i] = UiText.label(" ");
        return out;
    }

    private static JLabel muted(JLabel l) {
        l.setForeground(UiTheme.mutedForeground());
        return l;
    }

    private static void add(JComponent parent, JComponent child) {
        child.setAlignmentX(Component.LEFT_ALIGNMENT);
        parent.add(child);
    }

    // ---- state ---------------------------------------------------------------------------------------------------

    private void render() {
        boolean signingIn = pending != null;
        connection.setText(connectionLine(connected, signingIn));
        storage.setText("Credential kept in: " + store.description());
        // connected, the field names the service that ISSUED the credential (which usage and revoke go to), not the
        // configured address: the two differ when the setting was changed after connecting
        if (connected != null && !connected.service().equals(address.getText())) address.setText(connected.service());
        address.setEditable(connected == null && !signingIn && !busy);
        addressBox.setEnabled(address.isEditable());   // connected or signing in: the address is fixed, the list shut
        offer(suggestions(config, connected));
        // a read-only address holding the focus shows as a selected field; the focus belongs with the actions then
        if (!address.isEditable() && address.isFocusOwner()) close.requestFocusInWindow();
        boolean grew = signingIn && !signIn.isVisible();
        signIn.setVisible(signingIn);
        if (signingIn) {
            codeLabel.setText(pending.userCode());
            codeHint.setText(codeHint(pending));
            codeExpiry.setText(expiryHint(pending));
        }
        connect.setEnabled(connected == null && !signingIn && !busy);
        connect.setVisible(connected == null);
        for (JButton b : List.of(refresh, copyDetails, disconnect, revoke)) b.setVisible(connected != null);
        refresh.setEnabled(connected != null && !busy);
        disconnect.setEnabled(connected != null && !busy);
        revoke.setEnabled(connected != null && !busy);
        copyDetails.setEnabled(connected != null && !busy);
        revokeSelected.setEnabled(connected != null && !busy && deviceTable.getSelectedRow() >= 0);
        if (connected == null) {
            usage = null;
            me = null;
            if (deviceModel.getRowCount() > 0) showDevices(List.of());
        }
        boolean shows = connected != null;
        grew |= shows && !accountPanel.isVisible();
        boolean shrank = !shows && accountPanel.isVisible();
        accountPanel.setVisible(shows);
        if (shows) {
            setAll(accountValues, accountRows(me, usage, connected));
            setAll(creditValues, usage == null ? List.of() : creditFigures(usage));
            resetLine.setText(usage == null ? " " : UiText.inert(resetText(usage)));
            setAll(deviceValues, deviceRows(connected.credentialId(), thisDeviceName()));
        }
        if (isDisplayable()) {
            revalidate();
            if (grew || getPreferredSize().height > getHeight()) {   // the sign-in rows must not push the buttons off
                Dimension pref = getPreferredSize();
                setSize(Math.max(getWidth(), pref.width), Math.max(getHeight(), pref.height));
            } else if (shrank && !signingIn) {   // disconnected: no empty two-thirds; never while a sign-in is showing
                setSize(getWidth(), Math.max(getMinimumSize().height, getPreferredSize().height));
            }
            repaint();
        }
    }

    /** Make the drop-down offer {@code items}, leaving what the field shows as it was. */
    private void offer(List<String> items) {
        List<String> now = new ArrayList<>();
        for (int i = 0; i < addressBox.getItemCount(); i++) now.add(addressBox.getItemAt(i));
        if (now.equals(items)) return;
        String text = address.getText();
        addressBox.removeAllItems();
        for (String item : items) addressBox.addItem(item);
        address.setText(text);
    }

    private static void setAll(JLabel[] labels, List<String> values) {
        for (int i = 0; i < labels.length; i++) labels[i].setText(i < values.size() ? UiText.inert(values.get(i)) : " ");
    }

    private void loadStored() {
        busy = true;
        render();
        Background.run(() -> {
            try {
                return store.load();
            } catch (Exception e) {
                throw new IllegalStateException(e.getMessage());
            }
        }, (Optional<CompilerCredential> found) -> {
            busy = false;
            connected = found.orElse(null);
            render();
            if (connected != null) refreshUsage();
        }, err -> {
            busy = false;
            message.setText(UiText.inert("Could not read the stored credential: " + err.getMessage()));
            render();
        });
    }

    private void startSignIn() {
        String refusal = ServiceAddress.refusal(address.getText());
        if (refusal != null) {
            message.setText(refusal);
            return;
        }
        String base = ServiceAddress.require(address.getText());
        if (!base.equals(config.compilerServiceUrl)) {
            config.compilerServiceUrl = base;
            saveConfig.run();
        }
        message.setText("Asking the service for a sign-in code…");
        busy = true;
        render();
        AtomicBoolean flag = new AtomicBoolean();
        cancelled = flag;
        signInTask = Background.run(() -> {
            try {
                return client.start(base);
            } catch (CompilerAccountClient.AccountException e) {
                throw new IllegalStateException(e.getMessage());
            }
        }, started -> {
            busy = false;
            if (flag.get()) {
                render();
                return;
            }
            pending = started;
            message.setText("Waiting for you to approve the sign-in in the browser…");
            render();
            openBrowser(started);
            awaitApproval(started, flag);
        }, err -> {
            busy = false;
            message.setText(UiText.inert(err.getMessage()));
            render();
        });
    }

    private void awaitApproval(DeviceStart started, AtomicBoolean flag) {
        signInTask = Background.run(() -> client.await(started, flag::get), outcome -> {
            if (flag.get() || pending != started) return;   // cancelled or superseded: its answer is not news
            pending = null;
            if (outcome instanceof CompilerAccountClient.Approved a) {
                storeCredential(a.credential());
            } else {
                message.setText(outcomeText(outcome));
                render();
            }
        }, err -> {
            if (pending == started) pending = null;
            message.setText("The sign-in stopped unexpectedly.");
            render();
        });
    }

    private void storeCredential(CompilerCredential credential) {
        busy = true;
        message.setText("Approved. Storing the credential…");
        render();
        Background.run(() -> {
            try {
                store.save(credential);
                return credential;
            } catch (Exception e) {
                throw new IllegalStateException(e.getMessage());
            }
        }, saved -> {
            busy = false;
            connected = saved;
            // an address that worked is offered next time (machine tier; an address, never the credential)
            List<String> before = List.copyOf(config.recentCompilerServices);
            config.rememberCompilerService(saved.service());
            if (!before.equals(config.recentCompilerServices)) saveConfig.run();
            message.setText("Connected.");
            render();
            refreshUsage();
        }, err -> {
            busy = false;
            message.setText(UiText.inert("Approved, but the credential could not be stored: " + err.getMessage()
                    + ". Revoke the new device from your account page."));
            render();
        });
    }

    private void refreshUsage() {
        refreshUsage(null);
    }

    /** Read the account, credits and devices again; {@code done}, when given, is the message once they are read. */
    private void refreshUsage(String done) {
        CompilerCredential c = connected;
        if (c == null) return;
        busy = true;
        message.setText("Reading the account…");
        render();
        Background.run(() -> {
            Usage u;
            try {
                u = client.usage(c);
            } catch (CompilerAccountClient.AccountException e) {
                throw new IllegalStateException(e.getMessage());
            }
            // the account and device reads are extras: either failing still shows the credits, and says what is missing
            List<String> missing = new ArrayList<>();
            Me m = null;
            List<Device> d = null;
            try {
                m = client.me(c);
            } catch (CompilerAccountClient.AccountException e) {
                missing.add(e.getMessage());
            }
            try {
                d = client.devices(c);
            } catch (CompilerAccountClient.AccountException e) {
                missing.add(e.getMessage());
            }
            return new Read(u, m, d, String.join(". ", missing));
        }, (Read r) -> {
            busy = false;
            if (connected != c) return;   // disconnected or revoked while reading: the answer is not news
            usage = r.usage;
            me = r.me;
            if (r.devices != null) showDevices(r.devices);
            message.setText(!r.missing.isEmpty() ? UiText.inert(r.missing) : done == null ? " " : UiText.inert(done));
            render();
        }, err -> {
            busy = false;
            message.setText(UiText.inert(err.getMessage()));
            render();
        });
    }

    private record Read(Usage usage, Me me, List<Device> devices, String missing) { }

    private void showDevices(List<Device> devices) {
        shown.clear();
        shown.addAll(devices);
        deviceModel.setRowCount(0);
        String own = connected == null ? null : connected.credentialId();
        for (Device d : devices) deviceModel.addRow(deviceRow(d, d.credentialId().equals(own)));
    }

    private String thisDeviceName() {
        return shown.stream().filter(d -> d.credentialId().equals(connected.credentialId())).map(Device::name).findFirst().orElse(null);
    }

    private void copyDetails() {
        CompilerCredential c = connected;
        if (c == null) return;
        clipboard.accept(AccountDetails.text(c, me, usage, thisDeviceName(), ReleaseNotes.version(), Instant.now()));
        message.setText("Details copied to the clipboard. They never include the credential.");
    }

    private void revokeSelected() {
        CompilerCredential c = connected;
        int row = deviceTable.getSelectedRow();
        if (c == null || row < 0 || row >= shown.size()) return;
        Device d = shown.get(row);
        if (d.credentialId().equals(c.credentialId())) {   // this machine's own: exactly Revoke this device
            revoke();
            return;
        }
        String label = d.name().isEmpty() ? d.credentialId() : d.name();
        if (!confirm.test(REVOKE_SELECTED, "Ask the service to stop accepting the device \"" + label + "\" ("
                + d.credentialId() + ")?\nIt stops working at once; this machine stays connected.")) return;
        busy = true;
        message.setText("Revoking…");
        render();
        Background.run(() -> {
            try {
                return client.revoke(c, d.credentialId());
            } catch (CompilerAccountClient.AccountException e) {
                throw new IllegalStateException(e.getMessage());
            }
        }, revokedNow -> {
            busy = false;
            refreshUsage(revokedNow ? "Revoked \"" + label + "\"." : "The service no longer had \"" + label + "\".");
        }, err -> {
            busy = false;
            message.setText(UiText.inert(err.getMessage()));
            render();
        });
    }

    private void disconnect() {
        if (!confirm.test(DISCONNECT, "Delete the credential from this machine?\n"
                + "The service keeps accepting it until you revoke it.")) return;
        deleteLocal("Disconnected. The credential was deleted from this machine.");
    }

    private void revoke() {
        CompilerCredential c = connected;
        if (c == null) return;
        if (!confirm.test(REVOKE, "Ask the service to stop accepting this device, then delete its credential here?")) return;
        busy = true;
        message.setText("Revoking…");
        render();
        Background.run(() -> {
            try {
                return client.revoke(c);
            } catch (CompilerAccountClient.AccountException e) {
                throw new IllegalStateException(e.getMessage());
            }
        }, revokedNow -> {
            busy = false;
            deleteLocal(revokeText(revokedNow));
        }, err -> {
            busy = false;
            message.setText(UiText.inert(err.getMessage() + " Still connected."));
            render();
        });
    }

    private void deleteLocal(String done) {
        busy = true;
        render();
        Background.run(() -> {
            try {
                store.delete();
                return Boolean.TRUE;
            } catch (Exception e) {
                throw new IllegalStateException(e.getMessage());
            }
        }, ok -> {
            busy = false;
            connected = null;
            message.setText(done);
            render();
        }, err -> {
            busy = false;
            message.setText(UiText.inert("The credential could not be deleted: " + err.getMessage()));
            render();
        });
    }

    private void cancelSignIn() {
        cancelled.set(true);
        pending = null;
        busy = false;
        Future<?> task = signInTask;
        if (task != null) task.cancel(true);
    }

    private void openBrowser(DeviceStart started) {
        if (ServiceAddress.browsable(started.browserUri())) browser.accept(URI.create(started.browserUri()));
    }

    // ---- words: pure, so they are tested without a screen ----------------------------------------------------------

    /**
     * The address field's suggestions, most useful first and each once: the connected credential's issuer, the
     * addresses this machine connected with, then the configured one. Only usable addresses are offered.
     */
    static List<String> suggestions(AppConfig config, CompilerCredential connected) {
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
        if (connected != null) out.add(connected.service());
        out.addAll(config.recentCompilerServices);
        if (config.compilerServiceUrl != null) out.add(config.compilerServiceUrl);
        out.removeIf(a -> a == null || ServiceAddress.refusal(a) != null);
        return List.copyOf(out);
    }

    /** The host of a service address ("compiler.example.com"); the address itself if it has none. */
    static String host(String service) {
        try {
            String h = URI.create(service).getHost();
            return h == null || h.isBlank() ? service : h;
        } catch (IllegalArgumentException e) {
            return service;
        }
    }

    static String connectionLine(CompilerCredential connected, boolean signingIn) {
        // the host only: the account id is the Account section's first row, and the full address is in the field
        if (connected != null) return "Connected to " + host(connected.service());
        if (signingIn) return "Signing in…";
        return "Not connected.";
    }

    static String codeHint(DeviceStart s) {
        return "Enter this code at " + s.verificationUri();
    }

    static String expiryHint(DeviceStart s) {
        long minutes = Math.max(1, (s.expiresInSeconds() + 59) / 60);
        return "The browser was asked to open that page. The code expires in about " + minutes
                + (minutes == 1 ? " minute." : " minutes.");
    }

    static String outcomeText(Outcome outcome) {
        if (outcome instanceof CompilerAccountClient.Approved) return "Connected.";
        if (outcome instanceof CompilerAccountClient.Denied) return "The sign-in was denied in the browser. Nothing was stored.";
        if (outcome instanceof CompilerAccountClient.Expired) return "The code expired before it was approved. Connect again for a new code.";
        if (outcome instanceof CompilerAccountClient.Cancelled) return "Sign-in cancelled. Nothing was stored.";
        if (outcome instanceof CompilerAccountClient.Failed f) return f.reason() + ". Nothing was stored.";
        return "";
    }

    /** The Account rows' values, in {@link #ACCOUNT_ROWS} order; the plan rows are left out until usage is read. */
    static List<String> accountRows(Me me, Usage u, CompilerCredential c) {
        String account = u != null ? u.accountId() : me != null ? me.selectedAccount() : c.accountId();
        List<String> out = new ArrayList<>(List.of(account, me == null ? "Not read" : me.type(),
                me == null || me.role() == null ? "Unknown" : me.role()));
        if (u != null) {
            out.add(u.plan());
            out.add(u.active() ? "Active" : "Inactive");
        }
        return out;
    }

    /** The Credits figures, in {@link #CREDIT_CAPTIONS} order. */
    static List<String> creditFigures(Usage u) {
        return List.of(String.valueOf(u.available()), String.valueOf(u.consumed()), String.valueOf(u.reserved()),
                String.valueOf(u.generations()), String.valueOf(u.failures()));
    }

    /** A plan without a next reset is a one-time allowance: said as such, never a guessed date. */
    static String resetText(Usage u) {
        return u.nextReset() == null ? "One-time allowance (no reset)" : "Next reset: " + u.nextReset();
    }

    /** The This device rows' values, in {@link #DEVICE_ROWS} order. */
    static List<String> deviceRows(String credentialId, String name) {
        return List.of(name == null || name.isEmpty() ? "Not in the device list" : name, credentialId);
    }

    static Object[] deviceRow(Device d, boolean thisMachine) {
        String name = (d.name().isEmpty() ? d.credentialId() : d.name()) + (thisMachine ? THIS_MACHINE : "");
        return new Object[]{name, shortId(d.credentialId()), titleCase(d.kind()),
                d.createdAt() > 0 ? CREATED.format(Instant.ofEpochMilli(d.createdAt())) : ""};
    }

    /** The id as the table shows it: whole when short, otherwise its first {@link #SHORT_ID} characters and "…". */
    static String shortId(String id) {
        return id.length() > SHORT_ID ? id.substring(0, SHORT_ID) + "…" : id;
    }

    /** "DEVICE" reads as "Device", "AUTOMATION_KEY" as "Automation key": the service's kind, without shouting. */
    static String titleCase(String kind) {
        if (kind == null || kind.isBlank()) return "";
        String words = kind.strip().replace('_', ' ').toLowerCase(java.util.Locale.ROOT);
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    static String revokeText(boolean revokedNow) {
        return revokedNow
                ? "Revoked. The service no longer accepts this device, and its credential was deleted here."
                : "The service already did not accept this device's credential; it was deleted here.";
    }
}
