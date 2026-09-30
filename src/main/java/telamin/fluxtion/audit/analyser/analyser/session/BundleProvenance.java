package telamin.fluxtion.audit.analyser.analyser.session;

/**
 * Where the session in force came from: a verified evidence bundle, or the person's own project.
 *
 * <p>Published in the {@link SessionSnapshot} so a surface can say so for the whole session. Before this
 * existed the facts were computed and then thrown away — {@code SessionBoundary} held the plan only while the
 * transition was in flight and cleared it on settling, so the window title, the Project panel and the recents
 * list had nothing to render and nothing to remember a bundle against. A non-modal dialog at open time was the
 * only place a recipient was ever told, and dismissing it lost the provenance for good.
 *
 * <p><b>It is about the PROJECT, not the log on screen.</b> A bundle supplies a project; opening any other
 * audit log afterwards leaves that project — and therefore this provenance — in force. So the window can say
 * "evidence bundle X" while the records shown are the person's own, and that is correct but easy to over-read.
 * It is deliberate: the alternative, clearing the claim when an unrelated log opens, would be a lie in the
 * other direction, because the bundle's charts, walks, reports and source anchor are all still the ones in
 * use. {@code BundleProvenanceTest#theClaimIsAboutTheProjectNotTheLogOnScreen} pins it, and any surface that
 * puts this next to the records should say which it is describing.
 *
 * @param identity    {@code sha256:} of the bundle's manifest, or null when this session is not from a bundle
 * @param source      the {@code .fexp} the person opened — NOT the unpacked profile inside it
 * @param workingCopy the disposable copy the bundle was unpacked into
 * @param limits      what verification does not claim (unsigned, replay caveats), as the bundle stated them
 * @param notes       what the SENDER wrote in NOTES.md. Their words, not a fact about the evidence.
 */
public record BundleProvenance(String identity, String source, String workingCopy, String limits, String notes,
                               /** The sender's CLAIM about which processor the log came from; unverified. */
                               String processor) {

    /** An ordinary project: nothing was received from anyone. */
    public static final BundleProvenance NONE = new BundleProvenance(null, null, null, "", "", null);

    public BundleProvenance {
        limits = limits == null ? "" : limits;
        notes = notes == null ? "" : notes;
    }

    /**
     * Whether this session came from a bundle. Keyed on the identity, because that is the fact verification
     * produced: a session with a working copy but no identity was never verified and must not read as evidence.
     */
    public boolean fromBundle() {
        return identity != null;
    }

    /** Enough of the identity to recognise it in a title or a row, without the algorithm prefix. */
    public String shortIdentity() {
        if (identity == null) return null;
        String bare = identity.startsWith("sha256:") ? identity.substring("sha256:".length()) : identity;
        return bare.length() <= 12 ? bare : bare.substring(0, 12);
    }
}
