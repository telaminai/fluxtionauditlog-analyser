#!/usr/bin/env bash
# A configuration-indexed registry of native images, so a later run can be compared against an earlier
# one and DRIFT IS VISIBLE rather than silent.
#
# Why this exists. Round 63 measured a 30-node no-audit graph at 9.7 ns and an earlier session had
# recorded 3.41 ns for the same shape. Neither number was wrong; the harness had changed — the newer one
# constructed the processor in main() and passed it in, so it escaped and its nodes could no longer be
# scalar-replaced. Nothing reported that. A row per binary, carrying every input that decides the
# result, is what makes that difference findable instead of a mystery.
#
# The columns are chosen so ONE variable can be isolated at a time: hold every other column equal and
# the difference is attributable. That only works if the columns are complete, so add one whenever a
# new input is found to matter.
#
# Usage:
#   KIT_OUT=<scratch dir> ./index-binaries.sh > binaries.tsv
#   KIT_OUT=<scratch dir> TAG=$(git describe --always --dirty) ./index-binaries.sh >> binaries.tsv
#
# TAG should identify the SOURCE the binaries were built from — a git tag or describe output. Without
# it a row records how a binary was built but not what was built, and the code is as much an input as
# the flags are.
set -euo pipefail
SP=${KIT_OUT:?set KIT_OUT to the directory holding the nimg* directories}
TAG=${TAG:-$(git describe --always --dirty 2>/dev/null || echo "untagged")}
STAMP=${STAMP:-$(date -u +%Y-%m-%dT%H:%M:%SZ)}

printf "tag\tstamp\tbinary\tsha1\tbytes\tprofile_sha1\tgc\tpgo\ttarget\tinline\tisolates\tgraph\trecord\tclock\tns\n"
for img in "$SP"/nimg*/*; do
  [ -f "$img" ] && [ -x "$img" ] || continue
  case "$img" in *.log|*.iprof|*.collect|*_inst) continue;; esac
  log="$img.log"; [ -f "$log" ] || continue
  name=$(basename "$img"); dir=$(basename "$(dirname "$img")")
  sha=$(shasum -a1 "$img" | cut -c1-12)
  size=$(stat -f%z "$img")
  prof="$img.iprof"; psha=$([ -f "$prof" ] && shasum -a1 "$prof" | cut -c1-12 || echo "-")
  gc=$(grep -o "Garbage collector: [A-Za-z ]*" "$log" | head -1 | sed 's/Garbage collector: //' | tr -d ' ')
  pgo=$(grep -o "PGO: [a-z-]*" "$log" | head -1 | sed 's/PGO: //')
  tgt=$(grep -o "target machine: [a-z0-9.+-]*" "$log" | head -1 | sed 's/target machine: //')
  inl=$(grep -q "PriorityForceInline" "$log" && echo yes || echo no)
  iso=$(grep -q "SpawnIsolates" "$log" && echo off || echo on)
  # the collect run records the arm the profile was gathered under — the remaining inputs
  col="$img.collect"
  graph=$([ -f "$col" ] && grep -o "graph=[a-z]*" "$col" | head -1 | cut -d= -f2 || echo "-")
  rec=$([ -f "$col" ] && grep -o "record=[a-z]*" "$col" | head -1 | cut -d= -f2 || echo "-")
  clk=$([ -f "$col" ] && grep -o "clock=[a-z]*" "$col" | head -1 | cut -d= -f2 || echo "-")
  ns=$([ -f "$img.ns" ] && cat "$img.ns" || echo "-")
  printf "%s\t%s\t%s/%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n" \
      "$TAG" "$STAMP" "$dir" "$name" "$sha" "$size" "$psha" "${gc:--}" "${pgo:--}" "${tgt:--}" \
      "$inl" "$iso" "${graph:--}" "${rec:--}" "${clk:--}" "$ns"
done
