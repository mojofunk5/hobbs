# Decisions

A dated record of significant architecture/engineering decisions for `hobbs` and its sibling repos
(`hobbs-ui`, `caddy`) where the decision affects this backend. Same convention as
`things/docs/BACKLOG.md`: **never delete an entry.** A decision that's later superseded gets a note
here pointing to what replaced it and why, rather than being removed - this file is a record of how
the system got the way it is, not a live TODO list (`docs/DECISIONS.md`'s sibling for outstanding
work is this repo's README "Not yet built" section and CLAUDE.md "Open work").

Reverse-chronological - newest first.

## 2026-08-29: Branch protection + auto-delete-on-merge added retroactively

Both `hobbs` and `hobbs-ui` are public GitHub repos, so real branch protection (require a PR, require
the `build` status check, block force-push/delete, applies even to admins) has been available for
free the whole time - just hadn't been turned on. Enabled once both repos' CI actually ran `build` on
every branch (not just `master` - see the `hobbs-ui` `chore/ci-run-on-all-branches` fix, since
requiring a status check that never runs on a PR branch would just block every merge). No required
approval count: Andy is the sole reviewer, so requiring an approval would just block him reviewing
his own PRs.

Auto-delete-on-merge was enabled alongside this. Doesn't conflict with the CLAUDE.md rule "never
delete a branch" above - that rule is about actions Claude takes autonomously; a branch deleted as a
direct, visible consequence of Andy clicking "Merge" is his own action, not an autonomous deletion.

## 2026-08-29: Caddy consolidated into a separate shared `caddy` repo

`hobbs-ui` originally shipped with its own per-repo Caddy compose stack (mirroring the pattern
`things-ui` used at the time). That broke the moment `hobbs-ui` needed the same host ports 80/443
that `things-ui`'s own Caddy container already had bound - two independent Caddy containers can't
both bind the same host ports on one VPS. Rather than pick non-standard ports for one of them (or
inventing a co-existence hack), consolidated into a single shared [`caddy`](https://github.com/mojofunk5/caddy)
repo: one Caddy instance, one Caddyfile with a site block per domain
(`things.bssd.co.uk`/`hobbs.bssd.co.uk`), each app's own repo just deploys static files/proxies to
its own backend container by name. Neither `hobbs` nor `hobbs-ui` runs its own reverse proxy or holds
a TLS cert anymore - see the CLAUDE.md note above.
