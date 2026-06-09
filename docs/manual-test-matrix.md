# Manual test matrix (Phase 10.3)

Run against a real project: `npm create slidev@latest` (accept defaults, do **not** start the
server from the terminal unless the case says so). Launch the sandbox with the **Run Plugin**
run configuration (`./gradlew runIde`) and open the created project.

Legend: ☐ untested · ✅ pass · ❌ fail (file an issue with notes)

## 1. Project detection

| # | Case | Steps | Expected | Result |
|---|---|---|---|---|
| 1.1 | Auto-detect on open | Open the project | Slidev tool window available; Projects tab lists `slides.md`, marked active | ☐ |
| 1.2 | Add entry manually | Projects tab → `+`, pick another `*.md` | Entry appears; can be set active | ☐ |
| 1.3 | Remove entry | Context menu → Remove | Entry gone; active falls back / empty state shows | ☐ |
| 1.4 | Rescan | Create `talks/foo.md` with frontmatter, hit Rescan | New entry found per include glob | ☐ |
| 1.5 | Choose entry from empty state | Remove all entries → Slides tab | "Choose one" link opens entry popup; choosing restores tree | ☐ |

## 2. Server lifecycle

| # | Case | Steps | Expected | Result |
|---|---|---|---|---|
| 2.1 | Spawn server | Preview tab → Start Server | Run tool window console shows slidev output; preview loads | ☐ |
| 2.2 | Adopt running server | Start `npm run dev` in a terminal first, then open preview / Start Server | Existing server adopted (no second process), preview loads on its port | ☐ |
| 2.3 | Stop server | Toolbar Stop | Process terminated; preview shows "not running" card with Start link | ☐ |
| 2.4 | Port collision | Occupy 3030 with a non-Slidev server, then Start | Auto-allocates next free port; compat warning **not** shown for the spawned server | ☐ |
| 2.5 | Server failure | Break the dev command in Settings (e.g. `nosuchbin ${args}`), Start | Failure notification with working "Show Output" link | ☐ |
| 2.6 | Compat mode | Point the port setting at an old/non-versioned Slidev server | Warning notification; preview nav buttons + overview toggle hidden; refresh & open-in-browser still shown | ☐ |

## 3. Editor ↔ preview sync

| # | Case | Steps | Expected | Result |
|---|---|---|---|---|
| 3.1 | Editor → preview | Move caret across slide boundaries in `slides.md` | Preview navigates to the slide under the caret | ☐ |
| 3.2 | Preview → editor | Click next/prev in the preview iframe itself | Editor scrolls to the slide (without stealing focus) | ☐ |
| 3.3 | Toolbar nav | Prev/Next slide + Prev/Next click buttons | Preview navigates; buttons disable at ends (`hasPrev`/`hasNext`) | ☐ |
| 3.4 | Sync toggle | Disable sync, repeat 3.1/3.2 | No sync in either direction; re-enable restores it | ☐ |
| 3.5 | `src:` imported slides | Put a slide in `pages/x.md` via `src:`, caret into that file | Sync resolves through the import to the right slide number | ☐ |
| 3.6 | Editor nav shortcuts | Alt+Up / Alt+Down in `slides.md` | Caret jumps between slide starts | ☐ |

## 4. Overview mode

| # | Case | Steps | Expected | Result |
|---|---|---|---|---|
| 4.1 | Toggle | Toolbar overview toggle | Wrapper reloads into `/overview`; toggle state persists per project | ☐ |
| 4.2 | Click a slide | Click a slide card in overview | Editor jumps to that slide's source (focused) | ☐ |
| 4.3 | Scroll sync | Move caret in editor while overview is open | Overview scrolls to the slide (debounced, no feedback loop) | ☐ |

## 5. Multi-project

| # | Case | Steps | Expected | Result |
|---|---|---|---|---|
| 5.1 | Two entries, two servers | Add a second entry, start both | Distinct ports; Projects tab shows both running | ☐ |
| 5.2 | Switch active | Set the other entry active | Preview swaps to its server; slides tree re-roots | ☐ |
| 5.3 | Stop inactive | Stop the non-active server | Active preview unaffected | ☐ |

## 6. Editor decorations & language support

| # | Case | Steps | Expected | Result |
|---|---|---|---|---|
| 6.1 | Slide annotations | Open `slides.md` | Slide-number line hints at each `---` divider; frontmatter tinted | ☐ |
| 6.2 | Folding | Code → Folding | Each slide foldable (requires Markdown plugin enabled) | ☐ |
| 6.3 | Frontmatter completion | In a slide's `---` block, type `lay` / `transition: ` | Key completion with docs; enum values for `layout`/`transition` | ☐ |
| 6.4 | Headmatter vs frontmatter | Complete in slide 0 vs slide N | Headmatter-only keys (e.g. `theme`) offered only in slide 0 | ☐ |
| 6.5 | Validation | `transition: [oops` (broken YAML) / wrong value type | Annotator warning on the block | ☐ |
| 6.6 | Built-in markdown preview with Vue attrs | Add `<span @click="$slidev.nav.next" :class="x">hi</span>` to a slide, open the *built-in* markdown split preview | Preview renders without `InvalidCharacterError` in idea.log; the span shows (Vue attrs dropped) | ☐ |

## 7. Environment fallbacks

| # | Case | Steps | Expected | Result |
|---|---|---|---|---|
| 7.1 | No JCEF | Run in a JCEF-less runtime (e.g. `-Dide.browser.jcef.enabled=false`) | Preview shows "embedded browser not available" card with working "Open in Browser instead" link (after the server is started) | ☐ |
| 7.2 | Markdown plugin disabled | Disable the Markdown plugin, restart sandbox | Plugin still loads; folding/completion absent, server + preview + trees still work | ☐ |
| 7.3 | IDE theme switch | Toggle light/dark while preview is open | Preview re-posts color schema; slides follow the IDE theme | ☐ |
