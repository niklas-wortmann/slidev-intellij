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

- [x] **8.4 — Spike: injection + JSON-schema binding** (done first, ⚠ risk item — **spike failed, fallback implemented**)
  - **Spike outcome:** in the 2025.3 Markdown plugin only `MarkdownFrontMatterHeader` (top-of-file only, setting-gated) and `MarkdownCodeFence` implement `PsiLanguageInjectionHost`. Mid-document `---` frontmatter blocks parse as plain paragraphs/setext headings, which are not injection hosts — so neither `MultiHostInjector` nor `JsonSchemaProviderFactory`-over-injected-YAML can work for per-slide frontmatter.
  - **Implemented the anticipated fallback instead:** schema-driven `SlidevFrontmatterCompletionContributor` + `SlidevFrontmatterDocumentationProvider` + `SlidevFrontmatterAnnotator` (+ `SlidevFrontmatterTypedHandler` for auto-popup), all driven by the vendored schemas over `SlidevParser` block ranges (`SlidevFrontmatterBlocks`), registered in `slidev-markdown.xml`.
- [x] **8.1 — YAML plugin dependency** — **not needed**: the fallback path never injects YAML, so no `org.jetbrains.plugins.yaml` dependency. Slidev's alternative ```` ```yaml ```` frontmatter style already gets YAML injection for free from the Markdown plugin's code-fence injection.
- [x] **8.2 — YAML injection into frontmatter blocks** — superseded by the 8.4 fallback (injection infeasible, see above). Block location logic (per-slide, fence-safe, CRLF) lives in `SlidevFrontmatterBlocks` and is unit-tested.
- [x] **8.3 — Vendor JSON schemas** (S): `src/main/resources/schemas/frontmatter.json` + `headmatter.json` vendored from `packages/vscode/schema/` (regenerated upstream by `scripts/schema.ts` / ts-json-schema-generator over `@slidev/types` — noted in `SlidevSchemas` kdoc). Parsed with the bundled SnakeYAML (JSON ⊂ YAML), no new dependency.
- [x] **8.5 — Schema-driven value completion polish** (S): enum completions for `BuiltinLayouts`/`BuiltinSlideTransition` after `key:`; hover docs render the `markdownDescription` (code/lists/links). Note: the schemas give `layout`/`transition` a plain-string branch, so custom values are *not* flagged — matching the real VS Code behavior (the original acceptance criterion overreached).
- [x] **8.6 — Tests** (M): `SlidevSchemaTest` (schema resolution, enum/type matching), `SlidevFrontmatterBlocksTest` (block ranges per-slide, fence-safe, CRLF, key/value contexts), `SlidevFrontmatterSupportTest` (platform: key/enum completion, headmatter vs frontmatter schema, non-Slidev files ignored, annotator type/YAML errors).

## Phase 9 — Command parity gaps (small, mechanical)

- [x] **9.1 — `choose-entry` action** (S): quick-pick equivalent (popup `JBList` of registered entries → set active). Vscode has it in the command palette and the slides-tree empty state. Register in plugin.xml + bundle keys. *(Done: `SlidevChooseEntryAction` + `Slidev.ChooseEntry` registration; popup lists entries relative to the project root.)*
- [x] **9.2 — Slides panel empty state** (S): "No active slides entry — Choose one" link panel invoking 9.1 (port of `viewsWelcome`). *(Done: `emptyText` link line in `SlidevSlidesPanel` invoking the chooser popup.)*
- [x] **9.3 — Dev-server console** (S–M): verify `ProcessHandler` output is surfaced; if not, attach a `ConsoleView` tab (or Run tool window) — port of `views/logger.ts`. Server-failed notification should link to it. *(Verified: `SlidevServerManager` already runs the handler through `RunContentExecutor`, so PTY output lands in a Run tool window console. Added a "Show Output" action to the server-failed notification.)*
- [x] **9.4 — `config-port` parity check** (XS): **Decision:** no per-project port override needed. VS Code's `config-port` is per-window because one window can hold many workspaces; in IntelliJ, `SlidevSettings` is already project-level (`Service.Level.PROJECT` + `nonDefaultProject` configurable), which is the same granularity. Multiple entries in one IDE project get auto-allocated ports (`ServerDetector.allocPort`).
- [x] **9.5 — `force-enabled` decision** (XS): **Decision:** intentionally dropped. VS Code needs `force-enabled` because the extension activates lazily on globs; the IntelliJ tool window is always available and project scanning is cheap, so there is nothing to force.

