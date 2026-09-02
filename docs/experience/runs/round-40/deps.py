"""Round 40 dependency graph = round 39 plus pricing 2.0's additions.

pricing.rateIn and pricing.multIn are two log names emitted by the SAME node (PxRate), one per
handler; Spread reads that node, so either may appear as its parent depending on the event.
"""
DEPS = {
    "marketdata.tickIn": [], "marketdata.configIn": [], "liquidity.tickIn": [],
    "pricing.rateIn": [], "pricing.multIn": [], "risk.rateIn": [], "risk.tradeIn": [],
    "capital.tradeIn": [], "capital.configIn": [],
    "marketdata.mid":   ["marketdata.tickIn"],
    "marketdata.depth": ["marketdata.tickIn"],
    "marketdata.vol":   ["marketdata.configIn", "marketdata.mid"],
    "pricing.adjusted": ["marketdata.mid", "marketdata.depth"],
    "pricing.spread":   ["pricing.rateIn", "pricing.multIn", "pricing.adjusted"],
    "pricing.skew":     ["marketdata.vol", "pricing.adjusted"],
    "liquidity.book":   ["liquidity.tickIn", "marketdata.depth"],
    "liquidity.score":  ["pricing.adjusted", "liquidity.book"],
    "risk.notional":    ["risk.tradeIn", "marketdata.mid"],
    "risk.exposure":    ["risk.notional", "liquidity.score"],
    "risk.var":         ["risk.rateIn", "risk.exposure", "marketdata.vol"],
    "capital.charge":   ["capital.configIn", "risk.exposure"],
    "capital.buffer":   ["capital.tradeIn", "capital.charge", "risk.var"],
}

def order_ok(seq):
    pos = {s: i for i, s in enumerate(seq)}
    for s in seq:
        for p in DEPS.get(s, []):
            if p in pos and pos[p] > pos[s]:
                return False, f"{s} ran before its parent {p}"
    return True, ""
