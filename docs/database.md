# PostgreSQL item logging

SneakyPrototypeKit writes **new finalized-item logs** through the public SneakyDB API.
Database credentials, pooling and worker threads belong to SneakyDB. This plugin
must not create/alter schemas or import historical PocketBase data.

## External table contract (before deployment)

The [migration ownership map](https://github.com/Team-Sneakymouse/migrateSQL/blob/1798ab37cdbff65db800a559c2cc6bf488dcfb89/pocketbase-collection-map.md)
assigns `lom2_prototype_kit_items` to schema `lom`. The map is a proposal, **not
executed PostgreSQL DDL**. The source [collection metadata](https://github.com/Team-Sneakymouse/pocketbase/blob/bf24b576cda83fb72b4db68c503e661d0fa452da/metadata/collections-20261025T095957Z.json)
defines the following fields. Coordinate the externally managed table with this
insert contract before enabling the new plugin:

- Target: `lom.lom2_prototype_kit_items` (schema-qualified; no search-path dependency).
- `id`: text primary key, exactly 15 lowercase ASCII letters/digits. New logs
  generate their own random IDs; existing IDs are imported by the external migration.
- `type`, `ability`, `creator`, `name`, `material`, `lore`: text.
- `item_model`: text, NOT NULL with default `''`. The item-model id of the finalized item
  (e.g. `lom:items/prototype-food/07_bread`); missing is written as `''`. **Added after the
  item-model change** ([#7](https://github.com/Team-Sneakymouse/SneakyPrototypeKit/issues/7)).
- `model_data`: numeric, **legacy**. The plugin no longer produces integer custom model data and
  always writes `0`; the column stays for rows logged before the change (the original collection
  permits non-integers). Do not drop it while the old rows are the only record of their icon.
- Missing lore is written as `""`, matching PocketBase's optional-text default.
- `created`, `updated`: `timestamptz` columns accepting PostgreSQL
  `CURRENT_TIMESTAMP`; both are explicitly set for each new log.
- No extra required columns without defaults; no dependence on database-generated
  IDs or timestamp defaults. Preserve the metadata's optional/unbounded field semantics;
  do not impose integer-only/positive-only model data, nonempty text or enum restrictions
  on imported rows.
- Runtime role: schema `USAGE` and table `INSERT`; no schema ownership or DDL
  privileges are needed by this plugin.

### Item-model migration (stored rows)

Rows logged before the change hold `material` + integer `model_data` and an empty `item_model`.
`tools/backfill_item_model_sql.py` prints the one-off, idempotent SQL (add the column, then fill
`item_model` from the `(material, model_data)` pair via the same `legacy-item-models.json` table the plugin
bundles; rows without a pack model keep `''`). Run it **before** deploying this version: an `INSERT` that
names `item_model` fails (category `INSERT`, item delivery unaffected) until the column exists. Hand it to
the migration owner; the plugin never runs DDL. Run it with **no arguments** so every carrier in the
table is covered; restricting it to a few materials leaves rows of the other carriers unfilled:

```
python3 tools/backfill_item_model_sql.py | psql ...
```

The statement is a single transaction and only touches rows whose `item_model` is still `''`, so it is
safe to re-run. **Re-run it after cutover and after any rollback:** until cutover, and again after a
rollback, the old plugin keeps inserting rows with only `model_data` (its `INSERT` does not name
`item_model`, so the column default `''` applies); those rows stay unfilled until the backfill runs again.
Rows written by this version (`item_model` set, `model_data` `0`) are never modified.

Regenerate `legacy-item-models.json` with `tools/generate_legacy_item_models.py` after the pack's mapping changes.

The plugin does not query or update old rows. Migration SQL, data transfer, grants,
cutover and rollback coordination belong in **migrateSQL**, not this codebase.
Apart from the one-off item-model backfill generator above, no migration scripts or production
database connections are provided here.

## Runtime semantics

Logging is best-effort and independent of giving the player their finalized item.
No network waits run on the Minecraft thread. A log waits asynchronously for
SneakyDB readiness; at most 64 pending/in-flight logs are admitted. Cancelling a
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

1. Confirm the external migration created the exact target contract and grants;
   independently verify historical record counts/content outside this plugin.
2. Install SneakyDB `2026.10.26-fd4e29e90ad0` or a compatible newer API and configure
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
coordinate rollback with the external migration owner.