## Phase 10 — Preview hardening

- [x] **10.1 — Compat mode parity** (S): vscode falls back when the server is an older Slidev version (`slidev:preview:compat` hides nav buttons). *(Done: checked upstream `package.json` when-clauses — compat hides the 4 nav buttons + overview toggle, keeps refresh/open-in-browser. Added `hiddenInCompatMode` gate to `SlidevPreviewAction` (set on the 4 nav actions) and a compat check to `SlidevTogglePreviewModeAction`. `notification.compat.mode` was already wired in `SlidevServerManager` on both the adopt and spawn paths.)*
- [x] **10.2 — JCEF-unavailable fallback** (S): *(Verified in code: `SlidevPreviewPanel` only adds the browser card when `JBCefApp.isSupported()`; otherwise the `preview.unsupported` card with an "Open in Browser instead" `ActionLink` is shown. The card is only reachable when the server is running, so `openInBrowser()` always has a port. Bundle keys present.)*
- [ ] **10.3 — Manual test matrix** (S): editor↔preview sync both directions, clicks, overview mode, multi-project switching, server adopted-vs-spawned. Run against a real `npm create slidev` project. *(Matrix authored in `docs/manual-test-matrix.md` — 30 cases across 7 areas; execution requires a human in the sandbox IDE, still pending.)*

## Phase 11 — LM tools (optional / stretch)

The 7 `lmTools.ts` tools (getActiveSlide, getSlideContent, getAllSlideTitles, findSlideNoByTitle, listEntries, getPreviewPort, chooseEntry) have no direct IntelliJ API equivalent.

- [x] **11.1 — Spike** (S): **EP exists.** The MCP server plugin (`com.intellij.mcpServer`, bundled since 2025.2) exposes dynamic EPs `mcpToolset` (reflection over `@McpTool`/`@McpDescription`-annotated suspend methods) and `mcpToolsProvider`; project access via `coroutineContext.project`. Verified against the 2025.3 `mcpserver.jar` via javap.
- [x] **11.2 — Implement 7 tools** (M): `SlidevMcpToolset` ports all 7 `lmTools.ts` tools (`slidev_get_active_slide`, `slidev_get_slide_content`, `slidev_get_all_slide_titles`, `slidev_find_slide_no_by_title`, `slidev_list_entries`, `slidev_get_preview_port`, `slidev_choose_entry`) as pure delegations to `SlidevProjectService`, including the upstream entry-resolution semantics (`$ACTIVE_SLIDE_ENTRY`/blank → active, exact → unique substring match) and `McpExpectedError` for the upstream error texts. Registered via optional `com.intellij.mcpServer` dependency (`slidev-mcp.xml`) + `bundledPlugin` on the compile classpath.

## Phase 12 — Release readiness

- [x] **12.1 — plugin.xml metadata** (S): description (HTML), vendor, `since-build`/`until-build` range, change-notes from CHANGELOG. *(Done: expanded description to the full feature set; `pluginConfiguration` block in Gradle sets `sinceBuild = "253"`, open-ended until-build, and change-notes rendered from `CHANGELOG.md` [Unreleased] — verified via `patchPluginXml`. Signing/publishing wired to env vars for CI.)*
- [x] **12.2 — Plugin Verifier** (S): `verifyPlugin` against the recommended set (IU-253.33813.25, IU-261.25134.95, IU-262.7132.23) — **Compatible** on all three. Fixed along the way: plugin ID renamed `dev.slidev.intellij` → `dev.slidev.plugin` (Marketplace forbids "intellij" in IDs), internal-API usage `serviceAsync` in `SlidevStartupActivity` replaced with `getService`, change-notes rendered eagerly to satisfy the Gradle configuration cache. Remaining report items are non-blocking experimental-API warnings (`ToolWindowFactory.getIcon/getAnchor/manage`) and 4 deprecation notes.
- [ ] **12.3 — README + screenshots** (S): feature table mirroring the vscode README, dev-command docs link. *(README rewritten: feature table, getting-started, settings table, requirements, dev commands. Screenshots still pending — take them during the 10.3 manual sandbox session.)*
- [x] **12.4 — CI** (M): GitHub Actions — `build.yml` (buildPlugin + check + verifyPlugin with IDE cache, artifacts on failure) and `release.yml` (tests + GitHub release with the zip attached on `v*` tags, pre-release for suffixed tags; `publishPlugin` runs only once the `PUBLISH_TOKEN` secret exists — signing/publishing read `PUBLISH_TOKEN`/`CERTIFICATE_CHAIN`/`PRIVATE_KEY`/`PRIVATE_KEY_PASSWORD` via the Gradle `signing`/`publishing` blocks).
- [x] **12.5 — Initial commit hygiene** (XS): repo has everything staged but zero commits — commit the scaffold and port as logical chunks before further work. *(Done: 7 logical commits on `main`.)*
- [x] **12.6 — Alpha release mechanics** (S): version `0.1.0-alpha.1` (gradle.properties), Marketplace channel derived from the version's pre-release suffix (`-alpha.1` → `alpha` channel, none → `default`), CHANGELOG patched, README install-from-disk + alpha-channel instructions. Remaining **human steps**: run the 10.3 manual matrix in the sandbox; optionally create the Marketplace listing (the *first* upload must be manual via the web UI, channel `alpha`), then create a token + `release` environment secrets so future tags publish automatically; screenshots (12.3).

