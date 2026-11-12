package net.sneakyprototypekit.ability.actions

import net.sneakyprototypekit.SneakyPrototypeKit
import org.bukkit.Bukkit
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitTask
import kotlin.random.Random

/**
 * Runs an ability's `actions:` list natively (the port of the MagicSpells `prototype-*` spells).
 *
 * Every action takes `delay` (ticks), `repeat` + `interval` (ticks) and `chance` (0-100).
 */
object ActionEngine {
    private val plugin get() = SneakyPrototypeKit.getInstance()
    private val warned = mutableSetOf<String>()

    private val actions: Map<String, (Node, Ctx) -> Unit> = mapOf(
        "particles" to EffectActions::particles,
        "burst" to EffectActions::burst,
        "ring" to EffectActions::ring,
        "sound" to EffectActions::sound,
        "title-animation" to EffectActions::titleAnimation,
        "message" to EffectActions::message,
        "command" to EffectActions::command,
        "player-command" to EffectActions::playerCommand,
        "leap" to MoveActions::leap,
        "forcepush" to MoveActions::forcepush,
        "toss" to MoveActions::toss,
        "levitate" to MoveActions::levitate,
        "blink" to MoveActions::blink,
        "magnet" to MoveActions::magnet,
        "flight" to MoveActions::flight,
        "potion" to StatusActions::potion,
        "heal" to StatusActions::heal,
        "ignite" to StatusActions::ignite,
        "freeze" to StatusActions::freeze,
        "invulnerable" to StatusActions::invulnerable,
        "age" to StatusActions::age,
        "jump-buff" to StatusActions::jumpBuff,
        "enderchest" to StatusActions::enderchest,
        "beam" to TargetActions::beam,
        "projectile" to TargetActions::projectile,
        "chain" to TargetActions::chain,
        "area" to TargetActions::area,
        "arrow-rain" to TargetActions::arrowRain,
        "conjure" to WorldActions::conjure,
        "summon" to WorldActions::summon,
        "wall" to WorldActions::wall,
        "entomb" to WorldActions::entomb,
    )

    val types: Set<String> get() = actions.keys

    /** Runs [nodes] for [caster]. */
    fun run(caster: Player, nodes: List<Node>) = runAll(nodes, Ctx(caster, caster, caster.location))

    fun runAll(nodes: List<Node>, ctx: Ctx) = nodes.forEach { schedule(it, ctx) }

    private fun schedule(node: Node, ctx: Ctx) {
        val action = actions[node.type]
        if (action == null) {
            if (warned.add(node.type)) plugin.logger.warning("Unknown prototype action type '${node.type}'")
            return
        }
        val chance = node.double("chance", 100.0)
        val fire = {
            if (ctx.caster.isOnline && (chance >= 100.0 || Random.nextDouble(100.0) < chance)) {
                runCatching { action(node, ctx) }
                    .onFailure { plugin.logger.warning("Prototype action '${node.type}' failed: $it") }
            }
        }
        val delay = node.int("delay", 0).toLong().coerceAtLeast(0)
        val repeat = node.int("repeat", 1)
        if (repeat <= 1) {
            if (delay == 0L) fire() else later(delay, fire)
            return
        }
        var left = repeat
        timer(delay, node.int("interval", 1).toLong().coerceAtLeast(1)) { task ->
            fire()
            if (--left <= 0) task.cancel()
        }
    }

    fun later(ticks: Long, block: () -> Unit): BukkitTask =
        Bukkit.getScheduler().runTaskLater(plugin, Runnable { block() }, ticks.coerceAtLeast(1))

    fun timer(delay: Long, period: Long, block: (BukkitTask) -> Unit) {
        Bukkit.getScheduler().runTaskTimer(plugin, { task: BukkitTask -> block(task) }, delay, period.coerceAtLeast(1))
    }

    /** `can-target`: any of self, players, mobs (alias nonplayers, monsters). Default players + mobs. */
    fun canTarget(node: Node, ctx: Ctx, target: LivingEntity, allowSelf: Boolean = true): Boolean {
        if (!target.isValid || target is ArmorStand || target.isInvulnerable) return false
        val kinds = node.strings("can-target").map { it.lowercase() }.ifEmpty { listOf("players", "mobs") }
        if (target == ctx.caster) return allowSelf && ("self" in kinds || "caster" in kinds)
        if (target is Player) return "players" in kinds && target.gameMode.name != "SPECTATOR"
        return kinds.any { it == "mobs" || it == "nonplayers" || it == "monsters" }
    }
}
