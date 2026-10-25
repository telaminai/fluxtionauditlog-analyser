package telamin.fluxtion.audit.analyser.analyser.walk;

import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;
import telamin.fluxtion.audit.analyser.analyser.config.GraphSpec;
import telamin.fluxtion.audit.analyser.analyser.config.SettingsShare;
import telamin.fluxtion.audit.analyser.analyser.session.WalkPlaybackState;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Reads frozen shipped commentary through the existing settings-fragment reader; never persists or binds to live data. */
public final class BundledWalks {
    private BundledWalks() { }
    public record Parts(WalkSpec walk, GraphSpec chart) { }

    public static Parts read(String text, String name, WalkPlaybackState.Stage stage) {
        var plan = new SettingsShare("").preview(text, new AppConfig());
        WalkSpec walk = plan.walks() == null ? null : plan.walks().stream().filter(w -> name.equals(w.name())).findFirst().orElse(null);
        if (walk == null) return null;
        WalkSpec staged = new WalkSpec(walk.name(), walk.title(), walk.author(), walk.createdAt(), walk.updatedAt(),
                walk.fingerprint(), walk.runBasis(), walk.steps(), walk.extras(), walk.conversation(), stage);
        GraphSpec chart = plan.graphs() == null ? null : plan.graphs().stream()
                .filter(g -> walk.steps().stream().anyMatch(s -> s.view() != null && g.name().equals(s.view().graph())))
                .findFirst().orElse(null);
        return new Parts(staged, chart);
    }

    /** Resource and expected digest are supplied by the shipped catalogue, never by a walk verb or imported file. */
    public static Parts resource(String resource, String name, WalkPlaybackState.Stage stage, String digest) throws IOException {
        byte[] bytes;
        try (var in = BundledWalks.class.getResourceAsStream(resource)) {
            if (in == null) throw new IOException("the shipped walk is missing");
            bytes = in.readNBytes(128 * 1024 + 1);
        }
        if (bytes.length > 128 * 1024 || !WalkIdentity.sha256(bytes).equals("sha256:" + digest))
            throw new IOException("the shipped walk does not match its manifest");
        Parts parts = read(new String(bytes, StandardCharsets.UTF_8), name, stage);
        if (parts == null) throw new IOException("the shipped walk is absent from its fragment");
        WalkSpec w = parts.walk();
        return new Parts(new WalkSpec(w.name(), w.title(), "bundled", w.createdAt(), w.updatedAt(),
                w.fingerprint(), w.runBasis(), w.steps(), w.extras(), w.conversation(), w.stage()), parts.chart());
    }
}
