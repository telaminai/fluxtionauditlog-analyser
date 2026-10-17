package telamin.fluxtion.audit.analyser.analyser.session;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * M74 P2b (spec §5.6): Claude Code's set-up proposal as the {@code onboardingProposal} node decided it. The banner, the
 * review dialog and {@code context.proposals} RENDER this and decide nothing.
 *
 * <p>{@link Item#reason()} is CLAUDE's words, kept for the review dialog's box labelled <i>"Claude's reason —
 * unverified"</i>; {@link #contextRows()} — what an assistant reads — carries ids, kinds, states and the analyser's own
 * verdict words only, never a reason.
 *
 * @param id             {@code p-n}, or null when there has been no proposal
 * @param summary        Claude's one-sentence summary (its words), or ""
 * @param setupDir       the set-up directory the items are relative to, once the preview reported it
 * @param projectExists  the set-up directory already holds a project: no create row
 * @param previewed      every item was checked: the banner may show
 * @param items          the items, in Claude's order
 * @param create         the analyser's own create row: {@code NONE} (a project exists, or not yet previewed),
 *                       {@code PENDING}, {@code APPLYING}, {@code APPLIED}, {@code DECLINED}, {@code FAILED}
 * @param applying       an apply is in progress (the next item is asked for after each outcome)
 * @param pairing        the analyser's check after the last item, or ""
 * @param fit            the graph–log fit after the last item, or ""
 * @param answers        the node's answer per {@code propose} request (for the verb's reply), the last few
 */
public record ProposalState(String id, String summary, String setupDir, boolean projectExists, boolean previewed,
                            List<Item> items, String create, boolean applying, String pairing, String fit,
                            Map<Long, Answer> answers) {

    public enum ItemState { CHECKING, PENDING, APPLYING, APPLIED, DECLINED, REFUSED, OUTSIDE, FAILED }

    /** One item: the node's id, what Claude named, its state and the analyser's verdict, and why (refused, failed). */
    public record Item(String id, String kind, String path, String className, String reason, ItemState state,
                       String verdict, String why) {
        public Item {
            verdict = verdict == null ? "" : verdict;
            why = why == null ? "" : why;
        }

        /** Only a pending item can be answered Yes: an outside or refused one never is (OD-74.13). */
        public boolean applicable() {
            return state == ItemState.PENDING;
        }
    }

    /** What the verb returns for one call: accepted with the proposal id, or refused whole. */
    public record Answer(boolean accepted, String proposalId, String refusal) {
    }

    public ProposalState {
        summary = summary == null ? "" : summary;
        items = List.copyOf(items == null ? List.of() : items);
        create = create == null ? "NONE" : create;
        pairing = pairing == null ? "" : pairing;
        fit = fit == null ? "" : fit;
        answers = Map.copyOf(answers == null ? Map.of() : answers);
    }

    public static final ProposalState NONE = new ProposalState(null, "", null, false, false, List.of(), "NONE", false,
            "", "", Map.of());

    public boolean exists() {
        return id != null;
    }

    /** Something is still the person's to answer: the banner shows. */
    public boolean awaitingPerson() {
        if (!exists() || !previewed || applying) return false;
        if ("PENDING".equals(create)) return true;
        for (Item i : items) if (i.state() == ItemState.PENDING) return true;
        return false;
    }

    public long count(ItemState s) {
        return items.stream().filter(i -> i.state() == s).count();
    }

    /**
     * {@code context.proposals}: {@code [{id, kind, state, verdict, why?}]} — ids and enums plus the analyser's verdict
     * words; no Claude reason text, path or summary.
     */
    public List<Map<String, Object>> contextRows() {
        java.util.ArrayList<Map<String, Object>> out = new java.util.ArrayList<>();
        if (!exists()) return out;
        if (!"NONE".equals(create)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", id + ".create");
            row.put("kind", "createProject");
            row.put("state", create.toLowerCase(java.util.Locale.ROOT));
            out.add(row);
        }
        for (Item i : items) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", i.id());
            row.put("kind", i.kind() == null ? "unknown" : i.kind());
            row.put("state", i.state().name().toLowerCase(java.util.Locale.ROOT));
            row.put("verdict", i.verdict());
            if (!i.why().isBlank()) row.put("why", i.why());
            out.add(row);
        }
        if (!pairing.isBlank()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", id + ".check");
            row.put("kind", "check");
            row.put("pairing", pairing);
            row.put("fit", fit);
            out.add(row);
        }
        return out;
    }

    /** "Onboarding proposal: 3 applied, 1 declined" — the Project panel's row. */
    public String panelLine() {
        if (!exists()) return "";
        return "Onboarding proposal: " + count(ItemState.APPLIED) + " applied, " + count(ItemState.DECLINED) + " declined"
                + (count(ItemState.PENDING) > 0 ? ", " + count(ItemState.PENDING) + " waiting" : "");
    }
}
