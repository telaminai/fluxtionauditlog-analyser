# Shared compiler sign-in (candidate)

One machine sign-in, in `~/.fluxtion/credentials`, is authoritative for the Analyser and builds.
The properties format is unchanged (`token`, `credential_id`, `account_id`, `url`).
Environment or explicit build properties can override it; an Analyser account is not proof of an external build's account.

The Compiler account dialog offers migration from its previous OS store. The user chooses which account to keep,
and the chosen credential is checked with its issuer before migration. A persistent migration marker prevents a stale
older Analyser from silently restoring an OS credential after logout. No automatic fallback to the OS credential occurs.
Migration removes the old local copy; it does not promise to revoke discarded devices at the server. Use the account's
devices list to revoke any unused device.

Login retains the observed credential until browser approval and server identity verification succeed. Saving compares
the observed credential under an exclusive cross-process lock. If another client changed it, saving refuses and the
new uncommitted credential is revoked best-effort. Logout revokes only its observed credential and deletes only if it
still matches. It never deletes a newer sign-in. Changing service explicitly confirms replacement and revocation.

New readers refuse symlinks at the file and existing parent paths. Writes are owner-only and require atomic replacement.
The persistent `.lock` file is not deleted: replacing its inode would split cross-process locking. Old released clients
do not honour this lock or the new no-follow policy; upgrade all clients before relying on concurrent-write protection.
The plugin's `CredentialStore.java` and Analyser's `SharedCredentialFile.java` share the same implementation (package and
class name only differ); changes must be copied and reviewed together.

Refresh reloads the shared file and queries that credential's issuer. Closing and reopening the dialog also reloads it.
A cached display is a snapshot, not continuous verification. A failed read leaves a visible error, not a new account claim.

Browser verification addresses must match the explicitly trusted identity-provider origin configured by
`fluxtion.login.verificationOrigin`. The staging default is operator-confirmed; the production default remains unconfigured and is a release gate.
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
