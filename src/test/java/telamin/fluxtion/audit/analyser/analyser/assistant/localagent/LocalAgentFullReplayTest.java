package telamin.fluxtion.audit.analyser.analyser.assistant.localagent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.session.LocalAgentPosture;
import telamin.fluxtion.audit.analyser.analyser.session.LocalAgentPosture.FullMode;
import telamin.fluxtion.audit.analyser.analyser.session.LocalAgentState;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M74 P1b over the REAL adapter, parser and session processor, with a fake CLI replaying the sanitised isolated
 * captures of the P1b probes (docs/specs/evidence/m74-p1b/fixtures, PB-report.md): the full command line and
 * environment as spawned, the hook status lines, the full-posture assertion on a real full {@code system/init} (the
 * person's 26 built-in tools and a user-scope registration), and billing read from {@code initialize}.
 *
 * <p>The captures are not logged in (isolated config), so their {@code initialize} account is rewritten to a
 * subscription's shape to get past the handshake; what is under test is everything after it.
 */
class LocalAgentFullReplayTest {

    @TempDir
    Path home;

    static final java.util.function.UnaryOperator<String> LOGGED_IN =
            l -> l.replace("\"tokenSource\": \"none\"", "\"subscriptionType\": \"<subscription>\"");

    private Harness full(String fixture, FullMode mode) throws Exception {
        FixtureAgent agent = new FixtureAgent(FixtureAgent.PB_FIXTURES, fixture);
        agent.rewrite = LOGGED_IN;
        agent.conformHostedTools = true;
        Harness h = new Harness(agent, home);
        h.consent = h.fullConsent(mode);
        // the GUI PATH, plus where this harness's fake claude lives (the resolver must still find it)
        h.environment = Map.of("PATH", "/usr/bin:/bin:/usr/sbin:/sbin:" + home.resolve("bin"));
        h.start();
        return h;
    }

    /**
     * ★ Witnesses: (1) report the hook's {@code stdout}/{@code output} in the parsed {@code Hook} record (a
     * {@code detail} field the node puts in the note) — the marker reaches the snapshot and the store and both
     * assertions fail; (2) treat {@code hook_*} as {@code Ignored} again (P1a) — no hook note appears.
     */
    @Test
    @DisplayName("★ hook status lines from pb-hooks-iso: event and outcome shown, the hook's output never in the store or the snapshot")
    void hookStatusLinesFromTheFixture() throws Exception {
        Harness h = full("pb-hooks-iso.jsonl", FullMode.DEFAULT);
        assertTrue(Files.readString(FixtureAgent.PB_FIXTURES.resolve("pb-hooks-iso.jsonl")).contains("HOOK-STDOUT-MARKER"),
                "the fixture really carries the hooks' output");
        List<LocalAgentState.Entry> hooks = h.state().entries().stream().filter(e -> e.status().startsWith("HOOK")).toList();
        assertEquals(1, hooks.size(), "one coalesced note for SessionStart: " + h.state().entries());
        assertEquals("HOOK_FAILED", hooks.get(0).status());
        assertTrue(hooks.get(0).detail().startsWith("SessionStart: 1 ran, 1 failed"), hooks.get(0).detail());
        String everything = h.state().toString() + h.store.dump() + h.facts;
        for (String marker : List.of("HOOK-STDOUT-MARKER", "HOOK-STDERR-MARKER")) {
            assertFalse(everything.contains(marker), "a hook's output never reaches the snapshot, the store or a fact");
        }
        assertEquals(4, h.facts(SessionEvents.LocalAgentHookObserved.class).size(), "two started, two answered");
    }

    /**
     * ★ The full assertion on a REAL full {@code system/init}. Witness: keep the default posture's whole-list
     * comparison for the full posture — the first fixture (26 of the person's tools) is refused TOOLS_MISMATCH; and
     * one that ignores the registration — the witness fixture is admitted.
     */
    @Test
    @DisplayName("★ the full assertion on recorded inits: the person's own tools admitted; their registration's tools refused")
    void theFullAssertionOnRecordedInits() throws Exception {
        Harness ok = full("pb-mode-default-iso.jsonl", FullMode.DEFAULT);
        assertEquals("READY", ok.state().phase(), ok.state().reason());
        assertEquals(1, ok.state().personServers(), "the user-scope registration is the person's server, spawned and hidden");
        ok.send("hello");
        assertEquals(1, ok.state().admittedGeneration(), "the person's Bash, Edit, Task… are theirs: admitted: "
                + ok.state().reason());
        List<String> command = ok.agent.commands.get(0);
        assertFalse(command.contains("--restricted") || command.contains("--strict-mcp-config") || command.contains("--tools"),
                "spawned with the full line: " + command);
        assertEquals("default", command.get(command.indexOf("--permission-mode") + 1));
        assertFalse(command.contains("--max-budget-usd"), "no limit set: no flag");
        Map<String, String> env = ok.agent.environments.get(0);
        assertFalse(env.containsKey(ClaudeCodeProvider.CLAUDE_MD_ENV), "no CLAUDE.md flag in the full posture");

        Harness bad = new Harness(witness(), home.resolve("w"));
        bad.consent = bad.fullConsent(FullMode.DEFAULT);
        bad.environment = Map.of("PATH", home.resolve("w").resolve("bin").toString());
        bad.start();
        bad.send("hello");
        assertEquals("TOOLS_MISMATCH", bad.state().verdict(),
                "the person's own fluxtion-analyser tools are visible: never this analyser");
        assertEquals(0, bad.state().admittedGeneration());
    }

    private FixtureAgent witness() throws Exception {
        FixtureAgent agent = new FixtureAgent(FixtureAgent.PB_FIXTURES, "pb-mode-witness-iso.jsonl");
        // that run sent no initialize of its own, so the P0 answer stands in for it: give it the full line's mode
        agent.rewrite = l -> LOGGED_IN.apply(l).replaceAll("\"current_permission_mode\": ?\"[A-Za-z]+\"",
                "\"current_permission_mode\": \"default\"");
        agent.conformHostedTools = true;
        return agent;
    }

    @Test
    @DisplayName("PB-3 recorded: auto on haiku reported default at initialize — MODE_MISMATCH with the auto words")
    void autoFallbackFromTheFixture() throws Exception {
        Harness h = full("pb-mode-auto-iso.jsonl", FullMode.AUTO);
        assertEquals("MODE_MISMATCH", h.state().verdict());
        assertTrue(h.state().reason().contains("auto mode is not available for the configured Claude Code model"),
                h.state().reason());
        assertEquals(1, h.agent.last().terminated, "stopped before any turn");
    }

    @Test
    @DisplayName("plan mode recorded: initialize and system/init both report plan, and the session is admitted")
    void planFromTheFixture() throws Exception {
        Harness h = full("pb-mode-plan-iso.jsonl", FullMode.PLAN);
        h.send("hello");
        assertEquals(1, h.state().admittedGeneration(), h.state().reason());
    }

    /**
     * ★ owner, 2026-10-17: billing is the node's, from the initialize fact. Witness: report UNKNOWN for every
     * initialize — the subscription and API-key assertions fail (and the header falls back to no figure).
     */
    @Test
    @DisplayName("★ billing from initialize: a Claude plan, an API key")
    void billingFromInitialize() throws Exception {
        Harness plan = full("pb-mode-default-iso.jsonl", FullMode.DEFAULT);
        assertEquals(SessionEvents.Billing.SUBSCRIPTION, plan.state().billing());
        FixtureAgent key = new FixtureAgent("p0b-account-iso-apikey.jsonl");
        Harness b = new Harness(key, home.resolve("k"));
        b.start();
        assertEquals(SessionEvents.Billing.API_KEY, b.state().billing());
        assertTrue(b.state().budgetLimited(), "the P1a harness consent sets a limit: the header shows used of limit");
    }

    @Test
    @DisplayName("the default posture's spawn is unchanged: the CLAUDE.md flag, no PATH, the confining flags")
    void theDefaultPostureIsUnchanged() throws Exception {
        FixtureAgent agent = new FixtureAgent("p0b-default-posture.jsonl");
        Harness h = new Harness(agent, home);
        h.start();
        assertEquals(Map.of(ClaudeCodeProvider.CLAUDE_MD_ENV, "1"), agent.environments.get(0));
        assertTrue(agent.commands.get(0).contains("--restricted"));
        assertEquals(LocalAgentPosture.ANALYSER_ASSISTANT, h.state().posture());
    }

    /**
     * ★ Review finding 2, over the REAL parser and adapter, from a capture: a {@code set_permission_mode} control request
     * under an isolated config made the CLI emit {@code system/status{permissionMode:"acceptEdits"}} (no login, no
     * spend). Replayed into a bound default-posture session, it is refused at once. Witness: parse {@code status} as
     * {@code Ignored} (P1b) — the session stays admitted.
     */
    @Test
    @DisplayName("★ the recorded system/status mode change mid-session is MODE_MISMATCH and kills the process")
    void aRecordedModeChangeIsRefused() throws Exception {
        FixtureAgent agent = new FixtureAgent(FixtureAgent.PB_FIXTURES, "pb-status-mode-iso.jsonl");
        Harness h = new Harness(agent, home);
        h.start();
        assertEquals("READY", h.state().phase(), h.state().reason());
        h.send("hello");                          // the fixture's two status lines are this turn's
        assertEquals("MODE_MISMATCH", h.state().verdict(), h.state().reason());
        assertTrue(h.state().reason().contains("changed its permission mode to acceptEdits"), h.state().reason());
        assertEquals(1, agent.last().terminated, "killed at once");
        assertEquals(2, h.facts(SessionEvents.LocalAgentModeObserved.class).size(), "both lines are reported");
        assertEquals(1, agent.last().terminated, "the second, from a process already stopped, changes nothing");
    }

    @Test
    @DisplayName("review finding 11: apiKeySource \"none\" is no key; an API key beside a subscription is what is billed")
    void billingShapes() throws Exception {
        var none = (StreamJson.ControlResponse) StreamJson.parse("{\"type\":\"control_response\",\"response\":{\"subtype\":"
                + "\"success\",\"request_id\":\"i\",\"response\":{\"account\":{\"apiKeySource\":\"none\",\"tokenSource\":"
                + "\"none\"},\"commands\":[]}}}");
        assertNull(none.initialize().apiKeySource());
        assertFalse(none.initialize().loggedIn(), "\"none\" is not a key: not logged in");
        var both = (StreamJson.ControlResponse) StreamJson.parse("{\"type\":\"control_response\",\"response\":{\"subtype\":"
                + "\"success\",\"request_id\":\"i\",\"response\":{\"account\":{\"apiKeySource\":\"ANTHROPIC_API_KEY\","
                + "\"subscriptionType\":\"<subscription>\"},\"commands\":[]}}}");
        assertEquals("ANTHROPIC_API_KEY", both.initialize().apiKeySource());

        // through the adapter: a subscription with an API key in force is billed as the API key
        FixtureAgent agent = new FixtureAgent("multiturn-partial-replay-midturn.jsonl");
        agent.rewrite = l -> l.replace("\"subscriptionType\": \"<subscription>\"",
                "\"subscriptionType\": \"<subscription>\", \"apiKeySource\": \"ANTHROPIC_API_KEY\"");
        Harness h = new Harness(agent, home.resolve("both"));
        h.start();
        assertEquals(SessionEvents.Billing.API_KEY, h.state().billing());
        assertEquals("API key (ANTHROPIC_API_KEY)", h.store.header(h.state().headerRef()).billing());
    }

    /**
     * ★ Holistic review B7 (2026-10-17): the spawn checks the folder's FULL trust digest, not only the posture reference —
     * a hook added to the folder after the person trusted it refuses the spawn TRUST_CHANGED, and nothing is started.
     * Witness: drop the full-digest check in {@code LocalAgentAdapter.spawn} — the process is started with the new hook.
     */
    @Test
    @DisplayName("★ B7: a folder changed after its full-posture trust is refused at spawn; nothing is started")
    void theSpawnChecksTheFullTrustDigest() throws Exception {
        FixtureAgent agent = new FixtureAgent(FixtureAgent.PB_FIXTURES, "pb-mode-default-iso.jsonl");
        agent.rewrite = LOGGED_IN;
        agent.conformHostedTools = true;
        Harness h = new Harness(agent, home);
        h.consent = h.fullConsent(FullMode.DEFAULT);
        h.environment = Map.of("PATH", "/usr/bin:/bin:/usr/sbin:/sbin:" + home.resolve("bin"));
        Path dir = home.resolve("proj");
        h.fullTrust = new java.util.ArrayList<>(List.of(FullFolderTrust.record(ClaudeCodeProvider.realPath(dir),
                FullFolderTrust.digest(dir))));                                    // trusted as it was
        Files.createDirectories(dir.resolve(".claude"));
        Files.writeString(dir.resolve(".claude/settings.json"),
                "{\"hooks\":{\"SessionStart\":[{\"hooks\":[{\"type\":\"command\",\"command\":\"./added.sh\"}]}]}}");
        h.start();
        var spawned = h.facts(SessionEvents.LocalAgentSpawned.class);
        assertEquals(SessionEvents.SpawnFailure.TRUST_CHANGED, spawned.get(spawned.size() - 1).failure());
        assertTrue(agent.processes.isEmpty(), "nothing was started");
        assertTrue(h.state().reason().contains("changed after you confirmed it"), h.state().reason());
        assertTrue(FullFolderTrust.dialogText(dir, FullFolderTrust.scan(dir)).contains(FullFolderTrust.NOT_RECHECKED),
                "the dialog says plainly what the trust does not cover");
    }

    /**
     * ★ Authoring walk (2026-10-18, shots 36/37): Full Claude Code with NO project open works in the analyser's own empty
     * scratch folder — confirmed by the consent's rule (review finding 3) and, now, by the spawn's re-check too. It was
     * refused TRUST_CHANGED ("changed after you confirmed it") at every Start, though nothing had ever been confirmed.
     * Witness: make {@code FullFolderTrust.confirmedFull} ignore {@code scratch} (records only) — the consent asks a
     * question the person never needed, and the spawn is refused.
     */
    @Test
    @DisplayName("★ Full with no project: the empty scratch folder starts, by the same rule the consent used")
    void fullWithNoProjectStartsInTheEmptyScratchFolder() throws Exception {
        FixtureAgent agent = new FixtureAgent(FixtureAgent.PB_FIXTURES, "pb-mode-default-iso.jsonl");
        agent.rewrite = LOGGED_IN;
        agent.conformHostedTools = true;
        Harness h = new Harness(agent, home);
        Path scratch = Files.createDirectories(home.resolve("scratch"));
        h.scratch = scratch;
        h.workingDirectory = scratch;
        h.fullTrust = new java.util.ArrayList<>();                         // nothing was ever trusted
        h.environment = Map.of("PATH", "/usr/bin:/bin:/usr/sbin:/sbin:" + home.resolve("bin"));
        h.consent = LocalAgentConsents.of(scratch, scratch, home,
                new LocalAgentConsents.Settings(true, true, "default", List.of(), List.of(), null), p -> false);
        assertTrue(h.consent.confirmedForPosture(), "the consent exempts the empty scratch folder");
        h.start();
        var spawned = h.facts(SessionEvents.LocalAgentSpawned.class);
        assertFalse(spawned.isEmpty(), "a spawn was attempted");
        assertEquals(SessionEvents.SpawnFailure.NONE, spawned.get(spawned.size() - 1).failure(), h.state().reason());
        assertFalse(agent.processes.isEmpty(), "Claude Code was started in the scratch folder");
        assertFalse(h.state().reason().contains("changed after you confirmed it"), h.state().reason());
    }

    /**
     * ★ Authoring walk: a project folder never trusted for Full is ASKED about at Start — the consent says it is not
     * confirmed, so the node asks for the folder dialog; and should a spawn ever meet a folder no record names, it is
     * TRUST_NEEDED ("not trusted … yet"), never TRUST_CHANGED. Witness: report TRUST_CHANGED whatever
     * {@code namesFolder} says — the spawn's verdict is TRUST_CHANGED.
     */
    @Test
    @DisplayName("★ a folder never trusted for Full is asked about, never reported as changed")
    void aFolderNeverTrustedIsAskedNotChanged() throws Exception {
        FixtureAgent agent = new FixtureAgent(FixtureAgent.PB_FIXTURES, "pb-mode-default-iso.jsonl");
        agent.rewrite = LOGGED_IN;
        agent.conformHostedTools = true;
        Harness h = new Harness(agent, home);
        Path dir = home.resolve("proj");
        h.fullTrust = new java.util.ArrayList<>();
        h.environment = Map.of("PATH", "/usr/bin:/bin:/usr/sbin:/sbin:" + home.resolve("bin"));
        // what the frame observes: not confirmed, so Start asks
        h.consent = LocalAgentConsents.of(dir, home.resolve("elsewhere"), home,
                new LocalAgentConsents.Settings(true, true, "default", List.of(), List.of(), null), p -> false);
        assertFalse(h.consent.confirmedForPosture());
        h.start();
        assertTrue(h.others.stream().anyMatch(e -> e instanceof telamin.fluxtion.audit.analyser.analyser.session
                .SessionEffects.ConfirmLocalAgentFolderEffect c && c.full()), "Start asks about the folder: " + h.others);
        assertTrue(agent.processes.isEmpty(), "nothing was started before the answer");
        // a consent that claimed it (a record removed between the observation and the spawn): not "changed"
        h.consent = h.fullConsent(FullMode.DEFAULT);
        h.start();
        var spawned = h.facts(SessionEvents.LocalAgentSpawned.class);
        assertEquals(SessionEvents.SpawnFailure.TRUST_NEEDED, spawned.get(spawned.size() - 1).failure());
        assertTrue(h.state().reason().contains("not trusted for full Claude Code yet"), h.state().reason());
        assertTrue(agent.processes.isEmpty(), "nothing was started");
    }

    /**
     * ★ Authoring walk (2026-10-18, shot 54): a question SENT while Full asks for folder trust is not lost. Send opens the
     * folder question; Trust starts Claude Code and the question is sent once, at READY. Don't trust starts nothing, and
     * the question is not accepted (the composer keeps it). Witness: drop the hold in {@code onFolderAnswered} (never set
     * {@code pendingDraft} there) — "Ready" with nothing sent.
     */
    @Test
    @DisplayName("★ a Send that asks for folder trust is sent once after Trust, and kept on Don't trust")
    void aSendThatAsksForTrustIsSentOnceAfterIt() throws Exception {
        Harness h = untrustedFull(home);
        h.send("build me a diamond");
        var asked = h.others.stream().filter(e -> e instanceof telamin.fluxtion.audit.analyser.analyser.session
                .SessionEffects.ConfirmLocalAgentFolderEffect).map(e -> (telamin.fluxtion.audit.analyser.analyser.session
                .SessionEffects.ConfirmLocalAgentFolderEffect) e).toList();
        assertEquals(1, asked.size(), "Send asks about the folder: " + h.others);
        assertFalse(h.state().answer().accepted(), "nothing is accepted before the answer");
        h.driver.post(new SessionEvents.LocalAgentFolderAnswered(asked.get(0).request(), true, true, h.consent));
        assertEquals("READY", h.state().phase(), h.state().reason());
        assertTrue(h.state().answer().accepted(), "the Send's request is accepted: the composer clears");
        assertEquals(asked.get(0).request(), h.state().answer().request());
        assertEquals(List.of("build me a diamond"), h.userTurns(), "the question was sent, once");
        assertEquals(1, h.agent.last().stdin.stream().filter(l -> l.contains("build me a diamond")).count(),
                "and reached Claude Code once");
        assertEquals(0, h.state().pendingDraft());

        Harness no = untrustedFull(home.resolve("no"));
        no.send("build me a diamond");
        var askedNo = no.others.stream().filter(e -> e instanceof telamin.fluxtion.audit.analyser.analyser.session
                .SessionEffects.ConfirmLocalAgentFolderEffect).map(e -> (telamin.fluxtion.audit.analyser.analyser.session
                .SessionEffects.ConfirmLocalAgentFolderEffect) e).toList();
        no.driver.post(new SessionEvents.LocalAgentFolderAnswered(askedNo.get(0).request(), false, true, no.consent));
        assertFalse(no.state().answer().accepted(), "declined: the question stays in the composer");
        assertTrue(no.agent.processes.isEmpty(), "nothing was started");
        assertEquals(List.of(), no.userTurns());
        assertEquals(0, no.state().pendingDraft(), "and nothing waits to be sent later");
    }

    /** Full Claude Code in a project folder the person has not yet trusted for it (the consent the frame observes). */
    private Harness untrustedFull(Path at) throws Exception {
        FixtureAgent agent = new FixtureAgent(FixtureAgent.PB_FIXTURES, "pb-mode-default-iso.jsonl");
        agent.rewrite = LOGGED_IN;
        agent.conformHostedTools = true;
        Files.createDirectories(at);
        Harness h = new Harness(agent, at);
        h.environment = Map.of("PATH", "/usr/bin:/bin:/usr/sbin:/sbin:" + at.resolve("bin"));
        h.consent = new SessionEvents.LocalAgentConsent(true, false, false, ClaudeCodeProvider.postureRef(at.resolve("proj"),
                LocalAgentPosture.full(FullMode.DEFAULT)), 0, SessionEvents.FolderVerdict.OK, true, FullMode.DEFAULT, false);
        return h;
    }

    /**
     * ★ Owner 2026-10-18: the Full session is LAUNCHED with the analyser's standing instruction (stay in the project, never
     * look for the key) on its {@code --append-system-prompt}; the Safe session is not; and the folder-trust dialog says
     * the analyser adds it. Witness: end {@code FULL_SYSTEM_NOTE} before {@code STAY_IN_PROJECT_NOTE} — the launched argv
     * lacks it.
     */
    @Test
    @DisplayName("★ the launched Full argv carries the stay-in-the-project instruction; Safe's does not; the dialog says so")
    void theFullArgvCarriesTheStandingInstruction() throws Exception {
        Harness full = full("pb-mode-default-iso.jsonl", FullMode.DEFAULT);
        List<String> argv = full.agent.commands.get(0);
        String note = argv.get(argv.indexOf("--append-system-prompt") + 1);
        assertTrue(note.contains(ClaudeCodeProvider.STAY_IN_PROJECT_NOTE), note);
        assertEquals(1, argv.stream().filter(a -> a.contains(ClaudeCodeProvider.STAY_IN_PROJECT_NOTE)).count(),
                "carried once, as the system prompt, never as a user turn");

        FixtureAgent agent = new FixtureAgent("multiturn-partial-replay-midturn.jsonl");
        agent.conformInit = true;
        Harness safe = new Harness(agent, home.resolve("safe"));
        safe.start();
        List<String> safeArgv = safe.agent.commands.get(0);
        assertFalse(String.join(" ", safeArgv).contains(ClaudeCodeProvider.STAY_IN_PROJECT_NOTE),
                "Safe has no shell and runs --restricted: no such instruction");

        Path dir = home.resolve("proj");
        assertTrue(FullFolderTrust.dialogText(dir, FullFolderTrust.scan(dir)).contains(FullFolderTrust.ADDS_INSTRUCTION),
                "the person is told the analyser adds it");
    }

    private static String openRequest(String id, String log) {
        return "{\"type\":\"control_request\",\"request_id\":\"" + id + "\",\"request\":{\"subtype\":\"can_use_tool\","
                + "\"tool_name\":\"mcp__fluxtion-analyser-hosted__analyser_open\",\"input\":{\"log\":\""
                + log.replace("\\", "\\\\") + "\"},\"tool_use_id\":\"toolu_" + id + "\"}}";
    }

    /**
     * ★ Owner 2026-10-18, through the REAL adapter and processor: in Full, Claude Code's request to {@code analyser_open}
     * the project's own log is judged PRE_ALLOWED (the node then allows it with no dialog — LocalAgentPermission's
     * PRE_ALLOWED path); one outside the folder, and the same request in Safe analysis, are CHANGES (asked). Witness:
     * pass {@code null} for the scope in the adapter's {@code hostedCallKind} call — the in-project open is CHANGES.
     */
    @Test
    @DisplayName("★ Full: open of the project's own log needs no approval; outside the folder, or in Safe, it is asked")
    void anInProjectOpenNeedsNoApprovalInFull() throws Exception {
        Harness h = full("pb-mode-default-iso.jsonl", FullMode.DEFAULT);
        Path log = Files.writeString(home.resolve("proj/diamond-audit.yaml"), "eventLogRecord:\n");
        Path outside = Files.writeString(home.resolve("elsewhere.yaml"), "eventLogRecord:\n");
        h.agent.last().emit(openRequest("in", log.toString()));
        h.drain();
        var in = h.facts(SessionEvents.LocalAgentPermissionRequested.class);
        assertEquals(SessionEvents.HostedCallKind.PRE_ALLOWED, in.get(in.size() - 1).kind(), "the project's own log");
        h.agent.last().emit(openRequest("out", outside.toString()));
        h.drain();
        var out = h.facts(SessionEvents.LocalAgentPermissionRequested.class);
        assertEquals(SessionEvents.HostedCallKind.CHANGES, out.get(out.size() - 1).kind(), "outside the folder: asked");

        FixtureAgent agent = new FixtureAgent("multiturn-partial-replay-midturn.jsonl");
        agent.conformInit = true;
        Harness safe = new Harness(agent, home.resolve("safe"));
        safe.start();
        Path safeLog = Files.writeString(home.resolve("safe/proj/diamond-audit.yaml"), "eventLogRecord:\n");
        safe.agent.last().emit(openRequest("safe", safeLog.toString()));
        safe.drain();
        var asked = safe.facts(SessionEvents.LocalAgentPermissionRequested.class);
        assertEquals(SessionEvents.HostedCallKind.CHANGES, asked.get(asked.size() - 1).kind(), "Safe is unchanged");
    }

    private static String reportRequest(String id, String name) {
        return "{\"type\":\"control_request\",\"request_id\":\"" + id + "\",\"request\":{\"subtype\":\"can_use_tool\","
                + "\"tool_name\":\"mcp__fluxtion-analyser-hosted__analyser_report\",\"input\":{\"name\":\"" + name
                + "\",\"sections\":[{\"kind\":\"narrative\",\"text\":\"DEMO\"}]},\"tool_use_id\":\"toolu_" + id + "\"}}";
    }

    /**
     * ★ Owner 2026-10-18, through the REAL adapter and processor: in Full, Claude Code's request to {@code analyser_report}
     * a NEW report into the active project is judged PRE_ALLOWED (the node then allows it with no dialog —
     * {@code InProjectReportTest} and LocalAgentPermission's PRE_ALLOWED path); a request to write over the person's
     * report, and the same create in Safe analysis, are CHANGES (asked). The tool line says why. Witness (run
     * 2026-10-18): drop {@code env.reports()} from the adapter's {@code scopeOf} — the create is CHANGES.
     */
    @Test
    @DisplayName("★ Full: a new report inside the project needs no approval; over the person's report, or in Safe, it is asked")
    void anInProjectReportNeedsNoApprovalInFull() throws Exception {
        Harness h = full("pb-mode-default-iso.jsonl", FullMode.DEFAULT);
        var reports = new telamin.fluxtion.audit.analyser.analyser.session.FakeReportState(
                Files.createDirectories(home.resolve("proj")));
        reports.put("Person's findings", "p-1");
        h.reports = reports;
        h.agent.last().emit(reportRequest("new", "DEMO trace"));
        h.drain();
        var created = h.facts(SessionEvents.LocalAgentPermissionRequested.class);
        assertEquals(SessionEvents.HostedCallKind.PRE_ALLOWED, created.get(created.size() - 1).kind(), "a new report");
        assertEquals("allowed: writes a new report inside this project", LocalAgentAdapter.REPORT_IN_PROJECT_MARK);
        h.agent.last().emit(reportRequest("over", "Person's findings"));
        h.drain();
        var over = h.facts(SessionEvents.LocalAgentPermissionRequested.class);
        assertEquals(SessionEvents.HostedCallKind.CHANGES, over.get(over.size() - 1).kind(), "the person's report: asked");

        FixtureAgent agent = new FixtureAgent("multiturn-partial-replay-midturn.jsonl");
        agent.conformInit = true;
        Harness safe = new Harness(agent, home.resolve("safe"));
        safe.reports = reports;
        safe.start();
        safe.agent.last().emit(reportRequest("safe", "DEMO safe"));
        safe.drain();
        var asked = safe.facts(SessionEvents.LocalAgentPermissionRequested.class);
        assertEquals(SessionEvents.HostedCallKind.CHANGES, asked.get(asked.size() - 1).kind(), "Safe is unchanged");
    }
}
