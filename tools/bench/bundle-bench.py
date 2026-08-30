#!/usr/bin/env python3
"""Static preflight for an M19 generated analyser bundle (contract m19-bundle/3 or /4).

This is the analyser-owned first half of P3. It accepts either an unzipped project directory or the
download zip and checks the contract facts that do not require starting Maven/Mongoose: safe inventory,
guide/version mirror, exact profile ABI, source/GraphML paths, runbook/frontmatter parity, provenance,
minimum analyser version, command scripts and placeholder refusal.

It deliberately does NOT claim the live P3 result. The live half still has to run the bundle's own
run/export/stop commands and drive a fresh analyser over REST/MCP against the resulting YAML + GraphML.

Usage:
    tools/bench/bundle-bench.py path/to/project-or.zip --analyser-version 1.12.0
"""

import argparse
import dataclasses
import pathlib
import re
import stat
import sys
import zipfile


CONTRACT = "m19-bundle/3"
CONTRACT_V4 = "m19-bundle/4"
SUPPORTED_CONTRACTS = (CONTRACT, CONTRACT_V4)

# v4 D-B2: the agreed reference set. Kept here as DATA, fetched from the analyser at build time in a real
# generator — this bench is a static preflight and must run offline, so it pins the four agreed URLs and
# the one that is appliesTo-gated. If reference-set.json changes, this list changes with it.
REFERENCE_ALWAYS = (
    "https://fluxtion-playground.dev/build-with-ai",
    "https://fluxtion-playground.dev/CLAUDE.md",
    "https://fluxtion-playground.dev/fluxtion-golden-path.md",
)
REFERENCE_BY_KIND = {"spring": "https://fluxtion-playground.dev/spring-authoring/contract.md"}
REFERENCE_EXCLUDED = (
    # excluded upstream and must never be shipped: it tells authors no instrumentation is needed
    "https://fluxtion-playground.dev/audit-replay",
)
REPLAY_SKILL = "replay-a-run"

# v4 D-B2: the machine-readable end of the generated reference block. The generator emits it; the bench
# bounds its restated-rule scan with it. Deliberately an HTML comment: invisible when rendered, and it
# leaves the surrounding prose free to be improved without breaking a parser.
REFERENCE_BLOCK_END = "<!-- reference-block:end -->"
PROFILE = ".analyser/project.fluxtion-settings"
GUIDES = ("CLAUDE.md", "AGENTS.md")
COMMAND_SCRIPTS = ("run-server.sh", "export-audit.sh", "stop-server.sh")
PROVENANCE = re.compile(
    r"(?:canonical|local)@[A-Za-z0-9._-]{1,120}"
    r"|mirror:https://[^\s@?#]+@[A-Za-z0-9._-]{1,120}"
    r"|none"
)
FQN = re.compile(r"[A-Za-z_$][A-Za-z0-9_$]*(?:\.[A-Za-z_$][A-Za-z0-9_$]*)+")
RUNBOOK_NAME = re.compile(r"[A-Za-z0-9_-]{1,40}")


@dataclasses.dataclass(frozen=True)
class Check:
    name: str
    ok: bool
    detail: str = ""


class Bundle:
    def __init__(self, source):
        self.source = pathlib.Path(source)
        self._zip = None
        self._paths = []
        self._duplicates = set()
        self._unsafe = set()
        self._archive_names = {}
        if self.source.is_dir():
            self._paths = sorted(
                p.relative_to(self.source).as_posix()
                for p in self.source.rglob("*") if p.is_file()
            )
        elif self.source.is_file() and zipfile.is_zipfile(self.source):
            self._zip = zipfile.ZipFile(self.source)
            raw_files = [info.filename for info in self._zip.infolist() if not info.is_dir()]
            self._unsafe.update(path for path in raw_files if not safe_relative(path))
            parts = [pathlib.PurePosixPath(path).parts for path in raw_files]
            # buildStarterZip intentionally wraps the project in <artifact>/. Accept exactly one
            # common safe top-level directory, but never normalize an unsafe member into safety.
            common_root = (
                parts[0][0] if parts and not self._unsafe
                and all(len(p) >= 2 and p[0] == parts[0][0] for p in parts)
                else None
            )
            seen = set()
            for archive_name in raw_files:
                logical = archive_name[len(common_root) + 1:] if common_root else archive_name
                if logical in seen:
                    self._duplicates.add(logical)
                seen.add(logical)
                self._paths.append(logical)
                self._archive_names[logical] = archive_name
            self._paths.sort()
        else:
            raise ValueError("input must be a project directory or zip file")

    def close(self):
        if self._zip is not None:
            self._zip.close()

    @property
    def paths(self):
        return tuple(self._paths)

    @property
    def duplicates(self):
        return frozenset(self._duplicates)

    @property
    def unsafe(self):
        return frozenset(self._unsafe)

    def exists(self, path):
        return path in self._paths

    def read(self, path):
        if self._zip is not None:
            return self._zip.read(self._archive_names[path])
        return (self.source / pathlib.PurePosixPath(path)).read_bytes()

    def executable(self, path):
        if self._zip is not None:
            info = self._zip.getinfo(self._archive_names[path])
            return bool((info.external_attr >> 16) & 0o111)
        return bool((self.source / pathlib.PurePosixPath(path)).stat().st_mode & stat.S_IXUSR)


