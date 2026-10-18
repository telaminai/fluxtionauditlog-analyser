package telamin.fluxtion.audit.analyser.analyser.session.graph;
//
// NOT `...session.build`, which is where this started. `.gitignore` carries `build/` for Maven output,
// and a Java package directory of that name matches it — so the one file in the repository that
// describes the graph, and the only one that needs an API key, was silently excluded from `git add`.
// The generated processor would have been committed with nothing to regenerate it from, and nothing
// would have failed. Found by reading `git status` rather than by anything failing, which is the point.
//

import com.telamin.fluxtion.builder.compile.config.FluxtionCompilerConfig;
import com.telamin.fluxtion.builder.compile.config.FluxtionGraphBuilder;
import com.telamin.fluxtion.builder.generation.config.EventProcessorConfig;
import com.telamin.fluxtion.runtime.audit.EventLogControlEvent;
import telamin.fluxtion.audit.analyser.analyser.session.node.ActiveProject;
import telamin.fluxtion.audit.analyser.analyser.session.node.AuditInstallation;
import telamin.fluxtion.audit.analyser.analyser.session.node.CoverageClaim;
import telamin.fluxtion.audit.analyser.analyser.session.node.LogArrival;
import telamin.fluxtion.audit.analyser.analyser.session.node.LogOpening;
import telamin.fluxtion.audit.analyser.analyser.session.node.Pairing;
import telamin.fluxtion.audit.analyser.analyser.session.node.PairingQualifier;
import telamin.fluxtion.audit.analyser.analyser.session.node.EffectOutcomes;
import telamin.fluxtion.audit.analyser.analyser.session.node.EffectQueue;
import telamin.fluxtion.audit.analyser.analyser.session.node.IgnoredParameters;
import telamin.fluxtion.audit.analyser.analyser.session.node.OpenGraph;
import telamin.fluxtion.audit.analyser.analyser.session.node.OpenLog;
import telamin.fluxtion.audit.analyser.analyser.session.node.OperationGate;
import telamin.fluxtion.audit.analyser.analyser.session.node.SessionBoundary;

/**
 * Authors the session transition graph — M44 slice 1.
 *
 * <p><b>This file is the only thing in the repository that needs a Fluxtion API key.</b> It lives in
 * {@code src/graph/java}, a source root added only by {@code -Pregen}, so the ordinary keyless build
 * never resolves {@code fluxtion-builder} at all. Regenerate with:
 *
 * <pre>
 *   mvn -Pregen process-classes      # needs ~/.fluxtion/fluxtion.apiKeyFile
 *   mvn test                         # GeneratedSourceIsPublishableTest tells you what to strip
 * </pre>
 *
 * <p><b>After regenerating, strip the generator's copyright line from both emitted copies of
 * SessionProcessor.java.</b> It carries a personal address on an employer domain into a PUBLIC
 * repository — rule 1 — and it comes back on every regeneration. Do not rely on remembering: the test
 * named above fails the build if it is still there, which is the only version of this instruction that
 * survives someone regenerating in six months without reading this comment.
 *
 * <p><b>Removing an event type or handler</b> (owner, 2026-09-24). The committed processor still dispatches to
 * whatever it was generated with, so deleting a handler or event first breaks the compile that regeneration needs.
 * Do it in three steps, and never hand-edit generated source:
 * <ol>
 *   <li>strip the {@code @OnEventHandler} annotation from each retiring handler, keeping the method and the event;</li>
 *   <li>regenerate — the emitted processor no longer references them, and everything still compiles;</li>
 *   <li>delete the now-unused methods and event types.</li>
 * </ol>
 * (M44.4a bootstrapped by hand-stripping the stale generated file instead; the regeneration overwrote it whole,
 * so nothing hand-edited survived — but the route above never has a hand-edited state to trust.)
 *
 * The generated processor and its GraphML are <b>committed</b>, so everyone else — CI, a reviewer
 * without a key, a fresh contributor — builds and tests from a bare checkout.
 *
 * <pre>
 *   OpenProjectRequested ─┐
 *   ProfileLoaded ────────┤
 *   ProfileApplied ───────┼──▶ operationGate ──┬──▶ activeProject ─┐
 *   LogClosed / GraphClosed                    ├──▶ openLog ───────┼──▶ sessionBoundary ══▶ effectQueue
 *   LogObserved / GraphObserved                └──▶ openGraph ─────┘        (decision)   (push)
 * </pre>
 *
 * <p>The gate is upstream of everything so a stale result is refused before any node believes it. The
 * decision is downstream of the state it reads. And the queue is reached by {@code @PushReference}, so
 * the wave visits the decision <em>first</em> — which is what makes the emitted picture show effects
 * descending from a decision rather than feeding into one.
 */
