
## What you must deliver

1. **The graph**, built with a `FluxtionGraphBuilder`. Generated class `SurveillanceProcessor`, package
   `com.acme.surveillance.generated`, output `src/main/java`, resources `src/main/resources`.
2. **Audit logging enabled**, and a runnable `Main` that feeds a scenario through the processor and
   writes `logs/surveillance-audit.yaml` in the shape given above.
3. **JUnit tests** under `src/test/java`. `mvn test` must be green. Cover every rule S1–S10 and every
   detector, including the cases where a detector must NOT trip.
4. **Evidence.** For each rule S1–S10, cite either a passing test or the audit-log lines that
   demonstrate it. Say which, for each.
