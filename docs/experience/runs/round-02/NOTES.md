# NOTES

Friction log for the task "track per-symbol count + max price, then show it in the audit log".

- **The project should have told me: adding a node needs a THIRD edit, not two.** `CLAUDE.md` §4
  ("Adding a node: two edits, both required") lists the `<bean>` and the `nodeBeans` entry. I made
  both and the regenerate build failed anyway:
  `cannot find matching constructor for: Field{name=symbolStats, ...} failed to match for these
  fields:[countBySymbol, maxPriceBySymbol, rootNode]`. The AOT generator reconstructs each node by
  matching its **non-transient instance fields** to a constructor, so a stateful node (my two
  `HashMap`s) cannot be built unless those fields are `transient` or appear as constructor args.
  Nothing in `CLAUDE.md`, `README.md` or the `regenerate` skill mentions this, and the two shipped
  node examples (`RootNode`, `RiskCheck`) both dodge it by holding only null-at-construction state,
  so there is no example to copy from. This is the single most expensive thing I hit.
- **I guessed at the fix above.** I inferred `transient` from the shape of the generated
  `MarketProcessor.java` (its node fields are `public final transient`) rather than from any project
  document. It worked, but it was a guess, and the error message names constructor matching, not
  transience — so "add a constructor taking the maps" would have been the equally plausible guess.
- **The project told me something that turned out to be wrong.** The `regenerate` skill says "If the
  build stops at `process-classes`, the key is why." My build stopped exactly at `process-classes`
  (the `fluxtion-maven-plugin:scan` goal is bound to that phase — `pom.xml:174`) and the key was
  *not* why; the key file was present and the failure was the constructor match above. Following the
  skill's rule would have sent me hunting a licence problem that did not exist.
- **I went outside the project to check the `auditLog` API.** The project only ever shows
  `auditLog.info(String, Object)` and `auditLog.info("price", price)`, and does not say which value
  types are supported. I wanted to log an `int` count and a `double` max without them being
  stringified, so I unpacked `fluxtion-runtime-1.0.13-sources.jar` from `~/.m2` and read
  `EventLogger.java` to confirm `info(String,int)` / `info(String,double)` overloads exist. They do.
- **The shipped demo data cannot demonstrate the feature.** `data/input.txt` has exactly one row per
  symbol, so a running count is always 1 and a running max is always the only price — the audit log
  would have looked identical to a broken implementation. I appended six rows (four more `AAPL`, one
  `MSFT`, one `NVDA`) so `AAPL` accumulates and its max (201.40) is reached *before* the last row,
  which is what makes the log evidence rather than coincidence. Nothing warned that the fixture was
  too thin for this.
- **Something worked and I am not sure why.** On boot the server logged
  `FileEventSource ... Found previous offset, trying to skip to file offset 0` / `Skipped to offset
  0`. So the file feed persists a read offset somewhere, and it replayed the whole (now longer) file
  from the start. I could not find where that offset lives — it is not in `.analyser/`, not in
  `data/`, and nothing in the project documents it. It happened to be 0 so every row replayed; had it
  been non-zero my appended rows might have been the only ones seen, or none. I did not establish
  which.
- **Minor: `RootNode.getLatestEvent()` returns `Object`.** A downstream node must `instanceof`-check
  and cast to `PriceEvent` even though the graph declares exactly one event type. Not documented as a
  convention either way; I followed `RiskCheck`'s lead and kept `Object`.

## Files I never opened

- `AGENTS.md`

(Read in full: `CLAUDE.md`, `README.md`, `.claude/skills/run-mongoose-server/SKILL.md`,
`.claude/skills/regenerate/SKILL.md`, `.claude/skills/read-audit-log/SKILL.md`.)
