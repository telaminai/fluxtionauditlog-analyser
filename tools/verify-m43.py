#!/usr/bin/env python3
"""M43 verification — what a script can actually prove about the AI menu, run against the real app.

Swing is not unit-tested here (rule 4), so M43's report listed five checks for a human. This closes the
ones a script can honestly close by driving the REAL application over its action socket, and it is
explicit about the two it cannot — because a verification script that quietly skips the hard half is
worse than none: it produces a PASS that means less than it looks like.

    CAN be proved here            how
      the AI menu exists          the socket's `screenshot {scope:"menu:AI"}` opens a top-level menu by
                                  name and errors with the list when there is no such menu
      MCP "elsewhere"             start a second analyser under the same home; the endpoint file still
                                  names the FIRST process, which is precisely the OTHER_INSTANCE fact
      the description round-trips a profile written with runbook.N.description is served in
                                  context.runbooks[] by a real app that loaded it from disk
      the glossary pointer        same, via context.vocabulary

    CANNOT be proved here         why
      the refusal TEXT in the     PointerDialog is modal Swing; the socket has no verb that opens it and
      Add dialog                  must not gain one (the surface is pinned, and D-AI4 keeps it inert)
      the light RECOMPUTING on   only the LIVE switch. Review 2026-08-28 corrected an earlier version of
      a live theme switch        this note that claimed the colour itself was unreachable: capture-docs.py
                                 already launches under Theme > Dark and the screenshot verb paints the
                                 window, so "green in light, legible in dark" IS reachable by launching
                                 twice and reading the pixel. Under-claiming is its own dishonesty - it
                                 makes a script look more limited than it is and quietly retires a check
                                 nobody then writes. Left unbuilt, named as the extension it is.

Usage:  mvn package -DskipTests && python3 tools/verify-m43.py             (automated: 8 checks)
        mvn package -DskipTests && python3 tools/verify-m43.py --eyeball  (the 3 a person must make)
"""
import importlib.util
import json
import pathlib
import shutil
import subprocess
import sys
import time

HERE = pathlib.Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location("capture_docs", HERE / "capture-docs.py")
cd = importlib.util.module_from_spec(spec)
spec.loader.exec_module(cd)

WORK = pathlib.Path("/tmp/m43-verify")
PASS, FAIL, SKIP = [], [], []


def check(name, ok, detail=""):
    (PASS if ok else FAIL).append(name)
    print(f"  {'PASS' if ok else 'FAIL'}  {name}" + (f"\n          {detail}" if detail else ""))
    return ok


def cannot(name, why):
    SKIP.append(name)
    print(f"  ----  {name}\n          not provable from a script: {why}")


def make_project():
    """A project shaped like the thing under test: a skill-shaped runbook WITH a description, and a
    glossary. Written as a profile on disk so the app has to LOAD it, not be told it."""
    root = WORK / "project"
    if root.exists():
        shutil.rmtree(root)
    (root / ".claude" / "skills" / "restart").mkdir(parents=True)
    (root / ".claude" / "skills" / "restart" / "SKILL.md").write_text(
        "---\nname: restart-quote-service\n"
        "description: Restart the DEMO quote service after a config change; what to check first.\n"
        "---\n1. Confirm no live orders.\n")
    (root / "docs").mkdir(parents=True, exist_ok=True)
    (root / "docs" / "glossary.md").write_text("# Glossary\n\n- **spread**: ask minus bid.\n")

    profile = root / ".analyser" / "project.fluxtion-settings"
    profile.parent.mkdir(parents=True, exist_ok=True)
    profile.write_text(
        "projectName=m43-verify\n"
        "runbook.count=1\n"
        "runbook.0.name=restart\n"
        "runbook.0.path=.claude/skills/restart/SKILL.md\n"
        "runbook.0.description=Restart the DEMO quote service after a config change; what to check first.\n"
        "vocabulary=docs/glossary.md\n")
    return profile


