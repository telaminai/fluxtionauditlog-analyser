package telamin.fluxtion.audit.analyser.analyser.session.node;

import com.telamin.fluxtion.runtime.annotations.OnEventHandler;
import com.telamin.fluxtion.runtime.annotations.PushReference;
import com.telamin.fluxtion.runtime.audit.EventLogSource;
import com.telamin.fluxtion.runtime.audit.EventLogger;
import com.telamin.fluxtion.runtime.audit.NullEventLogger;
import telamin.fluxtion.audit.analyser.analyser.session.ProposalState;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEffects;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * M74 P2b (spec §5.6, OD-74.9, OD-74.13): <b>Claude proposes, the analyser applies.</b> Every decision about a hosted
 * {@code propose} offer, in one place (rule 9).
 *
 * <ul>
 *   <li><b>Receipt</b> records an offer and changes nothing: a second proposal while one is open is refused whole; each
 *       item is refused on its own for an unknown kind, a missing path, a field over its length, a malformed class
 *       name, a duplicate, or a second processor or log. The only effect is the read-only preview.</li>
 *   <li><b>Preview</b> facts mark an item OUTSIDE the set-up directory (shown, never applicable) or REFUSED (not found,
 *       the wrong type), else PENDING with the analyser's own verdict. The banner shows once every item is checked.</li>
 *   <li><b>Apply</b> is the person's Apply chosen: every item not answered Yes is DECLINED; the Yes items are applied
 *       one at a time in the fixed order — create, source roots, processor, runbooks, GraphML, log — each through the
 *       person's own path ({@link SessionEffects.ApplyProposalItemEffect}), the next only after the previous outcome.</li>
 * </ul>
 *
 * <p>It never applies on receipt, never raises a modal (the banner is rendering) and never asks Claude anything.
 *
 * <p><b>Option 2 (owner, 2026-10-18): a starter item.</b> {@code {kind: starter, starter: <catalogue id>}} — create a
 * project from one of the analyser's starter templates — stands alone in its proposal (the shape refuses it beside
 * anything else). It names no path: the person chooses the folder in the review. Its id is checked against the
 * catalogue the analyser read; the accepted item is pushed to {@code starterProject}, which reads the template file's
 * defaults now and downloads nothing until the person's Apply with the row at Yes and an empty or absent folder chosen.
 * The person's Apply is the consent, so the {@code assistant.createProjects} switch does not gate this path.
 */
public class OnboardingProposal implements EventLogSource {

    private final LocalAgentSession localAgent;
    @PushReference
    private final EffectQueue effects;
    /** Option 2: the accepted starter item is pushed here, and the person's Apply is handed over (rule 9: one chain). */
    @PushReference
    private final StarterProject starter;

    private EventLogger auditLog = NullEventLogger.INSTANCE;
    // node-local state (not final: a final field is constructor-mapped by the generator)
    private int counter;
    private String id;
    private long summaryRef;
    private long setupDirRef;
    private String setupDirShown;
    private boolean projectExists;
    private boolean previewed;
    private List<ProposalState.Item> items = new ArrayList<>();
    private String create = "NONE";
    private boolean applying;
    private List<String> queue = new ArrayList<>();
    private String pairing = "";
    private String fit = "";
    /**
     * Review P2 N-a (2026-10-17): the final check ran while an item's open was still loading, so it read the OLD pairing.
     * The performer then answers {@code PENDING}; this node asks again when a log or graph result lands, a bounded number
     * of times, so the verdict shown is the one after the loads.
     */
    private boolean checkAwaiting;
    private int rechecks;
    static final int MAX_RECHECKS = 4;
    private Map<Long, ProposalState.Answer> answers = new LinkedHashMap<>();
    /** R5: the person's per-row answers to the open proposal, by item id ({@code create} for the create row). */
    private Map<String, Boolean> itemAnswers = new LinkedHashMap<>();
    /** Option 2: the starter item of the open proposal (null: none), its starterProject request, and its chain running. */
    private String starterItem;
    private long starterRequest;
    private boolean starterApplying;
    /** A starter proposal (its starter item accepted or not): never offered the analyser's empty-create row. */
    private boolean starterProposal;
    private ProposalState published = ProposalState.NONE;
    private boolean dirty = true;

    /** The fixed apply order (§5.6 step 6). */
    static final List<String> ORDER = List.of("create", "sourceRoot", "processor", "runbook", "graphml", "logToOpen");
    static final Set<String> KINDS = Set.of("sourceRoot", "processor", "graphml", "runbook", "logToOpen", "starter");
    static final String STARTER = "starter";

