# Chart style fixes — review evidence

Subject `90746e83`, parent `1e51545d`. macOS, Corretto 21.0.8, FlatLaf 3.5.1.
All logs/project fixtures are constructed placeholders. A private `user.home` is set before constructing
MainFrame. No real project/profile or credential is read by the display probe.

Files:

- `StyleContractProbe.java`: callbacks on unchanged/changed selections, eight installed/shipped
  look-and-feels, quiet restoration, then twelve complete profile/share round trips.
- `StyleDisplayProbe.java`: actual MainFrame and Robot clicks, production autosave, explicit project
  close/reopen and log reopen; then the post-dialog import seam reproduction. Its `PASS REPRODUCED`
  line asserts the observed bug, not correct application behaviour. It does not drive the import dialogs.
- `check-mutations.py`: green baseline, two isolated mutations, restore each source byte-for-byte,
  restored tests/probe. The second mutation intentionally demonstrates an ineffective committed test.
- `contract-probe.txt`, `display-probe.txt`: exact outputs.
- `mutation-results.json`, `mutations-summary.txt`: commands, test names, failure messages and source hashes.
- `01-line-selected.png`, `02-line-reopened.png`, `03-import-overwritten.png`: real display captures,
  inspected before commit. The first two show Line survives; the third shows the old Line chart after
  a Points/closed import was overwritten.

Reproduce in a disposable checkout of the subject. Maven may need local-socket permission for the full
suite. GUI commands need a real desktop and permission to drive it; they move the pointer. Set `JAVA_HOME`
to JDK 21 and put its `bin` on PATH. The review machine used
`/Users/greg/Library/Java/JavaVirtualMachines/corretto-21.0.8/Contents/Home`.

```sh
mvn -q test
mvn -q -DskipTests package
packet=docs/handoff/evidence/chart-style-fixes-review-2026-09-24
jar=target/fluxtion-auditlog-analyser-0.0.0-SNAPSHOT.jar
classes=/private/tmp/chart-style-probe-classes
javac -cp "$jar" -d "$classes" "$packet/StyleContractProbe.java" "$packet/StyleDisplayProbe.java"
java -Djava.awt.headless=false -cp "$classes:$jar" StyleContractProbe
java -Djava.awt.headless=false -cp "$classes:$jar" StyleDisplayProbe /private/tmp/chart-style-review-display-new
python3 "$packet/check-mutations.py" "$classes"
mvn -q test
mkdocs build --strict
git diff --check
```

The review packet lives on the review branch, so copy it into the disposable subject checkout before
running these commands. The mutation runner rewrites its result JSON: preserve the committed output when
reproducing. It restores source in `finally`, but run it only in a disposable checkout with clean source.
It executes the three committed `StyleDropdownRequestsASaveTest` tests, not the entire suite under each
mutation. The jar stays the clean subject; the mutation probe uses `target/classes` before that jar.

Two initial probe-compilation attempts used the wrong HeapLogStore constructor; neither was a product
failure or counted as a test result. The preserved probe uses the real String constructor. No GUI run
needed a retry. After the final mutation both changed production source files matched their original
SHA-256; the generated Maven POM was restored separately.
