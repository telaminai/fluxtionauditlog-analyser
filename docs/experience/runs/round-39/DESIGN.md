# Twelve stages, five subsystems, four shared event types

Bigger than round 37 in the two ways that matter: **more stages per subsystem**, and **events consumed
by more than one subsystem**, so the dispatch has several entry points fanning into one interleaved
graph rather than a single chain from one event.

## Events, and who consumes each

| event | consumed by |
|---|---|
| `TICK` | marketdata (`mid`, `depth`), liquidity (`book`) |
| `TRADE` | risk (`notional`), capital (`buffer`) |
| `RATE` | pricing (`spread`), risk (`var`) |
| `CONFIG` | marketdata (`vol`), capital (`charge`) |

**Every event type is shared by at least two subsystems.** No subsystem owns an entry point.

## The twelve stages

| subsystem | stages | reads |
|---|---|---|
| marketdata | `mid` | TICK |
| | `depth` | TICK |
| | `vol` | CONFIG, `mid` |
| pricing | `adjusted` | `mid`, `depth` |
| | `spread` | RATE, `adjusted` |
| liquidity | `book` | TICK, `depth` |
| | `score` | `adjusted`, `book` |
| risk | `notional` | TRADE, `mid` |
| | `exposure` | `notional`, `score` |
| | `var` | RATE, `exposure`, `vol` |
| capital | `charge` | CONFIG, `exposure` |
| | `buffer` | TRADE, `charge`, `var` |

Three subsystems now have stages at three different depths, and every subsystem is interleaved with at
least two others. A composition that runs subsystems as units is wrong in several places at once, not
one.
