/*
 * This source code is protected under international copyright law.  All rights
 * reserved and protected by the copyright holders.
 * This file is confidential and only available to authorized individuals with the
 * permission of the copyright holders.  If you encounter this file and do not have
 * permission, please contact the copyright holders and delete this file.
 */
package telamin.fluxtion.audit.analyser.analyser.session.generated;

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
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.EffectFailed;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.GraphClosed;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.GraphObserved;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LogClosed;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LogObserved;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.OpenProjectRequested;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ProfileApplied;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ProfileLoaded;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.SettingsRestored;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.StatusShown;
import telamin.fluxtion.audit.analyser.analyser.session.node.ActiveProject;
import telamin.fluxtion.audit.analyser.analyser.session.node.EffectOutcomes;
import telamin.fluxtion.audit.analyser.analyser.session.node.EffectQueue;
import telamin.fluxtion.audit.analyser.analyser.session.node.OpenGraph;
import telamin.fluxtion.audit.analyser.analyser.session.node.OpenLog;
import telamin.fluxtion.audit.analyser.analyser.session.node.OperationGate;
import telamin.fluxtion.audit.analyser.analyser.session.node.SessionBoundary;

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
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.EffectFailed
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.GraphClosed
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.GraphObserved
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LogClosed
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LogObserved
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.OpenProjectRequested
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ProfileApplied
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ProfileLoaded
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.SettingsRestored
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.StatusShown
 * </ul>
 *
 * @author Greg Higgins
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public class SessionProcessor
    implements CloneableDataFlow<SessionProcessor>,
        /*--- @ExportService start ---*/
        @ExportService ServiceListener,
        /*--- @ExportService end ---*/
        DataFlow,
        InternalEventProcessor,
        BatchHandler {

  //Node declarations
  private final transient CallbackDispatcherImpl callbackDispatcher = new CallbackDispatcherImpl();
  public final transient Clock clock = new Clock();
  public final transient EffectQueue effectQueue = new EffectQueue();
  public final transient EventLogManager eventLogger = new EventLogManager();
  public final transient NodeNameAuditor nodeNameLookup = new NodeNameAuditor();
  public final transient OperationGate operationGate = new OperationGate();
  public final transient ActiveProject activeProject =
      new telamin.fluxtion.audit.analyser.analyser.session.node.ActiveProject(operationGate);;
  public final transient EffectOutcomes effectOutcomes =
      new telamin.fluxtion.audit.analyser.analyser.session.node.EffectOutcomes(operationGate);;
  public final transient OpenGraph openGraph =
      new telamin.fluxtion.audit.analyser.analyser.session.node.OpenGraph(operationGate);;
  public final transient OpenLog openLog =
      new telamin.fluxtion.audit.analyser.analyser.session.node.OpenLog(operationGate);;
  public final transient SessionBoundary sessionBoundary =
      new telamin.fluxtion.audit.analyser.analyser.session.node.SessionBoundary(
          operationGate, activeProject, openLog, openGraph, effectQueue);;
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
      new IdentityHashMap<>(4);
  private final transient IdentityHashMap<Object, Consumer<Boolean>> dirtyFlagUpdateMap =
      new IdentityHashMap<>(4);

  private boolean isDirty_activeProject = false;
  private boolean isDirty_openGraph = false;
  private boolean isDirty_openLog = false;
  private boolean isDirty_operationGate = false;

  //Filter constants

  //Self-description of the embeddable surface — see ProcessorDescriptor
  private static final ProcessorDescriptor DESCRIPTOR =
      DescriptorSupport.of(
          SessionProcessor.class,
          SessionProcessor::new,
          new ProcessorDescriptor.Input[] {
            new ProcessorDescriptor.Input(
                "EffectFailed",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.EffectFailed",
                false),
            new ProcessorDescriptor.Input(
                "GraphClosed",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.GraphClosed",
                false),
            new ProcessorDescriptor.Input(
                "GraphObserved",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.GraphObserved",
                false),
            new ProcessorDescriptor.Input(
                "LogClosed",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LogClosed",
                false),
            new ProcessorDescriptor.Input(
                "LogObserved",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LogObserved",
                false),
            new ProcessorDescriptor.Input(
                "OpenProjectRequested",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.OpenProjectRequested",
                false),
            new ProcessorDescriptor.Input(
                "ProfileApplied",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ProfileApplied",
                false),
            new ProcessorDescriptor.Input(
                "ProfileLoaded",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ProfileLoaded",
                false),
            new ProcessorDescriptor.Input(
                "SettingsRestored",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.SettingsRestored",
                false),
            new ProcessorDescriptor.Input(
                "StatusShown",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.StatusShown",
                false)
          },
          new ProcessorDescriptor.Sink[] {},
          new ProcessorDescriptor.Service[] {},
          new DescriptorSupport.Meta(null, null, null, null));

  @Override
  public ProcessorDescriptor getDescriptor() {
    return DESCRIPTOR;
  }

  //unknown event handler
  private Consumer unKnownEventHandler = (e) -> {};

  public SessionProcessor(Map<Object, Object> contextMap) {
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

  public SessionProcessor() {
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
    } else if (event instanceof EffectFailed) {
      EffectFailed typedEvent = (EffectFailed) event;
      handleEvent(typedEvent);
    } else if (event instanceof GraphClosed) {
      GraphClosed typedEvent = (GraphClosed) event;
      handleEvent(typedEvent);
    } else if (event instanceof GraphObserved) {
      GraphObserved typedEvent = (GraphObserved) event;
      handleEvent(typedEvent);
    } else if (event instanceof LogClosed) {
      LogClosed typedEvent = (LogClosed) event;
      handleEvent(typedEvent);
    } else if (event instanceof LogObserved) {
      LogObserved typedEvent = (LogObserved) event;
      handleEvent(typedEvent);
    } else if (event instanceof OpenProjectRequested) {
      OpenProjectRequested typedEvent = (OpenProjectRequested) event;
      handleEvent(typedEvent);
    } else if (event instanceof ProfileApplied) {
      ProfileApplied typedEvent = (ProfileApplied) event;
      handleEvent(typedEvent);
    } else if (event instanceof ProfileLoaded) {
      ProfileLoaded typedEvent = (ProfileLoaded) event;
      handleEvent(typedEvent);
    } else if (event instanceof SettingsRestored) {
      SettingsRestored typedEvent = (SettingsRestored) event;
      handleEvent(typedEvent);
    } else if (event instanceof StatusShown) {
      StatusShown typedEvent = (StatusShown) event;
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
  public void onEvent(EffectFailed event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(GraphClosed event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(GraphObserved event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(LogClosed event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(LogObserved event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(OpenProjectRequested event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(ProfileApplied event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(ProfileLoaded event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(SettingsRestored event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(StatusShown event) {
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

  public void handleEvent(EffectFailed typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(operationGate, "operationGate", "onEffectFailed", typedEvent);
    isDirty_operationGate = operationGate.onEffectFailed(typedEvent);
    auditInvocation(effectOutcomes, "effectOutcomes", "onEffectFailed", typedEvent);
    effectOutcomes.onEffectFailed(typedEvent);
    afterEvent();
  }

  public void handleEvent(GraphClosed typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(operationGate, "operationGate", "onGraphClosed", typedEvent);
    isDirty_operationGate = operationGate.onGraphClosed(typedEvent);
    auditInvocation(effectOutcomes, "effectOutcomes", "onGraphClosed", typedEvent);
    effectOutcomes.onGraphClosed(typedEvent);
    auditInvocation(openGraph, "openGraph", "onGraphClosed", typedEvent);
    isDirty_openGraph = openGraph.onGraphClosed(typedEvent);
    afterEvent();
  }

  public void handleEvent(GraphObserved typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(operationGate, "operationGate", "onGraphObserved", typedEvent);
    isDirty_operationGate = operationGate.onGraphObserved(typedEvent);
    auditInvocation(openGraph, "openGraph", "onGraphObserved", typedEvent);
    isDirty_openGraph = openGraph.onGraphObserved(typedEvent);
    afterEvent();
  }

  public void handleEvent(LogClosed typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(operationGate, "operationGate", "onLogClosed", typedEvent);
    isDirty_operationGate = operationGate.onLogClosed(typedEvent);
    auditInvocation(effectOutcomes, "effectOutcomes", "onLogClosed", typedEvent);
    effectOutcomes.onLogClosed(typedEvent);
    auditInvocation(openLog, "openLog", "onLogClosed", typedEvent);
    isDirty_openLog = openLog.onLogClosed(typedEvent);
    afterEvent();
  }

  public void handleEvent(LogObserved typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(operationGate, "operationGate", "onLogObserved", typedEvent);
    isDirty_operationGate = operationGate.onLogObserved(typedEvent);
    auditInvocation(openLog, "openLog", "onLogObserved", typedEvent);
    isDirty_openLog = openLog.onLogObserved(typedEvent);
    afterEvent();
  }

  public void handleEvent(OpenProjectRequested typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(operationGate, "operationGate", "onOpenProjectRequested", typedEvent);
    isDirty_operationGate = operationGate.onOpenProjectRequested(typedEvent);
    auditInvocation(sessionBoundary, "sessionBoundary", "onOpenProjectRequested", typedEvent);
    sessionBoundary.onOpenProjectRequested(typedEvent);
    afterEvent();
  }

  public void handleEvent(ProfileApplied typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(operationGate, "operationGate", "onProfileApplied", typedEvent);
    isDirty_operationGate = operationGate.onProfileApplied(typedEvent);
    auditInvocation(activeProject, "activeProject", "onProfileApplied", typedEvent);
    isDirty_activeProject = activeProject.onProfileApplied(typedEvent);
    auditInvocation(effectOutcomes, "effectOutcomes", "onProfileApplied", typedEvent);
    effectOutcomes.onProfileApplied(typedEvent);
    afterEvent();
  }

  public void handleEvent(ProfileLoaded typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(operationGate, "operationGate", "onProfileLoaded", typedEvent);
    isDirty_operationGate = operationGate.onProfileLoaded(typedEvent);
    auditInvocation(effectOutcomes, "effectOutcomes", "onProfileLoaded", typedEvent);
    effectOutcomes.onProfileLoaded(typedEvent);
    auditInvocation(sessionBoundary, "sessionBoundary", "onProfileLoaded", typedEvent);
    sessionBoundary.onProfileLoaded(typedEvent);
    afterEvent();
  }

  public void handleEvent(SettingsRestored typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(operationGate, "operationGate", "onSettingsRestored", typedEvent);
    isDirty_operationGate = operationGate.onSettingsRestored(typedEvent);
    auditInvocation(activeProject, "activeProject", "onSettingsRestored", typedEvent);
    isDirty_activeProject = activeProject.onSettingsRestored(typedEvent);
    auditInvocation(effectOutcomes, "effectOutcomes", "onSettingsRestored", typedEvent);
    effectOutcomes.onSettingsRestored(typedEvent);
    afterEvent();
  }

  public void handleEvent(StatusShown typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(operationGate, "operationGate", "onStatusShown", typedEvent);
    isDirty_operationGate = operationGate.onStatusShown(typedEvent);
    auditInvocation(effectOutcomes, "effectOutcomes", "onStatusShown", typedEvent);
    effectOutcomes.onStatusShown(typedEvent);
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
    } else if (event instanceof EffectFailed) {
      EffectFailed typedEvent = (EffectFailed) event;
      auditEvent(typedEvent);
      auditInvocation(operationGate, "operationGate", "onEffectFailed", typedEvent);
      isDirty_operationGate = operationGate.onEffectFailed(typedEvent);
      auditInvocation(effectOutcomes, "effectOutcomes", "onEffectFailed", typedEvent);
      effectOutcomes.onEffectFailed(typedEvent);
    } else if (event instanceof GraphClosed) {
      GraphClosed typedEvent = (GraphClosed) event;
      auditEvent(typedEvent);
      auditInvocation(operationGate, "operationGate", "onGraphClosed", typedEvent);
      isDirty_operationGate = operationGate.onGraphClosed(typedEvent);
      auditInvocation(effectOutcomes, "effectOutcomes", "onGraphClosed", typedEvent);
      effectOutcomes.onGraphClosed(typedEvent);
      auditInvocation(openGraph, "openGraph", "onGraphClosed", typedEvent);
      isDirty_openGraph = openGraph.onGraphClosed(typedEvent);
    } else if (event instanceof GraphObserved) {
      GraphObserved typedEvent = (GraphObserved) event;
      auditEvent(typedEvent);
      auditInvocation(operationGate, "operationGate", "onGraphObserved", typedEvent);
      isDirty_operationGate = operationGate.onGraphObserved(typedEvent);
      auditInvocation(openGraph, "openGraph", "onGraphObserved", typedEvent);
      isDirty_openGraph = openGraph.onGraphObserved(typedEvent);
    } else if (event instanceof LogClosed) {
      LogClosed typedEvent = (LogClosed) event;
      auditEvent(typedEvent);
      auditInvocation(operationGate, "operationGate", "onLogClosed", typedEvent);
      isDirty_operationGate = operationGate.onLogClosed(typedEvent);
      auditInvocation(effectOutcomes, "effectOutcomes", "onLogClosed", typedEvent);
      effectOutcomes.onLogClosed(typedEvent);
      auditInvocation(openLog, "openLog", "onLogClosed", typedEvent);
      isDirty_openLog = openLog.onLogClosed(typedEvent);
    } else if (event instanceof LogObserved) {
      LogObserved typedEvent = (LogObserved) event;
      auditEvent(typedEvent);
      auditInvocation(operationGate, "operationGate", "onLogObserved", typedEvent);
      isDirty_operationGate = operationGate.onLogObserved(typedEvent);
      auditInvocation(openLog, "openLog", "onLogObserved", typedEvent);
      isDirty_openLog = openLog.onLogObserved(typedEvent);
    } else if (event instanceof OpenProjectRequested) {
      OpenProjectRequested typedEvent = (OpenProjectRequested) event;
      auditEvent(typedEvent);
      auditInvocation(operationGate, "operationGate", "onOpenProjectRequested", typedEvent);
      isDirty_operationGate = operationGate.onOpenProjectRequested(typedEvent);
      auditInvocation(sessionBoundary, "sessionBoundary", "onOpenProjectRequested", typedEvent);
      sessionBoundary.onOpenProjectRequested(typedEvent);
    } else if (event instanceof ProfileApplied) {
      ProfileApplied typedEvent = (ProfileApplied) event;
      auditEvent(typedEvent);
      auditInvocation(operationGate, "operationGate", "onProfileApplied", typedEvent);
      isDirty_operationGate = operationGate.onProfileApplied(typedEvent);
      auditInvocation(activeProject, "activeProject", "onProfileApplied", typedEvent);
      isDirty_activeProject = activeProject.onProfileApplied(typedEvent);
      auditInvocation(effectOutcomes, "effectOutcomes", "onProfileApplied", typedEvent);
      effectOutcomes.onProfileApplied(typedEvent);
    } else if (event instanceof ProfileLoaded) {
      ProfileLoaded typedEvent = (ProfileLoaded) event;
      auditEvent(typedEvent);
      auditInvocation(operationGate, "operationGate", "onProfileLoaded", typedEvent);
      isDirty_operationGate = operationGate.onProfileLoaded(typedEvent);
      auditInvocation(effectOutcomes, "effectOutcomes", "onProfileLoaded", typedEvent);
      effectOutcomes.onProfileLoaded(typedEvent);
      auditInvocation(sessionBoundary, "sessionBoundary", "onProfileLoaded", typedEvent);
      sessionBoundary.onProfileLoaded(typedEvent);
    } else if (event instanceof SettingsRestored) {
      SettingsRestored typedEvent = (SettingsRestored) event;
      auditEvent(typedEvent);
      auditInvocation(operationGate, "operationGate", "onSettingsRestored", typedEvent);
      isDirty_operationGate = operationGate.onSettingsRestored(typedEvent);
      auditInvocation(activeProject, "activeProject", "onSettingsRestored", typedEvent);
      isDirty_activeProject = activeProject.onSettingsRestored(typedEvent);
      auditInvocation(effectOutcomes, "effectOutcomes", "onSettingsRestored", typedEvent);
      effectOutcomes.onSettingsRestored(typedEvent);
    } else if (event instanceof StatusShown) {
      StatusShown typedEvent = (StatusShown) event;
      auditEvent(typedEvent);
      auditInvocation(operationGate, "operationGate", "onStatusShown", typedEvent);
      isDirty_operationGate = operationGate.onStatusShown(typedEvent);
      auditInvocation(effectOutcomes, "effectOutcomes", "onStatusShown", typedEvent);
      effectOutcomes.onStatusShown(typedEvent);
    }
  }

  public void triggerCalculation() {
    buffering = false;
    String typedEvent = "No event information - buffered dispatch";
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
    auditor.nodeRegistered(activeProject, "activeProject");
    auditor.nodeRegistered(effectOutcomes, "effectOutcomes");
    auditor.nodeRegistered(effectQueue, "effectQueue");
    auditor.nodeRegistered(openGraph, "openGraph");
    auditor.nodeRegistered(openLog, "openLog");
    auditor.nodeRegistered(operationGate, "operationGate");
    auditor.nodeRegistered(sessionBoundary, "sessionBoundary");
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
    isDirty_activeProject = false;
    isDirty_openGraph = false;
    isDirty_openLog = false;
    isDirty_operationGate = false;
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
      dirtyFlagSupplierMap.put(activeProject, () -> isDirty_activeProject);
      dirtyFlagSupplierMap.put(openGraph, () -> isDirty_openGraph);
      dirtyFlagSupplierMap.put(openLog, () -> isDirty_openLog);
      dirtyFlagSupplierMap.put(operationGate, () -> isDirty_operationGate);
    }
    return dirtyFlagSupplierMap.getOrDefault(node, DataFlow.ALWAYS_FALSE);
  }

  @Override
  public void setDirty(Object node, boolean dirtyFlag) {
    if (dirtyFlagUpdateMap.isEmpty()) {
      dirtyFlagUpdateMap.put(activeProject, (b) -> isDirty_activeProject = b);
      dirtyFlagUpdateMap.put(openGraph, (b) -> isDirty_openGraph = b);
      dirtyFlagUpdateMap.put(openLog, (b) -> isDirty_openLog = b);
      dirtyFlagUpdateMap.put(operationGate, (b) -> isDirty_operationGate = b);
    }
    dirtyFlagUpdateMap.get(node).accept(dirtyFlag);
  }

  private boolean guardCheck_activeProject() {
    return isDirty_operationGate;
  }

  private boolean guardCheck_effectOutcomes() {
    return isDirty_operationGate;
  }

  private boolean guardCheck_openGraph() {
    return isDirty_operationGate;
  }

  private boolean guardCheck_openLog() {
    return isDirty_operationGate;
  }

  private boolean guardCheck_sessionBoundary() {
    return isDirty_activeProject | isDirty_openGraph | isDirty_openLog | isDirty_operationGate;
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
  public SessionProcessor newInstance() {
    return new SessionProcessor();
  }

  @Override
  public SessionProcessor newInstance(Map<Object, Object> contextMap) {
    return new SessionProcessor();
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
