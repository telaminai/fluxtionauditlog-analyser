package telamin.fluxtion.audit.analyser.analyser.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #21 — a project may say WHERE the exchange directory is, never WHETHER.
 *
 * <p>The thing under test is a boundary, so it is tested as one: what a profile can make the
 * assistant write to. The opt-in never moves; the location may, but only to somewhere inside the
 * project that already exists.
 */
class ProjectSuppliedExchangeDirTest {

    @Test
    void anAcceptedAliasReturnsItsCheckedLocation(@TempDir Path root) throws IOException {
        AppConfig c = projectAt(root);
        Path inside = Files.createDirectory(root.resolve("inside"));
        Files.createSymbolicLink(root.resolve("exchange"), inside);
        c.projectExchangeDir = "exchange";
        assertEquals(inside.toRealPath().toString(), ExchangeDir.of(c).dir(),
                "keep the verified location instead of an alias that may later be redirected");
    }

    /** A project at {@code root} with a profile at {@code root/.analyser/project.fluxtion-settings}. */
    private static AppConfig projectAt(Path root) throws IOException {
        Files.createDirectories(root.resolve(".analyser"));
        AppConfig c = new AppConfig();
        c.activeProjectPath = root.resolve(".analyser").resolve("project.fluxtion-settings").toString();
        c.assistantExports = true;
        c.assistantExportDir = root.resolve("machine-tier").toString();
        return c;
    }

    // ---- the happy path ------------------------------------------------------------------------

    @Test
    @DisplayName("A project's directory is used, and context can say the project chose it")
    void theProjectsDirectoryWins(@TempDir Path root) throws IOException {
        AppConfig c = projectAt(root);
        Files.createDirectories(root.resolve("src/report/shared"));
        c.projectExchangeDir = "src/report/shared";

        ExchangeDir resolved = ExchangeDir.of(c);

        assertEquals(root.resolve("src/report/shared").toRealPath().toString(), resolved.dir(), "use the canonical project directory");
        assertEquals(ExchangeDir.PROJECT, resolved.source());
        assertTrue(resolved.fromProject());
        assertNull(resolved.refusal());
    }

    @Test
    @DisplayName("★ #182: no project value — the open project's own analyser-exchange/, never the machine's (another project's) folder")
    void anOpenProjectDefaultsToItsOwnExchangeDirectory(@TempDir Path root) throws IOException {
        AppConfig c = projectAt(root);
        c.projectExchangeDir = "";

        ExchangeDir resolved = ExchangeDir.of(c);

        assertEquals(root.toRealPath().resolve("analyser-exchange").toString(), resolved.dir(),
                "★ the project's own folder, not the machine tier's (red before #182: the machine directory)");
        assertEquals(ExchangeDir.PROJECT, resolved.source());
        assertTrue(resolved.projectDefault(), "and it says it is the default, so a reply can say where the file went");
        assertNull(resolved.refusal(), "nothing was refused — nothing was asked for");
        assertFalse(Files.exists(root.resolve("analyser-exchange")), "reading the location creates nothing");

        ExchangeDir.forWrite(c);
        assertTrue(Files.isDirectory(root.resolve("analyser-exchange")), "a write makes it, once");
        assertEquals(resolved.dir(), ExchangeDir.of(c).dir(), "and the same directory answers afterwards");
    }

    @Test
    @DisplayName("#182: no project open — the machine's directory, unremarked, as before")
    void noProjectKeepsTheMachineTier() {
        AppConfig c = new AppConfig();
        c.assistantExports = true;
        c.assistantExportDir = "/tmp/machine-tier";
        ExchangeDir resolved = ExchangeDir.of(c);
        assertEquals("/tmp/machine-tier", resolved.dir());
        assertEquals(ExchangeDir.MACHINE, resolved.source());
        assertFalse(resolved.projectDefault());
        assertNull(resolved.refusal());
    }

