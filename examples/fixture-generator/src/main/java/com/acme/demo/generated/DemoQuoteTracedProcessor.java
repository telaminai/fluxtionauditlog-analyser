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
 * api version               : 1.0.16
 * analyser version          : 1.0.71
 * target generator version  : 1.0.75
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
public class DemoQuoteTracedProcessor
    implements CloneableDataFlow<DemoQuoteTracedProcessor>,
        /*--- @ExportService start ---*/
        @ExportService QuoteControl,
        @ExportService ServiceListener,
        /*--- @ExportService end ---*/
        DataFlow,
        InternalEventProcessor,
        BatchHandler,
        com.telamin.fluxtion.runtime.node.NodeNameLookup {

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
  //M50/W1 - written by CallbackDispatcherImpl when it queues, cleared when it drains empty. Read on
  //the event path instead of walking processor->dispatcher->ArrayDeque to be told the queue is empty.
  //Measured saving on a 3-event-type graph: 0.098ns of a 5.61ns event on a JIT; nothing on native+PGO.
  private boolean callbacksPending = false;
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
          DemoQuoteTracedProcessor.class,
          DemoQuoteTracedProcessor::new,
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
          new DescriptorSupport.Meta(
              null,
              "1.0.71",
              "de849dc6785ee1f13da506afebd8db510ae090af3f6bbcb8ef1d2bdfad4e2c8e",
              null));

  @Override
  public ProcessorDescriptor getDescriptor() {
    return DESCRIPTOR;
  }

  //unknown event handler
  private Consumer unKnownEventHandler = (e) -> {};

  public DemoQuoteTracedProcessor(Map<Object, Object> contextMap) {
    if (context != null) {
      context.replaceMappings(contextMap);
    }
    riskMonitor.setDataFlowContext(context);
    eventLogger.setClearAfterPublish(false);
    eventLogger.trace = true;
    eventLogger.printEventToString = true;
    eventLogger.printThreadName = true;
    eventLogger.traceLevel = LogLevel.TRACE;
    eventLogger.clock = clock;
    eventLogger.binaryRecord = false;
    eventLogger.recordEndTime = true;
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

  public DemoQuoteTracedProcessor() {
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
      if (callbacksPending) {
        final boolean sharedBefore = clock.shareReading(true);
        callbackDispatcher.dispatchQueuedCallbacks();
        clock.shareReading(sharedBefore);
      }
      processing = false;
    }
  }

  /**
   * M50/W1 - the dispatcher tells this processor when it has queued work, and when the queue has
   * drained empty. Keeping the answer in a field this processor owns is what lets the event path
   * skip walking into the dispatcher and its ArrayDeque on every event to be told there is nothing
   * to do. The dispatcher owns the WRITE because it sees every queueing path - a node holding the
   * dispatcher directly can queue without this processor ever seeing the call.
   */
  @Override
  public void callbacksPending(boolean pending) {
    callbacksPending = pending;
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
    auditInvocation(priceListener, "priceListener", "marketData", typedEvent);
    isDirty_priceListener = priceListener.marketData(typedEvent);
    if (guardCheck_spreadCalculator()) {
      auditInvocation(spreadCalculator, "spreadCalculator", "calculate", typedEvent);
      isDirty_spreadCalculator = spreadCalculator.calculate();
    }
    if (guardCheck_quotePublisher()) {
      auditInvocation(quotePublisher, "quotePublisher", "publish", typedEvent);
      quotePublisher.publish();
    }
    afterEvent();
  }

  public void handleEvent(OrderUpdateEvent typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(orderTracker, "orderTracker", "orderUpdate", typedEvent);
    isDirty_orderTracker = orderTracker.orderUpdate(typedEvent);
    if (guardCheck_quotePublisher()) {
      auditInvocation(quotePublisher, "quotePublisher", "publish", typedEvent);
      quotePublisher.publish();
    }
    if (guardCheck_riskMonitor()) {
      auditInvocation(riskMonitor, "riskMonitor", "checkLimit", typedEvent);
      riskMonitor.checkLimit();
    }
    afterEvent();
  }

  public void handleEvent(RiskBreachEvent typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(breachHandler, "breachHandler", "onBreach", typedEvent);
    breachHandler.onBreach(typedEvent);
    afterEvent();
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

  @Override
  public void resumeQuoting() {
    beforeServiceCall("@Override\npublic void resumeQuoting()");
    ExportFunctionAuditEvent typedEvent = functionAudit;
    auditInvocation(quotePublisher, "quotePublisher", "resumeQuoting", typedEvent);
    quotePublisher.resumeQuoting();
    afterServiceCall();
  }

  @Override
  public void suspendQuoting(String arg0) {
    beforeServiceCall("@Override\npublic void suspendQuoting(String arg0)");
    ExportFunctionAuditEvent typedEvent = functionAudit;
    auditInvocation(quotePublisher, "quotePublisher", "suspendQuoting", typedEvent);
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
      auditInvocation(priceListener, "priceListener", "marketData", typedEvent);
      isDirty_priceListener = priceListener.marketData(typedEvent);
    } else if (event instanceof OrderUpdateEvent) {
      OrderUpdateEvent typedEvent = (OrderUpdateEvent) event;
      auditEvent(typedEvent);
      auditInvocation(orderTracker, "orderTracker", "orderUpdate", typedEvent);
      isDirty_orderTracker = orderTracker.orderUpdate(typedEvent);
    } else if (event instanceof RiskBreachEvent) {
      RiskBreachEvent typedEvent = (RiskBreachEvent) event;
      auditEvent(typedEvent);
      auditInvocation(breachHandler, "breachHandler", "onBreach", typedEvent);
      breachHandler.onBreach(typedEvent);
    } else if (event instanceof EventLogControlEvent) {
      EventLogControlEvent typedEvent = (EventLogControlEvent) event;
      auditEvent(typedEvent);
      auditInvocation(eventLogger, "eventLogger", "calculationLogConfig", typedEvent);
      eventLogger.calculationLogConfig(typedEvent);
    } else if (event instanceof ClockStrategyEvent) {
      ClockStrategyEvent typedEvent = (ClockStrategyEvent) event;
      auditEvent(typedEvent);
      auditInvocation(clock, "clock", "setClockStrategy", typedEvent);
      clock.setClockStrategy(typedEvent);
    }
  }

  public void triggerCalculation() {
    buffering = false;
    String typedEvent = "No event information - buffered dispatch";
    if (guardCheck_spreadCalculator()) {
      auditInvocation(spreadCalculator, "spreadCalculator", "calculate", typedEvent);
      isDirty_spreadCalculator = spreadCalculator.calculate();
    }
    if (guardCheck_quotePublisher()) {
      auditInvocation(quotePublisher, "quotePublisher", "publish", typedEvent);
      quotePublisher.publish();
    }
    if (guardCheck_riskMonitor()) {
      auditInvocation(riskMonitor, "riskMonitor", "checkLimit", typedEvent);
      riskMonitor.checkLimit();
    }
    afterEvent();
  }
  //EVENT BUFFERING - END

  private void auditEvent(Object typedEvent) {
    clock.eventReceived(typedEvent);
    eventLogger.eventReceived(typedEvent);
  }

  private void auditEvent(Event typedEvent) {
    clock.eventReceived(typedEvent);
    eventLogger.eventReceived(typedEvent);
  }

  private void auditInvocation(Object node, String nodeName, String methodName, Object typedEvent) {
    eventLogger.nodeInvoked(node, nodeName, methodName, typedEvent);
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

  /**
   * M50/W4 — nodes resolved by a generated switch, not by a populated map: registering them would
   * publish every node into the auditor's HashMaps and stop the graph being dissolved.
   */
  @SuppressWarnings("unchecked")
  @Override
  public <T> T getInstanceById(String id) throws NoSuchFieldException {
    switch (id) {
      case "breachHandler":
        return (T) breachHandler;
      case "orderTracker":
        return (T) orderTracker;
      case "priceListener":
        return (T) priceListener;
      case "quotePublisher":
        return (T) quotePublisher;
      case "riskMonitor":
        return (T) riskMonitor;
      case "spreadCalculator":
        return (T) spreadCalculator;
      case "eventLogger":
        return (T) eventLogger;
      case "nodeNameLookup":
        return (T) nodeNameLookup;
      case "callbackDispatcher":
        return (T) callbackDispatcher;
      case "subscriptionManager":
        return (T) subscriptionManager;
      case "context":
        return (T) context;
      case "serviceRegistry":
        return (T) serviceRegistry;
      case "clock":
        return (T) clock;
      default:
        throw new NoSuchFieldException(id);
    }
  }

  /** M50/W4 — the reverse direction, also generated. */
  @Override
  public String lookupInstanceName(Object node) {
    if (node == breachHandler) {
      return "breachHandler";
    }
    if (node == orderTracker) {
      return "orderTracker";
    }
    if (node == priceListener) {
      return "priceListener";
    }
    if (node == quotePublisher) {
      return "quotePublisher";
    }
    if (node == riskMonitor) {
      return "riskMonitor";
    }
    if (node == spreadCalculator) {
      return "spreadCalculator";
    }
    if (node == eventLogger) {
      return "eventLogger";
    }
    if (node == nodeNameLookup) {
      return "nodeNameLookup";
    }
    if (node == callbackDispatcher) {
      return "callbackDispatcher";
    }
    if (node == subscriptionManager) {
      return "subscriptionManager";
    }
    if (node == context) {
      return "context";
    }
    if (node == serviceRegistry) {
      return "serviceRegistry";
    }
    if (node == clock) {
      return "clock";
    }
    return null;
  }

  @Override
  public <T> T getNodeById(String id) throws NoSuchFieldException {
    try {
      return getInstanceById(id);
    } catch (NoSuchFieldException miss) {
      // Auditors live on the SEP as fields rather than in nodeNameLookup, so callers
      // (especially DataFlow.getServiceById) get one unified lookup path. The auditor
      // half is a generated switch, not a reflective probe: reflection here would
      // require native-image reflection configuration from every user, and would fail
      // at runtime rather than at build time.
      try {
        @SuppressWarnings("unchecked")
        T t = (T) getAuditorById(id);
        return t;
      } catch (NoSuchFieldException stillMissing) {
        throw miss;
      }
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public <A extends Auditor> A getAuditorById(String id) throws NoSuchFieldException {
    switch (id) {
      case "clock":
        return (A) clock;
      case "eventLogger":
        return (A) eventLogger;
      case "nodeNameLookup":
        return (A) nodeNameLookup;
      case "serviceRegistry":
        return (A) serviceRegistry;
      default:
        throw new NoSuchFieldException(id);
    }
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
  public DemoQuoteTracedProcessor newInstance() {
    return new DemoQuoteTracedProcessor();
  }

  @Override
  public DemoQuoteTracedProcessor newInstance(Map<Object, Object> contextMap) {
    return new DemoQuoteTracedProcessor();
  }

  @Override
  public String getLastAuditLogRecord() {
    try {
      EventLogManager eventLogManager = getAuditorById(EventLogManager.NODE_NAME);
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
