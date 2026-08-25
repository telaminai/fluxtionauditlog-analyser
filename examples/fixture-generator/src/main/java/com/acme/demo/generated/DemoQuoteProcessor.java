package com.acme.demo.generated;

import com.acme.demo.api.QuoteControl;
import com.acme.demo.event.Events.MarketDataEvent;
import com.acme.demo.event.Events.OrderUpdateEvent;
import com.acme.demo.event.Events.RiskBreachEvent;
import com.acme.demo.node.Nodes.BreachHandler;
import com.acme.demo.node.Nodes.OrderTracker;
import com.acme.demo.node.Nodes.PriceListener;
import com.acme.demo.node.Nodes.QuotePublisher;
import com.acme.demo.node.Nodes.RiskMonitor;
import com.acme.demo.node.Nodes.SpreadCalculator;
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
 *   <li>com.acme.demo.event.Events.MarketDataEvent
 *   <li>com.acme.demo.event.Events.OrderUpdateEvent
 *   <li>com.acme.demo.event.Events.RiskBreachEvent
 *   <li>com.telamin.fluxtion.runtime.audit.EventLogControlEvent
 *   <li>com.telamin.fluxtion.runtime.time.ClockStrategy.ClockStrategyEvent
 * </ul>
 *
 * @author Greg Higgins
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public class DemoQuoteProcessor
    implements CloneableDataFlow<DemoQuoteProcessor>,
        /*--- @ExportService start ---*/
        @ExportService QuoteControl,
        @ExportService ServiceListener,
        /*--- @ExportService end ---*/
        DataFlow,
        InternalEventProcessor,
        BatchHandler {

  //Node declarations
  private final transient CallbackDispatcherImpl callbackDispatcher = new CallbackDispatcherImpl();
  public final transient Clock clock = new Clock();
  public final transient EventLogManager eventLogger = new EventLogManager();
  public final transient NodeNameAuditor nodeNameLookup = new NodeNameAuditor();
  public final transient OrderTracker orderTracker = new OrderTracker();
  public final transient PriceListener priceListener = new PriceListener();
  public final transient SpreadCalculator spreadCalculator =
      new com.acme.demo.node.Nodes.SpreadCalculator(priceListener);;
  public final transient QuotePublisher quotePublisher =
      new com.acme.demo.node.Nodes.QuotePublisher(spreadCalculator, orderTracker);;
  private final transient SubscriptionManagerNode subscriptionManager =
      new SubscriptionManagerNode();
  private final transient MutableDataFlowContext context =
      new com.telamin.fluxtion.runtime.node.MutableDataFlowContext(
          nodeNameLookup, callbackDispatcher, subscriptionManager, callbackDispatcher);;
  public final transient RiskMonitor riskMonitor =
      new com.acme.demo.node.Nodes.RiskMonitor(orderTracker, 2);;
  public final transient ServiceRegistryNode serviceRegistry = new ServiceRegistryNode();
  public final transient BreachHandler breachHandler = new BreachHandler();
  private final transient ExportFunctionAuditEvent functionAudit = new ExportFunctionAuditEvent();
  //Dirty flags
  private boolean initCalled = false;
  private boolean processing = false;
  private boolean buffering = false;
  private final transient IdentityHashMap<Object, BooleanSupplier> dirtyFlagSupplierMap =
      new IdentityHashMap<>(3);
  private final transient IdentityHashMap<Object, Consumer<Boolean>> dirtyFlagUpdateMap =
      new IdentityHashMap<>(3);

  private boolean isDirty_orderTracker = false;
  private boolean isDirty_priceListener = false;
  private boolean isDirty_spreadCalculator = false;

  //Forked declarations

  //Filter constants

  //Self-description of the embeddable surface — see ProcessorDescriptor
  private static final ProcessorDescriptor DESCRIPTOR =
      DescriptorSupport.of(
          DemoQuoteProcessor.class,
          DemoQuoteProcessor::new,
          new ProcessorDescriptor.Input[] {
            new ProcessorDescriptor.Input(
                "MarketDataEvent", "com.acme.demo.event.Events.MarketDataEvent", false),
            new ProcessorDescriptor.Input(
                "OrderUpdateEvent", "com.acme.demo.event.Events.OrderUpdateEvent", false),
            new ProcessorDescriptor.Input(
                "RiskBreachEvent", "com.acme.demo.event.Events.RiskBreachEvent", false)
          },
          new ProcessorDescriptor.Sink[] {},
          new ProcessorDescriptor.Service[] {
            new ProcessorDescriptor.Service(
                "QuoteControl",
                "com.acme.demo.api.QuoteControl",
                ProcessorDescriptor.Service.Direction.EXPORTED)
          },
          new DescriptorSupport.Meta(null, null, null, null));

  @Override
  public ProcessorDescriptor getDescriptor() {
    return DESCRIPTOR;
  }

  //unknown event handler
  private Consumer unKnownEventHandler = (e) -> {};

  public DemoQuoteProcessor(Map<Object, Object> contextMap) {
    if (context != null) {
      context.replaceMappings(contextMap);
    }
    riskMonitor.setDataFlowContext(context);
    eventLogger.setClearAfterPublish(false);
    eventLogger.trace = false;
    eventLogger.printEventToString = true;
    eventLogger.printThreadName = true;
    eventLogger.traceLevel = LogLevel.NONE;
    eventLogger.clock = clock;
    context.setClock(clock);
    serviceRegistry.setDataFlowContext(context);
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

  public DemoQuoteProcessor() {
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
    if (event instanceof MarketDataEvent) {
      MarketDataEvent typedEvent = (MarketDataEvent) event;
      handleEvent(typedEvent);
    } else if (event instanceof OrderUpdateEvent) {
      OrderUpdateEvent typedEvent = (OrderUpdateEvent) event;
      handleEvent(typedEvent);
    } else if (event instanceof RiskBreachEvent) {
      RiskBreachEvent typedEvent = (RiskBreachEvent) event;
      handleEvent(typedEvent);
    } else if (event instanceof EventLogControlEvent) {
      EventLogControlEvent typedEvent = (EventLogControlEvent) event;
      handleEvent(typedEvent);
    } else if (event instanceof ClockStrategyEvent) {
      ClockStrategyEvent typedEvent = (ClockStrategyEvent) event;
      handleEvent(typedEvent);
    } else {
      unKnownEventHandler(event);
    }
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(MarketDataEvent event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(OrderUpdateEvent event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(RiskBreachEvent event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(EventLogControlEvent event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(ClockStrategyEvent event) {
    processEvent(event);
  }

  public void handleEvent(MarketDataEvent typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    isDirty_priceListener = priceListener.marketData(typedEvent);
    if (guardCheck_spreadCalculator()) {
      isDirty_spreadCalculator = spreadCalculator.calculate();
    }
    if (guardCheck_quotePublisher()) {
      quotePublisher.publish();
    }
    afterEvent();
  }

  public void handleEvent(OrderUpdateEvent typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    isDirty_orderTracker = orderTracker.orderUpdate(typedEvent);
    if (guardCheck_quotePublisher()) {
      quotePublisher.publish();
    }
    if (guardCheck_riskMonitor()) {
      riskMonitor.checkLimit();
    }
    afterEvent();
  }

  public void handleEvent(RiskBreachEvent typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    breachHandler.onBreach(typedEvent);
    afterEvent();
  }

  public void handleEvent(EventLogControlEvent typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    eventLogger.calculationLogConfig(typedEvent);
    afterEvent();
  }

  public void handleEvent(ClockStrategyEvent typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    clock.setClockStrategy(typedEvent);
    afterEvent();
  }
  //EVENT DISPATCH - END

  //EXPORTED SERVICE FUNCTIONS - START
  @Override
  public void deRegisterService(com.telamin.fluxtion.runtime.service.Service<?> arg0) {
    beforeServiceCall(
        "@Override\npublic void deRegisterService(com.telamin.fluxtion.runtime.service.Service<?> arg0)");
    ExportFunctionAuditEvent typedEvent = functionAudit;
    serviceRegistry.deRegisterService(arg0);
    afterServiceCall();
  }

  @Override
  public void registerService(com.telamin.fluxtion.runtime.service.Service<?> arg0) {
    beforeServiceCall(
        "@Override\npublic void registerService(com.telamin.fluxtion.runtime.service.Service<?> arg0)");
    ExportFunctionAuditEvent typedEvent = functionAudit;
    serviceRegistry.registerService(arg0);
    afterServiceCall();
  }

  @Override
  public void resumeQuoting() {
    beforeServiceCall("@Override\npublic void resumeQuoting()");
    ExportFunctionAuditEvent typedEvent = functionAudit;
    quotePublisher.resumeQuoting();
    afterServiceCall();
  }

  @Override
  public void suspendQuoting(String arg0) {
    beforeServiceCall("@Override\npublic void suspendQuoting(String arg0)");
    ExportFunctionAuditEvent typedEvent = functionAudit;
    quotePublisher.suspendQuoting(arg0);
    afterServiceCall();
  }
  //EXPORTED SERVICE FUNCTIONS - END

  //EVENT BUFFERING - START
  public void bufferEvent(Object event) {
    buffering = true;
    if (event instanceof MarketDataEvent) {
      MarketDataEvent typedEvent = (MarketDataEvent) event;
      auditEvent(typedEvent);
      isDirty_priceListener = priceListener.marketData(typedEvent);
    } else if (event instanceof OrderUpdateEvent) {
      OrderUpdateEvent typedEvent = (OrderUpdateEvent) event;
      auditEvent(typedEvent);
      isDirty_orderTracker = orderTracker.orderUpdate(typedEvent);
    } else if (event instanceof RiskBreachEvent) {
      RiskBreachEvent typedEvent = (RiskBreachEvent) event;
      auditEvent(typedEvent);
      breachHandler.onBreach(typedEvent);
    } else if (event instanceof EventLogControlEvent) {
      EventLogControlEvent typedEvent = (EventLogControlEvent) event;
      auditEvent(typedEvent);
      eventLogger.calculationLogConfig(typedEvent);
    } else if (event instanceof ClockStrategyEvent) {
      ClockStrategyEvent typedEvent = (ClockStrategyEvent) event;
      auditEvent(typedEvent);
      clock.setClockStrategy(typedEvent);
    }
  }

  public void triggerCalculation() {
    buffering = false;
    String typedEvent = "No event information - buffered dispatch";
    if (guardCheck_spreadCalculator()) {
      isDirty_spreadCalculator = spreadCalculator.calculate();
    }
    if (guardCheck_quotePublisher()) {
      quotePublisher.publish();
    }
    if (guardCheck_riskMonitor()) {
      riskMonitor.checkLimit();
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

  private void initialiseAuditor(Auditor auditor) {
    auditor.init();
    auditor.nodeRegistered(breachHandler, "breachHandler");
    auditor.nodeRegistered(orderTracker, "orderTracker");
    auditor.nodeRegistered(priceListener, "priceListener");
    auditor.nodeRegistered(quotePublisher, "quotePublisher");
    auditor.nodeRegistered(riskMonitor, "riskMonitor");
    auditor.nodeRegistered(spreadCalculator, "spreadCalculator");
    auditor.nodeRegistered(callbackDispatcher, "callbackDispatcher");
    auditor.nodeRegistered(subscriptionManager, "subscriptionManager");
    auditor.nodeRegistered(context, "context");
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
    isDirty_orderTracker = false;
    isDirty_priceListener = false;
    isDirty_spreadCalculator = false;
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
      dirtyFlagSupplierMap.put(orderTracker, () -> isDirty_orderTracker);
      dirtyFlagSupplierMap.put(priceListener, () -> isDirty_priceListener);
      dirtyFlagSupplierMap.put(spreadCalculator, () -> isDirty_spreadCalculator);
    }
    return dirtyFlagSupplierMap.getOrDefault(node, DataFlow.ALWAYS_FALSE);
  }

  @Override
  public void setDirty(Object node, boolean dirtyFlag) {
    if (dirtyFlagUpdateMap.isEmpty()) {
      dirtyFlagUpdateMap.put(orderTracker, (b) -> isDirty_orderTracker = b);
      dirtyFlagUpdateMap.put(priceListener, (b) -> isDirty_priceListener = b);
      dirtyFlagUpdateMap.put(spreadCalculator, (b) -> isDirty_spreadCalculator = b);
    }
    dirtyFlagUpdateMap.get(node).accept(dirtyFlag);
  }

  private boolean guardCheck_quotePublisher() {
    return isDirty_orderTracker | isDirty_spreadCalculator;
  }

  private boolean guardCheck_riskMonitor() {
    return isDirty_orderTracker;
  }

  private boolean guardCheck_spreadCalculator() {
    return isDirty_priceListener;
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
  public DemoQuoteProcessor newInstance() {
    return new DemoQuoteProcessor();
  }

  @Override
  public DemoQuoteProcessor newInstance(Map<Object, Object> contextMap) {
    return new DemoQuoteProcessor();
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
