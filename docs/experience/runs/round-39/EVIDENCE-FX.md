
## Evidence

**Do not build a log format of your own.** Enable the framework's audit log and write it to the
output file **exactly as it comes** — every record, unmodified, in the order the framework emits it.
Do not summarise it, reformat it, filter it or post-process it. The framework already records which
node ran, in what order, under which event; that is the evidence, and reproducing it by hand would be
building a log format of your own.
