package telamin.fluxtion.audit.analyser.analyser.session;

import java.util.List;

/**
 * Option 2 (owner, 2026-10-18): a project from one of the analyser's starter templates, as {@code starterProject} decided
 * it — the catalogue the analyser last read, and the one starter being created. The review row, the combined review,
 * {@code context.starters} and the status line RENDER this and decide nothing.
 *
 * <p>No catalogue word and no path is held here (R6): an entry is its id's digest and four vocabulary words, its name and
 * description stay in the frame's copy ({@code entriesRef}); a folder is a reference and {@code …/name}.
 *
 * @param catalogue  {@code not-read} (nothing has asked yet — {@code context} never fetches), {@code read} or
 *                   {@code unreachable}
 * @param entriesRef the frame's copy of the entries read (0: none)
 * @param entries    the entries, Spring first (owner, 2026-10-18), each Spring one recommended
 * @param request    the starter being created (0: none)
 * @param origin     {@code PROPOSAL} (Claude's starter item, confirmed in the review) or {@code MENU} (Project ▸ New
 *                   project from template…), "" for none
 * @param phase      where the chain is: {@code IDLE}, {@code CATALOGUE}, {@code CHOOSING}, {@code DEFAULTS},
 *                   {@code CONFIGURING}, {@code READY} (the review row is shown), {@code DOWNLOADING}, {@code VENDORING},
 *                   {@code DISCOVERING}, {@code REVIEWING}, {@code OPENING}, {@code OPEN}, {@code NOT_OPENED},
 *                   {@code DECLINED}, {@code CANCELLED}, {@code FAILED}
 * @param idDigest   the starter's id digest
 * @param defaultsRef the template file's defaults, by reference (0 until read)
 * @param keyPresent a Fluxtion key file is on this machine (observed with the defaults)
 * @param dirRef     the folder the person chose for the row, by reference (0: none yet)
 * @param dirShown   that folder as {@code …/name}
 * @param dirVerdict what the folder is ({@link SessionEvents.StarterDirectoryObserved}), "" before it was observed
 * @param rootShown  the unpacked project as {@code …/name}, once installed
 * @param vendored   the skill names written into the project
 * @param kept       the skill names left as they were (a file was already there)
 * @param graphWatch the GraphML watch is armed (the first build's graph is offered when it appears)
 * @param note       the analyser's own words about the last step, for the status line
 */
public record StarterState(String catalogue, long entriesRef, List<Entry> entries, long request, String origin,
                           String phase, String idDigest, long defaultsRef, boolean keyPresent, long dirRef,
                           String dirShown, String dirVerdict, String rootShown, List<String> vendored, List<String> kept,
                           boolean graphWatch, String note) {

    /** One catalogue entry as the node holds it. */
    public record Entry(String idDigest, String authoring, String host, String mode, String keyNeed,
                        boolean recommended) {
    }

    public StarterState {
        catalogue = catalogue == null ? NOT_READ : catalogue;
        entries = List.copyOf(entries == null ? List.of() : entries);
        origin = origin == null ? "" : origin;
        phase = phase == null ? "IDLE" : phase;
        dirVerdict = dirVerdict == null ? "" : dirVerdict;
        vendored = List.copyOf(vendored == null ? List.of() : vendored);
        kept = List.copyOf(kept == null ? List.of() : kept);
        note = note == null ? "" : note;
    }

    public static final String NOT_READ = "not-read";
    public static final String READ = "read";
    public static final String UNREACHABLE = "unreachable";

    public static final StarterState IDLE = new StarterState(NOT_READ, 0, List.of(), 0, "", "IDLE", null, 0, false, 0,
            null, "", null, List.of(), List.of(), false, "");

    /** The folder can receive a starter: it does not exist yet (its parent does), or it is an empty folder. */
    public boolean destinationReady() {
        return "ABSENT".equals(dirVerdict) || "EMPTY".equals(dirVerdict);
    }

    /** The entry whose id has {@code digest}, or null. */
    public Entry entry(String digest) {
        if (digest == null) return null;
        for (Entry e : entries) if (digest.equals(e.idDigest())) return e;
        return null;
    }

    /** The folder verdict in words, for the review row; "" before a folder was chosen. */
    public String dirWords() {
        return switch (dirVerdict) {
            case "" -> "";
            case "ABSENT" -> "a new folder — it will be created";
            case "EMPTY" -> "an empty folder";
            case "NOT_EMPTY" -> "not empty — choose a new or empty folder";
            case "NOT_A_DIRECTORY" -> "not a folder";
            case "NO_PARENT" -> "its parent folder does not exist";
            case "NOT_ABSOLUTE" -> "not a full path";
            case "SECRET_FOLDER" -> "your home folder or a credential folder — never a project";
            default -> dirVerdict;
        };
    }
}