## Phase 13 — Component index (foundation for component/comark language support)

Slides mix Markdown, Vue components ([docs](https://sli.dev/guide/component)), inline HTML, and optionally Comark syntax ([docs](https://sli.dev/features/comark)). All completion/highlighting features below depend on one shared component index that mirrors `unplugin-vue-components` resolution: built-ins + local `components/` dir + theme/addon packages.

- [x] **13.1 — Vendor built-in component metadata** (S): `resources/components/builtin-components.json` — all 20 built-ins with props (type/default/required/description) + docs URLs, plus the 5 global directives (`v-click`, `v-after`, `v-motion`, `v-mark`, `v-drag`). Hand-vendored from `packages/client/builtin` + `docs/builtin/components.md` (noted in `SlidevBuiltinComponents` kdoc); loaded with the bundled SnakeYAML like the frontmatter schemas.
- [x] **13.2 — Local components scanner** (S–M): `VueComponentScanner` in `parser/` — filename → PascalCase tag (mirroring `unplugin-vue-components`), best-effort `defineProps` extraction over raw text covering both the type-literal form (incl. `withDefaults`, optionality, nested generics/functions) and the object form (incl. `required: true`), brace/string/comment-aware. Pure Kotlin, unit-tested.
- [x] **13.3 — Theme/addon resolution** (S): `SlidevPackageNames` maps headmatter `theme:`/`addons:` to package candidates mirroring `resolver.ts` (`@slidev/theme-*` → `slidev-theme-*` / `slidev-addon-*` fallbacks, scoped/prefixed pass-through, local paths, `theme: none`); the index probes `node_modules` upward from the entry root and scans each package's `components/`.
- [x] **13.4 — Index service** (S): `SlidevComponentIndex` (project service) — per-entry cache keyed by (theme, addons, VFS stamp); `BulkFileListener` bumps the stamp on `.vue` changes and `package.json` create/delete; shadowing order builtin < theme < addon < local. EDT callers get stale-while-revalidate (last snapshot or built-ins, recompute in background) — `node_modules` is never scanned on the EDT. Tested (`SlidevComponentIndexTest`: precedence, node_modules resolution, invalidation; 28 new tests across the four classes).

## Phase 14 — Component & HTML completion/docs (text-based, all IDEs)

The Vue plugin only exists in paid IDEs, so the baseline is text-offset-based over `SlidevParser` ranges — the same house style as Phase 8's fallback. Works everywhere, including Community.

- [ ] **14.1 — Tag completion** (M): `<` in slide content → component names from the index, with self-closing-tag insertion. First verify what the bundled Markdown plugin already provides for plain HTML tags and only fill gaps.
- [ ] **14.2 — Attribute completion** (M): inside an open component tag, offer props from the index in plain / `:`-bound / `@event` variants, plus the global Slidev directives (`v-click`, `v-after`, `v-motion`, `v-mark`, `v-drag`).
- [ ] **14.3 — Hover docs** (S): component names/props via `platform.backend.documentation.targetProvider` (v2 API — same lesson as frontmatter: the legacy `lang.documentationProvider` gets shadowed).
- [ ] **14.4 — Goto declaration** (S): local/theme/addon component tag → its `.vue` file; built-ins → external docs URL.
- [ ] **14.5 — Typed handler** (XS): auto-popup on `<` and space-inside-tag (extend the `SlidevFrontmatterTypedHandler` pattern).
- [ ] **14.6 — Tests** (M): index resolution (built-in/local/theme precedence), tag + attribute completion, docs, navigation; non-Slidev markdown unaffected.

## Phase 15 — Highlighting for component tags & Vue attributes

- [ ] **15.1 — Annotator-based semantic coloring** (M): custom `TextAttributesKey`s + color settings page — known component names, Vue directive prefixes (`:`/`@`/`v-`), and a weak-warning for unknown components (typo-catcher). Layers *on top of* the Markdown plugin's generic HTML-tag coloring, doesn't replace it. Reuses debounced parse results (`SlidevFoldingBuilder` pattern).
- [ ] **15.2 — Spike: HTML injection into HTML blocks** (S, ⚠ optional): language injection into proper HTML *blocks* (not inline tags) for full HTML completion in Community. Risky given the 8.4 outcome — inline HTML comes as scattered `HTML_TAG` tokens in markdown PSI; 15.1 is the committed fallback.

## Phase 16 — Comark support (gated on `comark: true` / deprecated `mdc: true` headmatter)

Greenfield: no IntelliJ support for Comark exists anywhere. Custom scanner + annotator + completion, activated per-file from the headmatter flag (already present in the vendored schema). Grammar reference: [comark.dev/syntax/markdown](https://comark.dev/syntax/markdown) / `@comark/markdown-it`.

- [ ] **16.1 — Comark scanner** (M–L): pure-Kotlin scanner in `parser/` covering the syntax subset: `[text]{...}` spans, `:name{...}` inline components, `::name … ::` block components (with nesting), `{key=value .class #id key="value"}` attribute blocks after images/links/elements. Fixture tests against the comark spec. **Must not collide with Slidev's named-slot syntax (`::right::`)** — distinguish slot markers from block components.
- [ ] **16.2 — Annotator highlighting** (S–M): color settings entries for directive markers, component names, attribute keys/values, `.class`/`#id` shorthands.
- [ ] **16.3 — Completion** (M): component names after `:` / `::` (PascalCase→kebab-case mapping from the Phase 13 index), props inside `{}` including the shorthands; stretch: layout slot names after `::` from the active layout.
- [ ] **16.4 — Editing ergonomics** (S): auto-close `{}`, complete the `::` block terminator, brace matching.
- [ ] **16.5 — Activation plumbing** (XS): per-file headmatter check via existing `SlidevParser` results; everything no-ops when comark is off.
- [ ] **16.6 — Tests** (M): scanner fixtures (incl. slot-syntax non-collision), gating on/off, completion, annotator.

## Phase 17 — Deep Vue integration (optional, paid-IDE only, stretch)

- [ ] **17.1 — Spike: Polysymbols contribution** (M, ⚠): optional dependency on `JavaScript` + `org.jetbrains.plugins.vue` in a separate `slidev-vue.xml` (same pattern as `slidev-mcp.xml`). Contribute the Phase 13 index through the Polysymbols API (Web Symbols, renamed 2025.2+) so the platform's HTML/Vue machinery does type-aware prop completion, rename, and find-usages. The 13–16 baseline must stand alone without this.

### Phase 13–17 risks & notes

- **Markdown inline-HTML fragmentation:** inline tags are scattered `HTML_TAG` tokens in markdown PSI — text-offset approach (house style) avoids fighting it.
- **Spec drift:** Comark was recently renamed from MDC; vendor a fixed grammar subset with a refresh script, like the schemas.
- **Preview:** comark syntax renders as literal text in the IDE markdown preview (`SlidevPreviewVueAttributesExtension` only strips Vue attrs) — separate backlog item.
- **Dependency order:** 13 → 14/15 (parallelizable) → 16; 17 independent after 13.

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
| 7 | Phase 13 (component index) | ~2–3 days |
| 8 | Phase 14 + 15 (component completion/docs + highlighting, parallelizable) | ~3–5 days |
| 9 | Phase 16 (comark) | ~3–5 days, 16.1 scanner is the bulk |
| 10 | Phase 17 (Polysymbols/Vue) | ~1 week+, spike-gated, optional |

The only genuinely uncertain task is **8.4** (JSON-schema over injected YAML) — do that spike first within Phase 8, since its outcome decides whether 8.5 is free or becomes a hand-rolled completion contributor. For the language-support phases, the equivalent risk items are **15.2** (HTML injection spike — fallback already committed as 15.1) and **17.1** (Polysymbols spike — entire phase is optional).

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
