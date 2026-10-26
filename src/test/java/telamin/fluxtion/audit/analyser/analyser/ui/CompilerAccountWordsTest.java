package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.account.CompilerAccountClient;
import telamin.fluxtion.audit.analyser.analyser.account.CompilerAccountClient.Usage;

import static org.junit.jupiter.api.Assertions.*;

/** CA-1: the compiler-account dialog's words, decided without a screen. */
class CompilerAccountWordsTest {

    @Test
    void aPlanWithoutANextResetShowsNoResetLine_ratherThanAGuess() {
        Usage none = new Usage("acct-demo", "free", true, 120, 30, 2, 3, 1, null);
        assertEquals("One-time allowance (no reset)", CompilerAccountDialog.resetText(none), "a one-time allowance, never a date");
        assertEquals("Plan: free · Active", CompilerAccountDialog.planText(none));
        assertEquals("Remaining: 120 · Used: 30 · In progress: 2 · Generations: 3 · Failures: 1", CompilerAccountDialog.creditsText(none));
        Usage monthly = new Usage("acct-demo", "team", true, 4000, 0, 0, 0, 0, "2026-11-01T00:00:00Z");
        assertEquals("Next reset: 2026-11-01T00:00:00Z", CompilerAccountDialog.resetText(monthly));
        assertEquals("Plan: free · Inactive", CompilerAccountDialog.planText(new Usage("a", "free", false, 0, 0, 0, 0, 0, null)));
    }

    @Test
    void theAccountLineNamesTheTypeAndRole_andTheDeviceRowMarksThisMachine() {
        Usage u = new Usage("acct-demo", "free", true, 120, 0, 0, 0, 0, null);
        assertEquals("Account acct-demo · Organisation · Your role: member",
                CompilerAccountDialog.accountText(new CompilerAccountClient.Me("acct-demo", false, "member"), u, null));
        assertEquals("Account acct-demo · Unknown · Your role: unknown",
                CompilerAccountDialog.accountText(new CompilerAccountClient.Me("acct-demo", null, null), u, null));
        assertEquals("Account acct-demo", CompilerAccountDialog.accountText(null, u, null), "not read: no guess");
        CompilerAccountClient.Device d = new CompilerAccountClient.Device("cred01", "DEVICE", "Fluxtion analyser on demo-host", 1_790_000_000_000L);
        assertArrayEquals(new Object[]{"Fluxtion analyser on demo-host (this machine)", "DEVICE", "2026-09-21"},
                CompilerAccountDialog.deviceRow(d, true));
        assertEquals("Fluxtion analyser on demo-host", CompilerAccountDialog.deviceRow(d, false)[0]);
        assertEquals("Fluxtion analyser on demo-host · credential cred01", CompilerAccountDialog.deviceText("cred01", d.name()));
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
        assertEquals("Signing in…", CompilerAccountDialog.connectionLine(null, true));
    }
}