def safe_relative(path):
    if not path or "\\" in path or path.startswith("/"):
        return False
    p = pathlib.PurePosixPath(path)
    return all(part not in ("", ".", "..") for part in p.parts)


def parse_properties(text):
    values = {}
    errors = []
    for number, raw in enumerate(text.splitlines(), 1):
        line = raw.strip()
        if not line or line.startswith(("#", "!")):
            continue
        if line.endswith("\\"):
            errors.append(f"line {number}: continuations are not valid generated-profile output")
            continue
        if "=" not in line:
            errors.append(f"line {number}: expected key=value")
            continue
        key, value = line.split("=", 1)
        key, value = key.strip(), value.strip()
        if key in values:
            errors.append(f"line {number}: duplicate key {key}")
        values[key] = value
    return values, errors


def parse_frontmatter(text):
    if not text.startswith("---\n"):
        return {}, ["missing opening ---"]
    end = text.find("\n---\n", 4)
    if end < 0:
        return {}, ["missing closing ---"]
    values = {}
    errors = []
    for raw in text[4:end].splitlines():
        if ":" not in raw:
            errors.append(f"invalid frontmatter line: {raw}")
            continue
        key, value = raw.split(":", 1)
        key, value = key.strip(), value.strip()
        if key in values:
            errors.append(f"duplicate frontmatter key {key}")
        values[key] = value
    return values, errors


def version_tuple(value):
    if not re.fullmatch(r"\d+\.\d+\.\d+", value or ""):
        raise ValueError(f"not a three-part numeric version: {value!r}")
    return tuple(int(part) for part in value.split("."))


def check_v4(bundle, contract):
    """BUNDLE CONTRACT v4 (D-B1/D-B2/D-B4). Silent for a v3 bundle: v3 predates these obligations."""
    checks = []
    if contract != CONTRACT_V4:
        return checks

    def add(name, ok, detail=""):
        checks.append(Check(name, ok, detail))

    guide = bundle.read("CLAUDE.md").decode("utf-8", errors="ignore")
    kind = "spring" if bundle.exists("src/main/fluxtion/designer/application-context.xml") else None

    missing = [u for u in REFERENCE_ALWAYS if u not in guide]
    add("CLAUDE.md points at every always-on agreed resource", not missing, ", ".join(missing))

    # appliesTo SELECTS rather than annotates (review N1): a non-Spring project must not be charged for
    # a Spring link in a file that costs every turn
    for k, url in REFERENCE_BY_KIND.items():
        if kind == k:
            add(f"CLAUDE.md points at the {k} resource", url in guide, url)
        else:
            add(f"CLAUDE.md omits the {k} resource for a non-{k} project", url not in guide, url)

    shipped_excluded = [u for u in REFERENCE_EXCLUDED if u in guide]
    add("CLAUDE.md ships no EXCLUDED resource", not shipped_excluded, ", ".join(shipped_excluded))

    # D-AX1b: pointing is the point. Restating a rule the agreed set carries is the duplication that
    # produced four wrong versions of the audit contract, and it is why an upstream edit stops helping.
    #
    # BOUNDARY (contract decision, 2026-08-30): the scan is bounded by an explicit MARKER, not by matching
    # a sentence. A prose sentence is written for a human and must stay free to improve; welding a parser
    # to it makes every wording change a breaking change, and the two repos had already drifted
    # ("Everything below…" vs "Below this line…") before anyone noticed. An HTML comment renders as
    # nothing, says exactly one thing, and belongs to the machine.
    body_start = guide.find(REFERENCE_BLOCK_END)
    if body_start < 0:
        # FAIL CLOSED. Without the marker the boundary is unknown, and scanning the whole file would
        # flag the reference block's own text. Refusing is the honest answer; guessing is not.
        add("CLAUDE.md marks the end of the reference block", False,
            "expected " + REFERENCE_BLOCK_END + " — without it the restated-rule scan cannot be bounded")
    else:
        add("CLAUDE.md marks the end of the reference block", True)
        body = guide[body_start + len(REFERENCE_BLOCK_END):]
        restated = [t for t in ("@FluxtionIgnore", "declare transient", "source-gen triage") if t in body]
        add("CLAUDE.md restates no rule the agreed set already carries", not restated, ", ".join(restated))

    agents = bundle.read("AGENTS.md").decode("utf-8", errors="ignore") if bundle.exists("AGENTS.md") else None
    agents_ok = agents is not None and agents == guide
    # the detail is the FAILURE reason, so it must be empty on a pass. Emitting "differs" beside a green
    # check makes a hand-back read as evidence of a problem it does not have (review F6).
    add("AGENTS.md is byte-identical to CLAUDE.md (generated, not hand-written)", agents_ok,
        "" if agents_ok else ("missing" if agents is None else "differs"))

    # D-B1: replay carries a marker only a real replay entry point can substitute
    replay = [p for p in bundle.paths if REPLAY_SKILL in p and p.endswith("SKILL.md")]
    if replay:
        add("a bundle shipping replay-a-run has a replay entry point it can name",
            all(b"TODO(bundle)" not in bundle.read(p) for p in replay),
            "replay skill shipped with its marker unsubstituted: " + ", ".join(replay))
    else:
        add("no replay-a-run shipped, so no replay entry point is claimed", True)
    return checks