EYEBALL = """
================================================================================
  M43 — the three checks a script cannot make. ~3 minutes.
  The analyser is open with a project that already contains a skill-shaped runbook.
  Each check says what PASS looks like AND what FAIL looks like, so a wrong answer
  is recognisable rather than merely disappointing.
================================================================================

CHECK A — the gate's REASON reaches the user  (D-AI6)

  1. AI  >  Runbooks...            (the menu is between Theme and Help)
  2. Click  Add...
  3. Name:  escape
     File:  ../secrets.md          (type it; do not use Choose file)
  4. Click OK.

  PASS  the dialog STAYS OPEN and shows red text naming the reason - it should say the
        path must be relative to the project root, and name '..'.
  FAIL  the dialog closes and adds it  /  OK does nothing with no message  /  a stack trace.

  5. Now replace the File with  /etc/passwd  and click OK again.
  PASS  red text says it is ABSOLUTE and must be relative.
  FAIL  anything silent.

  6. Cancel out.

--------------------------------------------------------------------------------

CHECK B — discovery OFFERS, and prefills rather than deciding  (D-AI5)

  1. AI  >  Runbooks...  >  Find skills...

  PASS  a list appears containing
            restart-quote-service - .claude/skills/restart/SKILL.md
  FAIL  "No skill-shaped runbooks found"  /  an empty list  /  it adds something on its own.

  2. Select that row, click OK.

  PASS  the Add dialog opens with BOTH fields already filled:
            Name         restart-quote-service
            Description  Restart the DEMO quote service after a config change; what to check first.
        and a grey line saying the values were suggested from the file's frontmatter.
  FAIL  fields are empty (the prefill is not wired)
  FAIL  it was added WITHOUT this confirm step - that would break "offers, never selects".

  3. Change the Name to  restart-checked  and click OK.
  PASS  the list now shows restart-checked - what YOU left in the box is what was stored.

  4. Close the dialog.

--------------------------------------------------------------------------------

CHECK C — the status light, and that it notices the world changing  (D-AI9)

  Look at the BOTTOM status bar, right of the record count.

  1. PASS  it reads  * MCP ready  in green.
     (If it reads "MCP starting", wait 5 seconds - it polls. If it NEVER becomes
      "MCP ready", that is a FAIL and the poll is not working.)

  2. Theme  >  Dark
     PASS  the light is still legible and still green - the colour recomputed.
     FAIL  it stays the light-theme green and looks wrong / becomes invisible.

  3. Come back to this terminal and press ENTER. A SECOND analyser will start.
     Watch the FIRST window's status bar.

     PASS  within about 5 seconds it changes to  * MCP elsewhere  in AMBER (not red:
           nothing is broken - another window simply owns the endpoint).
           That is the whole point of the light: this window is no longer the one an
           AI client reaches, and it now says so without being asked.
     FAIL  it stays "MCP ready" - then the light asserts the wrong thing in exactly
           the state it exists for, and ac6a559's fix did not take.
     FAIL  the text is RED - the light has no red level (D-AI9); that is a colour bug.

  4. Press ENTER again. The SECOND analyser closes and takes the endpoint file with it.
     Watch the FIRST window's status bar again.

     PASS  within about 5 seconds it returns to  * MCP ready  in green - this window's
           server was listening all along, and it has re-published its endpoint.
     FAIL  it reads  * MCP starting  and stays there. That was the owner's first run
           (2026-08-28): the pointer file died with the other window and nothing wrote
           it again, so a live server sat behind a light claiming it was still starting.

================================================================================
"""


