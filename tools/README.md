# tools

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

**Reach.** `open` and `source_root` touch the filesystem — see `docs/proposals/upstream-asks.md` and the
`AppControl` javadoc for why they are held behind their own interface.
