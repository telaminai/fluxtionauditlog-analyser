# Source navigation

Jump from a log line straight to the code that produced it.

## Source roots & Maven repos

Open **Settings ▸ Source roots** and add the source directories for your processor and its node classes.
The analyser also searches your local **Maven repositories** (default `~/.m2/repository`) for
`*-sources.jar` when a class isn't under a source root — so third-party node sources resolve too.

A root or repo that can't be found shows **red** in Settings.

## Spring design and file glances

**File ▸ Open design…** selects the session's Spring XML. Add its directory to source roots first;
opening a project alone grants no file access. **Source ▸ Design** shows highlighted XML, a bean/config
index, **Follow design**, **Show node** and **Show records**. The latter opens the first record logging the
selected bean's name. Record and topology context menus offer **Show declaration**. These links are
matched by name and always marked **relationship unverified**.

An AI client uses `open {design: "src/main/resources/design.xml"}` for the session file and `source` for
a glance. Accepted shapes are `{file}`, `{file,line}`, `{line}`, `{bean}`, `{file,bean}`, `{fqn}` and
`{fqn,method}`. Mixed selectors are refused. A glance re-reads disk and joins the Back history without
changing the session design. This verb reads XML/Java under authorised local roots; it does not search
Maven jars. Paths may be absolute or project/root-relative. Ambiguous files or bean ids are refused.

Follow works with no log open. During an incomplete XML edit it keeps the last good render and shows the
parse error; an initially malformed file is visible as text with no bean index. Each parsed revision has a
SHA-256 identity. `source:design`, `source:design:bean:<id>` and `source:design:line:<n>` spotlights always
refer to the **session** design, even after a glance at another file. An edited bean's caption is qualified;
a removed/duplicate bean or an edited line anchor goes out. Cross-tab targets are shown in sequence.

The `source {bean}` echo's `records` is a preview count over `recordsScanned` records; `recordsExact` says
whether that covers the whole log. It never proves that this XML produced those records. File reads are
limited to 2 MiB, UTF-8. The analyser never evaluates Spring or writes the source files.

See [Spring authoring](../spring-authoring.md#view-design-and-producer-findings) for diagnostic intake and
the distinction between XML input freshness and a relationship to a run.

## Event processor

The **Source** tab renders the selected `EventProcessor`. Selecting a record scrolls the processor to
the method that dispatches that record (its callback), so you land on the code that ran.

## Click-through

- **Click a node line** in the record detail to open that node's class at the relevant method.
- **Ctrl/⌘-click** an identifier in the source to navigate: a node field's `receiver.method()` opens
  that node's class at the method; a field opens its type; a Type opens its source.
- **◀ Back** (Alt+Left) returns to the previous source.

Navigation resolves through the processor's field declarations, so it works from any file — the
processor or a node class.

![Source beside the graph: the generated processor above, the node class it dispatches into below](../assets/source-navigation.png)
