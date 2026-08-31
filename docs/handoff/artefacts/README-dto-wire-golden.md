# `dto-wire-golden-builder-1.0.64.bin` — a real old-client payload, captured while it was still possible

**What it is.** The exact bytes `fluxtion-builder` **1.0.64** — the last released builder — sends to the
source-gen service when compiling a graph. 27,027 bytes, captured 2026-08-31 by pointing a real
`mvn -Pregen process-classes` at a recording proxy via `-Dfluxtion.remote.host` /
`-Dfluxtion.remote.port`, which forwarded upstream untouched. **Not constructed, not reconstructed** —
this is what a pinned client actually puts on the wire.

**Why it is here and not in the compiler repo.** It belongs there. The compiler working tree was mid-merge
under another session, so it is parked here rather than not captured. **Please move it.**

**Why it could not wait.** The capture is only possible while 1.0.64 is still the last release. After the
next release the "last released client" is a different artefact, and the bytes a 1.0.64 client would have
sent are unrecoverable — you would be reconstructing them from source rather than recording them.
Everything else in the DTO gate can be written later; this could not.

## What the capture revealed

**The payload is Java serialization, not Kryo.** It begins `ac ed 00 05` — `ObjectOutputStream` stream
magic — followed by `sr` and `com.telamin…`. A real AOT Maven build takes the Java-serialization path by
default; Kryo (`application/x-kryo`) is the other format, selected by a system property.

That is not a defect, and it does refine the gate. `DtoWireCompatibilityTest` says in its own words that
a `serialVersionUID` change is not the failure mode it is worried about, and gates a **Kryo registration
id shift** — correctly, and the append-don't-insert rule it enforces is right. But it gates the format
a default Maven build does not use. Both paths are real; only one has a golden, and this is the other one.

The Java path is not unguarded — `TopologicallySortedDependencyGraphDto` and `NodeDto` both pin
`serialVersionUID`, which is the right mechanism. What a pinned UID does **not** cover is a changed field
*type* or a changed class hierarchy, which throws at the receiver with a pinned UID just the same.

## What to do with it

Add one test in the compiler repo that **deserialises this file at HEAD**. That is the old-client /
new-server case for the format the default path uses, and it is the check that would have caught a
breaking DTO change before every un-bumped user hit it simultaneously.

Then, per the pre-release notice, capture the matching **response** — the `EventProcessorModel` today's
server returns — and add the reverse direction. The response has **no closing window**: it is the current
server's output and can be captured at any time from any client, which is why only the request is here.

Add a new pair on each release rather than replacing this one. The matrix you want is "every released
client against today's server", and it builds itself one file at a time.

## Provenance and hygiene

* Graph: this repo's own `SessionProcessor` (M44), so every class name in the payload is public.
* Swept for the repo's rule-1 terms: clean. Checked for credential material: none — the API key travels
  in a header, and the headers file was deliberately **not** captured into the repo.
* The recorder forwarded requests untouched and never rewrote a body.
