# Managed policy (planned)

**Every switch is per user, on each machine.** The profile cards and *Settings ▸ Assistant* write the user's own
`~/.fluxtion-analyser/config`: *Allow the analyser to start Claude Code*, *Full Claude Code* and its mode, *External
MCP/REST access*, *Allow assistant file exchange* and *Allow the assistant to create projects*. None of them can come
from a project or a bundle, so a repository or a received bundle cannot turn one on. One can come from a shared
settings file: its *Assistant* category carries *External MCP/REST access*, and importing that category applies it
([Sharing setups](../user-guide/sharing-setups.md)). And
equally **there is no central lock yet**: any user can turn Full Claude Code on for themselves, and nothing an
administrator sets today prevents it. A **managed policy** that can (a machine-wide setting the cards read, with the
card showing *Disabled by your administrator*) is planned and not built. Until then, decide whether
Full Claude Code is acceptable on a machine the way you would decide about the person's own terminal Claude Code: it is
that, run inside the analyser.


The switches each user holds, and what each profile does, are on [Assistant profiles](profiles.md).
