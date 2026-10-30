# Start with Claude in the analyser

Open the analyser, enable Claude, then describe what you want to build.

You can start without installing Java or JBang first. The launcher downloads what it needs;
the analyser then guides you through installing and signing in to Claude Code if needed.

**Preview:** these commands download the onboarding preview shown here. It is separate from the normal release.

## 1. Open the analyser

Choose one command for your terminal. Each starts the analyser with Java 21.

**macOS / Linux (Bash), or Windows with a Bash shell:**

```bash
curl -fsSL https://sh.jbang.dev | bash -s - --java 21 https://github.com/telaminai/fluxtionauditlog-analyser/releases/download/v1.41.0-onboarding.1/fluxtion-auditlog-analyser.jar
```

**Windows PowerShell:**

```powershell
iex "& { $(iwr -useb https://ps.jbang.dev) } --java 21 https://github.com/telaminai/fluxtionauditlog-analyser/releases/download/v1.41.0-onboarding.1/fluxtion-auditlog-analyser.jar"
```

**Already have JBang, or prefer to [install it separately](https://www.jbang.dev/download/)?**

```bash
jbang --java 21 https://github.com/telaminai/fluxtionauditlog-analyser/releases/download/v1.41.0-onboarding.1/fluxtion-auditlog-analyser.jar
```

Review JBang's trust prompt if it appears. In the analyser, click **Build with Claude Code**.
This button stays on the start page when you return later.

You can reuse the same command next time. The [zero-install launcher](https://www.jbang.dev/documentation/jbang/latest/installation.html#zero-install-curlbash-and-powershell)
caches JBang under your home folder's `.jbang` directory; it does not add `jbang` to your PATH.
Permanent command setup is optional; see [JBang installation](https://www.jbang.dev/documentation/jbang/latest/installation.html).

![The welcome screen with Build with Claude Code](assets/start-with-claude.png)

## 2. Enable Claude

Check the results. If Claude Code is missing, click **How to install…** and follow the installation instructions,
then **Check again**. If it is not signed in, run the displayed `claude auth login` command in a terminal and
**Check again**. [Claude's quickstart](https://code.claude.com/docs/en/quickstart) also covers both.

**Before enabling:** Full Claude Code can use your tools and access files beyond the project folder. New setup uses
`acceptEdits`: file edits and some commands can run without asking, under your permission rules. For more prompts,
choose `default` in **Settings ▸ Assistant**; it asks before actions your rules do not already allow.

Read the consent screen, then click **Enable Claude Code**. This saves your choice for future launches;
it does not start Claude or send a message yet. Existing Full-mode settings are kept.

![Installation and sign-in checks above the consent and Enable Claude Code button](assets/start-with-claude-setup.png)

You can cancel, finish installing or signing in, and return to **Build with Claude Code**.
Claude usage counts against your own [Claude plan or API account](https://code.claude.com/docs/en/costs).
To turn it off, **Stop** any reply, then choose **Off** from **◆ Full ▾** in the assistant header.

## 3. Describe your idea and send

Type what you want to build, or copy this example:

> Help me build a temperature alert application. Ask me one question about the alert rule first.

Click **Send**. Claude connects inside the analyser and replies. Before a project is open it works in the analyser's
private, initially empty temporary folder; with your project open, it works in that project's folder.
If a trust prompt appears, check the named path and the instructions, hooks and servers it will load before approving.
It confirms Claude's working folder, not where your new project will be created.

![A sent application idea and Claude's reply in the analyser](assets/start-with-claude-reply.png)

Continue the conversation. When Claude proposes a project, click **Review…** and choose a new or empty folder.
After you approve creation, the analyser downloads the starter and adds its skills; review and open the project,
restart Claude in its folder when offered, then **Send** the prepared handoff to continue.
[Creating your first project](user-guide/projects.md#create-from-a-starter-with-the-assistant) shows the details.

**Need help?** [Installation](install.md) · [Claude setup](inapp/claude-code.md)
