# Slidev VSCode → IntelliJ Plugin Port Plan

Port of [slidevjs/slidev `packages/vscode`](https://github.com/slidevjs/slidev/tree/main/packages/vscode) (v52.16.0) to an IntelliJ Platform plugin.

**State as of 2026-06-08:** the majority of the extension is already ported (~50 Kotlin files + 11 test files covering parser, projects, server, preview, trees, decorations, actions). This plan maps the entire VSCode feature surface to IntelliJ tasks, marks what's done, and breaks the remaining work into fine-grained tasks.

## Architecture mapping (vscode module → IntelliJ counterpart)

| VSCode (`packages/vscode`) | IntelliJ equivalent | Status |
|---|---|---|
| `@slidev/parser` (dep) | `parser/` pure-Kotlin port (SlidevParser, DataLoader, Stringifier, CodeBlocks, RangeParser) | ✅ done + tests |
| `projects.ts`, `useProjectFromDoc` | `SlidevProjectService`, `SlidevProjectState`, `SlidevWorkspaceScanner`, `IdeFileTextProvider` | ✅ done + tests |
| `configs.ts` | `SlidevSettings` + `SlidevSettingsConfigurable` + `SlidevWorkspaceState` | ✅ done |
| `useDevServer`, `useServerDetector` | `SlidevServerManager`, `ServerDetector`, `SlidevCommandLine` | ✅ done + tests |
| `views/annotations.ts` | `SlidevSlideLinePainter`, `SlidevEditorDecorations` | ✅ done |
| `views/foldings.ts` | `SlidevFoldingBuilder` (optional Markdown-plugin dep) | ✅ done |
| `views/slidesTree.ts`, `projectsTree.ts` | `SlidevSlidesPanel`, `SlidevProjectsPanel` (tool window tabs, DnD reorder) | ✅ done |
| `views/previewWebview.ts`, `src/html` | `SlidevPreviewPanel` (JCEF + JS bridge), `SlidevPreviewService` | ✅ done |
| `commands.ts` (22 commands) | 19 registered actions | 🔶 mostly done |
| `views/logger.ts` (output channel) | console for dev-server output | ⬜ gap (verify) |
| **`language-server/` (Volar: YAML schema completion/validation/hover for frontmatter)** | YAML language injection + JSON-schema binding | ⬜ **biggest gap** |
| `schema/frontmatter.json`, `headmatter.json` | vendored schema resources | ⬜ gap |
| `syntaxes/*` (TextMate grammars) | mostly N/A — IntelliJ Markdown plugin already injects fenced code blocks; frontmatter handled via injection (Phase 8) | ⬜/N/A |
| `lmTools.ts` (7 Copilot LM tools) | MCP-server tool contributions (stretch) | ⬜ optional |
| `scripts/publish.ts`, vsce | Gradle `signPlugin`/`publishPlugin` + CI | ⬜ gap |

---

## Phase 8 — Frontmatter language support (port of the Volar language server) — **the main remaining feature**

The vscode language server does exactly two things worth porting: (a) treat each slide's `---` frontmatter block as an embedded YAML document, (b) bind JSON schemas to it — `headmatter.json` for slide 0, `frontmatter.json` for the rest — giving completion, hover docs, and validation. (Its prettier service is N/A; IntelliJ's YAML formatter covers injected fragments.)

- [ ] **8.1 — Add YAML plugin dependency** (S)
  - `build.gradle.kts`: `bundledPlugin("org.jetbrains.plugins.yaml")`; new optional `depends` + `slidev-yaml.xml` include (same pattern as `slidev-markdown.xml`).
  - Accept: plugin still loads in IDEs without YAML support.
- [ ] **8.2 — YAML injection into frontmatter blocks** (M)
  - New `editor/SlidevFrontmatterInjector.kt` implementing `MultiHostInjector` (or `LanguageInjectionContributor`) over Markdown PSI; reuse `SlidevParser` slide offsets to find each `frontmatterRaw` range, mirroring `languagePlugin.ts:getEmbeddedCodes`.
  - Only inject in files belonging to a registered project (reuse the same gate as `SlidevFoldingBuilder`).
  - Edge cases from the parser tests: frontmatter on first slide vs. `---` separators inside fenced code blocks; CRLF.
  - Accept: YAML syntax highlighting + structure inside every frontmatter block; no injection inside code fences.
- [ ] **8.3 — Vendor JSON schemas** (S)
  - Copy generated `schema/frontmatter.json` + `headmatter.json` into `src/main/resources/schemas/`; document regeneration source (`scripts/schema.ts` / `ts-json-schema-generator` over `@slidev/types`) in CHANGELOG/README.
- [ ] **8.4 — Spike: JSON-schema binding on injected YAML fragments** (M, ⚠ risk item — do first within this phase)
  - Try `JsonSchemaProviderFactory` matching the injected fragment's `VirtualFileWindow`; distinguish headmatter (slide index 0) vs frontmatter, matching the language server's priority-3/priority-2 setup.
  - Fallback if the schema engine ignores injected fragments: custom `CompletionContributor` + `DocumentationProvider` + local-inspection validator driven by the vendored schemas (more work — only if spike fails).
  - Accept: typing `lay` in a frontmatter block completes `layout` with the doc text from the schema; unknown built-in `transition` values flagged.
- [ ] **8.5 — Schema-driven value completion polish** (S)
  - Verify enum completions (`BuiltinLayouts`, `BuiltinSlideTransition`) and hover docs render the `markdownDescription`.
