package telamin.fluxtion.audit.analyser.analyser.io;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class S3SourceTest {

    @Test
    void recognisesS3Uris() {
        assertTrue(S3Source.isS3("s3://bucket/key.log"));
        assertFalse(S3Source.isS3("/local/file.log"));
        assertFalse(S3Source.isS3(null));
    }

    @Test
    void buildsCopyToFileCommandWithOptionalProfileAndRegion() {
        Path dest = Path.of("/tmp/x.log");
        List<String> base = S3Source.buildCommand("s3://b/k", dest, "", "");
        assertEquals(List.of("aws", "s3", "cp", "s3://b/k", "/tmp/x.log", "--quiet"), base);

        List<String> full = S3Source.buildCommand("s3://b/k", dest, "prod", "eu-west-1");
        assertTrue(full.contains("--profile") && full.contains("prod"));
        assertTrue(full.contains("--region") && full.contains("eu-west-1"));
        assertTrue(full.indexOf("/tmp/x.log") < full.indexOf("--quiet"), "dest before flags");
    }
}
