# Shared compiler sign-in (candidate)

One machine sign-in, in `~/.fluxtion/credentials`, is authoritative for the Analyser and builds.
The properties format is unchanged (`token`, `credential_id`, `account_id`, `url`).
Environment or explicit build properties can override it; an Analyser account is not proof of an external build's account.

The Compiler account dialog offers migration from its previous OS store. The user chooses which account to keep,
and the chosen credential is checked with its issuer before migration. A persistent migration marker prevents a stale
older Analyser from silently restoring an OS credential after logout. No automatic fallback to the OS credential occurs.
Migration deletes the old local copy only. The dialog explicitly states that unselected device credentials remain
active at their issuing services; use **Your devices** for the corresponding account to revoke unused devices. This
follow-up deliberately changes the wording rather than adding cross-service revocation to migration.

Login retains the observed credential until browser approval and server identity verification succeed. Saving compares
the observed credential under an exclusive cross-process lock. If another client changed it, saving refuses and the
new uncommitted credential is revoked best-effort. Logout revokes only its observed credential and deletes only if it
still matches. It never deletes a newer sign-in. Changing service explicitly confirms replacement and revocation.

The configured home prefix is canonicalised once, so relocated or symlinked homes work. For an explicit credentials-file
override, its parent is the trusted prefix. Below that boundary, readers refuse symlinks in store-owned directories
and at the credential, lock and temporary files. Writes are owner-only and require atomic replacement.
The persistent `.lock` file is not deleted: replacing its inode would split cross-process locking. Old released clients
do not honour this lock or the new no-follow policy; upgrade all clients before relying on concurrent-write protection.
The plugin's `CredentialStore.java` and Analyser's `SharedCredentialFile.java` share the same implementation (package and
class name only differ); changes must be copied and reviewed together.

Refresh reloads the shared file and queries that credential's issuer. Closing and reopening the dialog also reloads it.
A cached display is a snapshot, not continuous verification. A failed read leaves a visible error, not a new account claim.

Browser verification addresses must match the explicitly trusted identity-provider origin configured by
`fluxtion.login.verificationOrigin`. Both defaults are owner-confirmed: production uses `https://restful-nature-87.authkit.app` and staging uses
`https://meticulous-comfort-69-staging.authkit.app`. Both verification addresses must match the selected origin exactly.
The compiler and admin origins are not used as substitutes. No actual user credential is used in tests.

## Session-owned assistant tools

The owner authorised `compiler_sign_in_start`, `compiler_sign_in_status`, and `compiler_sign_in_cancel` in the
external MCP process. They are authentication operations owned by that stdio session, not audit-log session state;
they never appear on the desktop action socket or the hosted safe-analysis bridge. `CompilerAccountSafetyTest`
explicitly permits this one additional account entry point. The existing audit-action dispatch model is unchanged.

Start only after a user asks to sign in or build. Start returns an opaque attempt ID, validated browser address, user
code and expiry. Status and cancel require that ID; another session or restarted process reports unknown. A fresh
start never adopts another attempt. Four attempts may poll concurrently; terminal history is bounded to 128 entries
and thirty minutes. Evicted or expired history reports unknown. Device expiry is measured from authorization, not
from when a worker becomes free. Cancellation never logs out an existing credential. An approved but uncommitted
credential is revoked best-effort, even if cancellation interrupted verification; unconfirmed cleanup is reported.

A `signed_in` result means `/v1/me` verified the saved account. `effectiveBuildIdentity:not-observed` explicitly
keeps it separate from external Maven, environment or property overrides. No bearer or device code is returned.

The staging verification origin is pinned exactly to the operator-confirmed AuthKit host. Production remains a
fail-closed placeholder until the operator supplies its exact verification origin. Custom services require explicit
trusted-origin configuration; a reply can never choose its own trust root.

## File-system implementation and acceptance limits

Linux uses `SecureDirectoryStream` descriptor-relative operations. The macOS JDK lacks that interface, so the
mirrored Darwin adapter uses JNA `openat`, `mkdirat`, `renameat`, `unlinkat` and `flock`; ancestors are opened one at a
time with no-follow flags. Windows uses handle-relative native opens, reads, writes, rename and deletion, rejects
reparse points and alternate stream names, creates new directories and temporary files with an atomic owner-only
protected ACL, and holds an exclusive temporary-file handle until the write and rename complete. The old file-based migration store uses these same adapters
while preserving its original base64 format. Unsupported secure filesystem operations fail
closed. macOS tests include a parent-substitution race and competing JVMs. Linux and Windows native acceptance must
run before claiming those platforms verified; source review is not an execution result.

The deterministic `tools/bench/compiler-sign-in.py` acceptance withholds synthetic approval until the MCP client has
received a valid URL and user code. It then checks that a separate real Maven process verifies the same shared sign-in.
Its result explicitly distinguishes synthetic approval from a browser, real LLM or hosted generation test.

## Review follow-up decisions and acceptance

An existing shared record is authoritative even if a legacy keyring cannot be opened. Only a missing shared sign-in
asks for the explicit legacy migration choice. A headless user can instead use the documented Maven login flow;
`usage_error` and `service_error` results include `recovery:maven_login` where that route applies. This creates a
shared sign-in without silently deleting or migrating the previous OS-store credential. Storage failures name the
store, rather than blaming the compiler URL. A migration decision is still available from the dialog later.

Origin identity lowercases scheme and host and removes default ports. `localhost` and `127.0.0.1` compare as the
same local service without rewriting the stored transport hostname or doing DNS resolution. IPv6 loopback and other
127/8 addresses are not collapsed. Verification addresses still require the exact configured provider origin.
A redirect served by the trusted identity provider itself is outside this client-side origin check.

Two reviewed limitations remain explicit follow-ups: start currently serializes its local store work and up to two
HTTP calls on the attempt-manager monitor (each HTTP call has its own timeout), so status/cancel can wait behind
start; this release does not promise an immediate cancellation response during another start. The account dialog
re-verifies on Refresh or reopening, not by watching external file changes. These are deliberate scope decisions,
not claims of automatic re-verification or a bounded end-to-end start time.

Windows runtime acceptance must include:

- A fresh owner-only `.fluxtion` created relative to a parent handle opened with `READ_ATTRIBUTES`, on NTFS.
- A junction-relocated profile and a credentials override below a symlinked ancestor; store-owned junctions must
  still be refused, including substitution during read, write and delete.
- Replacement while a current reader and an old `FileInputStream` reader hold the target open. If Windows refuses
  rename, retain the old credential, revoke the unsaved approval and report the failed save without secret output.
- Atomic owner-only ACL creation, inherited broad parent ACLs, alternate streams, persistent locks, two-process CAS,
  stale logout and busy-lock timeout. Confirm the original sign-in remains usable after every failed operation.

Linux acceptance must run the real `SecureDirectoryStream` path, relocated-home checks and subprocess lock timeout.
Neither platform is accepted by macOS tests or a source/ABI review. Production origin configuration, these native
acceptance runs and the account-dialog display suite remain release gates.
