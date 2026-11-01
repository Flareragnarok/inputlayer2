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

Contents: [Install](#install-and-update) ·
[Layout](#the-layout) · [Keys](#gestures-on-the-keyboards-keys) ·
[Comfort bar](#comfort-bar) · [Edit strip](#edit-strip) ·
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

Top to bottom by default (each part can be resized and moved, see the
comfort bar's ↕ key):

| Part | What it is |
|---|---|
| **Comfort bar** (26dp) | tiny keys: copy/paste, cursor, height ↕, language |
| **Selection bar** | only shown when it has something: the preview of typing in format, or the editor of a Memory / clipboard item |
| **Suggestion bar** (28dp) | words only; see-through **Clipboard** icon bottom-left, **Memory** icon bottom-right; the Clipboard / Memory pane opens in its place |
| **Keyboard** | upstream's layouts, with extra swipes |
| **Edit strip** (28dp) | eight keys to select, format and restyle text |

All small keys work like keyboard keys: **tap** for the main action,
**swipe** toward one of 8 directions for the others (the faint labels on
the key's edges).

## Gestures on the keyboard's keys

| Key | Tap | Swipes added by Input Layer |
|---|---|---|
| **Space** | space | ↓ reset all nests · ← save the nests as a nest config · ↑ next nest config · → cursor right (upstream) |
| **Backspace** | ⌫, or ⌦ in forward mode (its label shows which) | ← **apply** Verse's look as picked (✦) · ↑ switch ⌫ / ⌦ · ↓ cut the selection (goes to the clipboard history) |
| **Arrow key** (next to space) | Sector's step in the ←→ direction (label ⇢ / ⇠) | arrows (upstream) |
| **Shift** | shift | ← **apply** View's format as picked (¶) · ↑ Caps Lock · → Compose (e.g. `a` `'` → á) |
| **Ctrl** | ctrl | ↖ undo (Ctrl+Z) · ↗ redo (Ctrl+Shift+Z) |

Upstream's layout switching on space ↑/↓ moved to the comfort bar's
language key; its left cursor slider is replaced (the arrow keys move the
cursor); the forward delete on backspace ↗ is retired.

## Comfort bar

| Key | Tap | Swipes |
|---|---|---|
| **Copy** | copy | ↖ cut · ↗ paste · ↙ select all |
| **Cursor** | – | ← → ↑ ↓ move the cursor (repeat while held) |
| **↕ Height** | picks a container to change: comfort bar → suggestions → keyboard → edit strip → selection bar → each keyboard row → off (outlined in blue) | ↑ ↓ make it taller / shorter · ← → move it up / down in the stack (containers, not rows) |
| **Language** | the suggestions' language (`EN`; `⬇ EN` when its dictionary is missing, then it offers the download) | ↙ next keyboard layout · ↘ system keyboard picker |

The keys can be chosen with a `comfortbar` module. Undo/redo left the
comfort bar for Ctrl's corners (the editing actions it used did nothing in
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

- **Clipboard**: history of copied text on the left third (content type
  filters, Clear), pins on the right two thirds. History is off by
  default upstream: one tap turns it on.
- **Memory**: your items by category: **Text, Links, Mails, Snippets,
  Symbols, Images, Files** and your own (added in Studio). Everything you
  copy is kept automatically under its content type, except copies from
  password fields and copies marked sensitive (Android 13+); a switch in
  Studio turns it off.
- **⟲ History** (right end of Memory's categories): what was copied, one
  record per phone restart; delete one item, one record or all (tap
  twice). Items you saved or moved yourself are never deleted with the
  history.
- **Tap** an item to type it; **long-press** for: Edit, Pin / Unpin, Save
  to Memory (likely category first), Copy to clipboard, Move to, Remove /
  Delete.
- **Edit**: the selection bar expands into an editor holding the item; the
  keyboard types into it instead of the app (keys, ⌫/⌦, arrows, copy/paste,
  the edit strip, Specs and Verts). ✓ saves (an edited clipboard entry
  becomes the newest entry), ✕ cancels.

Memory and the clipboard history file live in the app's private
(credential-protected) storage: readable only once the phone is unlocked.

## Suggestions

- 28dp bar, words only (the language switch is on the comfort bar).
- **Learns your words**: finished words are counted; your frequent words
  come first, words not in the dictionary are suggested once typed twice,
  words written in typing in format are suggested as written (typing `ob`
  offers `oBAmA`).
- **With nothing typed yet**, the words that usually follow the previous
  one (learned from you only; it starts empty).
- Never learned in password fields; Studio → Suggestions has *Learn my
  words* and *Forget learned words*.

## Studio screen

Settings → Studio (or from keys that open it):

- **Updates**: installed build, newer builds dropdown + Install, past
  builds dropdown + Restore.
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
  "bar_dp": 26, "comfort_bar_dp": 26, "edit_strip_dp": 28,
  "selection_bar_dp": 26, "margin_bottom_dp": 4,
  "rows": [0.8, 1.0, 1.0, 1.0, 1.2],
  "order": ["comfort", "selection", "suggestions", "keyboard", "edit"] }

{ "module": "comfortbar", "name": "mine", "version": 1,
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
to bottom, `order` of the containers. `comfortbar`: `c` is the tap, `n ne
e se s sw w nw` the swipes; actions `left right up down select_all copy
cut paste undo redo clipboard dictionary layout keyboard_picker
select_container height_up height_down move_up move_down studio
switch:<type>`. `snippets` and `theme` are reserved, not used yet.

## Building and releasing

- **On GitHub** (the remote build machine): `.github/workflows/studio-release.yml`
  builds every push to `studio` and publishes `build-N` (N = highest
  existing build + 1, so failed builds don't use a number); it refuses to
  publish without the `DEBUG_KEYSTORE` secret, as builds signed with
  another key can't update each other. On failure it writes the errors as
  annotations. `studio-restore.yml` (run by hand) republishes a past build.
- **Locally**: `android-build-setup.sh` (kept with the VM's recipe)
  installs JDK 17 and the Android SDK/NDK per user, without root; then
  `./gradlew clean test assembleDebug`. Keep `debug.keystore` (gitignored)
  to sign like the published builds.
- On the build VM, Gradle can miss newly added files: build with `clean`.
  GitHub's job logs aren't reachable from there; read failures through
  the check-run annotations.

## History

The fork started on 2026-10-31 from Unexpected Keyboard 2.1.0, from a
request to remove or shrink the suggestion bar and to allow custom
colours. Main steps (details per build in [ROADMAP.md](ROADMAP.md)):

1. **Foundation** (builds 1–5): own app id and name *Input Layer*
   (renamed from "Unexpected Keyboard Studio"), Studio screen, modules,
   in-app updates modelled on Obtainium, GitHub releases and restore.
2. **Bars** (2–10): comfort bar (from applets to tiny keys with swipes,
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
   place, editing items with the keyboard, icon navigation.

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
- **Comfort bar editor** in the app; **theme** (colours) and **snippets**
  modules.
- **Language**: auto-detecting the language among installed dictionaries,
  mixing two languages' suggestions (deferred).
- Store texts in `fastlane/` still say "Unexpected Keyboard" (F-Droid only).
- The original task's final report `DONE-keyboard.md` in the
  Studio-Briefing Drive folder is still to write.

## Open questions

- How grammar scopes should work in practice (tagger on the phone or on
  the desktop, which languages).
- What Sector's free ↓ swipe should do.
- Corner icons disappear with the suggestion bar when suggestions are
  off: move them to the comfort bar then?
- The comfort bar and edit strip in the split layout (tablets, landscape)
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
- The item editor draws its own cursor (`▏`); keyboard windows can't show
  the system's.
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
