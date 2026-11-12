# Native ability actions

An ability's `actions:` list runs in the plugin, no MagicSpells needed. If an ability has both `actions` and `command-console`, the actions run. The dev server abilities (ported from the MagicSpells `prototype-*` spells on 2026-11-11) are in [server-config.yml](server-config.yml); regenerate with `python tools/port_spells.py <MagicSpells-Data/spells-prototype> docs/server-config-before-port.yml docs/server-config.yml`.

Every action takes `delay` (ticks), `repeat` + `interval` (ticks) and `chance` (0-100).

**Where things happen.** Each action runs with a caster, an *entity* (the caster, or the hit target inside `on-hit`/`on-target`) and a *location* (the entity, or a beam/projectile point). Particles and sounds take `at: here|caster|target` and `height`. Forces use the MagicSpells scale: value / 10 = blocks per tick.

| type | keys | does |
|---|---|---|
| particles | particle, count, spread / spread-xz / spread-y, speed, color (hex), block, size | spawns particles |
| burst | particle, counts [3], spreads, spreads-y, heights, gap, speed, sound, volume, pitch | the 3-ring "effect spell" puff |
| ring | particle, radius, points, period, iterations, rise, spin | circle (rise > 0 = vortex) |
| sound | sound, volume, pitch, personal | plays a sound |
| title-animation | font, start-frame, interval, duration, fade-out | font animation in the subtitle (glyph U+E000+frame) |
| message | text | chat message to the entity |
| command | command | console command; `[playerName]` caster, `[targetName]` entity |
| player-command | command | the entity runs it (like CMI sudo) |
| leap | forward, upward, cancel-fall | launches the entity |
| forcepush | radius, force, vertical, max-vertical, can-target, on-hit | pushes everyone away |
| toss | horizontal, vertical | throws the entity away from the caster |
| levitate | duration, distance-change, min-distance, max-distance, y-offset, cancel-on-damage | holds/pulls the entity in front of the caster |
| blink | range, pass-through-ceiling, on-depart | teleports the caster to where they look |
| magnet | radius, velocity | pulls dropped items to the caster |
| flight | x, z, altitude, speed, trail, trail-interval | flies the caster to x/z |
| potion | effect, duration, amplifier, hidden, ambient | potion effect |
| heal / ignite / freeze | amount / ticks / ticks, damage | heal, set on fire, frost |
| invulnerable | ticks | no damage |
| age | age-lock, on-success | adult animal becomes a baby |
| jump-buff | ticks, on-jump, on-start, on-end | every jump runs on-jump |
| enderchest | | opens the caster's ender chest |
| beam | max-distance, step, hit-radius, y-offset, gravity, stop-on-ground, stop-on-hit, can-target, trail, on-hit | instant line |
| projectile | velocity (blocks/s), max-distance, hit-radius, controllable, sneak-inverts, stop-on-hit, trail, on-hit, on-ground | moving bolt |
| chain | range, bounces, bounce-range, bounce-delay, line, can-target, on-hit | jumps between targets |
| area | radius, vertical-radius, entity-types, can-target, on-target | everything nearby |
| arrow-rain | range, radius, height, volleys, interval, arrows, damage, speed, on-volley | rains arrows where the caster looks |
| conjure | items (`id amount chance%`), pickup-delay | drops SneakyItems magic items or vanilla items |
| summon | entity, count, spawn-interval, spread, lifetime, helmet, effects, tick-interval, on-spawn, on-tick, on-end | temporary mobs that ignore their caster and drop nothing |
| wall | distance, width, height, material, duration, y-offset, rise-interval | temporary wall (fills air only) |
| entomb | material, duration, shell | traps the entity in blocks (air only) |

`can-target`: any of `self`, `players`, `mobs` (default players + mobs). Temporary blocks are put back on plugin disable.
