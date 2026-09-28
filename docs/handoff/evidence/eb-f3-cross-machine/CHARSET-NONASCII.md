# The charset question, with data that could actually move

Recipient C ran the locale and charset variations and reported them honestly as inconclusive: *"the demo log and
graph contain no non-ASCII bytes… ASCII is identical under UTF-8, ISO-8859-1 and US-ASCII, so no charset could
have moved the record digest."* That is exactly right, and it is the kind of result that is easy to bank as a
pass and should not be.

So the test was re-run with a log that can move.

## The fixture

The DEMO log's first forty `priceListener` records carry a symbol with four kinds of non-ASCII:
`DEMO-Å–µ°中` — a Latin-1 letter, an en dash, a Greek letter, a degree sign and a CJK character. 310,303 bytes,
with non-ASCII present. The record digest is taken over the store's **parsed text**, so this is upstream of it.

## Result — identical

| | UTF-8 (default) | `-Dfile.encoding=ISO-8859-1` |
|---|---|---|
| record digest | `sha256:64abc4cf9e65d876137732b318e15978d3f2c1c61ad6c0437fbb4247d5d1cbb6` | **identical** |
| chart digest | `sha256:ac87d757…7410` | **identical** |
| normalised profile | `sha256:1c70746992cf603c64edc6e277fa26032b6e9d135c9d2b3c776b831d2c29472e` | **identical** |

The JVM confirmed the setting took effect (`Picked up JAVA_TOOL_OPTIONS: -Dfile.encoding=ISO-8859-1`).

**The control that makes this meaningful:** the record digest here is `64abc4cf…`, where the all-ASCII fixture
gave `84cf4845…`. The digest *does* move when the record text changes. So its not moving under a charset change
is a result, not an absence of one — which is the distinction recipient C was right to draw about their own run.

## What is now settled about portability

| variable | verdict |
|---|---|
| another machine | ruled out — identity and all three targets matched |
| JDK major version (21 vs 25) | ruled out, in both directions |
| case-sensitive filesystem | ruled out for capture and for unpack of a well-formed bundle |
| locale (`LANG=C`) | ruled out |
| **default charset, with non-ASCII records** | **ruled out** |
| a different OS | **still open** — no container runtime on either machine |

## Still open, and honestly

**A non-Darwin run.** Both machines were macOS; neither had a usable Docker. Everything ruled out above was
ruled out on one operating system. A CI runner would close it, and until one does, "portable" means "portable
across every variable we could vary on macOS".
