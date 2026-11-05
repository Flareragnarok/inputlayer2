# Input Layer

A fork of [Unexpected Keyboard](https://github.com/Julow/Unexpected-Keyboard)
(GPL-3.0, by Jules Aguillon and contributors), turning it into a keyboard
with an editing layer: tools to select, format, restyle and replay edits,
a clipboard and Memory that keep what you copy, suggestions that learn
from you, and every part of the keyboard resizable and movable.

This README is the reference kept between work sessions: what Input
Layer does, how it is built, its history, the ideas still open and the
questions not yet settled. [ROADMAP.md](ROADMAP.md) has the build-by-build
log.

## Names

The owner's terms (build 73); code, docs and chats use these.

| Term | What it is |
|---|---|
| **Quicksbar** | the bar of quicks at the top. Left to right: quickequip, quick suggestions, quick cursor, quick language, quickedit |
| **Quick** | any action a key, a swipe or the quicksbar can call; named by its function (see [Quick names](#quick-names)). The quicksbar's buttons are quicks too: a tap and up to eight swipes each |
| **Quickequip** ⇄ | customizes the layout: tap edit mode, ↑ copy, ↓ key repeat on/off, ↗ backspace split on/off |
| **Quick suggestions** | the suggestions' language |
| **Quick cursor** | in the center: ← → ↑ ↓ move the cursor |
| **Quick language** | DE / FR / RU…: tap next language, ← previous, ↑ list, ↓ save the key arrangement as a layout, → pick a saved layout |
| **Quickedit** ↕ | right end: tap cycles the **layer of focus** (whole keyboard → bars → components → items); tap an element to select it; ↑ ↓ size, ← → order, ↗ more, ↖ undo, ↙ undo all |
| **Layers of focus** | **whole** keyboard (everything in proportion) · **bars** (one part) · **components** (a keyboard row, or a bar) · **items** (one key) |
| **Parts** | the stacked areas the quickedit works on: quicksbar, selection bar, suggestion bar, keyboard, edit strip |
| **Edit mode** | the quickequip's tap: tapping a key opens the key editor |
| **Key editor** | Studio's screen for one key: tap, 8 swipes, width, split, swap. Also opened by holding a key |
| **Saved layout** | a key arrangement, saved with quick language ↓, picked with quick language → |
| **Tap / swipe / hold** | short press · toward one of ↑ ↗ → ↘ ↓ ↙ ← ↖ · long press |

## Quick names

Owner, 2026-11-06 (il-008): every quick is named by its function,
`<category> : <what> : <detail>`. Studio (Keys and behaviours, the key
picker, the layout editor) shows and searches these names; layout files and
saved settings keep the short legacy names, and either name works wherever a
key name is asked for (`quickfunction:copy` or `copy`). A quick name loads to
the very same key, so a layout written in quick names types exactly as fast.

| Category | What it holds | Examples |
|---|---|---|
| **quickfunction** | the default: input and plain functions; a key's properties under it | `quickfunction : copy` · `: paste` · `: type letter : a` · `: undo` (Ctrl+Z) · `: undo : android` · `: backspace : direction` |
| **quicktoggle** | switching layouts | `quicktoggle : layout : backspace` (split) · `: layout : next` / `previous` / `list` · `: layout : numbers` (123+) · `: layout : greek` · `: layout : <your layout>` · `: keyboard : next` · `: module : theme` |
| **pageswitch** | pages of the same layout | `pageswitch : symbols` (?123) · `: symbols more` (=\<) · `: letters` (ABC) |
| **quickopen** | opening | `quickopen : app : com.termux` · `: layout editor` · `: keys and behaviours` · `: studio` · `: emoji` · `: clipboard` · `: settings` · `: keyboards` |
| **quickedit** | the quickedit's work | `quickedit : focus` · `: resize : +` / `−` · `: move : up` / `down` · `: undo` · `: undo all` · `: menu` |
| **quickequip** | equipping keys | `quickequip : layout current` (edit mode: tap a key to edit it) · `: arrangement : save`; holding a key with long press set to equip |
| **quickbehaviour** | key behaviours | `quickbehaviour : key behaviour : long press` (repeat ↔ equip) |

Behaviour variants keep their mark: `pageswitch : symbols ~once`. Studio →
Layout editor → Open → "Convert a built-in layout to quick names…" saves a
built-in layout written in quick names, plus "… legacy", a replica with the
legacy names.

Contents: [Install](#install-and-update) ·
[Layout](#the-layout) · [Keys](#gestures-on-the-keyboards-keys) ·
[Quicksbar](#quicksbar) · [Edit strip](#edit-strip) ·
[Typing in format](#typing-in-format-scope-write) ·
[Spec and Vert](#spec-and-vert) · [Clipboard and Memory](#clipboard-and-memory) ·
[Suggestions](#suggestions) · [Studio](#studio-screen) ·
[Modules](#modules) · [Building](#building-and-releasing) ·
[History](#history) · [Ideas](#ideas) · [Open questions](#open-questions) ·
[Limits](#known-limits) · [License](#license)

## Install and update

Every push to the `studio` branch is tested, built and published by GitHub
Actions as release `build-N`. Install the APK from
[the latest release](https://github.com/Flareragnarok/inputlayer2/releases/latest)
once; after that, Settings → **Studio → Check for updates** lists newer
builds in a dropdown and installs them (Android asks to confirm). Past
builds are in a second dropdown: **Restore** republishes one's code as a
new build number, since Android never installs an older version over a
newer one. The app id is `io.github.flareragnarok.inputlayer` (`.debug`
for the debug builds published), so it installs next to Unexpected
Keyboard.

## The layout

Top to bottom by default (each part can be resized and moved with the
quickedit ↕):

| Part | What it is |
|---|---|
| **Quicksbar** (26dp) | quicks, left to right: **quickequip** ⇄, **quick suggestions**, **quick cursor**, **quick language**, **quickedit** ↕ (see Names) |
| **Selection bar** | only shown when it has something: the preview of typing in format |
| **Suggestion bar** (28dp) | words only; see-through **Clipboard** icon bottom-left, **Memory** icon bottom-right; the Clipboard / Memory pane opens in its place, with its own bar on top |
| **Keyboard** | Input Layer's own layouts (AZERTY, QWERTY), or upstream's under Legacy |
| **Edit strip** (28dp) | eight keys to select, format and restyle text |

All small keys work like keyboard keys: **tap** for the main action,
**swipe** toward one of 8 directions for the others (the faint labels on
the key's edges).

Names (owner, build 71): the **quickequip** is the quicksbar's left end
⇄, it customizes the layout; the **quick language** is the right end, it
switches languages and saved layouts; the **quick suggestions** next to it
sets the suggestions' language.

The **quickequip** ⇄: tap **edit mode** (below), ↑ copy, ↓ key repeat
on/off (↻ on: backspace, arrows… repeat; ✎ off: holding a key edits it),
↗ backspace split on/off (⌫⌦ / ⌫). Cut is backspace ↓, select all ⌦ ←.

The **quick language**: tap next language, ← previous, ↑ list; **↓ saves the
key arrangement** as "Layout N" (a `keymap` module; edit mode goes on with
the new one, the saved one stays as it was), **→ picks a saved layout** (or
"No changes", the layouts' own).

**Edit mode** with ⇄. While it's on (the keyboard is outlined in
green, the key shows ⇄ ✓), tapping a key opens it in Studio instead of
typing. The editor shows the key's tap and 8 swipes; pick one, then
**Add key…** / **Replace with…** (a list of every key of every language's
layout, every special key, music notes and all emojis, grouped Latin,
Greek, Cyrillic, other scripts, Music, digits and symbols, Emoji, special
keys; alphabetical inside, emojis in the emoji pane's order; with a
search), **Type text or emoji…** (any text on the key: 🎶, ♪~(´ε` ), a
word), **Remove**, or **Back to the layout's own**. **⇄ Swap the whole
key with…** trades everything two keys hold, tap and swipes. **↔ Width**
makes the key a quarter wider or narrower (it shows the row's total next to
the widest row's: keep them equal, or every key gets narrower). **✂ Split
into two keys** cuts it in halves; the right one taps forward delete or any
key picked, and is changed like any key by tapping it in layout mode;
**⧉ Join** puts it back. Changes apply to the
layout shown only, and are kept in a `keymap` module ("mine"). Back or ⇄
again leaves layout mode. Example: ⚙ from Q's corner to Enter's ↓: open
Enter, ↓, Add key, search "config"; then open Q, the ⚙ corner, Remove.

**Holding a key opens it in the key editor** (build 69), the same as
tapping it in edit mode; the key isn't typed. Nothing repeats while held,
unless key repeat is on (quickequip ↓). Modifiers (Shift, Ctrl) still lock
when held.

**The quickedit** ↕ (build 74). Its tap cycles the layer of focus; the
label shows it (↕ all, ↕ bars, ↕ rows, ↕ keys; then the selection, ↕ row
2, ↕ key). While a layer is on, **tap the element itself** to select it:
the keyboard and the bars don't type, they select (only the quickedit
still works as a key). Outlines: the whole keyboard in blue; the selected
bar; the selected row in blue with each of its keys thinly outlined (its
items), the selected key in orange.

| Layer | ↑ ↓ | ← → | ↗ more |
|---|---|---|---|
| Whole | the keyboard taller / shorter, the bars in proportion | – | – |
| Bars | the bar taller / shorter | its place in the stack | – |
| Components | the row taller / shorter | the row up / down (its height comes along) | add a key at the end · split the row in two · join with the row below · arrangement back to the layout's own |
| Items | the key a quarter wider / narrower, **the row keeping its width**: the space comes from the key next to it, or from the whole row (each key gives in proportion to what it has above its smallest) | the key left / right | size from: next key / whole row · add a key after it (Studio's picker) · edit its tap, swipes, split · remove it · split the row before it · arrangement back to the layout's own |

Back and ↖ undo one step (each menu action is one), ↙ all since the
keyboard was shown; Back with nothing to undo leaves the focus. The
arrangement (rows, order, added and removed keys) is kept per layout in
the `keymap` module (`_rows`), so it goes with saved layouts.

**Resizing** with the quickedit ↕, outside the layers (as before): tap selects a part or a row, ↑ / ↓ resize it (hold to
repeat), ← / → move it. **Back** undoes the last change, one gesture at a
time, and then leaves the selection; long Back or quickedit ↙ undoes everything since
the keyboard was shown, quickedit ↖ one step. Sizes stay within sane ranges (rows
0.5–2×), and stored sizes out of range are repaired when the keyboard
starts, from their last sane value or the default (build 62; see ROADMAP's
Bug log).

## Input Layer's layouts

**Input Layer AZERTY**, **QWERTZ** and **QWERTY** (`srcs/layouts/il_*.xml`)
are shaped like Gboard's French, German and English keyboards (QWERTZ:
ü ö ä on their own keys, S ↖ ß). The language's default ("system") picks
ours where they cover the language: French → AZERTY, German → QWERTZ,
English → QWERTY. Other languages keep their own layout, since ours lack
their letters. Upstream's layouts are listed after ours as
*Legacy · …*.

- **Letters**: digits on the top row's ↗ swipes (A/Q 1 … P 0); accents on
  the vowels' other corners (E: ↖ é, ↙ è, ↘ ê, ↓ ë; A: à â æ; U: ù û ü;
  I: î ï; O: ô œ ö; Y: ÿ), C ↖ ç, N ↖ ñ.
- **Bottom row** on every layout, upstream's included (it replaces
  `res/xml/bottom_row.xml`), no Ctrl / Fn / Compose: `?123` (↖ undo, ↗ redo) · `,`
  (↑ emoji) · space · `.` (↑ `?`, ↗ `!`, ↓ `:`) · Sector's arrow key ·
  Enter. Since build 67, **?123 and Shift trade places** on every layout
  (Shift bottom-left, ?123 left of the third letter row), and on the
  symbols pages the key back sits where ?123 was. Since build 68, ?123 is a
  quarter key wider and backspace as much tighter. Since build 70 a
  backspace 2 keys wide or more (QWERTZ) is split: ⌫ with its swipes (two
  thirds), then at the edge ⌦ forward delete (one third), ↓ key repeat
  on/off, ← select all (quickequip ↗ switches the split). Each part's width
  can be changed on its own (hold it, ↔ Width).
- **?123** opens the first symbols page, like Gboard's: digits;
  `@ # € _ & - + ( ) /` (€ ↗ $); `=\<` `* " ' : ; ! ?` ⌫.
- **=\<** opens the second page: `~ \` | • √ π ÷ × ¶ ∆`;
  `£ ¥ $ ¢ ^ ° = { } \`; `?123` `% © ® ™ ✓ [ ]` ⌫; `<` and `>` around
  the space bar. **ABC** goes back to the letters.
- Shift, backspace, space and the arrow key get Input Layer's swipes (see
  below) on these pages too.

## Gestures on the keyboard's keys

| Key | Tap | Swipes added by Input Layer |
|---|---|---|
| **Space** | space | ↓ reset all nests · ← save the nests as a nest config · ↑ next nest config · → cursor right (upstream) |
| **Backspace** | ⌫, or ⌦ in forward mode (its label shows which) | ← **apply** Verse's look as picked (✦) · ↑ switch ⌫ / ⌦ · ↓ cut the selection (goes to the clipboard history) |
| **Arrow key** (next to space) | Sector's step in the ←→ direction (label ⇢ / ⇠) | arrows (upstream) |
| **Shift** | shift | ← **apply** View's format as picked (¶) · ↑ Caps Lock · → Compose (e.g. `a` `'` → á) |
| **?123** (our layouts) | symbols | ↖ undo (Ctrl+Z) · ↗ redo (Ctrl+Shift+Z) · ↓ **Ctrl** |
| **Ctrl** (legacy layouts) | ctrl | ↖ undo · ↗ redo |

Upstream's layout switching on space ↑/↓ moved to the quicksbar's
quick language (right end); its left cursor slider is replaced (the arrow keys move the
cursor); the forward delete on backspace ↗ is retired.

## Quicksbar

**Switching keys and their behaviours** (il-005): Studio → Keys and
behaviours lists every special key with a search. Keys marked ⇆ switch
something (key repeat, backspace direction, backspace split, edit mode, the
number/symbol/Greek pages): pick how each one switches back by default:
toggle, after one use (one key edit for repeat and edit mode, one delete for
the backspace switches, one key typed for a page), at the end of the word,
or after N seconds. A variant name puts a behaviour on one place only:
`studio_toggle_repeat~once`, `il_symbols~word`, `switch_numeric~10s`
(labels get ¹, ʷ, ⏱); quicksbar modules can use `toggle_repeat~once`.

**Layout editor** (il-006): Studio → Layout editor edits a whole
keyboard: open the keyboard as you use it, a saved layout or a blank one;
tap a key to select it and drag its right edge to resize it, tap a row's
free space and drag its bottom edge for its height; move keys (◀ ▶ cross
into the next row), add and remove keys and rows, gaps; Names… sets the tap
and the eight swipes from the key list (search), text, or any key name
(`app:…`, `layout:…~once`). Save, Save as, Undo. A saved layout is shown by a
key `layout:<name>` exactly as drawn (no bottom row added), with the
behaviours of a page (Studio → Keys); "Add to my layouts" puts it in the
quick language's list. A backspace or number variant of your keyboard is a
saved layout plus a `layout:` key (toggle, or back after one key).

**App keys** (il-004): any key direction can open an app. In layout mode
pick the key → direction → "Open an app…", then optional extras for apps
that take them (the Remote Client: `tab=roadmap`, `commands`, `doc`,
`setup`, `status`). The key is named `app:<package>[?name=value&…]`, labelled
↗ and the package's last part (↗term, ↗remo); quicksbar modules can use
the same name as an action. It opens the app as the launcher does (a running
app comes back as it was). Nothing of another app lives in the keyboard.

| Key | Tap | Swipes |
|---|---|---|
| **Quickequip** ⇄ (left end) | edit mode on/off | ↑ copy · ↓ key repeat on/off · ↗ backspace split on/off |
| **Quick suggestions** | the suggestions' language (`EN`; `⬇ EN` when its dictionary is missing, then it offers the download) | – |
| **Quick cursor** (center) | – | ← → ↑ ↓ move the cursor |
| **Quick language** | the layout shown, as a language (`FR`, `DE`, `RU`, `EL`…); tap: the next one | ← previous · ↑ list to pick from · ↓ save the key arrangement · → pick a saved layout |
| **Quickedit** ↕ (right end) | picks a part to change: quicksbar → suggestions → keyboard → edit strip → selection bar → each keyboard row → off (outlined in blue) | ↑ ↓ taller / shorter · ← → move it up / down in the stack (parts, not rows) · ↖ undo · ↙ undo all |

The keys can be chosen with a `quicksbar` module. Undo/redo left the
quicksbar for Ctrl's corners (the editing actions it used did nothing in
many apps).

## Edit strip

`[←→] [Scope] [Spec] [Sector] [View] [Vert] [Verse] [↑↓]`

Keys with two lines show their name (or nest) on top and their current
state at the bottom.

| Key | Tap | → | ← | ↓ | ↑ |
|---|---|---|---|---|---|
| **←→** | flip the selection direction (Sector, the Scope cycle, the arrow key's label) | | | | |
| **Scope** | next scope (Word, Sentence, Line, Paragraph, All, then custom selections) | next nest (Text, Custom) | list | **start typing in format** | **push it** |
| **Spec** | apply the current Spec | next Spec | list | save the recorded Spec | Spec editor |
| **Sector** | cycle the mode: **excl → incl → desel** (shown at the bottom) | move by a unit (the selection if any, else the cursor) | same, backward | – | select the unit at the cursor, or deselect |
| **View** (format) | pick the next format of the app's syntax, plain in between | next syntax (remembered per app) | list (picks) | pick plain, back to the app's syntax | pick plain |
| **Vert** | apply the current Vert | next Vert | list | save the recorded Vert | Vert editor |
| **Verse** (look) | pick the next look of the nest | next nest | list (picks) | pick plain letters, back to the Serif nest | pick plain letters |
| **↑↓** | flip the cycling direction (View, Verse) | | | | |

**Sector's step** (the arrow key's tap): with nothing selected, selects
the scope's unit at the cursor; then, in the arrow's direction:
- **excl**: jumps to the next unit;
- **incl**: adds the next unit, with what surrounds it (the space after a
  word, a line's line break, a custom selection's delimiters); switching
  to incl grows a partial selection out to whole units;
- **desel**: removes units from the end opposite the arrow's tip, so the
  tip goes last, until nothing is selected.

**View** knows Markdown, Discord, Reddit, WhatsApp, Telegram, HTML,
BBCode and None, detected from the app (Threads, Instagram, X, TikTok,
Facebook get None: there, View cycles Verse's looks). It wraps and unwraps
(`**bold**` is told apart from `*italic*`). Custom formats are added to
every syntax.

**Verse** nests: **Case** (UPPER, lower, Title Case, tOGGLE cASE,
snake_case, camelCase, aLtErNaTiNg), **Serif** (𝐛𝐨𝐥𝐝, 𝑖𝑡𝑎𝑙𝑖𝑐, 𝒃𝒐𝒍𝒅 𝒊𝒕𝒂𝒍𝒊𝒄),
**Sans**, **Type** (𝚖𝚘𝚗𝚘, 𝓈𝒸𝓇𝒾𝓅𝓉, 𝔣𝔯𝔞𝔨𝔱𝔲𝔯, 𝕕𝕠𝕦𝕓𝕝𝕖), **Shapes** (ꜱᴍᴀʟʟ ᴄᴀᴘꜱ,
ⓒⓘⓡⓒⓛⓔⓓ, ｆｕｌｌ), **Marks** (s̶t̶r̶i̶k̶e̶, u̲n̲d̲e̲r̲l̲i̲n̲e̲, z̷a̴l̵g̶o̸) and **Fonts**
(saved `font` modules). Every nest but Case starts with *plain*. Looks
convert from plain letters, so they switch freely; snake_case and
camelCase convert into each other, Title splits them back into words.

**View and Verse only pick**: their keys never change the text. Picks are
applied to the selection, or to the scope's unit at the cursor when
nothing is selected:
- **Backspace ←** applies Verse's look to the letters (markers around them
  stay);
- **Shift ←** applies View's format, replacing any format already around
  the text (plain removes it). In apps without markup, where View picks
  Verse's looks, it applies the look.

The text inside the markers stays selected, so a new pick and another ←
replace it in turn.

**Nest configs**: the state of Scope, View and Verse (nests and items) is
saved with space ←, cycled with space ↑, reset with space ↓.

## Typing in format (scope write)

**Scope ↓** starts it, **Scope ↑** pushes it: the cursor goes after the
closing marker, nothing is selected, and the pushed text is done. Until
the cursor moves or something is typed, Backspace ← and Shift ← don't
treat it as the unit at the cursor (select it to change it again). Moving the cursor
out of the text also ends it.

- View's format goes around the text, Verse's look is applied to each
  letter as it is typed (cases look at what came before: `_` for spaces
  in snake_case, a dropped space capitalizes in camelCase...).
- With a selection, the selection is the text: it gets the format and
  look at once and typing continues inside it.
- Picking another look on Verse meanwhile applies to the letters typed
  from then on; a new View pick is for the next typing in format.
- Sector only moves within the typed text meanwhile; changes made to
  units with Backspace ← or Shift ← stay.
- The Scope key lights up; the selection bar previews the result (`✎ …`).
- Its words become suggestions, as written.
- Everything done is recorded: Sector, Scope and ←→ as Spec steps; typing,
  ⌫/⌦, Backspace ← and Shift ← as Vert operations (saved with Spec ↓ / Vert ↓).

## Spec and Vert

A **Spec** is a lens: atomic selection steps giving the scope a Vert works
on. A **Vert** is what is done to that scope, once, as recorded. Word is a
Spec; camelCase is a Vert applied to it. Both are stored as `spec` /
`vert` modules and edited step by step in Studio (Spec ↑ / Vert ↑ open the
editor on the current one).

| Spec step | | Vert operation | |
|---|---|---|---|
| `scope:<label>` | use that scope | `insert:<text>` | typed at the cursor |
| `mode:excl\|incl\|desel` | Sector's mode | `back:<n>` / `del:<n>` | n characters removed before / after the cursor |
| `dir:fwd\|back` | the ←→ direction | `clear` | the scope deleted |
| `step` | Sector's step | `wrap:<open>\|<close>` | inserted around the scope (what Markdown is) |
| `unit` | select the unit at the cursor / deselect | `unwrap` | the scope's format removed |
| `move:next\|prev` | move by a unit | `look:<style>` / `case:<case>` | a look or case on the scope |

- **Applying a Spec** runs its steps from the cursor or selection; its
  settings only last for the run.
- **Applying a Vert** is faithful: on an empty selection, insertions
  still happen (`oBAmA` typed at the cursor); transformations of nothing
  do nothing. Backspace-then-`o` on `a` (`back:1`, `insert:o`) is a
  transformation, different from deleting the selection (`clear`). The app
  gets the result as one edit.
- **Saving simplifies**: of runs of the same setting the last counts;
  settings already in effect and settings at the end go; an `incl` step
  followed by a `desel` step with the arrow the other way remove the same
  unit, so both go (repeated back and forth ends up as nothing); typing
  and removals merge.

## Clipboard and Memory

Tap the **Clipboard** (↙) or **Memory** (↘) icon on the suggestion bar:
the pane opens in the suggestion bar's place, the keyboard staying below.
The open tab's icon lights up blue; tap it again to close, the other icon
to switch.

- **The bar** on top of the pane: the last three copies, cut with `…` (tap
  one to type it); after you tap any item, its whole text on one line
  (scroll it sideways, tap to type it again, ✕ to go back to the last
  three).
- **Every item is one line**: its first words, then `…`. **Tap** types it,
  **✎** edits it, **long-press** opens the menu, Delete first (then Pin /
  Unpin, Save to Memory or Move to, Copy to clipboard).
- **Clipboard**: content type filters on top; the history of copies on the
  left 30% (with Clear), pins on the right. History is off by default
  upstream: one tap turns it on.
- **Memory**: three columns. The left 30% is kept free for now. On the
  right, the list of categories with items (count after each): **Text,
  Links, Mails, Commands, Output, Logs, Code, Snippets, Symbols, Images,
  Files** and your own (added in Studio), with **Keep?** first when big
  copies wait and **⟲ History** last. Tap one: its items show in the
  middle, one per line, alike ones together under a small label (`git`).
  Everything you copy is kept automatically under its content type (see
  *How copies are sorted*), except copies from password fields and copies
  marked sensitive (Android 13+); a switch in Studio turns it off.
- **Keep?**: copies of 1,000 characters or more (Studio → Memory sets the
  size) aren't kept silently. They wait there: ✓ keeps one in the
  history, ✕ drops it; the 20 newest wait.
- **⟲ History**: what was copied, one record per phone restart, each under
  its own title; delete one item, one record or all (tap twice). Items you
  saved or moved yourself are never deleted with the history.
- **Editing** (✎): the whole pane becomes the editor, the keyboard below
  typing into it instead of the app (keys, ⌫/⌦, arrows, copy/paste, the
  edit strip, Specs and Verts). The whole text scrolls and follows the
  cursor (the moving end of a selection); tap in it to place the cursor.
  The keyboard follows the item's cursor and selection meanwhile, not the
  app's. ✓ saves (an edited clipboard entry becomes the newest entry), ✕
  cancels, and so does closing the pane.

### How copies are sorted

By content only, never by the app (Android doesn't say which app copied).
The first rule that matches wins (`ContentType.java`):

| Category | When |
|---|---|
| **Mails** | an email address alone, or a `mailto:` link |
| **Links** | a URL alone |
| **Logs** | most lines have a timestamp and a level (`ERROR`, `W/Tag`, `E ActivityManager:`), or several lines a level, or a stack trace (`Traceback`, `at x.y(…)`, `Caused by:`) |
| **Output** | a terminal's: a prompt line (`$ ls`, `me@host:~$ …`) followed by other lines, `ls -l` rows, or most of 3+ lines in aligned columns |
| **Commands** | every line a command: after a `$`/`#` prompt; a known program (`git`, `adb`, `sudo`, `ls`, `pip`…) with arguments, unless it reads as a sentence (`make sure to…`); `./x`, `~/x`; a lowercase word with flags, `|`, `&&` or `>` |
| **Code** | two code signs or more: lines ending with `{ } ;`, starting with `def`, `class`, `import`, `function`, `return`, `#include`…, `=>`, decorators |
| **Snippets** | other text on several lines |
| **Text / Symbols** | the rest; Symbols when there is no letter nor digit |

**Alike items stay together** in a category: commands by program (`git`,
`adb`, after a prompt, `sudo` and `X=y`), links and mails by domain, logs
by tag or exception, output by the command that made it, code by
language. Groups come in the order of their newest item, and a group of
several starts with what they share (a small `git` label). Text,
Snippets and Symbols stay newest first.

When the rules change (`ContentType.RULES_VERSION`), the history is
sorted again on the next start; items you saved or moved stay where you
put them.

Memory and the clipboard history file live in the app's private
(credential-protected) storage: readable only once the phone is unlocked.

## Suggestions

- 28dp bar, words only (the language switch is on the quicksbar).
- **Learns your words**: finished words are counted; your frequent words
  come first, words not in the dictionary are suggested once typed twice,
  words written in typing in format are suggested as written (typing `ob`
  offers `oBAmA`).
- **With nothing typed yet**, the words that usually follow the previous
  one (learned from you only; it starts empty).
- Never learned in password fields; Studio → Suggestions has *Learn my
  words* and *Forget learned words*.

## Studio screen

**The app's icon opens Studio**, with *Turn on the keyboard* (Android's
keyboard list) and *Legacy settings* at the top. The keyboard's settings
show two entries: **Studio** and **Legacy settings**.
Legacy settings holds all of upstream's settings, unchanged (layouts,
typing, style, vibration, clipboard duration…; in the phone's language,
as upstream translates them). Studio has Input Layer's own (English
only):

- **Languages**: the layouts the quicksbar's quick language switches
  between: phone's language (automatic), Français (AZERTY), Deutsch
  (QWERTZ), English (QWERTY), Русский (ЙЦУКЕН), Ελληνικά. Ticks write the
  same list as Legacy settings → Layouts; layouts added there stay.
  Japanese has no layout yet (kana and kanji need a converter).
- **Updates**: installed build, newer builds dropdown + Install, past
  builds dropdown + Restore. **Beta builds**: every push comes out as a
  beta (a GitHub pre-release); with the switch off (default) only stable
  builds are offered as updates, and Studio names a newer beta when one is
  hidden. Past builds always lists every older build, betas marked. On a beta that works, *Mark build N stable* opens its
  GitHub page: untick *pre-release*. Restores come out stable.
- **Crash reports**: every crash, and every error the app survived (shown
  as a message instead), is kept on the phone with the error, where it
  happened and the build, never typed text; the last 10 stay. *Copy
  reports* puts them on the clipboard to send; nothing is sent by itself.
  Kept in device-protected storage, so crashes before unlocking are kept
  too (`CrashLog.java`).
- **Spec and Vert editor**: pick one, move ↑ ↓ / delete ✕ / add steps,
  New Spec, New Vert, Delete.
- **Edit strip tools**: new custom selection (delimiters or a pattern),
  format (before/after) or font (26 letters, optional capitals and digits).
- **Memory**: keep everything copied (on/off), categories.
- **Suggestions**: learn my words, forget them.
- **Modules**: every saved module, use / stop / export / delete, import a
  file.

## Modules

Settings only, never code (a keyboard sees everything typed). Every
module is a JSON file with `module` (type), `name` and `version`.

```json
{ "module": "changeheight", "name": "compact", "version": 1,
  "keyboard_percent": { "portrait": 30, "landscape": 45 },
  "bar_dp": 26, "quicksbar_dp": 26, "edit_strip_dp": 28,
  "selection_bar_dp": 26, "margin_bottom_dp": 4,
  "rows": [0.8, 1.0, 1.0, 1.0, 1.2],
  "order": ["quicksbar", "selection", "suggestions", "keyboard", "edit"] }

{ "module": "quicksbar", "name": "mine", "version": 1,
  "keys": [ { "c": "copy", "nw": "cut", "ne": "paste" },
            { "c": "dictionary", "sw": "layout" } ] }

{ "module": "selection", "name": "Parens", "version": 1, "open": "(", "close": ")" }
{ "module": "selection", "name": "Tags", "version": 1, "pattern": "#\\w+" }
{ "module": "format", "name": "Angles", "version": 1, "open": "<<", "close": ">>" }
{ "module": "font", "name": "Greekish", "version": 1, "lower": "αβςδεφγηιξκλμνοπθρστυvωχψζ" }
{ "module": "spec", "name": "Spec 1", "version": 1, "ops": ["mode:incl", "step", "step"] }
{ "module": "vert", "name": "Vert 1", "version": 1, "ops": ["back:5", "insert:oBAmA"] }
```

`changeheight`: sizes in dp (`0` hides a bar), `rows` height factors top
to bottom (0.5–2), `order` of the containers. `quicksbar`: `c` is the tap, `n ne
e se s sw w nw` the swipes; actions `left right up down select_all copy
cut paste undo redo clipboard dictionary layout toggle_repeat split_backspace
layouts_save layouts_pick keyboard_picker
select_container height_up height_down height_undo height_revert move_up
move_down layout_mode studio
switch:<type>`. `keymap`: `layouts` → layout name → key (its tap's name) →
direction → key name (`""` empties it), `width` (key units), `split` (the
right half's tap); and per layout `_rows`, the arrangement: rows of key ids. `snippets` and `theme` are reserved, not used yet.

## Building and releasing

- **On GitHub** (the remote build machine): `.github/workflows/studio-release.yml`
  builds every push to `studio` and publishes `build-N` as a pre-release
  (beta) (N = highest
  existing build + 1, so failed builds don't use a number); it refuses to
  publish without the `DEBUG_KEYSTORE` secret, as builds signed with
  another key can't update each other. On failure it writes the errors as
  annotations. `studio-restore.yml` (run by hand) republishes a past build.
- **Locally**: `android-build-setup.sh` (kept with the VM's recipe)
  installs JDK 17 and the Android SDK/NDK per user, without root; then
  `./gradlew clean test assembleDebug`. Keep `debug.keystore` (gitignored)
  to sign like the published builds.
- On the build VM, Gradle can miss newly added files: build with `clean`.
- Android's regex engine (ICU) is stricter than the JVM's the unit tests
  use: a bare `{` or `}` compiles in the tests and crashes on the phone
  (builds 51–53 crashed opening Clipboard / Memory). `ContentTypeTest`
  checks the patterns for it; escape braces in new patterns.
  GitHub's job logs aren't reachable from there; read failures through
  the check-run annotations.

## History

The fork started on 2026-10-31 from Unexpected Keyboard 2.1.0, from a
request to remove or shrink the suggestion bar and to allow custom
colours. Main steps (details per build in [ROADMAP.md](ROADMAP.md)):

1. **Foundation** (builds 1–5): own app id and name *Input Layer*
   (renamed from "Unexpected Keyboard Studio"), Studio screen, modules,
   in-app updates modelled on Obtainium, GitHub releases and restore.
2. **Bars** (2–10): quicksbar (from applets to quicks with swipes,
   key-like look), changeheight modules, smaller suggestion bar, language
   moved off the suggestion bar, corner icons.
3. **Clipboard and Memory** (11–13): two-tab pane, categories, automatic
   keeping of copies by content type, history records per restart.
4. **Edit strip** (14–23): scopes and selector, unicode looks and
   per-app markup, then seven and eight keys in the owner's order and
   names (Scope, Spec, Sector, View, Vert, Verse, two arrows), nests,
   nest configs on the space bar, plain in every nest.
5. **Typing in format** (21–34): typed letters get the look, ranges,
   selections, Scope ↓ / ↑, unit changes kept, Scope lit; from 48 View and
   Verse only pick, Backspace ← applies the look and Shift ← the format, a
   pushed text is left alone.
6. **Keys** (35–39): desel mode, the arrow key's tap for Sector, Shift's
   Caps Lock and Compose, backspace's ⌫/⌦ switch, undo/redo on Ctrl.
7. **Macros** (31–43): first Spec (steps) and Vert (replacement), then
   reworked into lenses of atomic selection steps and operations, with
   simplification and an editor.
8. **Suggestions and Memory** (40–47): learned words and next words,
   Mails, snake/camel/alternating cases, the pane in the suggestions'
   place, editing items with the keyboard, icon navigation; from 50 one
   line per item, the bar of last copies, Memory as rows of categories,
   "Keep?" for big copies and the editor in the whole pane; 51 Commands,
   Output, Logs and Code, alike items grouped.

Decisions taken along the way: View and Verse pick, Backspace ← applies the
look, Shift ← the format (the finished text of typing in format must not be restyled by a pick);
modules never contain code; categories by
content type, never by source app (Android doesn't tell it); pinning a
selection was dropped as unintuitive; upstream's space-bar layout switch
and left slider were given up for nest configs; the part-of-speech tagger
is postponed.

## Ideas

- **Images and files** in the clipboard and Memory, pasted into apps that
  accept them; adding files from storage to Memory.
- **Grammar scopes** (Verb, Noun, Adjective, Adverb, Name; "the last verb
  of a sentence"): needs a part-of-speech tagger (the dictionaries only
  have words and frequencies). Candidate: a small tagger trained on
  Universal Dependencies, maybe running on the desktop and updating the
  suggestions. Postponed.
- **Vert on every match** in the field.
- **Renaming** Specs and Verts; **adding Memory categories** from the pane.
- **Resize mode** by dragging a container's edge; keeping the total height
  when one row grows.
- **Quicksbar editor** in the app; **theme** (colours) and **snippets**
  modules.
- **Language**: auto-detecting the language among installed dictionaries,
  mixing two languages' suggestions (deferred).
- **Japanese**: a kana layout (12-key flick or romaji → kana) and kanji
  conversion; Mozc (BSD) is the open-source converter to look at.
- Store texts in `fastlane/` still say "Unexpected Keyboard" (F-Droid only).
- The original task's final report `DONE-keyboard.md` in the
  Studio-Briefing Drive folder is still to write.

## Open questions

- How grammar scopes should work in practice (tagger on the phone or on
  the desktop, which languages).
- What Sector's free ↓ swipe should do.
- Corner icons disappear with the suggestion bar when suggestions are
  off: move them to the quicksbar then?
- The quicksbar and edit strip in the split layout (tablets, landscape)
  are untested.
- Dictionaries come from upstream's dictionaries repository, which states
  no license: keep, or host our own word lists?
- The old pinned-selection code is still in the edit strip, unused: remove
  it, or reuse it?

## Known limits

- Android installs only newer version numbers: past builds come back
  through Restore, never as a downgrade.
- Some apps don't give the keyboard the field's whole text: typing in
  format and the edit strip see only the text around the cursor there.
- Ctrl+Z / Ctrl+Shift+Z depend on the app; a few keep their own undo.
- The item editor draws its own cursor (`▏`), and a tap places it but
  can't drag a selection (the edit strip and Sector select); keyboard
  windows can't show the system's.
- Copied images and files aren't kept yet.

## License

Input Layer is a modified version of Unexpected Keyboard, changed by
Flareragnarok starting 2026-10-31; the commits on the `studio` branch after
upstream tag `2.1.0` are the changes. Like upstream it is licensed under the
GNU General Public License v3.0 (see `LICENSE`), and so is every change
made here. Parts keep their own licenses: `vendor/cdict` is MIT,
`srcs/layouts` has its own license (`srcs/layouts/LICENSE`) and the compose
data comes from xorg.

The upstream README follows.

---

# Unexpected Keyboard [<img src="https://hosted.weblate.org/widget/unexpected-keyboard/svg-badge.svg" alt="État de la traduction" />](https://hosted.weblate.org/engage/unexpected-keyboard/)

---

⚠️ Google wants to kill the open-source Android community.

See [keepandroidopen.org](https://keepandroidopen.org/) and the [F-Droid blog](https://f-droid.org/en/2025/09/29/google-developer-registration-decree.html).

---

[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
     alt="Get it on F-Droid"
     height="80">](https://f-droid.org/packages/juloo.keyboard2/)
[<img src="https://play.google.com/intl/en_us/badges/images/generic/en-play-badge.png"
     alt="Get it on Google Play"
     height="80">](https://play.google.com/store/apps/details?id=juloo.keyboard2)

Lightweight and privacy-conscious virtual keyboard for Android.

https://github.com/Julow/Unexpected-Keyboard/assets/2310568/28f8f6fe-ac13-46f3-8c5e-d62443e16d0d

The main feature is that you can type more characters by swiping the keys towards the corners.

This application was originally designed for programmers using Termux.
Now perfect for everyday use.

This application contains no ads and is open source.

Usage: to apply the symbols located in the corners of each key, slide your finger in the direction of the symbols. For example, the Settings are opened by sliding in the left down corner.

| <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/1.png" alt="Screenshot-1" /> | <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/2.png" alt="Screenshot-2"/> | <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/3.png" alt="Screenshot-3"/> |
| --- | --- | --- |
| <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/4.png" alt="Screenshot-4" /> | <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/5.png" alt="Screenshot-5" /> | <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/6.png" alt="Screenshot-6" /> |

## Help translate the application

Improve the application translations [using Weblate](https://hosted.weblate.org/engage/unexpected-keyboard/).

[<img src="https://hosted.weblate.org/widget/unexpected-keyboard/multi-auto.svg" alt="État de la traduction" />](https://hosted.weblate.org/engage/unexpected-keyboard/)

## Contributing

For instructions on building the application, see
[Contributing](CONTRIBUTING.md).

## Acknowledgement

The [NLnet foundation](https://nlnet.nl/) funded the work on the spell checking feature.
