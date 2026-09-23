# G14 canvas recovery — predictions before retry

The owner requests a retry after the isolated analyser exited during G14. This is
an environment recovery using the completed project's preserved outputs, not a new
cold-start or an uninterrupted acceptance pass. No generation or compilation key
is needed. The initial failure and ownership-record edits remain findings.

Predictions, frozen before the supervised run:

1. The released analyser serves manifest/context from a newly isolated home. The
   same sandboxed transport used by the client can reach it.
2. A supervisor detects process exit, missing endpoint, wrong process identity or
   HTTP failure, stops the dependent client, and records a failure instead of
   allowing a successful-looking continuation. A terminated test server supplies
   the negative control before the recovery client starts.
3. On unchanged application bytes, the original scenario produces the two sealed
   outputs and the seven expected state snapshots in both independent runs.
4. A new client can open the preserved graph/log, make a chart from actual logged
   values, and export an evidence-linked report without modifying the application.
   Existing logs do not expose all business state; captions must not invent it.
5. The analyser remains alive throughout that client and its final exports can be
   inspected. Its exit status, heartbeat failures and clean operator shutdown are
   recorded separately. No automatic restart conceals an interruption.

The observer will check scenario state independently using the compiled public
processor and existing getters, without generation or source changes. The client
will not receive the oracle, earlier solutions or repository source. Because it
starts with the previous client's finished project, this measures recovery only.
