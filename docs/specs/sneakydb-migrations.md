# Spec: SneakyDB migrations for `lom.lom2_prototype_kit_items`

## Goal

Adopt [team database migrations](https://github.com/Team-Sneakymouse/team-development-policy/blob/main/policy/database-migrations.md) for SneakyPrototypeKit's finalized-item log table.

## Plugin id

`migrate` uses `sneakyprototypekit` (`SneakyPrototypeKit.IDENTIFIER`).

## Scripts

| Script | Purpose |
|--------|---------|
| `V001__init.sql` | Insert contract from [database.md](../database.md): `lom.lom2_prototype_kit_items` with `item_model` and legacy `model_data`. No `CREATE SCHEMA` (operator provisions schema `lom`). |

## Runtime

On enable, before the rest of startup logging, the plugin calls `SneakyDBProvider.getApi().migrate(IDENTIFIER, SneakyPrototypeKit::class.java)`. [PrototypeKitLog](../../src/main/kotlin/net/sneakyprototypekit/util/PrototypeKitLog.kt) waits on that future and on SneakyDB readiness before submitting `INSERT`s. On migration failure, item creation still works but logging stays unavailable until an operator fixes the database and the server restarts.

## Tests

- Unit tests mock the migration future (completed, pending, failed).
- `postgresTest` applies every `db/migrations` script after creating schema `lom`, then inserts one row through `PrototypeKitLog`.

## Operator adoption

1. Ensure schema `lom` exists (`CREATE SCHEMA lom` / `GRANT USAGE`) before this plugin's first migration reaches an environment.
2. If Live already has `lom.lom2_prototype_kit_items` matching V001, record `sneakyprototypekit:V001__init.sql` as applied in SneakyDB's migration table before the first jar that calls `migrate` starts there.
3. Historical PocketBase import and one-off `item_model` backfill for rows logged before the column existed remain operator-owned; see [database.md](../database.md).