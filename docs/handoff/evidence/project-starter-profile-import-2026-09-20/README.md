# Starter profile interoperability — 2026-09-20

Producer: playground `0b5660a`, following `0e7bd58`. Consumer: analyser `9244b0e` plus the
read-only `tools/VerifyStarterProfiles.java` probe committed with this packet. No app was executed.

The playground's project-support test invokes the real scaffold HTTP handler for each of its 14
catalogue entries. It exports the exact ZIP response contents to an isolated directory when
JOURNEY_FIXTURES_OUT is set. The consumer reads those files using ProjectProfile.load, checks that
all runbooks and processor declarations survive, resolves every runbook and source root, and checks
both agent files point to PROJECT.md. The profile byte hashes and consumer log are retained here.

Result: 14/14 imported without rejected declarations. This includes untagged templates and the
bundle, whose skills and project task runbooks coexist. The first expanded producer gate found
space-bearing runbook names in the earlier implementation; these are fixed. A negative witness
changes only the first name back to `Start here`: the real consumer refuses it. The original bytes
were restored in finally; `invalid-name-red.log` replaces the disposable root with `<downloads>`.

Reproduce from the two implementation branches:

```sh
# Playground, web/; use a fresh empty directory for the output.
JOURNEY_FIXTURES_OUT=/tmp/starter-profile-check pnpm vitest run src/lib/starter/project-support.test.ts
# Analyser, after mvn package; use the actual built jar name.
java --class-path target/classes:target/fluxtion-auditlog-analyser-0.0.0-SNAPSHOT.jar \
  tools/VerifyStarterProfiles.java /tmp/starter-profile-check 14
```

The probe is read-only. This witness does not establish deployed HTTP behaviour, interactive
archive acquisition/discovery, runtime runbook correctness, fresh-client success or publication.
Those remain separate acceptance items. Local starter integration tests use a provisioned jar;
they are not a public-artifact publication check.
