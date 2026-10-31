# Input Layer roadmap

Input Layer is a fork of Unexpected Keyboard 2.1.0. This file tracks what is
done, what comes next and what is still undecided. It is updated with the
builds; the newest build is always at
[releases/latest](https://github.com/Flareragnarok/inputlayer2/releases/latest).

Last updated: 2026-10-31 (build 10).

## Done

| Build | What |
|---|---|
| 1 | Own app id `io.github.flareragnarok.inputlayer` and name "Input Layer", installs next to Unexpected Keyboard. Studio screen (Settings → Studio): modules (import, export, use, delete) and in-app updates from GitHub releases. |
| 2 | Comfort bar above the suggestion bar. `changeheight` modules apply to the keyboard (keyboard height, bar height, row heights, bottom margin). |
| 3 | Comfort bar redesigned: tiny centered keys with a tap action and up to 8 swipe actions, like the keyboard's keys. See-through clipboard icon in the bottom-left corner of the suggestion bar. |
| 4 | The comfort bar's language key switches the suggestions' language (the button upstream had on the suggestion bar). |
| 5 | Updates: dropdown of newer builds, newest preselected. Past builds can be restored (republished on GitHub as a new build number). |
| 6 | Suggestion bar 28dp by default instead of a full key row. |
| 7 | Bigger clipboard corner icon; suggestions start after it. |
| 8 | The language key is a normal comfort bar key instead of pinned to the right end. ROADMAP.md. |
| 9 | Comfort bar keys look like the keyboard's keys, smaller and a shade brighter. The height key selects a whole container (comfort bar, suggestions, keyboard) and highlights it; swiping up/down resizes it. |
| 10 | Suggestion bar only shows suggestions: the language name, language button and "install a dictionary" message are gone from it (the comfort bar's language key shows `⬇ EN` when that dictionary is missing). Memory icon in the bottom-right corner (the pane itself comes next). |

Infrastructure:

- Every push to `studio` is tested, built and published as release `build-N`
  by GitHub Actions (`.github/workflows/studio-release.yml`). The
  "Restore Input Layer build" workflow republishes a past build.
- Local build machine: `android-build-setup.sh` (user-level JDK 17 and
  Android SDK, no root) and its section in the VM's `RECIPE.md`. Reusable for
  a remote build machine; GitHub Actions already covers remote builds.

## Next

1. **Clipboard pane, rebuilt.** History of everything copied, text and
   files, in a scrollable column on the left third: tap to paste, pin to keep.
   Pinned items fill the right two thirds for quick access.
2. **Memory pane.** Your own workspace: add items from your pins, from the
   clipboard and from files on the phone's storage. Opened from the
   see-through icon in the bottom-right corner of the suggestion bar.
3. **Switch between Clipboard and Memory** from inside either pane.

## Later

- **Resize mode.** Drag the edge of a highlighted container (comfort bar,
  suggestion bar, keyboard) instead of tapping up/down, and save the result
  as a named `changeheight` module. Containers only, never single keys or
  rows (decided 2026-10-31).
- **Snippets module.** Your own snippets (commands, ASCII art), inserted with
  one tap; multi-line supported.
- **Theme module.** Custom colours, picked in the app.
- **Comfort bar editor.** Choose and place the comfort bar's keys and their
  swipe actions in the app instead of writing JSON.
- **`DONE-keyboard.md`** final report in the Studio-Briefing Drive folder.
- Store texts in `fastlane/` still say "Unexpected Keyboard"; only matters
  for publishing on F-Droid.

## Undecided

- Language and suggestions, to rethink: should the suggestions' language
  follow the keyboard layout, should the language key cycle languages
  directly instead of opening a list, should the suggestion bar show more
  than 3 words (scrollable)?
- Corner icons disappear when suggestions are turned off. Move them to the
  comfort bar in that case?
- Comfort bar in the split layout (tablets, landscape): untested.
- Dictionaries come from upstream's
  [Unexpected-Keyboard-dictionaries](https://github.com/Julow/Unexpected-Keyboard-dictionaries)
  (word lists from Helium314's aosp-dictionaries), which states no license.
  Keep, or host our own word lists?

## Known limits

- Android only installs newer version numbers: past builds come back through
  the restore workflow, never as a direct downgrade.
- Restoring a build older than 5 brings back its older Studio screen
  (without the dropdown); installing any newer build from its list fixes it.
- Modules are settings only, never code: a keyboard sees everything typed,
  passwords included, so it never runs code from a file.

## Module reference (current)

Every module is a JSON file with `module` (its type), `name` and `version`.

```json
{ "module": "changeheight", "name": "compact", "version": 1,
  "keyboard_percent": { "portrait": 30, "landscape": 45 },
  "bar_dp": 26, "comfort_bar_dp": 26, "margin_bottom_dp": 4,
  "rows": [0.8, 1.0, 1.0, 1.0, 0.9] }
```

`rows` are height factors, top to bottom (file only; the height key never
resizes single rows). `comfort_bar_dp: 0` hides the comfort bar.

```json
{ "module": "comfortbar", "name": "mine", "version": 1,
  "keys": [ { "c": "copy", "nw": "cut", "ne": "paste", "sw": "select_all" },
            { "w": "left", "e": "right", "n": "up", "s": "down" },
            { "c": "dictionary", "sw": "layout", "se": "keyboard_picker" } ] }
```

`c` is the tap; `n ne e se s sw w nw` are the swipe directions. Actions:
`left right up down select_all copy cut paste undo redo clipboard
dictionary layout keyboard_picker select_container height_up height_down
studio` and
`switch:<type>` (cycle saved modules of a type).