def check_bundle(bundle, analyser_version):
    checks = []

    def add(name, ok, detail=""):
        checks.append(Check(name, bool(ok), str(detail) if detail else ""))
        return bool(ok)

    unsafe = sorted(set(path for path in bundle.paths if not safe_relative(path)) | set(bundle.unsafe))
    add("inventory paths are unique and project-relative",
        not unsafe and not bundle.duplicates,
        "; ".join(unsafe + sorted(bundle.duplicates)))

    required = [PROFILE, "CLAUDE.md", "AGENTS.md", "README.md", *COMMAND_SCRIPTS]
    missing = [path for path in required if not bundle.exists(path)]
    if not add("required root inventory is present", not missing,
               "missing: " + ", ".join(missing) if missing else ""):
        return checks

    claude = bundle.read("CLAUDE.md")
    agents = bundle.read("AGENTS.md")
    add("AGENTS.md is a byte-identical CLAUDE.md mirror", claude == agents)
    try:
        guide = claude.decode("utf-8")
    except UnicodeDecodeError as exc:
        add("agent guide is UTF-8", False, exc)
        return checks
    versions = re.findall(r"^Bundle contract: \*\*(m19-bundle/\d+)\*\*", guide, re.MULTILINE)
    add("agent guide declares exactly one supported contract",
        len(versions) == 1 and versions[0] in SUPPORTED_CONTRACTS, repr(versions))
    checks += check_v4(bundle, versions[0] if len(versions) == 1 else None)

    offenders = []
    developer_paths = []
    key_material = []
    for path in bundle.paths:
        data = bundle.read(path)
        if b"TODO(bundle)" in data or b"/path/to/" in data:
            offenders.append(path)
        text = data.decode("utf-8", errors="ignore")
        if re.search(r"(?:/Users/|/home/)[A-Za-z0-9._-]+/|[A-Za-z]:\\Users\\", text):
            developer_paths.append(path)
        for match in re.finditer(r"(?m)^\s*apiKey\s*=\s*(\S+)\s*$", text):
            if match.group(1) not in ("YOUR_KEY", "MISSING_KEY", "..."):
                key_material.append(path)
    add("no generated file contains a bundle placeholder", not offenders, ", ".join(offenders))
    add("no generated file contains an absolute developer-home path",
        not developer_paths, ", ".join(developer_paths))
    add("no generated file contains a literal Fluxtion key value", not key_material, ", ".join(key_material))

    for script in COMMAND_SCRIPTS:
        add(f"{script} is executable", bundle.executable(script))
        add(f"agent guide documents ./{script}", f"./{script}" in guide)

    try:
        profile_text = bundle.read(PROFILE).decode("utf-8")
    except UnicodeDecodeError as exc:
        add("profile is UTF-8", False, exc)
        return checks
    props, prop_errors = parse_properties(profile_text)
    add("profile is deterministic key=value syntax", not prop_errors, "; ".join(prop_errors))
    add("profile declares share.version=1", props.get("share.version") == "1",
        repr(props.get("share.version")))

    forbidden = sorted(key for key in props
                       if key in ("projectName", "skills.source")
                       or "apikey" in key.lower() or key.startswith("log."))
    add("profile contains no fictitious, retrieval, log or key properties",
        not forbidden, ", ".join(forbidden))

    def numbered(prefix, minimum=0, maximum=40):
        raw_count = props.get(prefix + ".count")
        try:
            count = int(raw_count)
        except (TypeError, ValueError):
            add(f"{prefix}.count is an integer", False, repr(raw_count))
            return []
        if not add(f"{prefix}.count is in bounds", minimum <= count <= maximum, count):
            return []
        expected = set(range(count))
        found = {
            int(match.group(1)) for key in props
            if (match := re.fullmatch(re.escape(prefix) + r"\.(\d+)", key))
        }
        add(f"{prefix} members are exactly zero-based 0..count-1",
            found == expected, f"expected {sorted(expected)}, found {sorted(found)}")
        return [props.get(f"{prefix}.{index}") for index in range(count)]

    source_roots = numbered("sourceRoot", minimum=1, maximum=16)
    safe_roots = all(value is not None and safe_relative(value) for value in source_roots)
    add("source roots are project-relative", safe_roots, repr(source_roots))
    missing_roots = [value for value in source_roots if value and not any(
        path == value or path.startswith(value.rstrip("/") + "/") for path in bundle.paths)]
    add("every source root exists in the bundle", not missing_roots, ", ".join(missing_roots))

    processors = numbered("eventProcessorFqn", minimum=1, maximum=40)
    add("event processor entries are Java FQNs",
        all(value is not None and FQN.fullmatch(value) for value in processors), repr(processors))
    selected = props.get("selectedEventProcessor")
    add("selectedEventProcessor names a declared processor",
        selected is not None and selected in processors, repr(selected))
    source_candidates = []
    if selected:
        suffix = selected.replace(".", "/") + ".java"
        source_candidates = [root.rstrip("/") + "/" + suffix for root in source_roots if root]
    add("the selected generated processor source is committed",
        any(bundle.exists(path) for path in source_candidates), ", ".join(source_candidates))

    provenance = props.get("skills.provenance")
    add("skills.provenance is present and sanitised",
        provenance is not None and PROVENANCE.fullmatch(provenance), repr(provenance))
    shown_provenance = re.findall(r"\bskills:\s*`([^`]+)`", guide)
    add("agent guide shows the profile's exact skills provenance",
        provenance is not None and shown_provenance == [provenance], repr(shown_provenance))

    raw_runbook_count = props.get("runbook.count")
    try:
        runbook_count = int(raw_runbook_count)
    except (TypeError, ValueError):
        runbook_count = -1
        add("runbook.count is an integer", False, repr(raw_runbook_count))
    add("runbook.count is in bounds", 0 <= runbook_count <= 40, runbook_count)
    expected_runbook_keys = {
        f"runbook.{index}.{field}"
        for index in range(max(runbook_count, 0)) for field in ("name", "path", "description")
    }
    found_runbook_keys = {key for key in props if re.fullmatch(r"runbook\.\d+\.(name|path|description)", key)}
    runbook_keys_ok = found_runbook_keys == expected_runbook_keys
    # same rule as the AGENTS.md check (review F6): the detail is the failure reason, so on a pass it is
    # empty. "missing=[] extra=[]" beside a green line is noise that reads like a problem.
    add("runbook members are exactly zero-based 0..count-1", runbook_keys_ok,
        "" if runbook_keys_ok else
        "missing=" + repr(sorted(expected_runbook_keys - found_runbook_keys))
        + " extra=" + repr(sorted(found_runbook_keys - expected_runbook_keys)))

    declared_skill_paths = set()
    declared_names = set()
    for index in range(max(runbook_count, 0)):
        name = props.get(f"runbook.{index}.name")
        path = props.get(f"runbook.{index}.path")
        description = props.get(f"runbook.{index}.description")
        prefix = f"runbook.{index}"
        add(prefix + " has a valid unique name",
            name is not None and RUNBOOK_NAME.fullmatch(name) and name not in declared_names, repr(name))
        if name:
            declared_names.add(name)
        path_ok = path is not None and safe_relative(path) and path.startswith(".claude/skills/")
        add(prefix + " has a safe skill path", path_ok, repr(path))
        if not path_ok:
            continue
        declared_skill_paths.add(path)
        if not add(prefix + " skill file exists", bundle.exists(path), path):
            continue
        try:
            skill_text = bundle.read(path).decode("utf-8")
        except UnicodeDecodeError as exc:
            add(prefix + " skill file is UTF-8", False, exc)
            continue
        frontmatter, fm_errors = parse_frontmatter(skill_text)
        add(prefix + " has valid frontmatter", not fm_errors, "; ".join(fm_errors))
        add(prefix + " name matches frontmatter and directory",
            frontmatter.get("name") == name and pathlib.PurePosixPath(path).parent.name == name,
            repr(frontmatter.get("name")))
        add(prefix + " description matches frontmatter",
            description is not None and frontmatter.get("description") == description,
            repr(frontmatter.get("description")))
        minimum = frontmatter.get("x-analyser-min-version")
        try:
            supported = version_tuple(minimum) <= version_tuple(analyser_version)
        except ValueError as exc:
            add(prefix + " minimum analyser version is valid", False, exc)
        else:
            add(prefix + " minimum analyser version is supported", supported,
                f"needs {minimum}; bench {analyser_version}")

    actual_skill_paths = {path for path in bundle.paths
                          if path.startswith(".claude/skills/") and path.lower().endswith("/skill.md")}
    add("profile declares exactly the shipped skills",
        actual_skill_paths == declared_skill_paths,
        "declared=" + repr(sorted(declared_skill_paths)) + " actual=" + repr(sorted(actual_skill_paths)))
    if provenance == "none":
        add("none provenance emits no skills/runbooks", runbook_count == 0 and not actual_skill_paths)
    else:
        add("Mongoose bundle carries load and run/export/stop skills",
            {"load-audit-log", "run-mongoose-server"}.issubset(declared_names), repr(sorted(declared_names)))

    text_paths = ["CLAUDE.md", "README.md", *sorted(actual_skill_paths)]
    declarations = "\n".join(bundle.read(path).decode("utf-8", errors="replace") for path in text_paths)
    graph_paths = sorted(set(re.findall(r"(?:\./)?(src/[A-Za-z0-9_./-]+\.graphml)\b", declarations)))
    log_paths = sorted(set(re.findall(r"(?:\./)?(logs/[A-Za-z0-9_.-]+\.ya?ml)\b", declarations)))
    add("all instructions declare one concrete GraphML path", len(graph_paths) == 1, repr(graph_paths))
    add("the declared GraphML exists", len(graph_paths) == 1 and bundle.exists(graph_paths[0]),
        graph_paths[0] if len(graph_paths) == 1 else "")
    add("all instructions declare one concrete audit YAML path", len(log_paths) == 1, repr(log_paths))
    if len(graph_paths) == 1:
        graph = pathlib.PurePosixPath(graph_paths[0])
        discovery_roots = [pathlib.PurePosixPath(root) for root in source_roots if root]
        discovery_roots.append(pathlib.PurePosixPath("src/main/resources"))
        in_scanned_tree = any(graph.is_relative_to(root) and len(graph.relative_to(root).parts) <= 12
                              for root in discovery_roots)
        # Existence is part of the claim, not a neighbouring one. The check says discovery CAN OFFER this
        # graph; discovery cannot offer a file that is not there, so a shape-only test made this pass green
        # beside two reds about the same missing file (production bundle, 2026-08-30). Two reds for one
        # cause is fine - they are different properties - but a green line that is false is not.
        present = bundle.exists(graph_paths[0])
        add("day-two bounded GraphML discovery can offer the declared graph",
            in_scanned_tree and present,
            "" if (in_scanned_tree and present) else
            (f"{graph} is not under a scanned root" if not in_scanned_tree
             else f"{graph} is declared but absent, so nothing can discover it"))

    return checks


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("bundle", help="an unzipped bundle directory or generated zip")
    parser.add_argument("--analyser-version", required=True,
                        help="analyser version used for x-analyser-min-version checks")
    args = parser.parse_args(argv)
    try:
        version_tuple(args.analyser_version)
        bundle = Bundle(args.bundle)
    except (OSError, ValueError, zipfile.BadZipFile) as exc:
        print(f"FAIL  open bundle — {exc}")
        return 2
    try:
        checks = check_bundle(bundle, args.analyser_version)
    finally:
        bundle.close()
    for check in checks:
        suffix = f" — {check.detail}" if check.detail else ""
        print(f"{'PASS' if check.ok else 'FAIL'}  {check.name}{suffix}")
    failed = [check for check in checks if not check.ok]
    print(f"\n{len(checks) - len(failed)} passed, {len(failed)} failed")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
