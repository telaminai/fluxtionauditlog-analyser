"""M34.0 spike — the supermarket POC's cold-chain subsystem, rebuilt on LangGraph.

Same domain, same events, same derived quantities, different engine. The Fluxtion original is
gregv12/supermarket-poc: TemperatureReading -> FridgeMonitor, CompressorTelemetry ->
CompressorHealth, both feeding TemperatureForecast, which feeds MaintenanceScheduler.

The point is NOT to write good LangGraph. It is to produce a real foreign run whose trace can be
translated into the analyser's audit-log format, so we can see what the instrument keeps and what it
loses. The fan-out is deliberate: fridge_monitor and compressor_health are siblings in one super-step
and are therefore CONCURRENT, where Fluxtion's AOT compiler would have derived a total dispatch
order. That difference is the finding D-A1a predicted, and this is where it becomes observable.
"""
import operator
from typing import Annotated, TypedDict

from langgraph.graph import END, START, StateGraph

SAFE_MAX_C = 5.0          # FridgeMonitor.SAFE_MAX_C in the original
FAILURE_P_ALERT = 0.7     # CompressorHealth's alert threshold
FORECAST_HORIZON_MIN = 30


class Store(TypedDict):
    """The graph state. Each node writes only the channels it owns — the attribution D-A3 asks for
    comes free here, because LangGraph reports per-task writes rather than whole-state snapshots."""
    unit_id: str
    celsius: float
    vibration_rms: float
    current_amps: float
    at_millis: int

    over_temp: bool
    failure_p: float
    predicted_c: float
    will_breach: bool
    open_jobs: Annotated[list, operator.add]


def fridge_monitor(s: Store) -> dict:
    """Per-unit temperature against the safe maximum (equipment/FridgeMonitor.java)."""
    over = s["celsius"] > SAFE_MAX_C
    return {"over_temp": over}


def compressor_health(s: Store) -> dict:
    """Vibration + current -> failure probability (forecast/CompressorHealth.java). The original
    calls a registered model; a transparent stand-in keeps the run deterministic and the shape
    identical — what matters here is the trace, not the maths."""
    p = min(1.0, max(0.0, (s["vibration_rms"] - 2.0) / 6.0 + (s["current_amps"] - 8.0) / 20.0))
    return {"failure_p": round(p, 4)}


def temperature_forecast(s: Store) -> dict:
    """Where the unit will be in 30 minutes given how hard the compressor is working
    (forecast/TemperatureForecast.java). Joins BOTH upstream nodes — the reason they fan out."""
    drift = 0.6 * s["failure_p"] + (0.4 if s["over_temp"] else 0.0)
    predicted = round(s["celsius"] + drift * FORECAST_HORIZON_MIN / 10.0, 3)
    return {"predicted_c": predicted, "will_breach": predicted > SAFE_MAX_C}


def maintenance_scheduler(s: Store) -> dict:
    """Raises a job when the forecast or the compressor says so (equipment/MaintenanceScheduler)."""
    reason = ("forecast breach" if s["will_breach"]
              else "failure predicted" if s["failure_p"] >= FAILURE_P_ALERT
              else None)
    return {"open_jobs": [f"{s['unit_id']}:{reason}"]} if reason else {"open_jobs": []}


def needs_maintenance(s: Store) -> str:
    return "maintenance_scheduler" if (s["will_breach"] or s["failure_p"] >= FAILURE_P_ALERT) else END


def build():
    b = StateGraph(Store)
    b.add_node("fridge_monitor", fridge_monitor)
    b.add_node("compressor_health", compressor_health)
    b.add_node("temperature_forecast", temperature_forecast)
    b.add_node("maintenance_scheduler", maintenance_scheduler)

    # THE FAN-OUT: both run in super-step 1, concurrently, with no declared order between them.
    b.add_edge(START, "fridge_monitor")
    b.add_edge(START, "compressor_health")
    b.add_edge("fridge_monitor", "temperature_forecast")
    b.add_edge("compressor_health", "temperature_forecast")
    b.add_conditional_edges("temperature_forecast", needs_maintenance,
                            ["maintenance_scheduler", END])
    b.add_edge("maintenance_scheduler", END)
    return b.compile()


# ---- the day's readings: 24 chiller units, deterministic, a handful genuinely in trouble ---------

def readings(units: int = 24, cycles: int = 30, start_millis: int = 1767258000000):
    """One TemperatureReading + CompressorTelemetry per unit per cycle, 30s apart. Units 3, 11 and
    19 run hot and shake; the rest are healthy. Deterministic — no RNG, so the log is reproducible."""
    out = []
    for c in range(cycles):
        for u in range(units):
            unit = f"CHILL-{u + 1}"
            sick = u in (2, 10, 18)
            drift = (c / cycles) * (3.5 if sick else 0.4)
            out.append({
                "unit_id": unit,
                "celsius": round((4.1 if sick else 2.8) + drift, 3),
                "vibration_rms": round((3.0 if sick else 1.6) + drift * 1.4, 3),
                "current_amps": round((9.5 if sick else 7.8) + drift, 3),
                "at_millis": start_millis + (c * units + u) * 30_000 // units,
                "over_temp": False, "failure_p": 0.0, "predicted_c": 0.0,
                "will_breach": False, "open_jobs": [],
            })
    return out
