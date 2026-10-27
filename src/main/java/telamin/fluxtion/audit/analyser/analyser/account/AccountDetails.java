package telamin.fluxtion.audit.analyser.analyser.account;

import telamin.fluxtion.audit.analyser.analyser.account.CompilerAccountClient.Balance;
import telamin.fluxtion.audit.analyser.analyser.account.CompilerAccountClient.Capabilities;
import telamin.fluxtion.audit.analyser.analyser.account.CompilerAccountClient.Me;
import telamin.fluxtion.audit.analyser.analyser.account.CompilerAccountClient.Usage;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * CA-1: the plain-text summary *Copy details* puts on the clipboard for a support request. It names the service, the
 * account, the compile and interpret credits, the plan and its limits, and this device by their public identifiers
 * only; the credential's secret is never part of it. It lives in this package so the one place that could reach the
 * bearer is the place a test pins. The plan's words ({@link #planLimits}) are shared with the dialog, so the panel and
 * the summary cannot say different things.
 */
public final class AccountDetails {

    private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'").withZone(ZoneOffset.UTC);

    private AccountDetails() {
    }

    /** {@code me} and {@code usage} may be null when they could not be read; their lines then say so. */
    public static String text(CompilerCredential credential, Me me, Usage usage, String deviceName, String version, Instant now) {
        StringBuilder b = new StringBuilder("Fluxtion analyser — compiler account\n");
        line(b, "Service", credential.service());
        line(b, "Account", usage != null ? usage.accountId() : me != null ? me.selectedAccount() : credential.accountId());
        line(b, "Type", me == null ? "not read" : me.type());
        line(b, "Role", me == null || me.role() == null ? "not read" : me.role());
        if (usage == null) {
            line(b, "Usage", "not read");
        } else {
            line(b, "Plan", usage.planName());
            line(b, "Active", usage.active() ? "yes" : "no");
            balance(b, "Compile", usage.compile());
            balance(b, "Interpret", usage.interpret());
            Capabilities caps = usage.capabilities();
            if (caps == null) {
                line(b, "Plan details", "unavailable from this service");
            } else {
                List<String> values = planLimits(caps);
                for (int i = 0; i < PLAN_LIMITS.size(); i++) line(b, PLAN_LIMITS.get(i), values.get(i));
                line(b, "Rate per minute", "compile " + stated(caps.compilePerMinute()) + ", interpret "
                        + stated(caps.interpretPerMinute()));
                if (!caps.overrides().isEmpty()) {
                    List<String> o = new ArrayList<>();
                    caps.overrides().forEach((k, v) -> o.add(k + "=" + v));
                    line(b, "Account overrides", String.join(", ", o));
                }
            }
            line(b, "Reset", usage.nextReset() == null ? "one-time allowance (no reset)" : usage.nextReset());
        }
        line(b, "This device", credential.credentialId());
        line(b, "Device name", deviceName == null || deviceName.isBlank() ? "not listed" : deviceName);
        line(b, "Analyser version", version);
        line(b, "Time", UTC.format(now));
        return b.toString();
    }

    /** What an older service, one without plans, leaves the panel and the summary to say about the plan's limits. */
    public static final String PLAN_DETAILS_UNAVAILABLE = "Plan details unavailable from this service";
    /** What a service without a separate interpret allowance leaves the Interpret figures to say. */
    public static final String INTERPRET_NOT_REPORTED = "Not reported by this service";
    /**
     * The plan's key limits, as the panel's rows and the summary's lines name them. "Max inputs" is event types plus
     * exported methods; an older service's {@code maxEventTypes} is shown under it, never as a row of its own.
     */
    public static final List<String> PLAN_LIMITS = List.of("Max nodes", "Max inputs", "Max exported interfaces", "Languages",
            "WASM", "Assurance");

    /**
     * The values of {@link #PLAN_LIMITS}, read from the service's capability profile only; a limit it did not state is
     * "Not stated", and one an operator set for this account is marked as such.
     */
    public static List<String> planLimits(Capabilities caps) {
        List<String> targets = caps.targets() == null ? List.of() : caps.targets();
        return List.of(
                overridden(stated(caps.maxNodes()), caps, "maxNodes", String.valueOf(caps.maxNodes())),
                overridden(stated(caps.maxInputs()), caps, caps.overrides().containsKey("maxInputs") ? "maxInputs"
                        : "maxEventTypes", String.valueOf(caps.maxInputs())),
                overridden(stated(caps.maxExportedInterfaces()), caps, "maxExportedInterfaces",
                        String.valueOf(caps.maxExportedInterfaces())),
                overridden(targets.isEmpty() ? "Not stated"
                                : String.join(", ", targets.stream().map(AccountDetails::language).toList()),
                        caps, "targets", String.join(",", targets)),
                overridden(caps.wasm() == null ? "Not stated" : caps.wasm() ? "Yes" : "No", caps, "wasm",
                        String.valueOf(caps.wasm())),
                overridden(assurance(caps.assurance()), caps, "assurance", String.valueOf(caps.assurance())));
    }

    /** A generator id as a language: {@code java} is Java, {@code cpp} C++; any other id is shown as the service gave it. */
    public static String language(String target) {
        return switch (target.toLowerCase(Locale.ROOT)) {
            case "java" -> "Java";
            case "cpp" -> "C++";
            default -> target;
        };
    }

    /** The software assurance level in words: none, provenance, or provenance with a verification report. */
    public static String assurance(String level) {
        if (level == null) return "Not stated";
        return switch (level) {
            case "none" -> "None";
            case "provenance" -> "Provenance";
            case "verification" -> "Provenance + verification report";
            default -> level;
        };
    }

    private static String stated(Long n) {
        return n == null ? "Not stated" : String.valueOf(n);
    }

    /**
     * Marks a limit an operator set for this account. The service keeps an override on any plan but applies it only
     * where the plan allows overrides, and reports the limit in force; so the mark is given only when the limit in
     * force IS the override, never merely because one is on record.
     */
    private static String overridden(String value, Capabilities caps, String capability, String wire) {
        String o = caps.overrides().get(capability);
        return o != null && o.replace(" ", "").equalsIgnoreCase(wire) ? value + " (set for this account)" : value;
    }

    private static void balance(StringBuilder b, String kind, Balance k) {
        if (k == null) {
            line(b, kind, INTERPRET_NOT_REPORTED.toLowerCase(Locale.ROOT));
            return;
        }
        line(b, kind + " remaining", String.valueOf(k.available()));
        line(b, kind + " used", String.valueOf(k.consumed()));
        line(b, kind + " in progress", String.valueOf(k.reserved()));
        line(b, kind + " completed", String.valueOf(k.completed()));
        line(b, kind + " failures", String.valueOf(k.failures()));
    }

    private static void line(StringBuilder b, String label, String value) {
        b.append(label).append(": ").append(value).append('\n');
    }
}
