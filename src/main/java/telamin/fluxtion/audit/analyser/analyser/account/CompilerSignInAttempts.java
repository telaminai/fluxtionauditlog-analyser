package telamin.fluxtion.audit.analyser.analyser.account;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/** Session-owned device attempts for an external MCP process; secrets never leave this package. */
public final class CompilerSignInAttempts implements AutoCloseable {
    private final CompilerAccountClient client;
    private final CredentialStore store;
    private final BooleanSupplier environmentOverride;
    private final Map<String,Attempt> attempts = new LinkedHashMap<>();
    private final ExecutorService workers = Executors.newFixedThreadPool(4, r -> {
        Thread t=new Thread(r,"compiler-sign-in"); t.setDaemon(true); return t;
    });
    private boolean closed;
    private static final class Attempt {
        final String id=UUID.randomUUID().toString();
        final String origin;
        final long created=System.nanoTime();
        final AtomicBoolean cancelled=new AtomicBoolean();
        Map<String,Object> result;
        Future<?> task;
        Attempt(String origin) { this.origin=origin; }
    }
    public CompilerSignInAttempts(CompilerAccountClient client, CredentialStore store, BooleanSupplier override) {
        this.client=client; this.store=store; this.environmentOverride=override;
    }
    public static CompilerSignInAttempts standard() {
        return new CompilerSignInAttempts(CompilerAccountClient.standard(),CredentialStores.forThisMachine(),
                () -> { String token=System.getenv("FLUXTION_API_TOKEN"); return token!=null&&!token.isBlank(); });
    }
    /** Explicit service replacement requires separate user confirmation supplied by the invoking client. */
    public synchronized Map<String,Object> start(String origin, boolean replaceService) {
        if (closed) return unknown();
        attempts.values().removeIf(a -> a.result!=null && !"pending".equals(a.result.get("event"))
                && System.nanoTime()-a.created > TimeUnit.MINUTES.toNanos(30));
        if (attempts.values().stream().filter(a -> a.result != null && "pending".equals(a.result.get("event"))).count() >= 4)
            return Map.of("event","usage_error","message","Four sign-in attempts are pending; finish or cancel one first.");
        if (attempts.size() >= 128) {
            Iterator<Attempt> oldest = attempts.values().iterator();
            while (oldest.hasNext()) if (!"pending".equals(oldest.next().result.get("event"))) { oldest.remove(); break; }
        }
        try { origin=ServiceAddress.require(origin); }
        catch (IllegalArgumentException e) { return Map.of("event","usage_error","message","A valid compiler origin is required."); }
        Attempt a=new Attempt(origin); attempts.put(a.id,a);
        if (environmentOverride.getAsBoolean()) return finish(a,"usage_error",null,"An environment token overrides builds. Diagnose it with the Maven account goal; signing in cannot replace it.");
        CompilerCredential previous;
        try { previous=store.load().orElse(null); }
        catch (Exception | LinkageError e) {
            return recovery(a,"service_error","The shared credential store could not be read. Check its path and permissions. "
                    + "Use the documented Maven login flow for build sign-in after correcting the store; no credential changed.");
        }
        // A valid shared record is authoritative. Reading a legacy keyring is not a prerequisite for using it.
        if (previous == null && store instanceof SharedCredentialStore shared) {
            try {
                if (shared.migration().isPresent())
                    return recovery(a,"usage_error","A previous Analyser-only sign-in needs an explicit migration choice. "
                            + "Use the Analyser Compiler account dialog, or use the documented Maven login flow in a background terminal. "
                            + "Maven can create the shared build sign-in without changing the previous Analyser copy.");
            } catch (Exception | LinkageError e) {
                return recovery(a,"service_error","The previous Analyser credential store is unavailable. "
                        + "Use the documented Maven login flow in a background terminal for build sign-in; the previous copy is unchanged.");
            }
        }
        try {
            if (previous!=null && !ServiceAddress.sameOrigin(previous.service(),origin) && !replaceService)
                return finish(a,"usage_error",null,"Switching service replaces the machine sign-in and revokes the previous credential. Ask the user to confirm replacement.");
            if (previous!=null && ServiceAddress.sameOrigin(previous.service(),origin)) {
                try {
                    if (!previous.accountId().equals(client.me(previous).selectedAccount()))
                        return finish(a,"service_error",null,"Stored account identity does not match the service.");
                    return finish(a,"signed_in",previous.accountId(),"Existing shared credential verified; external build overrides remain independent.");
                } catch (CompilerAccountClient.Unauthorized rejected) {
                    // The selected service rejected the credential; a new approval may repair it.
                } catch (CompilerAccountClient.AccountException e) {
                    // A failure is not proof of revocation; do not silently replace an account during an outage.
                    return finish(a,"service_error",null,"Existing sign-in could not be verified. Check the account, or explicitly remove it before a new sign-in.");
                }
            }
            CompilerAccountClient.DeviceStart device=client.start(origin);
            Map<String,Object> pending=result(a,"pending",null);
            pending.put("verificationUri",device.browserUri());
            pending.put("userCode",device.userCode()); pending.put("expiresIn",device.expiresInSeconds());
            a.result=Collections.unmodifiableMap(pending);
            a.task=workers.submit(()->await(a,device,previous));
            return a.result;
        } catch (CompilerAccountClient.AccountException e) {
            return recovery(a,"service_error",e.getMessage() + ". No credential changed. Use the documented Maven login flow if this client cannot start sign-in.");
        } catch (Exception e) {
            return recovery(a,"service_error","Could not start sign-in; no credential changed. Use the documented Maven login flow, or check the compiler service and trusted verification origin.");
        }
    }
    private Map<String,Object> recovery(Attempt a,String event,String message) {
        Map<String,Object> r=new LinkedHashMap<>(finish(a,event,null,message));
        r.put("recovery","maven_login"); a.result=Collections.unmodifiableMap(r); return a.result;
    }
    private void await(Attempt a, CompilerAccountClient.DeviceStart device, CompilerCredential previous) {
        CompilerAccountClient.Outcome outcome;
        try { outcome=client.await(device,a.cancelled::get); }
        catch (RuntimeException e) { synchronized (this) {
            if (!a.cancelled.get()) finish(a,"service_error",null,"Sign-in stopped unexpectedly; no credential saved.");
        } return; }
        if (outcome instanceof CompilerAccountClient.Approved approved) {
            CompilerCredential credential=approved.credential();
            boolean saved=false;
            try {
                if (!credential.accountId().equals(client.me(credential).selectedAccount()))
                    throw new IllegalStateException("account mismatch");
                synchronized (this) {
                    if (a.cancelled.get()||closed) return;
                    if (!store.replace(previous,credential)) {
                        finish(a,"service_error",null,"Another client changed sign-in; its credential was retained. Start again explicitly.");
                        return;
                    }
                    saved=true;
                    finish(a,"signed_in",credential.accountId(),"Credential saved and account verified. This does not certify an external build's effective credential.");
                }
                if (previous!=null && !previous.equals(credential)) {
                    try { client.revoke(previous); }
                    catch (Exception e) { synchronized (this) {
                        Map<String,Object> warning=new LinkedHashMap<>(a.result);
                        warning.put("previousRevocation","unconfirmed"); a.result=Collections.unmodifiableMap(warning);
                    } }
                }
            } catch (Exception e) { synchronized (this) {
                if (!a.cancelled.get()) finish(a,"service_error",null,"Approval could not be verified or saved; credential cleanup was attempted.");
            } } finally { if (!saved) {
                boolean interrupted = Thread.interrupted();
                try { client.revoke(credential); }
                catch (Exception ignored) { synchronized (this) {
                    Map<String,Object> warning=new LinkedHashMap<>(a.result);
                    warning.put("credentialCleanup","unconfirmed"); a.result=Collections.unmodifiableMap(warning);
                } } finally { if (interrupted) Thread.currentThread().interrupt(); }
            } }
        } else synchronized (this) {
            if (a.cancelled.get()||closed) return;
            String event=outcome instanceof CompilerAccountClient.Denied ? "denied"
                    : outcome instanceof CompilerAccountClient.Expired ? "expired"
                    : outcome instanceof CompilerAccountClient.Cancelled ? "cancelled" : "service_error";
            finish(a,event,null,"Sign-in ended without saving a credential.");
        }
    }
    public synchronized Map<String,Object> status(String id) {
        Attempt a=attempts.get(id); return a==null ? unknown() : a.result;
    }
    public synchronized Map<String,Object> cancel(String id) {
        Attempt a=attempts.get(id); if (a==null) return unknown();
        if ("pending".equals(a.result.get("event"))) {
            a.cancelled.set(true); if (a.task!=null) a.task.cancel(true);
            finish(a,"cancelled",null,"This attempt was cancelled; existing machine sign-in was not removed.");
        }
        return a.result;
    }
    private Map<String,Object> finish(Attempt a,String event,String account,String message) {
        Map<String,Object> r=result(a,event,account); r.put("message",message);
        a.result=Collections.unmodifiableMap(r); return a.result;
    }
    private Map<String,Object> result(Attempt a,String event,String account) {
        Map<String,Object> r=new LinkedHashMap<>(); r.put("schemaVersion",1); r.put("event",event);
        r.put("attemptId",a.id); r.put("clientScope","mcp"); r.put("compilerOrigin",a.origin);
        r.put("credentialSource","shared-file"); r.put("identityStatus",account==null?"unknown":"verified");
        r.put("observedAt",Instant.now().toString());
        if (account!=null) { r.put("accountId",account); r.put("credentialSaved",true); r.put("effectiveBuildIdentity","not-observed"); }
        return r;
    }
    private Map<String,Object> unknown() {
        return Map.of("schemaVersion",1,"event","unknown","identityStatus","unknown","message","Attempt not owned by this session or lost after restart. Start a new attempt.");
    }
    @Override public synchronized void close() {
        closed=true;
        for (Attempt a:attempts.values()) if (a.result!=null && "pending".equals(a.result.get("event"))) {
            a.cancelled.set(true); if (a.task!=null) a.task.cancel(true);
            finish(a,"cancelled",null,"Client session ended.");
        }
        workers.shutdownNow();
    }
}
