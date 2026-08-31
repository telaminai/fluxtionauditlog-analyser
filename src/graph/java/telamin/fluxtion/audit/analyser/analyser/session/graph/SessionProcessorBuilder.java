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
import telamin.fluxtion.audit.analyser.analyser.session.node.Pairing;
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
        OpenLog openLog = new OpenLog(gate);
        OpenGraph openGraph = new OpenGraph(gate);
        SessionBoundary boundary = new SessionBoundary(gate, activeProject, openLog, openGraph, effects);
        EffectOutcomes outcomes = new EffectOutcomes(gate);
        // M44.2 — the review's F3: three questions the first draft merged into one node.
        Pairing pairing = new Pairing(openLog, openGraph);
        AuditInstallation auditInstallation = new AuditInstallation(openGraph);
        LogArrival logArrival = new LogArrival(gate, pairing, openGraph, effects);
        CoverageClaim coverageClaim = new CoverageClaim(pairing, auditInstallation, openGraph, openLog);
        IgnoredParameters ignoredParameters = new IgnoredParameters();

        // These names become the instanceIds in nodeLogs and the node ids in the GraphML — they are
        // what a reader of the audit log sees, so they are the vocabulary of the rule, not of Java.
        cfg.addNode(gate, "operationGate");
        cfg.addNode(activeProject, "activeProject");
        cfg.addNode(openLog, "openLog");
        cfg.addNode(openGraph, "openGraph");
        cfg.addNode(boundary, "sessionBoundary");
        cfg.addNode(effects, "effectQueue");
        cfg.addNode(outcomes, "effectOutcomes");
        cfg.addNode(pairing, "pairing");
        cfg.addNode(auditInstallation, "auditInstallation");
        cfg.addNode(logArrival, "logArrival");
        cfg.addNode(coverageClaim, "coverageClaim");
        cfg.addNode(ignoredParameters, "ignoredParameters");

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
