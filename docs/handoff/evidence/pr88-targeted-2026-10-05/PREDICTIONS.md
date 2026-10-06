# PR #88 targeted correction predictions

The initial import regressions were already run before this file: cancelled imports, a switch to another
project, and switching away and back all changed the hidden columns on the old implementation. These
are observations, not sealed predictions. The corrected six-test import suite is green.

The owner then selected ownership and cleanup in this PR. Before running the ownership tests:

1. Shared leases will preserve active and pending copies, in this JVM and another JVM; cleanup will
   delete the copy only after the final lease closes.
2. A legacy directory, a foreign-host marker, a symlink, the root itself and a nested directory will
   survive cleanup. Only marked direct children are eligible.
3. Two windows sharing a copy will retain independent references. Closing one will not unlock it.
4. A newly unpacked copy will remain protected until the open settles, including when another
   extraction starts. Releasing a stale preparation will make it eligible for later cleanup.
5. The original cleanup tests need marked fixtures rather than treating every directory as disposable.
6. Existing bundle provenance tests will remain green after the asynchronous import fixture changes.

No full local mutation run is planned. Only controls added, changed or directly implicated here.
