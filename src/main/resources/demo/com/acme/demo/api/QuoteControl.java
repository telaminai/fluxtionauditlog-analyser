package com.acme.demo.api;

/**
 * A control surface the processor exports: an operator or another service calls these, and each call
 * dispatches into the graph as an event.
 *
 * <p>Every method returns {@code void} — required. The generator wraps each exported method with audit
 * dispatch and discards returns, so a non-void method fails compilation. Reads come back via
 * {@code getNodeById} and a plain getter, not through here.
 */
public interface QuoteControl {

    /** Stop publishing quotes; the reason is audited. */
    void suspendQuoting(String reason);

    /** Resume publishing. */
    void resumeQuoting();
}
