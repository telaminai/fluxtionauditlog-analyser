package telamin.fluxtion.audit.analyser.analyser.session.node;

import com.telamin.fluxtion.runtime.annotations.OnEventHandler;
import com.telamin.fluxtion.runtime.annotations.PushReference;
import com.telamin.fluxtion.runtime.audit.EventLogSource;
import com.telamin.fluxtion.runtime.audit.EventLogger;
import com.telamin.fluxtion.runtime.audit.NullEventLogger;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEffects;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;
import telamin.fluxtion.audit.analyser.analyser.session.StarterState;

import java.util.ArrayList;
import java.util.List;

/**
 * Option 2 (owner, 2026-10-18: "Spring is our preferred route: start with a template, then load the skills"; "when the
 * project starts, sync the analyser with the state"): a project from one of the analyser's starter templates — every
 * step decided here (rule 9). The frame reads, downloads, writes, asks and opens, and reports each as a fact.
 *
 * <p><b>The catalogue.</b> Read when someone needs it — the menu, a hosted Claude Code session starting (its starters
 * are then in {@code context}), the start page's S4/S7 flows — and never by {@code context}, which renders what was read.
 * Spring starters come first and are recommended, whether or not a key is on this machine: a build that needs a key
 * says so (the commercial rule: no keyless runs; a teaching key is the paid entry).
 *
 * <p><b>Two ways in, ONE chain.</b> Claude's {@code propose {kind: starter}} (via {@code onboardingProposal}, which
 * pushes the accepted item here and hands over the person's Apply) and <i>Project ▸ New project from template…</i>
 * reach the same steps: defaults → (the folder) → download → skills → discovery → ONE combined review → the open →
 * the Spring design → a GraphML watch → (a proposal's) pointer-only draft for Claude Code, or (the menu's) the starter's
 * own commands to copy. Nothing is downloaded before the person's Apply, and nothing is ever built or run.
 */
public class StarterProject implements EventLogSource {

    @PushReference
    private final EffectQueue effects;

    private EventLogger auditLog = NullEventLogger.INSTANCE;
    // node-local state (not final: a final field is constructor-mapped by the generator)
    private String catalogue = StarterState.NOT_READ;
    private long entriesRef;
    private List<StarterState.Entry> entries = new ArrayList<>();
    /** A catalogue read in flight, and who asked for it: {@code menu}, {@code session}; 0 when none. */
    private long readRequest;
    private long counter;
    private long request;
    private String origin = "";
    private String phase = "IDLE";
    private String proposalId;
    private String itemId;
    private String idDigest;
    private long namesRef;
    private long artifactRef;
    private long groupRef;
    private long basePackageRef;
    private long defaultsRef;
    private String authoring = "";
    private boolean keyPresent;
    private long dirRef;
    private String dirShown;
    private String dirVerdict = "";
    private boolean referenceGuide;
    private long rootRef;
    private String rootShown;
    private boolean profilePresent;
    private long commandsRef;
    private List<String> vendored = new ArrayList<>();
    private List<String> kept = new ArrayList<>();
    private String offerDigest;
    private long offerRef;
    private boolean graphWatch;
    private String note = "";
    private StarterState published = StarterState.IDLE;
    private boolean dirty = true;

    public StarterProject(EffectQueue effects) {
        this.effects = effects;
    }

    @Override
    public void setLogger(EventLogger log) {
        this.auditLog = log;
    }

    // ---- the catalogue ---------------------------------------------------------------------------------------------------

    /** A hosted Claude Code session started: read the catalogue once, so its starters are in {@code context}. */
    @OnEventHandler
    public boolean onSpawned(SessionEvents.LocalAgentSpawned e) {
        if (e.failure() != SessionEvents.SpawnFailure.NONE || StarterState.READ.equals(catalogue) || readRequest != 0) {
            return false;
        }
        readRequest = ++counter;
        auditLog.info("starterCatalogue", "READ").info("purpose", "session");
        effects.request(new SessionEffects.ReadStarterCatalogueEffect(0L, readRequest, "session"));
        return false;
    }

