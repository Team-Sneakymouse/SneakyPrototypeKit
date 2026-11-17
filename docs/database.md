# PostgreSQL item logging

SneakyPrototypeKit writes **new finalized-item logs** through the public SneakyDB API.
Database credentials, pooling and worker threads belong to SneakyDB.

This plugin **owns** table `lom.lom2_prototype_kit_items` via SneakyDB migrations
(`db/migrations/V001__init.sql`, plugin id `sneakyprototypekit`). The operator must
provision schema `lom` (`CREATE SCHEMA` / `GRANT USAGE`) before the first migration runs;
scripts never create schemas. Historical PocketBase import and coordinated cutover remain
operator-owned (see [migrateSQL](https://github.com/Team-Sneakymouse/migrateSQL)); the
plugin does not import old rows.

## Table contract

Target: `lom.lom2_prototype_kit_items` (schema-qualified; no search-path dependency).

- `id`: text primary key, exactly 15 lowercase ASCII letters/digits. New logs generate
  their own random IDs; existing IDs are retained when historical data is imported.
- `type`, `ability`, `creator`, `name`, `material`, `lore`: text.
- `item_model`: text, NOT NULL with default `''`. The item-model id of the finalized item
  (e.g. `lom:items/prototype-food/07_bread`); missing is written as `''`.
- `model_data`: numeric, **legacy**. The plugin no longer produces integer custom model data and
  always writes `0`; the column stays for rows logged before the change (the original collection
  permits non-integers). Do not drop it while the old rows are the only record of their icon.
- Missing lore is written as `""`, matching PocketBase's optional-text default.
- `created`, `updated`: `timestamptz` columns accepting PostgreSQL
  `CURRENT_TIMESTAMP`; both are explicitly set for each new log.
- No extra required columns without defaults; no dependence on database-generated
  IDs or timestamp defaults.
- Runtime role: insert-only through SneakyDB; the plugin does not query or update old rows.

### Item-model backfill (existing rows only)

Rows logged before the `item_model` column existed hold `material` + integer `model_data` and an
empty `item_model`. `tools/backfill_item_model_sql.py` prints the one-off, idempotent SQL to fill
`item_model` from the `(material, model_data)` pair via the same `legacy-item-models.json` table the
plugin bundles. Run it on databases that already had rows **before** V001 or before the column existed;
fresh databases created by V001 do not need the `ALTER TABLE` line. Hand the output to the operator;
the plugin never runs that DML at startup:

```
python3 tools/backfill_item_model_sql.py | psql ...
```

The `UPDATE` is safe to re-run. Rows written by this version (`item_model` set, `model_data` `0`)
are never modified.

Regenerate `legacy-item-models.json` with `tools/generate_legacy_item_models.py` after the pack's mapping changes.

## Runtime semantics

Logging is best-effort and independent of giving the player their finalized item.
No network waits run on the Minecraft thread. A log waits asynchronously for
SneakyDB migrations and readiness; at most 64 pending/in-flight logs are admitted. Cancelling a
caller's completion future does not cancel a log or free its capacity slot. A failed insert/readiness
or queue rejection is reported to the server log without echoing SQL parameters,
raw driver exceptions or credentials. Safe categories distinguish `CAPACITY`, `READINESS`,
`INSERT`, `ROW_COUNT` and `DISABLED`; a category does not prove rollback. A successful submission is not a confirmed
insert; the completion must report exactly one affected row.

Disabling this plugin rejects waiting/new logs and fences future submissions from
that instance. Already-submitted database operations may still commit. There is
no automatic retry, durable local queue or PocketBase fallback: an exceptional
completion can represent an uncertain write, so retrying blindly risks duplicates.
Re-enable/restart after fixing SneakyDB initialization failure.

## Deployment verification (operator / in-game)

1. Ensure schema `lom` exists and grants allow SneakyDB to apply migrations and insert.
   On Live with a pre-existing table matching V001, record V001 as already applied before
   the first migrat­ing jar starts (see [specs/sneakydb-migrations.md](specs/sneakydb-migrations.md)).
2. Install SneakyDB `2026.11.17-452c61d0f480` or a compatible newer API and configure
   its PostgreSQL connection. Keep SneakyPocketbase installed if other plugins use it.
3. Install this plugin plus SneakyUtils and start Paper 26.2 / Java 25.
4. Finalize an ITEM, FOOD and DRINK. Verify the item is delivered and one new row
   exists for each, including ability, creator, material, name/lore, `item_model` (`model_data` is `0`),
   a unique 15-character ID and timestamps. Test no model data, multiline/blank lore,
   Unicode and quotes without changing SQL structure.
5. Test delayed readiness, denied INSERT access and a database outage in a dev
   environment: item creation still works, logging failures are visible, and the
   server thread remains responsive. Pending logs are not durable across restarts.
6. Disable while readiness is pending; confirm no waiting writes are submitted
   afterwards. Re-enable and verify only the fresh instance accepts new logs.

Automated API-boundary tests/builds are not live database or in-game verification.
Reverting the code does not move newly written SQL logs back into PocketBase;
coordinate rollback with the operator.