- [ ] **8.6 — Tests** (M)
  - Platform tests: injection ranges (per-slide, fence-safe), schema resolution for slide 0 vs n, completion smoke test.

## Phase 9 — Command parity gaps (small, mechanical)

- [ ] **9.1 — `choose-entry` action** (S): quick-pick equivalent (popup `JBList` of registered entries → set active). Vscode has it in the command palette and the slides-tree empty state. Register in plugin.xml + bundle keys.
- [ ] **9.2 — Slides panel empty state** (S): "No active slides entry — Choose one" link panel invoking 9.1 (port of `viewsWelcome`).
- [ ] **9.3 — Dev-server console** (S–M): verify `ProcessHandler` output is surfaced; if not, attach a `ConsoleView` tab (or Run tool window) — port of `views/logger.ts`. Server-failed notification should link to it.
- [ ] **9.4 — `config-port` parity check** (XS): vscode prompts per-window; settings field exists — confirm a per-project port override isn't needed, document decision.
- [ ] **9.5 — `force-enabled` decision** (XS): N/A in IntelliJ (tool window is opt-in); document as intentionally dropped.

## Phase 10 — Preview hardening

- [ ] **10.1 — Compat mode parity** (S): vscode falls back when the server is an older Slidev version (`slidev:preview:compat` hides nav buttons). Verify `ServerDetector` version gate disables nav/overview actions the same way; bundle key `notification.compat` exists — confirm wiring.
- [ ] **10.2 — JCEF-unavailable fallback** (S): verify the "no JCEF" message path renders + offer "Open in browser" instead (Android Studio / some runtimes).
- [ ] **10.3 — Manual test matrix** (S): editor↔preview sync both directions, clicks, overview mode, multi-project switching, server adopted-vs-spawned. Run against a real `npm create slidev` project.

## Phase 11 — LM tools (optional / stretch)

The 7 `lmTools.ts` tools (getActiveSlide, getSlideContent, getAllSlideTitles, findSlideNoByTitle, listEntries, getPreviewPort, chooseEntry) have no direct IntelliJ API equivalent.

- [ ] **11.1 — Spike** (S): check whether the JetBrains MCP-server plugin exposes a third-party tool extension point in the target version (2025.3); if yes, the tools are thin wrappers over `SlidevProjectService` — verify the EP exists before committing.
- [ ] **11.2 — Implement 7 tools** (M, conditional on 11.1): pure delegations; reuse `getSlidesTitle`-equivalent logic already in the slides tree.
- If no EP: drop with a note; revisit when AI Assistant opens a tool API.

## Phase 12 — Release readiness

- [ ] **12.1 — plugin.xml metadata** (S): description (HTML), vendor, `since-build`/`until-build` range, change-notes from CHANGELOG.
- [ ] **12.2 — Plugin Verifier** (S): `runPluginVerifier` against 2025.3 + latest EAP; fix reported API issues.
- [ ] **12.3 — README + screenshots** (S): feature table mirroring the vscode README, dev-command docs link.
- [ ] **12.4 — CI** (M): GitHub Actions — build, test, verifier; `signPlugin`/`publishPlugin` with marketplace token on tag.
- [ ] **12.5 — Initial commit hygiene** (XS): repo has everything staged but zero commits — commit the scaffold and port as logical chunks before further work.

---

## Suggested order & effort

| Order | Work | Size |
|---|---|---|
| 1 | 12.5 commit current state | XS |
| 2 | Phase 9 (command parity: 9.1–9.5) | ~1 day |
| 3 | Phase 8 (frontmatter YAML + schemas) | ~3–5 days, 8.4 is the risk |
| 4 | Phase 10 (preview hardening + manual matrix) | ~1 day |
| 5 | Phase 12 (release) | ~1–2 days |
| 6 | Phase 11 (LM tools) | optional |

The only genuinely uncertain task is **8.4** (JSON-schema over injected YAML) — do that spike first within Phase 8, since its outcome decides whether 8.5 is free or becomes a hand-rolled completion contributor.

## Completed phases (reference)

Already ported, with tests where noted:

- **Phase 0 — Scaffolding**: Gradle (IntelliJ Platform Gradle Plugin 2.16.0, Kotlin 2.2.20, target 2025.3.5), plugin.xml, `SlidevBundle` (71 keys), icons (light/dark + marketplace).
- **Phase 1 — Parser** (`@slidev/parser` port): separators, fenced code blocks, HTML comments, YAML frontmatter, `src:` imports with `#1-3` ranges, stringifier. Unit-tested.
- **Phase 2 — Project management** (`projects.ts` port): registry, active entry, debounced re-parse, workspace scanning via include/exclude globs, unsaved-document text provider. Tested.
- **Phase 3 — Settings** (`configs.ts` port): port, annotations, preview sync, include/exclude globs, dev command template (`${args}`, `${port}` substitution). Tested.
- **Phase 4 — Dev server** (`useDevServer`/`useServerDetector` port): start/adopt/stop, HTTP probing with version + entry detection. Tested.
- **Phase 5 — Editor decorations**: slide-number annotations, frontmatter tint, virtual code-block line numbers, error messages, per-slide folding (optional Markdown plugin dep).
- **Phase 6 — Navigation**: prev/next slide in editor (Alt+Up/Down), prev/next slide + click in preview, caret↔slide mapping, slide reordering logic. Tested.
- **Phase 7 — Tool window**: Preview (JCEF wrapper page + iframe, bidirectional JS-bridge sync), Slides tree (DnD reorder, caret sync), Projects tree (add/remove/set-active/rescan). 19 actions registered.
