#!/usr/bin/env bash
# State the EXACT configuration behind a control, in one place.
#
# "What was this number taken against?" has to be answerable without archaeology. Round 63 spent a
# section discovering that a 3.41 ns figure and a 9.7 ns figure differed only by the harness, because
# no single artefact recorded harness, runtime, profile and flags together.
#
# Usage:  KIT_OUT=<scratch> describe-control.sh [control-name]
set -uo pipefail
export SP=${KIT_OUT:?set KIT_OUT to the directory holding the control binaries}
export JAVA_HOME=${JAVA_HOME:?set JAVA_HOME}
export RT=${RT:?set RT to the fluxtion-runtime jar}
BANDS="$(dirname "$0")/control-bands.tsv"
WANT="${1:-}"
TAB=$'\t'

field() { grep -o "$2=[A-Za-z0-9._-]*" <<<"$1" | head -1 | cut -d= -f2 || true; }

while IFS="$TAB" read -r control toolchain harness low high cmd; do
  case "$control" in \#*|"") continue;; esac
  [ -z "$WANT" ] || [ "$WANT" = "$control" ] || continue

  probe=$(eval "$cmd" 2>/dev/null || true)
  bin=$(awk '{for(i=1;i<=NF;i++) if ($i ~ /nimg|\/bin\/java/) {print $i; exit}}' <<<"$cmd" | tr -d '"')
  bin=$(eval echo "$bin" 2>/dev/null)

  echo "=============================================================="
  echo "control     : $control  [$toolchain]"
  echo "band        : $low – $high ns   (harness $harness)"
  echo "--------------------------------------------------------------"
  echo "harness     : $(field "$probe" harness)          <- from the binary, not a build log"
  echo "runtime     : $(grep -o 'rt:[0-9a-f]*' <<<"$probe" | head -1 || echo NONE)"
  echo "graph       : $(field "$probe" graph)"
  echo "record      : $(field "$probe" record)"
  echo "clock       : $(field "$probe" clock)"
  echo "verified    : recPerEvent=$(field "$probe" recPerEvent)  bytes/record=$(field "$probe" avgRecBytes)  checksum v=$(field "$probe" v)"

  if [ -f "$bin" ] && [ -x "$bin" ] && [ -f "$bin.log" ]; then
    echo "--------------------------------------------------------------"
    echo "binary      : $bin"
    echo "  sha1      : $(shasum -a1 "$bin" | cut -c1-12)   $(stat -f%z "$bin") bytes"
    echo "  profile   : $([ -f "$bin.iprof" ] && shasum -a1 "$bin.iprof" | cut -c1-12 || echo '-')"
    echo "  gc        : $(grep -o 'Garbage collector: [A-Za-z ]*' "$bin.log" | head -1 | sed 's/.*: //')"
    echo "  pgo       : $(grep -o 'PGO: [a-z-]*' "$bin.log" | head -1 | sed 's/PGO: //')"
    echo "  target    : $(grep -o 'target machine: [a-z0-9.+-]*' "$bin.log" | head -1 | sed 's/.*: //')"
    echo "  inline    : $(grep -q PriorityForceInline "$bin.log" && echo 'directive present' || echo 'ABSENT')"
    echo "  isolates  : $(grep -q SpawnIsolates "$bin.log" && echo off || echo on)"
  else
    echo "--------------------------------------------------------------"
    echo "jvm         : $($JAVA_HOME/bin/java -version 2>&1 | head -1)"
    echo "runtime jar : $(shasum -a1 "$RT" | cut -c1-12)"
  fi
  echo "source tag  : $(git -C "$(dirname "$0")" describe --always --dirty 2>/dev/null || echo untagged)"
  echo "machine     : $(sysctl -n machdep.cpu.brand_string 2>/dev/null || uname -m)"
done < "$BANDS"
