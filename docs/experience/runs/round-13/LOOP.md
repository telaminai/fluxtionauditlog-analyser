
## How to verify your work — read this before you start

Your evidence channels, in order of cost:

1. **`mvn test`** — the gate. A rule with a passing test is proven.
2. **The audit log** — it states, per cycle, exactly which nodes ran and in what order. S3, S6, S7, S8
   and S10 are all directly readable from it.
3. **Reading generated or dispatch source** — the last resort.

Use (3) **only** when a test fails or the audit log contradicts your intent. If you do use it, state in
your report **why the log was not sufficient**. That answer is part of the deliverable.
