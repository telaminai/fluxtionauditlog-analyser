"""The true dependency graph, read off the constructor signatures the suppliers publish.

C4 must accept ANY valid topological order, not just the reference's. Two stages with no path
between them may run in either order and both are correct - round 38 already recorded this
("the two orders differ only where the graph does not constrain them").
"""
DEPS = {
    "marketdata.tickIn": [], "marketdata.configIn": [], "liquidity.tickIn": [],
    "pricing.rateIn": [], "risk.rateIn": [], "risk.tradeIn": [],
    "capital.tradeIn": [], "capital.configIn": [],
    "marketdata.mid":   ["marketdata.tickIn"],
    "marketdata.depth": ["marketdata.tickIn"],
    "marketdata.vol":   ["marketdata.configIn", "marketdata.mid"],
    "pricing.adjusted": ["marketdata.mid", "marketdata.depth"],
    "pricing.spread":   ["pricing.rateIn", "pricing.adjusted"],
    "liquidity.book":   ["liquidity.tickIn", "marketdata.depth"],
    "liquidity.score":  ["pricing.adjusted", "liquidity.book"],
    "risk.notional":    ["risk.tradeIn", "marketdata.mid"],
    "risk.exposure":    ["risk.notional", "liquidity.score"],
    "risk.var":         ["risk.rateIn", "risk.exposure", "marketdata.vol"],
    "capital.charge":   ["capital.configIn", "risk.exposure"],
    "capital.buffer":   ["capital.tradeIn", "capital.charge", "risk.var"],
}

def order_ok(seq):
    """Every parent that also ran in this event must have run earlier."""
    pos = {s: i for i, s in enumerate(seq)}
    for s in seq:
        for p in DEPS.get(s, []):
            if p in pos and pos[p] > pos[s]:
                return False, f"{s} ran before its parent {p}"
    return True, ""
