# Input Layer: what was built (as of build 86, 2026-11-06)

Written 2026-11-08 by the VM Claude from the repo (ROADMAP.md, README.md,
scenarios, commits, the GitHub releases) for the owner and the laptop
Claude. One line per feature: **name**: what it does · state · since.
States: done (built, published) · partial · planned · dropped. "Done" means
built and published, never verified: only the owner verifies (scenarios).

## Now

- **Newest build**: 86 (2026-11-06, beta). Newest **stable**: 61. Builds 75-86
  are betas awaiting the owner's review (Drive: REVIEW-20261106-inputlayer-remotelayer.md).
- **Install / update**: install once from the releases page; then Studio →
  Check for updates lists newer builds (stable only, unless *Beta builds* is
  on) and installs them; Past builds → Restore republishes an older build
  under a new number (Android never installs older over newer).
- **Signing**: the debug key (CI secret DEBUG_KEYSTORE), app id
  `io.github.flareragnarok.inputlayer` with the `.debug` suffix on published
  builds. The release key is the owner's to create and hold (NOTE-inputlayer-20261101-2245);
  until then every build is debug-signed, so Android shows it as a debug app.
- **Open scenarios**: il-004 … il-008, all open (none verified by the owner yet).
- **Open bugs**: none logged after build 62's endless-row fix. Known limits:
  no direct downgrade (restore instead); restoring a build older than 5
  brings back its old Studio screen; modules are settings only, never code.

## Foundation and app

