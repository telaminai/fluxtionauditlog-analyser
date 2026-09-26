# Predictions for the second-pass review fixes of `eb70ec53`

Written **before** implementing. The second review found four blocking defects, all reproduced with
probes that needed no model session and no credentials — which is itself the headline: text-only SBPL
assertions were not enough, and a cheap enforcement probe found a launch-blocking rule.

Nothing below is verified by executing `run_trial`, reading the key file, or starting a model session.

## 1 — the sandbox aborts allowed commands

SBPL passes multiple filters to `deny` as **alternatives**. The final rule therefore reads "deny
anything under `~/.fluxtion/` **or** anything that is not the key file", and the second half matches
everything, so every read aborts. The reviewer saw `/bin/cat` abort with SIGABRT on an allowed project
file.

Prediction: wrapping both filters in `require-all` fixes it, and an enforcement test using
`sandbox-exec` plus `/bin/cat` over disposable fixtures will show **both** directions — an allowed
project file readable, a protected fixture denied. I predict the positive half is the one that would
have caught this; a negative-only test passes happily against a deny-everything policy, which is
exactly how this shipped. macOS-only, skipped elsewhere.

## 2 — normal exit leaves descendants running

`reap_process_group` resolves the group by `getpgid(pid)`. After a normal exit the leader is gone, the
lookup raises, and the function reports success while the group still runs. Prediction: capturing the
group id **at launch** and reaping that id, independent of the leader's survival, fixes it; a
regression that lets the leader exit normally while a child sleeps will fail before the fix and pass
after.

## 3 — valid Properties encodings evade the scan

The separator inference was right and the implementation still is not the format. Two shapes decode
to values the scanner never sees: `\uXXXX` escapes and backslash-newline continuations. Both produced
CLEAN.

Prediction: a real parser is needed, and it must also read the file as **ISO-8859-1**, because
`Properties.load(InputStream)` decodes bytes that way before processing escapes — reading UTF-8 would
be a third wrong answer for any non-ASCII byte. I predict the fix is to decode properly, keep the raw
lines as a belt-and-braces candidate set, and **fail closed** by recording when a line uses a form the
parser does not support, rather than silently returning fewer candidates.

## 4 — failed enumeration becomes successful cleanup

`_group_members` returns `[]` on any `ps` failure or timeout, and `[]` reads as "group empty, cleanup
succeeded". Prediction: enumeration must return **unknown** distinctly from **empty**, `ps`'s exit
status must be checked, and unknown must make the reaper report failure. Separately, `run_trial`'s
exit code currently reflects only the scan, so a false or unverified cleanup does not stop acceptance;
I predict a third exit code is needed so unverified cleanup cannot be read as a pass.

## What I expect to get wrong

The Properties parser is the piece most likely to be incomplete again. Java's format has more corners
than the two the reviewer found — escaped separators inside keys, `!` comments, trailing-backslash
counting (an even number of backslashes does *not* continue the line), and the natural-line versus
logical-line distinction. I predict I will implement those and still not match the JDK on some input,
which is why the raw-line candidates stay and why unsupported forms must be recorded rather than
dropped.

## Misses

**Predictions that held.** All four diagnoses and fixes landed as predicted, including the two details
I flagged as easy to get wrong: ISO-8859-1 as the input encoding, and the odd-backslash rule for
continuations. The JDK cross-check (`test_agrees_with_the_jdk`) confirms the decoder matches
`java.util.Properties` on every shape the reviewer used, so the prediction that I would "implement
those and still not match the JDK on some input" did **not** come true for the tested cases — though
it remains true that the format has corners none of these tests reach, which is why the raw-line
candidates and the fail-closed path stay.

**Two misses, both in the enforcement test's fixtures rather than the fix.** The enforcement probe is
new ground and both mistakes made a test lie in the safe-looking direction:

1. I first placed the fake home *inside* the base under `/private/tmp`. The policy's second deny
   alternative — everything under `/private/tmp` that is not `base/project` or `base/tmp` — then
   swallowed the key file, and `test_the_key_file_itself_is_readable` failed in a way that looked
   exactly like a policy bug. It was a fixture that did not mirror production, where home is
   `/Users/<user>` and the two alternatives cannot overlap.
2. Then both negative assertions passed for the wrong reason. `TemporaryDirectory()` lives under
   `/var/folders`, `/var` is a symlink to `/private/var`, and SBPL matches the **resolved** path — so
   the unresolved subpath in the policy never matched anything and "denied" was indistinguishable
   from "no rule applied". Resolving the fixture paths fixed it.

Both are recorded because they are the same class of error as the defect being fixed: a check that
passes without exercising what it claims to exercise.