    public static final String ALREADY_WAITING = "a proposal is already waiting for the person; call context to see its "
            + "outcome";

    public OnboardingProposal(LocalAgentSession localAgent, EffectQueue effects, StarterProject starter) {
        this.localAgent = localAgent;
        this.effects = effects;
        this.starter = starter;
    }

    @Override
    public void setLogger(EventLogger log) {
        this.auditLog = log;
    }

    // ---- receipt ------------------------------------------------------------------------------------------------------

    @OnEventHandler
    public boolean onReceived(SessionEvents.ProposalReceived e) {
        // re-checked here, on the session thread: the call's generation must be the one admitted now
        long admitted = localAgent.admittedGeneration();
        if (admitted == 0 || admitted != e.generation()) {
            answer(e.request(), new ProposalState.Answer(false, null, "the Claude Code session that called propose is "
                    + "not the one bound now (generation " + e.generation() + "); nothing was recorded"));
            auditLog.info("proposal", "REFUSED").info("why", "notAdmitted");
            return changed();
        }
        if (open()) {
            answer(e.request(), new ProposalState.Answer(false, id, ALREADY_WAITING));
            auditLog.info("proposal", "REFUSED").info("why", "alreadyWaiting");
            return changed();
        }
        counter++;
        id = "p-" + counter;
        itemAnswers = new LinkedHashMap<>();
        summaryRef = e.summaryRef();
        setupDirRef = 0;
        setupDirShown = null;
        projectExists = false;
        previewed = false;
        create = "NONE";
        applying = false;
        queue = new ArrayList<>();
        pairing = "";
        fit = "";
        checkAwaiting = false;
        rechecks = 0;
        starterItem = null;
        starterRequest = 0;
        starterApplying = false;
        starterProposal = e.items().stream().anyMatch(f -> STARTER.equals(f.kind()));
        items = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        SessionEvents.ProposalItemFact starterFact = null;
        boolean processor = false;
        boolean log = false;
        List<SessionEffects.ProposalItemRef> toPreview = new ArrayList<>();
        int n = 0;
        for (SessionEvents.ProposalItemFact f : e.items()) {
            n++;
            String itemId = id + "." + n;
            String why = STARTER.equals(f.kind()) ? starterRefusal(f) : itemRefusal(f, seen, processor, log);
            if (why == null && STARTER.equals(f.kind())) {
                starterItem = itemId;
                starterFact = f;
            } else if (why == null) {
                if ("processor".equals(f.kind())) processor = true;
                if ("logToOpen".equals(f.kind())) log = true;
                seen.add(key(f));
                toPreview.add(new SessionEffects.ProposalItemRef(itemId, f.kind(), f.pathRef(), f.classNameRef()));
            }
            items.add(new ProposalState.Item(itemId, KINDS.contains(f.kind()) ? f.kind() : "unknown", f.pathRef(),
                    f.classNameRef(), f.reasonRef(), why == null ? ProposalState.ItemState.CHECKING
                    : ProposalState.ItemState.REFUSED, why == null ? "" : "REFUSED", why));
        }
        // nothing is ever applied on receipt: CHECKING until the analyser's own preview answers
        auditLog.info("proposal", id).info("items", items.size()).info("previewing", toPreview.size())
                .info("starter", starterItem != null);
        answer(e.request(), new ProposalState.Answer(true, id, null));
        effects.request(new SessionEffects.PreviewProposalEffect(0L, id, toPreview));
        if (starterFact != null) {
            // Option 2: the template file's defaults are read now (read-only); nothing is downloaded before the Apply
            starterRequest = starter.beginFromProposal(id, starterItem, starterFact.starterDigest(),
                    starterFact.artifactRef(), starterFact.groupRef(), starterFact.basePackageRef());
        }
        return changed();
    }

