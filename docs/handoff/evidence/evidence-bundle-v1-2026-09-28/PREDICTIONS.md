# Evidence bundle v1 — predictions, before any code (2026-09-28)

Spec: `docs/specs/spec-evidence-bundle-packaging.md` r2 (`dff81924`). Scored in `RESULTS.md` beside this file.

## B0 · M69.F3, the caveat once per walk

1. A node change only (`WalkPlayback`: one boolean, reset per play). **No regeneration**: a new field, and no new
   handler, fact or effect.
2. The regression is red before the fix at a named assertion: step 2 of a two-step record walk still carries the
   caveat.
3. The four existing caveat tests pass unchanged. Their walks have one step, and "once" is the same as "every
   step" for one.
4. A replay (a new play request) states the caveat again on its first content step, because it is a new showing.

## B1 · `context.log.generation`

5. One `put` in `MainFrame.context`, projected from the session snapshot, with no new node. It sits inside the
   existing `log` section, so `ContextSections.SECTIONS` is unchanged. The recorded projection byte counts in
   `ContextSectionsTest` are also unchanged, because that fixture is hand-built.
6. The regression is a frame test: the generation equals the snapshot's, and increases when another log is opened.
   It is red before the change, because the key is absent.
7. `ProjectPanelIsRevealOnlyTest` is unaffected (the Project panel does not read the key).

## Both

8. Headless count: +3 or so (B0) and +0 headless (B1 is a frame test, so +1 skip headless). The frame suite for B1
   is registered in both CI lists.
9. Two to four new mutation controls, all caught. No retained control is orphaned, because neither edit touches a
   controlled line.

## B2 · the CLI: `--pack`, `--verify`, `--unpack` (added before B2's code, after B0/B1 landed)

10. One new pure class, `telamin.fluxtion.audit.analyser.bundle.EvidenceBundle`, using the existing `llm.Json` for
    the manifest (no new dependency), and three flags in `Main`, dispatched **before any UI bootstrap**, as `--mcp`
    is. No session node changes and no regeneration.
11. Headless tests with **pinned fixtures**: a DEMO folder packed with a fixed clock gives a **fixed identity**,
    asserted as a literal string. Verification cases:
    - changed member;
    - missing member;
    - unlisted member;
    - path escape (`../`), absolute path, backslash;
    - a duplicate entry;
    - a missing or malformed manifest.

    Each refusal names the member. Each regression is red before at a named assertion, and B2's code does not
    exist yet, so "red before" here means **each check's control** removes its guard and fails its named case.
12. `--unpack` extracts nothing on refusal, into a fresh directory it creates; the received file's bytes are unchanged
    (EP-A5).
13. No output ever says or implies "authenticated" or "verified sender". A test asserts the `limits` text is
    printed, and that the words "authentic" and "signed" appear only in the negative sentence.
14. About 8–10 new controls.
15. **What would show the design wrong:** a case where a correct bundle cannot be verified without trusting the
    folder layout, or an identity that changes between two packs of identical bytes with the same clock (a
    non-deterministic manifest). Either would mean the format is not pinned.