    @OnEventHandler
    public boolean onCatalogueObserved(SessionEvents.StarterCatalogueObserved e) {
        if (e.request() == readRequest) readRequest = 0;
        catalogue = e.reachable() ? StarterState.READ : StarterState.UNREACHABLE;
        if (e.reachable()) {
            entriesRef = e.entriesRef();
            entries = order(e.entries());
        }
        auditLog.info("starterCatalogue", catalogue).info("entries", entries.size());
        if ("CATALOGUE".equals(phase) && e.request() == request) {
            if (!e.reachable() || entries.isEmpty()) {
                end("FAILED", "the starter catalogue could not be read, so nothing was created");
                effects.request(new SessionEffects.ShowWarningEffect(0L, note));
            } else {
                phase = "CHOOSING";
                effects.request(new SessionEffects.ChooseStarterEffect(0L, request, entriesRef));
            }
        }
        return changed();
    }

    /** Spring first, each Spring entry recommended; within each group the catalogue's own order (owner, 2026-10-18). */
    static List<StarterState.Entry> order(List<SessionEvents.StarterFacts> facts) {
        List<StarterState.Entry> spring = new ArrayList<>();
        List<StarterState.Entry> rest = new ArrayList<>();
        for (SessionEvents.StarterFacts f : facts) {
            boolean s = "spring".equals(f.authoring());
            (s ? spring : rest).add(new StarterState.Entry(f.idDigest(), f.authoring(), f.host(), f.mode(), f.keyNeed(), s));
        }
        spring.addAll(rest);
        return spring;
    }

    /** Whether the catalogue was read and names this id (by its digest) — what a proposal's starter is checked against. */
    public boolean knows(String digest) {
        return StarterState.READ.equals(catalogue) && published().entry(digest) != null;
    }

    public boolean catalogueRead() {
        return StarterState.READ.equals(catalogue);
    }

    // ---- the menu's way in ----------------------------------------------------------------------------------------------

    @OnEventHandler
    public boolean onMenuRequested(SessionEvents.StarterMenuRequested e) {
        if (busy()) {
            auditLog.info("starterRefused", "BUSY");
            effects.request(new SessionEffects.ShowStatusEffect(0L, BUSY));
            return false;
        }
        begin("MENU");
        phase = "CATALOGUE";
        readRequest = request;
        auditLog.info("starter", "CATALOGUE").info("origin", origin);
        effects.request(new SessionEffects.ReadStarterCatalogueEffect(0L, request, "menu"));
        return changed();
    }

    /** The combined review's design-folder row starts at Yes (owner, 2026-10-18); every other row at No. */
    public static final boolean DESIGN_ROW_DEFAULT_YES = true;

    public static final String BUSY = "A project is already being created from a starter — finish or cancel it first";

    @OnEventHandler
    public boolean onChosen(SessionEvents.StarterChosen e) {
        if (stale(e.request(), "CHOOSING", "StarterChosen")) return false;
        if (e.idDigest() == null || published().entry(e.idDigest()) == null) {
            end("CANCELLED", "Template selection cancelled");
            effects.request(new SessionEffects.ShowStatusEffect(0L, note));
            return changed();
        }
        idDigest = e.idDigest();
        authoring = published().entry(idDigest).authoring();
        phase = "DEFAULTS";
        effects.request(new SessionEffects.ReadStarterDefaultsEffect(0L, request, entriesRef, idDigest));
        return changed();
    }

    @OnEventHandler
    public boolean onConfigured(SessionEvents.StarterConfigured e) {
        if (stale(e.request(), "CONFIGURING", "StarterConfigured")) return false;
        if (!e.ok() || e.dirRef() == 0) {
            end("CANCELLED", "Template download cancelled");
            effects.request(new SessionEffects.ShowStatusEffect(0L, note));
            return changed();
        }
        namesRef = e.namesRef();
        dirRef = e.dirRef();
        dirShown = e.dirShown();
        referenceGuide = e.referenceGuide();
        download();
        return changed();
    }

