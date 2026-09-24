# Release Process — Branching, GitHub Actions, Distribution

Status: operational reference · Owner: greg.higgins · Last updated: 2026-09-24

Companion docs: **[docs-site.md](docs-site.md)** (the GitHub Pages user site) ·
**[../specs/completed/spec-settings-share.md](../specs/completed/spec-settings-share.md)** (settings export/import) ·
**[../specs/tracker.md](../specs/tracker.md)** (milestone **M16**).

The goals, in priority order:

1. **Users download and run in one step** — a self-contained fatjar (`java -jar`) and a JBang alias
   (`jbang analyser@telaminai/…`), always pointing at the latest release.
2. **Minimum release effort** — one workflow-dispatch click with a version number; everything else
   (tests, changelog stamping, tagging, building, publishing, checksums) is automated.
3. **Release notes travel with the app** — the changelog is bundled into the jar and shown in the UI
   (Help → Release notes, plus a "What's new" note on first run of a new version).

---

## 0. Original setup checklist (historical, before the first release)

This records the original setup plan, not outstanding work for each release. The current executable
workflows linked in sections 5 and 6 are authoritative; permissions remain a repository setting.

- [ ] **Fix the artifactId typo**: `fluxtion-audiitlog-analyser` → `fluxtion-auditlog-analyser`
      (double "i"). Release asset names and the JBang alias are effectively permanent — fix it while
      nothing depends on it. All names below assume the corrected spelling.
- [ ] Set the pom `<version>` to `0.0.0-SNAPSHOT` — the pom version on `main` is a **placeholder**;
      real versions are stamped by CI from the release input (§4). Master never carries a release version,
      so releasing never creates version-bump commit noise beyond the changelog stamp.
- [ ] Add `CHANGELOG.md` at the repo root (Keep-a-Changelog format, seeded — see §3).
- [ ] Add the two workflows (§5, §6) under `.github/workflows/`.
- [ ] Bundle the changelog into the jar + add manifest version entries (§7 pom tweaks).
- [ ] Add `jbang-catalog.json` at the repo root (§8).
- [ ] Repo settings → Actions → General → Workflow permissions: **Read and write** (the release
      workflow pushes a changelog commit and a tag using `GITHUB_TOKEN` — no PAT needed).

---

## 1. Versioning

**Semantic versioning**, tags `vMAJOR.MINOR.PATCH` (e.g. `v1.4.2`).

- **PATCH** — fixes, doc/UI polish, no behaviour surprises.
- **MINOR** — new features (new verbs, new panels, new settings); config stays backward compatible
  (the `ConfigStore` already loads old profiles leniently — keep it that way).
- **MAJOR** — breaking changes to on-disk config, the action schema (`"v": 1`), or the log-format
  contract. Expect these to be rare.

The **pom version is never the source of truth** — CI stamps it (`mvn versions:set`) from the release
input. The app learns its own version from the jar manifest (`Implementation-Version`, §7) and shows it
in Help → About and the What's-new check.

## 2. Branching model — trunk-based, main always releasable

- **`main`** is the only long-lived branch. Every commit must pass `mvn verify` (CI enforces, §5).
  Anything merged is releasable — the release decision is *when*, not *what to stabilise*.
- **Short-lived feature branches + PRs** are optional (solo work can commit straight to main; the
  CI gate still applies). Use a PR when review or a checkpoint is wanted.
- **No develop branch, no release branches by default.** A release is a tag on main.
- **Hotfix path (rare):** if main has moved past a release and a fix must ship against the old
  version, branch `release/1.4.x` from the `v1.4.2` tag, cherry-pick the fix, and run the release
  workflow from that branch (`v1.4.3`). Delete the branch when the line is dead.

This is deliberately the smallest model that stays professional: one branch to reason about, releases
are cheap, and history is linear.

## 3. Changelog — the one manual habit

