package telamin.fluxtion.audit.analyser.analyser.session;

/**
 * #177: the banner {@code profileOnDisk} decided — the open profile (by the digest of its path, never the path) changed
 * on disk and waits for the person's answer. Published in the {@link SessionSnapshot}; the banner renders this alone.
 *
 * @param profileKey  which profile, as the session knows it
 * @param saveRefused whether a save of this window's edits was refused because of the change
 */
public record ProfileOnDiskState(String profileKey, boolean saveRefused) { }
