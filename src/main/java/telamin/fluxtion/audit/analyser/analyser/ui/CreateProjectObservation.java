package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;
import telamin.fluxtion.audit.analyser.analyser.config.ExchangeDir;
import telamin.fluxtion.audit.analyser.analyser.config.ProjectAdoption;
import telamin.fluxtion.audit.analyser.analyser.config.ProjectProfile;
import telamin.fluxtion.audit.analyser.analyser.config.SkillDiscovery;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;
import telamin.fluxtion.audit.analyser.analyser.topology.GraphmlDiscovery;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * M73 — the ADAPTER's half of creating a project (spec-create-project.md §3–§4): observe the directory and the
 * discovery offer as plain facts for the session, render a preview, and translate both surfaces' selections into
 * the one form the session judges. It decides nothing — every refusal is {@code sessionBoundary}'s.
 *
 * <p>Headless and side-effect free: everything here reads. The preview is built from
 * {@link NewProjectDiscovery.Offer} as it exists [R F5], so the socket sees what the dialog shows.
 */
public final class CreateProjectObservation {

    private CreateProjectObservation() {
    }

    /** The directory as it really is (links resolved), or the absolute normalised path when it does not exist. */
    public static Path realDir(Path given) {
        Path absolute = given.toAbsolutePath().normalize();
        try {
            return Files.isDirectory(given) ? given.toRealPath() : absolute;
        } catch (IOException | RuntimeException e) {
            return absolute;
        }
    }

    /** The offer as plain strings, in offer order, with its digest. */
    public static SessionEvents.CreateOffer offer(NewProjectDiscovery.Offer offer, Path dir) {
        if (offer == null) return SessionEvents.CreateOffer.empty();
        List<SessionEvents.OfferedRoot> roots = new ArrayList<>();
        for (Path root : offer.sourceRoots()) {
            Path absolute = root.toAbsolutePath().normalize();
            String relative = relative(dir, absolute);
            roots.add(new SessionEvents.OfferedRoot(relative, absolute.toString(), outside(dir, absolute)));
        }
        List<SessionEvents.OfferedSkill> skills = new ArrayList<>();
        for (SkillDiscovery.Candidate c : offer.skills().candidates()) {
            skills.add(new SessionEvents.OfferedSkill(c.path(), c.name(), c.description(), c.declared()));
        }
        String guide = offer.referenceGuide() == null ? "NOTHING_AGREED" : offer.referenceGuide().name();
        return new SessionEvents.CreateOffer(roots, skills, guide, offer.projectKind(),
                digest(roots, skills, guide, offer.projectKind()));
    }

    /**
     * Everything the node needs to decide a create, observed now. Links are NOT followed where following one would
     * hide a write through it: the profile's path and {@code .analyser} are inspected as themselves.
     *
     * @param given  the directory as the caller named it
     * @param offer  the discovery offer to judge the selection against, or null (a person's "already a project" check
     *               happens before any offer is made)
     */
    public static SessionEvents.CreateFacts facts(Path given, NewProjectDiscovery.Offer offer, AppConfig config) {
        boolean absolute = given.isAbsolute();
        boolean directory = Files.isDirectory(given);
        Path dir = realDir(given);
        Path profile = ProjectProfile.pathFor(dir);
        boolean profileExists = directory && Files.exists(profile, LinkOption.NOFOLLOW_LINKS);
        String analyserProblem = null;
        List<String> named = new ArrayList<>();
        Path analyser = dir.resolve(".analyser");
        if (directory && Files.exists(analyser, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isSymbolicLink(analyser)) {
                analyserProblem = "is a symbolic link";
            } else if (!Files.isDirectory(analyser, LinkOption.NOFOLLOW_LINKS)) {
                analyserProblem = "is not a directory";
            } else {
                try {
                    if (!analyser.toRealPath().startsWith(dir)) analyserProblem = "resolves outside the project directory";
                } catch (IOException e) {
                    analyserProblem = "cannot be resolved: " + e.getMessage();
                }
                try (var listed = Files.list(analyser)) {
                    listed.map(p -> p.getFileName().toString())
                            .filter(n -> ProjectProfile.isProjectProfileFileName(n)
                                    && !n.equals(profile.getFileName().toString()))
                            .sorted().forEach(named::add);
                } catch (IOException ignored) {
                    // unreadable: nothing to name
                }
            }
        }
        String exchange = null;
        if (config != null) {
            String ex = ExchangeDir.of(config).dir();
            if (ex != null && !ex.isBlank()) {
                Path exDir = realDir(Path.of(ex));
                if (dir.startsWith(exDir)) exchange = exDir.toString();
            }
        }
        Path parent = dir.getParent();
        Path enclosing = parent == null ? null : ProjectProfile.findNear(parent);
        return new SessionEvents.CreateFacts(dir.toString(), absolute, directory, profileExists, analyserProblem,
                telamin.fluxtion.audit.analyser.bundle.EvidenceBundle.isWorkingCopy(dir), exchange,
                enclosing == null ? null : enclosing.toString(), named, offer(offer, dir),
                config != null && config.assistantCreateProjects);
    }

