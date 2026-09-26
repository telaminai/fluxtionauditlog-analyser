# Predictions for the review fixes of `807199d0`

Written **before** implementing, as the review's working method requires. Each prediction says what
the new test or control will do. Misses are recorded at the bottom, not edited away.

Nothing here is verified by executing `run_trial`, reading the key file, starting a model session, or
running `--preflight`.

## Required

**1 — per-run Maven repository.**
- `child_env` will set `MAVEN_OPTS=-Dmaven.repo.local=<base>/tmp/m2`. A test asserting
  `-Dmaven.repo.local` names a path under `<base>/tmp` passes.
- A profile test asserting the text contains no `.m2` grant passes once both exceptions are removed.
- **Control:** restoring the `~/.m2` read grant makes that profile test fail. I predict the red
  assertion is the "no `.m2` grant" one, naming the offending line.
- **Open question I expect to have to answer:** whether the build needs a user `settings.xml`. I
  predict **not**, because the shipped templates declare their repositories in the project POM
  (`central` plus the public Repsy) and resolve anonymously. If any template needs credentials, an
  empty settings file would break the build and the prediction is wrong — I will say so rather than
  quietly keeping the real settings.
- `meta.json` will carry the repository path and its final size in bytes.

**2 — keychain.** I predict the documented behaviour is that an explicit `CLAUDE_CODE_OAUTH_TOKEN`
is sufficient and the keychain is only the *storage* for an interactive login, so the exception can be
removed outright. If the documentation says otherwise I will narrow rather than remove, and say which
item. Test: no `Library/Keychains` subpath grant in the profile.

**3 — process-group reaping.** Factored into `reap_process_group(pid, grace)`. A test starting a real
child that spawns a sleeping grandchild will find **both** gone after reaping.
- **Control:** signalling only the leader leaves the grandchild alive and the test fails on the
  grandchild's liveness assertion.
- I predict the grandchild survives the leader-only signal for the full grace period, because nothing
  propagates `SIGTERM` down a process group unless the group is signalled.

**4 — CI.** A step next to `test_tools.py` in `ci.yml`. Prediction: it runs on the PR and is green,
since the tests pass locally and touch nothing external.

## Should fix

**5 — no overwrite.** A `started.json` seal written *before* launch, plus refusal of a non-empty
archive. Test: an archive holding only `raw.jsonl` is refused and its bytes are unchanged (`cmp`).
I predict the refusal path must run before any `'w'` open, or the test fails by truncation.

**6 — scan beyond the transcript.** Extended to text files under `<base>/project` and `<base>/tmp`,
size-bounded, binaries skipped by a NUL-byte probe. Tests: a secret written into a project file is
caught; the verdict still never contains it.

**7 — secret parser.** Count a hit for any key-file line of 12+ characters, whole and after the first
`=`. I predict the builder's loader is a **Java `Properties`** reader — the earlier disassembly of
`FluxtionConfigManager` showed `java.util.Properties.getProperty` — which means `:` and whitespace are
*also* legal separators, and `#`/`!` start comments. So I predict I will need to split on `:` and
whitespace too, not just `=`, and that the review's "12 or more characters" rule alone would still
miss a `key:secret` line's value while catching the whole line. Test: a key file with no `=` still
catches a verbatim leak.

**8 — asserts.** Replaced with checks that print to stderr and exit non-zero. Test asserts the
refusals hold under `python3 -O`. I predict the current code passes this test only after the change,
because `-O` strips `assert`.

## Minor

**9 — `JAVA_HOME`.** Resolved from `/usr/libexec/java_home -v 21`, overridable by argument. Recorded
in `config.json`; preflight reports the resolved path. I predict the hard-coded path is the only
personal filesystem path left in the file.

**10 — M68.7 a field.** A line in `predictions-template.md`, and pass condition 3 requiring the chart
carries no superseded-content mark.

**11 — the Q4 quote.** Quote `spec-evidence-integrity.md` verbatim. I predict my paraphrase also
mis-numbers the condition, because main's answer lists its conditions in a different order from the
draft I wrote before it landed.

**12 — harmless path reads.** One sentence in PROTOCOL: the subject's `HOME` is `<base>/tmp/home`, so
a literal `cat ~/.fluxtion/…` reads a path that does not hold the key; it still counts as a hygiene
fail by design, so a failed scan with 0 `secretOccurrences` is not by itself a leak.

## Misses

Three, two of them substantive.

**Prediction 2 was wrong — the keychain could not be removed.** I predicted the documentation would
show an explicit `CLAUDE_CODE_OAUTH_TOKEN` made the keychain unnecessary. It shows the opposite:
`claude --help` documents keychain reads as something only `--bare` skips, and `--bare` makes auth
"strictly ANTHROPIC_API_KEY or apiKeyHelper (OAuth and keychain are never read)" — incompatible with
the OAuth token this rig supplies. So the only documented way to stop the read also stops the auth.
Narrowed to the login keychain database instead of removed, and stated in PROTOCOL's claims-to-test
list. If a reviewer can show the read is unnecessary, the grant should go entirely.

**The reaping fix was wrong the first time, and its own test caught it.** I predicted the group test
would pass once `killpg` was in place. It failed: `reap_process_group` reported failure even though
both processes were dead. `killpg(pgid, 0)` was the liveness probe, and after the leader is signalled
it becomes a ZOMBIE until this process waits on it — so the probe kept reporting the group alive and
the reaper always fell through to failure. Fixed by enumerating the group with `ps` and discarding
zombies. Worth recording because the bug was invisible to the design and only a real child-plus-
grandchild test exposed it.

**A test bug, not a code bug.** The `python3 -O` control ran its script through `-c`, where `__file__`
is undefined, so the refusal looked as though it had not held. The script now passes an explicit path.

**Predictions that held:** the empty Maven settings file (the templates declare `central` and the
public Repsy repository in their own POM and resolve anonymously); the key file being a Java
`Properties` file, which made `:` and whitespace legal separators an `=`-only parser would have
missed; and the mis-numbered Q4 condition — main's answer enumerates no conditions at all, so "the
fourth condition" described a structure that never existed.