    /**
     * Option 2's item-level refusals for a starter, in a fixed order: it takes no path (the person chooses the folder in
     * the review) and no class name; it needs a reason and a catalogue id; its names must be identifiers; the catalogue
     * must have been read and must name the id. The words are the analyser's — never Claude's id.
     */
    private String starterRefusal(SessionEvents.ProposalItemFact f) {
        if (f.hasPath()) return "a starter item takes no path: the person chooses its folder in the review";
        if (f.hasClassName()) return "a starter item takes no className";
        if (f.tooLong()) return "a field is over its length (starter, artifact, group, basePackage 100; reason 500)";
        if (!f.hasReason()) return "the item has no reason";
        if (!f.hasStarter()) return "a starter item needs 'starter': an id from context.starters";
        if (f.namesMalformed()) return "starter, artifact, group or basePackage is not a valid identifier";
        if (!starter.catalogueRead()) {
            return "the analyser has not read the starter catalogue (context.starters.state is not read) — nothing was "
                    + "recorded for this item";
        }
        if (!starter.knows(f.starterDigest())) return "not a starter in the catalogue the analyser read (context.starters)";
        return null;
    }

    /** Whether a proposal is still open: not every item is answered, or the preview has not reported. */
    private boolean open() {
        if (id == null) return false;
        if (!previewed || applying || starterApplying || "PENDING".equals(create)) return true;
        for (ProposalState.Item i : items) {
            if (i.state() == ProposalState.ItemState.PENDING || i.state() == ProposalState.ItemState.CHECKING
                    || i.state() == ProposalState.ItemState.APPLYING) return true;
        }
        return false;
    }

    /** §5.6's item-level refusals, by the item's own fields, in a fixed order; null when the item may be previewed. */
    static String itemRefusal(SessionEvents.ProposalItemFact f, Set<String> seen, boolean processorTaken, boolean logTaken) {
        if (f.kind() == null || !KINDS.contains(f.kind())) {
            return "unknown kind '" + (f.kind() == null ? "" : f.kind()) + "'";
        }
        if (f.tooLong()) return "a field is over its length (path 1024, className 300, reason 500)";
        if (!f.hasReason()) return "the item has no reason";
        if ("processor".equals(f.kind())) {
            if (!f.hasClassName() && !f.hasPath()) return "a processor item needs className or path";
            if (f.hasClassName() && f.classNameMalformed()) return "className is not a Java qualified name";
            if (processorTaken) return "a second processor item (one processor per proposal)";
        } else if (!f.hasPath()) {
            return "a " + f.kind() + " item needs a path";
        }
        if ("logToOpen".equals(f.kind()) && logTaken) return "a second logToOpen item (one log per proposal)";
        if (seen.contains(key(f))) return "a duplicate of an earlier item";
        return null;
    }

    /** What makes two items the same: the kind, and the path's digest (else the class name's) — never the words. */
    private static String key(SessionEvents.ProposalItemFact f) {
        return f.kind() + "\u0000" + (f.hasPath() ? f.pathDigest() : f.classNameDigest());
    }

    // ---- the preview ----------------------------------------------------------------------------------------------------

    @OnEventHandler
    public boolean onPreviewed(SessionEvents.ProposalItemPreviewed e) {
        if (id == null || !id.equals(e.proposalId())) {
            auditLog.info("staleFact", "ProposalItemPreviewed").info("proposal", String.valueOf(e.proposalId()));
            return false;
        }
        for (int i = 0; i < items.size(); i++) {
            ProposalState.Item x = items.get(i);
            if (!x.id().equals(e.itemId()) || x.state() != ProposalState.ItemState.CHECKING) continue;
            ProposalState.ItemState s;
            String why = "";
            if (e.outside()) {
                s = ProposalState.ItemState.OUTSIDE;
                why = "outside the set-up directory — shown, never applied";
            } else if (e.refusal() != null) {
                s = ProposalState.ItemState.REFUSED;
                why = switch (e.refusal()) {
                    case "NOT_FOUND" -> "it does not exist";
                    case "WRONG_TYPE" -> "it is not the kind of file or folder the item names";
                    // P5 (owner decision 2026-10-18): tier 1 of the source-root policy, whoever proposes it
                    case "SECRET_FOLDER" -> "it is your home folder or a credential folder (or holds one) — never a "
                            + "source root";
                    default -> e.refusal();
                };
            } else {
                s = ProposalState.ItemState.PENDING;
            }
            items.set(i, new ProposalState.Item(x.id(), x.kind(), x.pathRef(), x.classNameRef(), x.reasonRef(), s,
                    e.verdict(), why));
            return changed();
        }
        return false;
    }