public class SessionProcessorBuilder implements FluxtionGraphBuilder {

    @Override
    public void buildGraph(EventProcessorConfig cfg) {
        EffectQueue effects = new EffectQueue();
        OperationGate gate = new OperationGate();
        ActiveProject activeProject = new ActiveProject(gate);
        // Where the session came from: a verified bundle, or the person's own project. The facts existed
        // already and were discarded when the transition settled; this holds them for the session's life.
        telamin.fluxtion.audit.analyser.analyser.session.node.OpenBundle openBundle =
                new telamin.fluxtion.audit.analyser.analyser.session.node.OpenBundle(gate);
        OpenLog openLog = new OpenLog(gate);
        OpenGraph openGraph = new OpenGraph(gate);
        SessionBoundary boundary = new SessionBoundary(gate, activeProject, openLog, openGraph, effects);
        EffectOutcomes outcomes = new EffectOutcomes(gate);
        // M44.2 — the review's F3: three questions the first draft merged into one node.
        Pairing pairing = new Pairing(openLog, openGraph);
        AuditInstallation auditInstallation = new AuditInstallation(openGraph);
        LogArrival logArrival = new LogArrival(gate, pairing, openGraph, effects);
        LogOpening logOpening = new LogOpening(gate, effects);
        CoverageClaim coverageClaim = new CoverageClaim(pairing, auditInstallation, openGraph, openLog);
        IgnoredParameters ignoredParameters = new IgnoredParameters();
        // M44.4c: what wider comparisons say about the pairing, bound to the pair by generation and revision
        PairingQualifier pairingQualifier = new PairingQualifier(openLog, openGraph, pairing);
        // M44.5: the log's own derived state — producer findings and time order — and the decision of when to rescan
        telamin.fluxtion.audit.analyser.analyser.session.node.LogEvidence logEvidence =
                new telamin.fluxtion.audit.analyser.analyser.session.node.LogEvidence(openLog, effects);

        // These names become the instanceIds in nodeLogs and the node ids in the GraphML — they are
        // what a reader of the audit log sees, so they are the vocabulary of the rule, not of Java.
        cfg.addNode(gate, "operationGate");
        cfg.addNode(activeProject, "activeProject");
        cfg.addNode(openBundle, "openBundle");
        // #75: where a bundle's code lives on this machine. It decides; the frame performs and reports.
        cfg.addNode(new telamin.fluxtion.audit.analyser.analyser.session.node.BundleAnchor(openBundle, effects),
                "bundleAnchor");
        // O3: a project carries settings, never what was on screen. When one comes into force with
        // nothing to look at, offer its logs and topologies -- never for a bundle, which brings its own.
        cfg.addNode(new telamin.fluxtion.audit.analyser.analyser.session.node.ProjectReopenOffer(openBundle, effects),
                "projectReopenOffer");
        cfg.addNode(openLog, "openLog");
        cfg.addNode(openGraph, "openGraph");
        cfg.addNode(boundary, "sessionBoundary");
        cfg.addNode(effects, "effectQueue");
        cfg.addNode(outcomes, "effectOutcomes");
        cfg.addNode(pairing, "pairing");
        cfg.addNode(auditInstallation, "auditInstallation");
        cfg.addNode(logArrival, "logArrival");
        cfg.addNode(logOpening, "logOpening");
        cfg.addNode(coverageClaim, "coverageClaim");
        cfg.addNode(ignoredParameters, "ignoredParameters");
        cfg.addNode(pairingQualifier, "pairingQualifier");
        cfg.addNode(logEvidence, "logEvidence");
        // the Records view: what it shows and what each filter hides — measured on the node's request, published in
        // the snapshot, rendered by the "showing" label, the Records title, the Event types toggle and the empty table
        telamin.fluxtion.audit.analyser.analyser.session.node.RecordsView recordsView =
                new telamin.fluxtion.audit.analyser.analyser.session.node.RecordsView(openLog, effects);
        cfg.addNode(recordsView, "recordsView");
        // M69: spotlight-walk playback — every transition and decision about a showing walk, in one place (owner)
        // OA-1 (spec-onboard-assistant-journeys.md §5): the onboard assistant's turns, tickets, budgets and basis, in one place
        // M74 (spec-claude-code-provider.md §4.1): one assistant mode at a time, as a session fact, upstream of both
        // assistants so each enforces it in its own handlers; the local agent's lifecycle is a sibling of assistantLoop.
        telamin.fluxtion.audit.analyser.analyser.session.node.AssistantModeChoice assistantMode =
                new telamin.fluxtion.audit.analyser.analyser.session.node.AssistantModeChoice();
        cfg.addNode(assistantMode, "assistantMode");
        telamin.fluxtion.audit.analyser.analyser.session.node.AssistantLoop assistantLoop =
                new telamin.fluxtion.audit.analyser.analyser.session.node.AssistantLoop(openLog, openGraph, activeProject, gate, effects);
        assistantLoop.setAssistantMode(assistantMode);
        assistantLoop.setRecordsView(recordsView);   // the basis states what the filter hides (a JavaBean parent)
        cfg.addNode(assistantLoop, "assistantLoop");
        telamin.fluxtion.audit.analyser.analyser.session.node.LocalAgentSession localAgent =
                new telamin.fluxtion.audit.analyser.analyser.session.node.LocalAgentSession(assistantMode, effects);
        // M74 P2 (§8.2): the open log is a JavaBean parent — what a bound draft is compared with at Send
        localAgent.setOpenLog(openLog);
        // review B5 (2026-10-17): the action listener's lifecycle — a JavaBean parent the session tells when it needs the
        // hosted bridge; it decides whether a listener is up and whether it serves external clients (rule 9)
        telamin.fluxtion.audit.analyser.analyser.session.node.ActionListener actionListener =
                new telamin.fluxtion.audit.analyser.analyser.session.node.ActionListener(effects);
        cfg.addNode(actionListener, "actionListener");
        localAgent.setActionListener(actionListener);
        cfg.addNode(localAgent, "localAgentSession");
        // M74 P3 (§5.7, §8.3): permission prompts in the analyser — which requests reach the person, the one dialog, the
        // single-use grants. It PUSHES into localAgentSession, so it runs first for a fact both handle: on Stop the
        // pending request is answered deny before the interrupt is asked for (§4.5)
        cfg.addNode(new telamin.fluxtion.audit.analyser.analyser.session.node.LocalAgentPermission(localAgent, effects),
                "localAgentPermission");
        // M74 P2b (§5.6): Claude proposes, the analyser applies — the offer, the preview's verdicts, the person's answers
        // Option 2 (owner, 2026-10-18): a project from a starter template — the catalogue, and ONE chain for Claude's
        // starter item and the Project menu alike. onboardingProposal pushes an accepted starter item and its Apply here
        telamin.fluxtion.audit.analyser.analyser.session.node.StarterProject starterProject =
                new telamin.fluxtion.audit.analyser.analyser.session.node.StarterProject(effects);
        telamin.fluxtion.audit.analyser.analyser.session.node.OnboardingProposal onboarding =
                new telamin.fluxtion.audit.analyser.analyser.session.node.OnboardingProposal(localAgent, effects,
                        starterProject);
        cfg.addNode(onboarding, "onboardingProposal");
        cfg.addNode(starterProject, "starterProject");
        // M74 P2b (the simplified Move): a person's project switch is held while Claude Code runs, and the person asked
        boundary.setLocalAgentSession(localAgent);
        boundary.setOnboardingProposal(onboarding);
        // Option 2: the analyser's OWN open of a starter project is never a switch the person must be asked about
        boundary.setStarterProject(starterProject);
        // M74 P2b (owner request 2026-10-17): a prompt pack's example entry — confirm, fetch, verify, unpack, offer
        cfg.addNode(new telamin.fluxtion.audit.analyser.analyser.session.node.ExampleDownload(effects), "exampleDownload");
        // M74 UX (owner, 2026-10-17), M77 (spec-ai-setup-wizard.md §8.1, OD-77.13 — one node): the assistant profile and
        // Set up AI — whether Apply may be applied now (never mid-turn), the wizard's steps, the checks, the first-run
        // block and the AI line. Downstream of both assistants (it reads whether a turn is open), of the action listener
        // and of the open log (JavaBean parents) and of startPageNoLogHint (a held no-log question opens nothing);
        // upstream of the start page's prompt (a first-use question opens the
        // wizard instead of the no-AI fallback, and a carried question waits for its Finish)
        telamin.fluxtion.audit.analyser.analyser.session.node.AiSetup aiSetup =
                new telamin.fluxtion.audit.analyser.analyser.session.node.AiSetup(localAgent, assistantLoop, effects);
        aiSetup.setActionListener(actionListener);
        aiSetup.setOpenLog(openLog);
        cfg.addNode(aiSetup, "aiSetup");
        // M74 P2 (§5.8): the start page's "What would you like to do?" — its list and where its button goes
        telamin.fluxtion.audit.analyser.analyser.session.node.StartPagePrompt startPagePrompt =
                new telamin.fluxtion.audit.analyser.analyser.session.node.StartPagePrompt(assistantMode, localAgent,
                        openLog, effects);
        startPagePrompt.setAiSetup(aiSetup);
        // robot re-walk #3: an evidence question with no log open is held before the wizard, the cards or a placement see it
        telamin.fluxtion.audit.analyser.analyser.session.node.StartPageNoLogHint noLogHint =
                new telamin.fluxtion.audit.analyser.analyser.session.node.StartPageNoLogHint(openLog, effects);
        cfg.addNode(noLogHint, "startPageNoLogHint");
        aiSetup.setNoLogHint(noLogHint);
        startPagePrompt.setNoLogHint(noLogHint);
        cfg.addNode(startPagePrompt, "startPagePrompt");
        cfg.addNode(new telamin.fluxtion.audit.analyser.analyser.session.node.WalkPlayback(openLog, assistantLoop, effects), "walkPlayback");
        // evidence bundle capture (convergence): the capture skill's decisions, in the session (rule 9)
        cfg.addNode(new telamin.fluxtion.audit.analyser.analyser.session.node.EvidenceCapture(openLog, gate, effects), "evidenceCapture");
        cfg.addNode(new telamin.fluxtion.audit.analyser.analyser.session.node.DesignSession(gate), "designSession");
        cfg.addNode(new telamin.fluxtion.audit.analyser.analyser.session.node.SessionRecovery(gate), "sessionRecovery");

        // WITH a level: invocation tracing is compiled in, so every node that runs appears in the record
        // whether or not it made an auditLog call of its own. That is the regime in which absence from
        // the log really does mean the node did not run — which is the whole reason to audit our own
        // session transitions rather than merely log about them.
        //
        // fluxtion#25: this choice is fixed at generation time and cannot be changed without a key. We
        // are now living with the constraint we filed.
        cfg.addEventAudit(EventLogControlEvent.LogLevel.INFO);
    }

    @Override
    public void configureGeneration(FluxtionCompilerConfig cfg) {
        cfg.className("SessionProcessor");
        cfg.packageName("telamin.fluxtion.audit.analyser.analyser.session.generated");
        cfg.outputDirectory("src/main/java");
        // The GraphML ships inside the jar, so the analyser can open its OWN topology — the dog-food
        // check M44 asks for at the end of the milestone.
        cfg.resourcesOutputDirectory("src/main/resources");
        cfg.generateDescription(true);
        // No build timestamp: it would rewrite the committed source on every regeneration and turn a
        // no-op regen into a diff. The same reason the fixture generator pins its clock.
        cfg.addBuildTime(false);
    }
}
