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
import telamin.fluxtion.audit.analyser.analyser.design.DesignEvents.Cleared;
import telamin.fluxtion.audit.analyser.analyser.design.DesignEvents.ReadCompleted;
import telamin.fluxtion.audit.analyser.analyser.design.DesignEvents.ReadRequested;
import telamin.fluxtion.audit.analyser.analyser.design.DesignEvents.ResultReadCompleted;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.CloseRequested;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.EffectFailed;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.GraphCleared;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.GraphClosed;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.GraphOpened;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LogAppended;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LogCleared;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LogClosed;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LogIdentityObserved;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LogOpenFailed;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LogOpened;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.MembershipCompared;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.OpenLogRequested;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.OpenProjectRequested;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.OpenRequestReceived;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.Pending;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ProfileApplied;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ProfileLoaded;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.SettingsRestored;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.StatusShown;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ViewFilterChanged;
import telamin.fluxtion.audit.analyser.analyser.session.node.ActiveProject;
import telamin.fluxtion.audit.analyser.analyser.session.node.AuditInstallation;
import telamin.fluxtion.audit.analyser.analyser.session.node.CoverageClaim;
import telamin.fluxtion.audit.analyser.analyser.session.node.DesignSession;
import telamin.fluxtion.audit.analyser.analyser.session.node.EffectOutcomes;
import telamin.fluxtion.audit.analyser.analyser.session.node.EffectQueue;
import telamin.fluxtion.audit.analyser.analyser.session.node.IgnoredParameters;
import telamin.fluxtion.audit.analyser.analyser.session.node.LogArrival;
import telamin.fluxtion.audit.analyser.analyser.session.node.LogOpening;
import telamin.fluxtion.audit.analyser.analyser.session.node.OpenGraph;
import telamin.fluxtion.audit.analyser.analyser.session.node.OpenLog;
import telamin.fluxtion.audit.analyser.analyser.session.node.OperationGate;
import telamin.fluxtion.audit.analyser.analyser.session.node.Pairing;
import telamin.fluxtion.audit.analyser.analyser.session.node.PairingQualifier;
import telamin.fluxtion.audit.analyser.analyser.session.node.SessionBoundary;
import telamin.fluxtion.audit.analyser.analyser.session.node.SessionRecovery;
import telamin.fluxtion.audit.analyser.analyser.session.resume.ResumeEvents.Activated;
import telamin.fluxtion.audit.analyser.analyser.session.resume.ResumeEvents.Checked;
import telamin.fluxtion.audit.analyser.analyser.session.resume.ResumeEvents.Finished;
import telamin.fluxtion.audit.analyser.analyser.session.resume.ResumeEvents.OfferLoaded;
import telamin.fluxtion.audit.analyser.analyser.session.resume.ResumeEvents.Requested;