    @OnEventHandler
    public boolean onPreviewDone(SessionEvents.ProposalPreviewDone e) {
        if (id == null || !id.equals(e.proposalId())) return false;
        // an item the performer never reported is refused rather than left "being checked" (a starter item is checked
        // by its template file's read, not by the preview)
        for (int i = 0; i < items.size(); i++) {
            ProposalState.Item x = items.get(i);
            if (x.state() == ProposalState.ItemState.CHECKING && !x.id().equals(starterItem)) {
                items.set(i, new ProposalState.Item(x.id(), x.kind(), x.pathRef(), x.classNameRef(), x.reasonRef(),
                        ProposalState.ItemState.REFUSED, "UNKNOWN", "the analyser could not check it"));
            }
        }
        previewed = true;
        setupDirRef = e.setupDirRef();
        setupDirShown = e.setupDirShown();
        projectExists = e.projectExists();
        // Option 2: a starter makes its own project — the analyser's empty-create row is never offered beside it
        create = e.projectExists() || starterProposal ? "NONE" : "PENDING";
        auditLog.info("proposalPreviewed", id).info("pending", count(ProposalState.ItemState.PENDING))
                .info("createRow", !e.projectExists());
        return changed();
    }

    // ---- the person's answers ----------------------------------------------------------------------------------------------

    /**
     * Scorecard R5: one row's answer, recorded as the person gave it. Apply chosen then applies the Yes rows; a row
     * answered No here is never applied, whatever the apply's own list says.
     */
    @OnEventHandler
    public boolean onItemAnswered(SessionEvents.ProposalItemAnswered e) {
        if (id == null || !id.equals(e.proposalId()) || !previewed || applying) {
            auditLog.info("staleFact", "ProposalItemAnswered").info("proposal", String.valueOf(e.proposalId()));
            return false;
        }
        boolean known = "create".equals(e.itemId()) ? "PENDING".equals(create) : item(e.itemId()) != null
                && item(e.itemId()).state() == ProposalState.ItemState.PENDING;
        if (!known) {
            auditLog.info("staleFact", "ProposalItemAnswered").info("item", String.valueOf(e.itemId()));
            return false;
        }
        itemAnswers.put(e.itemId(), e.yes());
        auditLog.info("proposalItemAnswered", e.itemId()).info("yes", e.yes());
        return false;
    }

    @OnEventHandler
    public boolean onApplyRequested(SessionEvents.ProposalApplyRequested e) {
        if (id == null || !id.equals(e.proposalId()) || !previewed || applying) {
            auditLog.info("staleFact", "ProposalApplyRequested").info("proposal", String.valueOf(e.proposalId()));
            return false;
        }
        Set<String> yes = new HashSet<>(e.yes());
        // R5: a row the person answered No is never applied, whatever the apply's list says
        for (Map.Entry<String, Boolean> a : itemAnswers.entrySet()) {
            if (Boolean.FALSE.equals(a.getValue())) yes.remove(a.getKey());
        }
        if (starterItem != null) return applyStarter(yes);
        boolean createAnsweredNo = Boolean.FALSE.equals(itemAnswers.get("create"));
        itemAnswers = new LinkedHashMap<>();
        queue = new ArrayList<>();
        boolean createYes = e.createProject() && !createAnsweredNo && "PENDING".equals(create);
        if ("PENDING".equals(create)) create = createYes ? "APPLYING" : "DECLINED";
        if (createYes) queue.add("create");
        for (String kind : ORDER) {
            for (int i = 0; i < items.size(); i++) {
                ProposalState.Item x = items.get(i);
                if (!kind.equals(x.kind()) || x.state() != ProposalState.ItemState.PENDING) continue;
                // an item that needs a project is applicable only with one: an existing one or the create row's Yes
                boolean needsProject = !x.kind().equals("logToOpen") && !x.kind().equals("graphml");
                boolean can = !needsProject || projectExists || createYes;
                if (yes.contains(x.id()) && can) {
                    items.set(i, with(x, ProposalState.ItemState.APPLYING, ""));
                    queue.add(x.id());
                } else {
                    items.set(i, with(x, ProposalState.ItemState.DECLINED,
                            yes.contains(x.id()) ? "it needs a project, and Create a project was not chosen" : ""));
                }
            }
        }
        auditLog.info("proposalApply", id).info("queued", queue.size()).info("create", createYes);
        applying = !queue.isEmpty();
        checkAwaiting = false;
        rechecks = 0;
        next();
        return changed();
    }

