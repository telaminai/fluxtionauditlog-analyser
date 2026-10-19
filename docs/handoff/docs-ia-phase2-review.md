# Docs restructure, phase 2: independent review and dispositions

- **Phase:** 2, *Diagnose an incident*: the incident path (`diagnose/index.md`, which absorbs `support.md`),
  *Connect your client* (`connect-an-llm.md`, rewritten), *Your first diagnosis session*, *Hand the diagnosis on*,
  *Scripts and CI over REST* and *Troubleshooting the connection*.
- **Reviewed commit:** `394e2b7e` against `af0d48a9`.
- **Reviewer:** an independent general-purpose agent, working read-only. It checked every claim against the code at
  HEAD, ran a strict build from a clean `git archive` export, checked anchors and redirects, compared what left
  `assistant.md` and `support.md` with where it landed, and read the pages as a customer developer would.
- **Result:** no BLOCKER. 4 MAJOR, 9 MINOR, 6 NIT.
- **Verified exact by the reviewer:** `McpBridge.NOT_RUNNING`, both `ActionServer` refusals, the `ActionListener`
  status lines, the `--rest` console line, the 8-character token hint, every status code with the 10/s rate limit and
  its burst of 10, the `/manifest` fields, the endpoint file's mode, fields and removal, the 18 verbs and the six
  destructive ones, and every menu and button label.

| # | Grade | Finding (file:line at `394e2b7e`) | Disposition |
|---|---|---|---|
| M1 | MAJOR | `troubleshooting.md:63`, "a live owner is never displaced", is wrong: the analyser started last takes the endpoint (`net/ActionServer.java:158-171`, `mcp/McpSetupState.java:53-60`). Only *taking it back* respects a live owner. | **Fixed.** The *MCP elsewhere* row says the analyser started last takes the endpoint. The paragraph says a window takes it back only when the live owner closes. |
| M2 | MAJOR | `troubleshooting.md:60`, *MCP starting* "wait a moment", misses two cases where the light stays on: access closed for a session (`McpSetupState.java:39-42`, `ui/MainFrame.java:762-764`), and a listener that failed to start (`ActionListener.java:132`). | **Fixed.** The row names both cases, quotes *Could not start the assistant REST transport: …*, and links the closed-for-session section. That section now says the light reads **MCP starting**. |
| M3 | MAJOR | `hand-off.md:8` said a flag is seen "next time anyone opens the log in this project". Flags are session state, and opening another log clears them (`mcp/McpTools.java:78-80`). | **Fixed.** The row reads "see the finding now, in this analyser", and says a flag does not survive opening another log. |
| M4 | MAJOR | `hand-off.md:24-25` said a finding that must travel "goes into a report". A saved report renders the *live* flag (`report/ReportResolver.java:136-145`). | **Fixed.** A finding travels in the PDF rendered now, or in a walk step's caption. A saved report shows it only while the flag exists. |
| m1 | MINOR | `connect-an-llm.md:141`, "No file means access is off", left out two causes that `rest-automation.md:47` lists. | **Fixed.** The connect page now gives all three causes and links the closed-for-session section. |
| m2 | MINOR | `rest-automation.md`'s refusal table had no `500` row, and did not say that `GET /manifest` is refused with `403` while access is off or closed (`ActionServer.java:207`). | **Fixed.** Both are added, and the manifest sentence says so too. |
| m3 | MINOR | `rest-automation.md` was not quite enough for a working script: there was no wait for the endpoint file, and no rule for when an `open` has landed. | **Fixed.** The shell example waits up to 60 s for the file. The open rule says the echo carries `loading: true`, and to poll `context` until its log is the one you opened. It also mentions `analyser --rest <log>`. |
| m4 | MINOR | `diagnose/index.md:144` listed the file-exchange gate inaccurately: it left out `report {bundle}`, and implied every `report` needs file exchange. | **Fixed.** The list is now `screenshot`, `report {path}`, `report {bundle}` and `walk {reel}`. The destructive bullet says `report` writes a PDF, CSV or bundle, or saves a named report in the project. |
| m5 | MINOR | `diagnose/index.md:137-139`, "reverse each of these, except one", overstates: a new `note` or `fix` replaces the old text. | **Fixed.** It now says "Most of these can be reversed. Two cannot", and names both. |
| m6 | MINOR | The `propose` paragraph (`connect-an-llm.md:40-43`, `:156`) used an internal distinction, and "never will be" promised the future. | **Fixed.** It is now one sentence: the analyser's own Claude Code session has one extra verb, and outside clients never see it. |
| m7 | MINOR | No Codex way to check the tools was given (`first-session.md:3-4`, `connect-an-llm.md:143`). | **Fixed** with the command the analyser itself runs, `codex mcp get fluxtion-analyser` (`mcp/CodexMcpClient.java:139-141`), followed by an `analyser_context` call. |
| m8 | MINOR | Five facts were dropped from `support.md`: "the analyser does not say which one is right"; "none of its node logs are read"; "a node can run … and say nothing"; comparing fixed exports by a report's fingerprint; and the link to *Records, detail & filtering*. | **Fixed.** All five are back in `diagnose/index.md`. |
| m9 | MINOR | `tools/drive-analyser.sh` is not in the JBang install, and it does not check `pid`. | **Fixed.** The page says it is in the source repository, links it on GitHub, and says it does not check `pid`. |
| n1 | NIT | `connect-an-llm.md:66` quoted only the start of `EXTERNAL_OPEN`. | **Fixed.** The quote ends in "— …". |
| n2 | NIT | `sample-conversations.md:3-5` had three blank lines where the generator emits one. | **Fixed.** The page now matches what `capture-conversations.py` emits. |
| n3 | NIT | The "contain real names" warning was on two pages. | **Fixed.** It stays on `diagnose/index.md`; `hand-off.md` links to it. |
| n4 | NIT | `connect-an-llm.md:114-115` linked Spring's *Model choice*, which is off the diagnosis path. | **Fixed.** The link is removed. |
| n5 | NIT | The light states are described in four places. | **Accepted.** Each summary points to the one table, in troubleshooting, and M1 and M2 were fixed there. |
| n6 | NIT | `troubleshooting.md:30-31`, "publishes no endpoint file": a per-instance hosted file is written. | **Fixed.** It now reads "no endpoint file your client can use". |

**Regression checks.** `DocsExternalAccessClaimsTest` pins the moved quotes on their new pages: `NOT_RUNNING`, both
listener refusals and `HOSTED_ONLY` in troubleshooting; the token note in install, connect and rest-automation; the
connect page's link to the quotes. `DocsUrlResolutionTest` and the strict build with anchor validation cover the links
and the `support.md` redirect.

**Not testable by a static check, and so stated here:** the two-window ownership (M1). Its behaviour is pinned by the
code's own tests of `McpSetupState.shouldReclaim`. The page states the behaviour; nothing compares the two.
