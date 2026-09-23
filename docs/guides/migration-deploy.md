# Applying migrations: one-time repair, then CI

Until now every migration reached live project by hand, and `CLAUDE.md` carries matching warning: tagging release before applying breaks sync silently, no error on either phone. `.github/workflows/supabase-deploy.yml` removes step that must be remembered — but can only be switched on after live project's migration history agrees with files in `supabase/migrations/`.

## The history was out of step; it is not now

`0007`–`0011` applied through Supabase MCP, which names migration for moment it ran, not for source file. So project recorded timestamps (`20260918135125` and friends) where repo has filenames. `0012` applied as plain SQL, never recorded.

Reconciled 2026-09-22: six timestamped rows removed, `0007`–`0012` written in their place. `supabase_migrations.schema_migrations` now reads `0001` … `0012`, matching `supabase/migrations/` name for name. Bookkeeping only — no schema object, no application row touched. Counts checked before and after (154 records, 2 workspaces, 2 members, 8 device keys, 2 push tokens).

`0013_device_presence` deliberately **pending**. Table exists, since applied by hand before this rule written; history row then removed so pipeline records it. Every migration idempotent, so pipeline apply = no-op that writes row. That the point: pipeline's first act is what pipeline should have done originally.

If history drifts again, repair is `supabase migration repair --status applied
<version>` / `--status reverted <version>` after `supabase link` — but drift should not recur, since nothing applies migrations outside CI anymore.

## The secrets CI needs

Three secrets, on `production` **environment** not repository, so only job targeting that environment can read them. Environment also has required reviewer, so deploy waits for approval before running.

`SUPABASE_PROJECT_REF` already set. Other two must be added by hand, once, by person:

| secret | what it is |
|---|---|
| `SUPABASE_ACCESS_TOKEN` | personal access token from Supabase dashboard (Account > Access Tokens) |
| `SUPABASE_DB_PASSWORD` | project's database password |
| `SUPABASE_PROJECT_REF` | `idjtpfwnoncqhbhfthja` — already set |

Project ref not really secret — already in API URL app ships — but kept with other two so whole deploy config lives in one place.

## What the workflow does

`supabase-deploy.yml` reusable, not triggered directly. `android-release.yml` calls it as `migrations` job; release's `publish` job waits on it. On tag, order: schema first, APK second. APK built alongside migrations — building touches nothing phone can see, only publishing does. `workflow_dispatch` runs deploy alone when schema must move without release.

Every run proves migrations on empty DB before touching live one. `verify` job is `supabase-tests.yml` itself, called not copied: `supabase start` on fresh runner (empty volume, so every migration applies from scratch) and `test db`. Only after pass does `migrate` job ask approval, then `link` and `db push`. Pending list printed before push and after, so run log shows what tag changed.

`migrate` job names `production` as environment. Configuring that environment under Settings > Environments lets GitHub hold job for manual approval and record approver — worth doing, since this is only workflow here whose mistakes can't be fixed by rebuilding.

## The rule

`CLAUDE.md` now states it: migrations applied by pipeline and nothing else — not by hand, not from local CLI, not by agent with DB access. Two of three past schema drifts came from someone applying migration directly because faster in moment; both times file and DB ended up describing different things.

## What this does not do

No rollback. Wrong migration fixed by writing another migration — why every file in `supabase/migrations/` must stay idempotent and additive (see `CLAUDE.md`). CI applying them changes nothing about that rule; only removes chance to forget.