# Slidev for IntelliJ-based IDEs

<!-- Plugin description -->
Integrated support for [Slidev](https://sli.dev) presentations in IntelliJ-based IDEs —
a port of the official [Slidev VS Code extension](https://github.com/slidevjs/slidev/tree/main/packages/vscode).
<!-- Plugin description end -->

## Features

| Feature | Details |
|---|---|
| 🗂 **Project detection** | Scans the workspace for `slides.md` entries (configurable globs); multiple slide decks per project, with an active-entry switcher |
| ▶️ **Dev server** | Start/stop from the IDE, or adopt a server you started in a terminal; automatic port allocation; output in the Run tool window |
| 🖥 **Embedded preview** | JCEF-based side-by-side preview with bidirectional editor ↔ preview navigation sync, click stepping, and slide/overview modes; follows the IDE light/dark theme |
| 📰 **Split editor** | Deck entry files open with an editor/preview split (like the Markdown preview), embedding the same live preview; layout is remembered per file |
| 🧭 **Navigation** | <kbd>Alt</kbd>+<kbd>↑</kbd>/<kbd>↓</kbd> jumps between slides in the editor; clicking a slide in the overview jumps to its source |
| 🌲 **Slides tree** | Slide outline with titles, caret sync, and drag-and-drop slide reordering |
| ✏️ **Editor decorations** | Slide-number annotations on dividers, frontmatter tint, virtual line numbers in fenced code blocks, per-slide folding |
| 📑 **Frontmatter intelligence** | Schema-driven completion, quick documentation, and validation for headmatter and per-slide frontmatter (layouts, transitions, …) |
| 🚀 **New Project wizard** | *File → New → Project → Slidev* scaffolds the official starter template (demo deck, components, deploy configs) with your package manager of choice and an optional dependency install |

## Installation (alpha)

The plugin is in **alpha** — the feature set is complete, but it hasn't seen broad
real-world use yet. Two ways to install:

- **From a GitHub release:** download the `.zip` from the
  [latest release](../../releases/latest), then in the IDE go to
  *Settings | Plugins | ⚙ | Install Plugin from Disk…* and pick the zip.
- **From the Marketplace alpha channel** (once listed): add
  `https://plugins.jetbrains.com/plugins/alpha/list` as a
  [custom plugin repository](https://www.jetbrains.com/help/idea/managing-plugins.html#repos)
  under *Settings | Plugins | ⚙ | Manage Plugin Repositories…*, then install **Slidev**
  from the Marketplace tab.

Found a bug? Please [open an issue](../../issues).

## Getting started

1. Open a project containing a Slidev deck (e.g. created with `npm create slidev@latest`),
   or create one via **File → New → Project → Slidev**.
2. Open the **Slidev** tool window (right sidebar). The **Projects** tab lists detected entries;
   the first one is activated automatically.
3. In the **Preview** tab, hit **Start Server**. The plugin runs the configured dev command
   (default: `npm exec -c 'slidev ${args}'`) and embeds the result. If a Slidev server is
   already running on the configured port, it is adopted instead of spawning a new one.

## Settings

`Settings | Tools | Slidev` (stored per project in `.idea/slidev.xml`):

| Setting | Default | Description |
|---|---|---|
| Port | `3030` | Preferred dev-server port; additional entries get the next free ports |
| Dev command | `npm exec -c 'slidev ${args}'` | Command template; `${args}` and `${port}` are substituted |
| Include globs | `**/slides.md` | Where to look for slide entries |
| Exclude glob | `**/node_modules/**` | Paths to skip while scanning |
| Annotations | on | Slide-number annotations and frontmatter tint in the editor |
| Code-block line numbers | on | Virtual line numbers inside fenced code blocks |
| Preview sync | on | Bidirectional editor ↔ preview navigation sync (also toggleable from the preview toolbar) |

## Requirements

- IntelliJ-based IDE 2025.3+ (build 253+)
- Node.js with Slidev available in the project (`@slidev/cli`)
- JCEF-capable IDE runtime for the embedded preview (otherwise an *Open in Browser* fallback is offered)
- The bundled Markdown plugin (optional — enables per-slide folding; everything else works without it)

## Development

```bash
./gradlew runIde        # launch a sandbox IDE with the plugin
./gradlew test          # unit + platform tests
./gradlew verifyPlugin  # Plugin Verifier against the recommended IDE set
./gradlew buildPlugin   # distributable zip under build/distributions
```

See [`docs/manual-test-matrix.md`](docs/manual-test-matrix.md) for the manual QA checklist and
[`plan.md`](plan.md) for the port plan and feature-parity mapping against the VS Code extension.

## Credits

Built on the [IntelliJ Platform Plugin Template][template]. Slidev is created by
[Anthony Fu](https://github.com/antfu) and the [Slidev team](https://github.com/slidevjs).

[template]: https://github.com/JetBrains/intellij-platform-plugin-template
