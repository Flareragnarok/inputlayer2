# Notes for Claude (read at the start of every session)

## Scenarios (owner's rule, 2026-11-05)

Before any change, read `scenarios/`. Every commit message names the scenarios
it touches (`scenarios: il-003, il-007`). A change that fixes a problem no
scenario describes gets a new scenario first. Never mark a scenario verified;
only the owner does that.

## First thing next session

- 2026-11-06: builds 75–85 and Remote Client build 1 await review and the
  owner's test. Read `REVIEW-20261106-inputlayer-remotelayer.md` in the
  Studio-Briefing Drive folder (what changed, where the risk is, test list).
- Names (owner): the bar is the quicksbar, every action is a quick, named
  by function (README → Quick names). Never write "comfort" or "<x> key"
  for a quick.

- Read `NOTE-inputlayer-20261101-2231.md` and `NOTE-inputlayer-20261101-2245.md`
  in the Studio-Briefing Drive folder. **2245 sets the priorities:**
  - The release signing key is the owner's: they create and hold it.
  - A new core and dropping the debug tag are long-term goals.
  - The debug key stays in use until then.

## Used elsewhere (2026-11-08)

The Remote Client (Flareragnarok/remotelayer-, ~/work/remote-client)
distills its layout model from this app's: default layout + the owner's
edits as an overlay applied last (`keymap`), the quickedit's layers of
focus, hold-to-edit, the searchable key picker, saved arrangements, undo
and repair (ROADMAP 26-27, 62, 65-74; il-006..008). Its
`docs/design-lineage-inputlayer.md` maps each one. When arranging changes
here, check whether it should carry over there.

## Working notes

- `ROADMAP.md` (done / next / later / undecided) and `README.md` (features,
  gestures, names glossary) are the between-sessions reference. Update both
  when things change.
- Docs-only commits use `[skip ci]`, so CI doesn't publish an empty release.
