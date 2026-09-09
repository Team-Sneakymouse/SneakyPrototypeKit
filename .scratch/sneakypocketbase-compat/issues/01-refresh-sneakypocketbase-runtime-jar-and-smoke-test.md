# Refresh SneakyPocketbase Runtime Jar And Smoke Test

Status: ready-for-human
Type: HITL

## What to build

Refresh SneakyPrototypeKit's compile/runtime SneakyPocketbase jar to the updated SneakyPocketbase build and smoke test prototype kit logging. No removed SneakyPocketbase API usage was found, but this repo compiles against a local SneakyPocketbase jar and uses `PBRunnable` plus direct `pb()` record creation.

## Acceptance criteria

- [ ] SneakyPrototypeKit builds against the updated SneakyPocketbase jar.
- [ ] Finalized prototype kit items still log to the `lom2_prototype_kit_items` Pocketbase collection.
- [ ] Failed Pocketbase writes still fail gracefully without disrupting the item workflow.
- [ ] A maintainer smoke tests finalizing an item on a dev server with SneakyPocketbase enabled.

## Blocked by

- Updated SneakyPocketbase jar available to this repo's build.

## Source finding

- `build.gradle.kts` points at `libs/SneakyPocketbase-1.0.jar`.
- `PocketBaseUtil` uses `PBRunnable` and `SneakyPocketbase.getInstance().pb()` directly.
