package telamin.fluxtion.audit.analyser.analyser.ui;

import javax.swing.JOptionPane;
import javax.swing.JTextArea;
import java.awt.Component;
import java.net.IDN;
import java.net.URI;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * M74 P2 (spec §5.9, r8 F16): a link in Claude's words opens only on a click, only after a confirmation that shows the
 * full URL — default No — and only for {@code http} and {@code https}. The confirmation puts the scheme and HOST on a
 * line of their own, the host in its ASCII (punycode) form, and the URL with bidirectional and format characters as
 * marks, so a homograph or a reversed string cannot pass as another address. {@code file:}, {@code javascript:},
 * {@code data:} and every other scheme are shown and never opened.
 */
public final class LinkConfirm {

    private LinkConfirm() {
    }

    /** The scheme and host as the confirmation shows them: {@code https://xn--pple-43d.example:8443}, or null. */
    public static String origin(String url) {
        if (url == null) return null;
        String u = url.strip();
        int sep = u.indexOf("://");
        if (sep <= 0) return null;
        String scheme = u.substring(0, sep).toLowerCase(Locale.ROOT);
        String rest = u.substring(sep + 3);
        int end = rest.length();
        for (char c : new char[]{'/', '?', '#'}) {
            int at = rest.indexOf(c);
            if (at >= 0 && at < end) end = at;
        }
        String authority = rest.substring(0, end);
        int at = authority.lastIndexOf('@');
        if (at >= 0) authority = authority.substring(at + 1);         // user info is never the host
        String port = "";
        int colon = authority.lastIndexOf(':');
        if (colon > 0 && authority.indexOf(']') < colon) {
            port = authority.substring(colon);
            authority = authority.substring(0, colon);
        }
        String host;
        try {
            host = IDN.toASCII(authority, IDN.ALLOW_UNASSIGNED).toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            host = VisibleMarks.show(authority);
        }
        return scheme + "://" + host + VisibleMarks.show(port);
    }

    /** The confirmation's words (pure, for the test): the origin on its own line, then the URL with marks. */
    public static String message(String url) {
        String origin = origin(url);
        return "Open this link in your browser?\n\nScheme and host:\n    " + (origin == null ? "(none)" : origin)
                + "\n\nThe full address, as written (hidden characters shown as marks):\n    " + VisibleMarks.show(url)
                + "\n\nThe address was written by the model, not by the analyser.";
    }

    /** Why a link is not opened at all, or null when it may be offered. */
    public static String refusal(String url) {
        if (SafeMarkdown.openable(url)) {
            try {
                URI.create(url.strip());
                return null;
            } catch (IllegalArgumentException e) {
                return "this link is not a well-formed address, so the analyser does not open it";
            }
        }
        int colon = url == null ? -1 : url.indexOf(':');
        String scheme = colon > 0 ? url.substring(0, colon) : "(none)";
        return "the analyser opens only http and https links; this one's scheme is " + VisibleMarks.show(scheme);
    }

    /** How the confirmation is asked — a seam for the tests. */
    public interface Asker {
        boolean confirm(Component parent, String message);
    }

    /** The default asker: a plain-text area (never HTML), default button No. */
    public static final Asker DIALOG = (parent, message) -> {
        JTextArea text = new JTextArea(message);
        text.setEditable(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setColumns(60);
        text.setOpaque(false);
        Object[] options = {"Open in browser", "No"};
        int choice = JOptionPane.showOptionDialog(parent, text, "Open a link from Claude's answer?",
                JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[1]);
        return choice == 0;
    };

    /**
     * A click on a link: refused schemes say why; otherwise the confirmation, and only on its yes is {@code open} called.
     *
     * @return what happened, in words for the status line
     */
    public static String click(Component parent, String url, Asker asker, Consumer<URI> open) {
        String refused = refusal(url);
        if (refused != null) return refused;
        if (!asker.confirm(parent, message(url))) return "the link was not opened";
        open.accept(URI.create(url.strip()));
        return "opened in your browser";
    }
}
