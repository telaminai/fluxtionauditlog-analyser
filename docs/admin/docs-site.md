# Documentation Site — GitHub Pages (Design)

Status: DRAFT v2 · Owner: greg.higgins · Last updated: 2026-08-14

> **v2 (2026-08-14): migrated from Jekyll + Just the Docs to MkDocs Material**, matching
> [telaminai/mongoose-plugins](https://github.com/telaminai/mongoose-plugins) — one docs toolchain and
> one look across the org, and no Ruby toolchain (the `github-pages` gem pins 2018-era Jekyll 3.9 and
> cannot run on Ruby ≥ 4).

Companion docs: **[release-process.md](release-process.md)** ·
**[../specs/tracker.md](../specs/tracker.md)** (milestone **M17**).

A professional, public user site for the analyser at
**`https://telaminai.github.io/fluxtionauditlog-analyser/`** — the place the README, release notes and
JBang one-liner all point to. The audience is **users** (download, run, understand the log format, use
the assistant); the design specs in `docs/specs/` stay in-repo for contributors and are linked, not
duplicated.

---

## 1. Stack — MkDocs Material, built by Actions

- **[MkDocs Material](https://squidfunk.github.io/mkdocs-material/)** — the same generator and theme
  as **mongoose-plugins**, config copied from its `mkdocs.yml` (indigo/deep-orange palette, Inter +
  JetBrains Mono, dark-mode toggle, search, tabs). One toolchain and one visual identity across
  telaminai docs sites. Local preview is `pip install -r docs-requirements.txt && mkdocs serve` —
  no Ruby.
- **Built and deployed by GitHub Actions** (`mkdocs build --strict` + `actions/deploy-pages`), not
  the legacy "Pages builds from a branch" mode. The build **copies `CHANGELOG.md` in as the
  Release-notes page** so the site and the app always show the same notes with zero duplication.
  `--strict` fails the build on broken internal links.
- Site source lives in **`docs/site/`** (`docs_dir` in the root `mkdocs.yml`), so `docs/specs` and
  `docs/admin` stay plain markdown for GitHub browsing, untouched by the site build. Output goes to
  `target/docs-site` (gitignored with the rest of `target/`).
- _History_: v1 used Jekyll + Just the Docs on the `github-pages` gem. Abandoned because that gem
  pins Jekyll 3.9 (2018), fails on Ruby ≥ 3.2's removed taint API when the resolver falls back, and
  cannot run on Ruby 4 at all — and because mongoose-plugins had standardised on Material.

## 2. Site structure

```
mkdocs.yml                   (repo root) site config: theme, nav, docs_dir: docs/site
docs-requirements.txt        (repo root) pinned MkDocs toolchain, used by CI and locally
docs/site/
├─ index.md                  Landing: what it is, hero screenshot, Download / Run now
├─ install.md                JBang one-liner, fatjar download + java -jar, requirements (JDK 21+)
├─ user-guide/
│  ├─ index.md               opening logs (local / drag-drop / S3), table, filters, anomalies, diff
│  ├─ graphs.md              series, formulas f(x), pinned ranges, add-from-detail-viewer, export
│  ├─ assistant.md           LLM setup, explain, actions loop, copy-prompt mode, REST endpoint
│  ├─ source-navigation.md   roots, maven repos, event-processor inference, click-through
│  └─ sharing-setups.md      settings export/import (spec-settings-share)
├─ log-format.md             what a Fluxtion audit log is, record anatomy, why-not-strict-YAML
├─ release-notes.md          thin wrapper; the workflow copies CHANGELOG.md content in at build
├─ faq.md                    big files, memory threshold, keys/security, offline behaviour
└─ assets/                   screenshots (light + dark), logo
```

Navigation order is the `nav:` list in `mkdocs.yml` (pages carry no ordering front matter). The
theme/palette/extensions blocks are copied from mongoose-plugins' `mkdocs.yml` — when changing the
look, change both repos or neither.

Content strategy: the in-app `help.html` and this site share subject matter but not markup —
**the site pages are written once in markdown** and become the more complete reference; `help.html`
stays the compact offline version. (Unifying the two — generating help from the site pages — is a
possible later step, noted in §6.)

## 3. Landing page essentials (index.md)

- One-paragraph pitch + a **screenshot** (light theme, a real-looking log loaded, graph visible).
- **Run it now** block, verbatim from release-process.md §8:
  ```bash
  jbang analyser@telaminai/fluxtionauditlog-analyser
  # or
  curl -LO https://github.com/telaminai/fluxtionauditlog-analyser/releases/latest/download/fluxtion-auditlog-analyser.jar
  java -jar fluxtion-auditlog-analyser.jar
  ```
- Three feature cards: *Fast on multi-GB logs* · *Graph any node value (+formulas)* · *LLM explains
  the cycle, grounded in your source*.
- Buttons: **Download latest** (`releases/latest`) · **User guide** · **GitHub**.

## 4. Deploy workflow — `.github/workflows/pages.yml`

```yaml
name: Deploy docs site
on:
  push:
    branches: [main]
    paths: ['docs/site/**', 'mkdocs.yml', 'docs-requirements.txt', 'CHANGELOG.md', '.github/workflows/pages.yml']
  workflow_dispatch:

permissions:
  contents: read
  pages: write
  id-token: write

concurrency: { group: pages, cancel-in-progress: true }

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Inject changelog as the release-notes page body
        run: |
          printf -- '---\ntitle: Release notes\n---\n\n# Release notes\n\n' > docs/site/release-notes.md
          cat CHANGELOG.md >> docs/site/release-notes.md
      - uses: actions/setup-python@v5
        with: { python-version: '3.12', cache: pip, cache-dependency-path: docs-requirements.txt }
      - run: pip install -r docs-requirements.txt
      - run: mkdocs build --strict
      - uses: actions/configure-pages@v5
      - uses: actions/upload-pages-artifact@v3
        with: { path: target/docs-site }
  deploy:
    needs: build
    runs-on: ubuntu-latest
    environment: { name: github-pages, url: '${{ steps.deployment.outputs.page_url }}' }
    steps:
      - id: deployment
        uses: actions/deploy-pages@v4
```

The site republishes on every docs/changelog change on main — releases automatically refresh the
release-notes page.

### Enabling GitHub Pages for the repo (one-time)

1. **Settings → Pages → Build and deployment → Source: "GitHub Actions"** — this is the only
   strictly required switch. Do *not* pick "Deploy from a branch"; this workflow deploys an
   artifact via OIDC, not a branch.
2. **Settings → Actions → General → Actions permissions** — Actions must be allowed to run. If the
   policy is *"Allow &lt;org&gt; actions and select non-&lt;org&gt; actions"*, the allowlist must
   include the first-party `actions/*` used here (`checkout`, `setup-python`, `configure-pages`,
   `upload-pages-artifact`, `deploy-pages`). *"Allow all actions"* needs nothing extra.
3. **Workflow permissions** (same page): the default *"Read repository contents"* is fine — the
   workflow declares its own `permissions:` block (`pages: write`, `id-token: write`), which
   overrides the default. The `id-token: write` is the OIDC token `deploy-pages` uses to prove the
   deployment came from this workflow — no PAT or secret is needed anywhere.
4. **Settings → Environments → `github-pages`** — created automatically by the first deploy, with a
   protection rule allowing deployments from the default branch. If a deploy fails with *"branch not
   allowed to deploy"* (e.g. deploying from a non-default branch), adjust the environment's
   deployment-branch rule here.
5. **Org-level checks** (only if blocked): org Settings → Actions can override the repo's allowed-
   actions policy, and org Settings → Member privileges → *Pages creation* must permit **public**
   sites for a public github.io URL.

## 4a. Running the site locally

Needs Python 3.9+ (macOS ships one; `python3 -V` to check). From the **repo root**:

```bash
pip3 install -r docs-requirements.txt   # once (or into a venv, if you prefer)
mkdocs serve                            # http://127.0.0.1:8000/ — live-reloads on save
```

If `mkdocs` is "command not found", pip's user-install script dir isn't on your PATH — either run
`python3 -m mkdocs serve` (works regardless), or add `$(python3 -m site --user-base)/bin` to PATH.

`mkdocs build --strict` runs the same link-checking build CI uses.

Notes:
- The release-notes page is a placeholder locally: the `CHANGELOG.md` injection only runs in the
  deploy workflow (§4). To preview it, copy the changelog in by hand:
  `printf -- '---\ntitle: Release notes\n---\n\n# Release notes\n\n' > docs/site/release-notes.md && cat CHANGELOG.md >> docs/site/release-notes.md`
  (don't commit that copy — the workflow overwrites it).
- Build output goes to `target/docs-site` (gitignored via `target/`).

## 5. Delivery slices

1. **Skeleton live** — `mkdocs.yml` + `index.md` + `install.md` + the workflow; Pages enabled;
   confirm theme/search/URL. (Smallest publishable site — do this first, content follows.)
2. **User guide pages** — adapt/expand from `help.html` + README; capture screenshots (both themes).
3. **Log-format + FAQ pages**; wire release-notes injection; link the site from README badges and
   the in-app Help ("full documentation online").
4. _(with M15)_ `sharing-setups.md` page when settings export/import ships.

## 6. Open questions

- **Custom domain** (e.g. `analyser.telamin.com`)? Trivial to add later (CNAME + Pages setting);
  launch on `github.io` first.
- **Single-source help** — generate the in-app `help.html` from the site markdown at build time
  (one authoring place, offline copy stays fresh). Nice consolidation once the site content settles.
- **Versioned docs** — overkill for a desktop tool whose site tracks the latest release; the bundled
  in-app notes/help already match whatever version a user runs.