    @Test
    @DisplayName("#182: a machine directory already inside the open project is this project's folder, and stays")
    void aMachineDirectoryInsideTheProjectStays(@TempDir Path root) throws IOException {
        AppConfig c = projectAt(root);
        Path inside = Files.createDirectories(root.resolve("reports"));
        c.assistantExportDir = inside.toString();
        ExchangeDir resolved = ExchangeDir.of(c);
        assertEquals(inside.toString(), resolved.dir());
        assertFalse(resolved.projectDefault());
    }

    @Test
    @DisplayName("#182: a declared project value still wins over the default")
    void aDeclaredValueStillWins(@TempDir Path root) throws IOException {
        AppConfig c = projectAt(root);
        Files.createDirectories(root.resolve("out"));
        Files.createDirectories(root.resolve("analyser-exchange"));
        c.projectExchangeDir = "out";
        ExchangeDir resolved = ExchangeDir.of(c);
        assertEquals(root.resolve("out").toRealPath().toString(), resolved.dir());
        assertFalse(resolved.projectDefault());
    }

    @Test
    @DisplayName("#182: an analyser-exchange that is a link out of the project is refused, never written through")
    void aDefaultThatLeavesTheProjectIsRefused(@TempDir Path root, @TempDir Path elsewhere) throws IOException {
        AppConfig c = projectAt(root);
        Files.createSymbolicLink(root.resolve("analyser-exchange"), elsewhere);
        ExchangeDir resolved = ExchangeDir.of(c);
        assertEquals(c.assistantExportDir, resolved.dir());
        assertEquals(ExchangeDir.MACHINE, resolved.source());
        assertNotNull(resolved.refusal());
        assertTrue(resolved.refusal().contains("leaves the project"), resolved.refusal());
    }

    @Test
    @DisplayName("#182: a bundle's working copy and the sample project are the analyser's own — they keep the machine tier")
    void analyserOwnedProjectsKeepTheMachineTier(@TempDir Path home) throws IOException {
        String was = System.getProperty("user.home");
        try {
            System.setProperty("user.home", home.toString());
            for (Path owned : java.util.List.of(home.resolve(".fluxtion-analyser/bundles/bundle-abc"),
                    home.resolve(".fluxtion-analyser/demo"))) {
                AppConfig c = projectAt(Files.createDirectories(owned));
                ExchangeDir resolved = ExchangeDir.of(c);
                assertEquals(c.assistantExportDir, resolved.dir(), owned.toString());
                assertFalse(resolved.projectDefault(), owned.toString());
                assertNull(resolved.refusal());
            }
        } finally {
            System.setProperty("user.home", was);
        }
    }

    // ---- WHERE, never WHETHER ------------------------------------------------------------------

    @Test
    @DisplayName("With the exchange OFF, a project-supplied directory changes NOTHING")
    void aProjectCannotTurnTheExchangeOn(@TempDir Path root) throws IOException {
        AppConfig c = projectAt(root);
        Files.createDirectories(root.resolve("exports"));
        c.projectExchangeDir = "exports";
        c.assistantExports = false;              // the machine tier's opt-in, and the only one

        ExchangeDir resolved = ExchangeDir.of(c);

        assertEquals(ExchangeDir.MACHINE, resolved.source(),
                "opening someone's project must not widen a permission — the whole point of the split");
        assertNotEquals(root.resolve("exports").toString(), resolved.dir());
    }

    @Test
    @DisplayName("The opt-in is not a project-tier field at all")
    void theOptInIsNotInTheProfileTier() {
        AppConfig c = new AppConfig();
        c.assistantExports = true;
        c.projectExchangeDir = "exports";

        ProjectProfile.clearProjectScoped(c);

        assertTrue(c.assistantExports, "the OPT-IN survives a project switch: it is this machine's");
        assertEquals("", c.projectExchangeDir,
                "the LOCATION does not: project A's directory must not be in force under project B");
    }

    // ---- what a profile may ask for ------------------------------------------------------------

