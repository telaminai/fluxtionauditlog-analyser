# Vendored upstream authoring documents

Offline copies of the published Fluxtion AI-authoring resources, held here so the experiment has a
**mutable baseline it can measure changes against** — the point is to edit a copy and see what the edit
does to authoring cost, which is impossible against a live URL.

| file | source |
|---|---|
| `fluxtion-claude.txt` | `https://raw.githubusercontent.com/telaminai/fluxtion/main/docs/claude.txt` |
| `fluxtion-golden-path.md` | `https://fluxtion-playground.dev/fluxtion-golden-path.md` |

Fetched 2026-09-01. Swept clean of the four terms before committing.

**Why this exists at all.** Rounds 7–15 seeded agents with a hand-written local `CLAUDE.md` and never
with these, and no measured cell ever fetched them — verified across every transcript. The series was
therefore measuring a local subset rather than what a real author is actually given. Re-fetch and
re-sweep when comparing against upstream; do not assume this copy is current.
