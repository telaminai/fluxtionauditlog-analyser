package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.account.AccountDetails;
import telamin.fluxtion.audit.analyser.analyser.account.CompilerAccountClient;
import telamin.fluxtion.audit.analyser.analyser.account.CompilerAccountClient.Usage;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** CA-1: the compiler-account dialog's words, decided without a screen. */
class CompilerAccountWordsTest {

    @Test
    void aPlanWithoutANextResetShowsNoResetLine_ratherThanAGuess() {
        Usage none = new Usage("acct-demo", "free", true, 120, 30, 2, 3, 1, null);
        assertEquals("One-time allowance (no reset)", CompilerAccountDialog.resetText(none), "a one-time allowance, never a date");
        assertEquals(List.of("Remaining", "Used", "In progress", "Completed", "Failures"), CompilerAccountDialog.CREDIT_CAPTIONS);
        assertEquals(List.of("120", "30", "2", "3", "1"), CompilerAccountDialog.creditFigures(none.compile()));
        Usage monthly = new Usage("acct-demo", "team", true, 4000, 0, 0, 0, 0, "2026-11-01T00:00:00Z");
        assertEquals("Next reset: 2026-11-01T00:00:00Z", CompilerAccountDialog.resetText(monthly));
        assertEquals(List.of("a", "Personal", "member", "free", "Inactive"), CompilerAccountDialog.accountRows(
                new CompilerAccountClient.Me("a", true, "member"), new Usage("a", "free", false, 0, 0, 0, 0, 0, null), null));
    }

    /** Design 03 §2/§5: the plan's key limits in words, from the service's profile only. */
    @Test
    void thePlansLimitsAreSaidFromTheProfile_andAnOverrideIsMarkedOnlyWhenItIsInForce() {
        assertEquals(List.of("Max nodes", "Max event types", "Languages", "WASM", "Assurance"), CompilerAccountDialog.PLAN_ROWS);
        CompilerAccountClient.Capabilities free = new CompilerAccountClient.Capabilities("free", 20L, 5L, List.of("java"),
                false, "none", 10L, 60L, java.util.Map.of());
        assertEquals(List.of("20", "5", "Java", "No", "None"), AccountDetails.planLimits(free));
        CompilerAccountClient.Capabilities enterprise = new CompilerAccountClient.Capabilities("enterprise", 5000L, 50L,
                List.of("java", "cpp", "rust"), true, "verification", 30L, 300L, java.util.Map.of("maxNodes", "5000"));
        assertEquals(List.of("5000 (set for this account)", "50", "Java, C++, rust", "Yes", "Provenance + verification report"),
                AccountDetails.planLimits(enterprise));
        // the service keeps an override on any plan but applies it only on Enterprise: on Free the limit in force is
        // the plan's, and saying "set for this account" beside it would be false
        CompilerAccountClient.Capabilities keptNotApplied = new CompilerAccountClient.Capabilities("free", 20L, 5L,
                List.of("java"), false, "none", 10L, 60L, java.util.Map.of("maxNodes", "5000"));
        assertEquals("20", AccountDetails.planLimits(keptNotApplied).get(0));
        CompilerAccountClient.Capabilities unstated = new CompilerAccountClient.Capabilities(null, null, null, null, null,
                null, null, null, java.util.Map.of());
        assertEquals(List.of("Not stated", "Not stated", "Not stated", "Not stated", "Not stated"), AccountDetails.planLimits(unstated));
        assertEquals("Provenance", AccountDetails.assurance("provenance"));
        Usage plans = new Usage("acct-demo", "free", true, 120, 30, 0, 3, 0, null,
                new CompilerAccountClient.Balance(481, 18, 1, 17, 2), enterprise);
        assertEquals(List.of("acct-demo", "Personal", "member", "enterprise", "Active"), CompilerAccountDialog.accountRows(
                new CompilerAccountClient.Me("acct-demo", true, "member"), plans, null), "the plan named is the profile's");
        assertEquals(List.of("481", "18", "1", "17", "2"), CompilerAccountDialog.creditFigures(plans.interpret()));
        assertEquals(List.of("120", "30", "0", "3", "0"), CompilerAccountDialog.creditFigures(plans.compile()));
    }

    @Test
    void theAddressSuggestionsAreTheIssuerThenRememberedAddressesThenTheSetting_eachOnceAndOnlyUsableOnes() {
        telamin.fluxtion.audit.analyser.analyser.config.AppConfig config = new telamin.fluxtion.audit.analyser.analyser.config.AppConfig();
        assertEquals(List.of(), CompilerAccountDialog.suggestions(config, null), "nothing remembered: nothing invented");
        config.recentCompilerServices.addAll(List.of("https://b.example.com", "http://plain.example.com", "https://a.example.com"));
        config.compilerServiceUrl = "https://a.example.com";
        assertEquals(List.of("https://b.example.com", "https://a.example.com"), CompilerAccountDialog.suggestions(config, null),
                "an address the analyser would refuse is never offered");
        config.compilerServiceUrl = "https://set.example.com";
        assertEquals(List.of("https://b.example.com", "https://a.example.com", "https://set.example.com"),
                CompilerAccountDialog.suggestions(config, null));
    }

