# Input Layer roadmap

The build-by-build log. The [README](README.md) is the overview: how
everything works now, history, ideas and open questions.

Input Layer is a fork of Unexpected Keyboard 2.1.0. This file tracks what is
done, what comes next and what is still undecided. It is updated with the
builds; the newest build is always at
[releases/latest](https://github.com/Flareragnarok/inputlayer2/releases/latest).

Last updated: 2026-11-01 (build 74).

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
| 38 | Backspace ↑ switches between regular backspace (⌫) and forward removal (⌦), the tap and label following; the forward delete on backspace's ↗ is retired. |
| 39 | Undo/redo that work: Ctrl ↖ undoes (Ctrl+Z), Ctrl ↗ redoes (Ctrl+Shift+Z), replacing placeholders there. The editing actions behind the comfort bar's undo key often did nothing; that key is retired from the defaults, and `undo`/`redo` in comfortbar modules now send the Ctrl keys too. |
| 40 | Suggestions learn from typing: finished words are counted, the user's words come first (most used first) before the dictionary's, words not in the dictionary are suggested once typed twice, words written in typing in format are suggested as written, and with nothing typed yet the bar shows the usual next words. Private storage, never from password fields; off switch and "forget" in Studio. |
| 41 | Mails: copied email addresses (and mailto: links) are kept in a Mails category of Memory and can be filtered in the Clipboard tab. |
| 42 | Verse's Case nest: snake_case, camelCase and aLtErNaTiNg, also while typing in format (a space becomes _ or is dropped with the next letter capital). snake_case and camelCase convert into each other; Title splits them back into words. |
| 43 | Spec and Vert reworked. A Spec is a lens: atomic selection steps (scope, mode, direction, step, unit, move) giving the scope a Vert works on. A Vert operates on that scope once, faithfully: typing, ⌫/⌦, clear, wrap/unwrap, look, case (insertions even on an empty selection; transformations of nothing do nothing). Both are recorded in typing in format and simplified when saved (back-and-forth steps cancel), and edited step by step in Studio (Spec ↑ / Vert ↑). Old Specs became Verts. |
| 44 | Clipboard and Memory open in the suggestion bar's place, the keyboard staying below. Long-press → Edit on a history entry, pin or Memory item: the selection bar expands into an editor and the keyboard types into it (keys, ⌫/⌦, arrows, the edit strip, Specs and Verts); ✓ saves (an edited clipboard entry becomes the newest one), ✕ cancels. |
| 45 | The clipboard and memory icons stay in the corners over the open pane: the open tab's icon closes it (back to the keyboard), the other switches tabs. |
| 46 | The pane's tab row (Clipboard / Memory buttons, ✕) is gone: the corner icons alone navigate, the open tab's icon drawn fully opaque. |
| 47 | The open tab's corner icon lights up (blue highlight behind it). |
| 48 | View and Verse only pick (tap, swipes, lists, ↑ ↓ for plain); **Backspace ←** (✦) applies the picked format and look to the selection or the unit at the cursor, replacing a format already there. Scope ↑ leaves the pushed text alone: nothing selected, and picking plain afterwards no longer unwraps or restyles it. A Verse pick during typing in format applies to the letters typed next. |
| 49 | The apply is split: **Backspace ←** (✦) applies Verse's look only (markers around the text stay), **Shift ←** (¶) applies View's format (replacing the one there; in apps without markup, the look). |
| 50 | Clipboard and Memory reworked. A bar on top of the pane shows the last three copies (cut with …), or the whole text of the item last tapped. Every item is one line (first words, then …) with a ✎ to edit it; tap types, long-press opens the menu with Delete first. Clipboard: history on the left 30%, pins on the right. Memory: the left 30% free for now; on the right, categories scrolling up and down, each a row of entries scrolling sideways, then the history records. Copies of 1,000+ characters wait under "Keep?" (✓ keep, ✕ drop; size set in Studio). Editing takes the whole pane: the full text, scrolled to the cursor, a tap places the cursor. The selection bar no longer holds the editor. |
| 51 | New content types **Commands, Output, Logs, Code** with fixed rules (README → How copies are sorted); Snippets keeps other multi-line text. In a category, alike items are next to each other (commands by program, links and mails by domain, logs by tag, code by language), a group of several labelled. The history is sorted again with the new rules; saved and moved items stay. |
| 52 | Memory in three columns: the left 30% free, the categories listed on the right (Keep? first, ⟲ History last), the tapped one's items in the middle, one per line, groups labelled. Editing an item: the keyboard follows the item's cursor and selection instead of the app's (the app's selection updates no longer reach the edit strip and cursor keys meanwhile, the item's do), so Sector and the cursor keys land where expected; the view follows a selection's moving end. |
| 53 | Settings cleaned up: the main screen shows only Studio and **Legacy settings**, which holds all of upstream's settings unchanged. |
| 54 | Fix: opening Clipboard or Memory (and copying) crashed the keyboard since build 51. A content-type pattern had a bare `}`, which Android's regex engine refuses (the JVM's accepts it). Memory's file was not touched by the crash; copies made with 51–53 weren't kept. A test now checks the patterns. |
| 55 | Builds 51–53 also crashed Studio on opening (it loads Memory's categories), so the updater couldn't be reached: those builds need build 54+ installed by hand from the releases page. Now a failure in Memory shows as a message in Studio and in the pane, and the copy keeper skips the copy, instead of stopping Studio or the keyboard. |
| 56 | The app's icon opens Studio (upstream's launcher screen is gone), with buttons for Android's keyboard list and Legacy settings. |
| 57 | Input Layer's own layouts, **AZERTY** and **QWERTY**, shaped like Gboard's: digits and accents on the letters' swipes, a bottom row without Ctrl / Fn / Compose (?123, comma, space, period with ? and !, Sector's arrow, Enter), and Gboard's two symbols pages (?123 and =\<, with ~ on the second). "System" picks them for Latin-script languages; upstream's layouts are listed as *Legacy · …*. |
| 58 | **Input Layer QWERTZ** like Gboard's German keyboard (ü ö ä keys, S ↖ ß). Fix: build 57's "system" choice gave German (and every other Latin-script language) our QWERTY; now French → AZERTY, German → QWERTZ, English → QWERTY, and other languages keep their own layout. |
| 59 | **Crash reports** in Studio: crashes and survived errors are kept on the phone (error, place, build; never typed text), the last 10, with Copy and Clear. |
| 60 | **Beta and stable builds**: every push is published as a beta (GitHub pre-release). The updater offers only stable builds unless *Beta builds* is on; *Mark build N stable* opens the release on GitHub. Builds 56–59 were marked beta; 55 is the latest stable. |
| 61 | **Layout key** at the right end of the comfort bar: shows the layout (FR, DE, RU, EL…), tap next, ← previous, ↑ list. **Studio → Languages** ticks the layouts to switch between (automatic, French AZERTY, German QWERTZ, English QWERTY, Russian, Greek). Every layout, upstream's included, gets Input Layer's bottom row (no Ctrl / Fn / Compose). |
| 62 | **Fix: endless row growth** from the height key (see Bug log). **Back undoes** height changes and moves, one gesture at a time; long Back (or ↕ ↙) undoes all since the keyboard was shown; ↕ ↖ undoes one. Heights are clamped to sane ranges and **repaired on start** without touching other data. |
| 63 | **Updater no longer hides betas silently**: with *Beta builds* off, Studio said "You have the newest build" while a newer beta existed (owner on 61, build 62 was a beta, so they had to install from GitHub). It now says which beta is out and to turn on *Beta builds*; the switch re-filters the list at once, without checking again. |
| 64 | **Past builds lists every older build**, betas included (marked "· beta"). With *Beta builds* off it only offered stable ones, so on 61 the newest restorable build was 55. The switch now only filters the *newer* builds offered as updates. |
| 65 | **Layout mode** (owner's request): ⇄, the comfort bar's leftmost key (copy/cut/paste/select all moved to its swipes), makes a tapped key open in Studio's **key editor**: pick the tap or a swipe, then add/replace from a searchable list of every key of every layout plus the special keys (grouped by script, alphabetical), remove, or back to the layout's own. Per layout, in a `keymap` module, applied after all other changes. Dot and comma are left to the owner to rearrange with it. |
| 66 | Layout mode's picker gains **Music** (♩ ♪ ♫ ♬ ♭ ♮ ♯ 𝄞 … 🎵 🎶 🎼) and **Emoji** (all, in the emoji pane's order), and **Type text or emoji…** puts any text on a key (kaomoji with `:` and quotes included). Special keys show their names in the list (their icons only exist in the keyboard's font). |
| 67 | Owner's defaults: **?123 and Shift trade places** on every layout (symbols pages follow: the key back is where ?123 was), **Ctrl on ?123's ↓**. Layout mode can **swap whole keys** ("⇄ Swap the whole key with…" in the key editor), so rearranging no longer needs a build. |
| 68 | Owner's default: **?123 a quarter key wider, backspace as much tighter** (same row total). Layout mode's key editor gains **↔ Width** (±¼, with the row's total against the widest row) and **✂ Split into two keys** (right half: forward delete or any key, edited like any key) / **⧉ Join**. Stored as `width` and `split` in `keymap`. |
| 69 | **Hold a key to edit it** (owner): with key repeat off, the new default, holding a key opens Studio's key editor for it instead of repeating; nothing repeats. The layout key's **↓** switches key repeat (↻ on / ✎ off; was the keyboard picker, still on the language key's ↘). Modifiers still lock when held. |
| 70 | Keyboard picker on the **layout key's ↘** (off the language key). A backspace 2 keys wide or more (QWERTZ) is **split**: ⌫ with its swipes, then at the edge **⌦ forward delete, ↓ keyboard picker**. Narrower backspaces stay whole. |
| 71 | **Names** (owner): layout key = left end ⇄ (customizes the layout), language key = right end, suggestions key = the suggestions' language. **Language key ↓ saves the key arrangement** as "Layout N", **→ picks a saved one**. **Layout key**: tap edit mode, ↑ copy, ↓ key repeat on/off, ↗ backspace split on/off. Cut (backspace ↓) and select all (now ⌦ ←) live on the keys; paste dropped from the comfort bar (owner: superfluous); the keyboard picker is ⌦ ↓ only. |
| 72 | Split backspace is **⅔ ⌫ / ⅓ ⌦** (owner: one much smaller). **⌦ ↓ switches key repeat** (new key `studio_toggle_repeat`), replacing the keyboard picker there. |
| 73 | **Comfort bar order and names** (owner): layout key ⇄ · suggestions key · cursor key (center) · language key · **edit key** ↕ (was "height", now at the right end). README has a Names section. |
| 74 | **The edit key edits everything** (owner's model): tap cycles **layers of focus** (whole keyboard, bars, components = rows, items = keys); **tap the element** to select it (the keyboard catches the taps, `FocusLayout`); ↑ ↓ size (whole: everything in proportion; key: the row keeps its width, from the next key or the whole row), ← → order (rows carry their heights), ↗ menu: add a key (Studio's picker), split / join rows, remove a key, edit its tap and swipes, arrangement back. Arrangement stored per layout as `_rows` in `keymap` (`Arrange`, `KeyMap.arrange`). Undo covers heights and arrangement. Bars' own items (comfort and edit strip keys) are not editable yet. |

Infrastructure:

- Every push to `studio` is tested, built and published as release `build-N`
  by GitHub Actions (`.github/workflows/studio-release.yml`). The
  "Restore Input Layer build" workflow republishes a past build.
- Local build machine: `android-build-setup.sh` (user-level JDK 17 and
  Android SDK, no root) and its section in the VM's `RECIPE.md`. Reusable for
  a remote build machine; GitHub Actions already covers remote builds.
- Scenarios (owner's rule, 2026-11-05): `scenarios/`, every commit names
  the ones it touches, release notes list them.
- Switching keys (il-005): Studio → Keys and behaviours, with a search;
  toggle / once / word / timed per key, `~` variants to place.
- App keys (il-004): `app:<package>[?extras]` on any key opens that app
  (Termux, the Remote Client, …); picked in layout mode → "Open an app…".

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

- **Release signing (owner's priorities, NOTE-inputlayer-20261101-2245 in
  Studio-Briefing).** The release key is the owner's to create and hold.
  A new core and dropping the debug tag are long-term; builds stay signed
  with the debug key until then.
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
- **App icon**: the owner wants a new one (to be designed elsewhere, then
  added here).

## Undecided

- Grammar scopes (Verb, Noun, Adjective, Adverb, Name), e.g. "the last
  verb of a sentence": the dictionaries have no word types, so this needs
  a part-of-speech tagger. Proposal: a small tagger trained once on
  Universal Dependencies (English first, ~95% right, runs on the phone,
  no internet), maybe run on the desktop to update suggestions. Not now
  (decided 2026-11-01).

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

## Bug log

### Build 62: a keyboard row grows without end (builds 2–61)

**Symptom** (owner, 2026-11-01): while adjusting heights, the asdf row kept
growing, and grew again every time the keyboard opened. Input Layer was
unusable.

**Cause.** It was not a measure/layout loop: `Theme` derives the row height
from the screen size and the settings, never from a view's measured height.
It was a **repeat timer that outlived its key**:

1. The height key's `+` / `−` repeat while held (`ComfortKey.run`).
2. Every repeat saves the `changeheight` module. The preference change
   refreshes the config, and `ComfortBar.refresh` threw away and rebuilt all
   its keys, the held one included.
3. The removed key never saw the finger lift, and `run()` re-posted itself
   *after* performing the action, even when the key had been removed. The
   timer kept firing for as long as the keyboard process lived, hidden or
   not, saving a bigger row each time (up to the old clamp of 3×).
4. When the keyboard was shown again, the selection was reset to "none", so
   the same stray timer then grew the whole keyboard (up to 100 %). The
   timer also undid any attempt to shrink things back.

**Fix.**

- `ComfortKey` repeats only while the finger is on it and the key is
  attached; it stops on detach, and re-posts only if both still hold.
- `ComfortBar.refresh` relabels the keys instead of rebuilding them when they
  are the same, so a held key keeps working smoothly.
- `Heights` (new) holds all the height math. A step reads the stored module
  only, applies once, and saves nothing at the edge of a range.
  Ranges: rows 0.5–2×, keyboard 10–60 % portrait / 20–65 % landscape,
  bars 12–64 dp (or 0, hidden), bottom margin 0–48 dp.
- **Repair** (`Heights.check`, on keyboard start, Studio start and keyboard
  hide): each value of each `changeheight` module that is outside its range
  goes back to the module's last sane copy (`studio_heights_last_good`), or
  else to the default. Every other field and every other setting are kept;
  no app data is cleared.
- **Undo**: Back undoes the last height change or move (a held key counts as
  one step), then leaves the container selection, and only then hides the
  keyboard. Long Back, or ↕ ↙, undoes everything since the keyboard was
  shown. ↕ ↖ undoes one step. Hiding the keyboard keeps the changes.
- Tests: `HeightsTest` (steps settle at the range ends, repair to the last
  good value or the default, other fields kept, undo snapshots).

## Module reference (current)

Every module is a JSON file with `module` (its type), `name` and `version`.

```json
{ "module": "keymap", "name": "mine", "version": 1,
  "layouts": { "Input Layer QWERTZ": {
      "q": { "ne": "" },
      "enter": { "s": "config" } } } }
```

`keymap` (edit mode, edit key): per layout name, per key (the name of its tap in
the layout), per direction (`c n ne e se s sw w nw`), the key put there;
`""` empties it. `width` is the key's width in key units (0.5–5); `split`
cuts it in two, the right half tapping that key. A layout's `_rows` (edit
key) is its arrangement: rows of key ids, top to bottom; keys named there
but not in the layout are added, keys left out are removed.

```json
{ "module": "changeheight", "name": "compact", "version": 1,
  "keyboard_percent": { "portrait": 30, "landscape": 45 },
  "bar_dp": 26, "comfort_bar_dp": 26, "margin_bottom_dp": 4,
  "rows": [0.8, 1.0, 1.0, 1.0, 0.9] }
```

`edit_strip_dp` and `selection_bar_dp` set the edit strip and selection
bar (0 hides them); `order` lists the containers top to bottom (`comfort`,
`selection`, `suggestions`, `keyboard`, `edit`). `rows` are height factors, top to bottom (the height key changes them one
row at a time), 0.5 to 2. Values out of range are repaired on start (see
the Bug log, build 62). `comfort_bar_dp: 0` hides the comfort bar.

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
