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
- `model_data`: numeric (the original collection permits non-integers; Minecraft
  only supplies integers for new logs). Missing model data is written as `0`.
- Missing lore is written as `""`, matching PocketBase's optional-text default.
- `created`, `updated`: `timestamptz` columns accepting PostgreSQL
  `CURRENT_TIMESTAMP`; both are explicitly set for each new log.
- No extra required columns without defaults; no dependence on database-generated
  IDs or timestamp defaults. Preserve the metadata's optional/unbounded field semantics;
  do not impose integer-only/positive-only model data, nonempty text or enum restrictions
  on imported rows.
- Runtime role: schema `USAGE` and table `INSERT`; no schema ownership or DDL
  privileges are needed by this plugin.

The plugin does not query or update old rows. Migration SQL, data transfer, grants,
cutover and rollback coordination belong in **migrateSQL**, not this codebase.
No migration scripts or production database connections are provided here.

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
   exists for each, including ability, creator, material, name/lore, model data,
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
