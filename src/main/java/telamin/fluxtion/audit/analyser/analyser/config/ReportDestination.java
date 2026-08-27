package telamin.fluxtion.audit.analyser.analyser.config;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * M38.5 (spec-portable-context D-C6) — WHERE an investigation report is published: a bucket, a directory,
 * a ticket system's base URL. <b>Never how to authenticate there.</b>
 *
 * <p>The analyser does not publish. It states the place — in {@code context.reportDestinations} and the
 * Project panel — so the agent that renders a report knows where it belongs and publishes with its own
 * credentials, from the environment it already runs in. That keeps M18's closure intact (the analyser
 * gains no server-side code) and keeps the export confinement intact (file-writing verbs stay inside the
 * exchange directory). A destination is tier-1 fact: inert, shareable, and gated so that nothing shaped
 * like a secret can be stored as one.
 *
 * @param name     short handle: {@code shared-drive}, {@code incident-bucket}
 * @param location {@code s3://bucket/prefix}, {@code https://host/path}, or a directory path
 */
public record ReportDestination(String name, String location) {

    public enum Kind { S3, URL, DIRECTORY }

    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]{0,39}");
    private static final Pattern S3 = Pattern.compile("s3://[a-z0-9][a-z0-9.-]{1,61}[a-z0-9](/[A-Za-z0-9._/-]*)?");
    private static final Pattern URL = Pattern.compile("https?://[A-Za-z0-9.-]+(:[0-9]{1,5})?(/[A-Za-z0-9._~/-]*)?");
    private static final Pattern DIR_SEGMENT = Pattern.compile("[A-Za-z0-9._ -]+");
    /** Shapes a credential takes. A blocklist on top of the allowlists above, so the refusal can NAME the reason. */
    private static final Pattern CREDENTIAL = Pattern.compile(
            "(?i)(AKIA[0-9A-Z]{16}|(token|secret|password|passwd|api[_-]?key|authorization|bearer|sig(nature)?)\\s*[=:])");
    public static final int MAX_LOCATION = 300;
    /**
     * Review F1 (2026-08-27): a webhook's secret is its PATH — anyone holding the URL can post to the channel —
     * so no inspection of user info, query or fragment separates the place from the credential. The realistic
     * shapes are refused by host, with the reason named, because "publish the incident report to the team's
     * channel" is the first thing a support team would paste here. Everything this list does not know is
     * bounded by the category defaulting OFF (D-C8, the LLM precedent).
     */
    private static final Pattern WEBHOOK = Pattern.compile(
            "(?i)^https?://(hooks\\.slack\\.com/|[a-z0-9.-]*\\.?webhook\\.office\\.com/|outlook\\.office\\.com/webhook/"
            + "|discord(app)?\\.com/api/webhooks/|hooks\\.zapier\\.com/|api\\.telegram\\.org/bot|chat\\.googleapis\\.com/v1/spaces/)");

    public ReportDestination {
        name = name == null ? "" : name.trim();
        location = location == null ? "" : location.trim();
    }

    public Kind kind() {
        if (location.startsWith("s3://")) return Kind.S3;
        if (location.startsWith("http://") || location.startsWith("https://")) return Kind.URL;
        return Kind.DIRECTORY;
    }

    /** Why this destination may NOT be stored, or empty when it is a plain place. */
    public static Optional<String> refuse(ReportDestination d) {
        if (d == null) return Optional.of("no destination");
        if (!NAME.matcher(d.name()).matches()) {
            return Optional.of("destination name must be 1–40 letters, digits, '-' or '_' (got '" + d.name() + "')");
        }
        String loc = d.location();
        String label = "destination '" + d.name() + "'";
        if (loc.isBlank()) return Optional.of(label + ": no location");
        if (loc.length() > MAX_LOCATION) return Optional.of(label + ": location longer than " + MAX_LOCATION + " characters");
        if (loc.chars().anyMatch(ch -> ch == '\n' || ch == '\r' || ch == '\t')) return Optional.of(label + ": location must be one line");
        if (CREDENTIAL.matcher(loc).find()) {
            return Optional.of(label + ": looks like it carries a CREDENTIAL — a destination is a place, never how to "
                    + "authenticate there; credentials come from the environment the publisher runs in");
        }
        switch (d.kind()) {
            case S3 -> {
                if (!S3.matcher(loc).matches()) return Optional.of(label + ": '" + loc + "' is not s3://bucket[/prefix]");
            }
            case URL -> {
                if (WEBHOOK.matcher(loc).find()) {
                    return Optional.of(label + ": a webhook URL is a CREDENTIAL in path form — anyone holding it can post "
                            + "there — so it is not a place; publish through the agent's own configured integration instead");
                }
                if (loc.contains("@")) return Optional.of(label + ": a URL with user info is a credential, not a place");
                if (loc.contains("?") || loc.contains("#")) {
                    return Optional.of(label + ": a URL with a query or fragment is refused — tokens travel there; give the base URL");
                }
                if (!URL.matcher(loc).matches()) return Optional.of(label + ": '" + loc + "' is not a plain http(s) base URL");
            }
            case DIRECTORY -> {
                String p = loc.startsWith("~") ? loc.substring(1) : loc;
                if (p.matches("^[A-Za-z]:.*")) p = p.substring(2);
                for (String seg : p.split("[/\\\\]")) {
                    if (seg.isEmpty()) continue;
                    if (seg.equals("..")) return Optional.of(label + ": '..' in a directory path");
                    if (!DIR_SEGMENT.matcher(seg).matches()) {
                        return Optional.of(label + ": '" + loc + "' is not a plain directory path — quotes, '$', ';', '|', '&', "
                                + "'<', '>', '*', '?' and backticks are refused");
                    }
                }
            }
        }
        return Optional.empty();
    }
}