    /** The preview of {@code open {project, create: true}}: writes nothing, selects nothing (D-73.2). */
    public static Map<String, Object> preview(NewProjectDiscovery.Offer offer, SessionEvents.CreateFacts facts) {
        Map<String, Object> out = new LinkedHashMap<>();
        String dir = facts.dir();
        out.put("dir", dir);
        out.put("profileExists", facts.profileExists());
        if (!facts.namedProfiles().isEmpty()) {
            out.put("namedProfiles", facts.namedProfiles());
            out.put("namedProfilesNote", "named profiles are not the canonical project.fluxtion-settings, so they do "
                    + "not stop a create; open one with open {project: \"<dir>/.analyser/<name>\"}");
        }
        SessionEvents.CreateOffer plain = facts.offer();
        out.put("projectKind", plain.projectKind() == null ? "plain" : plain.projectKind());
        List<Object> roots = new ArrayList<>();
        for (SessionEvents.OfferedRoot r : plain.sourceRoots()) {
            if (r.outside()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("path", r.relative());
                m.put("outside", true);
                roots.add(m);
            } else {
                roots.add(r.relative());
            }
        }
        out.put("sourceRoots", roots);
        List<Map<String, Object>> skills = new ArrayList<>();
        for (SessionEvents.OfferedSkill k : plain.skills()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("path", k.path());
            m.put("name", k.name());
            m.put("description", k.description());
            skills.add(m);
        }
        out.put("skills", skills);
        List<Map<String, Object>> graphs = new ArrayList<>();
        if (offer != null) {
            for (GraphmlDiscovery.Candidate c : offer.graphs().candidates()) graphs.add(c.toMap());
        }
        out.put("graphs", graphs);
        out.put("graphsNote", "informational: a create opens no graph; open one with open {graphml} after creating");
        out.put("referenceGuide", plain.referenceGuide());
        out.put("truncated", offer != null && (offer.skills().truncated() || offer.graphs().truncated()));
        out.put("offerDigest", plain.digest());
        out.put("createProjectsSwitch", facts.createAllowed());
        if (facts.profileExists()) {
            out.put("next", "open {project: \"" + dir + "\"}");
            out.put("note", "this directory already has a project; a create never overwrites one — open it instead");
        } else {
            out.put("next", "open {project: \"" + dir + "\", create: {sourceRoots: [...], skills: [...], "
                    + "referenceGuide: false, offerDigest: \"" + plain.digest() + "\"}}");
            out.put("note", "nothing is selected: name what to adopt from the lists above (create: {} makes an empty "
                    + "project). An assistant's create also needs Settings ▸ Assistant ▸ Allow the assistant to "
                    + "create projects — createProjectsSwitch is its value now");
        }
        out.put("writes", "nothing; an apply writes " + dir + "/.analyser/project.fluxtion-settings (and "
                + "CLAUDE.md only if chosen)");
        return out;
    }

