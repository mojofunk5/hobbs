# Done

Plan docs move here from `docs/plans/` once every chunk they describe has merged - see `CLAUDE.md`'s
"Closing out a plan doc is part of the PR that finishes it" rule for exactly when and how.

A doc landing here still has a `Status:` line saying it's implemented, with links to the PR(s) that
did it - the file itself doesn't change shape or lose content, it just moves. `docs/plans/` (the
parent folder) becomes, by construction, the list of what's designed but not yet fully built - `ls
docs/plans/*.md` answers "what's in flight" without reading every file's `Status:` line.

The eight plan docs that were already implemented before this convention existed
(`logbook-entries.md`, `pilot-account-split.md`, `aircraft-picker.md`, `pilot-picker.md`,
`airfield-picker.md`, `split-integration-test-by-endpoint.md`, `picker-recent-endpoints.md`,
`new-entry-context-endpoint.md`) were initially left in `docs/plans/` rather than bulk-moved, to
avoid a large mechanical PR rewriting every cross-reference to them at once. That bulk move (and the
matching one in `hobbs-ui`) happened on 2026-08-31, once every cross-reference - other plan docs,
`CLAUDE.md`, `README.md`, `docs/GLOSSARY.md`, `docs/DECISIONS.md`, and `hobbs-ui`'s doc links by full
GitHub URL - had been checked and fixed up in the same change.