    @Test
    @DisplayName("'..' is refused — this directory is WRITTEN to, unlike source roots")
    void dotDotIsRefused(@TempDir Path root) throws IOException {
        AppConfig c = projectAt(root);
        c.projectExchangeDir = "../../elsewhere";

        ExchangeDir resolved = ExchangeDir.of(c);

        assertEquals(ExchangeDir.MACHINE, resolved.source());
        assertNotNull(resolved.refusal());
        assertTrue(resolved.refusal().contains("escapes the project root"), resolved.refusal());
    }

    @Test
    @DisplayName("An absolute path is refused")
    void absoluteIsRefused(@TempDir Path root) throws IOException {
        AppConfig c = projectAt(root);
        c.projectExchangeDir = "/tmp";

        ExchangeDir resolved = ExchangeDir.of(c);

        assertEquals(ExchangeDir.MACHINE, resolved.source());
        assertTrue(resolved.refusal().contains("absolute"), resolved.refusal());
    }

    @Test
    @DisplayName("A home-relative path is refused")
    void homeRelativeIsRefused(@TempDir Path root) throws IOException {
        AppConfig c = projectAt(root);
        c.projectExchangeDir = "~/Desktop";

        ExchangeDir resolved = ExchangeDir.of(c);

        assertEquals(ExchangeDir.MACHINE, resolved.source());
        assertNotNull(resolved.refusal());
    }

    @Test
    @DisplayName("A directory that is not there is refused with its reason, and is NOT created")
    void aMissingDirectoryIsRefusedNotCreated(@TempDir Path root) throws IOException {
        AppConfig c = projectAt(root);
        c.projectExchangeDir = "not/here";

        ExchangeDir resolved = ExchangeDir.of(c);

        assertEquals(ExchangeDir.MACHINE, resolved.source());
        assertTrue(resolved.refusal().contains("is not a directory"), resolved.refusal());
        assertFalse(Files.exists(root.resolve("not/here")),
                "creating directories as a side effect of opening someone's profile is exactly the kind "
                        + "of quiet act that should be deliberate");
    }

    @Test
    @DisplayName("A file where a directory was named is refused")
    void aFileIsNotADirectory(@TempDir Path root) throws IOException {
        AppConfig c = projectAt(root);
        Files.writeString(root.resolve("exports"), "not a directory");
        c.projectExchangeDir = "exports";

        assertEquals(ExchangeDir.MACHINE, ExchangeDir.of(c).source());
    }

    @Test
    @DisplayName("With no project open the value does not apply, and says nothing")
    void noProjectMeansNoOpinion(@TempDir Path root) throws IOException {
        AppConfig c = projectAt(root);
        c.projectExchangeDir = "exports";
        c.activeProjectPath = "";

        ExchangeDir resolved = ExchangeDir.of(c);

        assertEquals(ExchangeDir.MACHINE, resolved.source());
        assertNull(resolved.refusal(),
                "a project-relative value with no project is not refused, it simply does not apply — "
                        + "refusing would put a warning on every machine-tier session");
    }

    // ---- the tier round-trips ------------------------------------------------------------------

    @Test
    @DisplayName("It saves into a profile and comes back, and does NOT leak between projects")
    void itRoundTripsAndDoesNotLeak(@TempDir Path tmp) throws IOException {
        SettingsShare share = new SettingsShare("/home/tester");
        Path a = tmp.resolve("project-a");
        Path b = tmp.resolve("project-b");
        AppConfig ca = projectAt(a);
        Files.createDirectories(a.resolve("evidence"));
        ca.projectExchangeDir = "evidence";
        ca.reportDestinations.clear();
        Path profileA = Path.of(ca.activeProjectPath);
        ProjectProfile.save(profileA, ca, share);

        assertTrue(Files.readString(profileA).contains("assistant.exchangeDir=evidence"),
                "the profile states it, so a colleague's checkout gets the same directory");

        AppConfig cb = projectAt(b);
        assertEquals("", cb.projectExchangeDir);
        ProjectProfile.load(profileA, cb, share);
        assertEquals("evidence", cb.projectExchangeDir, "loading A's profile brings A's directory");

        ProjectProfile.load(Path.of(cb.activeProjectPath), cb, share);   // B has no profile on disk
        ProjectProfile.clearProjectScoped(cb);
        assertEquals("", cb.projectExchangeDir, "and leaving A takes it away again");
    }