`CHANGELOG.md` at the repo root, [Keep a Changelog](https://keepachangelog.com) format:

```markdown
# Changelog

## [Unreleased]
### Added
- Right-click an attribute in the detail viewer to add it as a graph series.
### Fixed
- Settings dialog no longer opens over-wide.
```

**The rule: any user-visible change lands with a line under `[Unreleased]` in the same commit/PR.**
That is the entire manual effort of the release process. (The `docs/specs/tracker.md` design log is
unaffected — the changelog is the *user-facing* summary, one line per change, no internals.)

At release time the workflow stamps `[Unreleased]` into `## [1.4.2] - 2026-08-13`, inserts a fresh
empty `[Unreleased]` skeleton, and commits — the extracted section becomes the GitHub release body
**and** ships inside the jar (§7). Release notes are therefore written incrementally by whoever made
the change, never reconstructed at release time.

> Why not auto-generate from commit messages / PR titles? It's zero-effort but produces notes written
> for developers, not users, and this app's audience reads the notes inside the UI. One curated line
> per change is the better trade. (GitHub's auto-notes stay available as a fallback: the release
> workflow appends a "Full commit log" link.)

## 4. Cutting a release — the whole procedure

### 4.0 Pre-release checklist — what CI cannot see

The release workflow runs the default Maven suite. Main CI separately runs frame tests on Xvfb,
but the release workflow does not launch the app, and three released versions (1.13.0–1.13.2)
shipped an `open {analysis}` that failed on **every call** through four independent reviews, because every one
of them read the code and ran the unit suite and none drove the path. So, on the commit you are about to
release, on a machine with a display (each script launches the BUILT jar under an isolated `user.home`, drives it
over the action socket with a hard per-call timeout, and exits non-zero on any failure):

- [ ] Main CI is green at the release candidate: build, loop-bench, ui-frame and mutation-gate.
      The frame job must have zero skips. Locally use
      `python3 tools/verify_project_chart_review.py --mode display` on a display; it passes
      `-Djava.awt.headless=false` directly and checks missing, empty and skipped suites.
      `-DargLine` alone is not a substitute.
- [ ] `mvn package` — report total / failures / errors / skips separately.
- [ ] `python3 tools/verify-m46-agent-api.py` — first verdict after open, no REST hang, honest echoes, `open {analysis}`
- [ ] `python3 tools/verify-m48-handoff.py` — the shared canvas through `open {posture | record}`
- [ ] `python3 tools/verify-m64-spotlight.py` — every spotlight target, sets, refusals that touch nothing
- [ ] `python3 tools/verify-session-restart.py` — separate JVMs, normal quit, explicit recovery and
      command-line input isolation. The launch shim only translates stdin EOF into window close.
- [ ] `python3 tools/capture-conversations.py` — all seven scenarios must complete. It rewrites
      `docs/site/sample-conversations.md` and five `conv-*.png`: **read** the diff and the images (CLAUDE.md
      rule 1) and commit them only if the content changed; restore them otherwise. Two signals: exit 0 means every
      scenario completed (this line is met); a `WARNING: … image(s) NOT regenerated` on stderr means the terminal
      lacks macOS Screen Recording permission, so the images could not be captured — grant it and re-run before
      committing docs (`--require-images` makes that a failure, exit 3, for a machine that should have the grant).
- [ ] CLAUDE.md rule 1's two checks: `git config user.email` is the personal address, and
      `git log --format='%ae' | sort | uniq -c` shows no new employer-domain commits.
- [ ] Apply the starter verification tiers: static checks on every commit, unattended preflight on
      the public bundle, and one acquisition-only client spot-check when routing surfaces change.
      Owner decision 2026-09-20 retires the cold-start battery; do not launch T3–T6 or a broader held-out
      client run as a release ritual. The first public v2 acquisition waits for a v2 ZIP and green
      public preflight. See [the tier report](../handoff/report_starter_verification_tiers_2026_09_20.md).
- [ ] The person-at-the-screen items the tracker lists as open for this release. No script substitutes for them.

### 4.1 The release itself

1. GitHub → Actions → **Release** → *Run workflow* → enter the version (e.g. `1.4.2`).
2. Watch the workflow and its explicit Pages dispatch to completion.
3. Download both public jars and the checksum file; verify hashes, the version manifest and
   bundled release notes. Confirm the latest-download route and the published release-notes page.
4. Record the release receipt, update live report statuses, and move fully shipped tracker items
   to completed. Keep unresolved follow-ups and owner decisions live.

The workflow (§6) then: verifies tests → stamps + commits the changelog → tags `v1.4.2` → builds the
fatjar with the version stamped in → publishes a GitHub Release with the notes and three assets:

| Asset | Purpose |
|---|---|
| `fluxtion-auditlog-analyser-1.4.2.jar` | the versioned fatjar (archival, reproducible links) |
| `fluxtion-auditlog-analyser.jar` | **stable-name** copy — `releases/latest/download/…` never changes, which is what the JBang catalog and the website's Download button point at |
| `*.sha256` | checksums for both jars |

## 5. CI workflow

[The executable CI workflow](../../.github/workflows/ci.yml) is the source of truth, rather than a
second YAML copy here. Every push and PR to main runs the build, loop bench, display frame gate and
full fast mutation gate. The mutation job uploads its JSON evidence. Frame suites must appear in
both registration lists; a skipped display suite is a failure, not a pass. The default headless
Maven suite alone cannot verify a click or dialog.

The fast mutation engine uses a fresh test JVM per control and a full rebuild when its API guard
requires one. Local mutation runs still default to the Maven engine for the initial comparison
cycle. Branch-subset selection is a development aid, not a replacement for CI's full gate.
Whether checks are required by branch protection is an owner setting.

## 6. Release workflow

[The executable release workflow](../../.github/workflows/release.yml) validates the version and
remote tag, runs `mvn -B verify`, stamps the changelog, and atomically pushes the changelog commit
and tag using the release bot identity. It stamps the POM for packaging, publishes both jar names
and `SHA256SUMS.sha256`, then explicitly dispatches Pages. The explicit dispatch matters because
pushes made with `GITHUB_TOKEN` do not trigger other push workflows.

A concurrent main update can reject the atomic push; inspect the run and remote state before
retrying. Do not overwrite a published tag or claim success from dispatch alone.

## 7. Bundling release notes + version into the jar (pom tweaks)

**(a) Changelog as a resource** — add to `<build>`:

```xml
<resources>
    <resource><directory>src/main/resources</directory></resource>
    <resource>
        <directory>${project.basedir}</directory>
        <includes><include>CHANGELOG.md</include></includes>
        <targetPath>release-notes</targetPath>
    </resource>
</resources>
```

**(b) Version in the manifest** — extend the existing `maven-jar-plugin` `<archive>` (and mirror in the
shade transformer's `manifestEntries`):

```xml
<manifest>
    <mainClass>telamin.fluxtion.audit.analyser.Main</mainClass>
    <addDefaultImplementationEntries>true</addDefaultImplementationEntries>
</manifest>
```

**(c) UI slices** (small, mirrors the existing Help panel pattern):

- `Help → Release notes` — a panel rendering the bundled `/release-notes/CHANGELOG.md` (monospaced
  text is acceptable v1; a ~40-line markdown-to-HTML lightener can come later — same ethos as the
  bespoke `Json`).
- `Help → About` shows `Package.getImplementationVersion()` (falls back to `dev` when run from the IDE).
- **What's new on upgrade** — `AppConfig.lastRunVersion` (persisted); on startup, if the manifest
  version differs, show a small dialog with the current version's changelog section and update the
  stored value. One config field + one comparison; no network, no update checker (a "check for
  updates" that hits the GitHub releases API is a possible later opt-in — never on by default).

## 8. JBang integration — `jbang-catalog.json` (repo root)

```json
{
  "aliases": {
    "analyser": {
      "script-ref": "https://github.com/telaminai/fluxtionauditlog-analyser/releases/latest/download/fluxtion-auditlog-analyser.jar",
      "description": "Fluxtion Audit Log Analyser — browse, graph and explain Fluxtion event-audit logs",
      "java": "21+"
    }
  }
}
```

Users then run, with zero install beyond JBang itself:

```bash
jbang analyser@telaminai/fluxtionauditlog-analyser            # run the latest release
jbang analyser@telaminai/fluxtionauditlog-analyser my-log.yaml
jbang app install analyser@telaminai/fluxtionauditlog-analyser   # installs an `analyser` command
```

The alias points at the **stable-name asset** under `releases/latest/download/`, so the catalog never
needs touching after a release — JBang users always get the newest version (its cache re-checks the
URL). The catalog file is committed once and forgotten.

**Plain-download users** (no JBang):

```bash
curl -LO https://github.com/telaminai/fluxtionauditlog-analyser/releases/latest/download/fluxtion-auditlog-analyser.jar
java -jar fluxtion-auditlog-analyser.jar
```

Both commands go verbatim on the website's front page and in the README.

## 9. Day-to-day summary

| Who | Does what | When |
|---|---|---|
| Contributor | adds one `[Unreleased]` changelog line with any user-visible change | every change |
| CI | `mvn verify` gate | every push/PR |
| Releaser | Actions → Release → type `1.4.2` | whenever worth shipping |
| Automation | tests, stamp, tag, build, publish, checksums, stable-name asset | at release |
| User | `jbang analyser@telaminai/fluxtionauditlog-analyser` or download + `java -jar` | any time |

## 10. Open questions

- **Artifact/repo naming** — artifactId typo fix is assumed (§0); should the *repo* also be renamed
  (`fluxtionauditlog-analyser` → `fluxtion-auditlog-analyser`)? GitHub redirects old URLs, but the
  JBang alias text is user-visible; decide before publicising.
- **Update notice** — opt-in "a newer version is available" check against the releases API: useful,
  but network calls from a forensic desktop tool should be conservative. Deferred until requested.
- **Native packaging** (`jpackage` .dmg/.msi/.deb): real value for non-Java users, real signing cost
  (Apple/Windows certs). Revisit if the audience widens beyond JVM-comfortable users.
