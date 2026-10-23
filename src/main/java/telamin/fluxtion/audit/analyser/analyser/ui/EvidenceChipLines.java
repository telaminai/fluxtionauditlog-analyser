package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.session.BundleProvenance;
import telamin.fluxtion.audit.analyser.analyser.session.LogObservationState;
import telamin.fluxtion.audit.analyser.analyser.session.SessionSnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * M79-3 (spec-first-minute §8.1): the evidence chip's words, from the {@link SessionSnapshot} and nothing else — one chip
 * at the top of the Context column in place of the verification rows, and the rows' own prose, verbatim, one click down.
 *
 * <p><b>Only a byte or digest check earns ✓</b> (OQ-1; M68 D-E6: unverified is never reported as proven unchanged, and
 * unchanged metadata is one of the unverified cases). The first state that applies, in this order:
 *
 * <ol>
 *   <li><b>⚠ changed</b>: Follow found the file changed or replaced, or its metadata no longer matches what was read;</li>
 *   <li><b>⚠ unknown</b>: nothing was observed, or the read could not be identified;</li>
 *   <li><b>Evidence ✓ checked</b>: a byte or digest check passed — Follow's byte comparison, or an evidence bundle
 *       verified on open (every member's SHA-256; unsigned) whose log is the one on screen;</li>
 *   <li><b>Evidence · as read</b>: an ordinary log whose metadata is unchanged, with no byte or digest check.</li>
 * </ol>
 *
 * <p>The glyph is never the only signal: the chip has words, and its accessible description is the state's summary.
 */
record EvidenceChipLines(State state, String chip, String summary, List<Line> details) {

    enum State { CHANGED, UNKNOWN, CHECKED, AS_READ }

    /** One row of the details: what it is, and its words as the surface that owns them states them. */
    record Line(String label, String words) {
    }

    EvidenceChipLines {
        details = List.copyOf(details);
    }

    static final String CHANGED_CHIP = "⚠ changed";
    static final String UNKNOWN_CHIP = "⚠ unknown";
    static final String CHECKED_CHIP = "Evidence ✓ checked";
    static final String AS_READ_CHIP = "Evidence · as read";

    static final String CHANGED_SUMMARY = "Something moved since this log was read.";
    static final String UNKNOWN_SUMMARY = "Nothing was observed, or the read could not be identified.";
    static final String CHECKED_SUMMARY = "A byte or digest check passed.";
    static final String AS_READ_SUMMARY = "The records shown are the bytes as they were read; nothing has re-checked them since.";

    /** The Context column's bundle row's words, before M79-3 moved them here. */
    static final String BUNDLE_WORDS = "verified on open; unsigned, so it is not authenticated to a sender";
    static final String BUNDLE_OTHER_LOG = "the bundle supplies the project; the log on screen is not the bundle's";
    static final String READ_UNIDENTIFIED = "the bytes read could not be identified by SHA-256: the file moved while it was read";

    /** The chip for {@code s}, or null when no log is open (there is no evidence to describe). */
    static EvidenceChipLines of(SessionSnapshot s) {
        if (s == null || !s.logOpen()) return null;
        LogObservationState o = s.logObservation();
        boolean observed = o.about(s.logGeneration());
        String verdict = s.logIdentity();
        boolean replaced = "UNVERIFIED".equals(verdict) || "REPLACEMENT".equals(verdict);
        BundleProvenance bundle = s.bundle();
        boolean bundleLog = bundle.fromBundle() && within(s.logPath(), bundle.workingCopy());

        List<Line> details = new ArrayList<>();
        // the records table's identity banner — the same words, from the same source (LogTablePanel)
        String banner = LogTablePanel.identityBannerText(verdict, s.logIdentityReason());
        if (banner != null) details.add(new Line("File identity", banner));
        else if ("VERIFIED".equals(verdict)) details.add(new Line("File identity", "verified — " + s.logIdentityReason()));
        // the Context column's former "File observation" row: its state and its basis, verbatim
        details.add(observed
                ? new Line("File observation", o.state() + " — " + o.basis())
                : new Line("File observation", "unknown — no read-time metadata observation"));
        if (observed && o.readReported() && !o.readIdentified()) details.add(new Line("Read identity", READ_UNIDENTIFIED));
        // the Context column's former bundle row's words, and the limits the bundle itself stated
        if (bundle.fromBundle()) {
            details.add(new Line("Evidence bundle " + bundle.shortIdentity(), BUNDLE_WORDS));
            if (!bundle.limits().isBlank()) details.add(new Line("Bundle limits", bundle.limits()));
            if (!bundleLog) details.add(new Line("Bundle", BUNDLE_OTHER_LOG));
        }

        State state;
        if (replaced || (observed && "changed-on-disk".equals(o.state()))) state = State.CHANGED;
        else if (!observed || "unknown".equals(o.state()) || (o.readReported() && !o.readIdentified())) state = State.UNKNOWN;
        else if ("VERIFIED".equals(verdict) || bundleLog) state = State.CHECKED;
        else state = State.AS_READ;
        return switch (state) {
            case CHANGED -> new EvidenceChipLines(state, CHANGED_CHIP, CHANGED_SUMMARY, details);
            case UNKNOWN -> new EvidenceChipLines(state, UNKNOWN_CHIP, UNKNOWN_SUMMARY, details);
            case CHECKED -> new EvidenceChipLines(state, CHECKED_CHIP, CHECKED_SUMMARY, details);
            case AS_READ -> new EvidenceChipLines(state, AS_READ_CHIP, AS_READ_SUMMARY, details);
        };
    }

    /** Whether {@code path} lies inside {@code dir}, comparing normalised absolute paths. */
    private static boolean within(String path, String dir) {
        if (path == null || dir == null) return false;
        try {
            var p = java.nio.file.Path.of(path).toAbsolutePath().normalize();
            var d = java.nio.file.Path.of(dir).toAbsolutePath().normalize();
            return p.startsWith(d);
        } catch (RuntimeException e) {
            return false;
        }
    }
}