    /**
     * Option 2: the person's Apply for a starter proposal. A Yes row whose template file was read and whose folder is
     * empty or absent is handed to {@code starterProject}, which downloads; anything else is declined and nothing is
     * downloaded — a No row creates nothing, and an Apply before the folder (or the preview) downloads nothing.
     */
    private boolean applyStarter(Set<String> yes) {
        itemAnswers = new LinkedHashMap<>();
        ProposalState.Item x = item(starterItem);
        if (x == null || x.state() != ProposalState.ItemState.PENDING) return changed();
        if (yes.contains(starterItem) && starter.apply(id, starterItem)) {
            mark(starterItem, ProposalState.ItemState.APPLYING, "");
            starterApplying = true;
            auditLog.info("proposalApply", id).info("starter", "DOWNLOADING");
        } else {
            mark(starterItem, ProposalState.ItemState.DECLINED, yes.contains(starterItem)
                    ? "no empty or new folder was chosen for it, so nothing was downloaded" : "");
            starter.decline(id);
            auditLog.info("proposalApply", id).info("starter", "DECLINED");
        }
        return changed();
    }

    /** Option 2: the template file was read — the starter row can be answered (or it is refused, with why). */
    @OnEventHandler
    public boolean onStarterDefaults(SessionEvents.StarterDefaultsObserved e) {
        if (starterItem == null || e.request() != starterRequest) return false;
        ProposalState.Item x = item(starterItem);
        if (x == null || x.state() != ProposalState.ItemState.CHECKING) return false;
        items.set(items.indexOf(x), new ProposalState.Item(x.id(), x.kind(), x.pathRef(), x.classNameRef(),
                x.reasonRef(), e.ok() ? ProposalState.ItemState.PENDING : ProposalState.ItemState.REFUSED,
                e.ok() ? "TEMPLATE_READ" : "UNKNOWN", e.ok() ? "" : "its template file could not be read"));
        return changed();
    }

    @OnEventHandler
    public boolean onStarterInstallFailed(SessionEvents.StarterInstallFailed e) {
        return starterEnded(e.request(), ProposalState.ItemState.FAILED, "the starter was not downloaded or unpacked");
    }

    @OnEventHandler
    public boolean onStarterReviewAnswered(SessionEvents.StarterReviewAnswered e) {
        if (e.open()) return false;
        return starterEnded(e.request(), ProposalState.ItemState.APPLIED, "downloaded; the project was not opened");
    }

    @OnEventHandler
    public boolean onStarterProjectOpened(SessionEvents.StarterProjectOpened e) {
        return starterEnded(e.request(), e.ok() ? ProposalState.ItemState.APPLIED : ProposalState.ItemState.FAILED,
                e.ok() ? "" : "the project was not opened");
    }

    private boolean starterEnded(long request, ProposalState.ItemState state, String why) {
        if (!starterApplying || request != starterRequest) return false;
        starterApplying = false;
        mark(starterItem, state, why);
        auditLog.info("proposalItem", starterItem).info("outcome", state.name());
        return changed();
    }

    @OnEventHandler
    public boolean onDismissed(SessionEvents.ProposalDismissed e) {
        if (id == null || !id.equals(e.proposalId()) || applying || starterApplying) return false;
        if (starterItem != null) starter.decline(id);
        if ("PENDING".equals(create)) create = "DECLINED";
        for (int i = 0; i < items.size(); i++) {
            ProposalState.Item x = items.get(i);
            if (x.state() == ProposalState.ItemState.PENDING) items.set(i, with(x, ProposalState.ItemState.DECLINED, ""));
        }
        auditLog.info("proposalDismissed", id);
        return changed();
    }

    @OnEventHandler
    public boolean onApplied(SessionEvents.ProposalItemApplied e) {
        if (id == null || !id.equals(e.proposalId()) || !applying || queue.isEmpty() || !queue.get(0).equals(e.itemId())) {
            auditLog.info("staleFact", "ProposalItemApplied").info("item", String.valueOf(e.itemId()));
            return false;
        }
        queue.remove(0);
        boolean ok = "APPLIED".equals(e.outcome());
        auditLog.info("proposalItem", e.itemId()).info("outcome", e.outcome());
        if ("create".equals(e.itemId())) {
            create = ok ? "APPLIED" : "FAILED";
            if (!ok) {
                // nothing that needed the project can follow a failed create
                List<String> rest = new ArrayList<>();
                for (String q : queue) {
                    ProposalState.Item x = item(q);
                    boolean needsProject = x != null && !x.kind().equals("logToOpen") && !x.kind().equals("graphml");
                    if (needsProject && !projectExists) mark(q, ProposalState.ItemState.FAILED, "the project was not created");
                    else rest.add(q);
                }
                queue = rest;
            } else {
                projectExists = true;
            }
        } else {
            mark(e.itemId(), ok ? ProposalState.ItemState.APPLIED
                    : "REFUSED".equals(e.outcome()) ? ProposalState.ItemState.REFUSED : ProposalState.ItemState.FAILED,
                    ok ? "" : e.why());
        }
        next();
        return changed();
    }