    @Test
    @DisplayName("A value a profile could never honour is refused at the DOOR, not at use")
    void aRefusedValueIsNeverStored(@TempDir Path tmp) throws IOException {
        SettingsShare share = new SettingsShare("/home/tester");
        Path root = tmp.resolve("project");
        AppConfig c = projectAt(root);
        Files.writeString(Path.of(c.activeProjectPath),
                "share.version=1\nassistant.exchangeDir=../../etc\n");

        var plan = share.preview(Files.readString(Path.of(c.activeProjectPath)), c);

        assertNull(plan.exchangeDir(), "not carried into the plan");
        String summary = plan.summary().get(SettingsShare.Category.DESTINATIONS);
        assertNotNull(summary, "and the person importing it is told");
        assertTrue(summary.contains("REFUSED"), summary);
    }

    /**
     * #31 and #21 meet here. A downloaded template carries profiles, and this is a WRITE location the
     * assistant will use — the one kind of value {@code TemplateRoots} exists to worry about. It needs
     * no new rule: the gate is relative-only and the resolution is project-root-anchored and must
     * already exist, so the worst a hostile template can name is a directory inside the project it
     * shipped. This pins that, because a later relaxation of the gate would silently widen it.
     */
    @Test
    @DisplayName("A template cannot use it to point writes outside its own project")
    void aTemplateCannotEscapeWithIt(@TempDir Path tmp) throws IOException {
        Path installed = tmp.resolve("installed");
        Path outside = tmp.resolve("outside");
        Files.createDirectories(outside);
        AppConfig c = projectAt(installed);

        for (String hostile : new String[]{"../outside", "../../outside", "/tmp", "~/x",
                "src/../../outside", "sub/../../../outside"}) {
            c.projectExchangeDir = hostile;
            ExchangeDir resolved = ExchangeDir.of(c);
            assertEquals(ExchangeDir.MACHINE, resolved.source(), "escaped with: " + hostile);
            assertNotEquals(outside.toString(), resolved.dir(), "escaped with: " + hostile);
        }
    }

    // ---- PR #33 review, R2: a link inside the project must not carry the write directory outside it ----

    @Test
    @DisplayName("A directory inside the project that is a link to OUTSIDE it is refused, naming the link")
    void aLinkOutOfTheProjectIsRefused(@TempDir Path tmp) throws IOException {
        Path root = Files.createDirectories(tmp.resolve("repo"));
        Path outside = Files.createDirectories(tmp.resolve("somewhere-else"));
        AppConfig c = projectAt(root);
        Files.createSymbolicLink(root.resolve("exports"), outside);   // git preserves links, so a clone can carry one
        c.projectExchangeDir = "exports";

        ExchangeDir resolved = ExchangeDir.of(c);

        assertEquals(ExchangeDir.MACHINE, resolved.source(), "a link that leaves the project must not be the write directory");
        assertEquals(c.assistantExportDir, resolved.dir(), "the machine setting is used instead");
        assertNotNull(resolved.refusal(), "and the person is told why");
        assertTrue(resolved.refusal().contains("link"), "the refusal names the link: " + resolved.refusal());
    }

    @Test
    @DisplayName("A link that stays INSIDE the project is still accepted: the rule is where it lands, not links as such")
    void aLinkWithinTheProjectIsAccepted(@TempDir Path tmp) throws IOException {
        Path root = Files.createDirectories(tmp.resolve("repo"));
        Path shared = Files.createDirectories(root.resolve("reports/shared"));
        AppConfig c = projectAt(root);
        Files.createSymbolicLink(root.resolve("exports"), shared);
        c.projectExchangeDir = "exports";

        ExchangeDir resolved = ExchangeDir.of(c);

        assertEquals(ExchangeDir.PROJECT, resolved.source(), "a link that stays in the project is the project's directory");
        assertNull(resolved.refusal());
    }
}
