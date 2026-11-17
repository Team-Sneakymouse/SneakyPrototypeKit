# Spec: SneakyDB migrations for `lom.lom2_prototype_kit_items`

## Goal

Adopt [team database migrations](https://github.com/Team-Sneakymouse/team-development-policy/blob/main/policy/database-migrations.md) for SneakyPrototypeKit's finalized-item log table.

## Plugin id

`migrate` uses `sneakyprototypekit` (`SneakyPrototypeKit.IDENTIFIER`).

## Scripts

| Script | Purpose |
|--------|---------|
| `V001__init.sql` | **Live baseline (2026-11-17):** `lom.lom2_prototype_kit_items` as on `team_sneakymouse` (`model_data bigint`, no `item_model`), Live indexes, `lom.touch_updated` trigger. |
| `V002__item_model.sql` | Add `item_model text NOT NULL DEFAULT ''` for CraftEngine ids. |

## Evidence (V001 vs Live)

Compared on 2026-11-17 against Live (`team_sneakymouse`) with `pg_dump --schema-only`. Live had no `item_model` column and used `bigint` for `model_data`. The first merged V001 described the repository insert contract instead.

## Runtime

On enable, before the rest of startup logging, the plugin calls `SneakyDBProvider.getApi().migrate(IDENTIFIER, SneakyPrototypeKit::class.java)`. [PrototypeKitLog](../../src/main/kotlin/net/sneakyprototypekit/util/PrototypeKitLog.kt) waits on that future and on SneakyDB readiness before submitting `INSERT`s. On migration failure, item creation still works but logging stays unavailable until an operator fixes the database and the server restarts.

## Tests

- Unit tests mock the migration future (completed, pending, failed).
- `postgresTest` applies every `db/migrations` script after creating schema `lom`, then inserts one row through `PrototypeKitLog`.

## Operator adoption

1. Ensure schema `lom` exists before this plugin's first migration reaches an environment.
2. **Live / Staging** (table matches V001): record `sneakyprototypekit` version `1` with a **null** checksum, then let `apply` fill it and run V002.
3. **Dev** (wrong V001 checksum already recorded / `item_model` already added): reset the table to the Live baseline (or drop `item_model` and restore `model_data` to `bigint`), `DELETE FROM sneakydb.schema_migrations WHERE plugin = 'sneakyprototypekit'`, then record version `1` with null checksum. V002 then applies.
4. Historical PocketBase import and optional `item_model` backfill for pre-column rows: see [database.md](../database.md).