    @OnEventHandler
    public boolean onChecked(SessionEvents.ProposalChecked e) {
        if (id == null || !id.equals(e.proposalId())) return false;
        pairing = e.pairing() == null ? "UNKNOWN" : e.pairing();
        fit = e.fit() == null ? "UNKNOWN" : e.fit();
        checkAwaiting = PENDING.equals(pairing) && rechecks < MAX_RECHECKS;
        auditLog.info("proposalChecked", id).info("pairing", pairing).info("fit", fit).info("awaiting", checkAwaiting);
        return changed();
    }

    /** What the check answers while a load an item started has not settled (N-a). */
    public static final String PENDING = "PENDING";

    /** N-a: a log or graph result landed — if the check is awaiting the loads, it is asked again now. */
    private boolean recheck(String what) {
        if (!checkAwaiting || id == null) return false;
        checkAwaiting = false;
        rechecks++;
        auditLog.info("proposalRecheck", what).info("n", rechecks);
        effects.request(new SessionEffects.CheckProposalOutcomeEffect(0L, id));
        return false;
    }

    @OnEventHandler(propagate = false)
    public boolean onLogOpened(SessionEvents.LogOpened e) {
        return recheck("LogOpened");
    }

    @OnEventHandler(propagate = false)
    public boolean onLogOpenFailed(SessionEvents.LogOpenFailed e) {
        return recheck("LogOpenFailed");
    }

    @OnEventHandler(propagate = false)
    public boolean onGraphOpened(SessionEvents.GraphOpened e) {
        return recheck("GraphOpened");
    }

    @OnEventHandler(propagate = false)
    public boolean onEffectStarted(SessionEvents.ProposalEffectStarted e) {
        auditLog.info("proposalEffect", e.what());
        return false;
    }

    /** Ask for the next queued item, or, after the last, the analyser's own check. */
    private void next() {
        if (queue.isEmpty()) {
            if (applying) {
                applying = false;
                effects.request(new SessionEffects.CheckProposalOutcomeEffect(0L, id));
            }
            return;
        }
        String q = queue.get(0);
        SessionEffects.ProposalItemRef ref = "create".equals(q)
                ? new SessionEffects.ProposalItemRef("create", "createProject", 0, 0)
                : toRef(item(q));
        effects.request(new SessionEffects.ApplyProposalItemEffect(0L, id, ref, setupDirRef));
    }

    /** True while the analyser's OWN create is being applied — never a switch the person must be asked about (§5.6 step 7). */
    public boolean applyingOwnCreate() {
        return applying && !queue.isEmpty() && "create".equals(queue.get(0));
    }

    private static SessionEffects.ProposalItemRef toRef(ProposalState.Item x) {
        return new SessionEffects.ProposalItemRef(x.id(), x.kind(), x.pathRef(), x.classNameRef());
    }

    private ProposalState.Item item(String itemId) {
        for (ProposalState.Item x : items) if (x.id().equals(itemId)) return x;
        return null;
    }

    private void mark(String itemId, ProposalState.ItemState s, String why) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).id().equals(itemId)) items.set(i, with(items.get(i), s, why));
        }
    }

    private static ProposalState.Item with(ProposalState.Item x, ProposalState.ItemState s, String why) {
        return new ProposalState.Item(x.id(), x.kind(), x.pathRef(), x.classNameRef(), x.reasonRef(), s, x.verdict(), why);
    }

    private long count(ProposalState.ItemState s) {
        return items.stream().filter(i -> i.state() == s).count();
    }

    private void answer(long request, ProposalState.Answer a) {
        answers.put(request, a);
        while (answers.size() > 16) answers.remove(answers.keySet().iterator().next());
    }

    private boolean changed() {
        dirty = true;
        return true;
    }

    public ProposalState state() {
        if (dirty) {
            published = new ProposalState(id, summaryRef, setupDirRef, setupDirShown, projectExists, previewed, items,
                    create, applying, pairing, fit, answers);
            dirty = false;
        }
        return published;
    }
}