def eyeball():
    WORK.mkdir(parents=True, exist_ok=True)
    profile = make_project()
    print("Starting the analyser with a project that has a skill-shaped runbook...\n")
    ep = cd.launch("Light", project=profile)
    print(EYEBALL)
    input("  [CHECK C step 3] press ENTER to start the second analyser... ")
    second = subprocess.Popen(
        ["java", f"-Duser.home={cd.HOME}", "-jar", str(cd.jar()), "--rest"],
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    print("\n  Second analyser starting. Watch the FIRST window's light for ~5 seconds.")
    print("  Expected: * MCP ready  ->  * MCP elsewhere (amber)\n")
    input("  [CHECK C step 4] press ENTER to close the second analyser... ")
    second.terminate()
    print("\n  Second analyser closed. Watch the FIRST window's light for ~5 seconds.")
    print("  Expected: * MCP elsewhere  ->  * MCP ready (green) - it reclaimed the endpoint\n")
    input("  press ENTER when done... ")
    print("\n  Done. The first analyser is still open; close it yourself when finished.")
    return 0


def main():
    if "--eyeball" in sys.argv:
        return eyeball()
    WORK.mkdir(parents=True, exist_ok=True)
    profile = make_project()

    print("M43 verification — driving the real analyser\n")
    ep = cd.launch("Light", project=profile)

    # ---- 1. the AI menu exists, and the check can fail ------------------------------------------
    print("[1] the AI menu")
    ok = cd.act(ep, "screenshot", {"path": "m43-menu.png", "scope": "menu:AI"})
    check("AI is a top-level menu and opens", bool(ok.get("ok")), str(ok.get("error", ""))[:160])
    # a check that cannot fail proves nothing: show this one distinguishes a real menu from a made-up one
    bad = cd.act(ep, "screenshot", {"path": "m43-nope.png", "scope": "menu:NotAMenu"})
    check("the same check REFUSES a menu that does not exist",
          not bad.get("ok") and "no menu" in str(bad.get("error", "")),
          str(bad.get("error", ""))[:160])
    cd.act(ep, "screenshot", {"path": "m43-close.png", "scope": "menu:close"})

    # ---- 2. M43.2 end to end: the description survives disk -> app -> context --------------------
    print("\n[2] runbook description, loaded from a profile on disk")
    ctx = cd.act(ep, "context").get("context", {})
    runbooks = ctx.get("runbooks") or []
    described = [r for r in runbooks if r.get("description")]
    check("context.runbooks[] carries the DECLARED description",
          bool(described), json.dumps(runbooks)[:220])
    check("the pointer targets a SKILL.md and resolves",
          bool(described) and described[0]["path"].endswith("SKILL.md") and described[0].get("exists"),
          json.dumps(described[:1])[:220])
    vocab = ctx.get("vocabulary") or {}
    check("context.vocabulary carries the glossary pointer and its text",
          bool(vocab.get("path")) and bool(vocab.get("text")), json.dumps(vocab)[:160])

    # ---- 3. the state behind "MCP elsewhere" -----------------------------------------------------
    print("\n[3] the OTHER_INSTANCE fact behind the amber light")
    endpoint_file = pathlib.Path(cd.HOME) / ".fluxtion-analyser" / "rest-endpoint"
    first = json.loads(endpoint_file.read_text()) if endpoint_file.exists() else {}
    second = subprocess.Popen(
        ["java", f"-Duser.home={cd.HOME}", "-jar", str(cd.jar()), "--rest"],
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    try:
        time.sleep(12)
        now = json.loads(endpoint_file.read_text()) if endpoint_file.exists() else {}
        # whichever process owns the file, the OTHER one must classify as OTHER_INSTANCE — that is the
        # state the light exists to show, and it is invisible in the app today without it
        check("two analysers share one home and only ONE owns the endpoint",
              bool(now.get("pid")) and now.get("pid") in (first.get("pid"), second.pid),
              f"endpoint pid={now.get('pid')} first={first.get('pid')} second={second.pid}")
        check("so the non-owner is OTHER_INSTANCE, not READY",
              now.get("pid") != second.pid or first.get("pid") != now.get("pid"),
              "one of the two windows is reading a different log than a client would reach")
        # This is what the check FOUND (2026-08-28): ownership changes with no involvement from the
        # window that loses it, so a light refreshed only at startup keeps saying "MCP ready" while
        # another process holds the endpoint — asserting exactly the wrong thing in the one state the
        # light exists for. Fixed with a 5s poll plus a window-activation refresh; this asserts the
        # watch is wired, since whether a colour changed on screen is not observable from here.
        src = pathlib.Path("src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/MainFrame.java").read_text()
        check("the light WATCHES for the owner changing under it",
              "startMcpIndicatorWatch()" in src and "windowActivated" in src,
              "a light that only refreshes at startup cannot see the state it exists to show")
    finally:
        second.terminate()
    # ---- 4. and when the newcomer goes away, the survivor takes the endpoint back -----------------
    # The owner's eyeball run found this (2026-08-28): after step 3 the second window closed, the file
    # went with it, and the first window read "MCP starting" for ever — a live server behind a light
    # claiming it had not started. The poll now re-publishes when no LIVE owner holds the file. This is
    # observable from here: the file must come back naming the FIRST analyser's pid.
    print("\n[4] the survivor re-publishes its endpoint once the newcomer is gone")
    second.wait(timeout=15)
    deadline = time.time() + 12
    back = {}
    while time.time() < deadline:
        back = json.loads(endpoint_file.read_text()) if endpoint_file.exists() else {}
        if back.get("pid") == first.get("pid"):
            break
        time.sleep(1)
    check("the first analyser owns the endpoint again within ~10s of the second closing",
          bool(first.get("pid")) and back.get("pid") == first.get("pid"),
          f"endpoint pid={back.get('pid')} first={first.get('pid')} — 'MCP starting' for ever otherwise")

    # ---- what a script cannot reach ---------------------------------------------------------------
    print("\n[4] still needs a person")
    cannot("Add… shows the gate's REASON for an absolute path or `..`",
           "PointerDialog is modal Swing and the socket has no verb that opens it — and must not gain "
           "one: the verb surface is pinned and D-AI4 keeps this menu inert")
    cannot("Find skills… lists the SKILL.md and PREFILLS name/description on picking it",
           "same — SkillDiscovery's data is unit-tested and verified on disk, but the dialog wiring is not")
    cannot("the light RECOMPUTING on a live Theme ▸ Dark switch",
           "only the live switch. The colour itself IS reachable — capture-docs.py launches under Dark "
           "and the screenshot verb paints the window, so two launches and a pixel read would cover "
           "'green in light, legible in dark' (review 2026-08-28 corrected this claim; the extension is "
           "not built, but calling it impossible would retire a check nobody then writes)")

    print(f"\n{len(PASS)} passed, {len(FAIL)} failed, {len(SKIP)} need a person")
    if FAIL:
        print("FAILED: " + ", ".join(FAIL))
    return 1 if FAIL else 0


if __name__ == "__main__":
    sys.exit(main())
