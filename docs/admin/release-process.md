# Release Process — Branching, GitHub Actions, Distribution

Status: DRAFT v1 · Owner: greg.higgins · Last updated: 2026-08-13

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

## 0. Pre-flight (once, before the first release)

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

1. GitHub → Actions → **Release** → *Run workflow* → enter the version (e.g. `1.4.2`).
2. There is no step 2.

The workflow (§6) then: verifies tests → stamps + commits the changelog → tags `v1.4.2` → builds the
fatjar with the version stamped in → publishes a GitHub Release with the notes and three assets:

| Asset | Purpose |
|---|---|
| `fluxtion-auditlog-analyser-1.4.2.jar` | the versioned fatjar (archival, reproducible links) |
| `fluxtion-auditlog-analyser.jar` | **stable-name** copy — `releases/latest/download/…` never changes, which is what the JBang catalog and the website's Download button point at |
| `*.sha256` | checksums for both jars |

## 5. CI workflow — `.github/workflows/ci.yml`

Every push/PR to main must be green before it can be released (and the release workflow re-verifies).

```yaml
name: CI
on:
  push:
    branches: [main]
  pull_request:
    branches: [main]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '21', cache: maven }
      - run: mvn -B verify
```

(The test suite is headless-safe — UI classes are only constructed, never shown — so ubuntu is fine.
Add a `macos-latest` matrix entry later only if a platform-specific bug ever warrants it.)

## 6. Release workflow — `.github/workflows/release.yml`

```yaml
name: Release
on:
  workflow_dispatch:
    inputs:
      version:
        description: 'Release version (e.g. 1.4.2 — no leading v)'
        required: true

permissions:
  contents: write

jobs:
  release:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '21', cache: maven }

      - name: Verify version input
        run: |
          [[ "${{ inputs.version }}" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "bad version"; exit 1; }
          git rev-parse "v${{ inputs.version }}" >/dev/null 2>&1 && { echo "tag exists"; exit 1; } || true

      - name: Run tests
        run: mvn -B verify

      - name: Stamp changelog (Unreleased -> version + date), extract notes
        id: notes
        run: |
          DATE=$(date -u +%Y-%m-%d)
          # extract the Unreleased body as the release notes
          awk '/^## \[Unreleased\]/{f=1;next} /^## \[/{f=0} f' CHANGELOG.md > /tmp/notes.md
          # rename Unreleased -> the version, insert a fresh Unreleased skeleton above it
          sed -i "s/^## \[Unreleased\]/## [Unreleased]\n\n## [${{ inputs.version }}] - ${DATE}/" CHANGELOG.md
          echo "Full commit log: https://github.com/${{ github.repository }}/commits/v${{ inputs.version }}" >> /tmp/notes.md

      - name: Commit changelog + tag
        run: |
          git config user.name  "github-actions[bot]"
          git config user.email "github-actions[bot]@users.noreply.github.com"
          git add CHANGELOG.md
          git commit -m "Release ${{ inputs.version }} — stamp changelog"
          git tag "v${{ inputs.version }}"
          git push origin HEAD --tags

      - name: Build fatjar with the release version
        run: |
          mvn -B versions:set -DnewVersion=${{ inputs.version }} -DgenerateBackupPoms=false
          mvn -B -DskipTests package
          cp target/fluxtion-auditlog-analyser-${{ inputs.version }}.jar fluxtion-auditlog-analyser.jar
          sha256sum fluxtion-auditlog-analyser.jar target/fluxtion-auditlog-analyser-${{ inputs.version }}.jar > SHA256SUMS.sha256

      - name: Create GitHub release
        uses: softprops/action-gh-release@v2
        with:
          tag_name: v${{ inputs.version }}
          name: v${{ inputs.version }}
          body_path: /tmp/notes.md
          files: |
            target/fluxtion-auditlog-analyser-${{ inputs.version }}.jar
            fluxtion-auditlog-analyser.jar
            SHA256SUMS.sha256
```

Notes on the design:

- **Tests run before anything is mutated**; the changelog commit + tag happen only on green.
- The **changelog stamp is the only commit** a release adds to main.
- `versions:set` happens **after** the tag so main's pom stays at the placeholder version (the
  stamped pom exists only in the workflow workspace — the tag records the changelog commit, and the
  release is reproducible from it via the same two commands).
- `GITHUB_TOKEN` with `contents: write` covers the push, the tag and the release — no secrets to manage.
- Future (not v1): Sigstore/cosign signing of the jars; a `jpackage` matrix producing native
  installers (macOS `.dmg` needs an Apple signing identity — out of scope until someone asks).

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
