# C++ generation (preview)

PREVIEW: the C++ target is evolving; ask what is supported, don't assume parity. Entry 1 is a PLACEHOLDER: pin before use.

1. **Teaching example: C++ target (placeholder)** — example: https://github.com/example/fluxtion-cpp-demo/archive/0000000000000000000000000000000000000000.zip
2. **What is the C++ path?** — I've heard Fluxtion can generate C++. In plain words, what does that path take in and what does it produce? Please treat it as a preview that is still changing, answer only from documentation or code you can point to, and tell me if you can't tell from what's here.
3. **What is supported today?** — What is the currently supported subset for C++ generation: which annotations, node shapes, event types, services and DSL features work, and which don't yet? Please don't assume it matches Java. If nothing current states it, say so, tell me if you can't tell from what's here, and tell me who I should ask.
4. **What I write and what is generated** — For the C++ target, which parts does the generator produce and which do I write myself, such as node bodies or the loop that feeds events in? Show me in this project if it's here, and tell me if you can't tell from what's here.
5. **Same graph, same behaviour?** — If I generate Java and C++ from the same graph, should the dispatch order and the audit records match? What do the docs say is actually tested, and what isn't promised yet? Tell me if you can't tell from what's here.
6. **Audit logs from C++** — Can a C++ processor write an audit log that this analyser can open? Which format does it write, and what differs from the Java runtime, for example settings fixed at generation time rather than changed at runtime? Answer from the docs, and tell me if you can't tell from what's here.
7. **Before I depend on it** — I'm thinking about using the C++ target for real work. Given it's a preview, what should I check first: versions, what's tested, known gaps and who to ask? Tell me plainly what you can't confirm, and tell me if you can't tell from what's here.
