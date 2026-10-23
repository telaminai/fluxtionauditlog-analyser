package telamin.fluxtion.audit.analyser.analyser.firstminute;

import telamin.fluxtion.audit.analyser.analyser.session.WalkPlaybackState;
import telamin.fluxtion.audit.analyser.analyser.walk.WalkSpec;

/** One hand-written composition-first walk, bound to the pinned Spring run, never to the file currently open. */
public final class DiamondDefinition {
    private DiamondDefinition() { }
    public static final String PIN = "24d6d74340d45a3b4856297a135c57c88d4243b1";
    public static final String NAME = "The diamond";
    public static final String RESOURCE = "/demo/diamond/diamond.fluxtion-settings";
    public static final String WALK_SHA256 = "ede2f8d03a2afd6494b809937b77ee12b0527c4d37033ab2ac8c4dce97bd03b8";
    public static final String LOG_SHA256 = "6291ba0566a9539c6e128cb929161c8cfc21e12b57919f5df28d4f00c2c70487";
    public static final String GRAPH_SHA256 = "7ebd36b0c39ad016cc552b1b54c7cd743c797dbceff9e7e4b37ba7cf8d6b5dec";
    public static final String DESIGN_SHA256 = "f050de05aa8d815e2cbb0a48abe2901cffcddffcba0e3f43f34de5de92cd9bd4";
    public static final String PROCESSOR = "com.acme.diamond.generated.spring.DiamondSpringProcessor";
    public static final String RECORD_2 = "sha256:bedfb8f29fd37715b160f675461f03df716e06295794fd20a8049b93dadc996f";
    public static final String RECORD_3 = "sha256:7fea6075da3d6c030796679647610063d9d770098fc9ec04cc6883af2460de16";
    public static final String RECORD_4 = "sha256:254885974d5dac3ab2a9e8a56887ddf8ce672c5e77b60c78b3d4578c5cfd4322";

    public static WalkSpec walk() {
        return Definition.WALK;
    }

    private static final class Definition {
        private static final WalkSpec WALK = read();
        private static WalkSpec read() {
            try {
                return telamin.fluxtion.audit.analyser.analyser.walk.BundledWalks.resource(
                        RESOURCE, NAME, WalkPlaybackState.Stage.TOPOLOGY, WALK_SHA256).walk();
            } catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
        }
    }
}
