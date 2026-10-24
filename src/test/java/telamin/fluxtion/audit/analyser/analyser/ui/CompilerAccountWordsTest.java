package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.account.CompilerAccountClient;
import telamin.fluxtion.audit.analyser.analyser.account.CompilerAccountClient.Usage;

import static org.junit.jupiter.api.Assertions.*;

/** CA-1: the compiler-account dialog's words, decided without a screen. */
class CompilerAccountWordsTest {

    @Test
    void aPlanWithoutANextResetShowsNoResetLine_ratherThanAGuess() {
        Usage none = new Usage("acct-demo", "free", true, 120, null);
        assertNull(CompilerAccountDialog.resetText(none));
        assertEquals("Plan: free · Available credits: 120", CompilerAccountDialog.usageText(none));
        Usage monthly = new Usage("acct-demo", "team", true, 4000, "2026-11-01T00:00:00Z");
        assertEquals("Next reset: 2026-11-01T00:00:00Z", CompilerAccountDialog.resetText(monthly));
        assertTrue(CompilerAccountDialog.usageText(new Usage("a", "free", false, 0, null)).contains("(inactive)"));
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