- **Own app id and name, installs next to Unexpected Keyboard** · done · 1
- **Studio screen** (modules import/export/use/delete; in-app updates) · done · 1
- **App icon opens Studio** (buttons to Android's keyboard list and Legacy settings) · done · 56
- **Legacy settings** (upstream's settings unchanged, out of the main screen) · done · 53
- **Updater**: newer builds dropdown, past builds restore, reads up to 300 releases sorted by number · done · 5, 19, 25
- **Beta and stable builds**; the updater points at a newer beta instead of saying "newest" · done · 60, 63, 64
- **Crash reports** kept on the phone (error, place, build; never typed text), copy/clear · done · 59
- **Modules** (`changeheight`, `keymap`, `layout`, quicksbar …): settings as files, never code · done · 1 onward
- **Scenarios** + scenario ids in release notes · done · 75
- **New core / engine rewrite** · planned (long-term, owner) · see the end

## Layouts and keys

- **Input Layer's own layouts**: AZERTY, QWERTY (Gboard-shaped, digits/accents on swipes, Gboard's two symbol pages) · done · 57
- **Input Layer QWERTZ**; "system" picks ours only where the language fits · done · 58
- **Our bottom row on every layout** (no Ctrl / Fn / Compose) · done · 61
- **Owner's defaults**: ?123 and Shift swapped, Ctrl on ?123 ↓, ?123 ¼ wider, backspace ¼ tighter · done · 67-68
- **Split backspace** ⅔ ⌫ / ⅓ ⌦ (⌦ ↓ switches key repeat) · done · 70, 72
- **Backspace ↑ toggles** ⌫ / ⌦ · done · 38
- **Backspace ↓ cuts** the selection into the clipboard history · done · 17
- **Undo / redo that work** (Ctrl ↖ / ↗) · done · 39
- **Arrow key by the space bar** = Sector's step; Shift ↑ Caps Lock, → Compose · done · 36
- **App keys** `app:<package>[?extras]`: any key opens another app, the keyboard comes back up there; app search · done · 76-77, 84 (il-004)
- **Hold a key to edit it** (key repeat off by default) · done · 69

## Arranging (the parts the Remote Client distills, docs/design-lineage-inputlayer.md there)

- **Layout mode** (quickequip ⇄): tap a key → key editor: tap or any swipe, searchable picker of every key of every layout + special keys, Music, Emoji, any text · done · 65-66
- **Key editor**: swap whole keys, width ±¼, split / join, back to the layout's own; titled by its quick path · done · 67-68, 85
- **keymap module**: the owner's edits stored per layout and applied after everything else · done · 65
- **Quickedit** ↕: layers of focus (keyboard → bars → rows → keys), tap to select, ↑↓ size, ←→ order, menu (add key, split/join rows, remove, edit, arrangement back) · done · 9, 27, 74
- **Heights**: per container and per row, clamped ranges, repair on start, Back undoes one step, long Back all · done · 2, 62
- **Saved arrangements**: quick language ↓ saves "Layout N", → picks one · done · 71
- **Layout editor** (Studio): whole keyboards drawn, rows/keys/widths/gaps/heights, saved as `layout` modules, `layout:<name>` keys switch to them · done · 79 (il-006)
- **Keys and behaviours** (Studio): a key search; every switching key gets a behaviour (toggle, back after one use, at word end, after N s) · done · 78 (il-005)
- **quickedit : add row** · planned (owner: "might, in the future")
- **Resize mode** (drag a container's edge, save as a named module) · planned
- **Quicksbar editor** (place quicks and swipes in the app, no JSON) · planned

## Quicksbar and names

- **Quicksbar** above the suggestions: tiny quicks with a tap and up to 8 swipes · done · 2-3, 9
- **Order**: quickequip ⇄ · quick suggestions · quick cursor (center) · quick language · quickedit ↕ · done · 73, 82
- **Quickequip**: tap edit mode, ↑ copy, ↓ key repeat, ↗ backspace split; keyboard picker on ⌦ ↓ · done · 61, 71
- **Quick language**: shows the layout (FR, DE…), next/previous/list; Studio → Languages picks the set · done · 61
- **Quick names**: every action is a quick, named by function (quickfunction, quicktoggle, pageswitch, quickopen, quickedit, quickequip, quickbehaviour); legacy key names still work · done · 80-83, 86 (il-007, il-008)

## Editing text (edit strip)

- **Edit strip** under the space bar: Scope, Spec, Sector, View, Verse, Vert, direction keys · done · 14-16, 19
- **Scope** (Word, Sentence, Line, Paragraph, All; nests Text/Custom) · done · 14, 20
- **Sector** (selector): excl / incl / desel modes, tap steps, ←→ move by unit, grows to whole units · done · 18, 22-24, 29, 35, 37
- **View** (markup per app: Markdown, Discord, Reddit, WhatsApp, Telegram, HTML, BBCode, none) · done · 14, 19-20
- **Verse** (look: case incl. snake_case/camelCase/aLtErNaTiNg, unicode bold/italic/mono/script/fraktur/…, zalgo) · done · 19-20, 42
- **Apply**: Backspace ← applies Verse's look, Shift ← applies View's format · done · 48-49
- **Typing in format** (Scope ↓ starts, Scope ↑ pushes; on a range too; previewed) · done · 21-34
- **Spec and Vert as lenses and operations** (atomic selection steps; a Vert operates once on that scope); macros saved and replayed · done · 31-32, 43
- **Pinned selection** · dropped · 26 → dropped in 28 (unintuitive)
- **Vert on every match in the field** · planned
- **Grammar scopes** (verb, noun …) · undecided (needs a part-of-speech tagger)

## Clipboard and Memory

- **Clipboard / Memory pane** in the suggestions' place, corner icons navigate · done · 11, 44-47
- **Memory keeps every copy** by content type (Text, Links, Mails, Snippets, Symbols, Commands, Output, Logs, Code), never from password fields · done · 12, 41, 51
- **History** per phone restart; delete item/record/all; saved items never deleted · done · 13
- **One line per item, ✎ edit in a full-pane editor; Keep? for 1000+ characters; three columns** · done · 50, 52
- **Images and files in Memory**, adding files from storage · planned (next)
- **Adding Memory categories from the pane** · planned (next)
- **Snippets module** (one-tap inserts) · planned

## Suggestions

- **Suggestion bar 28 dp, suggestions only**; quick suggestions switches their language · done · 4, 6, 10
- **Learns from typing** (your words first, new words after two uses, never from password fields; off switch, forget) · done · 40
- **Language and suggestions, rethought** · planned (undecided how)
- **Dictionaries' license** (upstream word lists state none) · undecided

## Looks

- **Theme module** (own colours) · planned
- **New app icon** · planned (designed elsewhere)
- **Store texts in fastlane still say Unexpected Keyboard** · planned (only for F-Droid)

## The planned engine rewrite (long-term, owner's priority list)

Input Layer still runs on Unexpected Keyboard 2.1.0's engine: `Keyboard2`
(the input service), `Keyboard2View` and `Pointers` (drawing, touch,
swipes), `KeyboardData` / `KeyValue` / `KeyValueParser` (layouts and keys),
`Config` and `Theme`. Input Layer's own work sits beside it in
`juloo.keyboard2.studio` (Quicksbar, EditStrip, MemoryStore, Arrange,
FocusLayout, Heights, Behaviours, LayoutEditorActivity …). What the owner
has said about the rewrite (via the laptop Claude): the inherited engine is
unstable and slow to edit, its swipes are not comfortable for a power user;
Memory, the clipboard, Sector, Verse and the quickedit need a better
foundation; the app should be untraceable to Unexpected Keyboard, trusted,
and update without a security prompt (a release key); long-term, keyboards
as always-present "apps" shown wherever wanted, several at once. A rewrite
would replace the engine classes above and move the studio parts onto the
new core. Not designed yet; the scope is the owner's to set.
