/*
 * Copyright: © 2025.  Gregory Higgins <greg.higgins@v12technology.com> - All Rights Reserved
 * This source code is protected under international copyright law.  All rights
 * reserved and protected by the copyright holders.
 * This file is confidential and only available to authorized individuals with the
 * permission of the copyright holders.  If you encounter this file and do not have
 * permission, please contact the copyright holders and delete this file.
 */
package com.acme.app.generated;

import com.acme.app.Adjust;
import com.acme.app.Amend;
import com.acme.app.Cancel;
import com.acme.app.Carrier;
import com.acme.app.CarrierStore;
import com.acme.app.Count;
import com.acme.app.Customer;
import com.acme.app.CustomerStore;
import com.acme.app.Dispatch;
import com.acme.app.HazardBlockDecider;
import com.acme.app.Order;
import com.acme.app.OrderStore;
import com.acme.app.OverweightDecider;
import com.acme.app.Paid;
import com.acme.app.PaidStore;
import com.acme.app.Payfail;
import com.acme.app.PickDone;
import com.acme.app.Product;
import com.acme.app.ProductStore;
import com.acme.app.Receipt;
import com.acme.app.ReleaseDecider;
import com.acme.app.SLABreachDecider;
import com.acme.app.StockStore;
import com.acme.app.StockoutDecider;
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
 *   <li>com.acme.app.Adjust
 *   <li>com.acme.app.Amend
 *   <li>com.acme.app.Cancel
 *   <li>com.acme.app.Carrier
 *   <li>com.acme.app.Count
 *   <li>com.acme.app.Customer
 *   <li>com.acme.app.Dispatch
 *   <li>com.acme.app.Order
 *   <li>com.acme.app.Paid
 *   <li>com.acme.app.Payfail
 *   <li>com.acme.app.PickDone
 *   <li>com.acme.app.Product
 *   <li>com.acme.app.Receipt
 *   <li>com.telamin.fluxtion.runtime.audit.EventLogControlEvent
 *   <li>com.telamin.fluxtion.runtime.time.ClockStrategy.ClockStrategyEvent
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
  public final transient CarrierStore carrierStore = new CarrierStore();
  public final transient Clock clock = new Clock();
  public final transient CustomerStore customerStore = new CustomerStore();
  public final transient EventLogManager eventLogger = new EventLogManager();
  public final transient NodeNameAuditor nodeNameLookup = new NodeNameAuditor();
  public final transient OrderStore orderStore = new OrderStore();
  public final transient OverweightDecider overweightDecider =
      new com.acme.app.OverweightDecider(orderStore, carrierStore);;
  public final transient PaidStore paidStore = new com.acme.app.PaidStore(orderStore);;
  public final transient ProductStore productStore = new ProductStore();
  public final transient HazardBlockDecider hazardBlockDecider =
      new com.acme.app.HazardBlockDecider(orderStore, productStore, carrierStore);;
  public final transient SLABreachDecider slaBreachDecider =
      new com.acme.app.SLABreachDecider(orderStore);;
  public final transient StockStore stockStore = new StockStore();
  public final transient ReleaseDecider releaseDecider =
      new com.acme.app.ReleaseDecider(
          orderStore, stockStore, paidStore, productStore, customerStore);;
  public final transient StockoutDecider stockoutDecider =
      new com.acme.app.StockoutDecider(stockStore);;
  private final transient SubscriptionManagerNode subscriptionManager =
      new SubscriptionManagerNode();
  private final transient MutableDataFlowContext context =
      new com.telamin.fluxtion.runtime.node.MutableDataFlowContext(
          nodeNameLookup, callbackDispatcher, subscriptionManager, callbackDispatcher);;
  public final transient ServiceRegistryNode serviceRegistry = new ServiceRegistryNode();
  private final transient ExportFunctionAuditEvent functionAudit = new ExportFunctionAuditEvent();
  //Dirty flags
  private boolean initCalled = false;
  private boolean processing = false;
  private boolean buffering = false;
  private final transient IdentityHashMap<Object, BooleanSupplier> dirtyFlagSupplierMap =
      new IdentityHashMap<>(2);
  private final transient IdentityHashMap<Object, Consumer<Boolean>> dirtyFlagUpdateMap =
      new IdentityHashMap<>(2);

  private boolean isDirty_orderStore = false;
  private boolean isDirty_stockStore = false;

  //Forked declarations

  //Filter constants

  //Self-description of the embeddable surface — see ProcessorDescriptor
  private static final ProcessorDescriptor DESCRIPTOR =
      DescriptorSupport.of(
          AppProcessor.class,
          AppProcessor::new,
          new ProcessorDescriptor.Input[] {
            new ProcessorDescriptor.Input("Adjust", "com.acme.app.Adjust", false),
            new ProcessorDescriptor.Input("Amend", "com.acme.app.Amend", false),
            new ProcessorDescriptor.Input("Cancel", "com.acme.app.Cancel", false),
            new ProcessorDescriptor.Input("Carrier", "com.acme.app.Carrier", false),
            new ProcessorDescriptor.Input("Count", "com.acme.app.Count", false),
            new ProcessorDescriptor.Input("Customer", "com.acme.app.Customer", false),
            new ProcessorDescriptor.Input("Dispatch", "com.acme.app.Dispatch", false),
            new ProcessorDescriptor.Input("Order", "com.acme.app.Order", false),
            new ProcessorDescriptor.Input("Paid", "com.acme.app.Paid", false),
            new ProcessorDescriptor.Input("Payfail", "com.acme.app.Payfail", false),
            new ProcessorDescriptor.Input("PickDone", "com.acme.app.PickDone", false),
            new ProcessorDescriptor.Input("Product", "com.acme.app.Product", false),
            new ProcessorDescriptor.Input("Receipt", "com.acme.app.Receipt", false)
          },
          new ProcessorDescriptor.Sink[] {},
          new ProcessorDescriptor.Service[] {},
          new DescriptorSupport.Meta(
              null,
              null,
              "104966e585f99aa3f574c45f96b7ba9f493500af88331fa4b2bfc4488f1a2806",
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
    if (event instanceof Adjust) {
      Adjust typedEvent = (Adjust) event;
      handleEvent(typedEvent);
    } else if (event instanceof Amend) {
      Amend typedEvent = (Amend) event;
      handleEvent(typedEvent);
    } else if (event instanceof Cancel) {
      Cancel typedEvent = (Cancel) event;
      handleEvent(typedEvent);
    } else if (event instanceof Carrier) {
      Carrier typedEvent = (Carrier) event;
      handleEvent(typedEvent);
    } else if (event instanceof Count) {
      Count typedEvent = (Count) event;
      handleEvent(typedEvent);
    } else if (event instanceof Customer) {
      Customer typedEvent = (Customer) event;
      handleEvent(typedEvent);
    } else if (event instanceof Dispatch) {
      Dispatch typedEvent = (Dispatch) event;
      handleEvent(typedEvent);
    } else if (event instanceof Order) {
      Order typedEvent = (Order) event;
      handleEvent(typedEvent);
    } else if (event instanceof Paid) {
      Paid typedEvent = (Paid) event;
      handleEvent(typedEvent);
    } else if (event instanceof Payfail) {
      Payfail typedEvent = (Payfail) event;
      handleEvent(typedEvent);
    } else if (event instanceof PickDone) {
      PickDone typedEvent = (PickDone) event;
      handleEvent(typedEvent);
    } else if (event instanceof Product) {
      Product typedEvent = (Product) event;
      handleEvent(typedEvent);
    } else if (event instanceof Receipt) {
      Receipt typedEvent = (Receipt) event;
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
  public void onEvent(Adjust event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(Amend event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(Cancel event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(Carrier event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(Count event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(Customer event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(Dispatch event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(Order event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(Paid event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(Payfail event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(PickDone event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(Product event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(Receipt event) {
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

  public void handleEvent(Adjust typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(stockStore, "stockStore", "onAdjust", typedEvent);
    isDirty_stockStore = stockStore.onAdjust(typedEvent);
    auditInvocation(releaseDecider, "releaseDecider", "onAdjust", typedEvent);
    releaseDecider.onAdjust(typedEvent);
    if (guardCheck_stockoutDecider()) {
      auditInvocation(stockoutDecider, "stockoutDecider", "trigger", typedEvent);
      stockoutDecider.trigger();
    }
    afterEvent();
  }

  public void handleEvent(Amend typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(orderStore, "orderStore", "onAmend", typedEvent);
    isDirty_orderStore = orderStore.onAmend(typedEvent);
    auditInvocation(releaseDecider, "releaseDecider", "onAmend", typedEvent);
    releaseDecider.onAmend(typedEvent);
    afterEvent();
  }

  public void handleEvent(Cancel typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(orderStore, "orderStore", "onCancel", typedEvent);
    isDirty_orderStore = orderStore.onCancel(typedEvent);
    auditInvocation(releaseDecider, "releaseDecider", "onCancel", typedEvent);
    releaseDecider.onCancel(typedEvent);
    afterEvent();
  }

  public void handleEvent(Carrier typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(carrierStore, "carrierStore", "onCarrier", typedEvent);
    carrierStore.onCarrier(typedEvent);
    afterEvent();
  }

  public void handleEvent(Count typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(stockStore, "stockStore", "onCount", typedEvent);
    isDirty_stockStore = stockStore.onCount(typedEvent);
    auditInvocation(releaseDecider, "releaseDecider", "onCount", typedEvent);
    releaseDecider.onCount(typedEvent);
    if (guardCheck_stockoutDecider()) {
      auditInvocation(stockoutDecider, "stockoutDecider", "trigger", typedEvent);
      stockoutDecider.trigger();
    }
    afterEvent();
  }

  public void handleEvent(Customer typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(customerStore, "customerStore", "onCustomer", typedEvent);
    customerStore.onCustomer(typedEvent);
    auditInvocation(releaseDecider, "releaseDecider", "onCustomer", typedEvent);
    releaseDecider.onCustomer(typedEvent);
    afterEvent();
  }

  public void handleEvent(Dispatch typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(overweightDecider, "overweightDecider", "onDispatch", typedEvent);
    overweightDecider.onDispatch(typedEvent);
    auditInvocation(hazardBlockDecider, "hazardBlockDecider", "onDispatch", typedEvent);
    hazardBlockDecider.onDispatch(typedEvent);
    afterEvent();
  }

  public void handleEvent(Order typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(orderStore, "orderStore", "onOrder", typedEvent);
    isDirty_orderStore = orderStore.onOrder(typedEvent);
    auditInvocation(releaseDecider, "releaseDecider", "onOrder", typedEvent);
    releaseDecider.onOrder(typedEvent);
    afterEvent();
  }

  public void handleEvent(Paid typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(paidStore, "paidStore", "onPaid", typedEvent);
    paidStore.onPaid(typedEvent);
    auditInvocation(releaseDecider, "releaseDecider", "onPaid", typedEvent);
    releaseDecider.onPaid(typedEvent);
    afterEvent();
  }

  public void handleEvent(Payfail typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(paidStore, "paidStore", "onPayfail", typedEvent);
    paidStore.onPayfail(typedEvent);
    auditInvocation(releaseDecider, "releaseDecider", "onPayfail", typedEvent);
    releaseDecider.onPayfail(typedEvent);
    afterEvent();
  }

  public void handleEvent(PickDone typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(slaBreachDecider, "slaBreachDecider", "onPickDone", typedEvent);
    slaBreachDecider.onPickDone(typedEvent);
    afterEvent();
  }

  public void handleEvent(Product typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(productStore, "productStore", "onProduct", typedEvent);
    productStore.onProduct(typedEvent);
    auditInvocation(releaseDecider, "releaseDecider", "onProduct", typedEvent);
    releaseDecider.onProduct(typedEvent);
    afterEvent();
  }

  public void handleEvent(Receipt typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(stockStore, "stockStore", "onReceipt", typedEvent);
    isDirty_stockStore = stockStore.onReceipt(typedEvent);
    auditInvocation(releaseDecider, "releaseDecider", "onReceipt", typedEvent);
    releaseDecider.onReceipt(typedEvent);
    if (guardCheck_stockoutDecider()) {
      auditInvocation(stockoutDecider, "stockoutDecider", "trigger", typedEvent);
      stockoutDecider.trigger();
    }
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
  //EXPORTED SERVICE FUNCTIONS - END

  //EVENT BUFFERING - START
  public void bufferEvent(Object event) {
    buffering = true;
    if (event instanceof Adjust) {
      Adjust typedEvent = (Adjust) event;
      auditEvent(typedEvent);
      auditInvocation(stockStore, "stockStore", "onAdjust", typedEvent);
      isDirty_stockStore = stockStore.onAdjust(typedEvent);
      auditInvocation(releaseDecider, "releaseDecider", "onAdjust", typedEvent);
      releaseDecider.onAdjust(typedEvent);
    } else if (event instanceof Amend) {
      Amend typedEvent = (Amend) event;
      auditEvent(typedEvent);
      auditInvocation(orderStore, "orderStore", "onAmend", typedEvent);
      isDirty_orderStore = orderStore.onAmend(typedEvent);
      auditInvocation(releaseDecider, "releaseDecider", "onAmend", typedEvent);
      releaseDecider.onAmend(typedEvent);
    } else if (event instanceof Cancel) {
      Cancel typedEvent = (Cancel) event;
      auditEvent(typedEvent);
      auditInvocation(orderStore, "orderStore", "onCancel", typedEvent);
      isDirty_orderStore = orderStore.onCancel(typedEvent);
      auditInvocation(releaseDecider, "releaseDecider", "onCancel", typedEvent);
      releaseDecider.onCancel(typedEvent);
    } else if (event instanceof Carrier) {
      Carrier typedEvent = (Carrier) event;
      auditEvent(typedEvent);
      auditInvocation(carrierStore, "carrierStore", "onCarrier", typedEvent);
      carrierStore.onCarrier(typedEvent);
    } else if (event instanceof Count) {
      Count typedEvent = (Count) event;
      auditEvent(typedEvent);
      auditInvocation(stockStore, "stockStore", "onCount", typedEvent);
      isDirty_stockStore = stockStore.onCount(typedEvent);
      auditInvocation(releaseDecider, "releaseDecider", "onCount", typedEvent);
      releaseDecider.onCount(typedEvent);
    } else if (event instanceof Customer) {
      Customer typedEvent = (Customer) event;
      auditEvent(typedEvent);
      auditInvocation(customerStore, "customerStore", "onCustomer", typedEvent);
      customerStore.onCustomer(typedEvent);
      auditInvocation(releaseDecider, "releaseDecider", "onCustomer", typedEvent);
      releaseDecider.onCustomer(typedEvent);
    } else if (event instanceof Dispatch) {
      Dispatch typedEvent = (Dispatch) event;
      auditEvent(typedEvent);
      auditInvocation(overweightDecider, "overweightDecider", "onDispatch", typedEvent);
      overweightDecider.onDispatch(typedEvent);
      auditInvocation(hazardBlockDecider, "hazardBlockDecider", "onDispatch", typedEvent);
      hazardBlockDecider.onDispatch(typedEvent);
    } else if (event instanceof Order) {
      Order typedEvent = (Order) event;
      auditEvent(typedEvent);
      auditInvocation(orderStore, "orderStore", "onOrder", typedEvent);
      isDirty_orderStore = orderStore.onOrder(typedEvent);
      auditInvocation(releaseDecider, "releaseDecider", "onOrder", typedEvent);
      releaseDecider.onOrder(typedEvent);
    } else if (event instanceof Paid) {
      Paid typedEvent = (Paid) event;
      auditEvent(typedEvent);
      auditInvocation(paidStore, "paidStore", "onPaid", typedEvent);
      paidStore.onPaid(typedEvent);
      auditInvocation(releaseDecider, "releaseDecider", "onPaid", typedEvent);
      releaseDecider.onPaid(typedEvent);
    } else if (event instanceof Payfail) {
      Payfail typedEvent = (Payfail) event;
      auditEvent(typedEvent);
      auditInvocation(paidStore, "paidStore", "onPayfail", typedEvent);
      paidStore.onPayfail(typedEvent);
      auditInvocation(releaseDecider, "releaseDecider", "onPayfail", typedEvent);
      releaseDecider.onPayfail(typedEvent);
    } else if (event instanceof PickDone) {
      PickDone typedEvent = (PickDone) event;
      auditEvent(typedEvent);
      auditInvocation(slaBreachDecider, "slaBreachDecider", "onPickDone", typedEvent);
      slaBreachDecider.onPickDone(typedEvent);
    } else if (event instanceof Product) {
      Product typedEvent = (Product) event;
      auditEvent(typedEvent);
      auditInvocation(productStore, "productStore", "onProduct", typedEvent);
      productStore.onProduct(typedEvent);
      auditInvocation(releaseDecider, "releaseDecider", "onProduct", typedEvent);
      releaseDecider.onProduct(typedEvent);
    } else if (event instanceof Receipt) {
      Receipt typedEvent = (Receipt) event;
      auditEvent(typedEvent);
      auditInvocation(stockStore, "stockStore", "onReceipt", typedEvent);
      isDirty_stockStore = stockStore.onReceipt(typedEvent);
      auditInvocation(releaseDecider, "releaseDecider", "onReceipt", typedEvent);
      releaseDecider.onReceipt(typedEvent);
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
    if (guardCheck_stockoutDecider()) {
      auditInvocation(stockoutDecider, "stockoutDecider", "trigger", typedEvent);
      stockoutDecider.trigger();
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
    auditor.nodeRegistered(carrierStore, "carrierStore");
    auditor.nodeRegistered(customerStore, "customerStore");
    auditor.nodeRegistered(hazardBlockDecider, "hazardBlockDecider");
    auditor.nodeRegistered(orderStore, "orderStore");
    auditor.nodeRegistered(overweightDecider, "overweightDecider");
    auditor.nodeRegistered(paidStore, "paidStore");
    auditor.nodeRegistered(productStore, "productStore");
    auditor.nodeRegistered(releaseDecider, "releaseDecider");
    auditor.nodeRegistered(slaBreachDecider, "slaBreachDecider");
    auditor.nodeRegistered(stockStore, "stockStore");
    auditor.nodeRegistered(stockoutDecider, "stockoutDecider");
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
    isDirty_orderStore = false;
    isDirty_stockStore = false;
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
      dirtyFlagSupplierMap.put(orderStore, () -> isDirty_orderStore);
      dirtyFlagSupplierMap.put(stockStore, () -> isDirty_stockStore);
    }
    return dirtyFlagSupplierMap.getOrDefault(node, DataFlow.ALWAYS_FALSE);
  }

  @Override
  public void setDirty(Object node, boolean dirtyFlag) {
    if (dirtyFlagUpdateMap.isEmpty()) {
      dirtyFlagUpdateMap.put(orderStore, (b) -> isDirty_orderStore = b);
      dirtyFlagUpdateMap.put(stockStore, (b) -> isDirty_stockStore = b);
    }
    dirtyFlagUpdateMap.get(node).accept(dirtyFlag);
  }

  private boolean guardCheck_paidStore() {
    return isDirty_orderStore;
  }

  private boolean guardCheck_releaseDecider() {
    return isDirty_orderStore;
  }

  private boolean guardCheck_stockoutDecider() {
    return isDirty_stockStore;
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
