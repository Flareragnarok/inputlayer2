# Scenarios

Every problem, change and feature of Input Layer is a scenario file here:
`scenarios/<id>.md` (ids `il-001`, `il-002`, ...). The owner checks whether a
scenario's Expected happened; prose descriptions elsewhere don't count.

## Format

```markdown
# il-NNN · <short title>

## Context
Where and when it happens, what was seen, what is suspected.

## Steps
1. What to do, in order, on the device.

## Expected
What must happen (and what must not).

## Shows in
Which log, test or check proves it.

## Status
open / fixed in build N / verified by owner
```

## Rules

- Builds name the scenarios they touched (`scenarios: il-003, il-007` in
  each commit message; CI lists them in the release notes).
- A change that fixes a problem no scenario describes gets a new scenario first.
- Only the owner marks a scenario `verified by owner`.
