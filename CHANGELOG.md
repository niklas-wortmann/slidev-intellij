<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Slidev Changelog

## Unreleased

## 0.1.0-alpha.1 - 2026-06-09

### Added

- Slidev project detection: scans the workspace for `slides.md` entries (configurable include/exclude globs), with a projects tree to add, remove, rescan, and switch the active entry
- Dev-server management: start, stop, and adoption of already-running servers, with automatic port allocation and a configurable dev command (`${args}`, `${port}` substitution); server output in the Run tool window
- Embedded preview (JCEF) with bidirectional editor ↔ preview sync, click stepping, slide/overview modes, and IDE theme propagation; "Open in Browser" fallback when JCEF is unavailable
- Slidev split editor: deck entry files open with an editor/preview split (like the Markdown preview), with the embedded live preview as the preview half; selecting a deck's editor makes it the active entry
- Slides tree with drag-and-drop slide reordering and caret sync
- Editor support: slide-number annotations, frontmatter tint, virtual code-block line numbers, per-slide folding, and Alt+Up/Alt+Down slide navigation
- Frontmatter language support: schema-driven completion, quick documentation, and validation for headmatter and per-slide frontmatter (vendored Slidev JSON schemas)
- Compat-mode handling for older Slidev servers (navigation/overview actions hidden, warning notification)
- Built-in markdown preview compatibility: Vue template attributes in slides (`@click`, `:class`, ...) no longer crash the preview with `InvalidCharacterError`
- New Project wizard generator: scaffolds the official `create-slidev` starter template (demo deck, components, snippets, deploy configs) with a package-manager choice and optional dependency install; template content is a snapshot of `create-slidev` 52.16.0 and should be refreshed per release