    @Test
    void theAccountLineNamesTheTypeAndRole_andTheDeviceRowMarksThisMachine() {
        Usage u = new Usage("acct-demo", "free", true, 120, 0, 0, 0, 0, null);
        assertEquals(List.of("Account", "Type", "Your role", "Plan", "Status"), CompilerAccountDialog.ACCOUNT_ROWS);
        assertEquals(List.of("acct-demo", "Organisation", "member", "free", "Active"),
                CompilerAccountDialog.accountRows(new CompilerAccountClient.Me("acct-demo", false, "member"), u, null));
        assertEquals(List.of("acct-demo", "Unknown", "Unknown", "free", "Active"),
                CompilerAccountDialog.accountRows(new CompilerAccountClient.Me("acct-demo", null, null), u, null));
        assertEquals(List.of("acct-demo", "Not read", "Unknown", "free", "Active"),
                CompilerAccountDialog.accountRows(null, u, null), "not read: no guess");
        CompilerAccountClient.Device d = new CompilerAccountClient.Device("cred01", "DEVICE", "Fluxtion analyser on demo-host", 1_790_000_000_000L);
        assertArrayEquals(new Object[]{"Fluxtion analyser on demo-host (this machine)", "cred01", "Device", "2026-09-21 14:13"},
                CompilerAccountDialog.deviceRow(d, true), "found on staging: three rows read 'device | DEVICE | <same day>'");
        assertEquals("cred_012…", CompilerAccountDialog.shortId("cred_0123456789abcdef"), "a cut id says it is cut");
        assertEquals("cred0123", CompilerAccountDialog.shortId("cred0123"));
        assertEquals("Automation key", CompilerAccountDialog.titleCase("AUTOMATION_KEY"));
        assertEquals("", CompilerAccountDialog.titleCase(""));
        CompilerAccountClient.Device sameDay = new CompilerAccountClient.Device("cred02", "DEVICE", "device", 1_790_000_060_000L);
        assertNotEquals(CompilerAccountDialog.deviceRow(d, false)[3], CompilerAccountDialog.deviceRow(sameDay, false)[3],
                "devices made a minute apart show different times");
        assertEquals("Fluxtion analyser on demo-host", CompilerAccountDialog.deviceRow(d, false)[0]);
        assertEquals(List.of("Fluxtion analyser on demo-host", "cred01"), CompilerAccountDialog.deviceRows("cred01", d.name()));
        assertEquals(List.of("Not in the device list", "cred01"), CompilerAccountDialog.deviceRows("cred01", null));
        assertTrue(CompilerAccountDialog.DELETION_NOTE.contains("support"));
    }

    @Test
    void everyEndingOfASignInSaysWhatHappened_andThatNothingWasStoredUnlessApproved() {
        assertTrue(CompilerAccountDialog.outcomeText(new CompilerAccountClient.Denied()).contains("denied"));
        assertTrue(CompilerAccountDialog.outcomeText(new CompilerAccountClient.Expired()).contains("expired"));
        assertTrue(CompilerAccountDialog.outcomeText(new CompilerAccountClient.Cancelled()).contains("cancelled"));
        assertEquals("Could not reach the compiler service. Nothing was stored.",
                CompilerAccountDialog.outcomeText(new CompilerAccountClient.Failed("Could not reach the compiler service")));
        for (var o : new CompilerAccountClient.Outcome[]{new CompilerAccountClient.Denied(), new CompilerAccountClient.Expired(),
                new CompilerAccountClient.Cancelled(), new CompilerAccountClient.Failed("x")})
            assertTrue(CompilerAccountDialog.outcomeText(o).endsWith("Nothing was stored.")
                    || CompilerAccountDialog.outcomeText(o).contains("Connect again"), o.toString());
    }

    @Test
    void revokeSaysWhetherTheServiceRevokedItNowOrHadAlready() {
        assertTrue(CompilerAccountDialog.revokeText(true).startsWith("Revoked."));
        assertTrue(CompilerAccountDialog.revokeText(false).contains("already did not accept"));
        assertEquals("Not connected.", CompilerAccountDialog.connectionLine(null, false));
        assertEquals("compiler.example.com", CompilerAccountDialog.host("https://compiler.example.com"));
        assertEquals("127.0.0.1", CompilerAccountDialog.host("http://127.0.0.1:8080"));
        assertEquals("Signing in…", CompilerAccountDialog.connectionLine(null, true));
    }
}
