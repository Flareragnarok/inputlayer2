# Input Layer roadmap

Input Layer is a fork of Unexpected Keyboard 2.1.0. This file tracks what is
done, what comes next and what is still undecided. It is updated with the
builds; the newest build is always at
[releases/latest](https://github.com/Flareragnarok/inputlayer2/releases/latest).

Last updated: 2026-11-01 (build 37).

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
| 11 | Clipboard / Memory pane with tabs, replacing upstream's clipboard pane. Clipboard: history on the left third, pins on the right two thirds (existing pins kept), one tap to turn history on. Memory: items by category (Links, Symbols, Snippets, Notes and your own, managed in Studio). Long-press menus: pin, save to Memory (likely category first), copy to clipboard, move, delete. |
| 12 | Memory keeps what is copied: every copied text is saved automatically under its content type (Text, Links, Snippets, Symbols; Images and Files come next), and stays after restarts. Skipped: copies from password fields and copies marked sensitive (Android 13+). Off switch in Studio. Clipboard tab filters by content type. The clipboard itself works as before. Source app is not used: Android doesn't tell which app a copy came from (decided 2026-10-31). |
| 13 | Memory's history: a ⟲ History button at the right end of Memory's categories shows what was copied, one record per phone restart. Delete one item, one record or all history (tap twice to confirm). Saved items (saved or moved by you) are never deleted with the history. Clipboard tab: Clear button for its short-term history. |
| 14 | Edit strip under the space bar. Left: scope (Word, Sentence, Line, Paragraph, All) and selector (select the unit at the cursor, previous/next, grow/shrink by one, deselect). Right: Aa Appearance (case; unicode bold, italic, mono, script, fraktur, double-struck, small caps, circled, fullwidth, strike, underline, zalgo, converting between them) and ¶ Format (the app's markup: Markdown, Discord, Reddit, WhatsApp, Telegram, HTML, BBCode, none for Threads/Instagram/X; chosen per app, wraps and unwraps). The ↕ key can resize it. |
| 15 | Edit strip has seven keys and cycles on tap: Scope, Selector (unit at the cursor, then next or previous), +[ ] custom selection, →↑/←↓ direction, Aa (next unicode style; swipe up: case, down: list), ¶ (next format, plain in between; swipe down: list and syntax), +¶ custom format. Your own selections (delimiters or pattern), formats (before/after) and fonts (your letters) are `selection`, `format` and `font` modules, made in Studio → Edit strip tools, and join the cycles. |
| 16 | Edit strip reordered into eight keys: ←→ (selection direction), Scope, Spec (defines selections), Sector (selector), View, Vers, Vex, ↑↓ (cycling direction). See build 19 for today's roles. |
| 17 | Swipe down on backspace cuts the selection, which then shows in the clipboard history (every layout; none used that spot). |
| 18 | Sector is built around taps: tap jumps to the next unit, swipe up adds one, down removes one, on the side the ←→ arrow points to (it only grew forward before). View and Vex: swipe left or right for the menu, down to restore (plain letters, format removed). |
| 19 | View and Verse swap jobs: View formats (markup), Verse changes the look (unicode, case); Vers is now Vert. Scope always says "Scope", the current scope shows top-left, swiping there lists them. Sector: swipe right deselects. Past builds in a dropdown with one Restore button. |
| 20 | Nested keys: Scope (Text, Custom), View (the syntaxes, remembered per app) and Verse (Case, Serif, Sans, Type, Shapes, Marks, Fonts) show their nest on top and current item at the bottom. Tap cycles the items, swipe left the nests, swipe right lists everything. |
| 21 | Nests switch with swipe right (left lists everything); swipe down goes back to the default nest, swipe up restores the text (View, Verse). Scope swiped down types in View's format until the scope's separator (space for Word). Space bar: down resets all nests, left saves them as a nest config, up switches configs (replaces upstream's layout switching and left cursor slider, decided 2026-10-31). |
| 22 | Typing in format (Scope down) also applies Verse's look to every letter typed; View on plain types in Verse's look only. Sector: left/right move the cursor by a unit without selecting, up selects the unit at the cursor or deselects, down switches exclusive/inclusive selection (inclusive takes the trailing space, the delimiters, the line break). |
| 23 | "plain" is an item of every Verse nest (but Case) and of View's list. Swipe down on View or Verse applies plain to the text and goes back to the default nest. Sector left/right with a selection moves the selection by a unit (staying inclusive or exclusive); without one, the cursor moves, skipping spaces when inclusive. |
| 24 | Inclusive selection grows a (partial) selection out to whole units and their separators, keeping what it held. Typing in format shows on the Scope key (lit, "✎ typing", the format and look) with a message saying what ends it. |
| 25 | Updater reads all releases (up to 300, by pages of 100) and sorts them by build number: GitHub lists them by tag as text (build-9 before build-24), so the newest could fall off a single page. CI writes build failures as annotations. |
| 26 | Pinned selection: Sector ↖ pins the selection; a selection bar (above the suggestions by default) shows it, tap selects it again, ✕ unpins. View, Verse and backspace ↓ (cut) act on the pinned text while the cursor types elsewhere; it is found again when text before it changes. The height key also moves the selected container: ← up, → down in the stack (comfort bar, selection bar, suggestions, keyboard, edit strip), saved as `order` in the changeheight module. The lowest container keeps clear of the navigation bar. |
| 27 | The height key also selects each keyboard row (after the containers: row 1 at the top to the last row), outlined in the keyboard; up/down change that row's height factor by 0.1, independently of the others. Growing a row makes the keyboard taller. |
| 28 | Pinning dropped (unintuitive). Typing in format moved to Scope ↑ and is previewed in the selection bar (✎ and the formatted text) while it goes to the cursor; ✕ stops it. Scope ↓ goes back to Text/Word. Sector shows the selection type (excl/incl) at the bottom. |
| 29 | Inclusive Sector keeps the selection: tap and left/right add the next or previous unit to it (exclusive still moves to it). Scope ↑ always starts typing in format, with what View and Verse show (even plain), so the selection bar's ✎ shows it's on. |
| 30 | Typing in format works on a range: Scope ↑ with a selection gives the selection View's format and Verse's look at once and goes on inside it; edits anywhere inside keep the look; the separator ends it only at the end. Sector moves within that text meanwhile. A look picked from Verse's list becomes Verse's nest and item. |
| 31 | Macros. Spec ↓ saves the last typing in format's steps (View's format, Verse's look, not the typed text); Vert ↓ saves its key replacements (the typed letters, e.g. "→ oBAmA"). Tap Spec or Vert applies the current one to the typed text during typing in format, otherwise to the selection (or Scope's unit); → picks the next, ← lists them, ↑ opens the definitions in Studio. Saved as `spec` and `vert` modules. |
| 32 | Typing in format applies Verse's look only to letters as they are typed, so changes made to single units stay when moving to the next. Spec records those changes as steps (scope, unit index, look or format) and replays them on the same units of another selection. |
| 33 | Typing in format starts with Scope ↓ and is pushed with Scope ↑ (no longer ended by typing a space). Scope shows "Scope" over the current scope, like Sector (nests get a name once they have a use). Spec lights up and counts the recorded steps while typing in format. |
| 34 | Scope lights up while typing in format (not Spec). In typing in format: Spec tap applies the current Spec to the typed text, → picks one, ↓ saves what was recorded since Scope ↓ started it. |
| 35 | Third Sector mode: Sector ↓ cycles excl → incl → desel. In desel, tap (in the arrow's direction) and ←/→ remove the unit at that end of the selection, until nothing is selected. |
| 36 | The arrow key next to the space bar: tap is Sector's step in the ←→ direction (its tap was an off-by-default Compose placeholder). Sector's tap cycles excl/incl/desel. Shift: swipe up Caps Lock, swipe right Compose. Verse's tOGGLE cASE gives exactly that (it flipped every letter, like UPPER on lowercase). |
| 37 | desel removes units from the end opposite to the arrow's tip, so the tip's unit goes last (it went first). The arrow key's tap shows ⇢ or ⇠ with Sector's direction. |

Infrastructure:

- Every push to `studio` is tested, built and published as release `build-N`
  by GitHub Actions (`.github/workflows/studio-release.yml`). The
  "Restore Input Layer build" workflow republishes a past build.
- Local build machine: `android-build-setup.sh` (user-level JDK 17 and
  Android SDK, no root) and its section in the VM's `RECIPE.md`. Reusable for
  a remote build machine; GitHub Actions already covers remote builds.

## Next

1. **Images and files.** Copied images and files kept in Memory (Images,
   Files) and shown in the Clipboard tab, pasted back into apps that accept
   them; adding files from the phone's storage to Memory.
2. **Adding Memory categories from the pane** (today: in Studio, since the
   keyboard can't easily type into its own dialogs).
3. **Language and suggestions, rethought** (see Undecided).
4. **Vert on every match** in the field (most apps give the keyboard the
   whole text), as an option.

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

`edit_strip_dp` and `selection_bar_dp` set the edit strip and selection
bar (0 hides them); `order` lists the containers top to bottom (`comfort`,
`selection`, `suggestions`, `keyboard`, `edit`). `rows` are height factors, top to bottom (the height key changes them one
row at a time). `comfort_bar_dp: 0` hides the comfort bar.

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

```json
{ "module": "selection", "name": "Parens", "version": 1, "open": "(", "close": ")" }
{ "module": "selection", "name": "Tags", "version": 1, "pattern": "#\\w+" }
{ "module": "format", "name": "Angles", "version": 1, "open": "<<", "close": ">>" }
{ "module": "font", "name": "Greekish", "version": 1,
  "lower": "αβςδεφγηιξκλμνοπθρστυvωχψζ", "upper": "", "digits": "" }
```
