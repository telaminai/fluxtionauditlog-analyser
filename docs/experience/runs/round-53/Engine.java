package com.acme;

import com.vendor.Capital;
import com.vendor.Events;
import com.vendor.FeeStrategies;
import com.vendor.FeeStrategy;
import com.vendor.Liquidity;
import com.vendor.MarketData;
import com.vendor.Pricing;
import com.vendor.Risk;

import java.util.Locale;
import java.util.function.Consumer;

/**
 * Assembles the five vendor components into one engine.
 *
 * <p>Nothing propagates between the components, so every reaction to an event is a call made here,
 * in dependency order: MarketData -> Pricing -> Liquidity -> Risk -> Capital. A component is called
 * for an event only if it consumes that event, and at most once, because its counters advance on
 * every call. Where an event moves an input that a downstream component reads but does not consume,
 * the downstream component is brought up to date with {@code refresh()}, which recomputes derived
 * figures without advancing a counter or publishing an alert.
 *
 * <p>Capital publishes breach alerts itself, to the sink installed here; this class never publishes
 * an alert of its own.
 */
public final class Engine {

    private final MarketData marketData = new MarketData();
    private final Pricing pricing = new Pricing(marketData);
    private final Liquidity liquidity = new Liquidity(marketData, pricing);
    private final Risk risk = new Risk(marketData, liquidity);
    private final Capital capital = new Capital(risk);

    public Engine(Consumer<String> alertConsumer) {
        capital.alertSink(alertConsumer::accept);
    }

    /**
     * Every component consumes Tick, so every component is called, once, in dependency order.
     * Nothing is left stale and no refresh is needed.
     */
    public void onTick(Events.Tick tick) {
        marketData.onTick(tick);
        pricing.onTick(tick);
        liquidity.onTick(tick);
        risk.onTick(tick);
        capital.onTick(tick);
    }

    /**
     * Only Risk and Capital consume Trade. MarketData, Pricing and Liquidity take no input from a
     * trade -- their figures are functions of the tick and of volFactor -- so they cannot go stale
     * here and must not be called, which would double-count nothing but would still be a second
     * call for one event.
     */
    public void onTrade(Events.Trade trade) {
        risk.onTrade(trade);
        capital.onTrade(trade);
    }

    /**
     * Pricing, Risk and Capital consume Rate. MarketData does not, and takes no input from a rate.
     * Liquidity does not consume Rate either: its figures move only with MarketData's, which a rate
     * does not touch, so it is fresh without being called (and it has no refresh() to call).
     */
    public void onRate(Events.Rate rate) {
        pricing.onRate(rate);
        risk.onRate(rate);
        capital.onRate(rate);
    }

    /**
     * The key is offered to both components that consume Config. One that does not own the key does
     * nothing and returns false, so an unowned key costs nothing anywhere -- no recompute, no audit
     * record, no alert, no counter.
     *
     * <p>MarketData owns volFactor and recomputes {@code vol} alone. Risk reads MarketData.vol but
     * does not consume Config, and Capital reads Risk.var, so both are stale the instant vol moves;
     * each is refreshed, in dependency order. Pricing and Liquidity read neither vol nor anything
     * derived from it, so they stay correct.
     *
     * <p>Capital owns chargePct and recomputes charge, buffer and fee itself; it is the last
     * component in the graph, so nothing downstream needs refreshing.
     */
    public void onConfig(Events.Config config) {
        boolean marketDataOwned = marketData.onConfig(config);
        capital.onConfig(config);
        if (marketDataOwned) {
            risk.refresh();
            capital.refresh();
        }
    }

    /**
     * Installs the operator's fee strategy, effective from the next recompute -- that is, from the
     * next event -- so nothing is recomputed here. An unrecognised name leaves the running strategy
     * in place rather than failing the run.
     */
    public void onStrategy(String name) {
        FeeStrategy strategy = strategyByName(name);
        if (strategy != null) {
            capital.feeStrategy(strategy);
        }
    }

    private static FeeStrategy strategyByName(String name) {
        if (name == null) {
            return null;
        }
        String trimmed = name.trim();
        try {
            return FeeStrategies.byName(trimmed);
        } catch (RuntimeException notThatName) {
            try {
                return FeeStrategies.byName(trimmed.toLowerCase(Locale.ROOT));
            } catch (RuntimeException notThatEither) {
                return null;
            }
        }
    }

    /** The reported breach count, as counted by Capital. */
    public int breachCount() {
        return capital.breaches();
    }

    /** The reported alert count, as counted by Capital. */
    public int alertCount() {
        return capital.alertCount();
    }

    /** The reported breach streak, as counted by Risk. */
    public int breachStreak() {
        return risk.streak();
    }
}
