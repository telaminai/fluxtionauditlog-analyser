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
 * @param identity    {@code sha256:} of the bundle's manifest, or null when this session is not from a bundle
 * @param source      the {@code .fexp} the person opened — NOT the unpacked profile inside it
 * @param workingCopy the disposable copy the bundle was unpacked into
 * @param limits      what verification does not claim (unsigned, replay caveats), as the bundle stated them
 */
public record BundleProvenance(String identity, String source, String workingCopy, String limits) {

    /** An ordinary project: nothing was received from anyone. */
    public static final BundleProvenance NONE = new BundleProvenance(null, null, null, "");

    public BundleProvenance {
        limits = limits == null ? "" : limits;
    }

    /**
     * Whether this session came from a bundle. Keyed on the identity, because that is the fact verification
     * produced: a session with a working copy but no identity was never verified and must not read as evidence.
     */
    public boolean fromBundle() {
        return identity != null;
    }
}
