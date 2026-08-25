# Start Page — the empty state, doing a job (Design Spec)

_Status: **PROPOSED** — 2026-08-25, from the owner's ask: a start page with "what it does · how it
helps · where it fits in the cycle · are you a developer, support, data analyst". This document
takes that ask and argues where it should live, what it must not become, and how it stays true._

---

## The proposition

**The analyser already has a start page. It is one sentence long and it is the only thing a new user
sees:**

```
No log loaded — File ▸ Open, drag a file in, or File ▸ Open from S3
```

That line is honest and useless. It tells someone how to load a file; it tells them nothing about
what the tool is for, whether it is aimed at them, or what they will be able to answer once the file
is open. Meanwhile the answer to "what is this?" lives in `HelpPanel` — a static bundled HTML page on
a tab nobody opens before they have a problem.

So this is not a new surface. **It is the empty state finally earning its keep**, and the test of the
whole design is that it costs a returning user nothing.

## D-S1 — it is a STATE, not a screen

Not a splash, not a modal, not a wizard.

- It occupies the main area **whenever no log is open** — first launch, after `File ▸ Close log`,
  after `Reset`.
- Opening a log replaces it. Closing one brings it back. There is no "dismiss", because there is
  nothing to dismiss: it is what the window shows when it is empty.
- It is reachable deliberately as **Help ▸ Start page**, for someone who wants it back without
  closing their work.

*Rationale.* A splash is a toll gate on every launch and gets muscle-memory-dismissed by week two,
taking its content with it. An empty state is seen exactly when it is useful and is invisible the
rest of the time. There is already a `SplashScreen` for load time; this is not that, and the two must
not be confused.

## D-S2 — every section ends in an ACTION, or it is a brochure

The owner's four sections, each with the thing that stops it being marketing:

| Section | What it says | The action that makes it real |
|---|---|---|
| **What it does** | Reads an audit log and reconstructs what the system did, event by event — which nodes ran, in what order, what each one computed. | **Open the demo log** — one click, no file needed. |
| **How it helps** | Three questions you cannot answer by reading a log: *why is this number what it is*, *which nodes never ran*, *what changed between these two runs*. | Each links straight to that view **on the demo log**. |
| **Where it fits** | dev → deploy → **analyse** → commit. This is the analyse leg; the log arrives from a build or a server, the findings leave as a report or a PR. | Links to the cycle in the docs, and to `File ▸ Find GraphML in source roots…` if roots are configured. |
| **Who you are** | Three lanes — see D-S3. | Each lane is a first step, not a description. |

**Non-negotiable: the demo log ships with the app and every action on this page works with no
configuration, no server, and no API key.** A start page whose buttons need setup first is a page
that lies on first contact.

## D-S3 — the audience lanes: recognition, not a questionnaire

The owner asked for "developer, support, data analyst". Do it as **three lanes on the page**, never
as a question the app asks.

- **Building a processor** — *"I am writing the graph and want to see what it actually does."*
  → open the demo log with its topology; step one cycle.
- **Something is wrong in production** — *"I have a log from a system I did not write."*
  → open a log, filter to the window, flag the record, export a finding.
- **Understanding the data** — *"I want the numbers out of this, not the plumbing."*
  → chart a value over time; export CSV.

*Rationale, and the risk it avoids.* A first-run modal asking "what kind of user are you?" is
friction on the path people are already impatient about, and it asks them to classify themselves
before they know what the categories mean here. **People recognise their own situation far faster
than they classify themselves** — so the lanes are phrased as the sentence the user would say, not as
a job title. Nothing is remembered, nothing is personalised, and picking one is a navigation act with
no consequences: a wrong choice costs a click.

**Explicitly rejected:** storing the answer and tailoring the UI. It would make two installs behave
differently for reasons neither user can see, which is the opposite of what a forensic tool should be.

## D-S4 — it must not rot, so it may not list features

A start page that enumerates capabilities is stale the release after it is written, and a *stale*
start page is worse than none: it is the first thing a new user reads, so its errors are the ones
they carry.

Therefore:

- **No feature list.** Three questions and three lanes, all phrased as problems, which change far
  more slowly than the features that answer them.
- **What it says about the app is derived where it can be** — the demo log's own record count and
  time range come from the fixture, not from prose.
- **Anything version-specific belongs in the release notes**, which are already generated and already
  in the app.
- The page is **short enough to read standing up**. If it needs a scrollbar at a normal window size,
  it has become the thing it was supposed to replace.

## D-S5 — its relationship to Help is subtraction, not addition

`HelpPanel` stays exactly as it is. The start page is not a summary of it and must not become one.

- **Start page:** *should I use this, and what do I do first?* Read once or twice, ever.
- **Help:** *how does this feature work?* Read when stuck, repeatedly.

If a sentence would serve someone who already has a log open, it belongs in Help. If it would only
ever be read by someone who does not, it belongs here. **A sentence that fits both goes in Help**,
because Help is reachable from the start page and not the reverse.

## Non-goals

- Not a tutorial, not a tour, not a checklist with ticks.
- Not a dashboard of recent files — that already exists as `File ▸ Open recent`.
- No telemetry, no "was this helpful?", no remembered dismissal state.
- Not a place for licensing or upgrade prompts.

## Open questions

- **O-S1** Should the demo log ship in the jar, or be generated on first click? The fixture is small
  and already in the repo; bundling is simpler and works offline, which the actions require. Bundling
  is the recommendation, but it is a size decision the owner should confirm.
- **O-S2** Does the start page replace the whole main area, or only the records pane? Replacing
  everything is cleaner to look at; replacing only the records pane keeps the tabs visible so the
  shape of the app is learned before it is used. Leaning to the second — the tabs *are* the product's
  structure, and hiding them on first contact teaches nothing.
- **O-S3** Rule 1 applies to anything on this page: every name, path and screenshot must be `DEMO` /
  `com.acme…`. It is the most-seen surface in the app and the one most likely to carry a real venue
  name into a screenshot on the docs site.

## Acceptance

1. A new user, with no configuration and no network, can go from launch to **a rendered topology and
   a stepped cycle on the demo log in two clicks**.
2. A returning user who opens a log from the command line **never sees the page**.
3. Closing the log brings it back, and closing it says so — consistent with M35.1's rule that the
   app never silently changes what it is showing.
4. Nothing on the page needs an API key, a server, or a source root.
5. The page contains **no list of features** and no version-specific claim.
6. Every string on it passes rule 1's sweep, and any screenshot is harness-generated
   (`tools/capture-docs.py`), not hand-taken.