    // ---- the proposal's way in (onboardingProposal pushes here) ----------------------------------------------------------

    /**
     * {@code onboardingProposal} accepted a starter item: the template file is read for its defaults (the receipt's only
     * other effect is the read-only preview). Nothing is downloaded. Returns the starter request the item is bound to.
     */
    public long beginFromProposal(String proposalId, String itemId, String idDigest, long artifactRef, long groupRef,
                                  long basePackageRef) {
        begin("PROPOSAL");
        this.proposalId = proposalId;
        this.itemId = itemId;
        this.idDigest = idDigest;
        this.artifactRef = artifactRef;
        this.groupRef = groupRef;
        this.basePackageRef = basePackageRef;
        StarterState.Entry entry = published().entry(idDigest);
        authoring = entry == null ? "" : entry.authoring();
        phase = "DEFAULTS";
        auditLog.info("starter", "DEFAULTS").info("origin", origin).info("item", itemId);
        effects.request(new SessionEffects.ReadStarterDefaultsEffect(0L, request, entriesRef, idDigest));
        changed();
        return request;
    }

    @OnEventHandler
    public boolean onDefaultsObserved(SessionEvents.StarterDefaultsObserved e) {
        if (stale(e.request(), "DEFAULTS", "StarterDefaultsObserved")) return false;
        if (!e.ok()) {
            end("FAILED", "The starter's template file could not be read: " + safe(e.failure()));
            if ("MENU".equals(origin)) effects.request(new SessionEffects.ShowWarningEffect(0L, note));
            return changed();
        }
        defaultsRef = e.defaultsRef();
        keyPresent = e.keyPresent();
        if (e.authoring() != null && !e.authoring().isBlank()) authoring = e.authoring();
        if ("MENU".equals(origin)) {
            phase = "CONFIGURING";
            effects.request(new SessionEffects.ConfigureStarterEffect(0L, request, entriesRef, idDigest, defaultsRef));
        } else {
            phase = "READY";
        }
        auditLog.info("starter", phase).info("keyPresent", keyPresent).info("authoring", authoring);
        return changed();
    }

    /** The review row's Choose…: the person's folder, observed before it can be applied. */
    @OnEventHandler
    public boolean onDestinationChosen(SessionEvents.StarterDestinationChosen e) {
        if (!"READY".equals(phase) || !"PROPOSAL".equals(origin) || proposalId == null
                || !proposalId.equals(e.proposalId()) || itemId == null || !itemId.equals(e.itemId())) {
            auditLog.info("staleFact", "StarterDestinationChosen").info("phase", phase);
            return false;
        }
        if (e.dirRef() == 0) {
            auditLog.info("starterFolder", "CANCELLED");
            return false;
        }
        effects.request(new SessionEffects.ObserveStarterDirectoryEffect(0L, request, e.dirRef(), e.dirShown()));
        return false;
    }

    @OnEventHandler
    public boolean onDirectoryObserved(SessionEvents.StarterDirectoryObserved e) {
        if (stale(e.request(), "READY", "StarterDirectoryObserved")) return false;
        dirRef = e.dirRef();
        dirShown = e.dirShown();
        dirVerdict = e.verdict() == null ? "" : e.verdict();
        auditLog.info("starterFolder", dirVerdict);
        return changed();
    }

    /** Whether the person's Apply can download now: the defaults are read and an empty or absent folder is chosen. */
    public boolean destinationReady() {
        return "READY".equals(phase) && ("ABSENT".equals(dirVerdict) || "EMPTY".equals(dirVerdict));
    }

