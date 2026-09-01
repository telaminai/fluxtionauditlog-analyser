
## What you must deliver

1. **The engine.** A class `SurveillanceProcessor` in package `com.acme.surveillance.generated` that
   accepts events one at a time and drives your nodes. There is **no event-processing framework
   available and none may be added** — the JDK only. How dispatch works is your design.
2. A runnable `Main` that feeds a scenario through the processor and writes
   `logs/surveillance-audit.yaml` in the shape given above.
3. **JUnit tests** under `src/test/java`. `mvn test` must be green. Cover every rule S1–S10 and every
   detector, including the cases where a detector must NOT trip.
4. **Evidence.** For each rule S1–S10, cite either a passing test or the audit-log lines that
   demonstrate it. Say which, for each.
