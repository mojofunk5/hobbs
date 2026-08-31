# Done

Plan docs move here from `docs/plans/` once every chunk they describe has merged - see `CLAUDE.md`'s
"Closing out a plan doc is part of the PR that finishes it" rule for exactly when and how.

A doc landing here still has a `Status:` line saying it's implemented, with links to the PR(s) that
did it - the file itself doesn't change shape or lose content, it just moves. `docs/plans/` (the
parent folder) becomes, by construction, the list of what's designed but not yet fully built - `ls
docs/plans/*.md` answers "what's in flight" without reading every file's `Status:` line.

Nothing currently in `docs/plans/done/` yet - the plan docs that were already implemented before
this convention existed (`logbook-entries.md`, `pilot-account-split.md`, `aircraft-picker.md`,
`pilot-picker.md`, `airfield-picker.md`, `split-integration-test-by-endpoint.md`,
`picker-recent-endpoints.md`, `new-entry-context-endpoint.md`) were deliberately left in place rather
than bulk-moved retroactively, to avoid a large mechanical PR rewriting every cross-reference to them
(including `hobbs-ui` doc links by full GitHub URL) at once. They'll migrate here individually,
opportunistically, whenever a PR is already touching one of them for another reason - or as a
dedicated cleanup PR, if one's ever worth doing on its own.