    /**
     * The person's Apply with the starter row at Yes ({@code onboardingProposal} decided that much): the download. False —
     * and nothing requested — unless the row is ready: Apply before the preview, the defaults or the folder downloads
     * nothing.
     */
    public boolean apply(String proposalId, String itemId) {
        if (!"PROPOSAL".equals(origin) || this.proposalId == null || !this.proposalId.equals(proposalId)
                || this.itemId == null || !this.itemId.equals(itemId) || !destinationReady()) {
            auditLog.info("starterApply", "REFUSED").info("phase", phase).info("folder", dirVerdict);
            return false;
        }
        download();
        changed();
        return true;
    }

    /** The row answered No (or the review dismissed): nothing is downloaded, the chain ends. */
    public void decline(String proposalId) {
        if (!"PROPOSAL".equals(origin) || this.proposalId == null || !this.proposalId.equals(proposalId)) return;
        if (!"DEFAULTS".equals(phase) && !"READY".equals(phase)) return;
        end("DECLINED", "Nothing was downloaded: the starter row was answered No");
        changed();
    }

    // ---- the one chain --------------------------------------------------------------------------------------------------

    private void download() {
        phase = "DOWNLOADING";
        note = "Downloading the starter and unpacking it — nothing is built or run";
        auditLog.info("starter", "DOWNLOADING").info("origin", origin);
        effects.request(new SessionEffects.DownloadStarterEffect(0L, request, entriesRef, idDigest, namesRef,
                artifactRef, groupRef, basePackageRef, defaultsRef, dirRef, referenceGuide));
    }

    @OnEventHandler
    public boolean onInstalled(SessionEvents.StarterInstalled e) {
        if (stale(e.request(), "DOWNLOADING", "StarterInstalled")) return false;
        rootRef = e.rootRef();
        rootShown = e.rootShown();
        profilePresent = e.profilePresent();
        commandsRef = e.commandsRef();
        phase = "VENDORING";
        note = "Unpacked the starter into " + rootShown + " — adding its skills";
        StarterState.Entry entry = published().entry(idDigest);
        auditLog.info("starter", "VENDORING").info("profile", profilePresent);
        // add-a-node's regeneration marker is filled from the starter's own first-run record and key declaration
        // (owner, 2026-10-18); with no record (commandsRef 0) the marker is kept
        effects.request(new SessionEffects.VendorSkillsEffect(0L, request, rootRef, authoring,
                entry == null ? "embedded" : entry.host(), commandsRef, entry == null ? "" : entry.keyNeed()));
        return changed();
    }

    @OnEventHandler
    public boolean onInstallFailed(SessionEvents.StarterInstallFailed e) {
        // Both background adapters report this failure fact. A discovery failure happens AFTER the files were
        // installed: settle that phase too, without claiming the download was rolled back.
        boolean discovering = "DISCOVERING".equals(phase);
        if (stale(e.request(), discovering ? "DISCOVERING" : "DOWNLOADING", "StarterInstallFailed")) return false;
        end("FAILED", discovering ? "The starter was unpacked into " + rootShown
                + ", but its project could not be reviewed: " + safe(e.why())
                : "The starter was not created: " + safe(e.why()));
        if ("MENU".equals(origin)) effects.request(new SessionEffects.ShowWarningEffect(0L, note));
        return changed();
    }

    @OnEventHandler
    public boolean onSkillsVendored(SessionEvents.SkillsVendored e) {
        if (stale(e.request(), "VENDORING", "SkillsVendored")) return false;
        vendored = new ArrayList<>(e.written());
        kept = new ArrayList<>(e.kept());
        // a refused skill set is said, and the project still opens: the skills are an addition, never the starter
        note = e.refusal() == null ? "Added " + vendored.size() + " skill(s) under .claude/skills"
                + (kept.isEmpty() ? "" : "; kept " + kept.size() + " already there")
                : "No skills were added: " + safe(e.refusal());
        phase = "DISCOVERING";
        auditLog.info("starterSkills", e.refusal() == null ? "WRITTEN" : "REFUSED").info("written", vendored.size())
                .info("kept", kept.size());
        effects.request(new SessionEffects.RunDiscoveryEffect(0L, request, rootRef));
        return changed();
    }

