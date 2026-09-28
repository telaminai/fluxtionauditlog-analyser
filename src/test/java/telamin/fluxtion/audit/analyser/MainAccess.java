package telamin.fluxtion.audit.analyser;

import java.util.Set;

/** Test access to {@link Main}'s package-private routing, for tests in other packages. */
public final class MainAccess {
    private MainAccess() {
    }

    public static Set<String> bundleFlags() {
        return Main.BUNDLE_FLAGS;
    }

    public static Set<String> retiredBundleFlags() {
        return Main.RETIRED_BUNDLE_FLAGS;
    }
}
