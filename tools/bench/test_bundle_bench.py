import importlib.util
import os
import pathlib
import shutil
import sys
import tempfile
import unittest
import zipfile


SCRIPT = pathlib.Path(__file__).with_name("bundle-bench.py")
SPEC = importlib.util.spec_from_file_location("bundle_bench", SCRIPT)
bundle_bench = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = bundle_bench
SPEC.loader.exec_module(bundle_bench)


class BundleBenchTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="m19-bundle-bench-")
        self.root = pathlib.Path(self.temp.name) / "demo-bundle"
        self.root.mkdir()
        self.write_fixture()

    def tearDown(self):
        self.temp.cleanup()

    def write(self, path, text, executable=False):
        target = self.root / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(text, encoding="utf-8")
        if executable:
            target.chmod(0o755)

    def write_fixture(self):
        description1 = "Open this project's exported audit log."
        description2 = "Run, export and stop this project's server."
        profile = f"""share.version=1
sourceRoot.count=1
sourceRoot.0=src/main/java
eventProcessorFqn.count=1
eventProcessorFqn.0=com.acme.demo.generated.DemoProcessor
selectedEventProcessor=com.acme.demo.generated.DemoProcessor
runbook.count=2
runbook.0.name=load-audit-log
runbook.0.path=.claude/skills/load-audit-log/SKILL.md
runbook.0.description={description1}
runbook.1.name=run-mongoose-server
runbook.1.path=.claude/skills/run-mongoose-server/SKILL.md
runbook.1.description={description2}
skills.provenance=canonical@rev-42
"""
        guide = """# Demo bundle

Bundle contract: **m19-bundle/3** · skills: `canonical@rev-42`

Run `./run-server.sh`, then `./export-audit.sh`, then `./stop-server.sh`.
Open `logs/audit-demo-bundle.yaml` with
`src/main/resources/com/acme/demo/generated/DemoProcessor.graphml`.
"""
        skill1 = f"""---
name: load-audit-log
description: {description1}
x-analyser-min-version: 1.12.0
---
Open `logs/audit-demo-bundle.yaml` with
`src/main/resources/com/acme/demo/generated/DemoProcessor.graphml`.
"""
        skill2 = f"""---
name: run-mongoose-server
description: {description2}
x-analyser-min-version: 1.12.0
---
Use `./run-server.sh`, `./export-audit.sh`, and `./stop-server.sh`.
The export is `logs/audit-demo-bundle.yaml`.
"""
        self.write(".analyser/project.fluxtion-settings", profile)
        self.write("CLAUDE.md", guide)
        self.write("AGENTS.md", guide)
        self.write("README.md", guide)
        self.write(".claude/skills/load-audit-log/SKILL.md", skill1)
        self.write(".claude/skills/run-mongoose-server/SKILL.md", skill2)
        self.write("src/main/java/com/acme/demo/generated/DemoProcessor.java", "package com.acme.demo.generated;")
        self.write("src/main/resources/com/acme/demo/generated/DemoProcessor.graphml", "<graphml/>")
        for script in bundle_bench.COMMAND_SCRIPTS:
            self.write(script, "#!/bin/bash\nexit 0\n", executable=True)

    def checks(self, source=None):
        bundle = bundle_bench.Bundle(source or self.root)
        try:
            return bundle_bench.check_bundle(bundle, "1.12.0")
        finally:
            bundle.close()

    def assert_passes(self, checks):
        failed = [check for check in checks if not check.ok]
        self.assertEqual([], failed, "\n".join(f"{c.name}: {c.detail}" for c in failed))

    def test_valid_directory_passes(self):
        self.assert_passes(self.checks())

    def test_v2_one_based_and_singular_profile_fails_loudly(self):
        profile = (self.root / bundle_bench.PROFILE).read_text()
        profile = profile.replace("sourceRoot.0=", "sourceRoot.1=")
        profile = profile.replace("eventProcessorFqn.count=1\neventProcessorFqn.0=", "eventProcessorFqn=")
        profile = profile.replace("runbook.0.", "runbook.2.").replace("runbook.1.", "runbook.3.")
        (self.root / bundle_bench.PROFILE).write_text(profile)

        failed = {check.name for check in self.checks() if not check.ok}

        self.assertIn("sourceRoot members are exactly zero-based 0..count-1", failed)
        self.assertIn("eventProcessorFqn.count is an integer", failed)
        self.assertIn("runbook members are exactly zero-based 0..count-1", failed)

    def test_unknown_contract_and_surviving_placeholder_fail(self):
        guide = (self.root / "CLAUDE.md").read_text().replace("m19-bundle/3", "m19-bundle/2")
        guide += "\nTODO(bundle): fill this later\n"
        self.write("CLAUDE.md", guide)
        self.write("AGENTS.md", guide)

        failed = {check.name for check in self.checks() if not check.ok}

        self.assertIn("agent guide declares exactly one supported contract", failed)
        self.assertIn("no generated file contains a bundle placeholder", failed)

    # ------------------------------------------------------------------ contract v4
    # These were added with check_v4 and NEVER RAN: the shared fixture declares m19-bundle/3, so check_v4
    # returned immediately every time. The bench is what a playground generator is told to run as its
    # definition of done, so an untested half of it is worse than no half at all.

    V4_REFERENCE_BLOCK = (
        "- <https://fluxtion-playground.dev/build-with-ai> — the loop\n"
        "- <https://fluxtion-playground.dev/CLAUDE.md> — the orientation\n"
        "- <https://fluxtion-playground.dev/fluxtion-golden-path.md> — the blessed shape\n"
        "\n<!-- reference-block:end -->\n")

    def make_v4(self, *, spring=False, reference_block=None, agents=None, extra_paths=()):
        """Promote the shared fixture to a v4 bundle and return its checks."""
        guide = (self.root / "CLAUDE.md").read_text().replace("m19-bundle/3", "m19-bundle/4")
        guide += "\n" + (self.V4_REFERENCE_BLOCK if reference_block is None else reference_block)
        if spring:
            guide += ("- <https://fluxtion-playground.dev/spring-authoring/contract.md> — the field table\n")
            self.write("src/main/fluxtion/designer/application-context.xml", "<beans/>")
        self.write("CLAUDE.md", guide)
        self.write("AGENTS.md", guide if agents is None else agents)
        for path, body in extra_paths:
            self.write(path, body)
        return {c.name: c for c in self.checks()}

    def test_v4_passes_when_the_reference_block_is_complete(self):
        checks = self.make_v4()
        v4 = [c for n, c in checks.items() if "reference" in n.lower() or "AGENTS.md" in n or "replay" in n]
        self.assertTrue(v4, "check_v4 did not run at all — the fixture is not declaring m19-bundle/4")
        for check in v4:
            self.assertTrue(check.ok, f"{check.name}: {check.detail}")

    def test_v4_fails_when_an_always_on_reference_is_missing(self):
        checks = self.make_v4(reference_block="- <https://fluxtion-playground.dev/build-with-ai> — only one\n")
        failed = {n for n, c in checks.items() if not c.ok}
        self.assertIn("CLAUDE.md points at every always-on agreed resource", failed)

    def test_v4_refuses_a_spring_link_in_a_NON_spring_project(self):
        checks = self.make_v4(reference_block=self.V4_REFERENCE_BLOCK
                              + "- <https://fluxtion-playground.dev/spring-authoring/contract.md> — nope\n")
        failed = {n for n, c in checks.items() if not c.ok}
        self.assertIn("CLAUDE.md omits the spring resource for a non-spring project", failed,
                      "appliesTo must SELECT, not annotate")

    def test_v4_requires_the_spring_link_for_a_spring_project(self):
        checks = self.make_v4(spring=True)
        self.assertTrue(checks["CLAUDE.md points at the spring resource"].ok)

    def test_v4_refuses_an_excluded_resource(self):
        checks = self.make_v4(reference_block=self.V4_REFERENCE_BLOCK
                              + "- <https://fluxtion-playground.dev/audit-replay> — excluded upstream\n")
        failed = {n for n, c in checks.items() if not c.ok}
        self.assertIn("CLAUDE.md ships no EXCLUDED resource", failed)

    def test_v4_refuses_a_restated_rule(self):
        checks = self.make_v4(reference_block=self.V4_REFERENCE_BLOCK
                              + "\nMark node state @FluxtionIgnore or declare transient.\n")
        failed = {n for n, c in checks.items() if not c.ok}
        self.assertIn("CLAUDE.md restates no rule the agreed set already carries", failed,
                      "pointing is the point — a restated rule stops upstream edits helping")

    def test_v4_requires_AGENTS_md_to_be_generated_not_written(self):
        checks = self.make_v4(agents="# hand-written and different\n")
        failed = {n for n, c in checks.items() if not c.ok}
        self.assertIn("AGENTS.md is byte-identical to CLAUDE.md (generated, not hand-written)", failed)

    def test_v4_refuses_replay_shipped_with_an_unsubstituted_marker(self):
        checks = self.make_v4(extra_paths=[
            (".claude/skills/replay-a-run/SKILL.md",
             "---\nname: replay-a-run\n---\nTODO(bundle): name the replay command\n")])
        failed = {n for n, c in checks.items() if not c.ok}
        self.assertIn("a bundle shipping replay-a-run has a replay entry point it can name", failed)

    def test_v4_fails_closed_when_the_reference_block_boundary_is_MISSING(self):
        """Contract decision: bound the restated-rule scan by an explicit marker, not by a sentence.

        Without the marker the boundary is unknown. Scanning the whole file would flag the reference
        block's own text; scanning none of it would pass anything. Refusing is the only honest option.
        """
        block = self.V4_REFERENCE_BLOCK.replace("\n<!-- reference-block:end -->\n", "")
        checks = self.make_v4(reference_block=block)
        failed = {n for n, c in checks.items() if not c.ok}
        self.assertIn("CLAUDE.md marks the end of the reference block", failed)

    def test_v4_scans_for_restated_rules_only_BELOW_the_boundary(self):
        """A rule named ABOVE the marker is the reference block describing itself, not a restatement."""
        above = ("- <https://fluxtion-playground.dev/CLAUDE.md> — the orientation, incl. the source-gen "
                 "triage table\n" + self.V4_REFERENCE_BLOCK)
        checks = self.make_v4(reference_block=above)
        self.assertTrue(checks["CLAUDE.md restates no rule the agreed set already carries"].ok,
                        "a link's own description must not count as a restatement")

    def test_a_passing_check_never_carries_a_FAILURE_WORD(self):
        """Review F6: check_v4 printed "differs" beside a GREEN AGENTS.md check.

        A hand-back is meant to be evidence, and a passing line that reads like a problem devalues every
        other line on the page. Note the property is NOT "a passing check has no detail" - my first
        attempt asserted that and it is false: the contract check legitimately reports which version it
        found. What must never appear on a green line is a word that MEANS failure.
        """
        failure_words = ("differs", "missing", "unreadable", "not found", "invalid", "refused")
        # BOTH contracts. Checked on v3 only, this missed the very check that prompted the finding, since
        # check_v4 does not run on a v3 bundle — the first mutation test I ran passed for that reason.
        for label, checks in (("v3", self.checks()), ("v4", self.make_v4().values())):
            for check in checks:
                if not check.ok:
                    continue
                detail = (check.detail or "").lower()
                for word in failure_words:
                    self.assertNotIn(word, detail,
                                     f"{label}: passing check reads as a failure: "
                                     f"{check.name!r} -> {check.detail!r}")

    def test_zip_preserves_the_same_contract_and_executable_checks(self):
        archive = pathlib.Path(self.temp.name) / "bundle.zip"
        with zipfile.ZipFile(archive, "w") as out:
            for path in self.root.rglob("*"):
                if path.is_file():
                    out.write(path, "demo-bundle/" + path.relative_to(self.root).as_posix())

        self.assert_passes(self.checks(archive))

    def test_zip_traversal_is_not_hidden_by_top_level_normalisation(self):
        archive = pathlib.Path(self.temp.name) / "unsafe.zip"
        with zipfile.ZipFile(archive, "w") as out:
            for path in self.root.rglob("*"):
                if path.is_file():
                    out.write(path, "demo-bundle/" + path.relative_to(self.root).as_posix())
            out.writestr("../outside.txt", "must not escape")

        failed = {check.name for check in self.checks(archive) if not check.ok}

        self.assertIn("inventory paths are unique and project-relative", failed)

    def test_minimum_analyser_version_is_enforced(self):
        skill = self.root / ".claude/skills/load-audit-log/SKILL.md"
        skill.write_text(skill.read_text().replace("1.12.0", "9.0.0"))

        failed = [check for check in self.checks()
                  if check.name == "runbook.0 minimum analyser version is supported"]

        self.assertEqual(1, len(failed))
        self.assertFalse(failed[0].ok)

    def test_developer_path_and_literal_key_are_refused(self):
        self.write("notes.txt", "/Users/alice/private/project\napiKey=live-secret-value\n")

        failed = {check.name for check in self.checks() if not check.ok}

        self.assertIn("no generated file contains an absolute developer-home path", failed)
        self.assertIn("no generated file contains a literal Fluxtion key value", failed)

    def test_none_mode_has_no_skills_or_runbooks_and_still_passes(self):
        profile_path = self.root / bundle_bench.PROFILE
        profile = "\n".join(
            line for line in profile_path.read_text().splitlines()
            if not line.startswith("runbook.") and not line.startswith("skills.provenance=")
        ) + "\nrunbook.count=0\nskills.provenance=none\n"
        profile_path.write_text(profile)
        shutil.rmtree(self.root / ".claude")
        for name in ("CLAUDE.md", "AGENTS.md", "README.md"):
            path = self.root / name
            path.write_text(path.read_text().replace("canonical@rev-42", "none"))

        self.assert_passes(self.checks())

    def test_clean_https_mirror_provenance_is_shown_and_passes(self):
        provenance = "mirror:https://mirror.example/fluxtion/skills@rev-42"
        profile = self.root / bundle_bench.PROFILE
        profile.write_text(profile.read_text().replace("canonical@rev-42", provenance))
        for name in ("CLAUDE.md", "AGENTS.md", "README.md"):
            path = self.root / name
            path.write_text(path.read_text().replace("canonical@rev-42", provenance))

        self.assert_passes(self.checks())


if __name__ == "__main__":
    unittest.main()