    @OnEventHandler
    public boolean onDiscoveryObserved(SessionEvents.StarterDiscoveryObserved e) {
        if (stale(e.request(), "DISCOVERING", "StarterDiscoveryObserved")) return false;
        offerDigest = e.offerDigest();
        offerRef = e.offerRef();
        profilePresent = e.profilePresent();
        phase = "REVIEWING";
        auditLog.info("starter", "REVIEWING").info("roots", e.roots()).info("skills", e.skills()).info("graphs", e.graphs());
        // the design folder's row starts at Yes (owner, 2026-10-18): it is inside the project just created and is what
        // shows the Spring design; every row that reaches outside the project, or changes settings beyond it, stays No
        effects.request(new SessionEffects.ReviewStarterProjectEffect(0L, request, rootRef, offerRef, profilePresent,
                vendored, DESIGN_ROW_DEFAULT_YES));
        return changed();
    }

    @OnEventHandler
    public boolean onReviewAnswered(SessionEvents.StarterReviewAnswered e) {
        if (stale(e.request(), "REVIEWING", "StarterReviewAnswered")) return false;
        if (!e.open()) {
            end("NOT_OPENED", "The starter is in " + rootShown + "; the project was not opened");
            effects.request(new SessionEffects.ShowStatusEffect(0L, note));
            return changed();
        }
        if (e.offerDigest() != null && offerDigest != null && !offerDigest.equals(e.offerDigest())) {
            end("FAILED", "The review answered another offer than the one discovered, so nothing was opened");
            effects.request(new SessionEffects.ShowWarningEffect(0L, note));
            return changed();
        }
        phase = "OPENING";
        auditLog.info("starter", "OPENING").info("profile", profilePresent).info("roots", e.sourceRoots().size())
                .info("skills", e.skills().size()).info("graph", e.graphRef() != 0).info("designRoot", e.designRoot());
        effects.request(new SessionEffects.OpenStarterProjectEffect(0L, request, rootRef, offerRef, profilePresent,
                new SessionEvents.CreateSelection(e.sourceRoots(), e.skills(), false, offerDigest), e.graphRef(),
                e.designRoot()));
        return changed();
    }

    /** While the analyser opens its OWN starter project — never a switch the person must be asked about. */
    public boolean openingOwnProject() {
        return "OPENING".equals(phase);
    }

    @OnEventHandler
    public boolean onProjectOpened(SessionEvents.StarterProjectOpened e) {
        if (stale(e.request(), "OPENING", "StarterProjectOpened")) return false;
        if (!e.ok()) {
            end("FAILED", "The starter's project was not opened: " + safe(e.why()));
            effects.request(new SessionEffects.ShowWarningEffect(0L, note));
            return changed();
        }
        phase = "OPEN";
        graphWatch = true;
        note = "Opened " + rootShown + " — the design is shown; its graph is offered after the first build";
        auditLog.info("starter", "OPEN").info("origin", origin);
        // the analyser now shows the state the project starts in: its design at once, its graph when a build writes one
        effects.request(new SessionEffects.OpenStarterDesignEffect(0L, request, rootRef));
        effects.request(new SessionEffects.WatchStarterGraphmlEffect(0L, request, rootRef));
        if ("PROPOSAL".equals(origin)) {
            effects.request(new SessionEffects.ArmStarterDraftEffect(0L, request, rootRef, entriesRef, idDigest));
        } else {
            effects.request(new SessionEffects.ShowStarterCommandsEffect(0L, request, rootRef, commandsRef));
        }
        return changed();
    }

    @OnEventHandler
    public boolean onDesignOpened(SessionEvents.StarterDesignOpened e) {
        if (e.request() != request) return false;
        auditLog.info("starterDesign", e.ok()).info("why", String.valueOf(e.why()));
        return false;
    }

