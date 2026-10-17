# Mongoose plugins

Plugins track. Entry 1 is the reference repository, pinned to the commit read on 2026-10-17: confirm the pin before use.

1. **Reference: fluxtion-server-plugins** — example: https://github.com/gregv12/fluxtion-server-plugins/archive/9fce03a1229b1b80dd7ef7abd4353b5c77f3a13d.zip
2. **What is a plugin?** — What is a Mongoose plugin, and how is it different from a node in my Fluxtion graph? Explain the three kinds the Mongoose docs describe, event sources, sinks and services, and show me one example of each in this repository. Tell me if you can't tell from what's here.
3. **A tour of the scheduler plugin** — Look at the serverplugin-scheduler module in this repository. What does it do, what does it register with the server, and how is it configured? Base the answer on its code and comments, and tell me if you can't tell from what's here.
4. **How a plugin reaches my processor** — How does a service such as the scheduler reach my processor or other services: @ServiceRegistered, a registry, a name? Show me where that happens in this repository, and tell me if you can't tell from what's here.
5. **Threads: plugin versus processor** — The scheduler plugin runs work on its own threads, yet the nodes in a Fluxtion graph must stay single-threaded. Why is that allowed here, and how does work get back onto a processor's thread safely? Check against the code and the Mongoose docs, and tell me if you can't tell from what's here.
6. **Which server does this target?** — Which server and Fluxtion runtime versions and packages is this repository built against? Do they match the Mongoose release I'd use today? Read the build files rather than guessing, and tell me plainly if you can't tell from what's here.
7. **Adding a plugin to my server** — How would I add one of these plugins to my own server configuration? Explain the steps for me to follow myself, from the plugin's code and the Mongoose docs; don't start or change any server. Tell me if you can't tell from what's here.
8. **The scheduler's outcome log** — The scheduler says it writes an outcome log in the audit-record layout. Could I open that in this analyser, and what would its records mean? Check against the plugin's code and the analyser's format specification, and tell me if you can't tell from what's here.
9. **Writing a plugin of my own** — I want to write a small plugin of my own. Which kind should it be, which interfaces and lifecycle methods matter, and how do I test it without a server? Answer from the Mongoose docs and this repository, and tell me if you can't tell from what's here.