    /** The dialog's selection as the session's: relative root strings and skill paths, in offer order. */
    public static SessionEvents.CreateSelection fromMenu(NewProjectDiscovery.Offer offer,
                                                         NewProjectDiscovery.Selection selection, Path dir) {
        if (offer == null || selection == null) return SessionEvents.CreateSelection.empty();
        List<String> roots = new ArrayList<>();
        java.util.Set<Path> chosen = new java.util.HashSet<>();
        for (Path p : selection.sourceRoots()) chosen.add(p.toAbsolutePath().normalize());
        for (Path root : offer.sourceRoots()) {
            if (chosen.contains(root.toAbsolutePath().normalize())) roots.add(relative(dir, root.toAbsolutePath().normalize()));
        }
        List<String> skills = new ArrayList<>();
        for (SkillDiscovery.Candidate c : offer.skills().candidates()) {
            if (selection.skillPaths().contains(c.path())) skills.add(c.path());
        }
        return new SessionEvents.CreateSelection(roots, skills, selection.createReferenceGuide(), null);
    }

    /**
     * The socket's {@code create} object as the session's selection. Its SHAPE has already been checked by
     * {@code ActionExecutor} (unknown keys, {@code profile}, types); this only reads it.
     */
    public static SessionEvents.CreateSelection fromSocket(Map<?, ?> create) {
        List<String> roots = new ArrayList<>();
        if (create.get("sourceRoots") instanceof List<?> l) for (Object o : l) roots.add(String.valueOf(o));
        List<String> skills = new ArrayList<>();
        if (create.get("skills") instanceof List<?> l) {
            for (Object o : l) skills.add(o instanceof Map<?, ?> m ? String.valueOf(m.get("path")) : String.valueOf(o));
        }
        boolean guide = Boolean.TRUE.equals(create.get("referenceGuide"));
        Object digest = create.get("offerDigest");
        return new SessionEvents.CreateSelection(roots, skills, guide, digest == null ? null : digest.toString());
    }

    /** What the performer hands {@link ProjectAdoption}: the node-approved items, in the node's (offer) order. */
    public static ProjectAdoption.Plan plan(SessionEvents.CreateApproved approved) {
        if (approved == null) return ProjectAdoption.Plan.empty(null);
        List<ProjectAdoption.Skill> skills = new ArrayList<>();
        for (SessionEvents.OfferedSkill k : approved.skills()) {
            skills.add(new ProjectAdoption.Skill(k.path(), k.name(), k.description(), k.declared()));
        }
        return new ProjectAdoption.Plan(approved.root() == null ? null : Path.of(approved.root()),
                approved.sourceRoots(), skills);
    }

    static String relative(Path dir, Path absolute) {
        if (absolute.equals(dir)) return ".";
        String rel = absolute.startsWith(dir) ? dir.relativize(absolute).toString() : absolute.toString();
        rel = rel.replace('\\', '/');
        return rel.isEmpty() ? "." : rel;
    }

    /** Whether a root resolves, through a link, outside the directory. Unresolvable is treated as outside. */
    static boolean outside(Path dir, Path root) {
        try {
            return !root.toRealPath().startsWith(dir);
        } catch (IOException | RuntimeException e) {
            return true;
        }
    }

    /** sha256 over the offered strings, in offer order — the identity of an offer a preview can quote back. */
    static String digest(List<SessionEvents.OfferedRoot> roots, List<SessionEvents.OfferedSkill> skills,
                         String guide, String kind) {
        StringBuilder text = new StringBuilder();
        text.append("kind:").append(kind == null ? "plain" : kind).append('\n');
        text.append("guide:").append(guide).append('\n');
        for (var r : roots) text.append("root:").append(r.relative()).append(r.outside() ? "\toutside" : "").append('\n');
        for (var k : skills) {
            text.append("skill:").append(k.path()).append('\t').append(k.name()).append('\t')
                    .append(k.description() == null ? "" : k.description()).append('\n');
        }
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(text.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder("sha256:");
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
