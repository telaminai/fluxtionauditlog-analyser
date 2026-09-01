
## 7. A new behaviour is usually a new NODE

When a requirement adds a step — something that must happen between two things that already happen —
**add a node and declare its parents**. Do not open an existing node and add a branch to it.

The compiler derives the dispatch order from the declarations, so an inserted node reorders execution
for you. Editing an existing node's logic instead gets you the same answer while throwing away the one
thing the framework is for: the order stays whatever it was, and the new step is invisible in the graph,
in the GraphML and in the audit log.

Reserve edits to an existing node for changes to **what that node itself computes**.