    @OnEventHandler
    public boolean onGraphmlAppeared(SessionEvents.StarterGraphmlAppeared e) {
        if (e.request() != request || !graphWatch) {
            auditLog.info("staleFact", "StarterGraphmlAppeared").info("request", e.request());
            return false;
        }
        graphWatch = false;
        auditLog.info("starterGraph", "APPEARED");
        effects.request(new SessionEffects.OfferStarterGraphEffect(0L, request, e.pathRef(), e.shown()));
        return changed();
    }

    @OnEventHandler(propagate = false)
    public boolean onGraphOfferAnswered(SessionEvents.StarterGraphOfferAnswered e) {
        auditLog.info("starterGraphOffer", e.open()).info("request", e.request());
        return false;
    }

    /** A project switch the person made after the starter opened ends the watch: its graph would be another project's. */
    @OnEventHandler(propagate = false)
    public boolean onProfileApplied(SessionEvents.ProfileApplied e) {
        if (!graphWatch || "OPENING".equals(phase)) return false;
        graphWatch = false;
        auditLog.info("starterGraph", "UNWATCHED");
        dirty = true;
        return false;
    }

    @OnEventHandler(propagate = false)
    public boolean onEffectStarted(SessionEvents.StarterEffectStarted e) {
        auditLog.info("starterEffect", e.what()).info("request", e.request());
        return false;
    }

    // ---- helpers --------------------------------------------------------------------------------------------------------

    private void begin(String origin) {
        request = ++counter;
        this.origin = origin;
        proposalId = null;
        itemId = null;
        idDigest = null;
        namesRef = 0;
        artifactRef = 0;
        groupRef = 0;
        basePackageRef = 0;
        defaultsRef = 0;
        authoring = "";
        keyPresent = false;
        dirRef = 0;
        dirShown = null;
        dirVerdict = "";
        referenceGuide = false;
        rootRef = 0;
        rootShown = null;
        profilePresent = false;
        commandsRef = 0;
        vendored = new ArrayList<>();
        kept = new ArrayList<>();
        offerDigest = null;
        offerRef = 0;
        graphWatch = false;
        note = "";
    }

    private void end(String phase, String words) {
        this.phase = phase;
        note = words;
        auditLog.info("starter", phase).info("origin", origin);
    }

    /** A chain is running: a second one waits. A shown review row (READY) is the proposal's, and ends with it. */
    private boolean busy() {
        return switch (phase) {
            case "CATALOGUE", "CHOOSING", "DEFAULTS", "CONFIGURING", "READY", "DOWNLOADING", "VENDORING", "DISCOVERING",
                 "REVIEWING", "OPENING" -> true;
            default -> false;
        };
    }

    private boolean stale(long req, String expected, String fact) {
        if (req == request && expected.equals(phase)) return false;
        auditLog.info("staleFact", fact).info("request", req).info("phase", phase);
        return true;
    }

    private static String safe(String s) {
        if (s == null) return "unknown";
        StringBuilder b = new StringBuilder();
        s.codePoints().limit(300).forEach(cp -> b.appendCodePoint(Character.isISOControl(cp) ? ' ' : cp));
        return b.toString();
    }

    private boolean changed() {
        dirty = true;
        return true;
    }

    private StarterState published() {
        return state();
    }

    public StarterState state() {
        if (dirty) {
            published = new StarterState(catalogue, entriesRef, entries, request, origin, phase, idDigest, defaultsRef,
                    keyPresent, dirRef, dirShown, dirVerdict, rootShown, vendored, kept, graphWatch, note);
            dirty = false;
        }
        return published;
    }

    /** The proposal this starter is bound to, and its item ({@code onboardingProposal} reads which facts are its own). */
    public String proposalId() {
        return proposalId;
    }

    public long request() {
        return request;
    }
}
