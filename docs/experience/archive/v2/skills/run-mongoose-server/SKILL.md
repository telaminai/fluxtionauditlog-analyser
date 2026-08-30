---
name: run-mongoose-server
description: Start the local server, produce an audit log, and stop it again — including how to recover when a previous run left the port held.
x-analyser-min-version: 1.12.0
---

# Run the server and get an audit log

**No Fluxtion API key is needed to run.** The generated processor ships with the project.

## The sequence — the first command BLOCKS

`run-server.sh` ends in `exec java … -jar`. It does not return. Background it:

```bash
./run-server.sh > server.log 2>&1 &        # blocks in the foreground otherwise
```

Wait until it is actually serving before exporting — exporting early gives a short log that looks
identical to a node that never fired:

```bash
for i in $(seq 1 60); do
  grep -q "started" server.log 2>/dev/null && break
  sleep 1
done
```

Then:

```bash
./export-audit.sh          # writes logs/audit-<name>.yaml
./stop-server.sh
```

## The file feed remembers where it got to

On boot you will see `Found previous offset, trying to skip to file offset N`. The file event source
persists a read position between runs. If you **change `data/input.txt` and re-run**, a non-zero offset
means the server may replay only your new rows — or none — rather than the whole file.

So when a run's output does not match the data you think you fed it, suspect this before suspecting your
node. Where the offset is stored is not documented in this project; treat a run after a data change as
unreliable until you have confirmed the record count matches the file.

## When the port is already held

This is the case the scripts handle worst, so check it first rather than after a confusing failure.

`run-server.sh` writes `~/.mongoose/servers/<name>` **before** the server is up, and does not remove it if
the boot then fails. So after a failed boot that file names a **dead** pid while a **different, live**
server still holds the port — and `stop-server.sh` will report a stale entry and **exit 0** without
stopping anything.

**Therefore: trust the port, not the registry file, when they disagree.**

```bash
lsof -nP -iTCP:8181 -sTCP:LISTEN      # the real owner of the port
kill -TERM <pid>                      # if it is yours to stop
```

The registry file is reliable while a server is running normally. It is unreliable immediately after a
failed boot, which is exactly when you will be reading it.
