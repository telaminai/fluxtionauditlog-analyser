# tools

## `bench/` — cross-repository conformance benches

`tools/bench/loop-bench.py` runs the agent-brokered dev loop end to end (registry file → export → drive
the analyser → assert) against either `mongoose-stub.py` (no Mongoose needed — the contract as a fixture)
or a real `~/.mongoose/servers/` directory. `bundle-bench.py` statically preflights a generated M19
directory or zip before the live keyless run. See [`bench/README.md`](bench/README.md).

## `drive-analyser.sh`

Drives a **running** analyser over its localhost REST transport, for scripted UI setup and screenshots.

Requires `Settings ▸ Assistant ▸ localhost REST` to be on (it is opt-in and off by default). The app
publishes its URL and token to `~/.fluxtion-analyser/rest-endpoint`; this script reads them.

```bash
tools/drive-analyser.sh source_root '{"add":["/path/to/src/main/java"]}'
tools/drive-analyser.sh open        '{"processor":"com.acme.demo.generated.DemoQuoteProcessor"}'
tools/drive-analyser.sh open        '{"graphml":"/path/to/processor.graphml","log":"/path/to/audit.yaml"}'
tools/drive-analyser.sh goto        '{"recordIndex":5,"reveal":true}'
tools/drive-analyser.sh topology    '{"select":"quotePublisher","scope":"neighbours","step":2}'
tools/drive-analyser.sh topology    '{"source":true}'
tools/drive-analyser.sh screenshot  '{"path":"/tmp/shot.png"}'
```

`screenshot` has the app paint itself, so it needs no screen-recording permission and captures exactly
the state the other verbs set up — but it cannot draw the **native title bar**, which the window server
owns. Its echo includes `windowBounds`, so a caller that *does* hold the macOS Screen Recording
permission can take a real window capture of the same window:

```bash
B=$(tools/drive-analyser.sh screenshot '{"path":"/tmp/painted.png"}')
python3 - "$B" <<'PY'
import json, subprocess, sys
b = json.loads(sys.argv[1])["wrote"]["windowBounds"]
subprocess.run(["screencapture", "-x", "-R",
                f"{b['x']},{b['y']},{b['width']},{b['height']}", "/tmp/window.png"])
PY
```

## The investigation loop

`context` is the verb that makes the rest worth having. The other ten let an assistant **change** the
view; `context` lets it **see** one — the active filter, the selection, the user's flags *and their
notes*, the topology cursor, the open graphs, and the source configuration.

That removes the copy-a-prompt seam. The human does the expensive part — narrowing the log, flagging what
looks wrong, writing down why — and the assistant reads it directly:

```bash
tools/drive-analyser.sh context
```

It returns **pointers, not payloads**: record indexes and byte offsets, never record text, so the answer
stays small and you fetch only what you need with `read`. The `filter` it reports is in the exact shape
`aggregate` takes, so "answer this using my filter" is passing it straight back:

```bash
F=$(tools/drive-analyser.sh context | python3 -c "import json,sys;print(json.dumps(json.load(sys.stdin)['context']['filter']))")
tools/drive-analyser.sh aggregate "{\"metric\":\"count\",\"groupBy\":\"dimension\",\"filter\":$F}"
```

**Reach.** `open` and `source_root` touch the filesystem — see `docs/proposals/upstream-asks.md` and the
`AppControl` javadoc for why they are held behind their own interface.
