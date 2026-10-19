package telamin.fluxtion.audit.analyser.analyser.config;

import telamin.fluxtion.audit.analyser.analyser.walk.WalkSpec;

import java.util.List;
import java.util.Properties;

/** Test access: a sample walk, and the ConfigStore walk round trip, for tests outside this package. */
public final class WalkPersistenceTestAccess {
    private WalkPersistenceTestAccess() {
    }

    public static WalkSpec sample(String name) {
        return WalkPersistenceTest.sample(name);
    }

    public static Properties write(List<WalkSpec> walks) {
        Properties p = new Properties();
        ConfigStore.writeWalks(p, walks);
        return p;
    }

    public static List<WalkSpec> read(Properties p) {
        List<WalkSpec> out = new java.util.ArrayList<>();
        ConfigStore.readWalks(p, out);
        return out;
    }
}
