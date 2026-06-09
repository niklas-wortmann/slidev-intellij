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

- [x] **14.1 — Tag completion** (M): `<` in slide content → component names from the index, with self-closing-tag insertion. First verify what the bundled Markdown plugin already provides for plain HTML tags and only fill gaps. *(Verified: the Markdown plugin contributes nothing for inline tags itself, but inside recognized inline HTML the platform completes against the markdown file's HTML PSI root — so the contributor is registered `language="any"` and guards itself.)*
- [x] **14.2 — Attribute completion** (M): inside an open component tag, offer props from the index in plain / `:`-bound variants, plus the global Slidev directives (`v-click`, `v-after`, `v-motion`, `v-mark`, `v-drag`). *(`@event` variants dropped: neither the vendored built-in metadata nor the `.vue` scanner carries emit data, so there is nothing real to offer; the char filter still keeps the lookup open while typing `@…` by hand.)*
- [x] **14.3 — Hover docs** (S): component names/props via `platform.backend.documentation.targetProvider` (v2 API — same lesson as frontmatter: the legacy `lang.documentationProvider` gets shadowed).
- [x] **14.4 — Goto declaration** (S): local/theme/addon component tag → its `.vue` file; built-ins → external docs URL.
- [x] **14.5 — Typed handler** (XS): auto-popup on `<` and space-inside-tag (extend the `SlidevFrontmatterTypedHandler` pattern).
- [x] **14.6 — Tests** (M): index resolution (built-in/local/theme precedence), tag + attribute completion, docs, navigation; non-Slidev markdown unaffected.

## Phase 15 — Highlighting for component tags & Vue attributes

- [x] **15.1 — Annotator-based semantic coloring** (M): `SlidevComponentAnnotator` + `SlidevHighlightColors` (3 `TextAttributesKey`s: component tag, `v-` directive attr, `:`/`@` bound attr) + `SlidevColorSettingsPage` (Settings | Editor | Color Scheme | Slidev). Known component names colored on open *and* close tags; unknown PascalCase tags get a weak warning (typo-catcher, opening tag only — lowercase HTML untouched). Driven by a new `SlidevSlideTags.tokens()` full-document scan (the per-caret `tokenAt` was refactored onto the same collector); fresh re-parse per pass like `SlidevFoldingBuilder`. Tested (`SlidevComponentHighlightingTest` + `tokens()` unit tests).
- [x] **15.2 — Spike: HTML injection into HTML blocks** (S, ⚠ optional): **spike failed, as anticipated** — verified against the 2025.3 `markdown.jar`: `MarkdownHtmlBlock` extends `ASTWrapperPsiElement` and implements only `MarkdownPsiElement`, not `PsiLanguageInjectionHost`; the plugin's only injection hosts remain `MarkdownFrontMatterHeader` and `MarkdownCodeFenceImpl` (same as the 8.4 outcome). No host → no `MultiHostInjector`/`LanguageInjectionContributor` path. Moot in practice anyway: recognized inline HTML already completes against the markdown file's HTML PSI root (the 14.1 finding), so Community gets baseline HTML completion without injection. 15.1 stands as the committed approach.
- [x] **15.3 — Unknown-tag/attribute false-positive suppression + kebab-case tags** (S): the platform's HTML inspections (and, in full IDEs, the Vue plugin / web-symbols layer) run on the markdown file's HTML template-data root and flagged Slidev syntax — `<v-click>` as an unknown tag, `v-click.fade` / `v-mark.circle.orange` / attributify `mt-12` as unknown attributes. `SlidevHtmlInspectionSuppressor` (`lang.inspectionSuppressor language="XML"`, slidev-markdown.xml) suppresses `HtmlUnknownAttribute`/`HtmlUnknownBooleanAttribute`/`VueUnrecognizedDirective` wholesale in deck HTML (incl. injected ```html fences) — directives + UnoCSS attributify make any attribute name legitimate — and `HtmlUnknownTag` only for tags the component index resolves. Kebab-case tag resolution (`<v-click>` → `VClick`, Vue semantics) added via `componentForTag()` and wired through `componentFor()`, so the annotator now colors kebab component tags and docs/goto/attr-completion work on them too. Tested (`SlidevHtmlInspectionSuppressionTest`; note: `HtmlUnknownTag` itself never fires on markdown HTML roots in the test platform — the unknown-tag squiggle in the real IDE comes from web-symbols under the same toolId).

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

## Phase 18 — JS/TS support for slide script blocks & Vue expressions ✅

Inline HTML blocks of a slide are fully re-parsed in the markdown file's HTML
template-data root (`DefaultMarkdownFileViewProvider`) — unlike the Markdown root
(8.4/15.2 outcomes). Optional `JavaScript` plugin dependency, same pattern as
slidev-mcp.xml.

- [x] **18.1 — Spike: HTML-root script-body shape** (S, ⚠ risk): **outcome was Path B** —
  the JS plugin's `HtmlEmbeddedContentSupport` lexer-embeds `<script>` bodies as real JS
  PSI in the HTML root, so completion works for free and script-body *injection* is
  impossible/unneeded. Two gaps found: the embedded parse is always a plain-JS dialect
  (`lang="ts"` ignored) and reports phantom "Newline or semicolon expected" errors
  (newlines are swallowed into `MARKDOWN_OUTER_BLOCK`). Attribute values are plain
  `XmlAttributeValue` hosts (Path A) as planned. Probe kept as a regression test.
- [x] **18.2 — Implementation** (M), three pieces in `editor/` sharing the
  `SlidevScriptSupport` guards (HTML root → `.md` → Markdown base language →
  `stateContaining`; language lookup by stable ID strings, no JS-plugin classes):
  - `SlidevScriptInjector`: MultiHostInjector over `XmlAttributeValue` only — `:`/`v-bind`
    wrapped in parens (object-literal vs block-statement ambiguity), `@`/`v-on` bare,
    `v-*` expressions minus v-else/v-pre/v-cloak/v-once/v-for/v-slot. Always JS, never TS.
  - `SlidevScriptBodyAnnotator`: lexer-driven coloring of embedded script bodies (the
    editor highlighter is the Markdown lexer, so the embedded JS PSI has no colors);
    honors `lang="ts"` by lexing with the TypeScript highlighter.
  - `SlidevScriptErrorFilter`: suppresses the phantom embedded-parse errors in decks.
- [x] **18.3 — Registration** (XS): `<depends optional config-file="slidev-javascript.xml">JavaScript`
  + `multiHostInjector`/`annotator`/`highlightErrorFilter` EPs; `bundledPlugin("JavaScript")`
  for sandbox/tests.
- [x] **18.4 — Tests** (M): `SlidevScriptInjectionTest` — probe, completion through the
  embedded body, keyword-coloring annotation, error suppression (and non-suppression in
  non-Slidev markdown), paren-wrap fragment text per attribute kind, negatives (v-for,
  plain attrs, fenced code, non-Slidev file), no component-completion noise in script
  bodies. Note: when the caret is inside an injected fragment, `myFixture.file` *is* the
  fragment file.
- [ ] **18.5 — Stretch (follow-up, not core)**: script-setup-aware attribute fragments —
  the script body as injection *prefix* of each expression fragment so `:enter="final"`
  resolves against script consts. Perf/invalidations need care.

## Phase 19 — Syntax highlighting in Shiki Magic Move blocks & their nested code fences ✅

[Magic Move](https://sli.dev/features/shiki-magic-move) syntax: a **4-backtick** outer fence
with info string `md magic-move`, optionally followed by options (`{at:4, lines:true}`) and a
title (`[app.js]`, v0.52+). Each animation step is a normal 3-backtick fence inside, with its
own language and optional Shiki meta — click ranges `{*|1|2-5}` and per-step options
`{*}{lines:false}`; non-code text between steps is ignored (comments). Today the IDE shows the
whole block as plain text: the Markdown plugin's `CodeFenceInjector` asks
`CodeFenceLanguageGuesser` to resolve the info string, and neither `md magic-move` nor
`js {*|2}`-style inner strings resolve cleanly. The outer fence *is* a `MarkdownCodeFenceImpl`
— the plugin's one reliable injection host (8.4/15.2 outcomes) — which is the hook.

Two candidate designs; **19.1 decides which**:

- **Design A — `fenceLanguageProvider`**: register the public dynamic EP
  `org.intellij.markdown.fenceLanguageProvider` (verified present in 262's markdown
  plugin.xml; providers receive the *full* info string before the alias lookup, same hook
  PlantUML/Mermaid use). Map `md magic-move…` → Markdown so the outer fence becomes injected
  markdown and the *nested* fences get the plugin's normal per-fence injection recursively.
  Low code, matches upstream's intent (the `md` prefix exists so editors treat the body as
  markdown). Caveats: `getLanguageByInfoString(String)` has **no file context** → global
  mapping in all markdown files (acceptable: the info string is Slidev-specific); depends on
  recursive injection inside injected markdown actually rendering (spike question).
- **Design B — own `MultiHostInjector`** over the outer `MarkdownCodeFenceImpl`, guarded to
  Slidev decks (`stateContaining`, the `SlidevScriptSupport` house pattern): scan the fence
  body for the step blocks and run one `startInjecting(language)` session per step (multiple
  injections into one host are supported). Full control — deck-scoped, exact inner-language
  resolution with meta stripped, no recursion question. More code; must coexist with the
  markdown plugin's own `CodeFenceInjector` on the same host (if the guesser's
  suffix-stripping happens to resolve `md magic-move` → `md` → Markdown today, suppress its
  injection by having a `fenceLanguageProvider` return `PlainTextLanguage` for magic-move).

- [x] **19.1 — Spike: what the platform already does** (S, ⚠ decides A vs B): **outcome was
  Design B.** (a) The guesser space-chops `md magic-move` to `md`, so the bundled plugin
  *does* inject — a whole-fence Markdown injection; (b) but fences nested inside *injected*
  markdown never get recursive injection, so the steps stayed plain text → **B**; (c) a plain
  top-level ` ```js {*|2|5-6} ` fence already resolves to JavaScript via the same
  space-chopping — **19.8 is moot**. All three pins kept as regression tests in
  `SlidevMagicMoveInjectionTest`.
- [x] **19.2 — Magic-move scanner** (S): `MagicMoveBlocks` in `parser/` (sibling of
  `CodeBlocks.kt`): 4+-backtick `md`/`markdown magic-move` fences with options `{…}` /
  title `[…]`, steps as (language, meta, content range) — meta split covers `{*|2|5-6}`,
  `{*}{lines:false}` and the space-less ` ```ts{2,3} ` form. CRLF-safe, non-code text
  between steps ignored, foreign fences skipped (quoted examples don't match), closing
  fences with info strings are content (CommonMark), unclosed fences swallow to EOF.
  Unit-tested (`MagicMoveBlocksTest`, 17 cases).
- [x] **19.3 — Language resolver without internal API** (S): `SlidevFenceLanguages` —
  alias table for the common Slidev tokens → case-insensitive `Language.findLanguageByID`
  → file-type-by-extension fallback, gated on `LanguageUtil.isInjectableLanguage` and
  never plain-text. Missing plugin (e.g. `ts`/`vue` in Community) → null → that step is
  skipped, never an error.
- [x] **19.4 — Implementation** (M): **Design B** — `SlidevMagicMoveInjector`
  (`com.intellij.multiHostInjector` in `slidev-markdown.xml`) over `MarkdownCodeFence`,
  one injection session per step using 19.2 + 19.3; shared guards in
  `SlidevMagicMoveSupport` (deck-scoped via `stateContaining`, through `originalFile` so
  completion copies keep their injection). Registered `order="first"`: injection is
  first-wins per host, and the markdown plugin's own `CodeFenceInjector` would otherwise
  shadow the step injections with its whole-fence Markdown fallback (19.1(a)) — which is
  also why no `fenceLanguageProvider` suppression is needed.
- [x] **19.5 — Error-noise control** (S): the markdown plugin's `CodeFenceHighlightInfoFilter`
  does cover the step fragments (their host is a markdown code fence) but is gated on the
  "show problems in fences" setting whose default is to *show* — so
  `SlidevMagicMoveErrorFilter` (the `SlidevScriptErrorFilter` pattern) suppresses parse
  errors in magic-move step fragments unconditionally; regular fences keep their errors.
- [x] **19.6 — Parser & decoration interplay** (XS–S): regression pins added —
  `SlidevParserTest` (`---` lines inside a magic-move block don't split slides; the
  fence-skip tracks the leading-backtick run) and `CodeBlocksTest` (only the inner
  3-backtick steps get line numbers, the outer fence doesn't — matches the VS Code
  annotator).
- [x] **19.7 — Tests** (M): `SlidevMagicMoveInjectionTest` (13 cases): per-step injection
  presence + language (mixed `js`→`ts`), meta-suffixed inner info strings, outer-fence
  options + `[title]`, empty/unresolvable steps skipped, syntax coloring over step content,
  completion inside a step, error suppression (and non-suppression outside magic-move),
  CRLF, scope negatives (non-Slidev markdown keeps the platform Markdown injection; plain
  ` ````md ` fence untouched), and the 19.1(c) line-highlight-meta pin.
- [x] **19.8 — Stretch: line-highlight meta on regular fences** — **moot**: per 19.1(c) the
  guesser's space-chopping already resolves ` ```ts {2-3|5} ` today; pinned as a regression
  test so a platform change would surface.

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
| 11 | Phase 18 (JS/TS in script blocks & Vue expressions) | ~2–3 days, 18.1 was the risk — done |
| 12 | Phase 19 (Magic Move highlighting) | ~1–2 days, 19.1 spike decided Design B — done |

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
