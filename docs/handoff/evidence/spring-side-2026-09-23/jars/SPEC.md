# Synthetic component contract, version 1

These are fictional, placeholder components and synthetic inputs, not a model recommendation.
The consuming application requires **one-day** risk:
`abs(exposure) * dailyVolatility * 2.0`. Volatility is a fraction, not a percentage.
Inputs must be finite, volatility nonnegative. Compare absolute error <= 0.000001.
Zero exposure yields zero. Do not annualise the result. Jar A intentionally follows
an incompatible supplier convention (multiply by sqrt(252)); it is built honestly
from that source. Jar B follows this consuming contract. Neither is tampered.

The limit check accepts a QuoteView interface. Size <= limit is accepted; size >
limit emits exactly one LimitBreach to the named sink. Unknown symbols and negative
sizes refuse. Node names are caller-supplied, globally unique. Notifier implements
NotificationService and publishes its text to the caller-supplied sink.

All node classes and wiring constructors are public. Nodes expose stable names,
getters and configuration setters. The runtime audit manager supplies their loggers.
