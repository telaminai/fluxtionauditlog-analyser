/*
 * Copyright: © 2025.  Gregory Higgins <greg.higgins@v12technology.com> - All Rights Reserved
 * This source code is protected under international copyright law.  All rights
 * reserved and protected by the copyright holders.
 * This file is confidential and only available to authorized individuals with the
 * permission of the copyright holders.  If you encounter this file and do not have
 * permission, please contact the copyright holders and delete this file.
 */
package com.acme.generated;

import com.telamin.fluxtion.runtime.CloneableDataFlow;
import com.telamin.fluxtion.runtime.DataFlow;
import com.telamin.fluxtion.runtime.annotations.ExportService;
import com.telamin.fluxtion.runtime.annotations.OnEventHandler;
import com.telamin.fluxtion.runtime.audit.Auditor;
import com.telamin.fluxtion.runtime.audit.EventLogControlEvent;
import com.telamin.fluxtion.runtime.audit.EventLogControlEvent.LogLevel;
import com.telamin.fluxtion.runtime.audit.EventLogManager;
import com.telamin.fluxtion.runtime.audit.NodeNameAuditor;
import com.telamin.fluxtion.runtime.callback.CallbackDispatcherImpl;
import com.telamin.fluxtion.runtime.callback.ExportFunctionAuditEvent;
import com.telamin.fluxtion.runtime.callback.InternalEventProcessor;
import com.telamin.fluxtion.runtime.context.DataFlowContext;
import com.telamin.fluxtion.runtime.describe.DescriptorSupport;
import com.telamin.fluxtion.runtime.describe.ProcessorDescriptor;
import com.telamin.fluxtion.runtime.event.Event;
import com.telamin.fluxtion.runtime.input.EventFeed;
import com.telamin.fluxtion.runtime.input.SubscriptionManager;
import com.telamin.fluxtion.runtime.input.SubscriptionManagerNode;
import com.telamin.fluxtion.runtime.lifecycle.BatchHandler;
import com.telamin.fluxtion.runtime.lifecycle.Lifecycle;
import com.telamin.fluxtion.runtime.node.ForkedTriggerTask;
import com.telamin.fluxtion.runtime.node.MutableDataFlowContext;
import com.telamin.fluxtion.runtime.service.ServiceListener;
import com.telamin.fluxtion.runtime.service.ServiceRegistryNode;
import com.telamin.fluxtion.runtime.time.Clock;
import com.telamin.fluxtion.runtime.time.ClockStrategy.ClockStrategyEvent;
import com.vendor.Events.Config;
import com.vendor.Events.Rate;
import com.vendor.Events.Tick;
import com.vendor.Events.Trade;
import com.vendor.capital.BreachCount;
import com.vendor.capital.Buffer;
import com.vendor.capital.Capital;
import com.vendor.capital.Charge;
import com.vendor.capital.CpConfig;
import com.vendor.capital.CpTrade;
import com.vendor.capital.Fee;
import com.vendor.liquidity.Book;
import com.vendor.liquidity.Liquidity;
import com.vendor.liquidity.LqTick;
import com.vendor.liquidity.Score;
import com.vendor.marketdata.Depth;
import com.vendor.marketdata.MarketData;
import com.vendor.marketdata.MdConfig;
import com.vendor.marketdata.MdTick;
import com.vendor.marketdata.Mid;
import com.vendor.marketdata.Vol;
import com.vendor.pricing.Adjusted;
import com.vendor.pricing.Pricing;
import com.vendor.pricing.PxRate;
import com.vendor.pricing.Spread;
import com.vendor.risk.Exposure;
import com.vendor.risk.Notional;
import com.vendor.risk.Risk;
import com.vendor.risk.RkRate;
import com.vendor.risk.RkTrade;
import com.vendor.risk.Var;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 *
 *
 * <pre>
 * generation time           : Not available
 * api version               : unknown api version
 * analyser version          : unknown analyser version
 * target generator version  : unknown generator version
 * </pre>
 *
 * Event classes supported:
 *
 * <ul>
 *   <li>com.telamin.fluxtion.runtime.audit.EventLogControlEvent
 *   <li>com.telamin.fluxtion.runtime.time.ClockStrategy.ClockStrategyEvent
 *   <li>com.vendor.Events.Config
 *   <li>com.vendor.Events.Rate
 *   <li>com.vendor.Events.Tick
 *   <li>com.vendor.Events.Trade
 * </ul>
 *
 * @author Greg Higgins
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public class AppProcessor
    implements CloneableDataFlow<AppProcessor>,
        /*--- @ExportService start ---*/
        @ExportService ServiceListener,
        /*--- @ExportService end ---*/
        DataFlow,
        InternalEventProcessor,
        BatchHandler {

  //Node declarations
  private final transient CallbackDispatcherImpl callbackDispatcher = new CallbackDispatcherImpl();
  public final transient Clock clock = new Clock();
  private final transient CpConfig cpConfig_22 = new CpConfig();
  private final transient CpTrade cpTrade_21 = new CpTrade();
  public final transient EventLogManager eventLogger = new EventLogManager();
  private final transient LqTick lqTick_13 = new LqTick();
  private final transient MdConfig mdConfig_6 = new MdConfig();
  private final transient MdTick mdTick_5 = new MdTick();
  private final transient Depth depth_8 = new com.vendor.marketdata.Depth(mdTick_5);;
  private final transient Book book_14 = new com.vendor.liquidity.Book(lqTick_13, depth_8);;
  private final transient Mid mid_7 = new com.vendor.marketdata.Mid(mdTick_5);;
  private final transient Adjusted adjusted_11 = new com.vendor.pricing.Adjusted(mid_7, depth_8);;
  public final transient NodeNameAuditor nodeNameLookup = new NodeNameAuditor();
  private final transient PxRate pxRate_10 = new PxRate();
  private final transient RkRate rkRate_17 = new RkRate();
  private final transient RkTrade rkTrade_16 = new RkTrade();
  private final transient Notional notional_18 = new com.vendor.risk.Notional(rkTrade_16, mid_7);;
  private final transient Score score_15 = new com.vendor.liquidity.Score(adjusted_11, book_14);;
  private final transient Exposure exposure_19 =
      new com.vendor.risk.Exposure(notional_18, score_15);;
  private final transient BreachCount breachCount_26 =
      new com.vendor.capital.BreachCount(exposure_19);;
  private final transient Charge charge_23 =
      new com.vendor.capital.Charge(cpConfig_22, exposure_19);;
  private final transient Fee fee_25 = new com.vendor.capital.Fee(exposure_19);;
  public final transient Liquidity liquidity =
      new com.vendor.liquidity.Liquidity(lqTick_13, book_14, score_15);;
  private final transient Spread spread_12 = new com.vendor.pricing.Spread(pxRate_10, adjusted_11);;
  public final transient Pricing pricing =
      new com.vendor.pricing.Pricing(pxRate_10, adjusted_11, spread_12);;
  private final transient SubscriptionManagerNode subscriptionManager =
      new SubscriptionManagerNode();
  private final transient MutableDataFlowContext context =
      new com.telamin.fluxtion.runtime.node.MutableDataFlowContext(
          nodeNameLookup, callbackDispatcher, subscriptionManager, callbackDispatcher);;
  public final transient ServiceRegistryNode serviceRegistry = new ServiceRegistryNode();
  private final transient Vol vol_9 = new com.vendor.marketdata.Vol(mdConfig_6, mid_7);;
  public final transient MarketData marketdata =
      new com.vendor.marketdata.MarketData(mdTick_5, mdConfig_6, mid_7, depth_8, vol_9);;
  private final transient Var var_20 = new com.vendor.risk.Var(rkRate_17, exposure_19, vol_9);;
  private final transient Buffer buffer_24 =
      new com.vendor.capital.Buffer(cpTrade_21, charge_23, var_20);;
  public final transient Capital capital =
      new com.vendor.capital.Capital(
          cpTrade_21, cpConfig_22, charge_23, buffer_24, fee_25, breachCount_26);;
  public final transient Risk risk =
      new com.vendor.risk.Risk(rkTrade_16, rkRate_17, notional_18, exposure_19, var_20);;
  private final transient ExportFunctionAuditEvent functionAudit = new ExportFunctionAuditEvent();
  //Dirty flags
  private boolean initCalled = false;
  private boolean processing = false;
  private boolean buffering = false;
  private final transient IdentityHashMap<Object, BooleanSupplier> dirtyFlagSupplierMap =
      new IdentityHashMap<>(22);
  private final transient IdentityHashMap<Object, Consumer<Boolean>> dirtyFlagUpdateMap =
      new IdentityHashMap<>(22);

  private boolean isDirty_adjusted_11 = false;
  private boolean isDirty_book_14 = false;
  private boolean isDirty_breachCount_26 = false;
  private boolean isDirty_buffer_24 = false;
  private boolean isDirty_charge_23 = false;
  private boolean isDirty_cpConfig_22 = false;
  private boolean isDirty_cpTrade_21 = false;
  private boolean isDirty_depth_8 = false;
  private boolean isDirty_exposure_19 = false;
  private boolean isDirty_fee_25 = false;
  private boolean isDirty_lqTick_13 = false;
  private boolean isDirty_mdConfig_6 = false;
  private boolean isDirty_mdTick_5 = false;
  private boolean isDirty_mid_7 = false;
  private boolean isDirty_notional_18 = false;
  private boolean isDirty_pxRate_10 = false;
  private boolean isDirty_rkRate_17 = false;
  private boolean isDirty_rkTrade_16 = false;
  private boolean isDirty_score_15 = false;
  private boolean isDirty_spread_12 = false;
  private boolean isDirty_var_20 = false;
  private boolean isDirty_vol_9 = false;

  //Forked declarations

  //Filter constants

  //Self-description of the embeddable surface — see ProcessorDescriptor
  private static final ProcessorDescriptor DESCRIPTOR =
      DescriptorSupport.of(
          AppProcessor.class,
          AppProcessor::new,
          new ProcessorDescriptor.Input[] {
            new ProcessorDescriptor.Input("Config", "com.vendor.Events.Config", false),
            new ProcessorDescriptor.Input("Rate", "com.vendor.Events.Rate", false),
            new ProcessorDescriptor.Input("Tick", "com.vendor.Events.Tick", false),
            new ProcessorDescriptor.Input("Trade", "com.vendor.Events.Trade", false)
          },
          new ProcessorDescriptor.Sink[] {},
          new ProcessorDescriptor.Service[] {},
          new DescriptorSupport.Meta(
              null,
              null,
              "4798ab83c188d1c3430a490ccc823474ced3eb7a77c1e837877831bc42c8289b",
              null));

  @Override
  public ProcessorDescriptor getDescriptor() {
    return DESCRIPTOR;
  }

  //unknown event handler
  private Consumer unKnownEventHandler = (e) -> {};

  public AppProcessor(Map<Object, Object> contextMap) {
    if (context != null) {
      context.replaceMappings(contextMap);
    }
    eventLogger.setClearAfterPublish(false);
    eventLogger.trace = true;
    eventLogger.printEventToString = true;
    eventLogger.printThreadName = true;
    eventLogger.traceLevel = LogLevel.INFO;
    eventLogger.clock = clock;
    context.setClock(clock);
    serviceRegistry.setDataFlowContext(context);
    breachCount_26.breaches = 0;
    breachCount_26.limit = 10000.0;
    //node auditors
    initialiseAuditor(clock);
    initialiseAuditor(eventLogger);
    initialiseAuditor(nodeNameLookup);
    initialiseAuditor(serviceRegistry);
    if (subscriptionManager != null) {
      subscriptionManager.setSubscribingEventProcessor(this);
    }
    if (context != null) {
      context.setEventProcessorCallback(this);
    }
  }

  public AppProcessor() {
    this(null);
  }

  @Override
  public void init() {
    initCalled = true;
    auditEvent(Lifecycle.LifecycleEvent.Init);
    //initialise dirty lookup map
    isDirty("test");
    clock.init();
    afterEvent();
  }

  @Override
  public void start() {
    if (!initCalled) {
      throw new RuntimeException("init() must be called before start()");
    }
    processing = true;
    auditEvent(Lifecycle.LifecycleEvent.Start);

    afterEvent();
    callbackDispatcher.dispatchQueuedCallbacks();
    processing = false;
  }

  @Override
  public void startComplete() {
    if (!initCalled) {
      throw new RuntimeException("init() must be called before startComplete()");
    }
    processing = true;
    auditEvent(Lifecycle.LifecycleEvent.StartComplete);

    afterEvent();
    callbackDispatcher.dispatchQueuedCallbacks();
    processing = false;
  }

  @Override
  public void stop() {
    if (!initCalled) {
      throw new RuntimeException("init() must be called before stop()");
    }
    processing = true;
    auditEvent(Lifecycle.LifecycleEvent.Stop);

    afterEvent();
    callbackDispatcher.dispatchQueuedCallbacks();
    processing = false;
  }

  @Override
  public void tearDown() {
    initCalled = false;
    auditEvent(Lifecycle.LifecycleEvent.TearDown);
    serviceRegistry.tearDown();
    nodeNameLookup.tearDown();
    eventLogger.tearDown();
    clock.tearDown();
    subscriptionManager.tearDown();
    afterEvent();
  }

  @Override
  public void setContextParameterMap(Map<Object, Object> newContextMapping) {
    context.replaceMappings(newContextMapping);
  }

  @Override
  public void addContextParameter(Object key, Object value) {
    context.addMapping(key, value);
  }

  //EVENT DISPATCH - START
  @Override
  public void onEvent(Object event) {
    processEvent(event);
  }

  private void processEvent(Object event) {
    if (buffering) {
      triggerCalculation();
    }
    if (processing) {
      callbackDispatcher.queueReentrantEvent(event);
    } else {
      processing = true;
      onEventInternal(event);
      callbackDispatcher.dispatchQueuedCallbacks();
      processing = false;
    }
  }

  @Override
  public void onEventInternal(Object event) {
    if (event instanceof EventLogControlEvent) {
      EventLogControlEvent typedEvent = (EventLogControlEvent) event;
      handleEvent(typedEvent);
    } else if (event instanceof ClockStrategyEvent) {
      ClockStrategyEvent typedEvent = (ClockStrategyEvent) event;
      handleEvent(typedEvent);
    } else if (event instanceof Config) {
      Config typedEvent = (Config) event;
      handleEvent(typedEvent);
    } else if (event instanceof Rate) {
      Rate typedEvent = (Rate) event;
      handleEvent(typedEvent);
    } else if (event instanceof Tick) {
      Tick typedEvent = (Tick) event;
      handleEvent(typedEvent);
    } else if (event instanceof Trade) {
      Trade typedEvent = (Trade) event;
      handleEvent(typedEvent);
    } else {
      unKnownEventHandler(event);
    }
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(EventLogControlEvent event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(ClockStrategyEvent event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(Config event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(Rate event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(Tick event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(Trade event) {
    processEvent(event);
  }

  public void handleEvent(EventLogControlEvent typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(eventLogger, "eventLogger", "calculationLogConfig", typedEvent);
    eventLogger.calculationLogConfig(typedEvent);
    afterEvent();
  }

  public void handleEvent(ClockStrategyEvent typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(clock, "clock", "setClockStrategy", typedEvent);
    clock.setClockStrategy(typedEvent);
    afterEvent();
  }

  public void handleEvent(Config typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(cpConfig_22, "cpConfig_22", "onConfig", typedEvent);
    isDirty_cpConfig_22 = cpConfig_22.onConfig(typedEvent);
    auditInvocation(mdConfig_6, "mdConfig_6", "onConfig", typedEvent);
    isDirty_mdConfig_6 = mdConfig_6.onConfig(typedEvent);
    if (guardCheck_charge_23()) {
      auditInvocation(charge_23, "charge_23", "calc", typedEvent);
      isDirty_charge_23 = charge_23.calc();
    }
    if (guardCheck_vol_9()) {
      auditInvocation(vol_9, "vol_9", "calc", typedEvent);
      isDirty_vol_9 = vol_9.calc();
    }
    if (guardCheck_var_20()) {
      auditInvocation(var_20, "var_20", "calc", typedEvent);
      isDirty_var_20 = var_20.calc();
    }
    if (guardCheck_buffer_24()) {
      auditInvocation(buffer_24, "buffer_24", "calc", typedEvent);
      isDirty_buffer_24 = buffer_24.calc();
    }
    afterEvent();
  }

  public void handleEvent(Rate typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(pxRate_10, "pxRate_10", "onRate", typedEvent);
    isDirty_pxRate_10 = pxRate_10.onRate(typedEvent);
    auditInvocation(rkRate_17, "rkRate_17", "onRate", typedEvent);
    isDirty_rkRate_17 = rkRate_17.onRate(typedEvent);
    if (guardCheck_spread_12()) {
      auditInvocation(spread_12, "spread_12", "calc", typedEvent);
      isDirty_spread_12 = spread_12.calc();
    }
    if (guardCheck_var_20()) {
      auditInvocation(var_20, "var_20", "calc", typedEvent);
      isDirty_var_20 = var_20.calc();
    }
    if (guardCheck_buffer_24()) {
      auditInvocation(buffer_24, "buffer_24", "calc", typedEvent);
      isDirty_buffer_24 = buffer_24.calc();
    }
    afterEvent();
  }

  public void handleEvent(Tick typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(lqTick_13, "lqTick_13", "onTick", typedEvent);
    isDirty_lqTick_13 = lqTick_13.onTick(typedEvent);
    auditInvocation(mdTick_5, "mdTick_5", "onTick", typedEvent);
    isDirty_mdTick_5 = mdTick_5.onTick(typedEvent);
    if (guardCheck_depth_8()) {
      auditInvocation(depth_8, "depth_8", "calc", typedEvent);
      isDirty_depth_8 = depth_8.calc();
    }
    if (guardCheck_book_14()) {
      auditInvocation(book_14, "book_14", "calc", typedEvent);
      isDirty_book_14 = book_14.calc();
    }
    if (guardCheck_mid_7()) {
      auditInvocation(mid_7, "mid_7", "calc", typedEvent);
      isDirty_mid_7 = mid_7.calc();
    }
    if (guardCheck_adjusted_11()) {
      auditInvocation(adjusted_11, "adjusted_11", "calc", typedEvent);
      isDirty_adjusted_11 = adjusted_11.calc();
    }
    if (guardCheck_notional_18()) {
      auditInvocation(notional_18, "notional_18", "calc", typedEvent);
      isDirty_notional_18 = notional_18.calc();
    }
    if (guardCheck_score_15()) {
      auditInvocation(score_15, "score_15", "calc", typedEvent);
      isDirty_score_15 = score_15.calc();
    }
    if (guardCheck_exposure_19()) {
      auditInvocation(exposure_19, "exposure_19", "calc", typedEvent);
      isDirty_exposure_19 = exposure_19.calc();
    }
    if (guardCheck_breachCount_26()) {
      auditInvocation(breachCount_26, "breachCount_26", "calc", typedEvent);
      isDirty_breachCount_26 = breachCount_26.calc();
    }
    if (guardCheck_charge_23()) {
      auditInvocation(charge_23, "charge_23", "calc", typedEvent);
      isDirty_charge_23 = charge_23.calc();
    }
    if (guardCheck_fee_25()) {
      auditInvocation(fee_25, "fee_25", "calc", typedEvent);
      isDirty_fee_25 = fee_25.calc();
    }
    if (guardCheck_spread_12()) {
      auditInvocation(spread_12, "spread_12", "calc", typedEvent);
      isDirty_spread_12 = spread_12.calc();
    }
    if (guardCheck_vol_9()) {
      auditInvocation(vol_9, "vol_9", "calc", typedEvent);
      isDirty_vol_9 = vol_9.calc();
    }
    if (guardCheck_var_20()) {
      auditInvocation(var_20, "var_20", "calc", typedEvent);
      isDirty_var_20 = var_20.calc();
    }
    if (guardCheck_buffer_24()) {
      auditInvocation(buffer_24, "buffer_24", "calc", typedEvent);
      isDirty_buffer_24 = buffer_24.calc();
    }
    afterEvent();
  }

  public void handleEvent(Trade typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(cpTrade_21, "cpTrade_21", "onTrade", typedEvent);
    isDirty_cpTrade_21 = cpTrade_21.onTrade(typedEvent);
    auditInvocation(rkTrade_16, "rkTrade_16", "onTrade", typedEvent);
    isDirty_rkTrade_16 = rkTrade_16.onTrade(typedEvent);
    if (guardCheck_notional_18()) {
      auditInvocation(notional_18, "notional_18", "calc", typedEvent);
      isDirty_notional_18 = notional_18.calc();
    }
    if (guardCheck_exposure_19()) {
      auditInvocation(exposure_19, "exposure_19", "calc", typedEvent);
      isDirty_exposure_19 = exposure_19.calc();
    }
    if (guardCheck_breachCount_26()) {
      auditInvocation(breachCount_26, "breachCount_26", "calc", typedEvent);
      isDirty_breachCount_26 = breachCount_26.calc();
    }
    if (guardCheck_charge_23()) {
      auditInvocation(charge_23, "charge_23", "calc", typedEvent);
      isDirty_charge_23 = charge_23.calc();
    }
    if (guardCheck_fee_25()) {
      auditInvocation(fee_25, "fee_25", "calc", typedEvent);
      isDirty_fee_25 = fee_25.calc();
    }
    if (guardCheck_var_20()) {
      auditInvocation(var_20, "var_20", "calc", typedEvent);
      isDirty_var_20 = var_20.calc();
    }
    if (guardCheck_buffer_24()) {
      auditInvocation(buffer_24, "buffer_24", "calc", typedEvent);
      isDirty_buffer_24 = buffer_24.calc();
    }
    afterEvent();
  }
  //EVENT DISPATCH - END

  //EXPORTED SERVICE FUNCTIONS - START
  @Override
  public void deRegisterService(com.telamin.fluxtion.runtime.service.Service<?> arg0) {
    beforeServiceCall(
        "@Override\npublic void deRegisterService(com.telamin.fluxtion.runtime.service.Service<?> arg0)");
    ExportFunctionAuditEvent typedEvent = functionAudit;
    auditInvocation(serviceRegistry, "serviceRegistry", "deRegisterService", typedEvent);
    serviceRegistry.deRegisterService(arg0);
    afterServiceCall();
  }

  @Override
  public void registerService(com.telamin.fluxtion.runtime.service.Service<?> arg0) {
    beforeServiceCall(
        "@Override\npublic void registerService(com.telamin.fluxtion.runtime.service.Service<?> arg0)");
    ExportFunctionAuditEvent typedEvent = functionAudit;
    auditInvocation(serviceRegistry, "serviceRegistry", "registerService", typedEvent);
    serviceRegistry.registerService(arg0);
    afterServiceCall();
  }
  //EXPORTED SERVICE FUNCTIONS - END

  //EVENT BUFFERING - START
  public void bufferEvent(Object event) {
    buffering = true;
    if (event instanceof EventLogControlEvent) {
      EventLogControlEvent typedEvent = (EventLogControlEvent) event;
      auditEvent(typedEvent);
      auditInvocation(eventLogger, "eventLogger", "calculationLogConfig", typedEvent);
      eventLogger.calculationLogConfig(typedEvent);
    } else if (event instanceof ClockStrategyEvent) {
      ClockStrategyEvent typedEvent = (ClockStrategyEvent) event;
      auditEvent(typedEvent);
      auditInvocation(clock, "clock", "setClockStrategy", typedEvent);
      clock.setClockStrategy(typedEvent);
    } else if (event instanceof Config) {
      Config typedEvent = (Config) event;
      auditEvent(typedEvent);
      auditInvocation(cpConfig_22, "cpConfig_22", "onConfig", typedEvent);
      isDirty_cpConfig_22 = cpConfig_22.onConfig(typedEvent);
      auditInvocation(mdConfig_6, "mdConfig_6", "onConfig", typedEvent);
      isDirty_mdConfig_6 = mdConfig_6.onConfig(typedEvent);
    } else if (event instanceof Rate) {
      Rate typedEvent = (Rate) event;
      auditEvent(typedEvent);
      auditInvocation(pxRate_10, "pxRate_10", "onRate", typedEvent);
      isDirty_pxRate_10 = pxRate_10.onRate(typedEvent);
      auditInvocation(rkRate_17, "rkRate_17", "onRate", typedEvent);
      isDirty_rkRate_17 = rkRate_17.onRate(typedEvent);
    } else if (event instanceof Tick) {
      Tick typedEvent = (Tick) event;
      auditEvent(typedEvent);
      auditInvocation(lqTick_13, "lqTick_13", "onTick", typedEvent);
      isDirty_lqTick_13 = lqTick_13.onTick(typedEvent);
      auditInvocation(mdTick_5, "mdTick_5", "onTick", typedEvent);
      isDirty_mdTick_5 = mdTick_5.onTick(typedEvent);
    } else if (event instanceof Trade) {
      Trade typedEvent = (Trade) event;
      auditEvent(typedEvent);
      auditInvocation(cpTrade_21, "cpTrade_21", "onTrade", typedEvent);
      isDirty_cpTrade_21 = cpTrade_21.onTrade(typedEvent);
      auditInvocation(rkTrade_16, "rkTrade_16", "onTrade", typedEvent);
      isDirty_rkTrade_16 = rkTrade_16.onTrade(typedEvent);
    }
  }

  public void triggerCalculation() {
    buffering = false;
    String typedEvent = "No event information - buffered dispatch";
    if (guardCheck_depth_8()) {
      auditInvocation(depth_8, "depth_8", "calc", typedEvent);
      isDirty_depth_8 = depth_8.calc();
    }
    if (guardCheck_book_14()) {
      auditInvocation(book_14, "book_14", "calc", typedEvent);
      isDirty_book_14 = book_14.calc();
    }
    if (guardCheck_mid_7()) {
      auditInvocation(mid_7, "mid_7", "calc", typedEvent);
      isDirty_mid_7 = mid_7.calc();
    }
    if (guardCheck_adjusted_11()) {
      auditInvocation(adjusted_11, "adjusted_11", "calc", typedEvent);
      isDirty_adjusted_11 = adjusted_11.calc();
    }
    if (guardCheck_notional_18()) {
      auditInvocation(notional_18, "notional_18", "calc", typedEvent);
      isDirty_notional_18 = notional_18.calc();
    }
    if (guardCheck_score_15()) {
      auditInvocation(score_15, "score_15", "calc", typedEvent);
      isDirty_score_15 = score_15.calc();
    }
    if (guardCheck_exposure_19()) {
      auditInvocation(exposure_19, "exposure_19", "calc", typedEvent);
      isDirty_exposure_19 = exposure_19.calc();
    }
    if (guardCheck_breachCount_26()) {
      auditInvocation(breachCount_26, "breachCount_26", "calc", typedEvent);
      isDirty_breachCount_26 = breachCount_26.calc();
    }
    if (guardCheck_charge_23()) {
      auditInvocation(charge_23, "charge_23", "calc", typedEvent);
      isDirty_charge_23 = charge_23.calc();
    }
    if (guardCheck_fee_25()) {
      auditInvocation(fee_25, "fee_25", "calc", typedEvent);
      isDirty_fee_25 = fee_25.calc();
    }
    if (guardCheck_spread_12()) {
      auditInvocation(spread_12, "spread_12", "calc", typedEvent);
      isDirty_spread_12 = spread_12.calc();
    }
    if (guardCheck_vol_9()) {
      auditInvocation(vol_9, "vol_9", "calc", typedEvent);
      isDirty_vol_9 = vol_9.calc();
    }
    if (guardCheck_var_20()) {
      auditInvocation(var_20, "var_20", "calc", typedEvent);
      isDirty_var_20 = var_20.calc();
    }
    if (guardCheck_buffer_24()) {
      auditInvocation(buffer_24, "buffer_24", "calc", typedEvent);
      isDirty_buffer_24 = buffer_24.calc();
    }
    afterEvent();
  }
  //EVENT BUFFERING - END

  private void auditEvent(Object typedEvent) {
    clock.eventReceived(typedEvent);
    eventLogger.eventReceived(typedEvent);
    nodeNameLookup.eventReceived(typedEvent);
    serviceRegistry.eventReceived(typedEvent);
  }

  private void auditEvent(Event typedEvent) {
    clock.eventReceived(typedEvent);
    eventLogger.eventReceived(typedEvent);
    nodeNameLookup.eventReceived(typedEvent);
    serviceRegistry.eventReceived(typedEvent);
  }

  private void auditInvocation(Object node, String nodeName, String methodName, Object typedEvent) {
    eventLogger.nodeInvoked(node, nodeName, methodName, typedEvent);
  }

  private void initialiseAuditor(Auditor auditor) {
    auditor.init();
    auditor.nodeRegistered(callbackDispatcher, "callbackDispatcher");
    auditor.nodeRegistered(subscriptionManager, "subscriptionManager");
    auditor.nodeRegistered(context, "context");
    auditor.nodeRegistered(breachCount_26, "breachCount_26");
    auditor.nodeRegistered(buffer_24, "buffer_24");
    auditor.nodeRegistered(capital, "capital");
    auditor.nodeRegistered(charge_23, "charge_23");
    auditor.nodeRegistered(cpConfig_22, "cpConfig_22");
    auditor.nodeRegistered(cpTrade_21, "cpTrade_21");
    auditor.nodeRegistered(fee_25, "fee_25");
    auditor.nodeRegistered(book_14, "book_14");
    auditor.nodeRegistered(liquidity, "liquidity");
    auditor.nodeRegistered(lqTick_13, "lqTick_13");
    auditor.nodeRegistered(score_15, "score_15");
    auditor.nodeRegistered(depth_8, "depth_8");
    auditor.nodeRegistered(marketdata, "marketdata");
    auditor.nodeRegistered(mdConfig_6, "mdConfig_6");
    auditor.nodeRegistered(mdTick_5, "mdTick_5");
    auditor.nodeRegistered(mid_7, "mid_7");
    auditor.nodeRegistered(vol_9, "vol_9");
    auditor.nodeRegistered(adjusted_11, "adjusted_11");
    auditor.nodeRegistered(pricing, "pricing");
    auditor.nodeRegistered(pxRate_10, "pxRate_10");
    auditor.nodeRegistered(spread_12, "spread_12");
    auditor.nodeRegistered(exposure_19, "exposure_19");
    auditor.nodeRegistered(notional_18, "notional_18");
    auditor.nodeRegistered(risk, "risk");
    auditor.nodeRegistered(rkRate_17, "rkRate_17");
    auditor.nodeRegistered(rkTrade_16, "rkTrade_16");
    auditor.nodeRegistered(var_20, "var_20");
  }

  private void beforeServiceCall(String functionDescription) {
    functionAudit.setFunctionDescription(functionDescription);
    auditEvent(functionAudit);
    if (buffering) {
      triggerCalculation();
    }
    processing = true;
  }

  private void afterServiceCall() {
    afterEvent();
    callbackDispatcher.dispatchQueuedCallbacks();
    processing = false;
  }

  private void afterEvent() {
    clock.processingComplete();
    eventLogger.processingComplete();
    nodeNameLookup.processingComplete();
    serviceRegistry.processingComplete();
    isDirty_adjusted_11 = false;
    isDirty_book_14 = false;
    isDirty_breachCount_26 = false;
    isDirty_buffer_24 = false;
    isDirty_charge_23 = false;
    isDirty_cpConfig_22 = false;
    isDirty_cpTrade_21 = false;
    isDirty_depth_8 = false;
    isDirty_exposure_19 = false;
    isDirty_fee_25 = false;
    isDirty_lqTick_13 = false;
    isDirty_mdConfig_6 = false;
    isDirty_mdTick_5 = false;
    isDirty_mid_7 = false;
    isDirty_notional_18 = false;
    isDirty_pxRate_10 = false;
    isDirty_rkRate_17 = false;
    isDirty_rkTrade_16 = false;
    isDirty_score_15 = false;
    isDirty_spread_12 = false;
    isDirty_var_20 = false;
    isDirty_vol_9 = false;
  }

  @Override
  public void batchPause() {
    auditEvent(Lifecycle.LifecycleEvent.BatchPause);
    processing = true;

    afterEvent();
    callbackDispatcher.dispatchQueuedCallbacks();
    processing = false;
  }

  @Override
  public void batchEnd() {
    auditEvent(Lifecycle.LifecycleEvent.BatchEnd);
    processing = true;

    afterEvent();
    callbackDispatcher.dispatchQueuedCallbacks();
    processing = false;
  }

  @Override
  public boolean isDirty(Object node) {
    return dirtySupplier(node).getAsBoolean();
  }

  @Override
  public BooleanSupplier dirtySupplier(Object node) {
    if (dirtyFlagSupplierMap.isEmpty()) {
      dirtyFlagSupplierMap.put(adjusted_11, () -> isDirty_adjusted_11);
      dirtyFlagSupplierMap.put(book_14, () -> isDirty_book_14);
      dirtyFlagSupplierMap.put(breachCount_26, () -> isDirty_breachCount_26);
      dirtyFlagSupplierMap.put(buffer_24, () -> isDirty_buffer_24);
      dirtyFlagSupplierMap.put(charge_23, () -> isDirty_charge_23);
      dirtyFlagSupplierMap.put(cpConfig_22, () -> isDirty_cpConfig_22);
      dirtyFlagSupplierMap.put(cpTrade_21, () -> isDirty_cpTrade_21);
      dirtyFlagSupplierMap.put(depth_8, () -> isDirty_depth_8);
      dirtyFlagSupplierMap.put(exposure_19, () -> isDirty_exposure_19);
      dirtyFlagSupplierMap.put(fee_25, () -> isDirty_fee_25);
      dirtyFlagSupplierMap.put(lqTick_13, () -> isDirty_lqTick_13);
      dirtyFlagSupplierMap.put(mdConfig_6, () -> isDirty_mdConfig_6);
      dirtyFlagSupplierMap.put(mdTick_5, () -> isDirty_mdTick_5);
      dirtyFlagSupplierMap.put(mid_7, () -> isDirty_mid_7);
      dirtyFlagSupplierMap.put(notional_18, () -> isDirty_notional_18);
      dirtyFlagSupplierMap.put(pxRate_10, () -> isDirty_pxRate_10);
      dirtyFlagSupplierMap.put(rkRate_17, () -> isDirty_rkRate_17);
      dirtyFlagSupplierMap.put(rkTrade_16, () -> isDirty_rkTrade_16);
      dirtyFlagSupplierMap.put(score_15, () -> isDirty_score_15);
      dirtyFlagSupplierMap.put(spread_12, () -> isDirty_spread_12);
      dirtyFlagSupplierMap.put(var_20, () -> isDirty_var_20);
      dirtyFlagSupplierMap.put(vol_9, () -> isDirty_vol_9);
    }
    return dirtyFlagSupplierMap.getOrDefault(node, DataFlow.ALWAYS_FALSE);
  }

  @Override
  public void setDirty(Object node, boolean dirtyFlag) {
    if (dirtyFlagUpdateMap.isEmpty()) {
      dirtyFlagUpdateMap.put(adjusted_11, (b) -> isDirty_adjusted_11 = b);
      dirtyFlagUpdateMap.put(book_14, (b) -> isDirty_book_14 = b);
      dirtyFlagUpdateMap.put(breachCount_26, (b) -> isDirty_breachCount_26 = b);
      dirtyFlagUpdateMap.put(buffer_24, (b) -> isDirty_buffer_24 = b);
      dirtyFlagUpdateMap.put(charge_23, (b) -> isDirty_charge_23 = b);
      dirtyFlagUpdateMap.put(cpConfig_22, (b) -> isDirty_cpConfig_22 = b);
      dirtyFlagUpdateMap.put(cpTrade_21, (b) -> isDirty_cpTrade_21 = b);
      dirtyFlagUpdateMap.put(depth_8, (b) -> isDirty_depth_8 = b);
      dirtyFlagUpdateMap.put(exposure_19, (b) -> isDirty_exposure_19 = b);
      dirtyFlagUpdateMap.put(fee_25, (b) -> isDirty_fee_25 = b);
      dirtyFlagUpdateMap.put(lqTick_13, (b) -> isDirty_lqTick_13 = b);
      dirtyFlagUpdateMap.put(mdConfig_6, (b) -> isDirty_mdConfig_6 = b);
      dirtyFlagUpdateMap.put(mdTick_5, (b) -> isDirty_mdTick_5 = b);
      dirtyFlagUpdateMap.put(mid_7, (b) -> isDirty_mid_7 = b);
      dirtyFlagUpdateMap.put(notional_18, (b) -> isDirty_notional_18 = b);
      dirtyFlagUpdateMap.put(pxRate_10, (b) -> isDirty_pxRate_10 = b);
      dirtyFlagUpdateMap.put(rkRate_17, (b) -> isDirty_rkRate_17 = b);
      dirtyFlagUpdateMap.put(rkTrade_16, (b) -> isDirty_rkTrade_16 = b);
      dirtyFlagUpdateMap.put(score_15, (b) -> isDirty_score_15 = b);
      dirtyFlagUpdateMap.put(spread_12, (b) -> isDirty_spread_12 = b);
      dirtyFlagUpdateMap.put(var_20, (b) -> isDirty_var_20 = b);
      dirtyFlagUpdateMap.put(vol_9, (b) -> isDirty_vol_9 = b);
    }
    dirtyFlagUpdateMap.get(node).accept(dirtyFlag);
  }

  private boolean guardCheck_breachCount_26() {
    return isDirty_exposure_19;
  }

  private boolean guardCheck_buffer_24() {
    return isDirty_charge_23 | isDirty_cpTrade_21 | isDirty_var_20;
  }

  private boolean guardCheck_capital() {
    return isDirty_breachCount_26
        | isDirty_buffer_24
        | isDirty_charge_23
        | isDirty_cpConfig_22
        | isDirty_cpTrade_21
        | isDirty_fee_25;
  }

  private boolean guardCheck_charge_23() {
    return isDirty_cpConfig_22 | isDirty_exposure_19;
  }

  private boolean guardCheck_fee_25() {
    return isDirty_exposure_19;
  }

  private boolean guardCheck_book_14() {
    return isDirty_depth_8 | isDirty_lqTick_13;
  }

  private boolean guardCheck_liquidity() {
    return isDirty_book_14 | isDirty_lqTick_13 | isDirty_score_15;
  }

  private boolean guardCheck_score_15() {
    return isDirty_adjusted_11 | isDirty_book_14;
  }

  private boolean guardCheck_depth_8() {
    return isDirty_mdTick_5;
  }

  private boolean guardCheck_marketdata() {
    return isDirty_depth_8 | isDirty_mdConfig_6 | isDirty_mdTick_5 | isDirty_mid_7 | isDirty_vol_9;
  }

  private boolean guardCheck_mid_7() {
    return isDirty_mdTick_5;
  }

  private boolean guardCheck_vol_9() {
    return isDirty_mdConfig_6 | isDirty_mid_7;
  }

  private boolean guardCheck_adjusted_11() {
    return isDirty_depth_8 | isDirty_mid_7;
  }

  private boolean guardCheck_pricing() {
    return isDirty_adjusted_11 | isDirty_pxRate_10 | isDirty_spread_12;
  }

  private boolean guardCheck_spread_12() {
    return isDirty_adjusted_11 | isDirty_pxRate_10;
  }

  private boolean guardCheck_exposure_19() {
    return isDirty_notional_18 | isDirty_score_15;
  }

  private boolean guardCheck_notional_18() {
    return isDirty_mid_7 | isDirty_rkTrade_16;
  }

  private boolean guardCheck_risk() {
    return isDirty_exposure_19
        | isDirty_notional_18
        | isDirty_rkRate_17
        | isDirty_rkTrade_16
        | isDirty_var_20;
  }

  private boolean guardCheck_var_20() {
    return isDirty_exposure_19 | isDirty_rkRate_17 | isDirty_vol_9;
  }

  @Override
  public <T> T getNodeById(String id) throws NoSuchFieldException {
    try {
      return nodeNameLookup.getInstanceById(id);
    } catch (NoSuchFieldException miss) {
      // Auditors live on the SEP as public fields rather than in
      // nodeNameLookup. Fall back to a reflective field probe so
      // callers (especially DataFlow.getServiceById) have a single
      // unified lookup path — no need to know whether the id maps to
      // a regular node or an auditor.
      try {
        @SuppressWarnings("unchecked")
        T t = (T) this.getClass().getField(id).get(this);
        return t;
      } catch (IllegalAccessException unreachable) {
        throw new NoSuchFieldException(id);
      } catch (NoSuchFieldException stillMissing) {
        throw miss;
      }
    }
  }

  @Override
  public <A extends Auditor> A getAuditorById(String id)
      throws NoSuchFieldException, IllegalAccessException {
    return (A) this.getClass().getField(id).get(this);
  }

  @Override
  public void addEventFeed(EventFeed eventProcessorFeed) {
    subscriptionManager.addEventProcessorFeed(eventProcessorFeed);
  }

  @Override
  public void removeEventFeed(EventFeed eventProcessorFeed) {
    subscriptionManager.removeEventProcessorFeed(eventProcessorFeed);
  }

  @Override
  public AppProcessor newInstance() {
    return new AppProcessor();
  }

  @Override
  public AppProcessor newInstance(Map<Object, Object> contextMap) {
    return new AppProcessor();
  }

  @Override
  public String getLastAuditLogRecord() {
    try {
      EventLogManager eventLogManager =
          (EventLogManager) this.getClass().getField(EventLogManager.NODE_NAME).get(this);
      return eventLogManager.lastRecordAsString();
    } catch (Throwable e) {
      return "";
    }
  }

  public void unKnownEventHandler(Object object) {
    unKnownEventHandler.accept(object);
  }

  @Override
  public <T> void setUnKnownEventHandler(Consumer<T> consumer) {
    unKnownEventHandler = consumer;
  }

  @Override
  public SubscriptionManager getSubscriptionManager() {
    return subscriptionManager;
  }
}
