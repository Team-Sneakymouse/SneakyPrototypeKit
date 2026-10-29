# Agent instructions (SneakyPrototypeKit)

Follow the Team-Sneakymouse cross-repo rules in EmptyPlugin's AGENTS.md; this file adds rules specific to this repo.

## Repo-specific rules

- **DB logging stays best-effort and async.** Snapshot Bukkit data on the main thread, then call SneakyDB asynchronously; a logging failure must never block or cancel item creation (`DatabaseUtil`, `PrototypeKitLog`).
- **PDC keys are API.** The keys in `SneakyPrototypeKit.kt` identify existing items on the server. Never rename or retype them; add new keys and migrate.
- **Abilities come from config only.** `AbilityManager` runs configured commands with substitution from the console; never accept player-typed text as a command.
- **Prefer SneakyUtils `TextUtility`.** Don't extend `util/TextUtility.kt`; migrate callers to `com.sneakyutils.util.TextUtility`.
- **Inventory UI hops.** Menu swaps in `creation/ui` use `runTaskLater(1)`; keep that, guard with `isOnline`, and clear state on `InventoryCloseEvent`.
