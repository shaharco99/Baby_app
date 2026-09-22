# Applying migrations: one-time repair, then CI

Until now every migration reached the live project by hand, and `CLAUDE.md` carries the warning
that goes with that: tagging a release before applying breaks sync silently, with no error on
either phone. `.github/workflows/supabase-deploy.yml` removes the step that has to be remembered
— but it can only be switched on after the live project's migration history is made to agree
with the files in `supabase/migrations/`.

## The history was out of step; it is not now

`0007`–`0011` had been applied through the Supabase MCP, which names a migration for the moment
it ran rather than for the file it came from, so the project recorded timestamps
(`20260918135125` and friends) where the repository has filenames. `0012` had been applied as
plain SQL and was never recorded at all.

That was reconciled on 2026-09-22: the six timestamped rows were removed and `0007`–`0012`
written in their place, so `supabase_migrations.schema_migrations` now reads `0001` … `0012`,
matching `supabase/migrations/` name for name. Bookkeeping only — no schema object and no
application row was touched, and the counts were checked either side (154 records, 2 workspaces,
2 members, 8 device keys, 2 push tokens).

`0013_device_presence` is deliberately **pending**. Its table exists, because it was applied by
hand before this rule was written; its history row was then removed so that the pipeline is what
records it. Every migration is idempotent, so the pipeline's apply is a no-op that writes the
row — which is exactly the point: the first thing the pipeline ever does is the thing that
should have been done by the pipeline in the first place.

If the history ever drifts again, the repair is `supabase migration repair --status applied
<version>` / `--status reverted <version>` after `supabase link` — but drift should not recur,
because nothing applies migrations outside CI any more.

## The secrets CI needs

Three secrets, on the `production` **environment** rather than the repository, so only a job that
targets that environment can read them. The environment also carries a required reviewer, so a
deploy waits for an approval before it runs.

`SUPABASE_PROJECT_REF` is already set. The other two have to be added by hand, once, by a person:

| secret | what it is |
|---|---|
| `SUPABASE_ACCESS_TOKEN` | a personal access token from the Supabase dashboard (Account > Access Tokens) |
| `SUPABASE_DB_PASSWORD` | the project's database password |
| `SUPABASE_PROJECT_REF` | `idjtpfwnoncqhbhfthja` — already set |

The project reference is not really a secret — it is in the API URL the app already ships — but
it is kept with the other two so that the whole deploy configuration lives in one place.

## What the workflow does

`supabase-deploy.yml` is reusable, not triggered directly. `android-release.yml` calls it as the
`migrations` job and the release waits on it, so on a tag the order is: schema first, APK second.
`workflow_dispatch` runs it alone when the schema needs to move without a release.

Every run proves the migrations on an empty database before it touches the live one: `supabase
start`, `db reset`, `test db`, and only then `link` and `db push`. The pending list is printed
before the push and again after, so the run's log says what that tag changed.

The job names `production` as its environment. Configuring that environment under
Settings > Environments lets GitHub hold the job for a manual approval and record who gave it —
worth doing, since this is the one workflow here whose mistakes cannot be fixed by rebuilding.

## The rule

`CLAUDE.md` now states it: migrations are applied by the pipeline and by nothing else — not by
hand, not from a local CLI, and not by an agent with database access. Two of the three ways this
schema has drifted came from someone applying a migration directly because it was faster in the
moment, and both times the file and the database ended up describing different things.

## What this does not do

No rollback. A migration that is wrong is fixed by writing another migration, which is why every
file in `supabase/migrations/` must stay idempotent and additive — see `CLAUDE.md`. Nothing about
CI applying them changes that rule; it only removes the chance to forget.
