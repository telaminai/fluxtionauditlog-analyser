# Predictions before this pass changes production code

Pinned baseline: 00b39ce8. This reviewer previously examined 90746e83 and built an
unmerged integration prototype. These are informed predictions, not a claim of blindness.

- Explicit newTab with an existing saved/open name will duplicate or overwrite it.
- A named graph action targeting a closed chart will replace its metadata.
- Importing Points/closed over Line/open will retain the old view in the saved definition.
- Existing generated-name and rename guards will retain an unrelated closed chart.
- SettingsShare production is fixed, but reverting its withExternal call will not fail
  the old direct-wither test; a real ProjectProfile round trip will fail.
- The actual Project buttons select report identities, and reopen charts without rebuilding
  an already open one. Its adapter still needs a committed display regression.
- A stale Delete confirmation can delete a replacement tab.
- Restoring zero/all-closed definitions creates or retains a placeholder; last Close is a no-op.

The separate bd5cfe40 branch is neither merged nor used as the subject. No menu change is
part of this review; the earlier screenshot/menu work remains in its separate worktree.
