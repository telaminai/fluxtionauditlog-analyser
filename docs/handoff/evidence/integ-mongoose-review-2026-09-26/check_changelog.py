"""Independent merge-content check, not a permanent release-format rule."""
import hashlib
import json
import re
import subprocess
import tempfile
from pathlib import Path

PARENTS = ("a5bd6e0c", "aa268458", "da5377d9", "39001c35")

def source(ref):
    return subprocess.check_output(["git", "show", ref + ":CHANGELOG.md"], text=True)

def unreleased(text):
    return re.split(r"^## ", text.split("## [Unreleased]", 1)[1], maxsplit=1, flags=re.M)[0]

def lines(text):
    return {line for line in unreleased(text).splitlines() if line.strip() and not line.startswith("#")}

def check(text):
    present = lines(text)
    missing = {ref: sorted(lines(source(ref)) - present) for ref in PARENTS}
    assert not any(missing.values()), "parent Unreleased lines lost: " + str(missing)
    paragraphs = [p for p in unreleased(text).split("\n\n") if p.startswith("- ")]
    assert len(paragraphs) == len(set(paragraphs)), "duplicated change paragraphs"

original = source("5776e750").encode()
check(original.decode())
results = []
with tempfile.TemporaryDirectory(prefix="changelog-integration-") as d:
    path = Path(d) / "CHANGELOG.md"
    backup = Path(d) / "original"
    backup.write_bytes(original)
    for ref in PARENTS[:2]:
        path.write_bytes(original)
        check(path.read_text())
        path.write_text(source(ref))
        try:
            check(path.read_text())
        except AssertionError as error:
            result = {"parentOnly": ref, "namedFailure": str(error)}
        else:
            raise AssertionError("parent-only control survived: " + ref)
        path.write_bytes(backup.read_bytes())
        subprocess.run(["cmp", str(path), str(backup)], check=True)
        check(path.read_text())
        result.update(baselineGreen=True, restoredGreen=True, byteIdentical=True,
                      sha256=hashlib.sha256(original).hexdigest())
        results.append(result)
print(json.dumps(results, indent=2))