/**
 *
 *
 * <pre>
 * generation time           : Not available
 * api version               : 1.0.16
 * analyser version          : 1.0.71
 * target generator version  : 1.0.74
 * </pre>
 *
 * Event classes supported:
 *
 * <ul>
 *   <li>com.telamin.fluxtion.runtime.audit.EventLogControlEvent
 *   <li>com.telamin.fluxtion.runtime.time.ClockStrategy.ClockStrategyEvent
 *   <li>telamin.fluxtion.audit.analyser.analyser.design.DesignEvents.Cleared
 *   <li>telamin.fluxtion.audit.analyser.analyser.design.DesignEvents.ReadCompleted
 *   <li>telamin.fluxtion.audit.analyser.analyser.design.DesignEvents.ReadRequested
 *   <li>telamin.fluxtion.audit.analyser.analyser.design.DesignEvents.ResultReadCompleted
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.CloseRequested
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.EffectFailed
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.GraphCleared
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.GraphClosed
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.GraphOpened
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LogAppended
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LogCleared
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LogClosed
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LogIdentityObserved
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LogOpenFailed
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LogOpened
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.MembershipCompared
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.OpenLogRequested
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.OpenProjectRequested
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.OpenRequestReceived
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.Pending
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ProfileApplied
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ProfileLoaded
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.SettingsRestored
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.StatusShown
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ViewFilterChanged
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.resume.ResumeEvents.Activated
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.resume.ResumeEvents.Checked
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.resume.ResumeEvents.Finished
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.resume.ResumeEvents.OfferLoaded
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.resume.ResumeEvents.Requested
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
        BatchHandler,
        com.telamin.fluxtion.runtime.node.NodeNameLookup {

  //Node declarations
  private final transient CallbackDispatcherImpl callbackDispatcher = new CallbackDispatcherImpl();
  public final transient Clock clock = new Clock();
  public final transient EventLogManager eventLogger = new EventLogManager();
  public final transient NodeNameAuditor nodeNameLookup = new NodeNameAuditor();
  public final transient OperationGate operationGate = new OperationGate();
  public final transient ActiveProject activeProject =
      new telamin.fluxtion.audit.analyser.analyser.session.node.ActiveProject(operationGate);;
  public final transient DesignSession designSession =
      new telamin.fluxtion.audit.analyser.analyser.session.node.DesignSession(operationGate);;
  public final transient EffectOutcomes effectOutcomes =
      new telamin.fluxtion.audit.analyser.analyser.session.node.EffectOutcomes(operationGate);;
  public final transient OpenGraph openGraph =
      new telamin.fluxtion.audit.analyser.analyser.session.node.OpenGraph(operationGate);;
  public final transient AuditInstallation auditInstallation =
      new telamin.fluxtion.audit.analyser.analyser.session.node.AuditInstallation(openGraph);;
  public final transient OpenLog openLog =
      new telamin.fluxtion.audit.analyser.analyser.session.node.OpenLog(operationGate);;
  public final transient Pairing pairing =
      new telamin.fluxtion.audit.analyser.analyser.session.node.Pairing(openLog, openGraph);;
  public final transient CoverageClaim coverageClaim =
      new telamin.fluxtion.audit.analyser.analyser.session.node.CoverageClaim(
          pairing, auditInstallation, openGraph, openLog);;
  public final transient PairingQualifier pairingQualifier =
      new telamin.fluxtion.audit.analyser.analyser.session.node.PairingQualifier(
          openLog, openGraph, pairing);;
  public final transient SessionRecovery sessionRecovery =
      new telamin.fluxtion.audit.analyser.analyser.session.node.SessionRecovery(operationGate);;
  private final transient SubscriptionManagerNode subscriptionManager =
      new SubscriptionManagerNode();
  private final transient MutableDataFlowContext context =
      new com.telamin.fluxtion.runtime.node.MutableDataFlowContext(
          nodeNameLookup, callbackDispatcher, subscriptionManager, callbackDispatcher);;
  public final transient EffectQueue effectQueue = new EffectQueue();
  public final transient LogArrival logArrival =
      new telamin.fluxtion.audit.analyser.analyser.session.node.LogArrival(
          operationGate, pairing, openGraph, effectQueue);;
  public final transient LogOpening logOpening =
      new telamin.fluxtion.audit.analyser.analyser.session.node.LogOpening(
          operationGate, effectQueue);;
  public final transient ServiceRegistryNode serviceRegistry = new ServiceRegistryNode();
  public final transient SessionBoundary sessionBoundary =
      new telamin.fluxtion.audit.analyser.analyser.session.node.SessionBoundary(
          operationGate, activeProject, openLog, openGraph, effectQueue);;
  public final transient IgnoredParameters ignoredParameters = new IgnoredParameters();
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
      new IdentityHashMap<>(6);
  private final transient IdentityHashMap<Object, Consumer<Boolean>> dirtyFlagUpdateMap =
      new IdentityHashMap<>(6);

  private boolean isDirty_activeProject = false;
  private boolean isDirty_auditInstallation = false;
  private boolean isDirty_openGraph = false;
  private boolean isDirty_openLog = false;
  private boolean isDirty_operationGate = false;
  private boolean isDirty_pairing = false;

  //Forked declarations

  //Filter constants

  //Self-description of the embeddable surface — see ProcessorDescriptor
  private static final ProcessorDescriptor DESCRIPTOR =
      DescriptorSupport.of(
          SessionProcessor.class,
          SessionProcessor::new,
          new ProcessorDescriptor.Input[] {
            new ProcessorDescriptor.Input(
                "Activated",
                "telamin.fluxtion.audit.analyser.analyser.session.resume.ResumeEvents.Activated",
                false),
            new ProcessorDescriptor.Input(
                "Checked",
                "telamin.fluxtion.audit.analyser.analyser.session.resume.ResumeEvents.Checked",
                false),
            new ProcessorDescriptor.Input(
                "Cleared",
                "telamin.fluxtion.audit.analyser.analyser.design.DesignEvents.Cleared",
                false),
            new ProcessorDescriptor.Input(
                "CloseRequested",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.CloseRequested",
                false),
            new ProcessorDescriptor.Input(
                "EffectFailed",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.EffectFailed",
                false),
            new ProcessorDescriptor.Input(
                "Finished",
                "telamin.fluxtion.audit.analyser.analyser.session.resume.ResumeEvents.Finished",
                false),
            new ProcessorDescriptor.Input(
                "GraphCleared",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.GraphCleared",
                false),
            new ProcessorDescriptor.Input(
                "GraphClosed",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.GraphClosed",
                false),
            new ProcessorDescriptor.Input(
                "GraphOpened",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.GraphOpened",
                false),
            new ProcessorDescriptor.Input(
                "LogAppended",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LogAppended",
                false),
            new ProcessorDescriptor.Input(
                "LogCleared",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LogCleared",
                false),
            new ProcessorDescriptor.Input(
                "LogClosed",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LogClosed",
                false),
            new ProcessorDescriptor.Input(
                "LogIdentityObserved",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LogIdentityObserved",
                false),
            new ProcessorDescriptor.Input(
                "LogOpenFailed",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LogOpenFailed",
                false),
            new ProcessorDescriptor.Input(
                "LogOpened",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LogOpened",
                false),
            new ProcessorDescriptor.Input(
                "MembershipCompared",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.MembershipCompared",
                false),
            new ProcessorDescriptor.Input(
                "OfferLoaded",
                "telamin.fluxtion.audit.analyser.analyser.session.resume.ResumeEvents.OfferLoaded",
                false),
            new ProcessorDescriptor.Input(
                "OpenLogRequested",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.OpenLogRequested",
                false),
            new ProcessorDescriptor.Input(
                "OpenProjectRequested",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.OpenProjectRequested",
                false),
            new ProcessorDescriptor.Input(
                "OpenRequestReceived",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.OpenRequestReceived",
                false),
            new ProcessorDescriptor.Input(
                "Pending",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.Pending",
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
                "ReadCompleted",
                "telamin.fluxtion.audit.analyser.analyser.design.DesignEvents.ReadCompleted",
                false),
            new ProcessorDescriptor.Input(
                "ReadRequested",
                "telamin.fluxtion.audit.analyser.analyser.design.DesignEvents.ReadRequested",
                false),
            new ProcessorDescriptor.Input(
                "Requested",
                "telamin.fluxtion.audit.analyser.analyser.session.resume.ResumeEvents.Requested",
                false),
            new ProcessorDescriptor.Input(
                "ResultReadCompleted",
                "telamin.fluxtion.audit.analyser.analyser.design.DesignEvents.ResultReadCompleted",
                false),
            new ProcessorDescriptor.Input(
                "SettingsRestored",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.SettingsRestored",
                false),
            new ProcessorDescriptor.Input(
                "StatusShown",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.StatusShown",
                false),
            new ProcessorDescriptor.Input(
                "ViewFilterChanged",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ViewFilterChanged",
                false)
          },
          new ProcessorDescriptor.Sink[] {},
          new ProcessorDescriptor.Service[] {},
          new DescriptorSupport.Meta(
              null,
              "1.0.71",
              "c63344b3513cee78d3d57b9d402cbaed0f63cb7a8f406e71dbd932bdadbefc43",
              null));

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
    eventLogger.binaryRecord = false;
    eventLogger.recordEndTime = true;
    context.setClock(clock);
    serviceRegistry.setDataFlowContext(context);
    effectQueue.setDataFlowContext(context);
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
    if (event instanceof EventLogControlEvent) {
      EventLogControlEvent typedEvent = (EventLogControlEvent) event;
      handleEvent(typedEvent);
    } else if (event instanceof ClockStrategyEvent) {
      ClockStrategyEvent typedEvent = (ClockStrategyEvent) event;
      handleEvent(typedEvent);
    } else if (event instanceof Cleared) {
      Cleared typedEvent = (Cleared) event;
      handleEvent(typedEvent);
    } else if (event instanceof ReadCompleted) {
      ReadCompleted typedEvent = (ReadCompleted) event;
      handleEvent(typedEvent);
    } else if (event instanceof ReadRequested) {
      ReadRequested typedEvent = (ReadRequested) event;
      handleEvent(typedEvent);
    } else if (event instanceof ResultReadCompleted) {
      ResultReadCompleted typedEvent = (ResultReadCompleted) event;
      handleEvent(typedEvent);
    } else if (event instanceof CloseRequested) {
      CloseRequested typedEvent = (CloseRequested) event;
      handleEvent(typedEvent);
    } else if (event instanceof EffectFailed) {
      EffectFailed typedEvent = (EffectFailed) event;
      handleEvent(typedEvent);
    } else if (event instanceof GraphCleared) {
      GraphCleared typedEvent = (GraphCleared) event;
      handleEvent(typedEvent);
    } else if (event instanceof GraphClosed) {
      GraphClosed typedEvent = (GraphClosed) event;
      handleEvent(typedEvent);
    } else if (event instanceof GraphOpened) {
      GraphOpened typedEvent = (GraphOpened) event;
      handleEvent(typedEvent);
    } else if (event instanceof LogAppended) {
      LogAppended typedEvent = (LogAppended) event;
      handleEvent(typedEvent);
    } else if (event instanceof LogCleared) {
      LogCleared typedEvent = (LogCleared) event;
      handleEvent(typedEvent);
    } else if (event instanceof LogClosed) {
      LogClosed typedEvent = (LogClosed) event;
      handleEvent(typedEvent);
    } else if (event instanceof LogIdentityObserved) {
      LogIdentityObserved typedEvent = (LogIdentityObserved) event;
      handleEvent(typedEvent);
    } else if (event instanceof LogOpenFailed) {
      LogOpenFailed typedEvent = (LogOpenFailed) event;
      handleEvent(typedEvent);
    } else if (event instanceof LogOpened) {
      LogOpened typedEvent = (LogOpened) event;
      handleEvent(typedEvent);
    } else if (event instanceof MembershipCompared) {
      MembershipCompared typedEvent = (MembershipCompared) event;
      handleEvent(typedEvent);
    } else if (event instanceof OpenLogRequested) {
      OpenLogRequested typedEvent = (OpenLogRequested) event;
      handleEvent(typedEvent);
    } else if (event instanceof OpenProjectRequested) {
      OpenProjectRequested typedEvent = (OpenProjectRequested) event;
      handleEvent(typedEvent);
    } else if (event instanceof OpenRequestReceived) {
      OpenRequestReceived typedEvent = (OpenRequestReceived) event;
      handleEvent(typedEvent);
    } else if (event instanceof Pending) {
      Pending typedEvent = (Pending) event;
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
    } else if (event instanceof ViewFilterChanged) {
      ViewFilterChanged typedEvent = (ViewFilterChanged) event;
      handleEvent(typedEvent);
    } else if (event instanceof Activated) {
      Activated typedEvent = (Activated) event;
      handleEvent(typedEvent);
    } else if (event instanceof Checked) {
      Checked typedEvent = (Checked) event;
      handleEvent(typedEvent);
    } else if (event instanceof Finished) {
      Finished typedEvent = (Finished) event;
      handleEvent(typedEvent);
    } else if (event instanceof OfferLoaded) {
      OfferLoaded typedEvent = (OfferLoaded) event;
      handleEvent(typedEvent);
    } else if (event instanceof Requested) {
      Requested typedEvent = (Requested) event;
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
  public void onEvent(Cleared event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(ReadCompleted event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(ReadRequested event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(ResultReadCompleted event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(CloseRequested event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(EffectFailed event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(GraphCleared event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(GraphClosed event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(GraphOpened event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(LogAppended event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(LogCleared event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(LogClosed event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(LogIdentityObserved event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(LogOpenFailed event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(LogOpened event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(MembershipCompared event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(OpenLogRequested event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(OpenProjectRequested event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(OpenRequestReceived event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(Pending event) {
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

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(ViewFilterChanged event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(Activated event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(Checked event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(Finished event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(OfferLoaded event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(Requested event) {
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

  public void handleEvent(Cleared typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(designSession, "designSession", "cleared", typedEvent);
    designSession.cleared(typedEvent);
    afterEvent();
  }

  public void handleEvent(ReadCompleted typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(designSession, "designSession", "read", typedEvent);
    designSession.read(typedEvent);
    afterEvent();
  }

  public void handleEvent(ReadRequested typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(designSession, "designSession", "requested", typedEvent);
    designSession.requested(typedEvent);
    afterEvent();
  }

  public void handleEvent(ResultReadCompleted typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(designSession, "designSession", "result", typedEvent);
    designSession.result(typedEvent);
    afterEvent();
  }

  public void handleEvent(CloseRequested typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(operationGate, "operationGate", "onCloseRequested", typedEvent);
    isDirty_operationGate = operationGate.onCloseRequested(typedEvent);
    commonDispatchTail_1(typedEvent);
    afterEvent();
  }

  public void handleEvent(EffectFailed typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(operationGate, "operationGate", "onEffectFailed", typedEvent);
    isDirty_operationGate = operationGate.onEffectFailed(typedEvent);
    auditInvocation(effectOutcomes, "effectOutcomes", "onEffectFailed", typedEvent);
    effectOutcomes.onEffectFailed(typedEvent);
    commonDispatchTail_1(typedEvent);
    afterEvent();
  }

  public void handleEvent(GraphCleared typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(operationGate, "operationGate", "onGraphCleared", typedEvent);
    isDirty_operationGate = operationGate.onGraphCleared(typedEvent);
    auditInvocation(openGraph, "openGraph", "onGraphCleared", typedEvent);
    isDirty_openGraph = openGraph.onGraphCleared(typedEvent);
    commonDispatchTail_1(typedEvent);
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
    commonDispatchTail_1(typedEvent);
    afterEvent();
  }

  public void handleEvent(GraphOpened typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(operationGate, "operationGate", "onGraphOpened", typedEvent);
    isDirty_operationGate = operationGate.onGraphOpened(typedEvent);
    auditInvocation(openGraph, "openGraph", "onGraphOpened", typedEvent);
    isDirty_openGraph = openGraph.onGraphOpened(typedEvent);
    commonDispatchTail_1(typedEvent);
    afterEvent();
  }

  public void handleEvent(LogAppended typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(operationGate, "operationGate", "onLogAppended", typedEvent);
    isDirty_operationGate = operationGate.onLogAppended(typedEvent);
    if (guardCheck_auditInstallation()) {
      auditInvocation(auditInstallation, "auditInstallation", "recomputeOnStateChange", typedEvent);
      isDirty_auditInstallation = auditInstallation.recomputeOnStateChange();
    }
    auditInvocation(openLog, "openLog", "onLogAppended", typedEvent);
    isDirty_openLog = openLog.onLogAppended(typedEvent);
    if (guardCheck_pairing()) {
      auditInvocation(pairing, "pairing", "recomputeOnStateChange", typedEvent);
      isDirty_pairing = pairing.recomputeOnStateChange();
    }
    if (guardCheck_coverageClaim()) {
      auditInvocation(coverageClaim, "coverageClaim", "recomputeOnStateChange", typedEvent);
      coverageClaim.recomputeOnStateChange();
    }
    if (guardCheck_pairingQualifier()) {
      auditInvocation(pairingQualifier, "pairingQualifier", "onPairChanged", typedEvent);
      pairingQualifier.onPairChanged();
    }
    afterEvent();
  }

  public void handleEvent(LogCleared typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(operationGate, "operationGate", "onLogCleared", typedEvent);
    isDirty_operationGate = operationGate.onLogCleared(typedEvent);
    if (guardCheck_auditInstallation()) {
      auditInvocation(auditInstallation, "auditInstallation", "recomputeOnStateChange", typedEvent);
      isDirty_auditInstallation = auditInstallation.recomputeOnStateChange();
    }
    auditInvocation(openLog, "openLog", "onLogCleared", typedEvent);
    isDirty_openLog = openLog.onLogCleared(typedEvent);
    if (guardCheck_pairing()) {
      auditInvocation(pairing, "pairing", "recomputeOnStateChange", typedEvent);
      isDirty_pairing = pairing.recomputeOnStateChange();
    }
    if (guardCheck_coverageClaim()) {
      auditInvocation(coverageClaim, "coverageClaim", "recomputeOnStateChange", typedEvent);
      coverageClaim.recomputeOnStateChange();
    }
    if (guardCheck_pairingQualifier()) {
      auditInvocation(pairingQualifier, "pairingQualifier", "onPairChanged", typedEvent);
      pairingQualifier.onPairChanged();
    }
    afterEvent();
  }

  public void handleEvent(LogClosed typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(operationGate, "operationGate", "onLogClosed", typedEvent);
    isDirty_operationGate = operationGate.onLogClosed(typedEvent);
    auditInvocation(effectOutcomes, "effectOutcomes", "onLogClosed", typedEvent);
    effectOutcomes.onLogClosed(typedEvent);
    if (guardCheck_auditInstallation()) {
      auditInvocation(auditInstallation, "auditInstallation", "recomputeOnStateChange", typedEvent);
      isDirty_auditInstallation = auditInstallation.recomputeOnStateChange();
    }
    auditInvocation(openLog, "openLog", "onLogClosed", typedEvent);
    isDirty_openLog = openLog.onLogClosed(typedEvent);
    if (guardCheck_pairing()) {
      auditInvocation(pairing, "pairing", "recomputeOnStateChange", typedEvent);
      isDirty_pairing = pairing.recomputeOnStateChange();
    }
    if (guardCheck_coverageClaim()) {
      auditInvocation(coverageClaim, "coverageClaim", "recomputeOnStateChange", typedEvent);
      coverageClaim.recomputeOnStateChange();
    }
    if (guardCheck_pairingQualifier()) {
      auditInvocation(pairingQualifier, "pairingQualifier", "onPairChanged", typedEvent);
      pairingQualifier.onPairChanged();
    }
    afterEvent();
  }

  public void handleEvent(LogIdentityObserved typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(openLog, "openLog", "onLogIdentityObserved", typedEvent);
    isDirty_openLog = openLog.onLogIdentityObserved(typedEvent);
    if (guardCheck_pairing()) {
      auditInvocation(pairing, "pairing", "recomputeOnStateChange", typedEvent);
      isDirty_pairing = pairing.recomputeOnStateChange();
    }
    if (guardCheck_coverageClaim()) {
      auditInvocation(coverageClaim, "coverageClaim", "recomputeOnStateChange", typedEvent);
      coverageClaim.recomputeOnStateChange();
    }
    if (guardCheck_pairingQualifier()) {
      auditInvocation(pairingQualifier, "pairingQualifier", "onPairChanged", typedEvent);
      pairingQualifier.onPairChanged();
    }
    afterEvent();
  }

  public void handleEvent(LogOpenFailed typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(operationGate, "operationGate", "onLogOpenFailed", typedEvent);
    isDirty_operationGate = operationGate.onLogOpenFailed(typedEvent);
    auditInvocation(effectOutcomes, "effectOutcomes", "onLogOpenFailed", typedEvent);
    effectOutcomes.onLogOpenFailed(typedEvent);
    auditInvocation(logOpening, "logOpening", "onLogOpenFailed", typedEvent);
    logOpening.onLogOpenFailed(typedEvent);
    commonDispatchTail_1(typedEvent);
    afterEvent();
  }

  public void handleEvent(LogOpened typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(operationGate, "operationGate", "onLogOpened", typedEvent);
    isDirty_operationGate = operationGate.onLogOpened(typedEvent);
    auditInvocation(effectOutcomes, "effectOutcomes", "onLogOpened", typedEvent);
    effectOutcomes.onLogOpened(typedEvent);
    if (guardCheck_auditInstallation()) {
      auditInvocation(auditInstallation, "auditInstallation", "recomputeOnStateChange", typedEvent);
      isDirty_auditInstallation = auditInstallation.recomputeOnStateChange();
    }
    auditInvocation(openLog, "openLog", "onLogOpened", typedEvent);
    isDirty_openLog = openLog.onLogOpened(typedEvent);
    if (guardCheck_pairing()) {
      auditInvocation(pairing, "pairing", "recomputeOnStateChange", typedEvent);
      isDirty_pairing = pairing.recomputeOnStateChange();
    }
    if (guardCheck_coverageClaim()) {
      auditInvocation(coverageClaim, "coverageClaim", "recomputeOnStateChange", typedEvent);
      coverageClaim.recomputeOnStateChange();
    }
    auditInvocation(logArrival, "logArrival", "onLogOpened", typedEvent);
    logArrival.onLogOpened(typedEvent);
    if (guardCheck_pairingQualifier()) {
      auditInvocation(pairingQualifier, "pairingQualifier", "onPairChanged", typedEvent);
      pairingQualifier.onPairChanged();
    }
    afterEvent();
  }

  public void handleEvent(MembershipCompared typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(pairingQualifier, "pairingQualifier", "onMembershipCompared", typedEvent);
    pairingQualifier.onMembershipCompared(typedEvent);
    afterEvent();
  }

  public void handleEvent(OpenLogRequested typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(operationGate, "operationGate", "onOpenLogRequested", typedEvent);
    isDirty_operationGate = operationGate.onOpenLogRequested(typedEvent);
    auditInvocation(logOpening, "logOpening", "onOpenLogRequested", typedEvent);
    logOpening.onOpenLogRequested(typedEvent);
    if (guardCheck_auditInstallation()) {
      auditInvocation(auditInstallation, "auditInstallation", "recomputeOnStateChange", typedEvent);
      isDirty_auditInstallation = auditInstallation.recomputeOnStateChange();
    }
    if (guardCheck_pairing()) {
      auditInvocation(pairing, "pairing", "recomputeOnStateChange", typedEvent);
      isDirty_pairing = pairing.recomputeOnStateChange();
    }
    if (guardCheck_coverageClaim()) {
      auditInvocation(coverageClaim, "coverageClaim", "recomputeOnStateChange", typedEvent);
      coverageClaim.recomputeOnStateChange();
    }
    auditInvocation(logArrival, "logArrival", "onOpenLogRequested", typedEvent);
    logArrival.onOpenLogRequested(typedEvent);
    if (guardCheck_pairingQualifier()) {
      auditInvocation(pairingQualifier, "pairingQualifier", "onPairChanged", typedEvent);
      pairingQualifier.onPairChanged();
    }
    afterEvent();
  }

  public void handleEvent(OpenProjectRequested typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(operationGate, "operationGate", "onOpenProjectRequested", typedEvent);
    isDirty_operationGate = operationGate.onOpenProjectRequested(typedEvent);
    if (guardCheck_auditInstallation()) {
      auditInvocation(auditInstallation, "auditInstallation", "recomputeOnStateChange", typedEvent);
      isDirty_auditInstallation = auditInstallation.recomputeOnStateChange();
    }
    if (guardCheck_pairing()) {
      auditInvocation(pairing, "pairing", "recomputeOnStateChange", typedEvent);
      isDirty_pairing = pairing.recomputeOnStateChange();
    }
    if (guardCheck_coverageClaim()) {
      auditInvocation(coverageClaim, "coverageClaim", "recomputeOnStateChange", typedEvent);
      coverageClaim.recomputeOnStateChange();
    }
    if (guardCheck_pairingQualifier()) {
      auditInvocation(pairingQualifier, "pairingQualifier", "onPairChanged", typedEvent);
      pairingQualifier.onPairChanged();
    }
    auditInvocation(sessionBoundary, "sessionBoundary", "onOpenProjectRequested", typedEvent);
    sessionBoundary.onOpenProjectRequested(typedEvent);
    afterEvent();
  }

  public void handleEvent(OpenRequestReceived typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(ignoredParameters, "ignoredParameters", "onOpenRequestReceived", typedEvent);
    ignoredParameters.onOpenRequestReceived(typedEvent);
    afterEvent();
  }

  public void handleEvent(Pending typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(operationGate, "operationGate", "onPending", typedEvent);
    isDirty_operationGate = operationGate.onPending(typedEvent);
    auditInvocation(effectOutcomes, "effectOutcomes", "onPending", typedEvent);
    effectOutcomes.onPending(typedEvent);
    commonDispatchTail_1(typedEvent);
    afterEvent();
  }

  public void handleEvent(ProfileApplied typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(operationGate, "operationGate", "onProfileApplied", typedEvent);
    isDirty_operationGate = operationGate.onProfileApplied(typedEvent);
    auditInvocation(activeProject, "activeProject", "onProfileApplied", typedEvent);
    isDirty_activeProject = activeProject.onProfileApplied(typedEvent);
    auditInvocation(designSession, "designSession", "project", typedEvent);
    designSession.project(typedEvent);
    auditInvocation(effectOutcomes, "effectOutcomes", "onProfileApplied", typedEvent);
    effectOutcomes.onProfileApplied(typedEvent);
    commonDispatchTail_1(typedEvent);
    afterEvent();
  }

  public void handleEvent(ProfileLoaded typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(operationGate, "operationGate", "onProfileLoaded", typedEvent);
    isDirty_operationGate = operationGate.onProfileLoaded(typedEvent);
    auditInvocation(effectOutcomes, "effectOutcomes", "onProfileLoaded", typedEvent);
    effectOutcomes.onProfileLoaded(typedEvent);
    if (guardCheck_auditInstallation()) {
      auditInvocation(auditInstallation, "auditInstallation", "recomputeOnStateChange", typedEvent);
      isDirty_auditInstallation = auditInstallation.recomputeOnStateChange();
    }
    if (guardCheck_pairing()) {
      auditInvocation(pairing, "pairing", "recomputeOnStateChange", typedEvent);
      isDirty_pairing = pairing.recomputeOnStateChange();
    }
    if (guardCheck_coverageClaim()) {
      auditInvocation(coverageClaim, "coverageClaim", "recomputeOnStateChange", typedEvent);
      coverageClaim.recomputeOnStateChange();
    }
    if (guardCheck_pairingQualifier()) {
      auditInvocation(pairingQualifier, "pairingQualifier", "onPairChanged", typedEvent);
      pairingQualifier.onPairChanged();
    }
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
    auditInvocation(designSession, "designSession", "restored", typedEvent);
    designSession.restored(typedEvent);
    auditInvocation(effectOutcomes, "effectOutcomes", "onSettingsRestored", typedEvent);
    effectOutcomes.onSettingsRestored(typedEvent);
    commonDispatchTail_1(typedEvent);
    afterEvent();
  }

  public void handleEvent(StatusShown typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(operationGate, "operationGate", "onStatusShown", typedEvent);
    isDirty_operationGate = operationGate.onStatusShown(typedEvent);
    auditInvocation(effectOutcomes, "effectOutcomes", "onStatusShown", typedEvent);
    effectOutcomes.onStatusShown(typedEvent);
    commonDispatchTail_1(typedEvent);
    afterEvent();
  }

  public void handleEvent(ViewFilterChanged typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(pairingQualifier, "pairingQualifier", "onViewFilterChanged", typedEvent);
    pairingQualifier.onViewFilterChanged(typedEvent);
    afterEvent();
  }

  public void handleEvent(Activated typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(sessionRecovery, "sessionRecovery", "activate", typedEvent);
    sessionRecovery.activate(typedEvent);
    afterEvent();
  }

  public void handleEvent(Checked typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(sessionRecovery, "sessionRecovery", "checked", typedEvent);
    sessionRecovery.checked(typedEvent);
    afterEvent();
  }

  public void handleEvent(Finished typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(sessionRecovery, "sessionRecovery", "finished", typedEvent);
    sessionRecovery.finished(typedEvent);
    afterEvent();
  }

  public void handleEvent(OfferLoaded typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(sessionRecovery, "sessionRecovery", "offer", typedEvent);
    sessionRecovery.offer(typedEvent);
    afterEvent();
  }

  public void handleEvent(Requested typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(sessionRecovery, "sessionRecovery", "request", typedEvent);
    sessionRecovery.request(typedEvent);
    afterEvent();
  }
  //EVENT DISPATCH - END

  //MERGED DISPATCH HELPERS - START

  private void commonDispatchTail_1(Object typedEvent) {
    if (guardCheck_auditInstallation()) {
      auditInvocation(auditInstallation, "auditInstallation", "recomputeOnStateChange", typedEvent);
      isDirty_auditInstallation = auditInstallation.recomputeOnStateChange();
    }
    if (guardCheck_pairing()) {
      auditInvocation(pairing, "pairing", "recomputeOnStateChange", typedEvent);
      isDirty_pairing = pairing.recomputeOnStateChange();
    }
    if (guardCheck_coverageClaim()) {
      auditInvocation(coverageClaim, "coverageClaim", "recomputeOnStateChange", typedEvent);
      coverageClaim.recomputeOnStateChange();
    }
    if (guardCheck_pairingQualifier()) {
      auditInvocation(pairingQualifier, "pairingQualifier", "onPairChanged", typedEvent);
      pairingQualifier.onPairChanged();
    }
  }
  //MERGED DISPATCH HELPERS - END

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
    } else if (event instanceof Cleared) {
      Cleared typedEvent = (Cleared) event;
      auditEvent(typedEvent);
      auditInvocation(designSession, "designSession", "cleared", typedEvent);
      designSession.cleared(typedEvent);
    } else if (event instanceof ReadCompleted) {
      ReadCompleted typedEvent = (ReadCompleted) event;
      auditEvent(typedEvent);
      auditInvocation(designSession, "designSession", "read", typedEvent);
      designSession.read(typedEvent);
    } else if (event instanceof ReadRequested) {
      ReadRequested typedEvent = (ReadRequested) event;
      auditEvent(typedEvent);
      auditInvocation(designSession, "designSession", "requested", typedEvent);
      designSession.requested(typedEvent);
    } else if (event instanceof ResultReadCompleted) {
      ResultReadCompleted typedEvent = (ResultReadCompleted) event;
      auditEvent(typedEvent);
      auditInvocation(designSession, "designSession", "result", typedEvent);
      designSession.result(typedEvent);
    } else if (event instanceof CloseRequested) {
      CloseRequested typedEvent = (CloseRequested) event;
      auditEvent(typedEvent);
      auditInvocation(operationGate, "operationGate", "onCloseRequested", typedEvent);
      isDirty_operationGate = operationGate.onCloseRequested(typedEvent);
    } else if (event instanceof EffectFailed) {
      EffectFailed typedEvent = (EffectFailed) event;
      auditEvent(typedEvent);
      auditInvocation(operationGate, "operationGate", "onEffectFailed", typedEvent);
      isDirty_operationGate = operationGate.onEffectFailed(typedEvent);
      auditInvocation(effectOutcomes, "effectOutcomes", "onEffectFailed", typedEvent);
      effectOutcomes.onEffectFailed(typedEvent);
    } else if (event instanceof GraphCleared) {
      GraphCleared typedEvent = (GraphCleared) event;
      auditEvent(typedEvent);
      auditInvocation(operationGate, "operationGate", "onGraphCleared", typedEvent);
      isDirty_operationGate = operationGate.onGraphCleared(typedEvent);
      auditInvocation(openGraph, "openGraph", "onGraphCleared", typedEvent);
      isDirty_openGraph = openGraph.onGraphCleared(typedEvent);
    } else if (event instanceof GraphClosed) {
      GraphClosed typedEvent = (GraphClosed) event;
      auditEvent(typedEvent);
      auditInvocation(operationGate, "operationGate", "onGraphClosed", typedEvent);
      isDirty_operationGate = operationGate.onGraphClosed(typedEvent);
      auditInvocation(effectOutcomes, "effectOutcomes", "onGraphClosed", typedEvent);
      effectOutcomes.onGraphClosed(typedEvent);
      auditInvocation(openGraph, "openGraph", "onGraphClosed", typedEvent);
      isDirty_openGraph = openGraph.onGraphClosed(typedEvent);
    } else if (event instanceof GraphOpened) {
      GraphOpened typedEvent = (GraphOpened) event;
      auditEvent(typedEvent);
      auditInvocation(operationGate, "operationGate", "onGraphOpened", typedEvent);
      isDirty_operationGate = operationGate.onGraphOpened(typedEvent);
      auditInvocation(openGraph, "openGraph", "onGraphOpened", typedEvent);
      isDirty_openGraph = openGraph.onGraphOpened(typedEvent);
    } else if (event instanceof LogAppended) {
      LogAppended typedEvent = (LogAppended) event;
      auditEvent(typedEvent);
      auditInvocation(operationGate, "operationGate", "onLogAppended", typedEvent);
      isDirty_operationGate = operationGate.onLogAppended(typedEvent);
      auditInvocation(openLog, "openLog", "onLogAppended", typedEvent);
      isDirty_openLog = openLog.onLogAppended(typedEvent);
    } else if (event instanceof LogCleared) {
      LogCleared typedEvent = (LogCleared) event;
      auditEvent(typedEvent);
      auditInvocation(operationGate, "operationGate", "onLogCleared", typedEvent);
      isDirty_operationGate = operationGate.onLogCleared(typedEvent);
      auditInvocation(openLog, "openLog", "onLogCleared", typedEvent);
      isDirty_openLog = openLog.onLogCleared(typedEvent);
    } else if (event instanceof LogClosed) {
      LogClosed typedEvent = (LogClosed) event;
      auditEvent(typedEvent);
      auditInvocation(operationGate, "operationGate", "onLogClosed", typedEvent);
      isDirty_operationGate = operationGate.onLogClosed(typedEvent);
      auditInvocation(effectOutcomes, "effectOutcomes", "onLogClosed", typedEvent);
      effectOutcomes.onLogClosed(typedEvent);
      auditInvocation(openLog, "openLog", "onLogClosed", typedEvent);
      isDirty_openLog = openLog.onLogClosed(typedEvent);
    } else if (event instanceof LogIdentityObserved) {
      LogIdentityObserved typedEvent = (LogIdentityObserved) event;
      auditEvent(typedEvent);
      auditInvocation(openLog, "openLog", "onLogIdentityObserved", typedEvent);
      isDirty_openLog = openLog.onLogIdentityObserved(typedEvent);
    } else if (event instanceof LogOpenFailed) {
      LogOpenFailed typedEvent = (LogOpenFailed) event;
      auditEvent(typedEvent);
      auditInvocation(operationGate, "operationGate", "onLogOpenFailed", typedEvent);
      isDirty_operationGate = operationGate.onLogOpenFailed(typedEvent);
      auditInvocation(effectOutcomes, "effectOutcomes", "onLogOpenFailed", typedEvent);
      effectOutcomes.onLogOpenFailed(typedEvent);
      auditInvocation(logOpening, "logOpening", "onLogOpenFailed", typedEvent);
      logOpening.onLogOpenFailed(typedEvent);
    } else if (event instanceof LogOpened) {
      LogOpened typedEvent = (LogOpened) event;
      auditEvent(typedEvent);
      auditInvocation(operationGate, "operationGate", "onLogOpened", typedEvent);
      isDirty_operationGate = operationGate.onLogOpened(typedEvent);
      auditInvocation(effectOutcomes, "effectOutcomes", "onLogOpened", typedEvent);
      effectOutcomes.onLogOpened(typedEvent);
      auditInvocation(openLog, "openLog", "onLogOpened", typedEvent);
      isDirty_openLog = openLog.onLogOpened(typedEvent);
      auditInvocation(logArrival, "logArrival", "onLogOpened", typedEvent);
      logArrival.onLogOpened(typedEvent);
    } else if (event instanceof MembershipCompared) {
      MembershipCompared typedEvent = (MembershipCompared) event;
      auditEvent(typedEvent);
      auditInvocation(pairingQualifier, "pairingQualifier", "onMembershipCompared", typedEvent);
      pairingQualifier.onMembershipCompared(typedEvent);
    } else if (event instanceof OpenLogRequested) {
      OpenLogRequested typedEvent = (OpenLogRequested) event;
      auditEvent(typedEvent);
      auditInvocation(operationGate, "operationGate", "onOpenLogRequested", typedEvent);
      isDirty_operationGate = operationGate.onOpenLogRequested(typedEvent);
      auditInvocation(logOpening, "logOpening", "onOpenLogRequested", typedEvent);
      logOpening.onOpenLogRequested(typedEvent);
      auditInvocation(logArrival, "logArrival", "onOpenLogRequested", typedEvent);
      logArrival.onOpenLogRequested(typedEvent);
    } else if (event instanceof OpenProjectRequested) {
      OpenProjectRequested typedEvent = (OpenProjectRequested) event;
      auditEvent(typedEvent);
      auditInvocation(operationGate, "operationGate", "onOpenProjectRequested", typedEvent);
      isDirty_operationGate = operationGate.onOpenProjectRequested(typedEvent);
      auditInvocation(sessionBoundary, "sessionBoundary", "onOpenProjectRequested", typedEvent);
      sessionBoundary.onOpenProjectRequested(typedEvent);
    } else if (event instanceof OpenRequestReceived) {
      OpenRequestReceived typedEvent = (OpenRequestReceived) event;
      auditEvent(typedEvent);
      auditInvocation(ignoredParameters, "ignoredParameters", "onOpenRequestReceived", typedEvent);
      ignoredParameters.onOpenRequestReceived(typedEvent);
    } else if (event instanceof Pending) {
      Pending typedEvent = (Pending) event;
      auditEvent(typedEvent);
      auditInvocation(operationGate, "operationGate", "onPending", typedEvent);
      isDirty_operationGate = operationGate.onPending(typedEvent);
      auditInvocation(effectOutcomes, "effectOutcomes", "onPending", typedEvent);
      effectOutcomes.onPending(typedEvent);
    } else if (event instanceof ProfileApplied) {
      ProfileApplied typedEvent = (ProfileApplied) event;
      auditEvent(typedEvent);
      auditInvocation(operationGate, "operationGate", "onProfileApplied", typedEvent);
      isDirty_operationGate = operationGate.onProfileApplied(typedEvent);
      auditInvocation(activeProject, "activeProject", "onProfileApplied", typedEvent);
      isDirty_activeProject = activeProject.onProfileApplied(typedEvent);
      auditInvocation(designSession, "designSession", "project", typedEvent);
      designSession.project(typedEvent);
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
      auditInvocation(designSession, "designSession", "restored", typedEvent);
      designSession.restored(typedEvent);
      auditInvocation(effectOutcomes, "effectOutcomes", "onSettingsRestored", typedEvent);
      effectOutcomes.onSettingsRestored(typedEvent);
    } else if (event instanceof StatusShown) {
      StatusShown typedEvent = (StatusShown) event;
      auditEvent(typedEvent);
      auditInvocation(operationGate, "operationGate", "onStatusShown", typedEvent);
      isDirty_operationGate = operationGate.onStatusShown(typedEvent);
      auditInvocation(effectOutcomes, "effectOutcomes", "onStatusShown", typedEvent);
      effectOutcomes.onStatusShown(typedEvent);
    } else if (event instanceof ViewFilterChanged) {
      ViewFilterChanged typedEvent = (ViewFilterChanged) event;
      auditEvent(typedEvent);
      auditInvocation(pairingQualifier, "pairingQualifier", "onViewFilterChanged", typedEvent);
      pairingQualifier.onViewFilterChanged(typedEvent);
    } else if (event instanceof Activated) {
      Activated typedEvent = (Activated) event;
      auditEvent(typedEvent);
      auditInvocation(sessionRecovery, "sessionRecovery", "activate", typedEvent);
      sessionRecovery.activate(typedEvent);
    } else if (event instanceof Checked) {
      Checked typedEvent = (Checked) event;
      auditEvent(typedEvent);
      auditInvocation(sessionRecovery, "sessionRecovery", "checked", typedEvent);
      sessionRecovery.checked(typedEvent);
    } else if (event instanceof Finished) {
      Finished typedEvent = (Finished) event;
      auditEvent(typedEvent);
      auditInvocation(sessionRecovery, "sessionRecovery", "finished", typedEvent);
      sessionRecovery.finished(typedEvent);
    } else if (event instanceof OfferLoaded) {
      OfferLoaded typedEvent = (OfferLoaded) event;
      auditEvent(typedEvent);
      auditInvocation(sessionRecovery, "sessionRecovery", "offer", typedEvent);
      sessionRecovery.offer(typedEvent);
    } else if (event instanceof Requested) {
      Requested typedEvent = (Requested) event;
      auditEvent(typedEvent);
      auditInvocation(sessionRecovery, "sessionRecovery", "request", typedEvent);
      sessionRecovery.request(typedEvent);
    }
  }

  public void triggerCalculation() {
    buffering = false;
    String typedEvent = "No event information - buffered dispatch";
    if (guardCheck_auditInstallation()) {
      auditInvocation(auditInstallation, "auditInstallation", "recomputeOnStateChange", typedEvent);
      isDirty_auditInstallation = auditInstallation.recomputeOnStateChange();
    }
    if (guardCheck_pairing()) {
      auditInvocation(pairing, "pairing", "recomputeOnStateChange", typedEvent);
      isDirty_pairing = pairing.recomputeOnStateChange();
    }
    if (guardCheck_coverageClaim()) {
      auditInvocation(coverageClaim, "coverageClaim", "recomputeOnStateChange", typedEvent);
      coverageClaim.recomputeOnStateChange();
    }
    if (guardCheck_pairingQualifier()) {
      auditInvocation(pairingQualifier, "pairingQualifier", "onPairChanged", typedEvent);
      pairingQualifier.onPairChanged();
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
    auditor.nodeRegistered(callbackDispatcher, "callbackDispatcher");
    auditor.nodeRegistered(subscriptionManager, "subscriptionManager");
    auditor.nodeRegistered(context, "context");
    auditor.nodeRegistered(activeProject, "activeProject");
    auditor.nodeRegistered(auditInstallation, "auditInstallation");
    auditor.nodeRegistered(coverageClaim, "coverageClaim");
    auditor.nodeRegistered(designSession, "designSession");
    auditor.nodeRegistered(effectOutcomes, "effectOutcomes");
    auditor.nodeRegistered(effectQueue, "effectQueue");
    auditor.nodeRegistered(ignoredParameters, "ignoredParameters");
    auditor.nodeRegistered(logArrival, "logArrival");
    auditor.nodeRegistered(logOpening, "logOpening");
    auditor.nodeRegistered(openGraph, "openGraph");
    auditor.nodeRegistered(openLog, "openLog");
    auditor.nodeRegistered(operationGate, "operationGate");
    auditor.nodeRegistered(pairingQualifier, "pairingQualifier");
    auditor.nodeRegistered(pairing, "pairing");
    auditor.nodeRegistered(sessionBoundary, "sessionBoundary");
    auditor.nodeRegistered(sessionRecovery, "sessionRecovery");
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
    isDirty_activeProject = false;
    isDirty_auditInstallation = false;
    isDirty_openGraph = false;
    isDirty_openLog = false;
    isDirty_operationGate = false;
    isDirty_pairing = false;
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
    effectQueue.performRequestedEffects();
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
      dirtyFlagSupplierMap.put(auditInstallation, () -> isDirty_auditInstallation);
      dirtyFlagSupplierMap.put(openGraph, () -> isDirty_openGraph);
      dirtyFlagSupplierMap.put(openLog, () -> isDirty_openLog);
      dirtyFlagSupplierMap.put(operationGate, () -> isDirty_operationGate);
      dirtyFlagSupplierMap.put(pairing, () -> isDirty_pairing);
    }
    return dirtyFlagSupplierMap.getOrDefault(node, DataFlow.ALWAYS_FALSE);
  }

  @Override
  public void setDirty(Object node, boolean dirtyFlag) {
    if (dirtyFlagUpdateMap.isEmpty()) {
      dirtyFlagUpdateMap.put(activeProject, (b) -> isDirty_activeProject = b);
      dirtyFlagUpdateMap.put(auditInstallation, (b) -> isDirty_auditInstallation = b);
      dirtyFlagUpdateMap.put(openGraph, (b) -> isDirty_openGraph = b);
      dirtyFlagUpdateMap.put(openLog, (b) -> isDirty_openLog = b);
      dirtyFlagUpdateMap.put(operationGate, (b) -> isDirty_operationGate = b);
      dirtyFlagUpdateMap.put(pairing, (b) -> isDirty_pairing = b);
    }
    dirtyFlagUpdateMap.get(node).accept(dirtyFlag);
  }

  private boolean guardCheck_activeProject() {
    return isDirty_operationGate;
  }

  private boolean guardCheck_auditInstallation() {
    return isDirty_openGraph;
  }

  private boolean guardCheck_coverageClaim() {
    return isDirty_auditInstallation | isDirty_openGraph | isDirty_openLog | isDirty_pairing;
  }

  private boolean guardCheck_designSession() {
    return isDirty_operationGate;
  }

  private boolean guardCheck_effectOutcomes() {
    return isDirty_operationGate;
  }

  private boolean guardCheck_logArrival() {
    return isDirty_openGraph | isDirty_operationGate | isDirty_pairing;
  }

  private boolean guardCheck_logOpening() {
    return isDirty_operationGate;
  }

  private boolean guardCheck_openGraph() {
    return isDirty_operationGate;
  }

  private boolean guardCheck_openLog() {
    return isDirty_operationGate;
  }

  private boolean guardCheck_pairingQualifier() {
    return isDirty_openGraph | isDirty_openLog | isDirty_pairing;
  }

  private boolean guardCheck_pairing() {
    return isDirty_openGraph | isDirty_openLog;
  }

  private boolean guardCheck_sessionBoundary() {
    return isDirty_activeProject | isDirty_openGraph | isDirty_openLog | isDirty_operationGate;
  }

  private boolean guardCheck_sessionRecovery() {
    return isDirty_operationGate;
  }

  /**
   * M50/W4 — nodes resolved by a generated switch, not by a populated map: registering them would
   * publish every node into the auditor's HashMaps and stop the graph being dissolved.
   */
  @SuppressWarnings("unchecked")
  @Override
  public <T> T getInstanceById(String id) throws NoSuchFieldException {
    switch (id) {
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
      case "activeProject":
        return (T) activeProject;
      case "auditInstallation":
        return (T) auditInstallation;
      case "coverageClaim":
        return (T) coverageClaim;
      case "designSession":
        return (T) designSession;
      case "effectOutcomes":
        return (T) effectOutcomes;
      case "effectQueue":
        return (T) effectQueue;
      case "ignoredParameters":
        return (T) ignoredParameters;
      case "logArrival":
        return (T) logArrival;
      case "logOpening":
        return (T) logOpening;
      case "openGraph":
        return (T) openGraph;
      case "openLog":
        return (T) openLog;
      case "operationGate":
        return (T) operationGate;
      case "pairingQualifier":
        return (T) pairingQualifier;
      case "pairing":
        return (T) pairing;
      case "sessionBoundary":
        return (T) sessionBoundary;
      case "sessionRecovery":
        return (T) sessionRecovery;
      default:
        throw new NoSuchFieldException(id);
    }
  }

  /** M50/W4 — the reverse direction, also generated. */
  @Override
  public String lookupInstanceName(Object node) {
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
    if (node == activeProject) {
      return "activeProject";
    }
    if (node == auditInstallation) {
      return "auditInstallation";
    }
    if (node == coverageClaim) {
      return "coverageClaim";
    }
    if (node == designSession) {
      return "designSession";
    }
    if (node == effectOutcomes) {
      return "effectOutcomes";
    }
    if (node == effectQueue) {
      return "effectQueue";
    }
    if (node == ignoredParameters) {
      return "ignoredParameters";
    }
    if (node == logArrival) {
      return "logArrival";
    }
    if (node == logOpening) {
      return "logOpening";
    }
    if (node == openGraph) {
      return "openGraph";
    }
    if (node == openLog) {
      return "openLog";
    }
    if (node == operationGate) {
      return "operationGate";
    }
    if (node == pairingQualifier) {
      return "pairingQualifier";
    }
    if (node == pairing) {
      return "pairing";
    }
    if (node == sessionBoundary) {
      return "sessionBoundary";
    }
    if (node == sessionRecovery) {
      return "sessionRecovery";
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
