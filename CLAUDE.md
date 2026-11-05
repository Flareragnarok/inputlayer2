# Notes for Claude (read at the start of every session)

## Scenarios (owner's rule, 2026-11-05)

Before any change, read `scenarios/`. Every commit message names the scenarios
it touches (`scenarios: il-003, il-007`). A change that fixes a problem no
scenario describes gets a new scenario first. Never mark a scenario verified;
only the owner does that.

## First thing next session

- Read `NOTE-inputlayer-20261101-2231.md` and `NOTE-inputlayer-20261101-2245.md`
  in the Studio-Briefing Drive folder. **2245 sets the priorities:**
  - The release signing key is the owner's: they create and hold it.
  - A new core and dropping the debug tag are long-term goals.
  - The debug key stays in use until then.

## Working notes

- `ROADMAP.md` (done / next / later / undecided) and `README.md` (features,
  gestures, names glossary) are the between-sessions reference. Update both
  when things change.
- Docs-only commits use `[skip ci]`, so CI doesn't publish an empty release.
