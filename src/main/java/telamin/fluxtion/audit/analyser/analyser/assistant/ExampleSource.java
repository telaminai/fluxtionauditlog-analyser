package telamin.fluxtion.audit.analyser.analyser.assistant;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * M74 P2b (owner request 2026-10-17): where a prompt pack's <b>example</b> entry downloads from — an archive (zip) of one
 * PINNED commit, over https, from an allowed host. PURE: a URL (and an optional SHA-256) in, a source or one named
 * refusal out. No git binary, no clone, no hooks, no submodules: the analyser downloads one archive and unpacks it.
 *
 * <p>Accepted shapes (the commit is always 40 hexadecimal characters; a branch, a tag or any shorter name is a MOVING
 * reference and is refused):
 * <pre>
 *   https://github.com/OWNER/REPO/archive/COMMIT.zip
 *   https://codeload.github.com/OWNER/REPO/zip/COMMIT
 *   https://gitlab.com/OWNER/REPO/-/archive/COMMIT/REPO-COMMIT.zip
 * </pre>
 * No user information, port, query or fragment. A publisher other than the Telamin GitHub organisation is marked
 * <i>third-party</i>; nothing here, or anywhere, marks an example "validated" or "approved" — that needs a
 * Telamin-published catalogue, a later owner item ({@link #publisherVerdict} is its seam).
 *
 * @param sha256 the entry's integrity digest (64 lowercase hex), or null when the entry has none
 */
public record ExampleSource(String url, String host, String owner, String repo, String commit, String sha256) {

    /**
     * The hosts an example may come from, and a redirect may lead to. A constant now; from P4 an administrator's managed
     * policy narrows it (the policy seam: replace this list's reader, never widen it from a pack).
     */
    public static final List<String> ALLOWED_HOSTS = List.of("github.com", "codeload.github.com", "gitlab.com");

    /** The publisher whose examples are not marked third-party: the Telamin GitHub organisation. */
    public static final String TELAMIN_ORG = "telaminai";

    /** The largest archive fetched (the template archive's own expanded caps apply on unpack). */
    public static final long MAX_ARCHIVE_BYTES = 64L * 1024 * 1024;

    public enum Refusal {
        NOT_HTTPS("only an https address is downloaded"),
        HOST_NOT_ALLOWED("the host is not one the analyser downloads examples from (" + String.join(", ", ALLOWED_HOSTS) + ")"),
        USER_INFO_OR_PORT("the address carries a user name, password or port"),
        QUERY_OR_FRAGMENT("the address carries a query or fragment"),
        NOT_AN_ARCHIVE("the address is not a commit archive (…/archive/<commit>.zip)"),
        MOVING_REF("the address names a branch or tag, which can change: pin a 40-character commit"),
        BAD_DIGEST("the sha256 must be 64 hexadecimal characters");

        public final String words;

        Refusal(String words) {
            this.words = words;
        }
    }

    /** A source, or why not — exactly one is non-null. */
    public record Parsed(ExampleSource source, Refusal refusal) {
    }

    private static final String HEX40 = "[0-9a-fA-F]{40}";
    private static final String SEG = "[A-Za-z0-9._-]{1,100}";
    private static final Pattern GITHUB = Pattern.compile("^/(" + SEG + ")/(" + SEG + ")/archive/(.+)\\.zip$");
    private static final Pattern CODELOAD = Pattern.compile("^/(" + SEG + ")/(" + SEG + ")/zip/(.+)$");
    private static final Pattern GITLAB = Pattern.compile("^/(" + SEG + ")/(" + SEG + ")/-/archive/([^/]+)/([^/]+)\\.zip$");
    private static final Pattern SHA256 = Pattern.compile("^[0-9a-fA-F]{64}$");

    public static Parsed parse(String raw, String sha256) {
        if (sha256 != null && !SHA256.matcher(sha256).matches()) return refuse(Refusal.BAD_DIGEST);
        URI uri;
        try {
            uri = URI.create(raw == null ? "" : raw.strip());
        } catch (IllegalArgumentException e) {
            return refuse(Refusal.NOT_AN_ARCHIVE);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) return refuse(Refusal.NOT_HTTPS);
        String host = uri.getHost() == null ? null : uri.getHost().toLowerCase(Locale.ROOT);
        if (host == null || !ALLOWED_HOSTS.contains(host)) return refuse(Refusal.HOST_NOT_ALLOWED);
        if (uri.getRawUserInfo() != null || uri.getPort() != -1) return refuse(Refusal.USER_INFO_OR_PORT);
        if (uri.getRawQuery() != null || uri.getRawFragment() != null) return refuse(Refusal.QUERY_OR_FRAGMENT);
        String path = uri.getRawPath() == null ? "" : uri.getRawPath();
        Matcher m;
        String owner;
        String repo;
        String ref;
        if (host.equals("github.com") && (m = GITHUB.matcher(path)).matches()) {
            owner = m.group(1);
            repo = m.group(2);
            ref = m.group(3);
        } else if (host.equals("codeload.github.com") && (m = CODELOAD.matcher(path)).matches()) {
            owner = m.group(1);
            repo = m.group(2);
            ref = m.group(3);
        } else if (host.equals("gitlab.com") && (m = GITLAB.matcher(path)).matches()) {
            owner = m.group(1);
            repo = m.group(2);
            ref = m.group(3);
            if (!m.group(4).equals(repo + "-" + ref)) return refuse(Refusal.NOT_AN_ARCHIVE);
        } else {
            return refuse(Refusal.NOT_AN_ARCHIVE);
        }
        if (!ref.matches(HEX40)) return refuse(Refusal.MOVING_REF);
        return new Parsed(new ExampleSource(uri.toString(), host, owner, repo, ref.toLowerCase(Locale.ROOT),
                sha256 == null ? null : sha256.toLowerCase(Locale.ROOT)), null);
    }

    /** The first seven characters of the commit, as git shows it. */
    public String shortCommit() {
        return commit.substring(0, 7);
    }

    /** "github.com / OWNER / REPO @ 1a2b3c4" — what the confirmation leads with. */
    public String publisher() {
        return host + " / " + owner + " / " + repo + " @ " + shortCommit();
    }

    /** Anything not from the Telamin GitHub organisation is third-party. */
    public boolean thirdParty() {
        return !(host.equals("github.com") || host.equals("codeload.github.com")) || !owner.equalsIgnoreCase(TELAMIN_ORG);
    }

    /**
     * The seam for a later owner item: a Telamin-published catalogue that could say an example was checked. Until it
     * exists the answer is only first-party or third-party; nothing is ever called "validated" or "approved".
     */
    public String publisherVerdict() {
        return thirdParty() ? "third-party" : "Telamin";
    }

    /**
     * Whether {@code location} (a redirect) may be followed for this source: https, an allowed host, no user info or
     * port, and the same pinned commit in its path. GitHub answers {@code /archive/<commit>.zip} with a redirect to
     * codeload; nothing else is followed.
     */
    public boolean mayRedirectTo(URI location) {
        if (location == null || !"https".equalsIgnoreCase(location.getScheme())) return false;
        String h = location.getHost() == null ? null : location.getHost().toLowerCase(Locale.ROOT);
        if (h == null || !ALLOWED_HOSTS.contains(h)) return false;
        if (location.getRawUserInfo() != null || location.getPort() != -1) return false;
        String p = location.getRawPath() == null ? "" : location.getRawPath().toLowerCase(Locale.ROOT);
        return p.contains(commit);
    }

    /** The hosts' set, for a redirect check that has no source at hand. */
    public static Set<String> allowedHosts() {
        return Set.copyOf(ALLOWED_HOSTS);
    }

    private static Parsed refuse(Refusal r) {
        return new Parsed(null, r);
    }

    @Override
    public String toString() {
        return "ExampleSource[" + publisher() + "]";
    }
}
