package telamin.fluxtion.audit.analyser.analyser.account;

import telamin.fluxtion.audit.analyser.analyser.account.CompilerAccountClient.Me;
import telamin.fluxtion.audit.analyser.analyser.account.CompilerAccountClient.Usage;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * CA-1: the plain-text summary *Copy details* puts on the clipboard for a support request. It names the service, the
 * account, the credits and this device by their public identifiers only; the credential's secret is never part of it.
 * It lives in this package so the one place that could reach the bearer is the place a test pins.
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
            line(b, "Plan", usage.plan());
            line(b, "Active", usage.active() ? "yes" : "no");
            line(b, "Remaining", String.valueOf(usage.available()));
            line(b, "Used", String.valueOf(usage.consumed()));
            line(b, "In progress", String.valueOf(usage.reserved()));
            line(b, "Generations", String.valueOf(usage.generations()));
            line(b, "Failures", String.valueOf(usage.failures()));
            line(b, "Reset", usage.nextReset() == null ? "one-time allowance (no reset)" : usage.nextReset());
        }
        line(b, "This device", credential.credentialId());
        line(b, "Device name", deviceName == null || deviceName.isBlank() ? "not listed" : deviceName);
        line(b, "Analyser version", version);
        line(b, "Time", UTC.format(now));
        return b.toString();
    }

    private static void line(StringBuilder b, String label, String value) {
        b.append(label).append(": ").append(value).append('\n');
    }
}
