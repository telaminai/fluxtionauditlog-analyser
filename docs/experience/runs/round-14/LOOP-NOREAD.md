
## How to verify your work — read this before you start

**You may not open the generated dispatcher source at any point.** `SurveillanceProcessor.java` under
`generated/` is off limits: do not read it, grep it, or quote it. This is a hard constraint of this run.

Your evidence channels are:

1. **`mvn test`** — the gate. A rule with a passing test is proven.
2. **The audit log** — it states, per cycle, exactly which nodes ran and in what order. S3, S6, S7, S8
   and S10 are all directly readable from it.

If you reach a question you believe only the generated source can answer, **stop and say so in your
report** — name the question and why the log and a test could not settle it. That answer is a
deliverable and is more useful than a workaround.
