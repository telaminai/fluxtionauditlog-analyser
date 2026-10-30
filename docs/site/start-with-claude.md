# Build your first application with Claude

Launch the analyser, describe a small application, and work with Claude Code in the same window where you inspect
the results. The analyser helps you create the project and supplies its authoring skills; Claude uses the project's
instructions to edit, build and run it.

!!! info "Welcome-screen preview"
    The **Build with Claude Code** welcome button shown here is an unreleased prototype. The JBang command below
    runs the published release. Until this welcome screen is released, use the **Set up AI…** route in step 2.

## 1. Launch the analyser

You need [JBang](https://www.jbang.dev/download/) and a desktop session. The analyser requires Java 21 or later;
JBang can obtain the required Java runtime. For installation help, see [Install & run](install.md).

```bash
jbang analyser@telaminai/fluxtionauditlog-analyser
```

Review JBang's download-trust prompt when it appears. If you have run an older release before, use
`jbang --fresh analyser@telaminai/fluxtionauditlog-analyser` to refresh its cached download.

![Prototype welcome screen offering Build with Claude Code, Explore a demo, and Connect another AI client](assets/start-with-claude.png)

## 2. Set up Claude in the analyser

Have the **Claude Code CLI installed and signed in**. The analyser uses your existing Claude Code login. If you
have not set that up yet, follow [Claude Code's quickstart](https://code.claude.com/docs/en/quickstart), then return
here. You can explore the analyser's demo while doing that.

Choose **Build with Claude Code**. The analyser checks the installation and sign-in, and selects **Full Claude Code**
for building. Review its permission mode and confirm **Turn on full Claude Code**. Full mode can edit files and run
commands under your Claude Code permission rules, and loads your skills, hooks and MCP servers. Setup alone sends
no question and starts no Claude session.

**Using the published welcome screen, or already configured?** Open **Set up AI…**, choose **Claude inside the analyser**,
then **Full Claude Code**, complete the checks and confirmation, and finish setup. On the start page, choose
**Build a new application with me**.

The built-in session connects to this analyser automatically. It needs neither `--rest` nor registration in an
external AI client. [Claude Code in the analyser](inapp/claude-code.md) explains the connection and permission modes.

## 3. Describe one small application

In the new welcome flow, choose **Describe your application** after setup. Give Claude a small task with a result
you can check. For example:

> Build a Spring application that reads temperature events. Emit an alert when the temperature crosses from
> 30 or below to above 30. For readings 28, 31, 33, 29, 32, I expect two alerts: at 31 and 32. Start from a suitable
> template and show me those alerts in the analyser.

Press **Start building** to prepare the request in the assistant. Review the draft, then press **Send**.
This starts Claude; review the folder-trust prompt when it appears. With no project open yet, the analyser uses its
own empty working folder. Your question and the information Claude reads go to its model provider.

## 4. Review the project Claude proposes

When Claude proposes a starter, choose **Review…**, select a new or empty folder, and accept the starter you want.
Only **Apply chosen** downloads the template and installs its bundled skills. Review the project settings before
opening it. The download itself does not build or run the application.

The analyser then offers to restart Claude in the new project folder. Confirm that folder, review the handoff draft,
and press **Send**. Claude can now read the project's `CLAUDE.md`, `PROJECT.md` and authoring skills. The
[starter walkthrough](user-guide/projects.md#create-from-a-starter-with-the-assistant) describes each review.

## 5. Build, run and inspect the evidence

Follow the starter's setup instructions with Claude. A template that needs compiler access says so in its review;
configure that before building ([compiler account setup](getting-started.md#connect-a-compiler-account)). Claude's
login and compiler access are separate. Keep credentials out of the conversation and project files.

Ask Claude to run the example inputs and open the resulting audit evidence in the analyser. For the temperature
example, inspect the records for the two expected alerts and the readings that should not emit one. The first useful
result is a running example whose behaviour you can point to on screen.

Continue with [Check your criteria against the evidence](build/validate-intent.md). If you already have code, use
[Set up a project with Claude](build/set-up-with-claude.md).

**Just exploring?** Choose **Explore a demo** on the new welcome screen, or **Explore DEMO ▸ Guided tour** on the
published start page. It needs no Claude login or compiler access. **Prefer another AI client?** Use
[Connect your client](connect-an-llm.md).
