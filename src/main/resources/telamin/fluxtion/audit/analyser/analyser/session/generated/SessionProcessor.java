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
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ActionListenerConfigured;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AiClientsObserved;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AiSetupAlsoTurnOffChosen;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AiSetupBack;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AiSetupBuildModeAnswered;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AiSetupCancelled;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AiSetupCheckAgain;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AiSetupChoiceMade;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AiSetupClientChosen;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AiSetupContinue;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AiSetupCopyRequested;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AiSetupFirstRunDismissed;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AiSetupMoreOptions;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AiSetupPostureChosen;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AiSetupRegisterRequested;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AiSetupRequested;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AiSetupStartupObserved;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AiSetupSuggestedTaken;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AiSetupTimerFired;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AssistantActionFinished;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AssistantCancelRequested;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AssistantCompletionFailed;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AssistantCompletionReceived;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AssistantContextFailed;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AssistantContextPrepared;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AssistantDraftComposed;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AssistantDraftPending;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AssistantEffectStarted;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AssistantFileExchangeObserved;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AssistantFullProfileAnswered;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AssistantHandoffRequested;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AssistantHostRequested;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AssistantHostShown;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AssistantModeChosen;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AssistantNewChatRequested;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AssistantOpenApplied;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AssistantOpenApplyFailed;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AssistantProfileApplied;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AssistantProfileChosen;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AssistantProfileObserved;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AssistantProfilesShown;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AssistantSendRequested;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AssistantTabShown;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.BridgeObserved;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.BundleCaptureRequested;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.BundleDeleted;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.BundleRootsRestored;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.BundleWriteFailed;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.BundleWritten;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.CanvasLeftAssistant;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.CaptureStarted;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ClaudeAuthObserved;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ClaudeObserved;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.CloseRequested;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.DraftPlaced;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.EffectFailed;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ExampleDeadlinePassed;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ExampleDownloadConfirmed;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ExampleDownloadRequested;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ExampleEffectStarted;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ExampleFetched;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ExampleUnpacked;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ExternalAccessAnswered;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ExternalAccessChosen;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ExternalAccessRequested;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ExternalClientSeen;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.FollowSet;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.FollowToggled;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.GraphCleared;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.GraphClosed;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.GraphOpened;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.HostedCallAbandoned;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.HostedCallPresented;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.HostedCallRefusedByServer;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.HostedReportWritten;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentAskAgainRequested;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentBridgeServed;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentCallAdmissionRequested;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentConsentChanged;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentCostObserved;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentDiagnostics;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentDraftComposed;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentEffectStarted;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentExited;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentFolderAnswered;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentGrantExpired;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentHookObserved;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentInitObserved;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentInitialized;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentInterruptAcked;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentModeObserved;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentNewChatRequested;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentNoted;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentPermissionAnswered;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentPermissionCancelled;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentPermissionChosen;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentPermissionDeadline;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentPermissionRequested;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentRestartAnswered;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentRestartOfferRequested;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentSendRequested;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentSetupDirectoryChosen;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentSpawned;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentStartDeadline;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentStartRequested;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentStatusObserved;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentStopDeadline;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentStopRequested;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentSwitchChosen;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentTextUpdated;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentTurnEnded;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentTurnStarted;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LogAppended;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LogCleared;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LogClosed;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LogContentObserved;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LogIdentityObserved;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LogOpenFailed;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LogOpened;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.McpReadinessObserved;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.McpRegistrationChanged;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.McpRegistrationObserved;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.MembershipCompared;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.OpenLogRequested;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.OpenProjectRequested;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.OpenRequestReceived;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.Pending;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ProducerFindingsObserved;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ProfileApplied;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ProfileLoaded;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ProjectReopenPresented;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ProjectReopenReady;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ProjectReopenRequested;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ProposalApplyRequested;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ProposalChecked;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ProposalDismissed;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ProposalEffectStarted;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ProposalItemAnswered;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ProposalItemApplied;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ProposalItemPreviewed;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ProposalPreviewDone;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ProposalReceived;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.RecordFlagsChanged;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.RecordsViewInstalled;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.RecordsViewMeasured;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.RegisterAnswered;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.RestSwitchObserved;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ReturnToMainOptions;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ScanScheduled;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.SettingsRestored;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.SkillsVendored;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.SourceRootsObserved;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.StartPageAvailability;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.StartPageEffectStarted;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.StartPageFlowDone;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.StartPageNoLogAnswered;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.StartPagePromptSent;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.StartPromptPackDropped;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.StarterCatalogueObserved;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.StarterChosen;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.StarterConfigured;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.StarterDefaultsObserved;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.StarterDesignOpened;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.StarterDestinationChosen;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.StarterDirectoryObserved;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.StarterDiscoveryObserved;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.StarterEffectStarted;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.StarterGraphOfferAnswered;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.StarterGraphmlAppeared;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.StarterInstallFailed;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.StarterInstalled;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.StarterMenuRequested;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.StarterProjectOpened;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.StarterReviewAnswered;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.StatusShown;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.TimeOrderObserved;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ViewFilterChanged;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.WalkAcknowledged;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.WalkDefinitionChanged;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.WalkEndRequested;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.WalkNavigated;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.WalkPlayRequested;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.WalkStepPrepared;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.WalkTargetsLit;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.WalkViewApplied;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.WalkViewChanged;
import telamin.fluxtion.audit.analyser.analyser.session.node.ActionListener;
import telamin.fluxtion.audit.analyser.analyser.session.node.ActiveProject;
import telamin.fluxtion.audit.analyser.analyser.session.node.AiSetup;
import telamin.fluxtion.audit.analyser.analyser.session.node.AssistantLoop;
import telamin.fluxtion.audit.analyser.analyser.session.node.AssistantModeChoice;
import telamin.fluxtion.audit.analyser.analyser.session.node.AuditInstallation;
import telamin.fluxtion.audit.analyser.analyser.session.node.BundleAnchor;
import telamin.fluxtion.audit.analyser.analyser.session.node.CoverageClaim;
import telamin.fluxtion.audit.analyser.analyser.session.node.DesignSession;
import telamin.fluxtion.audit.analyser.analyser.session.node.EffectOutcomes;
import telamin.fluxtion.audit.analyser.analyser.session.node.EffectQueue;
import telamin.fluxtion.audit.analyser.analyser.session.node.EvidenceCapture;
import telamin.fluxtion.audit.analyser.analyser.session.node.ExampleDownload;
import telamin.fluxtion.audit.analyser.analyser.session.node.IgnoredParameters;
import telamin.fluxtion.audit.analyser.analyser.session.node.LocalAgentPermission;
import telamin.fluxtion.audit.analyser.analyser.session.node.LocalAgentSession;
import telamin.fluxtion.audit.analyser.analyser.session.node.LogArrival;
import telamin.fluxtion.audit.analyser.analyser.session.node.LogEvidence;
import telamin.fluxtion.audit.analyser.analyser.session.node.LogOpening;
import telamin.fluxtion.audit.analyser.analyser.session.node.OnboardingProposal;
import telamin.fluxtion.audit.analyser.analyser.session.node.OpenBundle;
import telamin.fluxtion.audit.analyser.analyser.session.node.OpenGraph;
import telamin.fluxtion.audit.analyser.analyser.session.node.OpenLog;
import telamin.fluxtion.audit.analyser.analyser.session.node.OperationGate;
import telamin.fluxtion.audit.analyser.analyser.session.node.Pairing;
import telamin.fluxtion.audit.analyser.analyser.session.node.PairingQualifier;
import telamin.fluxtion.audit.analyser.analyser.session.node.ProjectReopenOffer;
import telamin.fluxtion.audit.analyser.analyser.session.node.RecordsView;
import telamin.fluxtion.audit.analyser.analyser.session.node.SessionBoundary;
import telamin.fluxtion.audit.analyser.analyser.session.node.SessionRecovery;
import telamin.fluxtion.audit.analyser.analyser.session.node.StartPageNoLogHint;
import telamin.fluxtion.audit.analyser.analyser.session.node.StartPagePrompt;
import telamin.fluxtion.audit.analyser.analyser.session.node.StarterProject;
import telamin.fluxtion.audit.analyser.analyser.session.node.WalkPlayback;
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
 * api version               : 1.1.0
 * analyser version          : 1.0.76
 * target generator version  : 1.0.79
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
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ActionListenerConfigured
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AiClientsObserved
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AiSetupAlsoTurnOffChosen
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AiSetupBack
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AiSetupBuildModeAnswered
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AiSetupCancelled
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AiSetupCheckAgain
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AiSetupChoiceMade
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AiSetupClientChosen
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AiSetupContinue
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AiSetupCopyRequested
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AiSetupFirstRunDismissed
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AiSetupMoreOptions
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AiSetupPostureChosen
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AiSetupRegisterRequested
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AiSetupRequested
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AiSetupStartupObserved
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AiSetupSuggestedTaken
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AiSetupTimerFired
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AssistantActionFinished
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AssistantCancelRequested
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AssistantCompletionFailed
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AssistantCompletionReceived
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AssistantContextFailed
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AssistantContextPrepared
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AssistantDraftComposed
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AssistantDraftPending
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AssistantEffectStarted
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AssistantFileExchangeObserved
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AssistantFullProfileAnswered
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AssistantHandoffRequested
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AssistantHostRequested
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AssistantHostShown
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AssistantModeChosen
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AssistantNewChatRequested
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AssistantOpenApplied
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AssistantOpenApplyFailed
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AssistantProfileApplied
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AssistantProfileChosen
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AssistantProfileObserved
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AssistantProfilesShown
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AssistantSendRequested
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AssistantTabShown
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.BridgeObserved
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.BundleCaptureRequested
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.BundleDeleted
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.BundleRootsRestored
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.BundleWriteFailed
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.BundleWritten
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.CanvasLeftAssistant
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.CaptureStarted
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ClaudeAuthObserved
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ClaudeObserved
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.CloseRequested
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.DraftPlaced
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.EffectFailed
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ExampleDeadlinePassed
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ExampleDownloadConfirmed
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ExampleDownloadRequested
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ExampleEffectStarted
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ExampleFetched
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ExampleUnpacked
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ExternalAccessAnswered
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ExternalAccessChosen
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ExternalAccessRequested
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ExternalClientSeen
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.FollowSet
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.FollowToggled
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.GraphCleared
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.GraphClosed
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.GraphOpened
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.HostedCallAbandoned
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.HostedCallPresented
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.HostedCallRefusedByServer
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.HostedReportWritten
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentAskAgainRequested
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentBridgeServed
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentCallAdmissionRequested
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentConsentChanged
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentCostObserved
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentDiagnostics
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentDraftComposed
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentEffectStarted
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentExited
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentFolderAnswered
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentGrantExpired
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentHookObserved
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentInitObserved
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentInitialized
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentInterruptAcked
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentModeObserved
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentNewChatRequested
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentNoted
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentPermissionAnswered
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentPermissionCancelled
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentPermissionChosen
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentPermissionDeadline
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentPermissionRequested
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentRestartAnswered
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentRestartOfferRequested
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentSendRequested
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentSetupDirectoryChosen
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentSpawned
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentStartDeadline
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentStartRequested
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentStatusObserved
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentStopDeadline
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentStopRequested
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentSwitchChosen
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentTextUpdated
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentTurnEnded
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentTurnStarted
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LogAppended
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LogCleared
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LogClosed
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LogContentObserved
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LogIdentityObserved
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LogOpenFailed
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LogOpened
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.McpReadinessObserved
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.McpRegistrationChanged
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.McpRegistrationObserved
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.MembershipCompared
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.OpenLogRequested
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.OpenProjectRequested
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.OpenRequestReceived
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.Pending
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ProducerFindingsObserved
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ProfileApplied
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ProfileLoaded
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ProjectReopenPresented
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ProjectReopenReady
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ProjectReopenRequested
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ProposalApplyRequested
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ProposalChecked
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ProposalDismissed
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ProposalEffectStarted
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ProposalItemAnswered
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ProposalItemApplied
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ProposalItemPreviewed
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ProposalPreviewDone
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ProposalReceived
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.RecordFlagsChanged
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.RecordsViewInstalled
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.RecordsViewMeasured
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.RegisterAnswered
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.RestSwitchObserved
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ReturnToMainOptions
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ScanScheduled
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.SettingsRestored
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.SkillsVendored
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.SourceRootsObserved
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.StartPageAvailability
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.StartPageEffectStarted
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.StartPageFlowDone
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.StartPageNoLogAnswered
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.StartPagePromptSent
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.StartPromptPackDropped
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.StarterCatalogueObserved
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.StarterChosen
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.StarterConfigured
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.StarterDefaultsObserved
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.StarterDesignOpened
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.StarterDestinationChosen
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.StarterDirectoryObserved
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.StarterDiscoveryObserved
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.StarterEffectStarted
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.StarterGraphOfferAnswered
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.StarterGraphmlAppeared
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.StarterInstallFailed
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.StarterInstalled
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.StarterMenuRequested
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.StarterProjectOpened
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.StarterReviewAnswered
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.StatusShown
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.TimeOrderObserved
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ViewFilterChanged
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.WalkAcknowledged
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.WalkDefinitionChanged
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.WalkEndRequested
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.WalkNavigated
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.WalkPlayRequested
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.WalkStepPrepared
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.WalkTargetsLit
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.WalkViewApplied
 *   <li>telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.WalkViewChanged
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
  public final transient AssistantModeChoice assistantMode = new AssistantModeChoice();
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
  public final transient OpenBundle openBundle =
      new telamin.fluxtion.audit.analyser.analyser.session.node.OpenBundle(operationGate);;
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
  public final transient ActionListener actionListener =
      new telamin.fluxtion.audit.analyser.analyser.session.node.ActionListener(effectQueue);;
  public final transient BundleAnchor bundleAnchor =
      new telamin.fluxtion.audit.analyser.analyser.session.node.BundleAnchor(
          openBundle, effectQueue);;
  public final transient EvidenceCapture evidenceCapture =
      new telamin.fluxtion.audit.analyser.analyser.session.node.EvidenceCapture(
          openLog, operationGate, effectQueue);;
  public final transient ExampleDownload exampleDownload =
      new telamin.fluxtion.audit.analyser.analyser.session.node.ExampleDownload(effectQueue);;
  public final transient LocalAgentSession localAgentSession =
      new telamin.fluxtion.audit.analyser.analyser.session.node.LocalAgentSession(
          assistantMode, effectQueue);;
  public final transient LocalAgentPermission localAgentPermission =
      new telamin.fluxtion.audit.analyser.analyser.session.node.LocalAgentPermission(
          localAgentSession, effectQueue);;
  public final transient LogArrival logArrival =
      new telamin.fluxtion.audit.analyser.analyser.session.node.LogArrival(
          operationGate, pairing, openGraph, effectQueue);;
  public final transient LogEvidence logEvidence =
      new telamin.fluxtion.audit.analyser.analyser.session.node.LogEvidence(openLog, effectQueue);;
  public final transient LogOpening logOpening =
      new telamin.fluxtion.audit.analyser.analyser.session.node.LogOpening(
          operationGate, effectQueue);;
  public final transient ProjectReopenOffer projectReopenOffer =
      new telamin.fluxtion.audit.analyser.analyser.session.node.ProjectReopenOffer(
          openBundle, effectQueue);;
  public final transient RecordsView recordsView =
      new telamin.fluxtion.audit.analyser.analyser.session.node.RecordsView(openLog, effectQueue);;
  public final transient AssistantLoop assistantLoop =
      new telamin.fluxtion.audit.analyser.analyser.session.node.AssistantLoop(
          openLog, openGraph, activeProject, operationGate, effectQueue);;
  public final transient ServiceRegistryNode serviceRegistry = new ServiceRegistryNode();
  public final transient StartPageNoLogHint startPageNoLogHint =
      new telamin.fluxtion.audit.analyser.analyser.session.node.StartPageNoLogHint(
          openLog, effectQueue);;
  public final transient AiSetup aiSetup =
      new telamin.fluxtion.audit.analyser.analyser.session.node.AiSetup(
          localAgentSession, assistantLoop, effectQueue);;
  public final transient StartPagePrompt startPagePrompt =
      new telamin.fluxtion.audit.analyser.analyser.session.node.StartPagePrompt(
          assistantMode, localAgentSession, openLog, effectQueue);;
  public final transient StarterProject starterProject =
      new telamin.fluxtion.audit.analyser.analyser.session.node.StarterProject(effectQueue);;
  public final transient OnboardingProposal onboardingProposal =
      new telamin.fluxtion.audit.analyser.analyser.session.node.OnboardingProposal(
          localAgentSession, effectQueue, starterProject);;
  public final transient SessionBoundary sessionBoundary =
      new telamin.fluxtion.audit.analyser.analyser.session.node.SessionBoundary(
          operationGate, activeProject, openLog, openGraph, effectQueue);;
  public final transient WalkPlayback walkPlayback =
      new telamin.fluxtion.audit.analyser.analyser.session.node.WalkPlayback(
          openLog, assistantLoop, effectQueue);;
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
      new IdentityHashMap<>(17);
  private final transient IdentityHashMap<Object, Consumer<Boolean>> dirtyFlagUpdateMap =
      new IdentityHashMap<>(17);

  private boolean isDirty_actionListener = false;
  private boolean isDirty_activeProject = false;
  private boolean isDirty_aiSetup = false;
  private boolean isDirty_assistantLoop = false;
  private boolean isDirty_assistantMode = false;
  private boolean isDirty_auditInstallation = false;
  private boolean isDirty_localAgentPermission = false;
  private boolean isDirty_localAgentSession = false;
  private boolean isDirty_onboardingProposal = false;
  private boolean isDirty_openBundle = false;
  private boolean isDirty_openGraph = false;
  private boolean isDirty_openLog = false;
  private boolean isDirty_operationGate = false;
  private boolean isDirty_pairing = false;
  private boolean isDirty_recordsView = false;
  private boolean isDirty_startPageNoLogHint = false;
  private boolean isDirty_starterProject = false;

  //Forked declarations

  //Filter constants

  //Self-description of the embeddable surface — see ProcessorDescriptor
  private static final ProcessorDescriptor DESCRIPTOR =
      DescriptorSupport.of(
          SessionProcessor.class,
          SessionProcessor::new,
          new ProcessorDescriptor.Input[] {
            new ProcessorDescriptor.Input(
                "ActionListenerConfigured",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ActionListenerConfigured",
                false),
            new ProcessorDescriptor.Input(
                "Activated",
                "telamin.fluxtion.audit.analyser.analyser.session.resume.ResumeEvents.Activated",
                false),
            new ProcessorDescriptor.Input(
                "AiClientsObserved",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AiClientsObserved",
                false),
            new ProcessorDescriptor.Input(
                "AiSetupAlsoTurnOffChosen",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AiSetupAlsoTurnOffChosen",
                false),
            new ProcessorDescriptor.Input(
                "AiSetupBack",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AiSetupBack",
                false),
            new ProcessorDescriptor.Input(
                "AiSetupBuildModeAnswered",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AiSetupBuildModeAnswered",
                false),
            new ProcessorDescriptor.Input(
                "AiSetupCancelled",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AiSetupCancelled",
                false),
            new ProcessorDescriptor.Input(
                "AiSetupCheckAgain",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AiSetupCheckAgain",
                false),
            new ProcessorDescriptor.Input(
                "AiSetupChoiceMade",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AiSetupChoiceMade",
                false),
            new ProcessorDescriptor.Input(
                "AiSetupClientChosen",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AiSetupClientChosen",
                false),
            new ProcessorDescriptor.Input(
                "AiSetupContinue",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AiSetupContinue",
                false),
            new ProcessorDescriptor.Input(
                "AiSetupCopyRequested",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AiSetupCopyRequested",
                false),
            new ProcessorDescriptor.Input(
                "AiSetupFirstRunDismissed",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AiSetupFirstRunDismissed",
                false),
            new ProcessorDescriptor.Input(
                "AiSetupMoreOptions",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AiSetupMoreOptions",
                false),
            new ProcessorDescriptor.Input(
                "AiSetupPostureChosen",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AiSetupPostureChosen",
                false),
            new ProcessorDescriptor.Input(
                "AiSetupRegisterRequested",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AiSetupRegisterRequested",
                false),
            new ProcessorDescriptor.Input(
                "AiSetupRequested",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AiSetupRequested",
                false),
            new ProcessorDescriptor.Input(
                "AiSetupStartupObserved",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AiSetupStartupObserved",
                false),
            new ProcessorDescriptor.Input(
                "AiSetupSuggestedTaken",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AiSetupSuggestedTaken",
                false),
            new ProcessorDescriptor.Input(
                "AiSetupTimerFired",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AiSetupTimerFired",
                false),
            new ProcessorDescriptor.Input(
                "AssistantActionFinished",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AssistantActionFinished",
                false),
            new ProcessorDescriptor.Input(
                "AssistantCancelRequested",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AssistantCancelRequested",
                false),
            new ProcessorDescriptor.Input(
                "AssistantCompletionFailed",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AssistantCompletionFailed",
                false),
            new ProcessorDescriptor.Input(
                "AssistantCompletionReceived",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AssistantCompletionReceived",
                false),
            new ProcessorDescriptor.Input(
                "AssistantContextFailed",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AssistantContextFailed",
                false),
            new ProcessorDescriptor.Input(
                "AssistantContextPrepared",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AssistantContextPrepared",
                false),
            new ProcessorDescriptor.Input(
                "AssistantDraftComposed",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AssistantDraftComposed",
                false),
            new ProcessorDescriptor.Input(
                "AssistantDraftPending",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AssistantDraftPending",
                false),
            new ProcessorDescriptor.Input(
                "AssistantEffectStarted",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AssistantEffectStarted",
                false),
            new ProcessorDescriptor.Input(
                "AssistantFileExchangeObserved",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AssistantFileExchangeObserved",
                false),
            new ProcessorDescriptor.Input(
                "AssistantFullProfileAnswered",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AssistantFullProfileAnswered",
                false),
            new ProcessorDescriptor.Input(
                "AssistantHandoffRequested",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AssistantHandoffRequested",
                false),
            new ProcessorDescriptor.Input(
                "AssistantHostRequested",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AssistantHostRequested",
                false),
            new ProcessorDescriptor.Input(
                "AssistantHostShown",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AssistantHostShown",
                false),
            new ProcessorDescriptor.Input(
                "AssistantModeChosen",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AssistantModeChosen",
                false),
            new ProcessorDescriptor.Input(
                "AssistantNewChatRequested",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AssistantNewChatRequested",
                false),
            new ProcessorDescriptor.Input(
                "AssistantOpenApplied",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AssistantOpenApplied",
                false),
            new ProcessorDescriptor.Input(
                "AssistantOpenApplyFailed",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AssistantOpenApplyFailed",
                false),
            new ProcessorDescriptor.Input(
                "AssistantProfileApplied",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AssistantProfileApplied",
                false),
            new ProcessorDescriptor.Input(
                "AssistantProfileChosen",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AssistantProfileChosen",
                false),
            new ProcessorDescriptor.Input(
                "AssistantProfileObserved",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AssistantProfileObserved",
                false),
            new ProcessorDescriptor.Input(
                "AssistantProfilesShown",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AssistantProfilesShown",
                false),
            new ProcessorDescriptor.Input(
                "AssistantSendRequested",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AssistantSendRequested",
                false),
            new ProcessorDescriptor.Input(
                "AssistantTabShown",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AssistantTabShown",
                false),
            new ProcessorDescriptor.Input(
                "BridgeObserved",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.BridgeObserved",
                false),
            new ProcessorDescriptor.Input(
                "BundleCaptureRequested",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.BundleCaptureRequested",
                false),
            new ProcessorDescriptor.Input(
                "BundleDeleted",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.BundleDeleted",
                false),
            new ProcessorDescriptor.Input(
                "BundleRootsRestored",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.BundleRootsRestored",
                false),
            new ProcessorDescriptor.Input(
                "BundleWriteFailed",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.BundleWriteFailed",
                false),
            new ProcessorDescriptor.Input(
                "BundleWritten",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.BundleWritten",
                false),
            new ProcessorDescriptor.Input(
                "CanvasLeftAssistant",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.CanvasLeftAssistant",
                false),
            new ProcessorDescriptor.Input(
                "CaptureStarted",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.CaptureStarted",
                false),
            new ProcessorDescriptor.Input(
                "Checked",
                "telamin.fluxtion.audit.analyser.analyser.session.resume.ResumeEvents.Checked",
                false),
            new ProcessorDescriptor.Input(
                "ClaudeAuthObserved",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ClaudeAuthObserved",
                false),
            new ProcessorDescriptor.Input(
                "ClaudeObserved",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ClaudeObserved",
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
                "DraftPlaced",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.DraftPlaced",
                false),
            new ProcessorDescriptor.Input(
                "EffectFailed",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.EffectFailed",
                false),
            new ProcessorDescriptor.Input(
                "ExampleDeadlinePassed",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ExampleDeadlinePassed",
                false),
            new ProcessorDescriptor.Input(
                "ExampleDownloadConfirmed",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ExampleDownloadConfirmed",
                false),
            new ProcessorDescriptor.Input(
                "ExampleDownloadRequested",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ExampleDownloadRequested",
                false),
            new ProcessorDescriptor.Input(
                "ExampleEffectStarted",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ExampleEffectStarted",
                false),
            new ProcessorDescriptor.Input(
                "ExampleFetched",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ExampleFetched",
                false),
            new ProcessorDescriptor.Input(
                "ExampleUnpacked",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ExampleUnpacked",
                false),
            new ProcessorDescriptor.Input(
                "ExternalAccessAnswered",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ExternalAccessAnswered",
                false),
            new ProcessorDescriptor.Input(
                "ExternalAccessChosen",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ExternalAccessChosen",
                false),
            new ProcessorDescriptor.Input(
                "ExternalAccessRequested",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ExternalAccessRequested",
                false),
            new ProcessorDescriptor.Input(
                "ExternalClientSeen",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ExternalClientSeen",
                false),
            new ProcessorDescriptor.Input(
                "Finished",
                "telamin.fluxtion.audit.analyser.analyser.session.resume.ResumeEvents.Finished",
                false),
            new ProcessorDescriptor.Input(
                "FollowSet",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.FollowSet",
                false),
            new ProcessorDescriptor.Input(
                "FollowToggled",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.FollowToggled",
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
                "HostedCallAbandoned",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.HostedCallAbandoned",
                false),
            new ProcessorDescriptor.Input(
                "HostedCallPresented",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.HostedCallPresented",
                false),
            new ProcessorDescriptor.Input(
                "HostedCallRefusedByServer",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.HostedCallRefusedByServer",
                false),
            new ProcessorDescriptor.Input(
                "HostedReportWritten",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.HostedReportWritten",
                false),
            new ProcessorDescriptor.Input(
                "LocalAgentAskAgainRequested",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentAskAgainRequested",
                false),
            new ProcessorDescriptor.Input(
                "LocalAgentBridgeServed",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentBridgeServed",
                false),
            new ProcessorDescriptor.Input(
                "LocalAgentCallAdmissionRequested",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentCallAdmissionRequested",
                false),
            new ProcessorDescriptor.Input(
                "LocalAgentConsentChanged",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentConsentChanged",
                false),
            new ProcessorDescriptor.Input(
                "LocalAgentCostObserved",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentCostObserved",
                false),
            new ProcessorDescriptor.Input(
                "LocalAgentDiagnostics",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentDiagnostics",
                false),
            new ProcessorDescriptor.Input(
                "LocalAgentDraftComposed",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentDraftComposed",
                false),
            new ProcessorDescriptor.Input(
                "LocalAgentEffectStarted",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentEffectStarted",
                false),
            new ProcessorDescriptor.Input(
                "LocalAgentExited",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentExited",
                false),
            new ProcessorDescriptor.Input(
                "LocalAgentFolderAnswered",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentFolderAnswered",
                false),
            new ProcessorDescriptor.Input(
                "LocalAgentGrantExpired",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentGrantExpired",
                false),
            new ProcessorDescriptor.Input(
                "LocalAgentHookObserved",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentHookObserved",
                false),
            new ProcessorDescriptor.Input(
                "LocalAgentInitObserved",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentInitObserved",
                false),
            new ProcessorDescriptor.Input(
                "LocalAgentInitialized",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentInitialized",
                false),
            new ProcessorDescriptor.Input(
                "LocalAgentInterruptAcked",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentInterruptAcked",
                false),
            new ProcessorDescriptor.Input(
                "LocalAgentModeObserved",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentModeObserved",
                false),
            new ProcessorDescriptor.Input(
                "LocalAgentNewChatRequested",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentNewChatRequested",
                false),
            new ProcessorDescriptor.Input(
                "LocalAgentNoted",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentNoted",
                false),
            new ProcessorDescriptor.Input(
                "LocalAgentPermissionAnswered",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentPermissionAnswered",
                false),
            new ProcessorDescriptor.Input(
                "LocalAgentPermissionCancelled",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentPermissionCancelled",
                false),
            new ProcessorDescriptor.Input(
                "LocalAgentPermissionChosen",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentPermissionChosen",
                false),
            new ProcessorDescriptor.Input(
                "LocalAgentPermissionDeadline",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentPermissionDeadline",
                false),
            new ProcessorDescriptor.Input(
                "LocalAgentPermissionRequested",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentPermissionRequested",
                false),
            new ProcessorDescriptor.Input(
                "LocalAgentRestartAnswered",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentRestartAnswered",
                false),
            new ProcessorDescriptor.Input(
                "LocalAgentRestartOfferRequested",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentRestartOfferRequested",
                false),
            new ProcessorDescriptor.Input(
                "LocalAgentSendRequested",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentSendRequested",
                false),
            new ProcessorDescriptor.Input(
                "LocalAgentSetupDirectoryChosen",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentSetupDirectoryChosen",
                false),
            new ProcessorDescriptor.Input(
                "LocalAgentSpawned",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentSpawned",
                false),
            new ProcessorDescriptor.Input(
                "LocalAgentStartDeadline",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentStartDeadline",
                false),
            new ProcessorDescriptor.Input(
                "LocalAgentStartRequested",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentStartRequested",
                false),
            new ProcessorDescriptor.Input(
                "LocalAgentStatusObserved",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentStatusObserved",
                false),
            new ProcessorDescriptor.Input(
                "LocalAgentStopDeadline",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentStopDeadline",
                false),
            new ProcessorDescriptor.Input(
                "LocalAgentStopRequested",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentStopRequested",
                false),
            new ProcessorDescriptor.Input(
                "LocalAgentSwitchChosen",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentSwitchChosen",
                false),
            new ProcessorDescriptor.Input(
                "LocalAgentTextUpdated",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentTextUpdated",
                false),
            new ProcessorDescriptor.Input(
                "LocalAgentTurnEnded",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentTurnEnded",
                false),
            new ProcessorDescriptor.Input(
                "LocalAgentTurnStarted",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentTurnStarted",
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
                "LogContentObserved",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LogContentObserved",
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
                "McpReadinessObserved",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.McpReadinessObserved",
                false),
            new ProcessorDescriptor.Input(
                "McpRegistrationChanged",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.McpRegistrationChanged",
                false),
            new ProcessorDescriptor.Input(
                "McpRegistrationObserved",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.McpRegistrationObserved",
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
                "ProducerFindingsObserved",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ProducerFindingsObserved",
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
                "ProjectReopenPresented",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ProjectReopenPresented",
                false),
            new ProcessorDescriptor.Input(
                "ProjectReopenReady",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ProjectReopenReady",
                false),
            new ProcessorDescriptor.Input(
                "ProjectReopenRequested",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ProjectReopenRequested",
                false),
            new ProcessorDescriptor.Input(
                "ProposalApplyRequested",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ProposalApplyRequested",
                false),
            new ProcessorDescriptor.Input(
                "ProposalChecked",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ProposalChecked",
                false),
            new ProcessorDescriptor.Input(
                "ProposalDismissed",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ProposalDismissed",
                false),
            new ProcessorDescriptor.Input(
                "ProposalEffectStarted",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ProposalEffectStarted",
                false),
            new ProcessorDescriptor.Input(
                "ProposalItemAnswered",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ProposalItemAnswered",
                false),
            new ProcessorDescriptor.Input(
                "ProposalItemApplied",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ProposalItemApplied",
                false),
            new ProcessorDescriptor.Input(
                "ProposalItemPreviewed",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ProposalItemPreviewed",
                false),
            new ProcessorDescriptor.Input(
                "ProposalPreviewDone",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ProposalPreviewDone",
                false),
            new ProcessorDescriptor.Input(
                "ProposalReceived",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ProposalReceived",
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
                "RecordFlagsChanged",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.RecordFlagsChanged",
                false),
            new ProcessorDescriptor.Input(
                "RecordsViewInstalled",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.RecordsViewInstalled",
                false),
            new ProcessorDescriptor.Input(
                "RecordsViewMeasured",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.RecordsViewMeasured",
                false),
            new ProcessorDescriptor.Input(
                "RegisterAnswered",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.RegisterAnswered",
                false),
            new ProcessorDescriptor.Input(
                "Requested",
                "telamin.fluxtion.audit.analyser.analyser.session.resume.ResumeEvents.Requested",
                false),
            new ProcessorDescriptor.Input(
                "RestSwitchObserved",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.RestSwitchObserved",
                false),
            new ProcessorDescriptor.Input(
                "ResultReadCompleted",
                "telamin.fluxtion.audit.analyser.analyser.design.DesignEvents.ResultReadCompleted",
                false),
            new ProcessorDescriptor.Input(
                "ReturnToMainOptions",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ReturnToMainOptions",
                false),
            new ProcessorDescriptor.Input(
                "ScanScheduled",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ScanScheduled",
                false),
            new ProcessorDescriptor.Input(
                "SettingsRestored",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.SettingsRestored",
                false),
            new ProcessorDescriptor.Input(
                "SkillsVendored",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.SkillsVendored",
                false),
            new ProcessorDescriptor.Input(
                "SourceRootsObserved",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.SourceRootsObserved",
                false),
            new ProcessorDescriptor.Input(
                "StartPageAvailability",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.StartPageAvailability",
                false),
            new ProcessorDescriptor.Input(
                "StartPageEffectStarted",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.StartPageEffectStarted",
                false),
            new ProcessorDescriptor.Input(
                "StartPageFlowDone",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.StartPageFlowDone",
                false),
            new ProcessorDescriptor.Input(
                "StartPageNoLogAnswered",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.StartPageNoLogAnswered",
                false),
            new ProcessorDescriptor.Input(
                "StartPagePromptSent",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.StartPagePromptSent",
                false),
            new ProcessorDescriptor.Input(
                "StartPromptPackDropped",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.StartPromptPackDropped",
                false),
            new ProcessorDescriptor.Input(
                "StarterCatalogueObserved",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.StarterCatalogueObserved",
                false),
            new ProcessorDescriptor.Input(
                "StarterChosen",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.StarterChosen",
                false),
            new ProcessorDescriptor.Input(
                "StarterConfigured",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.StarterConfigured",
                false),
            new ProcessorDescriptor.Input(
                "StarterDefaultsObserved",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.StarterDefaultsObserved",
                false),
            new ProcessorDescriptor.Input(
                "StarterDesignOpened",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.StarterDesignOpened",
                false),
            new ProcessorDescriptor.Input(
                "StarterDestinationChosen",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.StarterDestinationChosen",
                false),
            new ProcessorDescriptor.Input(
                "StarterDirectoryObserved",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.StarterDirectoryObserved",
                false),
            new ProcessorDescriptor.Input(
                "StarterDiscoveryObserved",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.StarterDiscoveryObserved",
                false),
            new ProcessorDescriptor.Input(
                "StarterEffectStarted",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.StarterEffectStarted",
                false),
            new ProcessorDescriptor.Input(
                "StarterGraphOfferAnswered",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.StarterGraphOfferAnswered",
                false),
            new ProcessorDescriptor.Input(
                "StarterGraphmlAppeared",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.StarterGraphmlAppeared",
                false),
            new ProcessorDescriptor.Input(
                "StarterInstallFailed",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.StarterInstallFailed",
                false),
            new ProcessorDescriptor.Input(
                "StarterInstalled",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.StarterInstalled",
                false),
            new ProcessorDescriptor.Input(
                "StarterMenuRequested",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.StarterMenuRequested",
                false),
            new ProcessorDescriptor.Input(
                "StarterProjectOpened",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.StarterProjectOpened",
                false),
            new ProcessorDescriptor.Input(
                "StarterReviewAnswered",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.StarterReviewAnswered",
                false),
            new ProcessorDescriptor.Input(
                "StatusShown",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.StatusShown",
                false),
            new ProcessorDescriptor.Input(
                "TimeOrderObserved",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.TimeOrderObserved",
                false),
            new ProcessorDescriptor.Input(
                "ViewFilterChanged",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ViewFilterChanged",
                false),
            new ProcessorDescriptor.Input(
                "WalkAcknowledged",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.WalkAcknowledged",
                false),
            new ProcessorDescriptor.Input(
                "WalkDefinitionChanged",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.WalkDefinitionChanged",
                false),
            new ProcessorDescriptor.Input(
                "WalkEndRequested",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.WalkEndRequested",
                false),
            new ProcessorDescriptor.Input(
                "WalkNavigated",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.WalkNavigated",
                false),
            new ProcessorDescriptor.Input(
                "WalkPlayRequested",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.WalkPlayRequested",
                false),
            new ProcessorDescriptor.Input(
                "WalkStepPrepared",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.WalkStepPrepared",
                false),
            new ProcessorDescriptor.Input(
                "WalkTargetsLit",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.WalkTargetsLit",
                false),
            new ProcessorDescriptor.Input(
                "WalkViewApplied",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.WalkViewApplied",
                false),
            new ProcessorDescriptor.Input(
                "WalkViewChanged",
                "telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.WalkViewChanged",
                false)
          },
          new ProcessorDescriptor.Sink[] {},
          new ProcessorDescriptor.Service[] {},
          new DescriptorSupport.Meta(
              null,
              "1.0.76",
              "1914ab736f7b230f01fd95e95e316a49e9de1152c7a281d0580995a2d3e5ab1b",
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
    aiSetup.setActionListener(actionListener);
    aiSetup.setNoLogHint(startPageNoLogHint);
    aiSetup.setOpenLog(openLog);
    assistantLoop.setAssistantMode(assistantMode);
    assistantLoop.setRecordsView(recordsView);
    effectQueue.setDataFlowContext(context);
    localAgentSession.setActionListener(actionListener);
    localAgentSession.setOpenLog(openLog);
    sessionBoundary.setLocalAgentSession(localAgentSession);
    sessionBoundary.setOnboardingProposal(onboardingProposal);
    sessionBoundary.setStarterProject(starterProject);
    startPagePrompt.setAiSetup(aiSetup);
    startPagePrompt.setNoLogHint(startPageNoLogHint);
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
    } else if (event instanceof ActionListenerConfigured) {
      ActionListenerConfigured typedEvent = (ActionListenerConfigured) event;
      handleEvent(typedEvent);
    } else if (event instanceof AiClientsObserved) {
      AiClientsObserved typedEvent = (AiClientsObserved) event;
      handleEvent(typedEvent);
    } else if (event instanceof AiSetupAlsoTurnOffChosen) {
      AiSetupAlsoTurnOffChosen typedEvent = (AiSetupAlsoTurnOffChosen) event;
      handleEvent(typedEvent);
    } else if (event instanceof AiSetupBack) {
      AiSetupBack typedEvent = (AiSetupBack) event;
      handleEvent(typedEvent);
    } else if (event instanceof AiSetupBuildModeAnswered) {
      AiSetupBuildModeAnswered typedEvent = (AiSetupBuildModeAnswered) event;
      handleEvent(typedEvent);
    } else if (event instanceof AiSetupCancelled) {
      AiSetupCancelled typedEvent = (AiSetupCancelled) event;
      handleEvent(typedEvent);
    } else if (event instanceof AiSetupCheckAgain) {
      AiSetupCheckAgain typedEvent = (AiSetupCheckAgain) event;
      handleEvent(typedEvent);
    } else if (event instanceof AiSetupChoiceMade) {
      AiSetupChoiceMade typedEvent = (AiSetupChoiceMade) event;
      handleEvent(typedEvent);
    } else if (event instanceof AiSetupClientChosen) {
      AiSetupClientChosen typedEvent = (AiSetupClientChosen) event;
      handleEvent(typedEvent);
    } else if (event instanceof AiSetupContinue) {
      AiSetupContinue typedEvent = (AiSetupContinue) event;
      handleEvent(typedEvent);
    } else if (event instanceof AiSetupCopyRequested) {
      AiSetupCopyRequested typedEvent = (AiSetupCopyRequested) event;
      handleEvent(typedEvent);
    } else if (event instanceof AiSetupFirstRunDismissed) {
      AiSetupFirstRunDismissed typedEvent = (AiSetupFirstRunDismissed) event;
      handleEvent(typedEvent);
    } else if (event instanceof AiSetupMoreOptions) {
      AiSetupMoreOptions typedEvent = (AiSetupMoreOptions) event;
      handleEvent(typedEvent);
    } else if (event instanceof AiSetupPostureChosen) {
      AiSetupPostureChosen typedEvent = (AiSetupPostureChosen) event;
      handleEvent(typedEvent);
    } else if (event instanceof AiSetupRegisterRequested) {
      AiSetupRegisterRequested typedEvent = (AiSetupRegisterRequested) event;
      handleEvent(typedEvent);
    } else if (event instanceof AiSetupRequested) {
      AiSetupRequested typedEvent = (AiSetupRequested) event;
      handleEvent(typedEvent);
    } else if (event instanceof AiSetupStartupObserved) {
      AiSetupStartupObserved typedEvent = (AiSetupStartupObserved) event;
      handleEvent(typedEvent);
    } else if (event instanceof AiSetupSuggestedTaken) {
      AiSetupSuggestedTaken typedEvent = (AiSetupSuggestedTaken) event;
      handleEvent(typedEvent);
    } else if (event instanceof AiSetupTimerFired) {
      AiSetupTimerFired typedEvent = (AiSetupTimerFired) event;
      handleEvent(typedEvent);
    } else if (event instanceof AssistantActionFinished) {
      AssistantActionFinished typedEvent = (AssistantActionFinished) event;
      handleEvent(typedEvent);
    } else if (event instanceof AssistantCancelRequested) {
      AssistantCancelRequested typedEvent = (AssistantCancelRequested) event;
      handleEvent(typedEvent);
    } else if (event instanceof AssistantCompletionFailed) {
      AssistantCompletionFailed typedEvent = (AssistantCompletionFailed) event;
      handleEvent(typedEvent);
    } else if (event instanceof AssistantCompletionReceived) {
      AssistantCompletionReceived typedEvent = (AssistantCompletionReceived) event;
      handleEvent(typedEvent);
    } else if (event instanceof AssistantContextFailed) {
      AssistantContextFailed typedEvent = (AssistantContextFailed) event;
      handleEvent(typedEvent);
    } else if (event instanceof AssistantContextPrepared) {
      AssistantContextPrepared typedEvent = (AssistantContextPrepared) event;
      handleEvent(typedEvent);
    } else if (event instanceof AssistantDraftComposed) {
      AssistantDraftComposed typedEvent = (AssistantDraftComposed) event;
      handleEvent(typedEvent);
    } else if (event instanceof AssistantDraftPending) {
      AssistantDraftPending typedEvent = (AssistantDraftPending) event;
      handleEvent(typedEvent);
    } else if (event instanceof AssistantEffectStarted) {
      AssistantEffectStarted typedEvent = (AssistantEffectStarted) event;
      handleEvent(typedEvent);
    } else if (event instanceof AssistantFileExchangeObserved) {
      AssistantFileExchangeObserved typedEvent = (AssistantFileExchangeObserved) event;
      handleEvent(typedEvent);
    } else if (event instanceof AssistantFullProfileAnswered) {
      AssistantFullProfileAnswered typedEvent = (AssistantFullProfileAnswered) event;
      handleEvent(typedEvent);
    } else if (event instanceof AssistantHandoffRequested) {
      AssistantHandoffRequested typedEvent = (AssistantHandoffRequested) event;
      handleEvent(typedEvent);
    } else if (event instanceof AssistantHostRequested) {
      AssistantHostRequested typedEvent = (AssistantHostRequested) event;
      handleEvent(typedEvent);
    } else if (event instanceof AssistantHostShown) {
      AssistantHostShown typedEvent = (AssistantHostShown) event;
      handleEvent(typedEvent);
    } else if (event instanceof AssistantModeChosen) {
      AssistantModeChosen typedEvent = (AssistantModeChosen) event;
      handleEvent(typedEvent);
    } else if (event instanceof AssistantNewChatRequested) {
      AssistantNewChatRequested typedEvent = (AssistantNewChatRequested) event;
      handleEvent(typedEvent);
    } else if (event instanceof AssistantOpenApplied) {
      AssistantOpenApplied typedEvent = (AssistantOpenApplied) event;
      handleEvent(typedEvent);
    } else if (event instanceof AssistantOpenApplyFailed) {
      AssistantOpenApplyFailed typedEvent = (AssistantOpenApplyFailed) event;
      handleEvent(typedEvent);
    } else if (event instanceof AssistantProfileApplied) {
      AssistantProfileApplied typedEvent = (AssistantProfileApplied) event;
      handleEvent(typedEvent);
    } else if (event instanceof AssistantProfileChosen) {
      AssistantProfileChosen typedEvent = (AssistantProfileChosen) event;
      handleEvent(typedEvent);
    } else if (event instanceof AssistantProfileObserved) {
      AssistantProfileObserved typedEvent = (AssistantProfileObserved) event;
      handleEvent(typedEvent);
    } else if (event instanceof AssistantProfilesShown) {
      AssistantProfilesShown typedEvent = (AssistantProfilesShown) event;
      handleEvent(typedEvent);
    } else if (event instanceof AssistantSendRequested) {
      AssistantSendRequested typedEvent = (AssistantSendRequested) event;
      handleEvent(typedEvent);
    } else if (event instanceof AssistantTabShown) {
      AssistantTabShown typedEvent = (AssistantTabShown) event;
      handleEvent(typedEvent);
    } else if (event instanceof BridgeObserved) {
      BridgeObserved typedEvent = (BridgeObserved) event;
      handleEvent(typedEvent);
    } else if (event instanceof BundleCaptureRequested) {
      BundleCaptureRequested typedEvent = (BundleCaptureRequested) event;
      handleEvent(typedEvent);
    } else if (event instanceof BundleDeleted) {
      BundleDeleted typedEvent = (BundleDeleted) event;
      handleEvent(typedEvent);
    } else if (event instanceof BundleRootsRestored) {
      BundleRootsRestored typedEvent = (BundleRootsRestored) event;
      handleEvent(typedEvent);
    } else if (event instanceof BundleWriteFailed) {
      BundleWriteFailed typedEvent = (BundleWriteFailed) event;
      handleEvent(typedEvent);
    } else if (event instanceof BundleWritten) {
      BundleWritten typedEvent = (BundleWritten) event;
      handleEvent(typedEvent);
    } else if (event instanceof CanvasLeftAssistant) {
      CanvasLeftAssistant typedEvent = (CanvasLeftAssistant) event;
      handleEvent(typedEvent);
    } else if (event instanceof CaptureStarted) {
      CaptureStarted typedEvent = (CaptureStarted) event;
      handleEvent(typedEvent);
    } else if (event instanceof ClaudeAuthObserved) {
      ClaudeAuthObserved typedEvent = (ClaudeAuthObserved) event;
      handleEvent(typedEvent);
    } else if (event instanceof ClaudeObserved) {
      ClaudeObserved typedEvent = (ClaudeObserved) event;
      handleEvent(typedEvent);
    } else if (event instanceof CloseRequested) {
      CloseRequested typedEvent = (CloseRequested) event;
      handleEvent(typedEvent);
    } else if (event instanceof DraftPlaced) {
      DraftPlaced typedEvent = (DraftPlaced) event;
      handleEvent(typedEvent);
    } else if (event instanceof EffectFailed) {
      EffectFailed typedEvent = (EffectFailed) event;
      handleEvent(typedEvent);
    } else if (event instanceof ExampleDeadlinePassed) {
      ExampleDeadlinePassed typedEvent = (ExampleDeadlinePassed) event;
      handleEvent(typedEvent);
    } else if (event instanceof ExampleDownloadConfirmed) {
      ExampleDownloadConfirmed typedEvent = (ExampleDownloadConfirmed) event;
      handleEvent(typedEvent);
    } else if (event instanceof ExampleDownloadRequested) {
      ExampleDownloadRequested typedEvent = (ExampleDownloadRequested) event;
      handleEvent(typedEvent);
    } else if (event instanceof ExampleEffectStarted) {
      ExampleEffectStarted typedEvent = (ExampleEffectStarted) event;
      handleEvent(typedEvent);
    } else if (event instanceof ExampleFetched) {
      ExampleFetched typedEvent = (ExampleFetched) event;
      handleEvent(typedEvent);
    } else if (event instanceof ExampleUnpacked) {
      ExampleUnpacked typedEvent = (ExampleUnpacked) event;
      handleEvent(typedEvent);
    } else if (event instanceof ExternalAccessAnswered) {
      ExternalAccessAnswered typedEvent = (ExternalAccessAnswered) event;
      handleEvent(typedEvent);
    } else if (event instanceof ExternalAccessChosen) {
      ExternalAccessChosen typedEvent = (ExternalAccessChosen) event;
      handleEvent(typedEvent);
    } else if (event instanceof ExternalAccessRequested) {
      ExternalAccessRequested typedEvent = (ExternalAccessRequested) event;
      handleEvent(typedEvent);
    } else if (event instanceof ExternalClientSeen) {
      ExternalClientSeen typedEvent = (ExternalClientSeen) event;
      handleEvent(typedEvent);
    } else if (event instanceof FollowSet) {
      FollowSet typedEvent = (FollowSet) event;
      handleEvent(typedEvent);
    } else if (event instanceof FollowToggled) {
      FollowToggled typedEvent = (FollowToggled) event;
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
    } else if (event instanceof HostedCallAbandoned) {
      HostedCallAbandoned typedEvent = (HostedCallAbandoned) event;
      handleEvent(typedEvent);
    } else if (event instanceof HostedCallPresented) {
      HostedCallPresented typedEvent = (HostedCallPresented) event;
      handleEvent(typedEvent);
    } else if (event instanceof HostedCallRefusedByServer) {
      HostedCallRefusedByServer typedEvent = (HostedCallRefusedByServer) event;
      handleEvent(typedEvent);
    } else if (event instanceof HostedReportWritten) {
      HostedReportWritten typedEvent = (HostedReportWritten) event;
      handleEvent(typedEvent);
    } else if (event instanceof LocalAgentAskAgainRequested) {
      LocalAgentAskAgainRequested typedEvent = (LocalAgentAskAgainRequested) event;
      handleEvent(typedEvent);
    } else if (event instanceof LocalAgentBridgeServed) {
      LocalAgentBridgeServed typedEvent = (LocalAgentBridgeServed) event;
      handleEvent(typedEvent);
    } else if (event instanceof LocalAgentCallAdmissionRequested) {
      LocalAgentCallAdmissionRequested typedEvent = (LocalAgentCallAdmissionRequested) event;
      handleEvent(typedEvent);
    } else if (event instanceof LocalAgentConsentChanged) {
      LocalAgentConsentChanged typedEvent = (LocalAgentConsentChanged) event;
      handleEvent(typedEvent);
    } else if (event instanceof LocalAgentCostObserved) {
      LocalAgentCostObserved typedEvent = (LocalAgentCostObserved) event;
      handleEvent(typedEvent);
    } else if (event instanceof LocalAgentDiagnostics) {
      LocalAgentDiagnostics typedEvent = (LocalAgentDiagnostics) event;
      handleEvent(typedEvent);
    } else if (event instanceof LocalAgentDraftComposed) {
      LocalAgentDraftComposed typedEvent = (LocalAgentDraftComposed) event;
      handleEvent(typedEvent);
    } else if (event instanceof LocalAgentEffectStarted) {
      LocalAgentEffectStarted typedEvent = (LocalAgentEffectStarted) event;
      handleEvent(typedEvent);
    } else if (event instanceof LocalAgentExited) {
      LocalAgentExited typedEvent = (LocalAgentExited) event;
      handleEvent(typedEvent);
    } else if (event instanceof LocalAgentFolderAnswered) {
      LocalAgentFolderAnswered typedEvent = (LocalAgentFolderAnswered) event;
      handleEvent(typedEvent);
    } else if (event instanceof LocalAgentGrantExpired) {
      LocalAgentGrantExpired typedEvent = (LocalAgentGrantExpired) event;
      handleEvent(typedEvent);
    } else if (event instanceof LocalAgentHookObserved) {
      LocalAgentHookObserved typedEvent = (LocalAgentHookObserved) event;
      handleEvent(typedEvent);
    } else if (event instanceof LocalAgentInitObserved) {
      LocalAgentInitObserved typedEvent = (LocalAgentInitObserved) event;
      handleEvent(typedEvent);
    } else if (event instanceof LocalAgentInitialized) {
      LocalAgentInitialized typedEvent = (LocalAgentInitialized) event;
      handleEvent(typedEvent);
    } else if (event instanceof LocalAgentInterruptAcked) {
      LocalAgentInterruptAcked typedEvent = (LocalAgentInterruptAcked) event;
      handleEvent(typedEvent);
    } else if (event instanceof LocalAgentModeObserved) {
      LocalAgentModeObserved typedEvent = (LocalAgentModeObserved) event;
      handleEvent(typedEvent);
    } else if (event instanceof LocalAgentNewChatRequested) {
      LocalAgentNewChatRequested typedEvent = (LocalAgentNewChatRequested) event;
      handleEvent(typedEvent);
    } else if (event instanceof LocalAgentNoted) {
      LocalAgentNoted typedEvent = (LocalAgentNoted) event;
      handleEvent(typedEvent);
    } else if (event instanceof LocalAgentPermissionAnswered) {
      LocalAgentPermissionAnswered typedEvent = (LocalAgentPermissionAnswered) event;
      handleEvent(typedEvent);
    } else if (event instanceof LocalAgentPermissionCancelled) {
      LocalAgentPermissionCancelled typedEvent = (LocalAgentPermissionCancelled) event;
      handleEvent(typedEvent);
    } else if (event instanceof LocalAgentPermissionChosen) {
      LocalAgentPermissionChosen typedEvent = (LocalAgentPermissionChosen) event;
      handleEvent(typedEvent);
    } else if (event instanceof LocalAgentPermissionDeadline) {
      LocalAgentPermissionDeadline typedEvent = (LocalAgentPermissionDeadline) event;
      handleEvent(typedEvent);
    } else if (event instanceof LocalAgentPermissionRequested) {
      LocalAgentPermissionRequested typedEvent = (LocalAgentPermissionRequested) event;
      handleEvent(typedEvent);
    } else if (event instanceof LocalAgentRestartAnswered) {
      LocalAgentRestartAnswered typedEvent = (LocalAgentRestartAnswered) event;
      handleEvent(typedEvent);
    } else if (event instanceof LocalAgentRestartOfferRequested) {
      LocalAgentRestartOfferRequested typedEvent = (LocalAgentRestartOfferRequested) event;
      handleEvent(typedEvent);
    } else if (event instanceof LocalAgentSendRequested) {
      LocalAgentSendRequested typedEvent = (LocalAgentSendRequested) event;
      handleEvent(typedEvent);
    } else if (event instanceof LocalAgentSetupDirectoryChosen) {
      LocalAgentSetupDirectoryChosen typedEvent = (LocalAgentSetupDirectoryChosen) event;
      handleEvent(typedEvent);
    } else if (event instanceof LocalAgentSpawned) {
      LocalAgentSpawned typedEvent = (LocalAgentSpawned) event;
      handleEvent(typedEvent);
    } else if (event instanceof LocalAgentStartDeadline) {
      LocalAgentStartDeadline typedEvent = (LocalAgentStartDeadline) event;
      handleEvent(typedEvent);
    } else if (event instanceof LocalAgentStartRequested) {
      LocalAgentStartRequested typedEvent = (LocalAgentStartRequested) event;
      handleEvent(typedEvent);
    } else if (event instanceof LocalAgentStatusObserved) {
      LocalAgentStatusObserved typedEvent = (LocalAgentStatusObserved) event;
      handleEvent(typedEvent);
    } else if (event instanceof LocalAgentStopDeadline) {
      LocalAgentStopDeadline typedEvent = (LocalAgentStopDeadline) event;
      handleEvent(typedEvent);
    } else if (event instanceof LocalAgentStopRequested) {
      LocalAgentStopRequested typedEvent = (LocalAgentStopRequested) event;
      handleEvent(typedEvent);
    } else if (event instanceof LocalAgentSwitchChosen) {
      LocalAgentSwitchChosen typedEvent = (LocalAgentSwitchChosen) event;
      handleEvent(typedEvent);
    } else if (event instanceof LocalAgentTextUpdated) {
      LocalAgentTextUpdated typedEvent = (LocalAgentTextUpdated) event;
      handleEvent(typedEvent);
    } else if (event instanceof LocalAgentTurnEnded) {
      LocalAgentTurnEnded typedEvent = (LocalAgentTurnEnded) event;
      handleEvent(typedEvent);
    } else if (event instanceof LocalAgentTurnStarted) {
      LocalAgentTurnStarted typedEvent = (LocalAgentTurnStarted) event;
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
    } else if (event instanceof LogContentObserved) {
      LogContentObserved typedEvent = (LogContentObserved) event;
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
    } else if (event instanceof McpReadinessObserved) {
      McpReadinessObserved typedEvent = (McpReadinessObserved) event;
      handleEvent(typedEvent);
    } else if (event instanceof McpRegistrationChanged) {
      McpRegistrationChanged typedEvent = (McpRegistrationChanged) event;
      handleEvent(typedEvent);
    } else if (event instanceof McpRegistrationObserved) {
      McpRegistrationObserved typedEvent = (McpRegistrationObserved) event;
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
    } else if (event instanceof ProducerFindingsObserved) {
      ProducerFindingsObserved typedEvent = (ProducerFindingsObserved) event;
      handleEvent(typedEvent);
    } else if (event instanceof ProfileApplied) {
      ProfileApplied typedEvent = (ProfileApplied) event;
      handleEvent(typedEvent);
    } else if (event instanceof ProfileLoaded) {
      ProfileLoaded typedEvent = (ProfileLoaded) event;
      handleEvent(typedEvent);
    } else if (event instanceof ProjectReopenPresented) {
      ProjectReopenPresented typedEvent = (ProjectReopenPresented) event;
      handleEvent(typedEvent);
    } else if (event instanceof ProjectReopenReady) {
      ProjectReopenReady typedEvent = (ProjectReopenReady) event;
      handleEvent(typedEvent);
    } else if (event instanceof ProjectReopenRequested) {
      ProjectReopenRequested typedEvent = (ProjectReopenRequested) event;
      handleEvent(typedEvent);
    } else if (event instanceof ProposalApplyRequested) {
      ProposalApplyRequested typedEvent = (ProposalApplyRequested) event;
      handleEvent(typedEvent);
    } else if (event instanceof ProposalChecked) {
      ProposalChecked typedEvent = (ProposalChecked) event;
      handleEvent(typedEvent);
    } else if (event instanceof ProposalDismissed) {
      ProposalDismissed typedEvent = (ProposalDismissed) event;
      handleEvent(typedEvent);
    } else if (event instanceof ProposalEffectStarted) {
      ProposalEffectStarted typedEvent = (ProposalEffectStarted) event;
      handleEvent(typedEvent);
    } else if (event instanceof ProposalItemAnswered) {
      ProposalItemAnswered typedEvent = (ProposalItemAnswered) event;
      handleEvent(typedEvent);
    } else if (event instanceof ProposalItemApplied) {
      ProposalItemApplied typedEvent = (ProposalItemApplied) event;
      handleEvent(typedEvent);
    } else if (event instanceof ProposalItemPreviewed) {
      ProposalItemPreviewed typedEvent = (ProposalItemPreviewed) event;
      handleEvent(typedEvent);
    } else if (event instanceof ProposalPreviewDone) {
      ProposalPreviewDone typedEvent = (ProposalPreviewDone) event;
      handleEvent(typedEvent);
    } else if (event instanceof ProposalReceived) {
      ProposalReceived typedEvent = (ProposalReceived) event;
      handleEvent(typedEvent);
    } else if (event instanceof RecordFlagsChanged) {
      RecordFlagsChanged typedEvent = (RecordFlagsChanged) event;
      handleEvent(typedEvent);
    } else if (event instanceof RecordsViewInstalled) {
      RecordsViewInstalled typedEvent = (RecordsViewInstalled) event;
      handleEvent(typedEvent);
    } else if (event instanceof RecordsViewMeasured) {
      RecordsViewMeasured typedEvent = (RecordsViewMeasured) event;
      handleEvent(typedEvent);
    } else if (event instanceof RegisterAnswered) {
      RegisterAnswered typedEvent = (RegisterAnswered) event;
      handleEvent(typedEvent);
    } else if (event instanceof RestSwitchObserved) {
      RestSwitchObserved typedEvent = (RestSwitchObserved) event;
      handleEvent(typedEvent);
    } else if (event instanceof ReturnToMainOptions) {
      ReturnToMainOptions typedEvent = (ReturnToMainOptions) event;
      handleEvent(typedEvent);
    } else if (event instanceof ScanScheduled) {
      ScanScheduled typedEvent = (ScanScheduled) event;
      handleEvent(typedEvent);
    } else if (event instanceof SettingsRestored) {
      SettingsRestored typedEvent = (SettingsRestored) event;
      handleEvent(typedEvent);
    } else if (event instanceof SkillsVendored) {
      SkillsVendored typedEvent = (SkillsVendored) event;
      handleEvent(typedEvent);
    } else if (event instanceof SourceRootsObserved) {
      SourceRootsObserved typedEvent = (SourceRootsObserved) event;
      handleEvent(typedEvent);
    } else if (event instanceof StartPageAvailability) {
      StartPageAvailability typedEvent = (StartPageAvailability) event;
      handleEvent(typedEvent);
    } else if (event instanceof StartPageEffectStarted) {
      StartPageEffectStarted typedEvent = (StartPageEffectStarted) event;
      handleEvent(typedEvent);
    } else if (event instanceof StartPageFlowDone) {
      StartPageFlowDone typedEvent = (StartPageFlowDone) event;
      handleEvent(typedEvent);
    } else if (event instanceof StartPageNoLogAnswered) {
      StartPageNoLogAnswered typedEvent = (StartPageNoLogAnswered) event;
      handleEvent(typedEvent);
    } else if (event instanceof StartPagePromptSent) {
      StartPagePromptSent typedEvent = (StartPagePromptSent) event;
      handleEvent(typedEvent);
    } else if (event instanceof StartPromptPackDropped) {
      StartPromptPackDropped typedEvent = (StartPromptPackDropped) event;
      handleEvent(typedEvent);
    } else if (event instanceof StarterCatalogueObserved) {
      StarterCatalogueObserved typedEvent = (StarterCatalogueObserved) event;
      handleEvent(typedEvent);
    } else if (event instanceof StarterChosen) {
      StarterChosen typedEvent = (StarterChosen) event;
      handleEvent(typedEvent);
    } else if (event instanceof StarterConfigured) {
      StarterConfigured typedEvent = (StarterConfigured) event;
      handleEvent(typedEvent);
    } else if (event instanceof StarterDefaultsObserved) {
      StarterDefaultsObserved typedEvent = (StarterDefaultsObserved) event;
      handleEvent(typedEvent);
    } else if (event instanceof StarterDesignOpened) {
      StarterDesignOpened typedEvent = (StarterDesignOpened) event;
      handleEvent(typedEvent);
    } else if (event instanceof StarterDestinationChosen) {
      StarterDestinationChosen typedEvent = (StarterDestinationChosen) event;
      handleEvent(typedEvent);
    } else if (event instanceof StarterDirectoryObserved) {
      StarterDirectoryObserved typedEvent = (StarterDirectoryObserved) event;
      handleEvent(typedEvent);
    } else if (event instanceof StarterDiscoveryObserved) {
      StarterDiscoveryObserved typedEvent = (StarterDiscoveryObserved) event;
      handleEvent(typedEvent);
    } else if (event instanceof StarterEffectStarted) {
      StarterEffectStarted typedEvent = (StarterEffectStarted) event;
      handleEvent(typedEvent);
    } else if (event instanceof StarterGraphOfferAnswered) {
      StarterGraphOfferAnswered typedEvent = (StarterGraphOfferAnswered) event;
      handleEvent(typedEvent);
    } else if (event instanceof StarterGraphmlAppeared) {
      StarterGraphmlAppeared typedEvent = (StarterGraphmlAppeared) event;
      handleEvent(typedEvent);
    } else if (event instanceof StarterInstallFailed) {
      StarterInstallFailed typedEvent = (StarterInstallFailed) event;
      handleEvent(typedEvent);
    } else if (event instanceof StarterInstalled) {
      StarterInstalled typedEvent = (StarterInstalled) event;
      handleEvent(typedEvent);
    } else if (event instanceof StarterMenuRequested) {
      StarterMenuRequested typedEvent = (StarterMenuRequested) event;
      handleEvent(typedEvent);
    } else if (event instanceof StarterProjectOpened) {
      StarterProjectOpened typedEvent = (StarterProjectOpened) event;
      handleEvent(typedEvent);
    } else if (event instanceof StarterReviewAnswered) {
      StarterReviewAnswered typedEvent = (StarterReviewAnswered) event;
      handleEvent(typedEvent);
    } else if (event instanceof StatusShown) {
      StatusShown typedEvent = (StatusShown) event;
      handleEvent(typedEvent);
    } else if (event instanceof TimeOrderObserved) {
      TimeOrderObserved typedEvent = (TimeOrderObserved) event;
      handleEvent(typedEvent);
    } else if (event instanceof ViewFilterChanged) {
      ViewFilterChanged typedEvent = (ViewFilterChanged) event;
      handleEvent(typedEvent);
    } else if (event instanceof WalkAcknowledged) {
      WalkAcknowledged typedEvent = (WalkAcknowledged) event;
      handleEvent(typedEvent);
    } else if (event instanceof WalkDefinitionChanged) {
      WalkDefinitionChanged typedEvent = (WalkDefinitionChanged) event;
      handleEvent(typedEvent);
    } else if (event instanceof WalkEndRequested) {
      WalkEndRequested typedEvent = (WalkEndRequested) event;
      handleEvent(typedEvent);
    } else if (event instanceof WalkNavigated) {
      WalkNavigated typedEvent = (WalkNavigated) event;
      handleEvent(typedEvent);
    } else if (event instanceof WalkPlayRequested) {
      WalkPlayRequested typedEvent = (WalkPlayRequested) event;
      handleEvent(typedEvent);
    } else if (event instanceof WalkStepPrepared) {
      WalkStepPrepared typedEvent = (WalkStepPrepared) event;
      handleEvent(typedEvent);
    } else if (event instanceof WalkTargetsLit) {
      WalkTargetsLit typedEvent = (WalkTargetsLit) event;
      handleEvent(typedEvent);
    } else if (event instanceof WalkViewApplied) {
      WalkViewApplied typedEvent = (WalkViewApplied) event;
      handleEvent(typedEvent);
    } else if (event instanceof WalkViewChanged) {
      WalkViewChanged typedEvent = (WalkViewChanged) event;
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
  public void onEvent(ActionListenerConfigured event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(AiClientsObserved event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(AiSetupAlsoTurnOffChosen event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(AiSetupBack event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(AiSetupBuildModeAnswered event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(AiSetupCancelled event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(AiSetupCheckAgain event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(AiSetupChoiceMade event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(AiSetupClientChosen event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(AiSetupContinue event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(AiSetupCopyRequested event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(AiSetupFirstRunDismissed event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(AiSetupMoreOptions event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(AiSetupPostureChosen event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(AiSetupRegisterRequested event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(AiSetupRequested event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(AiSetupStartupObserved event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(AiSetupSuggestedTaken event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(AiSetupTimerFired event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(AssistantActionFinished event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(AssistantCancelRequested event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(AssistantCompletionFailed event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(AssistantCompletionReceived event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(AssistantContextFailed event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(AssistantContextPrepared event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(AssistantDraftComposed event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(AssistantDraftPending event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(AssistantEffectStarted event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(AssistantFileExchangeObserved event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(AssistantFullProfileAnswered event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(AssistantHandoffRequested event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(AssistantHostRequested event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(AssistantHostShown event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(AssistantModeChosen event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(AssistantNewChatRequested event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(AssistantOpenApplied event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(AssistantOpenApplyFailed event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(AssistantProfileApplied event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(AssistantProfileChosen event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(AssistantProfileObserved event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(AssistantProfilesShown event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(AssistantSendRequested event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(AssistantTabShown event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(BridgeObserved event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(BundleCaptureRequested event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(BundleDeleted event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(BundleRootsRestored event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(BundleWriteFailed event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(BundleWritten event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(CanvasLeftAssistant event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(CaptureStarted event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(ClaudeAuthObserved event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(ClaudeObserved event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(CloseRequested event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(DraftPlaced event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(EffectFailed event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(ExampleDeadlinePassed event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(ExampleDownloadConfirmed event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(ExampleDownloadRequested event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(ExampleEffectStarted event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(ExampleFetched event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(ExampleUnpacked event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(ExternalAccessAnswered event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(ExternalAccessChosen event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(ExternalAccessRequested event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(ExternalClientSeen event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(FollowSet event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(FollowToggled event) {
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
  public void onEvent(HostedCallAbandoned event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(HostedCallPresented event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(HostedCallRefusedByServer event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(HostedReportWritten event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(LocalAgentAskAgainRequested event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(LocalAgentBridgeServed event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(LocalAgentCallAdmissionRequested event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(LocalAgentConsentChanged event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(LocalAgentCostObserved event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(LocalAgentDiagnostics event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(LocalAgentDraftComposed event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(LocalAgentEffectStarted event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(LocalAgentExited event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(LocalAgentFolderAnswered event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(LocalAgentGrantExpired event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(LocalAgentHookObserved event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(LocalAgentInitObserved event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(LocalAgentInitialized event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(LocalAgentInterruptAcked event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(LocalAgentModeObserved event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(LocalAgentNewChatRequested event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(LocalAgentNoted event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(LocalAgentPermissionAnswered event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(LocalAgentPermissionCancelled event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(LocalAgentPermissionChosen event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(LocalAgentPermissionDeadline event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(LocalAgentPermissionRequested event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(LocalAgentRestartAnswered event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(LocalAgentRestartOfferRequested event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(LocalAgentSendRequested event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(LocalAgentSetupDirectoryChosen event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(LocalAgentSpawned event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(LocalAgentStartDeadline event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(LocalAgentStartRequested event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(LocalAgentStatusObserved event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(LocalAgentStopDeadline event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(LocalAgentStopRequested event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(LocalAgentSwitchChosen event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(LocalAgentTextUpdated event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(LocalAgentTurnEnded event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(LocalAgentTurnStarted event) {
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
  public void onEvent(LogContentObserved event) {
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
  public void onEvent(McpReadinessObserved event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(McpRegistrationChanged event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(McpRegistrationObserved event) {
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
  public void onEvent(ProducerFindingsObserved event) {
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
  public void onEvent(ProjectReopenPresented event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(ProjectReopenReady event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(ProjectReopenRequested event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(ProposalApplyRequested event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(ProposalChecked event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(ProposalDismissed event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(ProposalEffectStarted event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(ProposalItemAnswered event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(ProposalItemApplied event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(ProposalItemPreviewed event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(ProposalPreviewDone event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(ProposalReceived event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(RecordFlagsChanged event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(RecordsViewInstalled event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(RecordsViewMeasured event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(RegisterAnswered event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(RestSwitchObserved event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(ReturnToMainOptions event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(ScanScheduled event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(SettingsRestored event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(SkillsVendored event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(SourceRootsObserved event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(StartPageAvailability event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(StartPageEffectStarted event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(StartPageFlowDone event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(StartPageNoLogAnswered event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(StartPagePromptSent event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(StartPromptPackDropped event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(StarterCatalogueObserved event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(StarterChosen event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(StarterConfigured event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(StarterDefaultsObserved event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(StarterDesignOpened event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(StarterDestinationChosen event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(StarterDirectoryObserved event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(StarterDiscoveryObserved event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(StarterEffectStarted event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(StarterGraphOfferAnswered event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(StarterGraphmlAppeared event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(StarterInstallFailed event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(StarterInstalled event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(StarterMenuRequested event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(StarterProjectOpened event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(StarterReviewAnswered event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(StatusShown event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(TimeOrderObserved event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(ViewFilterChanged event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(WalkAcknowledged event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(WalkDefinitionChanged event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(WalkEndRequested event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(WalkNavigated event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(WalkPlayRequested event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(WalkStepPrepared event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(WalkTargetsLit event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(WalkViewApplied event) {
    processEvent(event);
  }

  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(WalkViewChanged event) {
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

  public void handleEvent(ActionListenerConfigured typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(actionListener, "actionListener", "onConfigured", typedEvent);
    isDirty_actionListener = actionListener.onConfigured(typedEvent);
    auditInvocation(aiSetup, "aiSetup", "onListenerConfigured", typedEvent);
    isDirty_aiSetup = aiSetup.onListenerConfigured(typedEvent);
    if (guardCheck_walkPlayback()) {
      auditInvocation(walkPlayback, "walkPlayback", "onLogChanged", typedEvent);
      walkPlayback.onLogChanged();
    }
    afterEvent();
  }

  public void handleEvent(AiClientsObserved typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(aiSetup, "aiSetup", "onClientsObserved", typedEvent);
    isDirty_aiSetup = aiSetup.onClientsObserved(typedEvent);
    afterEvent();
  }

  public void handleEvent(AiSetupAlsoTurnOffChosen typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(aiSetup, "aiSetup", "onAlsoTurnOff", typedEvent);
    isDirty_aiSetup = aiSetup.onAlsoTurnOff(typedEvent);
    afterEvent();
  }

  public void handleEvent(AiSetupBack typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(aiSetup, "aiSetup", "onBack", typedEvent);
    isDirty_aiSetup = aiSetup.onBack(typedEvent);
    afterEvent();
  }

  public void handleEvent(AiSetupBuildModeAnswered typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(aiSetup, "aiSetup", "onBuildModeAnswered", typedEvent);
    isDirty_aiSetup = aiSetup.onBuildModeAnswered(typedEvent);
    afterEvent();
  }

  public void handleEvent(AiSetupCancelled typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(aiSetup, "aiSetup", "onCancelled", typedEvent);
    isDirty_aiSetup = aiSetup.onCancelled(typedEvent);
    auditInvocation(startPagePrompt, "startPagePrompt", "onSetupCancelled", typedEvent);
    startPagePrompt.onSetupCancelled(typedEvent);
    afterEvent();
  }

  public void handleEvent(AiSetupCheckAgain typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(aiSetup, "aiSetup", "onCheckAgain", typedEvent);
    isDirty_aiSetup = aiSetup.onCheckAgain(typedEvent);
    afterEvent();
  }

  public void handleEvent(AiSetupChoiceMade typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(aiSetup, "aiSetup", "onChoiceMade", typedEvent);
    isDirty_aiSetup = aiSetup.onChoiceMade(typedEvent);
    afterEvent();
  }

  public void handleEvent(AiSetupClientChosen typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(aiSetup, "aiSetup", "onClientChosen", typedEvent);
    isDirty_aiSetup = aiSetup.onClientChosen(typedEvent);
    afterEvent();
  }

  public void handleEvent(AiSetupContinue typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(aiSetup, "aiSetup", "onContinue", typedEvent);
    isDirty_aiSetup = aiSetup.onContinue(typedEvent);
    afterEvent();
  }

  public void handleEvent(AiSetupCopyRequested typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(aiSetup, "aiSetup", "onCopyRequested", typedEvent);
    isDirty_aiSetup = aiSetup.onCopyRequested(typedEvent);
    afterEvent();
  }

  public void handleEvent(AiSetupFirstRunDismissed typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(aiSetup, "aiSetup", "onFirstRunDismissed", typedEvent);
    isDirty_aiSetup = aiSetup.onFirstRunDismissed(typedEvent);
    afterEvent();
  }

  public void handleEvent(AiSetupMoreOptions typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(aiSetup, "aiSetup", "onMoreOptions", typedEvent);
    isDirty_aiSetup = aiSetup.onMoreOptions(typedEvent);
    afterEvent();
  }

  public void handleEvent(AiSetupPostureChosen typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(aiSetup, "aiSetup", "onPostureChosen", typedEvent);
    isDirty_aiSetup = aiSetup.onPostureChosen(typedEvent);
    afterEvent();
  }

  public void handleEvent(AiSetupRegisterRequested typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(aiSetup, "aiSetup", "onRegisterRequested", typedEvent);
    isDirty_aiSetup = aiSetup.onRegisterRequested(typedEvent);
    afterEvent();
  }

  public void handleEvent(AiSetupRequested typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(aiSetup, "aiSetup", "onSetupRequested", typedEvent);
    isDirty_aiSetup = aiSetup.onSetupRequested(typedEvent);
    afterEvent();
  }

  public void handleEvent(AiSetupStartupObserved typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(aiSetup, "aiSetup", "onStartupObserved", typedEvent);
    isDirty_aiSetup = aiSetup.onStartupObserved(typedEvent);
    afterEvent();
  }

  public void handleEvent(AiSetupSuggestedTaken typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(aiSetup, "aiSetup", "onSuggestedTaken", typedEvent);
    isDirty_aiSetup = aiSetup.onSuggestedTaken(typedEvent);
    afterEvent();
  }

  public void handleEvent(AiSetupTimerFired typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(aiSetup, "aiSetup", "onTimerFired", typedEvent);
    isDirty_aiSetup = aiSetup.onTimerFired(typedEvent);
    afterEvent();
  }

  public void handleEvent(AssistantActionFinished typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(assistantLoop, "assistantLoop", "onActionFinished", typedEvent);
    isDirty_assistantLoop = assistantLoop.onActionFinished(typedEvent);
    if (guardCheck_walkPlayback()) {
      auditInvocation(walkPlayback, "walkPlayback", "onLogChanged", typedEvent);
      walkPlayback.onLogChanged();
    }
    afterEvent();
  }

  public void handleEvent(AssistantCancelRequested typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(assistantLoop, "assistantLoop", "onCancelRequested", typedEvent);
    isDirty_assistantLoop = assistantLoop.onCancelRequested(typedEvent);
    if (guardCheck_walkPlayback()) {
      auditInvocation(walkPlayback, "walkPlayback", "onLogChanged", typedEvent);
      walkPlayback.onLogChanged();
    }
    afterEvent();
  }

  public void handleEvent(AssistantCompletionFailed typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(assistantLoop, "assistantLoop", "onCompletionFailed", typedEvent);
    isDirty_assistantLoop = assistantLoop.onCompletionFailed(typedEvent);
    if (guardCheck_walkPlayback()) {
      auditInvocation(walkPlayback, "walkPlayback", "onLogChanged", typedEvent);
      walkPlayback.onLogChanged();
    }
    afterEvent();
  }

  public void handleEvent(AssistantCompletionReceived typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(assistantLoop, "assistantLoop", "onCompletionReceived", typedEvent);
    isDirty_assistantLoop = assistantLoop.onCompletionReceived(typedEvent);
    if (guardCheck_walkPlayback()) {
      auditInvocation(walkPlayback, "walkPlayback", "onLogChanged", typedEvent);
      walkPlayback.onLogChanged();
    }
    afterEvent();
  }

  public void handleEvent(AssistantContextFailed typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(assistantLoop, "assistantLoop", "onContextFailed", typedEvent);
    isDirty_assistantLoop = assistantLoop.onContextFailed(typedEvent);
    if (guardCheck_walkPlayback()) {
      auditInvocation(walkPlayback, "walkPlayback", "onLogChanged", typedEvent);
      walkPlayback.onLogChanged();
    }
    afterEvent();
  }

  public void handleEvent(AssistantContextPrepared typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(assistantLoop, "assistantLoop", "onContextPrepared", typedEvent);
    isDirty_assistantLoop = assistantLoop.onContextPrepared(typedEvent);
    if (guardCheck_walkPlayback()) {
      auditInvocation(walkPlayback, "walkPlayback", "onLogChanged", typedEvent);
      walkPlayback.onLogChanged();
    }
    afterEvent();
  }

  public void handleEvent(AssistantDraftComposed typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(assistantLoop, "assistantLoop", "onDraftComposed", typedEvent);
    isDirty_assistantLoop = assistantLoop.onDraftComposed(typedEvent);
    if (guardCheck_walkPlayback()) {
      auditInvocation(walkPlayback, "walkPlayback", "onLogChanged", typedEvent);
      walkPlayback.onLogChanged();
    }
    afterEvent();
  }

  public void handleEvent(AssistantDraftPending typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(aiSetup, "aiSetup", "onDraftPending", typedEvent);
    isDirty_aiSetup = aiSetup.onDraftPending(typedEvent);
    afterEvent();
  }

  public void handleEvent(AssistantEffectStarted typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(assistantLoop, "assistantLoop", "onEffectStarted", typedEvent);
    isDirty_assistantLoop = assistantLoop.onEffectStarted(typedEvent);
    afterEvent();
  }

  public void handleEvent(AssistantFileExchangeObserved typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(localAgentPermission, "localAgentPermission", "onFileExchange", typedEvent);
    isDirty_localAgentPermission = localAgentPermission.onFileExchange(typedEvent);
    afterEvent();
  }

  public void handleEvent(AssistantFullProfileAnswered typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(aiSetup, "aiSetup", "onFullProfileAnswered", typedEvent);
    isDirty_aiSetup = aiSetup.onFullProfileAnswered(typedEvent);
    afterEvent();
  }

  public void handleEvent(AssistantHandoffRequested typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(assistantLoop, "assistantLoop", "onHandoffRequested", typedEvent);
    isDirty_assistantLoop = assistantLoop.onHandoffRequested(typedEvent);
    if (guardCheck_walkPlayback()) {
      auditInvocation(walkPlayback, "walkPlayback", "onLogChanged", typedEvent);
      walkPlayback.onLogChanged();
    }
    auditInvocation(walkPlayback, "walkPlayback", "onAssistantHandoffRequested", typedEvent);
    walkPlayback.onAssistantHandoffRequested(typedEvent);
    afterEvent();
  }

  public void handleEvent(AssistantHostRequested typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(assistantLoop, "assistantLoop", "onHostRequested", typedEvent);
    isDirty_assistantLoop = assistantLoop.onHostRequested(typedEvent);
    if (guardCheck_walkPlayback()) {
      auditInvocation(walkPlayback, "walkPlayback", "onLogChanged", typedEvent);
      walkPlayback.onLogChanged();
    }
    afterEvent();
  }

  public void handleEvent(AssistantHostShown typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(assistantLoop, "assistantLoop", "onHostShown", typedEvent);
    isDirty_assistantLoop = assistantLoop.onHostShown(typedEvent);
    if (guardCheck_walkPlayback()) {
      auditInvocation(walkPlayback, "walkPlayback", "onLogChanged", typedEvent);
      walkPlayback.onLogChanged();
    }
    afterEvent();
  }

  public void handleEvent(AssistantModeChosen typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(localAgentSession, "localAgentSession", "onModeChosen", typedEvent);
    isDirty_localAgentSession = localAgentSession.onModeChosen(typedEvent);
    auditInvocation(assistantMode, "assistantMode", "onModeChosen", typedEvent);
    isDirty_assistantMode = assistantMode.onModeChosen(typedEvent);
    auditInvocation(assistantLoop, "assistantLoop", "onModeChosen", typedEvent);
    isDirty_assistantLoop = assistantLoop.onModeChosen(typedEvent);
    auditInvocation(startPagePrompt, "startPagePrompt", "onModeChosen", typedEvent);
    startPagePrompt.onModeChosen(typedEvent);
    if (guardCheck_walkPlayback()) {
      auditInvocation(walkPlayback, "walkPlayback", "onLogChanged", typedEvent);
      walkPlayback.onLogChanged();
    }
    afterEvent();
  }

  public void handleEvent(AssistantNewChatRequested typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(assistantLoop, "assistantLoop", "onNewChatRequested", typedEvent);
    isDirty_assistantLoop = assistantLoop.onNewChatRequested(typedEvent);
    if (guardCheck_walkPlayback()) {
      auditInvocation(walkPlayback, "walkPlayback", "onLogChanged", typedEvent);
      walkPlayback.onLogChanged();
    }
    afterEvent();
  }

  public void handleEvent(AssistantOpenApplied typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(assistantLoop, "assistantLoop", "onAssistantOpenApplied", typedEvent);
    isDirty_assistantLoop = assistantLoop.onAssistantOpenApplied(typedEvent);
    if (guardCheck_walkPlayback()) {
      auditInvocation(walkPlayback, "walkPlayback", "onLogChanged", typedEvent);
      walkPlayback.onLogChanged();
    }
    afterEvent();
  }

  public void handleEvent(AssistantOpenApplyFailed typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(assistantLoop, "assistantLoop", "onAssistantOpenApplyFailed", typedEvent);
    isDirty_assistantLoop = assistantLoop.onAssistantOpenApplyFailed(typedEvent);
    if (guardCheck_walkPlayback()) {
      auditInvocation(walkPlayback, "walkPlayback", "onLogChanged", typedEvent);
      walkPlayback.onLogChanged();
    }
    afterEvent();
  }

  public void handleEvent(AssistantProfileApplied typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(localAgentSession, "localAgentSession", "onProfileApplied", typedEvent);
    isDirty_localAgentSession = localAgentSession.onProfileApplied(typedEvent);
    auditInvocation(aiSetup, "aiSetup", "onProfileApplied", typedEvent);
    isDirty_aiSetup = aiSetup.onProfileApplied(typedEvent);
    auditInvocation(startPagePrompt, "startPagePrompt", "onProfileApplied", typedEvent);
    startPagePrompt.onProfileApplied(typedEvent);
    if (guardCheck_walkPlayback()) {
      auditInvocation(walkPlayback, "walkPlayback", "onLogChanged", typedEvent);
      walkPlayback.onLogChanged();
    }
    afterEvent();
  }

  public void handleEvent(AssistantProfileChosen typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(aiSetup, "aiSetup", "onProfileChosen", typedEvent);
    isDirty_aiSetup = aiSetup.onProfileChosen(typedEvent);
    afterEvent();
  }

  public void handleEvent(AssistantProfileObserved typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(aiSetup, "aiSetup", "onProfileObserved", typedEvent);
    isDirty_aiSetup = aiSetup.onProfileObserved(typedEvent);
    auditInvocation(startPagePrompt, "startPagePrompt", "onProfileObserved", typedEvent);
    startPagePrompt.onProfileObserved(typedEvent);
    afterEvent();
  }

  public void handleEvent(AssistantProfilesShown typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(aiSetup, "aiSetup", "onProfilesShown", typedEvent);
    isDirty_aiSetup = aiSetup.onProfilesShown(typedEvent);
    afterEvent();
  }

  public void handleEvent(AssistantSendRequested typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(assistantLoop, "assistantLoop", "onSendRequested", typedEvent);
    isDirty_assistantLoop = assistantLoop.onSendRequested(typedEvent);
    auditInvocation(aiSetup, "aiSetup", "onAssistantSend", typedEvent);
    isDirty_aiSetup = aiSetup.onAssistantSend(typedEvent);
    if (guardCheck_walkPlayback()) {
      auditInvocation(walkPlayback, "walkPlayback", "onLogChanged", typedEvent);
      walkPlayback.onLogChanged();
    }
    auditInvocation(walkPlayback, "walkPlayback", "onAssistantSendRequested", typedEvent);
    walkPlayback.onAssistantSendRequested(typedEvent);
    afterEvent();
  }

  public void handleEvent(AssistantTabShown typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(assistantLoop, "assistantLoop", "onAssistantTabShown", typedEvent);
    isDirty_assistantLoop = assistantLoop.onAssistantTabShown(typedEvent);
    if (guardCheck_walkPlayback()) {
      auditInvocation(walkPlayback, "walkPlayback", "onLogChanged", typedEvent);
      walkPlayback.onLogChanged();
    }
    afterEvent();
  }

  public void handleEvent(BridgeObserved typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(aiSetup, "aiSetup", "onBridgeObserved", typedEvent);
    isDirty_aiSetup = aiSetup.onBridgeObserved(typedEvent);
    afterEvent();
  }

  public void handleEvent(BundleCaptureRequested typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(evidenceCapture, "evidenceCapture", "onBundleCaptureRequested", typedEvent);
    evidenceCapture.onBundleCaptureRequested(typedEvent);
    afterEvent();
  }

  public void handleEvent(BundleDeleted typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(evidenceCapture, "evidenceCapture", "onBundleDeleted", typedEvent);
    evidenceCapture.onBundleDeleted(typedEvent);
    afterEvent();
  }

  public void handleEvent(BundleRootsRestored typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(bundleAnchor, "bundleAnchor", "onBundleRootsRestored", typedEvent);
    bundleAnchor.onBundleRootsRestored(typedEvent);
    afterEvent();
  }

  public void handleEvent(BundleWriteFailed typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(evidenceCapture, "evidenceCapture", "onBundleWriteFailed", typedEvent);
    evidenceCapture.onBundleWriteFailed(typedEvent);
    afterEvent();
  }

  public void handleEvent(BundleWritten typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(evidenceCapture, "evidenceCapture", "onBundleWritten", typedEvent);
    evidenceCapture.onBundleWritten(typedEvent);
    afterEvent();
  }

  public void handleEvent(CanvasLeftAssistant typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(assistantLoop, "assistantLoop", "onCanvasLeftAssistant", typedEvent);
    isDirty_assistantLoop = assistantLoop.onCanvasLeftAssistant(typedEvent);
    if (guardCheck_walkPlayback()) {
      auditInvocation(walkPlayback, "walkPlayback", "onLogChanged", typedEvent);
      walkPlayback.onLogChanged();
    }
    afterEvent();
  }

  public void handleEvent(CaptureStarted typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(evidenceCapture, "evidenceCapture", "onCaptureStarted", typedEvent);
    evidenceCapture.onCaptureStarted(typedEvent);
    afterEvent();
  }

  public void handleEvent(ClaudeAuthObserved typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(aiSetup, "aiSetup", "onClaudeAuthObserved", typedEvent);
    isDirty_aiSetup = aiSetup.onClaudeAuthObserved(typedEvent);
    afterEvent();
  }

  public void handleEvent(ClaudeObserved typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(aiSetup, "aiSetup", "onClaudeObserved", typedEvent);
    isDirty_aiSetup = aiSetup.onClaudeObserved(typedEvent);
    afterEvent();
  }

  public void handleEvent(CloseRequested typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(operationGate, "operationGate", "onCloseRequested", typedEvent);
    isDirty_operationGate = operationGate.onCloseRequested(typedEvent);
    if (guardCheck_auditInstallation()) {
      auditInvocation(auditInstallation, "auditInstallation", "recomputeOnStateChange", typedEvent);
      isDirty_auditInstallation = auditInstallation.recomputeOnStateChange();
    }
    if (guardCheck_logEvidence()) {
      auditInvocation(logEvidence, "logEvidence", "onOpenLogChanged", typedEvent);
      logEvidence.onOpenLogChanged();
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
    if (guardCheck_recordsView()) {
      auditInvocation(recordsView, "recordsView", "onOpenLogChanged", typedEvent);
      isDirty_recordsView = recordsView.onOpenLogChanged();
    }
    auditInvocation(assistantLoop, "assistantLoop", "onCloseRequested", typedEvent);
    isDirty_assistantLoop = assistantLoop.onCloseRequested(typedEvent);
    if (guardCheck_walkPlayback()) {
      auditInvocation(walkPlayback, "walkPlayback", "onLogChanged", typedEvent);
      walkPlayback.onLogChanged();
    }
    afterEvent();
  }

  public void handleEvent(DraftPlaced typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(localAgentSession, "localAgentSession", "onDraftPlaced", typedEvent);
    isDirty_localAgentSession = localAgentSession.onDraftPlaced(typedEvent);
    afterEvent();
  }

  public void handleEvent(EffectFailed typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(operationGate, "operationGate", "onEffectFailed", typedEvent);
    isDirty_operationGate = operationGate.onEffectFailed(typedEvent);
    auditInvocation(effectOutcomes, "effectOutcomes", "onEffectFailed", typedEvent);
    effectOutcomes.onEffectFailed(typedEvent);
    if (guardCheck_auditInstallation()) {
      auditInvocation(auditInstallation, "auditInstallation", "recomputeOnStateChange", typedEvent);
      isDirty_auditInstallation = auditInstallation.recomputeOnStateChange();
    }
    if (guardCheck_logEvidence()) {
      auditInvocation(logEvidence, "logEvidence", "onOpenLogChanged", typedEvent);
      logEvidence.onOpenLogChanged();
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
    if (guardCheck_recordsView()) {
      auditInvocation(recordsView, "recordsView", "onOpenLogChanged", typedEvent);
      isDirty_recordsView = recordsView.onOpenLogChanged();
    }
    auditInvocation(assistantLoop, "assistantLoop", "onEffectFailed", typedEvent);
    isDirty_assistantLoop = assistantLoop.onEffectFailed(typedEvent);
    auditInvocation(sessionBoundary, "sessionBoundary", "onEffectFailed", typedEvent);
    sessionBoundary.onEffectFailed(typedEvent);
    if (guardCheck_walkPlayback()) {
      auditInvocation(walkPlayback, "walkPlayback", "onLogChanged", typedEvent);
      walkPlayback.onLogChanged();
    }
    afterEvent();
  }

  public void handleEvent(ExampleDeadlinePassed typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(exampleDownload, "exampleDownload", "onDeadline", typedEvent);
    exampleDownload.onDeadline(typedEvent);
    afterEvent();
  }

  public void handleEvent(ExampleDownloadConfirmed typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(exampleDownload, "exampleDownload", "onConfirmed", typedEvent);
    exampleDownload.onConfirmed(typedEvent);
    afterEvent();
  }

  public void handleEvent(ExampleDownloadRequested typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(exampleDownload, "exampleDownload", "onRequested", typedEvent);
    exampleDownload.onRequested(typedEvent);
    afterEvent();
  }

  public void handleEvent(ExampleEffectStarted typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(exampleDownload, "exampleDownload", "onEffectStarted", typedEvent);
    exampleDownload.onEffectStarted(typedEvent);
    afterEvent();
  }

  public void handleEvent(ExampleFetched typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(exampleDownload, "exampleDownload", "onFetched", typedEvent);
    exampleDownload.onFetched(typedEvent);
    afterEvent();
  }

  public void handleEvent(ExampleUnpacked typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(exampleDownload, "exampleDownload", "onUnpacked", typedEvent);
    exampleDownload.onUnpacked(typedEvent);
    afterEvent();
  }

  public void handleEvent(ExternalAccessAnswered typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(actionListener, "actionListener", "onExternalAccessAnswered", typedEvent);
    isDirty_actionListener = actionListener.onExternalAccessAnswered(typedEvent);
    auditInvocation(aiSetup, "aiSetup", "onExternalAccessAnswered", typedEvent);
    isDirty_aiSetup = aiSetup.onExternalAccessAnswered(typedEvent);
    if (guardCheck_walkPlayback()) {
      auditInvocation(walkPlayback, "walkPlayback", "onLogChanged", typedEvent);
      walkPlayback.onLogChanged();
    }
    afterEvent();
  }

  public void handleEvent(ExternalAccessChosen typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(actionListener, "actionListener", "onExternalChosen", typedEvent);
    isDirty_actionListener = actionListener.onExternalChosen(typedEvent);
    auditInvocation(aiSetup, "aiSetup", "onExternalChosen", typedEvent);
    isDirty_aiSetup = aiSetup.onExternalChosen(typedEvent);
    if (guardCheck_walkPlayback()) {
      auditInvocation(walkPlayback, "walkPlayback", "onLogChanged", typedEvent);
      walkPlayback.onLogChanged();
    }
    afterEvent();
  }

  public void handleEvent(ExternalAccessRequested typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(actionListener, "actionListener", "onExternalAccessRequested", typedEvent);
    isDirty_actionListener = actionListener.onExternalAccessRequested(typedEvent);
    auditInvocation(aiSetup, "aiSetup", "onExternalAccessRequested", typedEvent);
    isDirty_aiSetup = aiSetup.onExternalAccessRequested(typedEvent);
    if (guardCheck_walkPlayback()) {
      auditInvocation(walkPlayback, "walkPlayback", "onLogChanged", typedEvent);
      walkPlayback.onLogChanged();
    }
    afterEvent();
  }

  public void handleEvent(ExternalClientSeen typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(aiSetup, "aiSetup", "onClientSeen", typedEvent);
    isDirty_aiSetup = aiSetup.onClientSeen(typedEvent);
    afterEvent();
  }

  public void handleEvent(FollowSet typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(evidenceCapture, "evidenceCapture", "onFollowSet", typedEvent);
    evidenceCapture.onFollowSet(typedEvent);
    afterEvent();
  }

  public void handleEvent(FollowToggled typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(openLog, "openLog", "onFollowToggled", typedEvent);
    isDirty_openLog = openLog.onFollowToggled(typedEvent);
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
    if (guardCheck_auditInstallation()) {
      auditInvocation(auditInstallation, "auditInstallation", "recomputeOnStateChange", typedEvent);
      isDirty_auditInstallation = auditInstallation.recomputeOnStateChange();
    }
    if (guardCheck_logEvidence()) {
      auditInvocation(logEvidence, "logEvidence", "onOpenLogChanged", typedEvent);
      logEvidence.onOpenLogChanged();
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
    if (guardCheck_recordsView()) {
      auditInvocation(recordsView, "recordsView", "onOpenLogChanged", typedEvent);
      isDirty_recordsView = recordsView.onOpenLogChanged();
    }
    auditInvocation(assistantLoop, "assistantLoop", "onGraphCleared", typedEvent);
    isDirty_assistantLoop = assistantLoop.onGraphCleared(typedEvent);
    if (guardCheck_walkPlayback()) {
      auditInvocation(walkPlayback, "walkPlayback", "onLogChanged", typedEvent);
      walkPlayback.onLogChanged();
    }
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
    if (guardCheck_auditInstallation()) {
      auditInvocation(auditInstallation, "auditInstallation", "recomputeOnStateChange", typedEvent);
      isDirty_auditInstallation = auditInstallation.recomputeOnStateChange();
    }
    if (guardCheck_logEvidence()) {
      auditInvocation(logEvidence, "logEvidence", "onOpenLogChanged", typedEvent);
      logEvidence.onOpenLogChanged();
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
    if (guardCheck_recordsView()) {
      auditInvocation(recordsView, "recordsView", "onOpenLogChanged", typedEvent);
      isDirty_recordsView = recordsView.onOpenLogChanged();
    }
    auditInvocation(assistantLoop, "assistantLoop", "onGraphClosed", typedEvent);
    isDirty_assistantLoop = assistantLoop.onGraphClosed(typedEvent);
    if (guardCheck_walkPlayback()) {
      auditInvocation(walkPlayback, "walkPlayback", "onLogChanged", typedEvent);
      walkPlayback.onLogChanged();
    }
    afterEvent();
  }

  public void handleEvent(GraphOpened typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(operationGate, "operationGate", "onGraphOpened", typedEvent);
    isDirty_operationGate = operationGate.onGraphOpened(typedEvent);
    auditInvocation(openGraph, "openGraph", "onGraphOpened", typedEvent);
    isDirty_openGraph = openGraph.onGraphOpened(typedEvent);
    if (guardCheck_auditInstallation()) {
      auditInvocation(auditInstallation, "auditInstallation", "recomputeOnStateChange", typedEvent);
      isDirty_auditInstallation = auditInstallation.recomputeOnStateChange();
    }
    if (guardCheck_logEvidence()) {
      auditInvocation(logEvidence, "logEvidence", "onOpenLogChanged", typedEvent);
      logEvidence.onOpenLogChanged();
    }
    auditInvocation(onboardingProposal, "onboardingProposal", "onGraphOpened", typedEvent);
    isDirty_onboardingProposal = onboardingProposal.onGraphOpened(typedEvent);
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
    if (guardCheck_recordsView()) {
      auditInvocation(recordsView, "recordsView", "onOpenLogChanged", typedEvent);
      isDirty_recordsView = recordsView.onOpenLogChanged();
    }
    auditInvocation(assistantLoop, "assistantLoop", "onGraphOpened", typedEvent);
    isDirty_assistantLoop = assistantLoop.onGraphOpened(typedEvent);
    if (guardCheck_walkPlayback()) {
      auditInvocation(walkPlayback, "walkPlayback", "onLogChanged", typedEvent);
      walkPlayback.onLogChanged();
    }
    auditInvocation(projectReopenOffer, "projectReopenOffer", "onGraphOpened", typedEvent);
    projectReopenOffer.onGraphOpened(typedEvent);
    afterEvent();
  }

  public void handleEvent(HostedCallAbandoned typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(
        localAgentPermission, "localAgentPermission", "onHostedCallAbandoned", typedEvent);
    isDirty_localAgentPermission = localAgentPermission.onHostedCallAbandoned(typedEvent);
    afterEvent();
  }

  public void handleEvent(HostedCallPresented typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(localAgentPermission, "localAgentPermission", "onHostedCall", typedEvent);
    isDirty_localAgentPermission = localAgentPermission.onHostedCall(typedEvent);
    afterEvent();
  }

  public void handleEvent(HostedCallRefusedByServer typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(localAgentSession, "localAgentSession", "onHostedRefusedByServer", typedEvent);
    isDirty_localAgentSession = localAgentSession.onHostedRefusedByServer(typedEvent);
    afterEvent();
  }

  public void handleEvent(HostedReportWritten typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(
        localAgentPermission, "localAgentPermission", "onHostedReportWritten", typedEvent);
    isDirty_localAgentPermission = localAgentPermission.onHostedReportWritten(typedEvent);
    afterEvent();
  }

  public void handleEvent(LocalAgentAskAgainRequested typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(localAgentSession, "localAgentSession", "onAskAgain", typedEvent);
    isDirty_localAgentSession = localAgentSession.onAskAgain(typedEvent);
    if (guardCheck_walkPlayback()) {
      auditInvocation(walkPlayback, "walkPlayback", "onLogChanged", typedEvent);
      walkPlayback.onLogChanged();
    }
    afterEvent();
  }

  public void handleEvent(LocalAgentBridgeServed typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(actionListener, "actionListener", "onBridgeServed", typedEvent);
    isDirty_actionListener = actionListener.onBridgeServed(typedEvent);
    auditInvocation(localAgentSession, "localAgentSession", "onBridgeServed", typedEvent);
    isDirty_localAgentSession = localAgentSession.onBridgeServed(typedEvent);
    if (guardCheck_walkPlayback()) {
      auditInvocation(walkPlayback, "walkPlayback", "onLogChanged", typedEvent);
      walkPlayback.onLogChanged();
    }
    afterEvent();
  }

  public void handleEvent(LocalAgentCallAdmissionRequested typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(localAgentSession, "localAgentSession", "onCallAdmission", typedEvent);
    isDirty_localAgentSession = localAgentSession.onCallAdmission(typedEvent);
    afterEvent();
  }

  public void handleEvent(LocalAgentConsentChanged typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(localAgentPermission, "localAgentPermission", "onConsentChanged", typedEvent);
    isDirty_localAgentPermission = localAgentPermission.onConsentChanged(typedEvent);
    auditInvocation(localAgentSession, "localAgentSession", "onConsentChanged", typedEvent);
    isDirty_localAgentSession = localAgentSession.onConsentChanged(typedEvent);
    if (guardCheck_walkPlayback()) {
      auditInvocation(walkPlayback, "walkPlayback", "onLogChanged", typedEvent);
      walkPlayback.onLogChanged();
    }
    afterEvent();
  }

  public void handleEvent(LocalAgentCostObserved typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(localAgentSession, "localAgentSession", "onCostObserved", typedEvent);
    isDirty_localAgentSession = localAgentSession.onCostObserved(typedEvent);
    if (guardCheck_walkPlayback()) {
      auditInvocation(walkPlayback, "walkPlayback", "onLogChanged", typedEvent);
      walkPlayback.onLogChanged();
    }
    afterEvent();
  }

  public void handleEvent(LocalAgentDiagnostics typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(localAgentSession, "localAgentSession", "onDiagnostics", typedEvent);
    isDirty_localAgentSession = localAgentSession.onDiagnostics(typedEvent);
    if (guardCheck_walkPlayback()) {
      auditInvocation(walkPlayback, "walkPlayback", "onLogChanged", typedEvent);
      walkPlayback.onLogChanged();
    }
    afterEvent();
  }

  public void handleEvent(LocalAgentDraftComposed typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(localAgentSession, "localAgentSession", "onDraftComposed", typedEvent);
    isDirty_localAgentSession = localAgentSession.onDraftComposed(typedEvent);
    if (guardCheck_walkPlayback()) {
      auditInvocation(walkPlayback, "walkPlayback", "onLogChanged", typedEvent);
      walkPlayback.onLogChanged();
    }
    afterEvent();
  }

  public void handleEvent(LocalAgentEffectStarted typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(localAgentSession, "localAgentSession", "onEffectStarted", typedEvent);
    isDirty_localAgentSession = localAgentSession.onEffectStarted(typedEvent);
    afterEvent();
  }

  public void handleEvent(LocalAgentExited typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(localAgentPermission, "localAgentPermission", "onExited", typedEvent);
    isDirty_localAgentPermission = localAgentPermission.onExited(typedEvent);
    auditInvocation(localAgentSession, "localAgentSession", "onExited", typedEvent);
    isDirty_localAgentSession = localAgentSession.onExited(typedEvent);
    if (guardCheck_walkPlayback()) {
      auditInvocation(walkPlayback, "walkPlayback", "onLogChanged", typedEvent);
      walkPlayback.onLogChanged();
    }
    afterEvent();
  }

  public void handleEvent(LocalAgentFolderAnswered typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(localAgentSession, "localAgentSession", "onFolderAnswered", typedEvent);
    isDirty_localAgentSession = localAgentSession.onFolderAnswered(typedEvent);
    if (guardCheck_walkPlayback()) {
      auditInvocation(walkPlayback, "walkPlayback", "onLogChanged", typedEvent);
      walkPlayback.onLogChanged();
    }
    afterEvent();
  }

  public void handleEvent(LocalAgentGrantExpired typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(localAgentPermission, "localAgentPermission", "onGrantExpired", typedEvent);
    isDirty_localAgentPermission = localAgentPermission.onGrantExpired(typedEvent);
    if (guardCheck_walkPlayback()) {
      auditInvocation(walkPlayback, "walkPlayback", "onLogChanged", typedEvent);
      walkPlayback.onLogChanged();
    }
    afterEvent();
  }

  public void handleEvent(LocalAgentHookObserved typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(localAgentSession, "localAgentSession", "onHookObserved", typedEvent);
    isDirty_localAgentSession = localAgentSession.onHookObserved(typedEvent);
    if (guardCheck_walkPlayback()) {
      auditInvocation(walkPlayback, "walkPlayback", "onLogChanged", typedEvent);
      walkPlayback.onLogChanged();
    }
    afterEvent();
  }

  public void handleEvent(LocalAgentInitObserved typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(localAgentSession, "localAgentSession", "onInitObserved", typedEvent);
    isDirty_localAgentSession = localAgentSession.onInitObserved(typedEvent);
    if (guardCheck_walkPlayback()) {
      auditInvocation(walkPlayback, "walkPlayback", "onLogChanged", typedEvent);
      walkPlayback.onLogChanged();
    }
    afterEvent();
  }

  public void handleEvent(LocalAgentInitialized typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(localAgentSession, "localAgentSession", "onInitialized", typedEvent);
    isDirty_localAgentSession = localAgentSession.onInitialized(typedEvent);
    if (guardCheck_walkPlayback()) {
      auditInvocation(walkPlayback, "walkPlayback", "onLogChanged", typedEvent);
      walkPlayback.onLogChanged();
    }
    afterEvent();
  }

  public void handleEvent(LocalAgentInterruptAcked typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(localAgentSession, "localAgentSession", "onInterruptAcked", typedEvent);
    isDirty_localAgentSession = localAgentSession.onInterruptAcked(typedEvent);
    if (guardCheck_walkPlayback()) {
      auditInvocation(walkPlayback, "walkPlayback", "onLogChanged", typedEvent);
      walkPlayback.onLogChanged();
    }
    afterEvent();
  }

  public void handleEvent(LocalAgentModeObserved typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(localAgentSession, "localAgentSession", "onModeObserved", typedEvent);
    isDirty_localAgentSession = localAgentSession.onModeObserved(typedEvent);
    if (guardCheck_walkPlayback()) {
      auditInvocation(walkPlayback, "walkPlayback", "onLogChanged", typedEvent);
      walkPlayback.onLogChanged();
    }
    afterEvent();
  }

  public void handleEvent(LocalAgentNewChatRequested typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(localAgentPermission, "localAgentPermission", "onNewChat", typedEvent);
    isDirty_localAgentPermission = localAgentPermission.onNewChat(typedEvent);
    auditInvocation(localAgentSession, "localAgentSession", "onNewChatRequested", typedEvent);
    isDirty_localAgentSession = localAgentSession.onNewChatRequested(typedEvent);
    if (guardCheck_walkPlayback()) {
      auditInvocation(walkPlayback, "walkPlayback", "onLogChanged", typedEvent);
      walkPlayback.onLogChanged();
    }
    afterEvent();
  }

  public void handleEvent(LocalAgentNoted typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(localAgentSession, "localAgentSession", "onNoted", typedEvent);
    isDirty_localAgentSession = localAgentSession.onNoted(typedEvent);
    if (guardCheck_walkPlayback()) {
      auditInvocation(walkPlayback, "walkPlayback", "onLogChanged", typedEvent);
      walkPlayback.onLogChanged();
    }
    afterEvent();
  }

  public void handleEvent(LocalAgentPermissionAnswered typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(localAgentPermission, "localAgentPermission", "onAnswered", typedEvent);
    isDirty_localAgentPermission = localAgentPermission.onAnswered(typedEvent);
    afterEvent();
  }

  public void handleEvent(LocalAgentPermissionCancelled typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(localAgentPermission, "localAgentPermission", "onCancelled", typedEvent);
    isDirty_localAgentPermission = localAgentPermission.onCancelled(typedEvent);
    if (guardCheck_walkPlayback()) {
      auditInvocation(walkPlayback, "walkPlayback", "onLogChanged", typedEvent);
      walkPlayback.onLogChanged();
    }
    afterEvent();
  }

  public void handleEvent(LocalAgentPermissionChosen typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(localAgentPermission, "localAgentPermission", "onChosen", typedEvent);
    isDirty_localAgentPermission = localAgentPermission.onChosen(typedEvent);
    if (guardCheck_walkPlayback()) {
      auditInvocation(walkPlayback, "walkPlayback", "onLogChanged", typedEvent);
      walkPlayback.onLogChanged();
    }
    afterEvent();
  }

  public void handleEvent(LocalAgentPermissionDeadline typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(localAgentPermission, "localAgentPermission", "onDeadline", typedEvent);
    isDirty_localAgentPermission = localAgentPermission.onDeadline(typedEvent);
    if (guardCheck_walkPlayback()) {
      auditInvocation(walkPlayback, "walkPlayback", "onLogChanged", typedEvent);
      walkPlayback.onLogChanged();
    }
    afterEvent();
  }

  public void handleEvent(LocalAgentPermissionRequested typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(localAgentPermission, "localAgentPermission", "onRequested", typedEvent);
    isDirty_localAgentPermission = localAgentPermission.onRequested(typedEvent);
    if (guardCheck_walkPlayback()) {
      auditInvocation(walkPlayback, "walkPlayback", "onLogChanged", typedEvent);
      walkPlayback.onLogChanged();
    }
    afterEvent();
  }

  public void handleEvent(LocalAgentRestartAnswered typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(localAgentSession, "localAgentSession", "onRestartAnswered", typedEvent);
    isDirty_localAgentSession = localAgentSession.onRestartAnswered(typedEvent);
    if (guardCheck_walkPlayback()) {
      auditInvocation(walkPlayback, "walkPlayback", "onLogChanged", typedEvent);
      walkPlayback.onLogChanged();
    }
    afterEvent();
  }

  public void handleEvent(LocalAgentRestartOfferRequested typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(localAgentSession, "localAgentSession", "onRestartOfferRequested", typedEvent);
    isDirty_localAgentSession = localAgentSession.onRestartOfferRequested(typedEvent);
    if (guardCheck_walkPlayback()) {
      auditInvocation(walkPlayback, "walkPlayback", "onLogChanged", typedEvent);
      walkPlayback.onLogChanged();
    }
    afterEvent();
  }

  public void handleEvent(LocalAgentSendRequested typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(localAgentSession, "localAgentSession", "onSendRequested", typedEvent);
    isDirty_localAgentSession = localAgentSession.onSendRequested(typedEvent);
    auditInvocation(aiSetup, "aiSetup", "onLocalAgentSend", typedEvent);
    isDirty_aiSetup = aiSetup.onLocalAgentSend(typedEvent);
    if (guardCheck_walkPlayback()) {
      auditInvocation(walkPlayback, "walkPlayback", "onLogChanged", typedEvent);
      walkPlayback.onLogChanged();
    }
    afterEvent();
  }

  public void handleEvent(LocalAgentSetupDirectoryChosen typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(localAgentSession, "localAgentSession", "onSetupDirectoryChosen", typedEvent);
    isDirty_localAgentSession = localAgentSession.onSetupDirectoryChosen(typedEvent);
    if (guardCheck_walkPlayback()) {
      auditInvocation(walkPlayback, "walkPlayback", "onLogChanged", typedEvent);
      walkPlayback.onLogChanged();
    }
    afterEvent();
  }

  public void handleEvent(LocalAgentSpawned typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(localAgentSession, "localAgentSession", "onSpawned", typedEvent);
    isDirty_localAgentSession = localAgentSession.onSpawned(typedEvent);
    auditInvocation(starterProject, "starterProject", "onSpawned", typedEvent);
    isDirty_starterProject = starterProject.onSpawned(typedEvent);
    if (guardCheck_walkPlayback()) {
      auditInvocation(walkPlayback, "walkPlayback", "onLogChanged", typedEvent);
      walkPlayback.onLogChanged();
    }
    afterEvent();
  }

  public void handleEvent(LocalAgentStartDeadline typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(localAgentSession, "localAgentSession", "onStartDeadline", typedEvent);
    isDirty_localAgentSession = localAgentSession.onStartDeadline(typedEvent);
    if (guardCheck_walkPlayback()) {
      auditInvocation(walkPlayback, "walkPlayback", "onLogChanged", typedEvent);
      walkPlayback.onLogChanged();
    }
    afterEvent();
  }

  public void handleEvent(LocalAgentStartRequested typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(localAgentSession, "localAgentSession", "onStartRequested", typedEvent);
    isDirty_localAgentSession = localAgentSession.onStartRequested(typedEvent);
    auditInvocation(aiSetup, "aiSetup", "onStartRequested", typedEvent);
    isDirty_aiSetup = aiSetup.onStartRequested(typedEvent);
    if (guardCheck_walkPlayback()) {
      auditInvocation(walkPlayback, "walkPlayback", "onLogChanged", typedEvent);
      walkPlayback.onLogChanged();
    }
    afterEvent();
  }

  public void handleEvent(LocalAgentStatusObserved typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(localAgentSession, "localAgentSession", "onStatusObserved", typedEvent);
    isDirty_localAgentSession = localAgentSession.onStatusObserved(typedEvent);
    if (guardCheck_walkPlayback()) {
      auditInvocation(walkPlayback, "walkPlayback", "onLogChanged", typedEvent);
      walkPlayback.onLogChanged();
    }
    afterEvent();
  }

  public void handleEvent(LocalAgentStopDeadline typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(localAgentSession, "localAgentSession", "onStopDeadline", typedEvent);
    isDirty_localAgentSession = localAgentSession.onStopDeadline(typedEvent);
    if (guardCheck_walkPlayback()) {
      auditInvocation(walkPlayback, "walkPlayback", "onLogChanged", typedEvent);
      walkPlayback.onLogChanged();
    }
    afterEvent();
  }

  public void handleEvent(LocalAgentStopRequested typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(localAgentPermission, "localAgentPermission", "onStopRequested", typedEvent);
    isDirty_localAgentPermission = localAgentPermission.onStopRequested(typedEvent);
    auditInvocation(localAgentSession, "localAgentSession", "onStopRequested", typedEvent);
    isDirty_localAgentSession = localAgentSession.onStopRequested(typedEvent);
    if (guardCheck_walkPlayback()) {
      auditInvocation(walkPlayback, "walkPlayback", "onLogChanged", typedEvent);
      walkPlayback.onLogChanged();
    }
    afterEvent();
  }

  public void handleEvent(LocalAgentSwitchChosen typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(localAgentSession, "localAgentSession", "onSwitchChosen", typedEvent);
    isDirty_localAgentSession = localAgentSession.onSwitchChosen(typedEvent);
    auditInvocation(sessionBoundary, "sessionBoundary", "onSwitchChosen", typedEvent);
    sessionBoundary.onSwitchChosen(typedEvent);
    if (guardCheck_walkPlayback()) {
      auditInvocation(walkPlayback, "walkPlayback", "onLogChanged", typedEvent);
      walkPlayback.onLogChanged();
    }
    afterEvent();
  }

  public void handleEvent(LocalAgentTextUpdated typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(localAgentSession, "localAgentSession", "onTextUpdated", typedEvent);
    isDirty_localAgentSession = localAgentSession.onTextUpdated(typedEvent);
    if (guardCheck_walkPlayback()) {
      auditInvocation(walkPlayback, "walkPlayback", "onLogChanged", typedEvent);
      walkPlayback.onLogChanged();
    }
    afterEvent();
  }

  public void handleEvent(LocalAgentTurnEnded typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(localAgentPermission, "localAgentPermission", "onTurnEnded", typedEvent);
    isDirty_localAgentPermission = localAgentPermission.onTurnEnded(typedEvent);
    auditInvocation(localAgentSession, "localAgentSession", "onTurnEnded", typedEvent);
    isDirty_localAgentSession = localAgentSession.onTurnEnded(typedEvent);
    if (guardCheck_walkPlayback()) {
      auditInvocation(walkPlayback, "walkPlayback", "onLogChanged", typedEvent);
      walkPlayback.onLogChanged();
    }
    afterEvent();
  }

  public void handleEvent(LocalAgentTurnStarted typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(localAgentSession, "localAgentSession", "onTurnStarted", typedEvent);
    isDirty_localAgentSession = localAgentSession.onTurnStarted(typedEvent);
    if (guardCheck_walkPlayback()) {
      auditInvocation(walkPlayback, "walkPlayback", "onLogChanged", typedEvent);
      walkPlayback.onLogChanged();
    }
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
    if (guardCheck_logEvidence()) {
      auditInvocation(logEvidence, "logEvidence", "onOpenLogChanged", typedEvent);
      logEvidence.onOpenLogChanged();
    }
    auditInvocation(logEvidence, "logEvidence", "onLogAppended", typedEvent);
    logEvidence.onLogAppended(typedEvent);
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
    if (guardCheck_recordsView()) {
      auditInvocation(recordsView, "recordsView", "onOpenLogChanged", typedEvent);
      isDirty_recordsView = recordsView.onOpenLogChanged();
    }
    auditInvocation(recordsView, "recordsView", "onLogAppended", typedEvent);
    isDirty_recordsView = recordsView.onLogAppended(typedEvent);
    if (guardCheck_walkPlayback()) {
      auditInvocation(walkPlayback, "walkPlayback", "onLogChanged", typedEvent);
      walkPlayback.onLogChanged();
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
    if (guardCheck_logEvidence()) {
      auditInvocation(logEvidence, "logEvidence", "onOpenLogChanged", typedEvent);
      logEvidence.onOpenLogChanged();
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
    if (guardCheck_recordsView()) {
      auditInvocation(recordsView, "recordsView", "onOpenLogChanged", typedEvent);
      isDirty_recordsView = recordsView.onOpenLogChanged();
    }
    auditInvocation(assistantLoop, "assistantLoop", "onLogCleared", typedEvent);
    isDirty_assistantLoop = assistantLoop.onLogCleared(typedEvent);
    if (guardCheck_walkPlayback()) {
      auditInvocation(walkPlayback, "walkPlayback", "onLogChanged", typedEvent);
      walkPlayback.onLogChanged();
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
    if (guardCheck_logEvidence()) {
      auditInvocation(logEvidence, "logEvidence", "onOpenLogChanged", typedEvent);
      logEvidence.onOpenLogChanged();
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
    if (guardCheck_recordsView()) {
      auditInvocation(recordsView, "recordsView", "onOpenLogChanged", typedEvent);
      isDirty_recordsView = recordsView.onOpenLogChanged();
    }
    auditInvocation(assistantLoop, "assistantLoop", "onLogClosed", typedEvent);
    isDirty_assistantLoop = assistantLoop.onLogClosed(typedEvent);
    if (guardCheck_walkPlayback()) {
      auditInvocation(walkPlayback, "walkPlayback", "onLogChanged", typedEvent);
      walkPlayback.onLogChanged();
    }
    afterEvent();
  }

  public void handleEvent(LogContentObserved typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(logEvidence, "logEvidence", "onLogContentObserved", typedEvent);
    logEvidence.onLogContentObserved(typedEvent);
    afterEvent();
  }

  public void handleEvent(LogIdentityObserved typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(openLog, "openLog", "onLogIdentityObserved", typedEvent);
    isDirty_openLog = openLog.onLogIdentityObserved(typedEvent);
    commonDispatchTail_1(typedEvent);
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
    if (guardCheck_auditInstallation()) {
      auditInvocation(auditInstallation, "auditInstallation", "recomputeOnStateChange", typedEvent);
      isDirty_auditInstallation = auditInstallation.recomputeOnStateChange();
    }
    if (guardCheck_logEvidence()) {
      auditInvocation(logEvidence, "logEvidence", "onOpenLogChanged", typedEvent);
      logEvidence.onOpenLogChanged();
    }
    auditInvocation(onboardingProposal, "onboardingProposal", "onLogOpenFailed", typedEvent);
    isDirty_onboardingProposal = onboardingProposal.onLogOpenFailed(typedEvent);
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
    if (guardCheck_recordsView()) {
      auditInvocation(recordsView, "recordsView", "onOpenLogChanged", typedEvent);
      isDirty_recordsView = recordsView.onOpenLogChanged();
    }
    auditInvocation(assistantLoop, "assistantLoop", "onLogOpenFailed", typedEvent);
    isDirty_assistantLoop = assistantLoop.onLogOpenFailed(typedEvent);
    if (guardCheck_walkPlayback()) {
      auditInvocation(walkPlayback, "walkPlayback", "onLogChanged", typedEvent);
      walkPlayback.onLogChanged();
    }
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
    if (guardCheck_logEvidence()) {
      auditInvocation(logEvidence, "logEvidence", "onOpenLogChanged", typedEvent);
      logEvidence.onOpenLogChanged();
    }
    auditInvocation(onboardingProposal, "onboardingProposal", "onLogOpened", typedEvent);
    isDirty_onboardingProposal = onboardingProposal.onLogOpened(typedEvent);
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
    if (guardCheck_recordsView()) {
      auditInvocation(recordsView, "recordsView", "onOpenLogChanged", typedEvent);
      isDirty_recordsView = recordsView.onOpenLogChanged();
    }
    auditInvocation(assistantLoop, "assistantLoop", "onLogOpened", typedEvent);
    isDirty_assistantLoop = assistantLoop.onLogOpened(typedEvent);
    auditInvocation(startPageNoLogHint, "startPageNoLogHint", "onLogOpened", typedEvent);
    isDirty_startPageNoLogHint = startPageNoLogHint.onLogOpened(typedEvent);
    if (guardCheck_walkPlayback()) {
      auditInvocation(walkPlayback, "walkPlayback", "onLogChanged", typedEvent);
      walkPlayback.onLogChanged();
    }
    afterEvent();
  }

  public void handleEvent(McpReadinessObserved typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(aiSetup, "aiSetup", "onReadiness", typedEvent);
    isDirty_aiSetup = aiSetup.onReadiness(typedEvent);
    afterEvent();
  }

  public void handleEvent(McpRegistrationChanged typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(aiSetup, "aiSetup", "onRegistrationChanged", typedEvent);
    isDirty_aiSetup = aiSetup.onRegistrationChanged(typedEvent);
    afterEvent();
  }

  public void handleEvent(McpRegistrationObserved typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(aiSetup, "aiSetup", "onRegistrationObserved", typedEvent);
    isDirty_aiSetup = aiSetup.onRegistrationObserved(typedEvent);
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
    if (guardCheck_logEvidence()) {
      auditInvocation(logEvidence, "logEvidence", "onOpenLogChanged", typedEvent);
      logEvidence.onOpenLogChanged();
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
    if (guardCheck_recordsView()) {
      auditInvocation(recordsView, "recordsView", "onOpenLogChanged", typedEvent);
      isDirty_recordsView = recordsView.onOpenLogChanged();
    }
    auditInvocation(assistantLoop, "assistantLoop", "onOpenLogRequested", typedEvent);
    isDirty_assistantLoop = assistantLoop.onOpenLogRequested(typedEvent);
    if (guardCheck_walkPlayback()) {
      auditInvocation(walkPlayback, "walkPlayback", "onLogChanged", typedEvent);
      walkPlayback.onLogChanged();
    }
    auditInvocation(projectReopenOffer, "projectReopenOffer", "onOpenLogRequested", typedEvent);
    projectReopenOffer.onOpenLogRequested(typedEvent);
    afterEvent();
  }

  public void handleEvent(OpenProjectRequested typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(operationGate, "operationGate", "onOpenProjectRequested", typedEvent);
    isDirty_operationGate = operationGate.onOpenProjectRequested(typedEvent);
    auditInvocation(openBundle, "openBundle", "onOpenProjectRequested", typedEvent);
    isDirty_openBundle = openBundle.onOpenProjectRequested(typedEvent);
    if (guardCheck_auditInstallation()) {
      auditInvocation(auditInstallation, "auditInstallation", "recomputeOnStateChange", typedEvent);
      isDirty_auditInstallation = auditInstallation.recomputeOnStateChange();
    }
    if (guardCheck_logEvidence()) {
      auditInvocation(logEvidence, "logEvidence", "onOpenLogChanged", typedEvent);
      logEvidence.onOpenLogChanged();
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
    if (guardCheck_recordsView()) {
      auditInvocation(recordsView, "recordsView", "onOpenLogChanged", typedEvent);
      isDirty_recordsView = recordsView.onOpenLogChanged();
    }
    auditInvocation(assistantLoop, "assistantLoop", "onOpenProjectRequested", typedEvent);
    isDirty_assistantLoop = assistantLoop.onOpenProjectRequested(typedEvent);
    auditInvocation(sessionBoundary, "sessionBoundary", "onOpenProjectRequested", typedEvent);
    sessionBoundary.onOpenProjectRequested(typedEvent);
    if (guardCheck_walkPlayback()) {
      auditInvocation(walkPlayback, "walkPlayback", "onLogChanged", typedEvent);
      walkPlayback.onLogChanged();
    }
    auditInvocation(projectReopenOffer, "projectReopenOffer", "onOpenProjectRequested", typedEvent);
    projectReopenOffer.onOpenProjectRequested(typedEvent);
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
    if (guardCheck_auditInstallation()) {
      auditInvocation(auditInstallation, "auditInstallation", "recomputeOnStateChange", typedEvent);
      isDirty_auditInstallation = auditInstallation.recomputeOnStateChange();
    }
    commonDispatchTail_1(typedEvent);
    afterEvent();
  }

  public void handleEvent(ProducerFindingsObserved typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(logEvidence, "logEvidence", "onProducerFindingsObserved", typedEvent);
    logEvidence.onProducerFindingsObserved(typedEvent);
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
    auditInvocation(openBundle, "openBundle", "onProfileApplied", typedEvent);
    isDirty_openBundle = openBundle.onProfileApplied(typedEvent);
    if (guardCheck_auditInstallation()) {
      auditInvocation(auditInstallation, "auditInstallation", "recomputeOnStateChange", typedEvent);
      isDirty_auditInstallation = auditInstallation.recomputeOnStateChange();
    }
    if (guardCheck_logEvidence()) {
      auditInvocation(logEvidence, "logEvidence", "onOpenLogChanged", typedEvent);
      logEvidence.onOpenLogChanged();
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
    if (guardCheck_recordsView()) {
      auditInvocation(recordsView, "recordsView", "onOpenLogChanged", typedEvent);
      isDirty_recordsView = recordsView.onOpenLogChanged();
    }
    auditInvocation(assistantLoop, "assistantLoop", "onProfileApplied", typedEvent);
    isDirty_assistantLoop = assistantLoop.onProfileApplied(typedEvent);
    auditInvocation(starterProject, "starterProject", "onProfileApplied", typedEvent);
    isDirty_starterProject = starterProject.onProfileApplied(typedEvent);
    auditInvocation(sessionBoundary, "sessionBoundary", "onProfileApplied", typedEvent);
    sessionBoundary.onProfileApplied(typedEvent);
    if (guardCheck_walkPlayback()) {
      auditInvocation(walkPlayback, "walkPlayback", "onLogChanged", typedEvent);
      walkPlayback.onLogChanged();
    }
    auditInvocation(bundleAnchor, "bundleAnchor", "onProfileApplied", typedEvent);
    bundleAnchor.onProfileApplied(typedEvent);
    auditInvocation(projectReopenOffer, "projectReopenOffer", "onProfileApplied", typedEvent);
    projectReopenOffer.onProfileApplied(typedEvent);
    afterEvent();
  }

  public void handleEvent(ProfileLoaded typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(operationGate, "operationGate", "onProfileLoaded", typedEvent);
    isDirty_operationGate = operationGate.onProfileLoaded(typedEvent);
    auditInvocation(effectOutcomes, "effectOutcomes", "onProfileLoaded", typedEvent);
    effectOutcomes.onProfileLoaded(typedEvent);
    auditInvocation(openBundle, "openBundle", "onProfileLoaded", typedEvent);
    isDirty_openBundle = openBundle.onProfileLoaded(typedEvent);
    if (guardCheck_auditInstallation()) {
      auditInvocation(auditInstallation, "auditInstallation", "recomputeOnStateChange", typedEvent);
      isDirty_auditInstallation = auditInstallation.recomputeOnStateChange();
    }
    if (guardCheck_logEvidence()) {
      auditInvocation(logEvidence, "logEvidence", "onOpenLogChanged", typedEvent);
      logEvidence.onOpenLogChanged();
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
    if (guardCheck_recordsView()) {
      auditInvocation(recordsView, "recordsView", "onOpenLogChanged", typedEvent);
      isDirty_recordsView = recordsView.onOpenLogChanged();
    }
    auditInvocation(assistantLoop, "assistantLoop", "onProfileLoaded", typedEvent);
    isDirty_assistantLoop = assistantLoop.onProfileLoaded(typedEvent);
    auditInvocation(sessionBoundary, "sessionBoundary", "onProfileLoaded", typedEvent);
    sessionBoundary.onProfileLoaded(typedEvent);
    if (guardCheck_walkPlayback()) {
      auditInvocation(walkPlayback, "walkPlayback", "onLogChanged", typedEvent);
      walkPlayback.onLogChanged();
    }
    afterEvent();
  }

  public void handleEvent(ProjectReopenPresented typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(
        projectReopenOffer, "projectReopenOffer", "onProjectReopenPresented", typedEvent);
    projectReopenOffer.onProjectReopenPresented(typedEvent);
    afterEvent();
  }

  public void handleEvent(ProjectReopenReady typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(projectReopenOffer, "projectReopenOffer", "onProjectReopenReady", typedEvent);
    projectReopenOffer.onProjectReopenReady(typedEvent);
    afterEvent();
  }

  public void handleEvent(ProjectReopenRequested typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(
        projectReopenOffer, "projectReopenOffer", "onProjectReopenRequested", typedEvent);
    projectReopenOffer.onProjectReopenRequested(typedEvent);
    afterEvent();
  }

  public void handleEvent(ProposalApplyRequested typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(onboardingProposal, "onboardingProposal", "onApplyRequested", typedEvent);
    isDirty_onboardingProposal = onboardingProposal.onApplyRequested(typedEvent);
    afterEvent();
  }

  public void handleEvent(ProposalChecked typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(onboardingProposal, "onboardingProposal", "onChecked", typedEvent);
    isDirty_onboardingProposal = onboardingProposal.onChecked(typedEvent);
    afterEvent();
  }

  public void handleEvent(ProposalDismissed typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(onboardingProposal, "onboardingProposal", "onDismissed", typedEvent);
    isDirty_onboardingProposal = onboardingProposal.onDismissed(typedEvent);
    afterEvent();
  }

  public void handleEvent(ProposalEffectStarted typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(onboardingProposal, "onboardingProposal", "onEffectStarted", typedEvent);
    isDirty_onboardingProposal = onboardingProposal.onEffectStarted(typedEvent);
    afterEvent();
  }

  public void handleEvent(ProposalItemAnswered typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(onboardingProposal, "onboardingProposal", "onItemAnswered", typedEvent);
    isDirty_onboardingProposal = onboardingProposal.onItemAnswered(typedEvent);
    afterEvent();
  }

  public void handleEvent(ProposalItemApplied typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(localAgentSession, "localAgentSession", "onProposalItemApplied", typedEvent);
    isDirty_localAgentSession = localAgentSession.onProposalItemApplied(typedEvent);
    auditInvocation(onboardingProposal, "onboardingProposal", "onApplied", typedEvent);
    isDirty_onboardingProposal = onboardingProposal.onApplied(typedEvent);
    if (guardCheck_walkPlayback()) {
      auditInvocation(walkPlayback, "walkPlayback", "onLogChanged", typedEvent);
      walkPlayback.onLogChanged();
    }
    afterEvent();
  }

  public void handleEvent(ProposalItemPreviewed typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(onboardingProposal, "onboardingProposal", "onPreviewed", typedEvent);
    isDirty_onboardingProposal = onboardingProposal.onPreviewed(typedEvent);
    afterEvent();
  }

  public void handleEvent(ProposalPreviewDone typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(onboardingProposal, "onboardingProposal", "onPreviewDone", typedEvent);
    isDirty_onboardingProposal = onboardingProposal.onPreviewDone(typedEvent);
    afterEvent();
  }

  public void handleEvent(ProposalReceived typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(onboardingProposal, "onboardingProposal", "onReceived", typedEvent);
    isDirty_onboardingProposal = onboardingProposal.onReceived(typedEvent);
    afterEvent();
  }

  public void handleEvent(RecordFlagsChanged typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(recordsView, "recordsView", "onRecordFlagsChanged", typedEvent);
    isDirty_recordsView = recordsView.onRecordFlagsChanged(typedEvent);
    if (guardCheck_walkPlayback()) {
      auditInvocation(walkPlayback, "walkPlayback", "onLogChanged", typedEvent);
      walkPlayback.onLogChanged();
    }
    afterEvent();
  }

  public void handleEvent(RecordsViewInstalled typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(recordsView, "recordsView", "onRecordsViewInstalled", typedEvent);
    isDirty_recordsView = recordsView.onRecordsViewInstalled(typedEvent);
    if (guardCheck_walkPlayback()) {
      auditInvocation(walkPlayback, "walkPlayback", "onLogChanged", typedEvent);
      walkPlayback.onLogChanged();
    }
    afterEvent();
  }

  public void handleEvent(RecordsViewMeasured typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(recordsView, "recordsView", "onRecordsViewMeasured", typedEvent);
    isDirty_recordsView = recordsView.onRecordsViewMeasured(typedEvent);
    auditInvocation(assistantLoop, "assistantLoop", "onRecordsViewMeasured", typedEvent);
    isDirty_assistantLoop = assistantLoop.onRecordsViewMeasured(typedEvent);
    if (guardCheck_walkPlayback()) {
      auditInvocation(walkPlayback, "walkPlayback", "onLogChanged", typedEvent);
      walkPlayback.onLogChanged();
    }
    afterEvent();
  }

  public void handleEvent(RegisterAnswered typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(aiSetup, "aiSetup", "onRegisterAnswered", typedEvent);
    isDirty_aiSetup = aiSetup.onRegisterAnswered(typedEvent);
    afterEvent();
  }

  public void handleEvent(RestSwitchObserved typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(actionListener, "actionListener", "onRestSwitch", typedEvent);
    isDirty_actionListener = actionListener.onRestSwitch(typedEvent);
    auditInvocation(aiSetup, "aiSetup", "onRestSwitch", typedEvent);
    isDirty_aiSetup = aiSetup.onRestSwitch(typedEvent);
    if (guardCheck_walkPlayback()) {
      auditInvocation(walkPlayback, "walkPlayback", "onLogChanged", typedEvent);
      walkPlayback.onLogChanged();
    }
    afterEvent();
  }

  public void handleEvent(ReturnToMainOptions typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(startPagePrompt, "startPagePrompt", "onReturnToMainOptions", typedEvent);
    startPagePrompt.onReturnToMainOptions(typedEvent);
    afterEvent();
  }

  public void handleEvent(ScanScheduled typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(logEvidence, "logEvidence", "onScanScheduled", typedEvent);
    logEvidence.onScanScheduled(typedEvent);
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
    auditInvocation(openBundle, "openBundle", "onSettingsRestored", typedEvent);
    isDirty_openBundle = openBundle.onSettingsRestored(typedEvent);
    if (guardCheck_auditInstallation()) {
      auditInvocation(auditInstallation, "auditInstallation", "recomputeOnStateChange", typedEvent);
      isDirty_auditInstallation = auditInstallation.recomputeOnStateChange();
    }
    if (guardCheck_logEvidence()) {
      auditInvocation(logEvidence, "logEvidence", "onOpenLogChanged", typedEvent);
      logEvidence.onOpenLogChanged();
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
    if (guardCheck_recordsView()) {
      auditInvocation(recordsView, "recordsView", "onOpenLogChanged", typedEvent);
      isDirty_recordsView = recordsView.onOpenLogChanged();
    }
    auditInvocation(assistantLoop, "assistantLoop", "onSettingsRestored", typedEvent);
    isDirty_assistantLoop = assistantLoop.onSettingsRestored(typedEvent);
    if (guardCheck_walkPlayback()) {
      auditInvocation(walkPlayback, "walkPlayback", "onLogChanged", typedEvent);
      walkPlayback.onLogChanged();
    }
    afterEvent();
  }

  public void handleEvent(SkillsVendored typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(starterProject, "starterProject", "onSkillsVendored", typedEvent);
    isDirty_starterProject = starterProject.onSkillsVendored(typedEvent);
    afterEvent();
  }

  public void handleEvent(SourceRootsObserved typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(bundleAnchor, "bundleAnchor", "onSourceRootsObserved", typedEvent);
    bundleAnchor.onSourceRootsObserved(typedEvent);
    afterEvent();
  }

  public void handleEvent(StartPageAvailability typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(aiSetup, "aiSetup", "onAvailability", typedEvent);
    isDirty_aiSetup = aiSetup.onAvailability(typedEvent);
    auditInvocation(startPagePrompt, "startPagePrompt", "onAvailability", typedEvent);
    startPagePrompt.onAvailability(typedEvent);
    afterEvent();
  }

  public void handleEvent(StartPageEffectStarted typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(startPagePrompt, "startPagePrompt", "onEffectStarted", typedEvent);
    startPagePrompt.onEffectStarted(typedEvent);
    afterEvent();
  }

  public void handleEvent(StartPageFlowDone typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(startPageNoLogHint, "startPageNoLogHint", "onFlowDone", typedEvent);
    isDirty_startPageNoLogHint = startPageNoLogHint.onFlowDone(typedEvent);
    auditInvocation(startPagePrompt, "startPagePrompt", "onFlowDone", typedEvent);
    startPagePrompt.onFlowDone(typedEvent);
    afterEvent();
  }

  public void handleEvent(StartPageNoLogAnswered typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(startPageNoLogHint, "startPageNoLogHint", "onAnswered", typedEvent);
    isDirty_startPageNoLogHint = startPageNoLogHint.onAnswered(typedEvent);
    afterEvent();
  }

  public void handleEvent(StartPagePromptSent typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(startPageNoLogHint, "startPageNoLogHint", "onSent", typedEvent);
    isDirty_startPageNoLogHint = startPageNoLogHint.onSent(typedEvent);
    auditInvocation(aiSetup, "aiSetup", "onStartPageSent", typedEvent);
    isDirty_aiSetup = aiSetup.onStartPageSent(typedEvent);
    auditInvocation(startPagePrompt, "startPagePrompt", "onSent", typedEvent);
    startPagePrompt.onSent(typedEvent);
    afterEvent();
  }

  public void handleEvent(StartPromptPackDropped typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(startPagePrompt, "startPagePrompt", "onPackDropped", typedEvent);
    startPagePrompt.onPackDropped(typedEvent);
    afterEvent();
  }

  public void handleEvent(StarterCatalogueObserved typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(starterProject, "starterProject", "onCatalogueObserved", typedEvent);
    isDirty_starterProject = starterProject.onCatalogueObserved(typedEvent);
    afterEvent();
  }

  public void handleEvent(StarterChosen typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(starterProject, "starterProject", "onChosen", typedEvent);
    isDirty_starterProject = starterProject.onChosen(typedEvent);
    afterEvent();
  }

  public void handleEvent(StarterConfigured typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(starterProject, "starterProject", "onConfigured", typedEvent);
    isDirty_starterProject = starterProject.onConfigured(typedEvent);
    afterEvent();
  }

  public void handleEvent(StarterDefaultsObserved typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(onboardingProposal, "onboardingProposal", "onStarterDefaults", typedEvent);
    isDirty_onboardingProposal = onboardingProposal.onStarterDefaults(typedEvent);
    auditInvocation(starterProject, "starterProject", "onDefaultsObserved", typedEvent);
    isDirty_starterProject = starterProject.onDefaultsObserved(typedEvent);
    afterEvent();
  }

  public void handleEvent(StarterDesignOpened typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(starterProject, "starterProject", "onDesignOpened", typedEvent);
    isDirty_starterProject = starterProject.onDesignOpened(typedEvent);
    afterEvent();
  }

  public void handleEvent(StarterDestinationChosen typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(starterProject, "starterProject", "onDestinationChosen", typedEvent);
    isDirty_starterProject = starterProject.onDestinationChosen(typedEvent);
    afterEvent();
  }

  public void handleEvent(StarterDirectoryObserved typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(starterProject, "starterProject", "onDirectoryObserved", typedEvent);
    isDirty_starterProject = starterProject.onDirectoryObserved(typedEvent);
    afterEvent();
  }

  public void handleEvent(StarterDiscoveryObserved typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(starterProject, "starterProject", "onDiscoveryObserved", typedEvent);
    isDirty_starterProject = starterProject.onDiscoveryObserved(typedEvent);
    afterEvent();
  }

  public void handleEvent(StarterEffectStarted typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(starterProject, "starterProject", "onEffectStarted", typedEvent);
    isDirty_starterProject = starterProject.onEffectStarted(typedEvent);
    afterEvent();
  }

  public void handleEvent(StarterGraphOfferAnswered typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(starterProject, "starterProject", "onGraphOfferAnswered", typedEvent);
    isDirty_starterProject = starterProject.onGraphOfferAnswered(typedEvent);
    afterEvent();
  }

  public void handleEvent(StarterGraphmlAppeared typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(starterProject, "starterProject", "onGraphmlAppeared", typedEvent);
    isDirty_starterProject = starterProject.onGraphmlAppeared(typedEvent);
    afterEvent();
  }

  public void handleEvent(StarterInstallFailed typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(onboardingProposal, "onboardingProposal", "onStarterInstallFailed", typedEvent);
    isDirty_onboardingProposal = onboardingProposal.onStarterInstallFailed(typedEvent);
    auditInvocation(starterProject, "starterProject", "onInstallFailed", typedEvent);
    isDirty_starterProject = starterProject.onInstallFailed(typedEvent);
    afterEvent();
  }

  public void handleEvent(StarterInstalled typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(starterProject, "starterProject", "onInstalled", typedEvent);
    isDirty_starterProject = starterProject.onInstalled(typedEvent);
    afterEvent();
  }

  public void handleEvent(StarterMenuRequested typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(starterProject, "starterProject", "onMenuRequested", typedEvent);
    isDirty_starterProject = starterProject.onMenuRequested(typedEvent);
    afterEvent();
  }

  public void handleEvent(StarterProjectOpened typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(onboardingProposal, "onboardingProposal", "onStarterProjectOpened", typedEvent);
    isDirty_onboardingProposal = onboardingProposal.onStarterProjectOpened(typedEvent);
    auditInvocation(starterProject, "starterProject", "onProjectOpened", typedEvent);
    isDirty_starterProject = starterProject.onProjectOpened(typedEvent);
    afterEvent();
  }

  public void handleEvent(StarterReviewAnswered typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(
        onboardingProposal, "onboardingProposal", "onStarterReviewAnswered", typedEvent);
    isDirty_onboardingProposal = onboardingProposal.onStarterReviewAnswered(typedEvent);
    auditInvocation(starterProject, "starterProject", "onReviewAnswered", typedEvent);
    isDirty_starterProject = starterProject.onReviewAnswered(typedEvent);
    afterEvent();
  }

  public void handleEvent(StatusShown typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(operationGate, "operationGate", "onStatusShown", typedEvent);
    isDirty_operationGate = operationGate.onStatusShown(typedEvent);
    auditInvocation(effectOutcomes, "effectOutcomes", "onStatusShown", typedEvent);
    effectOutcomes.onStatusShown(typedEvent);
    if (guardCheck_auditInstallation()) {
      auditInvocation(auditInstallation, "auditInstallation", "recomputeOnStateChange", typedEvent);
      isDirty_auditInstallation = auditInstallation.recomputeOnStateChange();
    }
    commonDispatchTail_1(typedEvent);
    afterEvent();
  }

  public void handleEvent(TimeOrderObserved typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(logEvidence, "logEvidence", "onTimeOrderObserved", typedEvent);
    logEvidence.onTimeOrderObserved(typedEvent);
    afterEvent();
  }

  public void handleEvent(ViewFilterChanged typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(pairingQualifier, "pairingQualifier", "onViewFilterChanged", typedEvent);
    pairingQualifier.onViewFilterChanged(typedEvent);
    auditInvocation(recordsView, "recordsView", "onViewFilterChanged", typedEvent);
    isDirty_recordsView = recordsView.onViewFilterChanged(typedEvent);
    auditInvocation(assistantLoop, "assistantLoop", "onViewFilterChanged", typedEvent);
    isDirty_assistantLoop = assistantLoop.onViewFilterChanged(typedEvent);
    if (guardCheck_walkPlayback()) {
      auditInvocation(walkPlayback, "walkPlayback", "onLogChanged", typedEvent);
      walkPlayback.onLogChanged();
    }
    afterEvent();
  }

  public void handleEvent(WalkAcknowledged typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(walkPlayback, "walkPlayback", "onWalkAcknowledged", typedEvent);
    walkPlayback.onWalkAcknowledged(typedEvent);
    afterEvent();
  }

  public void handleEvent(WalkDefinitionChanged typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(walkPlayback, "walkPlayback", "onWalkDefinitionChanged", typedEvent);
    walkPlayback.onWalkDefinitionChanged(typedEvent);
    afterEvent();
  }

  public void handleEvent(WalkEndRequested typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(walkPlayback, "walkPlayback", "onWalkEndRequested", typedEvent);
    walkPlayback.onWalkEndRequested(typedEvent);
    afterEvent();
  }

  public void handleEvent(WalkNavigated typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(walkPlayback, "walkPlayback", "onWalkNavigated", typedEvent);
    walkPlayback.onWalkNavigated(typedEvent);
    afterEvent();
  }

  public void handleEvent(WalkPlayRequested typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(walkPlayback, "walkPlayback", "onWalkPlayRequested", typedEvent);
    walkPlayback.onWalkPlayRequested(typedEvent);
    auditInvocation(projectReopenOffer, "projectReopenOffer", "onWalkPlayRequested", typedEvent);
    projectReopenOffer.onWalkPlayRequested(typedEvent);
    afterEvent();
  }

  public void handleEvent(WalkStepPrepared typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(walkPlayback, "walkPlayback", "onWalkStepPrepared", typedEvent);
    walkPlayback.onWalkStepPrepared(typedEvent);
    afterEvent();
  }

  public void handleEvent(WalkTargetsLit typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(walkPlayback, "walkPlayback", "onWalkTargetsLit", typedEvent);
    walkPlayback.onWalkTargetsLit(typedEvent);
    afterEvent();
  }

  public void handleEvent(WalkViewApplied typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(walkPlayback, "walkPlayback", "onWalkViewApplied", typedEvent);
    walkPlayback.onWalkViewApplied(typedEvent);
    afterEvent();
  }

  public void handleEvent(WalkViewChanged typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(walkPlayback, "walkPlayback", "onWalkViewChanged", typedEvent);
    walkPlayback.onWalkViewChanged(typedEvent);
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
    if (guardCheck_logEvidence()) {
      auditInvocation(logEvidence, "logEvidence", "onOpenLogChanged", typedEvent);
      logEvidence.onOpenLogChanged();
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
    if (guardCheck_recordsView()) {
      auditInvocation(recordsView, "recordsView", "onOpenLogChanged", typedEvent);
      isDirty_recordsView = recordsView.onOpenLogChanged();
    }
    if (guardCheck_walkPlayback()) {
      auditInvocation(walkPlayback, "walkPlayback", "onLogChanged", typedEvent);
      walkPlayback.onLogChanged();
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
    } else if (event instanceof ActionListenerConfigured) {
      ActionListenerConfigured typedEvent = (ActionListenerConfigured) event;
      auditEvent(typedEvent);
      auditInvocation(actionListener, "actionListener", "onConfigured", typedEvent);
      isDirty_actionListener = actionListener.onConfigured(typedEvent);
      auditInvocation(aiSetup, "aiSetup", "onListenerConfigured", typedEvent);
      isDirty_aiSetup = aiSetup.onListenerConfigured(typedEvent);
    } else if (event instanceof AiClientsObserved) {
      AiClientsObserved typedEvent = (AiClientsObserved) event;
      auditEvent(typedEvent);
      auditInvocation(aiSetup, "aiSetup", "onClientsObserved", typedEvent);
      isDirty_aiSetup = aiSetup.onClientsObserved(typedEvent);
    } else if (event instanceof AiSetupAlsoTurnOffChosen) {
      AiSetupAlsoTurnOffChosen typedEvent = (AiSetupAlsoTurnOffChosen) event;
      auditEvent(typedEvent);
      auditInvocation(aiSetup, "aiSetup", "onAlsoTurnOff", typedEvent);
      isDirty_aiSetup = aiSetup.onAlsoTurnOff(typedEvent);
    } else if (event instanceof AiSetupBack) {
      AiSetupBack typedEvent = (AiSetupBack) event;
      auditEvent(typedEvent);
      auditInvocation(aiSetup, "aiSetup", "onBack", typedEvent);
      isDirty_aiSetup = aiSetup.onBack(typedEvent);
    } else if (event instanceof AiSetupBuildModeAnswered) {
      AiSetupBuildModeAnswered typedEvent = (AiSetupBuildModeAnswered) event;
      auditEvent(typedEvent);
      auditInvocation(aiSetup, "aiSetup", "onBuildModeAnswered", typedEvent);
      isDirty_aiSetup = aiSetup.onBuildModeAnswered(typedEvent);
    } else if (event instanceof AiSetupCancelled) {
      AiSetupCancelled typedEvent = (AiSetupCancelled) event;
      auditEvent(typedEvent);
      auditInvocation(aiSetup, "aiSetup", "onCancelled", typedEvent);
      isDirty_aiSetup = aiSetup.onCancelled(typedEvent);
      auditInvocation(startPagePrompt, "startPagePrompt", "onSetupCancelled", typedEvent);
      startPagePrompt.onSetupCancelled(typedEvent);
    } else if (event instanceof AiSetupCheckAgain) {
      AiSetupCheckAgain typedEvent = (AiSetupCheckAgain) event;
      auditEvent(typedEvent);
      auditInvocation(aiSetup, "aiSetup", "onCheckAgain", typedEvent);
      isDirty_aiSetup = aiSetup.onCheckAgain(typedEvent);
    } else if (event instanceof AiSetupChoiceMade) {
      AiSetupChoiceMade typedEvent = (AiSetupChoiceMade) event;
      auditEvent(typedEvent);
      auditInvocation(aiSetup, "aiSetup", "onChoiceMade", typedEvent);
      isDirty_aiSetup = aiSetup.onChoiceMade(typedEvent);
    } else if (event instanceof AiSetupClientChosen) {
      AiSetupClientChosen typedEvent = (AiSetupClientChosen) event;
      auditEvent(typedEvent);
      auditInvocation(aiSetup, "aiSetup", "onClientChosen", typedEvent);
      isDirty_aiSetup = aiSetup.onClientChosen(typedEvent);
    } else if (event instanceof AiSetupContinue) {
      AiSetupContinue typedEvent = (AiSetupContinue) event;
      auditEvent(typedEvent);
      auditInvocation(aiSetup, "aiSetup", "onContinue", typedEvent);
      isDirty_aiSetup = aiSetup.onContinue(typedEvent);
    } else if (event instanceof AiSetupCopyRequested) {
      AiSetupCopyRequested typedEvent = (AiSetupCopyRequested) event;
      auditEvent(typedEvent);
      auditInvocation(aiSetup, "aiSetup", "onCopyRequested", typedEvent);
      isDirty_aiSetup = aiSetup.onCopyRequested(typedEvent);
    } else if (event instanceof AiSetupFirstRunDismissed) {
      AiSetupFirstRunDismissed typedEvent = (AiSetupFirstRunDismissed) event;
      auditEvent(typedEvent);
      auditInvocation(aiSetup, "aiSetup", "onFirstRunDismissed", typedEvent);
      isDirty_aiSetup = aiSetup.onFirstRunDismissed(typedEvent);
    } else if (event instanceof AiSetupMoreOptions) {
      AiSetupMoreOptions typedEvent = (AiSetupMoreOptions) event;
      auditEvent(typedEvent);
      auditInvocation(aiSetup, "aiSetup", "onMoreOptions", typedEvent);
      isDirty_aiSetup = aiSetup.onMoreOptions(typedEvent);
    } else if (event instanceof AiSetupPostureChosen) {
      AiSetupPostureChosen typedEvent = (AiSetupPostureChosen) event;
      auditEvent(typedEvent);
      auditInvocation(aiSetup, "aiSetup", "onPostureChosen", typedEvent);
      isDirty_aiSetup = aiSetup.onPostureChosen(typedEvent);
    } else if (event instanceof AiSetupRegisterRequested) {
      AiSetupRegisterRequested typedEvent = (AiSetupRegisterRequested) event;
      auditEvent(typedEvent);
      auditInvocation(aiSetup, "aiSetup", "onRegisterRequested", typedEvent);
      isDirty_aiSetup = aiSetup.onRegisterRequested(typedEvent);
    } else if (event instanceof AiSetupRequested) {
      AiSetupRequested typedEvent = (AiSetupRequested) event;
      auditEvent(typedEvent);
      auditInvocation(aiSetup, "aiSetup", "onSetupRequested", typedEvent);
      isDirty_aiSetup = aiSetup.onSetupRequested(typedEvent);
    } else if (event instanceof AiSetupStartupObserved) {
      AiSetupStartupObserved typedEvent = (AiSetupStartupObserved) event;
      auditEvent(typedEvent);
      auditInvocation(aiSetup, "aiSetup", "onStartupObserved", typedEvent);
      isDirty_aiSetup = aiSetup.onStartupObserved(typedEvent);
    } else if (event instanceof AiSetupSuggestedTaken) {
      AiSetupSuggestedTaken typedEvent = (AiSetupSuggestedTaken) event;
      auditEvent(typedEvent);
      auditInvocation(aiSetup, "aiSetup", "onSuggestedTaken", typedEvent);
      isDirty_aiSetup = aiSetup.onSuggestedTaken(typedEvent);
    } else if (event instanceof AiSetupTimerFired) {
      AiSetupTimerFired typedEvent = (AiSetupTimerFired) event;
      auditEvent(typedEvent);
      auditInvocation(aiSetup, "aiSetup", "onTimerFired", typedEvent);
      isDirty_aiSetup = aiSetup.onTimerFired(typedEvent);
    } else if (event instanceof AssistantActionFinished) {
      AssistantActionFinished typedEvent = (AssistantActionFinished) event;
      auditEvent(typedEvent);
      auditInvocation(assistantLoop, "assistantLoop", "onActionFinished", typedEvent);
      isDirty_assistantLoop = assistantLoop.onActionFinished(typedEvent);
    } else if (event instanceof AssistantCancelRequested) {
      AssistantCancelRequested typedEvent = (AssistantCancelRequested) event;
      auditEvent(typedEvent);
      auditInvocation(assistantLoop, "assistantLoop", "onCancelRequested", typedEvent);
      isDirty_assistantLoop = assistantLoop.onCancelRequested(typedEvent);
    } else if (event instanceof AssistantCompletionFailed) {
      AssistantCompletionFailed typedEvent = (AssistantCompletionFailed) event;
      auditEvent(typedEvent);
      auditInvocation(assistantLoop, "assistantLoop", "onCompletionFailed", typedEvent);
      isDirty_assistantLoop = assistantLoop.onCompletionFailed(typedEvent);
    } else if (event instanceof AssistantCompletionReceived) {
      AssistantCompletionReceived typedEvent = (AssistantCompletionReceived) event;
      auditEvent(typedEvent);
      auditInvocation(assistantLoop, "assistantLoop", "onCompletionReceived", typedEvent);
      isDirty_assistantLoop = assistantLoop.onCompletionReceived(typedEvent);
    } else if (event instanceof AssistantContextFailed) {
      AssistantContextFailed typedEvent = (AssistantContextFailed) event;
      auditEvent(typedEvent);
      auditInvocation(assistantLoop, "assistantLoop", "onContextFailed", typedEvent);
      isDirty_assistantLoop = assistantLoop.onContextFailed(typedEvent);
    } else if (event instanceof AssistantContextPrepared) {
      AssistantContextPrepared typedEvent = (AssistantContextPrepared) event;
      auditEvent(typedEvent);
      auditInvocation(assistantLoop, "assistantLoop", "onContextPrepared", typedEvent);
      isDirty_assistantLoop = assistantLoop.onContextPrepared(typedEvent);
    } else if (event instanceof AssistantDraftComposed) {
      AssistantDraftComposed typedEvent = (AssistantDraftComposed) event;
      auditEvent(typedEvent);
      auditInvocation(assistantLoop, "assistantLoop", "onDraftComposed", typedEvent);
      isDirty_assistantLoop = assistantLoop.onDraftComposed(typedEvent);
    } else if (event instanceof AssistantDraftPending) {
      AssistantDraftPending typedEvent = (AssistantDraftPending) event;
      auditEvent(typedEvent);
      auditInvocation(aiSetup, "aiSetup", "onDraftPending", typedEvent);
      isDirty_aiSetup = aiSetup.onDraftPending(typedEvent);
    } else if (event instanceof AssistantEffectStarted) {
      AssistantEffectStarted typedEvent = (AssistantEffectStarted) event;
      auditEvent(typedEvent);
      auditInvocation(assistantLoop, "assistantLoop", "onEffectStarted", typedEvent);
      isDirty_assistantLoop = assistantLoop.onEffectStarted(typedEvent);
    } else if (event instanceof AssistantFileExchangeObserved) {
      AssistantFileExchangeObserved typedEvent = (AssistantFileExchangeObserved) event;
      auditEvent(typedEvent);
      auditInvocation(localAgentPermission, "localAgentPermission", "onFileExchange", typedEvent);
      isDirty_localAgentPermission = localAgentPermission.onFileExchange(typedEvent);
    } else if (event instanceof AssistantFullProfileAnswered) {
      AssistantFullProfileAnswered typedEvent = (AssistantFullProfileAnswered) event;
      auditEvent(typedEvent);
      auditInvocation(aiSetup, "aiSetup", "onFullProfileAnswered", typedEvent);
      isDirty_aiSetup = aiSetup.onFullProfileAnswered(typedEvent);
    } else if (event instanceof AssistantHandoffRequested) {
      AssistantHandoffRequested typedEvent = (AssistantHandoffRequested) event;
      auditEvent(typedEvent);
      auditInvocation(assistantLoop, "assistantLoop", "onHandoffRequested", typedEvent);
      isDirty_assistantLoop = assistantLoop.onHandoffRequested(typedEvent);
      auditInvocation(walkPlayback, "walkPlayback", "onAssistantHandoffRequested", typedEvent);
      walkPlayback.onAssistantHandoffRequested(typedEvent);
    } else if (event instanceof AssistantHostRequested) {
      AssistantHostRequested typedEvent = (AssistantHostRequested) event;
      auditEvent(typedEvent);
      auditInvocation(assistantLoop, "assistantLoop", "onHostRequested", typedEvent);
      isDirty_assistantLoop = assistantLoop.onHostRequested(typedEvent);
    } else if (event instanceof AssistantHostShown) {
      AssistantHostShown typedEvent = (AssistantHostShown) event;
      auditEvent(typedEvent);
      auditInvocation(assistantLoop, "assistantLoop", "onHostShown", typedEvent);
      isDirty_assistantLoop = assistantLoop.onHostShown(typedEvent);
    } else if (event instanceof AssistantModeChosen) {
      AssistantModeChosen typedEvent = (AssistantModeChosen) event;
      auditEvent(typedEvent);
      auditInvocation(localAgentSession, "localAgentSession", "onModeChosen", typedEvent);
      isDirty_localAgentSession = localAgentSession.onModeChosen(typedEvent);
      auditInvocation(assistantMode, "assistantMode", "onModeChosen", typedEvent);
      isDirty_assistantMode = assistantMode.onModeChosen(typedEvent);
      auditInvocation(assistantLoop, "assistantLoop", "onModeChosen", typedEvent);
      isDirty_assistantLoop = assistantLoop.onModeChosen(typedEvent);
      auditInvocation(startPagePrompt, "startPagePrompt", "onModeChosen", typedEvent);
      startPagePrompt.onModeChosen(typedEvent);
    } else if (event instanceof AssistantNewChatRequested) {
      AssistantNewChatRequested typedEvent = (AssistantNewChatRequested) event;
      auditEvent(typedEvent);
      auditInvocation(assistantLoop, "assistantLoop", "onNewChatRequested", typedEvent);
      isDirty_assistantLoop = assistantLoop.onNewChatRequested(typedEvent);
    } else if (event instanceof AssistantOpenApplied) {
      AssistantOpenApplied typedEvent = (AssistantOpenApplied) event;
      auditEvent(typedEvent);
      auditInvocation(assistantLoop, "assistantLoop", "onAssistantOpenApplied", typedEvent);
      isDirty_assistantLoop = assistantLoop.onAssistantOpenApplied(typedEvent);
    } else if (event instanceof AssistantOpenApplyFailed) {
      AssistantOpenApplyFailed typedEvent = (AssistantOpenApplyFailed) event;
      auditEvent(typedEvent);
      auditInvocation(assistantLoop, "assistantLoop", "onAssistantOpenApplyFailed", typedEvent);
      isDirty_assistantLoop = assistantLoop.onAssistantOpenApplyFailed(typedEvent);
    } else if (event instanceof AssistantProfileApplied) {
      AssistantProfileApplied typedEvent = (AssistantProfileApplied) event;
      auditEvent(typedEvent);
      auditInvocation(localAgentSession, "localAgentSession", "onProfileApplied", typedEvent);
      isDirty_localAgentSession = localAgentSession.onProfileApplied(typedEvent);
      auditInvocation(aiSetup, "aiSetup", "onProfileApplied", typedEvent);
      isDirty_aiSetup = aiSetup.onProfileApplied(typedEvent);
      auditInvocation(startPagePrompt, "startPagePrompt", "onProfileApplied", typedEvent);
      startPagePrompt.onProfileApplied(typedEvent);
    } else if (event instanceof AssistantProfileChosen) {
      AssistantProfileChosen typedEvent = (AssistantProfileChosen) event;
      auditEvent(typedEvent);
      auditInvocation(aiSetup, "aiSetup", "onProfileChosen", typedEvent);
      isDirty_aiSetup = aiSetup.onProfileChosen(typedEvent);
    } else if (event instanceof AssistantProfileObserved) {
      AssistantProfileObserved typedEvent = (AssistantProfileObserved) event;
      auditEvent(typedEvent);
      auditInvocation(aiSetup, "aiSetup", "onProfileObserved", typedEvent);
      isDirty_aiSetup = aiSetup.onProfileObserved(typedEvent);
      auditInvocation(startPagePrompt, "startPagePrompt", "onProfileObserved", typedEvent);
      startPagePrompt.onProfileObserved(typedEvent);
    } else if (event instanceof AssistantProfilesShown) {
      AssistantProfilesShown typedEvent = (AssistantProfilesShown) event;
      auditEvent(typedEvent);
      auditInvocation(aiSetup, "aiSetup", "onProfilesShown", typedEvent);
      isDirty_aiSetup = aiSetup.onProfilesShown(typedEvent);
    } else if (event instanceof AssistantSendRequested) {
      AssistantSendRequested typedEvent = (AssistantSendRequested) event;
      auditEvent(typedEvent);
      auditInvocation(assistantLoop, "assistantLoop", "onSendRequested", typedEvent);
      isDirty_assistantLoop = assistantLoop.onSendRequested(typedEvent);
      auditInvocation(aiSetup, "aiSetup", "onAssistantSend", typedEvent);
      isDirty_aiSetup = aiSetup.onAssistantSend(typedEvent);
      auditInvocation(walkPlayback, "walkPlayback", "onAssistantSendRequested", typedEvent);
      walkPlayback.onAssistantSendRequested(typedEvent);
    } else if (event instanceof AssistantTabShown) {
      AssistantTabShown typedEvent = (AssistantTabShown) event;
      auditEvent(typedEvent);
      auditInvocation(assistantLoop, "assistantLoop", "onAssistantTabShown", typedEvent);
      isDirty_assistantLoop = assistantLoop.onAssistantTabShown(typedEvent);
    } else if (event instanceof BridgeObserved) {
      BridgeObserved typedEvent = (BridgeObserved) event;
      auditEvent(typedEvent);
      auditInvocation(aiSetup, "aiSetup", "onBridgeObserved", typedEvent);
      isDirty_aiSetup = aiSetup.onBridgeObserved(typedEvent);
    } else if (event instanceof BundleCaptureRequested) {
      BundleCaptureRequested typedEvent = (BundleCaptureRequested) event;
      auditEvent(typedEvent);
      auditInvocation(evidenceCapture, "evidenceCapture", "onBundleCaptureRequested", typedEvent);
      evidenceCapture.onBundleCaptureRequested(typedEvent);
    } else if (event instanceof BundleDeleted) {
      BundleDeleted typedEvent = (BundleDeleted) event;
      auditEvent(typedEvent);
      auditInvocation(evidenceCapture, "evidenceCapture", "onBundleDeleted", typedEvent);
      evidenceCapture.onBundleDeleted(typedEvent);
    } else if (event instanceof BundleRootsRestored) {
      BundleRootsRestored typedEvent = (BundleRootsRestored) event;
      auditEvent(typedEvent);
      auditInvocation(bundleAnchor, "bundleAnchor", "onBundleRootsRestored", typedEvent);
      bundleAnchor.onBundleRootsRestored(typedEvent);
    } else if (event instanceof BundleWriteFailed) {
      BundleWriteFailed typedEvent = (BundleWriteFailed) event;
      auditEvent(typedEvent);
      auditInvocation(evidenceCapture, "evidenceCapture", "onBundleWriteFailed", typedEvent);
      evidenceCapture.onBundleWriteFailed(typedEvent);
    } else if (event instanceof BundleWritten) {
      BundleWritten typedEvent = (BundleWritten) event;
      auditEvent(typedEvent);
      auditInvocation(evidenceCapture, "evidenceCapture", "onBundleWritten", typedEvent);
      evidenceCapture.onBundleWritten(typedEvent);
    } else if (event instanceof CanvasLeftAssistant) {
      CanvasLeftAssistant typedEvent = (CanvasLeftAssistant) event;
      auditEvent(typedEvent);
      auditInvocation(assistantLoop, "assistantLoop", "onCanvasLeftAssistant", typedEvent);
      isDirty_assistantLoop = assistantLoop.onCanvasLeftAssistant(typedEvent);
    } else if (event instanceof CaptureStarted) {
      CaptureStarted typedEvent = (CaptureStarted) event;
      auditEvent(typedEvent);
      auditInvocation(evidenceCapture, "evidenceCapture", "onCaptureStarted", typedEvent);
      evidenceCapture.onCaptureStarted(typedEvent);
    } else if (event instanceof ClaudeAuthObserved) {
      ClaudeAuthObserved typedEvent = (ClaudeAuthObserved) event;
      auditEvent(typedEvent);
      auditInvocation(aiSetup, "aiSetup", "onClaudeAuthObserved", typedEvent);
      isDirty_aiSetup = aiSetup.onClaudeAuthObserved(typedEvent);
    } else if (event instanceof ClaudeObserved) {
      ClaudeObserved typedEvent = (ClaudeObserved) event;
      auditEvent(typedEvent);
      auditInvocation(aiSetup, "aiSetup", "onClaudeObserved", typedEvent);
      isDirty_aiSetup = aiSetup.onClaudeObserved(typedEvent);
    } else if (event instanceof CloseRequested) {
      CloseRequested typedEvent = (CloseRequested) event;
      auditEvent(typedEvent);
      auditInvocation(operationGate, "operationGate", "onCloseRequested", typedEvent);
      isDirty_operationGate = operationGate.onCloseRequested(typedEvent);
      auditInvocation(assistantLoop, "assistantLoop", "onCloseRequested", typedEvent);
      isDirty_assistantLoop = assistantLoop.onCloseRequested(typedEvent);
    } else if (event instanceof DraftPlaced) {
      DraftPlaced typedEvent = (DraftPlaced) event;
      auditEvent(typedEvent);
      auditInvocation(localAgentSession, "localAgentSession", "onDraftPlaced", typedEvent);
      isDirty_localAgentSession = localAgentSession.onDraftPlaced(typedEvent);
    } else if (event instanceof EffectFailed) {
      EffectFailed typedEvent = (EffectFailed) event;
      auditEvent(typedEvent);
      auditInvocation(operationGate, "operationGate", "onEffectFailed", typedEvent);
      isDirty_operationGate = operationGate.onEffectFailed(typedEvent);
      auditInvocation(effectOutcomes, "effectOutcomes", "onEffectFailed", typedEvent);
      effectOutcomes.onEffectFailed(typedEvent);
      auditInvocation(assistantLoop, "assistantLoop", "onEffectFailed", typedEvent);
      isDirty_assistantLoop = assistantLoop.onEffectFailed(typedEvent);
      auditInvocation(sessionBoundary, "sessionBoundary", "onEffectFailed", typedEvent);
      sessionBoundary.onEffectFailed(typedEvent);
    } else if (event instanceof ExampleDeadlinePassed) {
      ExampleDeadlinePassed typedEvent = (ExampleDeadlinePassed) event;
      auditEvent(typedEvent);
      auditInvocation(exampleDownload, "exampleDownload", "onDeadline", typedEvent);
      exampleDownload.onDeadline(typedEvent);
    } else if (event instanceof ExampleDownloadConfirmed) {
      ExampleDownloadConfirmed typedEvent = (ExampleDownloadConfirmed) event;
      auditEvent(typedEvent);
      auditInvocation(exampleDownload, "exampleDownload", "onConfirmed", typedEvent);
      exampleDownload.onConfirmed(typedEvent);
    } else if (event instanceof ExampleDownloadRequested) {
      ExampleDownloadRequested typedEvent = (ExampleDownloadRequested) event;
      auditEvent(typedEvent);
      auditInvocation(exampleDownload, "exampleDownload", "onRequested", typedEvent);
      exampleDownload.onRequested(typedEvent);
    } else if (event instanceof ExampleEffectStarted) {
      ExampleEffectStarted typedEvent = (ExampleEffectStarted) event;
      auditEvent(typedEvent);
      auditInvocation(exampleDownload, "exampleDownload", "onEffectStarted", typedEvent);
      exampleDownload.onEffectStarted(typedEvent);
    } else if (event instanceof ExampleFetched) {
      ExampleFetched typedEvent = (ExampleFetched) event;
      auditEvent(typedEvent);
      auditInvocation(exampleDownload, "exampleDownload", "onFetched", typedEvent);
      exampleDownload.onFetched(typedEvent);
    } else if (event instanceof ExampleUnpacked) {
      ExampleUnpacked typedEvent = (ExampleUnpacked) event;
      auditEvent(typedEvent);
      auditInvocation(exampleDownload, "exampleDownload", "onUnpacked", typedEvent);
      exampleDownload.onUnpacked(typedEvent);
    } else if (event instanceof ExternalAccessAnswered) {
      ExternalAccessAnswered typedEvent = (ExternalAccessAnswered) event;
      auditEvent(typedEvent);
      auditInvocation(actionListener, "actionListener", "onExternalAccessAnswered", typedEvent);
      isDirty_actionListener = actionListener.onExternalAccessAnswered(typedEvent);
      auditInvocation(aiSetup, "aiSetup", "onExternalAccessAnswered", typedEvent);
      isDirty_aiSetup = aiSetup.onExternalAccessAnswered(typedEvent);
    } else if (event instanceof ExternalAccessChosen) {
      ExternalAccessChosen typedEvent = (ExternalAccessChosen) event;
      auditEvent(typedEvent);
      auditInvocation(actionListener, "actionListener", "onExternalChosen", typedEvent);
      isDirty_actionListener = actionListener.onExternalChosen(typedEvent);
      auditInvocation(aiSetup, "aiSetup", "onExternalChosen", typedEvent);
      isDirty_aiSetup = aiSetup.onExternalChosen(typedEvent);
    } else if (event instanceof ExternalAccessRequested) {
      ExternalAccessRequested typedEvent = (ExternalAccessRequested) event;
      auditEvent(typedEvent);
      auditInvocation(actionListener, "actionListener", "onExternalAccessRequested", typedEvent);
      isDirty_actionListener = actionListener.onExternalAccessRequested(typedEvent);
      auditInvocation(aiSetup, "aiSetup", "onExternalAccessRequested", typedEvent);
      isDirty_aiSetup = aiSetup.onExternalAccessRequested(typedEvent);
    } else if (event instanceof ExternalClientSeen) {
      ExternalClientSeen typedEvent = (ExternalClientSeen) event;
      auditEvent(typedEvent);
      auditInvocation(aiSetup, "aiSetup", "onClientSeen", typedEvent);
      isDirty_aiSetup = aiSetup.onClientSeen(typedEvent);
    } else if (event instanceof FollowSet) {
      FollowSet typedEvent = (FollowSet) event;
      auditEvent(typedEvent);
      auditInvocation(evidenceCapture, "evidenceCapture", "onFollowSet", typedEvent);
      evidenceCapture.onFollowSet(typedEvent);
    } else if (event instanceof FollowToggled) {
      FollowToggled typedEvent = (FollowToggled) event;
      auditEvent(typedEvent);
      auditInvocation(openLog, "openLog", "onFollowToggled", typedEvent);
      isDirty_openLog = openLog.onFollowToggled(typedEvent);
    } else if (event instanceof GraphCleared) {
      GraphCleared typedEvent = (GraphCleared) event;
      auditEvent(typedEvent);
      auditInvocation(operationGate, "operationGate", "onGraphCleared", typedEvent);
      isDirty_operationGate = operationGate.onGraphCleared(typedEvent);
      auditInvocation(openGraph, "openGraph", "onGraphCleared", typedEvent);
      isDirty_openGraph = openGraph.onGraphCleared(typedEvent);
      auditInvocation(assistantLoop, "assistantLoop", "onGraphCleared", typedEvent);
      isDirty_assistantLoop = assistantLoop.onGraphCleared(typedEvent);
    } else if (event instanceof GraphClosed) {
      GraphClosed typedEvent = (GraphClosed) event;
      auditEvent(typedEvent);
      auditInvocation(operationGate, "operationGate", "onGraphClosed", typedEvent);
      isDirty_operationGate = operationGate.onGraphClosed(typedEvent);
      auditInvocation(effectOutcomes, "effectOutcomes", "onGraphClosed", typedEvent);
      effectOutcomes.onGraphClosed(typedEvent);
      auditInvocation(openGraph, "openGraph", "onGraphClosed", typedEvent);
      isDirty_openGraph = openGraph.onGraphClosed(typedEvent);
      auditInvocation(assistantLoop, "assistantLoop", "onGraphClosed", typedEvent);
      isDirty_assistantLoop = assistantLoop.onGraphClosed(typedEvent);
    } else if (event instanceof GraphOpened) {
      GraphOpened typedEvent = (GraphOpened) event;
      auditEvent(typedEvent);
      auditInvocation(operationGate, "operationGate", "onGraphOpened", typedEvent);
      isDirty_operationGate = operationGate.onGraphOpened(typedEvent);
      auditInvocation(openGraph, "openGraph", "onGraphOpened", typedEvent);
      isDirty_openGraph = openGraph.onGraphOpened(typedEvent);
      auditInvocation(onboardingProposal, "onboardingProposal", "onGraphOpened", typedEvent);
      isDirty_onboardingProposal = onboardingProposal.onGraphOpened(typedEvent);
      auditInvocation(assistantLoop, "assistantLoop", "onGraphOpened", typedEvent);
      isDirty_assistantLoop = assistantLoop.onGraphOpened(typedEvent);
      auditInvocation(projectReopenOffer, "projectReopenOffer", "onGraphOpened", typedEvent);
      projectReopenOffer.onGraphOpened(typedEvent);
    } else if (event instanceof HostedCallAbandoned) {
      HostedCallAbandoned typedEvent = (HostedCallAbandoned) event;
      auditEvent(typedEvent);
      auditInvocation(
          localAgentPermission, "localAgentPermission", "onHostedCallAbandoned", typedEvent);
      isDirty_localAgentPermission = localAgentPermission.onHostedCallAbandoned(typedEvent);
    } else if (event instanceof HostedCallPresented) {
      HostedCallPresented typedEvent = (HostedCallPresented) event;
      auditEvent(typedEvent);
      auditInvocation(localAgentPermission, "localAgentPermission", "onHostedCall", typedEvent);
      isDirty_localAgentPermission = localAgentPermission.onHostedCall(typedEvent);
    } else if (event instanceof HostedCallRefusedByServer) {
      HostedCallRefusedByServer typedEvent = (HostedCallRefusedByServer) event;
      auditEvent(typedEvent);
      auditInvocation(
          localAgentSession, "localAgentSession", "onHostedRefusedByServer", typedEvent);
      isDirty_localAgentSession = localAgentSession.onHostedRefusedByServer(typedEvent);
    } else if (event instanceof HostedReportWritten) {
      HostedReportWritten typedEvent = (HostedReportWritten) event;
      auditEvent(typedEvent);
      auditInvocation(
          localAgentPermission, "localAgentPermission", "onHostedReportWritten", typedEvent);
      isDirty_localAgentPermission = localAgentPermission.onHostedReportWritten(typedEvent);
    } else if (event instanceof LocalAgentAskAgainRequested) {
      LocalAgentAskAgainRequested typedEvent = (LocalAgentAskAgainRequested) event;
      auditEvent(typedEvent);
      auditInvocation(localAgentSession, "localAgentSession", "onAskAgain", typedEvent);
      isDirty_localAgentSession = localAgentSession.onAskAgain(typedEvent);
    } else if (event instanceof LocalAgentBridgeServed) {
      LocalAgentBridgeServed typedEvent = (LocalAgentBridgeServed) event;
      auditEvent(typedEvent);
      auditInvocation(actionListener, "actionListener", "onBridgeServed", typedEvent);
      isDirty_actionListener = actionListener.onBridgeServed(typedEvent);
      auditInvocation(localAgentSession, "localAgentSession", "onBridgeServed", typedEvent);
      isDirty_localAgentSession = localAgentSession.onBridgeServed(typedEvent);
    } else if (event instanceof LocalAgentCallAdmissionRequested) {
      LocalAgentCallAdmissionRequested typedEvent = (LocalAgentCallAdmissionRequested) event;
      auditEvent(typedEvent);
      auditInvocation(localAgentSession, "localAgentSession", "onCallAdmission", typedEvent);
      isDirty_localAgentSession = localAgentSession.onCallAdmission(typedEvent);
    } else if (event instanceof LocalAgentConsentChanged) {
      LocalAgentConsentChanged typedEvent = (LocalAgentConsentChanged) event;
      auditEvent(typedEvent);
      auditInvocation(localAgentPermission, "localAgentPermission", "onConsentChanged", typedEvent);
      isDirty_localAgentPermission = localAgentPermission.onConsentChanged(typedEvent);
      auditInvocation(localAgentSession, "localAgentSession", "onConsentChanged", typedEvent);
      isDirty_localAgentSession = localAgentSession.onConsentChanged(typedEvent);
    } else if (event instanceof LocalAgentCostObserved) {
      LocalAgentCostObserved typedEvent = (LocalAgentCostObserved) event;
      auditEvent(typedEvent);
      auditInvocation(localAgentSession, "localAgentSession", "onCostObserved", typedEvent);
      isDirty_localAgentSession = localAgentSession.onCostObserved(typedEvent);
    } else if (event instanceof LocalAgentDiagnostics) {
      LocalAgentDiagnostics typedEvent = (LocalAgentDiagnostics) event;
      auditEvent(typedEvent);
      auditInvocation(localAgentSession, "localAgentSession", "onDiagnostics", typedEvent);
      isDirty_localAgentSession = localAgentSession.onDiagnostics(typedEvent);
    } else if (event instanceof LocalAgentDraftComposed) {
      LocalAgentDraftComposed typedEvent = (LocalAgentDraftComposed) event;
      auditEvent(typedEvent);
      auditInvocation(localAgentSession, "localAgentSession", "onDraftComposed", typedEvent);
      isDirty_localAgentSession = localAgentSession.onDraftComposed(typedEvent);
    } else if (event instanceof LocalAgentEffectStarted) {
      LocalAgentEffectStarted typedEvent = (LocalAgentEffectStarted) event;
      auditEvent(typedEvent);
      auditInvocation(localAgentSession, "localAgentSession", "onEffectStarted", typedEvent);
      isDirty_localAgentSession = localAgentSession.onEffectStarted(typedEvent);
    } else if (event instanceof LocalAgentExited) {
      LocalAgentExited typedEvent = (LocalAgentExited) event;
      auditEvent(typedEvent);
      auditInvocation(localAgentPermission, "localAgentPermission", "onExited", typedEvent);
      isDirty_localAgentPermission = localAgentPermission.onExited(typedEvent);
      auditInvocation(localAgentSession, "localAgentSession", "onExited", typedEvent);
      isDirty_localAgentSession = localAgentSession.onExited(typedEvent);
    } else if (event instanceof LocalAgentFolderAnswered) {
      LocalAgentFolderAnswered typedEvent = (LocalAgentFolderAnswered) event;
      auditEvent(typedEvent);
      auditInvocation(localAgentSession, "localAgentSession", "onFolderAnswered", typedEvent);
      isDirty_localAgentSession = localAgentSession.onFolderAnswered(typedEvent);
    } else if (event instanceof LocalAgentGrantExpired) {
      LocalAgentGrantExpired typedEvent = (LocalAgentGrantExpired) event;
      auditEvent(typedEvent);
      auditInvocation(localAgentPermission, "localAgentPermission", "onGrantExpired", typedEvent);
      isDirty_localAgentPermission = localAgentPermission.onGrantExpired(typedEvent);
    } else if (event instanceof LocalAgentHookObserved) {
      LocalAgentHookObserved typedEvent = (LocalAgentHookObserved) event;
      auditEvent(typedEvent);
      auditInvocation(localAgentSession, "localAgentSession", "onHookObserved", typedEvent);
      isDirty_localAgentSession = localAgentSession.onHookObserved(typedEvent);
    } else if (event instanceof LocalAgentInitObserved) {
      LocalAgentInitObserved typedEvent = (LocalAgentInitObserved) event;
      auditEvent(typedEvent);
      auditInvocation(localAgentSession, "localAgentSession", "onInitObserved", typedEvent);
      isDirty_localAgentSession = localAgentSession.onInitObserved(typedEvent);
    } else if (event instanceof LocalAgentInitialized) {
      LocalAgentInitialized typedEvent = (LocalAgentInitialized) event;
      auditEvent(typedEvent);
      auditInvocation(localAgentSession, "localAgentSession", "onInitialized", typedEvent);
      isDirty_localAgentSession = localAgentSession.onInitialized(typedEvent);
    } else if (event instanceof LocalAgentInterruptAcked) {
      LocalAgentInterruptAcked typedEvent = (LocalAgentInterruptAcked) event;
      auditEvent(typedEvent);
      auditInvocation(localAgentSession, "localAgentSession", "onInterruptAcked", typedEvent);
      isDirty_localAgentSession = localAgentSession.onInterruptAcked(typedEvent);
    } else if (event instanceof LocalAgentModeObserved) {
      LocalAgentModeObserved typedEvent = (LocalAgentModeObserved) event;
      auditEvent(typedEvent);
      auditInvocation(localAgentSession, "localAgentSession", "onModeObserved", typedEvent);
      isDirty_localAgentSession = localAgentSession.onModeObserved(typedEvent);
    } else if (event instanceof LocalAgentNewChatRequested) {
      LocalAgentNewChatRequested typedEvent = (LocalAgentNewChatRequested) event;
      auditEvent(typedEvent);
      auditInvocation(localAgentPermission, "localAgentPermission", "onNewChat", typedEvent);
      isDirty_localAgentPermission = localAgentPermission.onNewChat(typedEvent);
      auditInvocation(localAgentSession, "localAgentSession", "onNewChatRequested", typedEvent);
      isDirty_localAgentSession = localAgentSession.onNewChatRequested(typedEvent);
    } else if (event instanceof LocalAgentNoted) {
      LocalAgentNoted typedEvent = (LocalAgentNoted) event;
      auditEvent(typedEvent);
      auditInvocation(localAgentSession, "localAgentSession", "onNoted", typedEvent);
      isDirty_localAgentSession = localAgentSession.onNoted(typedEvent);
    } else if (event instanceof LocalAgentPermissionAnswered) {
      LocalAgentPermissionAnswered typedEvent = (LocalAgentPermissionAnswered) event;
      auditEvent(typedEvent);
      auditInvocation(localAgentPermission, "localAgentPermission", "onAnswered", typedEvent);
      isDirty_localAgentPermission = localAgentPermission.onAnswered(typedEvent);
    } else if (event instanceof LocalAgentPermissionCancelled) {
      LocalAgentPermissionCancelled typedEvent = (LocalAgentPermissionCancelled) event;
      auditEvent(typedEvent);
      auditInvocation(localAgentPermission, "localAgentPermission", "onCancelled", typedEvent);
      isDirty_localAgentPermission = localAgentPermission.onCancelled(typedEvent);
    } else if (event instanceof LocalAgentPermissionChosen) {
      LocalAgentPermissionChosen typedEvent = (LocalAgentPermissionChosen) event;
      auditEvent(typedEvent);
      auditInvocation(localAgentPermission, "localAgentPermission", "onChosen", typedEvent);
      isDirty_localAgentPermission = localAgentPermission.onChosen(typedEvent);
    } else if (event instanceof LocalAgentPermissionDeadline) {
      LocalAgentPermissionDeadline typedEvent = (LocalAgentPermissionDeadline) event;
      auditEvent(typedEvent);
      auditInvocation(localAgentPermission, "localAgentPermission", "onDeadline", typedEvent);
      isDirty_localAgentPermission = localAgentPermission.onDeadline(typedEvent);
    } else if (event instanceof LocalAgentPermissionRequested) {
      LocalAgentPermissionRequested typedEvent = (LocalAgentPermissionRequested) event;
      auditEvent(typedEvent);
      auditInvocation(localAgentPermission, "localAgentPermission", "onRequested", typedEvent);
      isDirty_localAgentPermission = localAgentPermission.onRequested(typedEvent);
    } else if (event instanceof LocalAgentRestartAnswered) {
      LocalAgentRestartAnswered typedEvent = (LocalAgentRestartAnswered) event;
      auditEvent(typedEvent);
      auditInvocation(localAgentSession, "localAgentSession", "onRestartAnswered", typedEvent);
      isDirty_localAgentSession = localAgentSession.onRestartAnswered(typedEvent);
    } else if (event instanceof LocalAgentRestartOfferRequested) {
      LocalAgentRestartOfferRequested typedEvent = (LocalAgentRestartOfferRequested) event;
      auditEvent(typedEvent);
      auditInvocation(
          localAgentSession, "localAgentSession", "onRestartOfferRequested", typedEvent);
      isDirty_localAgentSession = localAgentSession.onRestartOfferRequested(typedEvent);
    } else if (event instanceof LocalAgentSendRequested) {
      LocalAgentSendRequested typedEvent = (LocalAgentSendRequested) event;
      auditEvent(typedEvent);
      auditInvocation(localAgentSession, "localAgentSession", "onSendRequested", typedEvent);
      isDirty_localAgentSession = localAgentSession.onSendRequested(typedEvent);
      auditInvocation(aiSetup, "aiSetup", "onLocalAgentSend", typedEvent);
      isDirty_aiSetup = aiSetup.onLocalAgentSend(typedEvent);
    } else if (event instanceof LocalAgentSetupDirectoryChosen) {
      LocalAgentSetupDirectoryChosen typedEvent = (LocalAgentSetupDirectoryChosen) event;
      auditEvent(typedEvent);
      auditInvocation(localAgentSession, "localAgentSession", "onSetupDirectoryChosen", typedEvent);
      isDirty_localAgentSession = localAgentSession.onSetupDirectoryChosen(typedEvent);
    } else if (event instanceof LocalAgentSpawned) {
      LocalAgentSpawned typedEvent = (LocalAgentSpawned) event;
      auditEvent(typedEvent);
      auditInvocation(localAgentSession, "localAgentSession", "onSpawned", typedEvent);
      isDirty_localAgentSession = localAgentSession.onSpawned(typedEvent);
      auditInvocation(starterProject, "starterProject", "onSpawned", typedEvent);
      isDirty_starterProject = starterProject.onSpawned(typedEvent);
    } else if (event instanceof LocalAgentStartDeadline) {
      LocalAgentStartDeadline typedEvent = (LocalAgentStartDeadline) event;
      auditEvent(typedEvent);
      auditInvocation(localAgentSession, "localAgentSession", "onStartDeadline", typedEvent);
      isDirty_localAgentSession = localAgentSession.onStartDeadline(typedEvent);
    } else if (event instanceof LocalAgentStartRequested) {
      LocalAgentStartRequested typedEvent = (LocalAgentStartRequested) event;
      auditEvent(typedEvent);
      auditInvocation(localAgentSession, "localAgentSession", "onStartRequested", typedEvent);
      isDirty_localAgentSession = localAgentSession.onStartRequested(typedEvent);
      auditInvocation(aiSetup, "aiSetup", "onStartRequested", typedEvent);
      isDirty_aiSetup = aiSetup.onStartRequested(typedEvent);
    } else if (event instanceof LocalAgentStatusObserved) {
      LocalAgentStatusObserved typedEvent = (LocalAgentStatusObserved) event;
      auditEvent(typedEvent);
      auditInvocation(localAgentSession, "localAgentSession", "onStatusObserved", typedEvent);
      isDirty_localAgentSession = localAgentSession.onStatusObserved(typedEvent);
    } else if (event instanceof LocalAgentStopDeadline) {
      LocalAgentStopDeadline typedEvent = (LocalAgentStopDeadline) event;
      auditEvent(typedEvent);
      auditInvocation(localAgentSession, "localAgentSession", "onStopDeadline", typedEvent);
      isDirty_localAgentSession = localAgentSession.onStopDeadline(typedEvent);
    } else if (event instanceof LocalAgentStopRequested) {
      LocalAgentStopRequested typedEvent = (LocalAgentStopRequested) event;
      auditEvent(typedEvent);
      auditInvocation(localAgentPermission, "localAgentPermission", "onStopRequested", typedEvent);
      isDirty_localAgentPermission = localAgentPermission.onStopRequested(typedEvent);
      auditInvocation(localAgentSession, "localAgentSession", "onStopRequested", typedEvent);
      isDirty_localAgentSession = localAgentSession.onStopRequested(typedEvent);
    } else if (event instanceof LocalAgentSwitchChosen) {
      LocalAgentSwitchChosen typedEvent = (LocalAgentSwitchChosen) event;
      auditEvent(typedEvent);
      auditInvocation(localAgentSession, "localAgentSession", "onSwitchChosen", typedEvent);
      isDirty_localAgentSession = localAgentSession.onSwitchChosen(typedEvent);
      auditInvocation(sessionBoundary, "sessionBoundary", "onSwitchChosen", typedEvent);
      sessionBoundary.onSwitchChosen(typedEvent);
    } else if (event instanceof LocalAgentTextUpdated) {
      LocalAgentTextUpdated typedEvent = (LocalAgentTextUpdated) event;
      auditEvent(typedEvent);
      auditInvocation(localAgentSession, "localAgentSession", "onTextUpdated", typedEvent);
      isDirty_localAgentSession = localAgentSession.onTextUpdated(typedEvent);
    } else if (event instanceof LocalAgentTurnEnded) {
      LocalAgentTurnEnded typedEvent = (LocalAgentTurnEnded) event;
      auditEvent(typedEvent);
      auditInvocation(localAgentPermission, "localAgentPermission", "onTurnEnded", typedEvent);
      isDirty_localAgentPermission = localAgentPermission.onTurnEnded(typedEvent);
      auditInvocation(localAgentSession, "localAgentSession", "onTurnEnded", typedEvent);
      isDirty_localAgentSession = localAgentSession.onTurnEnded(typedEvent);
    } else if (event instanceof LocalAgentTurnStarted) {
      LocalAgentTurnStarted typedEvent = (LocalAgentTurnStarted) event;
      auditEvent(typedEvent);
      auditInvocation(localAgentSession, "localAgentSession", "onTurnStarted", typedEvent);
      isDirty_localAgentSession = localAgentSession.onTurnStarted(typedEvent);
    } else if (event instanceof LogAppended) {
      LogAppended typedEvent = (LogAppended) event;
      auditEvent(typedEvent);
      auditInvocation(operationGate, "operationGate", "onLogAppended", typedEvent);
      isDirty_operationGate = operationGate.onLogAppended(typedEvent);
      auditInvocation(openLog, "openLog", "onLogAppended", typedEvent);
      isDirty_openLog = openLog.onLogAppended(typedEvent);
      auditInvocation(logEvidence, "logEvidence", "onLogAppended", typedEvent);
      logEvidence.onLogAppended(typedEvent);
      auditInvocation(recordsView, "recordsView", "onLogAppended", typedEvent);
      isDirty_recordsView = recordsView.onLogAppended(typedEvent);
    } else if (event instanceof LogCleared) {
      LogCleared typedEvent = (LogCleared) event;
      auditEvent(typedEvent);
      auditInvocation(operationGate, "operationGate", "onLogCleared", typedEvent);
      isDirty_operationGate = operationGate.onLogCleared(typedEvent);
      auditInvocation(openLog, "openLog", "onLogCleared", typedEvent);
      isDirty_openLog = openLog.onLogCleared(typedEvent);
      auditInvocation(assistantLoop, "assistantLoop", "onLogCleared", typedEvent);
      isDirty_assistantLoop = assistantLoop.onLogCleared(typedEvent);
    } else if (event instanceof LogClosed) {
      LogClosed typedEvent = (LogClosed) event;
      auditEvent(typedEvent);
      auditInvocation(operationGate, "operationGate", "onLogClosed", typedEvent);
      isDirty_operationGate = operationGate.onLogClosed(typedEvent);
      auditInvocation(effectOutcomes, "effectOutcomes", "onLogClosed", typedEvent);
      effectOutcomes.onLogClosed(typedEvent);
      auditInvocation(openLog, "openLog", "onLogClosed", typedEvent);
      isDirty_openLog = openLog.onLogClosed(typedEvent);
      auditInvocation(assistantLoop, "assistantLoop", "onLogClosed", typedEvent);
      isDirty_assistantLoop = assistantLoop.onLogClosed(typedEvent);
    } else if (event instanceof LogContentObserved) {
      LogContentObserved typedEvent = (LogContentObserved) event;
      auditEvent(typedEvent);
      auditInvocation(logEvidence, "logEvidence", "onLogContentObserved", typedEvent);
      logEvidence.onLogContentObserved(typedEvent);
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
      auditInvocation(onboardingProposal, "onboardingProposal", "onLogOpenFailed", typedEvent);
      isDirty_onboardingProposal = onboardingProposal.onLogOpenFailed(typedEvent);
      auditInvocation(assistantLoop, "assistantLoop", "onLogOpenFailed", typedEvent);
      isDirty_assistantLoop = assistantLoop.onLogOpenFailed(typedEvent);
    } else if (event instanceof LogOpened) {
      LogOpened typedEvent = (LogOpened) event;
      auditEvent(typedEvent);
      auditInvocation(operationGate, "operationGate", "onLogOpened", typedEvent);
      isDirty_operationGate = operationGate.onLogOpened(typedEvent);
      auditInvocation(effectOutcomes, "effectOutcomes", "onLogOpened", typedEvent);
      effectOutcomes.onLogOpened(typedEvent);
      auditInvocation(openLog, "openLog", "onLogOpened", typedEvent);
      isDirty_openLog = openLog.onLogOpened(typedEvent);
      auditInvocation(onboardingProposal, "onboardingProposal", "onLogOpened", typedEvent);
      isDirty_onboardingProposal = onboardingProposal.onLogOpened(typedEvent);
      auditInvocation(logArrival, "logArrival", "onLogOpened", typedEvent);
      logArrival.onLogOpened(typedEvent);
      auditInvocation(assistantLoop, "assistantLoop", "onLogOpened", typedEvent);
      isDirty_assistantLoop = assistantLoop.onLogOpened(typedEvent);
      auditInvocation(startPageNoLogHint, "startPageNoLogHint", "onLogOpened", typedEvent);
      isDirty_startPageNoLogHint = startPageNoLogHint.onLogOpened(typedEvent);
    } else if (event instanceof McpReadinessObserved) {
      McpReadinessObserved typedEvent = (McpReadinessObserved) event;
      auditEvent(typedEvent);
      auditInvocation(aiSetup, "aiSetup", "onReadiness", typedEvent);
      isDirty_aiSetup = aiSetup.onReadiness(typedEvent);
    } else if (event instanceof McpRegistrationChanged) {
      McpRegistrationChanged typedEvent = (McpRegistrationChanged) event;
      auditEvent(typedEvent);
      auditInvocation(aiSetup, "aiSetup", "onRegistrationChanged", typedEvent);
      isDirty_aiSetup = aiSetup.onRegistrationChanged(typedEvent);
    } else if (event instanceof McpRegistrationObserved) {
      McpRegistrationObserved typedEvent = (McpRegistrationObserved) event;
      auditEvent(typedEvent);
      auditInvocation(aiSetup, "aiSetup", "onRegistrationObserved", typedEvent);
      isDirty_aiSetup = aiSetup.onRegistrationObserved(typedEvent);
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
      auditInvocation(assistantLoop, "assistantLoop", "onOpenLogRequested", typedEvent);
      isDirty_assistantLoop = assistantLoop.onOpenLogRequested(typedEvent);
      auditInvocation(projectReopenOffer, "projectReopenOffer", "onOpenLogRequested", typedEvent);
      projectReopenOffer.onOpenLogRequested(typedEvent);
    } else if (event instanceof OpenProjectRequested) {
      OpenProjectRequested typedEvent = (OpenProjectRequested) event;
      auditEvent(typedEvent);
      auditInvocation(operationGate, "operationGate", "onOpenProjectRequested", typedEvent);
      isDirty_operationGate = operationGate.onOpenProjectRequested(typedEvent);
      auditInvocation(openBundle, "openBundle", "onOpenProjectRequested", typedEvent);
      isDirty_openBundle = openBundle.onOpenProjectRequested(typedEvent);
      auditInvocation(assistantLoop, "assistantLoop", "onOpenProjectRequested", typedEvent);
      isDirty_assistantLoop = assistantLoop.onOpenProjectRequested(typedEvent);
      auditInvocation(sessionBoundary, "sessionBoundary", "onOpenProjectRequested", typedEvent);
      sessionBoundary.onOpenProjectRequested(typedEvent);
      auditInvocation(
          projectReopenOffer, "projectReopenOffer", "onOpenProjectRequested", typedEvent);
      projectReopenOffer.onOpenProjectRequested(typedEvent);
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
    } else if (event instanceof ProducerFindingsObserved) {
      ProducerFindingsObserved typedEvent = (ProducerFindingsObserved) event;
      auditEvent(typedEvent);
      auditInvocation(logEvidence, "logEvidence", "onProducerFindingsObserved", typedEvent);
      logEvidence.onProducerFindingsObserved(typedEvent);
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
      auditInvocation(openBundle, "openBundle", "onProfileApplied", typedEvent);
      isDirty_openBundle = openBundle.onProfileApplied(typedEvent);
      auditInvocation(assistantLoop, "assistantLoop", "onProfileApplied", typedEvent);
      isDirty_assistantLoop = assistantLoop.onProfileApplied(typedEvent);
      auditInvocation(starterProject, "starterProject", "onProfileApplied", typedEvent);
      isDirty_starterProject = starterProject.onProfileApplied(typedEvent);
      auditInvocation(sessionBoundary, "sessionBoundary", "onProfileApplied", typedEvent);
      sessionBoundary.onProfileApplied(typedEvent);
      auditInvocation(bundleAnchor, "bundleAnchor", "onProfileApplied", typedEvent);
      bundleAnchor.onProfileApplied(typedEvent);
      auditInvocation(projectReopenOffer, "projectReopenOffer", "onProfileApplied", typedEvent);
      projectReopenOffer.onProfileApplied(typedEvent);
    } else if (event instanceof ProfileLoaded) {
      ProfileLoaded typedEvent = (ProfileLoaded) event;
      auditEvent(typedEvent);
      auditInvocation(operationGate, "operationGate", "onProfileLoaded", typedEvent);
      isDirty_operationGate = operationGate.onProfileLoaded(typedEvent);
      auditInvocation(effectOutcomes, "effectOutcomes", "onProfileLoaded", typedEvent);
      effectOutcomes.onProfileLoaded(typedEvent);
      auditInvocation(openBundle, "openBundle", "onProfileLoaded", typedEvent);
      isDirty_openBundle = openBundle.onProfileLoaded(typedEvent);
      auditInvocation(assistantLoop, "assistantLoop", "onProfileLoaded", typedEvent);
      isDirty_assistantLoop = assistantLoop.onProfileLoaded(typedEvent);
      auditInvocation(sessionBoundary, "sessionBoundary", "onProfileLoaded", typedEvent);
      sessionBoundary.onProfileLoaded(typedEvent);
    } else if (event instanceof ProjectReopenPresented) {
      ProjectReopenPresented typedEvent = (ProjectReopenPresented) event;
      auditEvent(typedEvent);
      auditInvocation(
          projectReopenOffer, "projectReopenOffer", "onProjectReopenPresented", typedEvent);
      projectReopenOffer.onProjectReopenPresented(typedEvent);
    } else if (event instanceof ProjectReopenReady) {
      ProjectReopenReady typedEvent = (ProjectReopenReady) event;
      auditEvent(typedEvent);
      auditInvocation(projectReopenOffer, "projectReopenOffer", "onProjectReopenReady", typedEvent);
      projectReopenOffer.onProjectReopenReady(typedEvent);
    } else if (event instanceof ProjectReopenRequested) {
      ProjectReopenRequested typedEvent = (ProjectReopenRequested) event;
      auditEvent(typedEvent);
      auditInvocation(
          projectReopenOffer, "projectReopenOffer", "onProjectReopenRequested", typedEvent);
      projectReopenOffer.onProjectReopenRequested(typedEvent);
    } else if (event instanceof ProposalApplyRequested) {
      ProposalApplyRequested typedEvent = (ProposalApplyRequested) event;
      auditEvent(typedEvent);
      auditInvocation(onboardingProposal, "onboardingProposal", "onApplyRequested", typedEvent);
      isDirty_onboardingProposal = onboardingProposal.onApplyRequested(typedEvent);
    } else if (event instanceof ProposalChecked) {
      ProposalChecked typedEvent = (ProposalChecked) event;
      auditEvent(typedEvent);
      auditInvocation(onboardingProposal, "onboardingProposal", "onChecked", typedEvent);
      isDirty_onboardingProposal = onboardingProposal.onChecked(typedEvent);
    } else if (event instanceof ProposalDismissed) {
      ProposalDismissed typedEvent = (ProposalDismissed) event;
      auditEvent(typedEvent);
      auditInvocation(onboardingProposal, "onboardingProposal", "onDismissed", typedEvent);
      isDirty_onboardingProposal = onboardingProposal.onDismissed(typedEvent);
    } else if (event instanceof ProposalEffectStarted) {
      ProposalEffectStarted typedEvent = (ProposalEffectStarted) event;
      auditEvent(typedEvent);
      auditInvocation(onboardingProposal, "onboardingProposal", "onEffectStarted", typedEvent);
      isDirty_onboardingProposal = onboardingProposal.onEffectStarted(typedEvent);
    } else if (event instanceof ProposalItemAnswered) {
      ProposalItemAnswered typedEvent = (ProposalItemAnswered) event;
      auditEvent(typedEvent);
      auditInvocation(onboardingProposal, "onboardingProposal", "onItemAnswered", typedEvent);
      isDirty_onboardingProposal = onboardingProposal.onItemAnswered(typedEvent);
    } else if (event instanceof ProposalItemApplied) {
      ProposalItemApplied typedEvent = (ProposalItemApplied) event;
      auditEvent(typedEvent);
      auditInvocation(localAgentSession, "localAgentSession", "onProposalItemApplied", typedEvent);
      isDirty_localAgentSession = localAgentSession.onProposalItemApplied(typedEvent);
      auditInvocation(onboardingProposal, "onboardingProposal", "onApplied", typedEvent);
      isDirty_onboardingProposal = onboardingProposal.onApplied(typedEvent);
    } else if (event instanceof ProposalItemPreviewed) {
      ProposalItemPreviewed typedEvent = (ProposalItemPreviewed) event;
      auditEvent(typedEvent);
      auditInvocation(onboardingProposal, "onboardingProposal", "onPreviewed", typedEvent);
      isDirty_onboardingProposal = onboardingProposal.onPreviewed(typedEvent);
    } else if (event instanceof ProposalPreviewDone) {
      ProposalPreviewDone typedEvent = (ProposalPreviewDone) event;
      auditEvent(typedEvent);
      auditInvocation(onboardingProposal, "onboardingProposal", "onPreviewDone", typedEvent);
      isDirty_onboardingProposal = onboardingProposal.onPreviewDone(typedEvent);
    } else if (event instanceof ProposalReceived) {
      ProposalReceived typedEvent = (ProposalReceived) event;
      auditEvent(typedEvent);
      auditInvocation(onboardingProposal, "onboardingProposal", "onReceived", typedEvent);
      isDirty_onboardingProposal = onboardingProposal.onReceived(typedEvent);
    } else if (event instanceof RecordFlagsChanged) {
      RecordFlagsChanged typedEvent = (RecordFlagsChanged) event;
      auditEvent(typedEvent);
      auditInvocation(recordsView, "recordsView", "onRecordFlagsChanged", typedEvent);
      isDirty_recordsView = recordsView.onRecordFlagsChanged(typedEvent);
    } else if (event instanceof RecordsViewInstalled) {
      RecordsViewInstalled typedEvent = (RecordsViewInstalled) event;
      auditEvent(typedEvent);
      auditInvocation(recordsView, "recordsView", "onRecordsViewInstalled", typedEvent);
      isDirty_recordsView = recordsView.onRecordsViewInstalled(typedEvent);
    } else if (event instanceof RecordsViewMeasured) {
      RecordsViewMeasured typedEvent = (RecordsViewMeasured) event;
      auditEvent(typedEvent);
      auditInvocation(recordsView, "recordsView", "onRecordsViewMeasured", typedEvent);
      isDirty_recordsView = recordsView.onRecordsViewMeasured(typedEvent);
      auditInvocation(assistantLoop, "assistantLoop", "onRecordsViewMeasured", typedEvent);
      isDirty_assistantLoop = assistantLoop.onRecordsViewMeasured(typedEvent);
    } else if (event instanceof RegisterAnswered) {
      RegisterAnswered typedEvent = (RegisterAnswered) event;
      auditEvent(typedEvent);
      auditInvocation(aiSetup, "aiSetup", "onRegisterAnswered", typedEvent);
      isDirty_aiSetup = aiSetup.onRegisterAnswered(typedEvent);
    } else if (event instanceof RestSwitchObserved) {
      RestSwitchObserved typedEvent = (RestSwitchObserved) event;
      auditEvent(typedEvent);
      auditInvocation(actionListener, "actionListener", "onRestSwitch", typedEvent);
      isDirty_actionListener = actionListener.onRestSwitch(typedEvent);
      auditInvocation(aiSetup, "aiSetup", "onRestSwitch", typedEvent);
      isDirty_aiSetup = aiSetup.onRestSwitch(typedEvent);
    } else if (event instanceof ReturnToMainOptions) {
      ReturnToMainOptions typedEvent = (ReturnToMainOptions) event;
      auditEvent(typedEvent);
      auditInvocation(startPagePrompt, "startPagePrompt", "onReturnToMainOptions", typedEvent);
      startPagePrompt.onReturnToMainOptions(typedEvent);
    } else if (event instanceof ScanScheduled) {
      ScanScheduled typedEvent = (ScanScheduled) event;
      auditEvent(typedEvent);
      auditInvocation(logEvidence, "logEvidence", "onScanScheduled", typedEvent);
      logEvidence.onScanScheduled(typedEvent);
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
      auditInvocation(openBundle, "openBundle", "onSettingsRestored", typedEvent);
      isDirty_openBundle = openBundle.onSettingsRestored(typedEvent);
      auditInvocation(assistantLoop, "assistantLoop", "onSettingsRestored", typedEvent);
      isDirty_assistantLoop = assistantLoop.onSettingsRestored(typedEvent);
    } else if (event instanceof SkillsVendored) {
      SkillsVendored typedEvent = (SkillsVendored) event;
      auditEvent(typedEvent);
      auditInvocation(starterProject, "starterProject", "onSkillsVendored", typedEvent);
      isDirty_starterProject = starterProject.onSkillsVendored(typedEvent);
    } else if (event instanceof SourceRootsObserved) {
      SourceRootsObserved typedEvent = (SourceRootsObserved) event;
      auditEvent(typedEvent);
      auditInvocation(bundleAnchor, "bundleAnchor", "onSourceRootsObserved", typedEvent);
      bundleAnchor.onSourceRootsObserved(typedEvent);
    } else if (event instanceof StartPageAvailability) {
      StartPageAvailability typedEvent = (StartPageAvailability) event;
      auditEvent(typedEvent);
      auditInvocation(aiSetup, "aiSetup", "onAvailability", typedEvent);
      isDirty_aiSetup = aiSetup.onAvailability(typedEvent);
      auditInvocation(startPagePrompt, "startPagePrompt", "onAvailability", typedEvent);
      startPagePrompt.onAvailability(typedEvent);
    } else if (event instanceof StartPageEffectStarted) {
      StartPageEffectStarted typedEvent = (StartPageEffectStarted) event;
      auditEvent(typedEvent);
      auditInvocation(startPagePrompt, "startPagePrompt", "onEffectStarted", typedEvent);
      startPagePrompt.onEffectStarted(typedEvent);
    } else if (event instanceof StartPageFlowDone) {
      StartPageFlowDone typedEvent = (StartPageFlowDone) event;
      auditEvent(typedEvent);
      auditInvocation(startPageNoLogHint, "startPageNoLogHint", "onFlowDone", typedEvent);
      isDirty_startPageNoLogHint = startPageNoLogHint.onFlowDone(typedEvent);
      auditInvocation(startPagePrompt, "startPagePrompt", "onFlowDone", typedEvent);
      startPagePrompt.onFlowDone(typedEvent);
    } else if (event instanceof StartPageNoLogAnswered) {
      StartPageNoLogAnswered typedEvent = (StartPageNoLogAnswered) event;
      auditEvent(typedEvent);
      auditInvocation(startPageNoLogHint, "startPageNoLogHint", "onAnswered", typedEvent);
      isDirty_startPageNoLogHint = startPageNoLogHint.onAnswered(typedEvent);
    } else if (event instanceof StartPagePromptSent) {
      StartPagePromptSent typedEvent = (StartPagePromptSent) event;
      auditEvent(typedEvent);
      auditInvocation(startPageNoLogHint, "startPageNoLogHint", "onSent", typedEvent);
      isDirty_startPageNoLogHint = startPageNoLogHint.onSent(typedEvent);
      auditInvocation(aiSetup, "aiSetup", "onStartPageSent", typedEvent);
      isDirty_aiSetup = aiSetup.onStartPageSent(typedEvent);
      auditInvocation(startPagePrompt, "startPagePrompt", "onSent", typedEvent);
      startPagePrompt.onSent(typedEvent);
    } else if (event instanceof StartPromptPackDropped) {
      StartPromptPackDropped typedEvent = (StartPromptPackDropped) event;
      auditEvent(typedEvent);
      auditInvocation(startPagePrompt, "startPagePrompt", "onPackDropped", typedEvent);
      startPagePrompt.onPackDropped(typedEvent);
    } else if (event instanceof StarterCatalogueObserved) {
      StarterCatalogueObserved typedEvent = (StarterCatalogueObserved) event;
      auditEvent(typedEvent);
      auditInvocation(starterProject, "starterProject", "onCatalogueObserved", typedEvent);
      isDirty_starterProject = starterProject.onCatalogueObserved(typedEvent);
    } else if (event instanceof StarterChosen) {
      StarterChosen typedEvent = (StarterChosen) event;
      auditEvent(typedEvent);
      auditInvocation(starterProject, "starterProject", "onChosen", typedEvent);
      isDirty_starterProject = starterProject.onChosen(typedEvent);
    } else if (event instanceof StarterConfigured) {
      StarterConfigured typedEvent = (StarterConfigured) event;
      auditEvent(typedEvent);
      auditInvocation(starterProject, "starterProject", "onConfigured", typedEvent);
      isDirty_starterProject = starterProject.onConfigured(typedEvent);
    } else if (event instanceof StarterDefaultsObserved) {
      StarterDefaultsObserved typedEvent = (StarterDefaultsObserved) event;
      auditEvent(typedEvent);
      auditInvocation(onboardingProposal, "onboardingProposal", "onStarterDefaults", typedEvent);
      isDirty_onboardingProposal = onboardingProposal.onStarterDefaults(typedEvent);
      auditInvocation(starterProject, "starterProject", "onDefaultsObserved", typedEvent);
      isDirty_starterProject = starterProject.onDefaultsObserved(typedEvent);
    } else if (event instanceof StarterDesignOpened) {
      StarterDesignOpened typedEvent = (StarterDesignOpened) event;
      auditEvent(typedEvent);
      auditInvocation(starterProject, "starterProject", "onDesignOpened", typedEvent);
      isDirty_starterProject = starterProject.onDesignOpened(typedEvent);
    } else if (event instanceof StarterDestinationChosen) {
      StarterDestinationChosen typedEvent = (StarterDestinationChosen) event;
      auditEvent(typedEvent);
      auditInvocation(starterProject, "starterProject", "onDestinationChosen", typedEvent);
      isDirty_starterProject = starterProject.onDestinationChosen(typedEvent);
    } else if (event instanceof StarterDirectoryObserved) {
      StarterDirectoryObserved typedEvent = (StarterDirectoryObserved) event;
      auditEvent(typedEvent);
      auditInvocation(starterProject, "starterProject", "onDirectoryObserved", typedEvent);
      isDirty_starterProject = starterProject.onDirectoryObserved(typedEvent);
    } else if (event instanceof StarterDiscoveryObserved) {
      StarterDiscoveryObserved typedEvent = (StarterDiscoveryObserved) event;
      auditEvent(typedEvent);
      auditInvocation(starterProject, "starterProject", "onDiscoveryObserved", typedEvent);
      isDirty_starterProject = starterProject.onDiscoveryObserved(typedEvent);
    } else if (event instanceof StarterEffectStarted) {
      StarterEffectStarted typedEvent = (StarterEffectStarted) event;
      auditEvent(typedEvent);
      auditInvocation(starterProject, "starterProject", "onEffectStarted", typedEvent);
      isDirty_starterProject = starterProject.onEffectStarted(typedEvent);
    } else if (event instanceof StarterGraphOfferAnswered) {
      StarterGraphOfferAnswered typedEvent = (StarterGraphOfferAnswered) event;
      auditEvent(typedEvent);
      auditInvocation(starterProject, "starterProject", "onGraphOfferAnswered", typedEvent);
      isDirty_starterProject = starterProject.onGraphOfferAnswered(typedEvent);
    } else if (event instanceof StarterGraphmlAppeared) {
      StarterGraphmlAppeared typedEvent = (StarterGraphmlAppeared) event;
      auditEvent(typedEvent);
      auditInvocation(starterProject, "starterProject", "onGraphmlAppeared", typedEvent);
      isDirty_starterProject = starterProject.onGraphmlAppeared(typedEvent);
    } else if (event instanceof StarterInstallFailed) {
      StarterInstallFailed typedEvent = (StarterInstallFailed) event;
      auditEvent(typedEvent);
      auditInvocation(
          onboardingProposal, "onboardingProposal", "onStarterInstallFailed", typedEvent);
      isDirty_onboardingProposal = onboardingProposal.onStarterInstallFailed(typedEvent);
      auditInvocation(starterProject, "starterProject", "onInstallFailed", typedEvent);
      isDirty_starterProject = starterProject.onInstallFailed(typedEvent);
    } else if (event instanceof StarterInstalled) {
      StarterInstalled typedEvent = (StarterInstalled) event;
      auditEvent(typedEvent);
      auditInvocation(starterProject, "starterProject", "onInstalled", typedEvent);
      isDirty_starterProject = starterProject.onInstalled(typedEvent);
    } else if (event instanceof StarterMenuRequested) {
      StarterMenuRequested typedEvent = (StarterMenuRequested) event;
      auditEvent(typedEvent);
      auditInvocation(starterProject, "starterProject", "onMenuRequested", typedEvent);
      isDirty_starterProject = starterProject.onMenuRequested(typedEvent);
    } else if (event instanceof StarterProjectOpened) {
      StarterProjectOpened typedEvent = (StarterProjectOpened) event;
      auditEvent(typedEvent);
      auditInvocation(
          onboardingProposal, "onboardingProposal", "onStarterProjectOpened", typedEvent);
      isDirty_onboardingProposal = onboardingProposal.onStarterProjectOpened(typedEvent);
      auditInvocation(starterProject, "starterProject", "onProjectOpened", typedEvent);
      isDirty_starterProject = starterProject.onProjectOpened(typedEvent);
    } else if (event instanceof StarterReviewAnswered) {
      StarterReviewAnswered typedEvent = (StarterReviewAnswered) event;
      auditEvent(typedEvent);
      auditInvocation(
          onboardingProposal, "onboardingProposal", "onStarterReviewAnswered", typedEvent);
      isDirty_onboardingProposal = onboardingProposal.onStarterReviewAnswered(typedEvent);
      auditInvocation(starterProject, "starterProject", "onReviewAnswered", typedEvent);
      isDirty_starterProject = starterProject.onReviewAnswered(typedEvent);
    } else if (event instanceof StatusShown) {
      StatusShown typedEvent = (StatusShown) event;
      auditEvent(typedEvent);
      auditInvocation(operationGate, "operationGate", "onStatusShown", typedEvent);
      isDirty_operationGate = operationGate.onStatusShown(typedEvent);
      auditInvocation(effectOutcomes, "effectOutcomes", "onStatusShown", typedEvent);
      effectOutcomes.onStatusShown(typedEvent);
    } else if (event instanceof TimeOrderObserved) {
      TimeOrderObserved typedEvent = (TimeOrderObserved) event;
      auditEvent(typedEvent);
      auditInvocation(logEvidence, "logEvidence", "onTimeOrderObserved", typedEvent);
      logEvidence.onTimeOrderObserved(typedEvent);
    } else if (event instanceof ViewFilterChanged) {
      ViewFilterChanged typedEvent = (ViewFilterChanged) event;
      auditEvent(typedEvent);
      auditInvocation(pairingQualifier, "pairingQualifier", "onViewFilterChanged", typedEvent);
      pairingQualifier.onViewFilterChanged(typedEvent);
      auditInvocation(recordsView, "recordsView", "onViewFilterChanged", typedEvent);
      isDirty_recordsView = recordsView.onViewFilterChanged(typedEvent);
      auditInvocation(assistantLoop, "assistantLoop", "onViewFilterChanged", typedEvent);
      isDirty_assistantLoop = assistantLoop.onViewFilterChanged(typedEvent);
    } else if (event instanceof WalkAcknowledged) {
      WalkAcknowledged typedEvent = (WalkAcknowledged) event;
      auditEvent(typedEvent);
      auditInvocation(walkPlayback, "walkPlayback", "onWalkAcknowledged", typedEvent);
      walkPlayback.onWalkAcknowledged(typedEvent);
    } else if (event instanceof WalkDefinitionChanged) {
      WalkDefinitionChanged typedEvent = (WalkDefinitionChanged) event;
      auditEvent(typedEvent);
      auditInvocation(walkPlayback, "walkPlayback", "onWalkDefinitionChanged", typedEvent);
      walkPlayback.onWalkDefinitionChanged(typedEvent);
    } else if (event instanceof WalkEndRequested) {
      WalkEndRequested typedEvent = (WalkEndRequested) event;
      auditEvent(typedEvent);
      auditInvocation(walkPlayback, "walkPlayback", "onWalkEndRequested", typedEvent);
      walkPlayback.onWalkEndRequested(typedEvent);
    } else if (event instanceof WalkNavigated) {
      WalkNavigated typedEvent = (WalkNavigated) event;
      auditEvent(typedEvent);
      auditInvocation(walkPlayback, "walkPlayback", "onWalkNavigated", typedEvent);
      walkPlayback.onWalkNavigated(typedEvent);
    } else if (event instanceof WalkPlayRequested) {
      WalkPlayRequested typedEvent = (WalkPlayRequested) event;
      auditEvent(typedEvent);
      auditInvocation(walkPlayback, "walkPlayback", "onWalkPlayRequested", typedEvent);
      walkPlayback.onWalkPlayRequested(typedEvent);
      auditInvocation(projectReopenOffer, "projectReopenOffer", "onWalkPlayRequested", typedEvent);
      projectReopenOffer.onWalkPlayRequested(typedEvent);
    } else if (event instanceof WalkStepPrepared) {
      WalkStepPrepared typedEvent = (WalkStepPrepared) event;
      auditEvent(typedEvent);
      auditInvocation(walkPlayback, "walkPlayback", "onWalkStepPrepared", typedEvent);
      walkPlayback.onWalkStepPrepared(typedEvent);
    } else if (event instanceof WalkTargetsLit) {
      WalkTargetsLit typedEvent = (WalkTargetsLit) event;
      auditEvent(typedEvent);
      auditInvocation(walkPlayback, "walkPlayback", "onWalkTargetsLit", typedEvent);
      walkPlayback.onWalkTargetsLit(typedEvent);
    } else if (event instanceof WalkViewApplied) {
      WalkViewApplied typedEvent = (WalkViewApplied) event;
      auditEvent(typedEvent);
      auditInvocation(walkPlayback, "walkPlayback", "onWalkViewApplied", typedEvent);
      walkPlayback.onWalkViewApplied(typedEvent);
    } else if (event instanceof WalkViewChanged) {
      WalkViewChanged typedEvent = (WalkViewChanged) event;
      auditEvent(typedEvent);
      auditInvocation(walkPlayback, "walkPlayback", "onWalkViewChanged", typedEvent);
      walkPlayback.onWalkViewChanged(typedEvent);
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
    commonDispatchTail_1(typedEvent);
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
    auditor.nodeRegistered(actionListener, "actionListener");
    auditor.nodeRegistered(activeProject, "activeProject");
    auditor.nodeRegistered(aiSetup, "aiSetup");
    auditor.nodeRegistered(assistantLoop, "assistantLoop");
    auditor.nodeRegistered(assistantMode, "assistantMode");
    auditor.nodeRegistered(auditInstallation, "auditInstallation");
    auditor.nodeRegistered(bundleAnchor, "bundleAnchor");
    auditor.nodeRegistered(coverageClaim, "coverageClaim");
    auditor.nodeRegistered(designSession, "designSession");
    auditor.nodeRegistered(effectOutcomes, "effectOutcomes");
    auditor.nodeRegistered(effectQueue, "effectQueue");
    auditor.nodeRegistered(evidenceCapture, "evidenceCapture");
    auditor.nodeRegistered(exampleDownload, "exampleDownload");
    auditor.nodeRegistered(ignoredParameters, "ignoredParameters");
    auditor.nodeRegistered(localAgentPermission, "localAgentPermission");
    auditor.nodeRegistered(localAgentSession, "localAgentSession");
    auditor.nodeRegistered(logArrival, "logArrival");
    auditor.nodeRegistered(logEvidence, "logEvidence");
    auditor.nodeRegistered(logOpening, "logOpening");
    auditor.nodeRegistered(onboardingProposal, "onboardingProposal");
    auditor.nodeRegistered(openBundle, "openBundle");
    auditor.nodeRegistered(openGraph, "openGraph");
    auditor.nodeRegistered(openLog, "openLog");
    auditor.nodeRegistered(operationGate, "operationGate");
    auditor.nodeRegistered(pairingQualifier, "pairingQualifier");
    auditor.nodeRegistered(pairing, "pairing");
    auditor.nodeRegistered(projectReopenOffer, "projectReopenOffer");
    auditor.nodeRegistered(recordsView, "recordsView");
    auditor.nodeRegistered(sessionBoundary, "sessionBoundary");
    auditor.nodeRegistered(sessionRecovery, "sessionRecovery");
    auditor.nodeRegistered(startPageNoLogHint, "startPageNoLogHint");
    auditor.nodeRegistered(startPagePrompt, "startPagePrompt");
    auditor.nodeRegistered(starterProject, "starterProject");
    auditor.nodeRegistered(walkPlayback, "walkPlayback");
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

  /**
   * DataFlow.runInEventCycle: run a host's action as an event cycle, with auditEvent as its audit
   * context. A buffered calculation runs first (it closes its own record), then the cycle opens
   * with the caller's event and the action runs; a finally closes the cycle and dispatches what the
   * action queued, and an inner finally clears processing, so a throw cannot wedge the processor.
   * auditEvent is dispatched to no node and marks nothing dirty. An auditEvent that is an Event
   * supplies its own event time, as on the event path; any other object takes the process time. Not
   * re-entrant. To disable the path in a generated processor, make this throw.
   *
   * <p>No @Override, deliberately: generated source must also compile against a runtime that
   * predates DataFlow.runInEventCycle (1.0.16), where this is an ordinary public method a host
   * finds on the class. From the runtime that declares it, it overrides the interface default by
   * signature. GeneratedSourceOnOldRuntimeTest compiles freshly generated source against the oldest
   * supported runtime so this cannot silently regress.
   */
  public void runInEventCycle(Object auditEvent, Runnable action) {
    if (processing) {
      throw new IllegalStateException(
          "runInEventCycle is not re-entrant: it was called inside an event cycle");
    }
    if (buffering) {
      triggerCalculation();
    }
    processing = true;
    try {
      // an Event supplies its own event time, as on the event path: the static type selects the auditors'
      // overload, eventReceived(Event) or eventReceived(Object)
      if (auditEvent instanceof com.telamin.fluxtion.runtime.event.Event) {
        auditEvent((com.telamin.fluxtion.runtime.event.Event) auditEvent);
      } else {
        auditEvent(auditEvent);
      }
      action.run();
    } finally {
      // closed even when the action throws, as a host's own audit bracket closes its record; processing is
      // cleared innermost, so a throw from the close cannot wedge the processor either
      try {
        afterEvent();
        callbackDispatcher.dispatchQueuedCallbacks();
      } finally {
        processing = false;
      }
    }
  }

  private void afterEvent() {
    clock.processingComplete();
    eventLogger.processingComplete();
    isDirty_actionListener = false;
    isDirty_activeProject = false;
    isDirty_aiSetup = false;
    isDirty_assistantLoop = false;
    isDirty_assistantMode = false;
    isDirty_auditInstallation = false;
    isDirty_localAgentPermission = false;
    isDirty_localAgentSession = false;
    isDirty_onboardingProposal = false;
    isDirty_openBundle = false;
    isDirty_openGraph = false;
    isDirty_openLog = false;
    isDirty_operationGate = false;
    isDirty_pairing = false;
    isDirty_recordsView = false;
    isDirty_startPageNoLogHint = false;
    isDirty_starterProject = false;
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
      dirtyFlagSupplierMap.put(actionListener, () -> isDirty_actionListener);
      dirtyFlagSupplierMap.put(activeProject, () -> isDirty_activeProject);
      dirtyFlagSupplierMap.put(aiSetup, () -> isDirty_aiSetup);
      dirtyFlagSupplierMap.put(assistantLoop, () -> isDirty_assistantLoop);
      dirtyFlagSupplierMap.put(assistantMode, () -> isDirty_assistantMode);
      dirtyFlagSupplierMap.put(auditInstallation, () -> isDirty_auditInstallation);
      dirtyFlagSupplierMap.put(localAgentPermission, () -> isDirty_localAgentPermission);
      dirtyFlagSupplierMap.put(localAgentSession, () -> isDirty_localAgentSession);
      dirtyFlagSupplierMap.put(onboardingProposal, () -> isDirty_onboardingProposal);
      dirtyFlagSupplierMap.put(openBundle, () -> isDirty_openBundle);
      dirtyFlagSupplierMap.put(openGraph, () -> isDirty_openGraph);
      dirtyFlagSupplierMap.put(openLog, () -> isDirty_openLog);
      dirtyFlagSupplierMap.put(operationGate, () -> isDirty_operationGate);
      dirtyFlagSupplierMap.put(pairing, () -> isDirty_pairing);
      dirtyFlagSupplierMap.put(recordsView, () -> isDirty_recordsView);
      dirtyFlagSupplierMap.put(startPageNoLogHint, () -> isDirty_startPageNoLogHint);
      dirtyFlagSupplierMap.put(starterProject, () -> isDirty_starterProject);
    }
    return dirtyFlagSupplierMap.getOrDefault(node, DataFlow.ALWAYS_FALSE);
  }

  @Override
  public void setDirty(Object node, boolean dirtyFlag) {
    if (dirtyFlagUpdateMap.isEmpty()) {
      dirtyFlagUpdateMap.put(actionListener, (b) -> isDirty_actionListener = b);
      dirtyFlagUpdateMap.put(activeProject, (b) -> isDirty_activeProject = b);
      dirtyFlagUpdateMap.put(aiSetup, (b) -> isDirty_aiSetup = b);
      dirtyFlagUpdateMap.put(assistantLoop, (b) -> isDirty_assistantLoop = b);
      dirtyFlagUpdateMap.put(assistantMode, (b) -> isDirty_assistantMode = b);
      dirtyFlagUpdateMap.put(auditInstallation, (b) -> isDirty_auditInstallation = b);
      dirtyFlagUpdateMap.put(localAgentPermission, (b) -> isDirty_localAgentPermission = b);
      dirtyFlagUpdateMap.put(localAgentSession, (b) -> isDirty_localAgentSession = b);
      dirtyFlagUpdateMap.put(onboardingProposal, (b) -> isDirty_onboardingProposal = b);
      dirtyFlagUpdateMap.put(openBundle, (b) -> isDirty_openBundle = b);
      dirtyFlagUpdateMap.put(openGraph, (b) -> isDirty_openGraph = b);
      dirtyFlagUpdateMap.put(openLog, (b) -> isDirty_openLog = b);
      dirtyFlagUpdateMap.put(operationGate, (b) -> isDirty_operationGate = b);
      dirtyFlagUpdateMap.put(pairing, (b) -> isDirty_pairing = b);
      dirtyFlagUpdateMap.put(recordsView, (b) -> isDirty_recordsView = b);
      dirtyFlagUpdateMap.put(startPageNoLogHint, (b) -> isDirty_startPageNoLogHint = b);
      dirtyFlagUpdateMap.put(starterProject, (b) -> isDirty_starterProject = b);
    }
    dirtyFlagUpdateMap.get(node).accept(dirtyFlag);
  }

  private boolean guardCheck_activeProject() {
    return isDirty_operationGate;
  }

  private boolean guardCheck_aiSetup() {
    return isDirty_actionListener
        | isDirty_assistantLoop
        | isDirty_localAgentSession
        | isDirty_openLog
        | isDirty_startPageNoLogHint;
  }

  private boolean guardCheck_assistantLoop() {
    return isDirty_activeProject
        | isDirty_assistantMode
        | isDirty_openGraph
        | isDirty_openLog
        | isDirty_operationGate
        | isDirty_recordsView;
  }

  private boolean guardCheck_assistantMode() {
    return isDirty_localAgentSession;
  }

  private boolean guardCheck_auditInstallation() {
    return isDirty_openGraph;
  }

  private boolean guardCheck_bundleAnchor() {
    return isDirty_openBundle;
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

  private boolean guardCheck_evidenceCapture() {
    return isDirty_openLog | isDirty_operationGate;
  }

  private boolean guardCheck_localAgentSession() {
    return isDirty_actionListener | isDirty_localAgentPermission | isDirty_openLog;
  }

  private boolean guardCheck_logArrival() {
    return isDirty_openGraph | isDirty_operationGate | isDirty_pairing;
  }

  private boolean guardCheck_logEvidence() {
    return isDirty_openLog;
  }

  private boolean guardCheck_logOpening() {
    return isDirty_operationGate;
  }

  private boolean guardCheck_onboardingProposal() {
    return isDirty_localAgentSession;
  }

  private boolean guardCheck_openBundle() {
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

  private boolean guardCheck_projectReopenOffer() {
    return isDirty_openBundle;
  }

  private boolean guardCheck_recordsView() {
    return isDirty_openLog;
  }

  private boolean guardCheck_sessionBoundary() {
    return isDirty_activeProject
        | isDirty_localAgentSession
        | isDirty_onboardingProposal
        | isDirty_openGraph
        | isDirty_openLog
        | isDirty_operationGate
        | isDirty_starterProject;
  }

  private boolean guardCheck_sessionRecovery() {
    return isDirty_operationGate;
  }

  private boolean guardCheck_startPageNoLogHint() {
    return isDirty_openLog;
  }

  private boolean guardCheck_startPagePrompt() {
    return isDirty_aiSetup
        | isDirty_assistantMode
        | isDirty_localAgentSession
        | isDirty_openLog
        | isDirty_startPageNoLogHint;
  }

  private boolean guardCheck_starterProject() {
    return isDirty_onboardingProposal;
  }

  private boolean guardCheck_walkPlayback() {
    return isDirty_assistantLoop | isDirty_openLog;
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
      case "actionListener":
        return (T) actionListener;
      case "activeProject":
        return (T) activeProject;
      case "aiSetup":
        return (T) aiSetup;
      case "assistantLoop":
        return (T) assistantLoop;
      case "assistantMode":
        return (T) assistantMode;
      case "auditInstallation":
        return (T) auditInstallation;
      case "bundleAnchor":
        return (T) bundleAnchor;
      case "coverageClaim":
        return (T) coverageClaim;
      case "designSession":
        return (T) designSession;
      case "effectOutcomes":
        return (T) effectOutcomes;
      case "effectQueue":
        return (T) effectQueue;
      case "evidenceCapture":
        return (T) evidenceCapture;
      case "exampleDownload":
        return (T) exampleDownload;
      case "ignoredParameters":
        return (T) ignoredParameters;
      case "localAgentPermission":
        return (T) localAgentPermission;
      case "localAgentSession":
        return (T) localAgentSession;
      case "logArrival":
        return (T) logArrival;
      case "logEvidence":
        return (T) logEvidence;
      case "logOpening":
        return (T) logOpening;
      case "onboardingProposal":
        return (T) onboardingProposal;
      case "openBundle":
        return (T) openBundle;
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
      case "projectReopenOffer":
        return (T) projectReopenOffer;
      case "recordsView":
        return (T) recordsView;
      case "sessionBoundary":
        return (T) sessionBoundary;
      case "sessionRecovery":
        return (T) sessionRecovery;
      case "startPageNoLogHint":
        return (T) startPageNoLogHint;
      case "startPagePrompt":
        return (T) startPagePrompt;
      case "starterProject":
        return (T) starterProject;
      case "walkPlayback":
        return (T) walkPlayback;
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
    if (node == actionListener) {
      return "actionListener";
    }
    if (node == activeProject) {
      return "activeProject";
    }
    if (node == aiSetup) {
      return "aiSetup";
    }
    if (node == assistantLoop) {
      return "assistantLoop";
    }
    if (node == assistantMode) {
      return "assistantMode";
    }
    if (node == auditInstallation) {
      return "auditInstallation";
    }
    if (node == bundleAnchor) {
      return "bundleAnchor";
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
    if (node == evidenceCapture) {
      return "evidenceCapture";
    }
    if (node == exampleDownload) {
      return "exampleDownload";
    }
    if (node == ignoredParameters) {
      return "ignoredParameters";
    }
    if (node == localAgentPermission) {
      return "localAgentPermission";
    }
    if (node == localAgentSession) {
      return "localAgentSession";
    }
    if (node == logArrival) {
      return "logArrival";
    }
    if (node == logEvidence) {
      return "logEvidence";
    }
    if (node == logOpening) {
      return "logOpening";
    }
    if (node == onboardingProposal) {
      return "onboardingProposal";
    }
    if (node == openBundle) {
      return "openBundle";
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
    if (node == projectReopenOffer) {
      return "projectReopenOffer";
    }
    if (node == recordsView) {
      return "recordsView";
    }
    if (node == sessionBoundary) {
      return "sessionBoundary";
    }
    if (node == sessionRecovery) {
      return "sessionRecovery";
    }
    if (node == startPageNoLogHint) {
      return "startPageNoLogHint";
    }
    if (node == startPagePrompt) {
      return "startPagePrompt";
    }
    if (node == starterProject) {
      return "starterProject";
    }
    if (node == walkPlayback) {
      return "walkPlayback";
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
