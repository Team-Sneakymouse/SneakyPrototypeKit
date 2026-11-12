package net.sneakyprototypekit.ability.actions

import org.bukkit.NamespacedKey
import org.bukkit.Registry
import org.bukkit.entity.Ageable
import org.bukkit.entity.Breedable
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType

/** Effects on the entity: potions, healing, fire, freezing and timed buffs. */
object StatusActions {
    private val aliases = mapOf(
        "slow" to "slowness", "jump" to "jump_boost", "confusion" to "nausea", "fast_digging" to "haste",
        "slow_digging" to "mining_fatigue", "increase_damage" to "strength", "damage_resistance" to "resistance",
        "heal" to "instant_health", "harm" to "instant_damage",
    )

    fun effectType(name: String): PotionEffectType? {
        val key = name.lowercase().removePrefix("minecraft:")
        return Registry.EFFECT.get(NamespacedKey.minecraft(aliases[key] ?: key))
    }

    /** `potion`: effect, duration (ticks), amplifier, hidden (no particles), ambient. */
    fun potion(node: Node, ctx: Ctx) {
        val type = effectType(node.str("effect", "speed")) ?: return
        ctx.entity.addPotionEffect(
            PotionEffect(type, node.int("duration", 100), node.int("amplifier", 0), node.bool("ambient", false), !node.bool("hidden", true), true)
        )
    }

    /** `heal`: amount (half hearts). */
    fun heal(node: Node, ctx: Ctx) {
        if (ctx.entity.isDead) return
        ctx.entity.heal(node.double("amount", 4.0))
    }

    /** `ignite`: sets the entity on fire for `ticks`. */
    fun ignite(node: Node, ctx: Ctx) {
        ctx.entity.fireTicks = maxOf(ctx.entity.fireTicks, node.int("ticks", 100))
    }

    /** `freeze`: powder-snow frost overlay and slowdown for `ticks`; damage only with `damage: true`. */
    fun freeze(node: Node, ctx: Ctx) {
        val e = ctx.entity
        val full = e.maxFreezeTicks
        e.freezeTicks = if (node.bool("damage", false)) full else full - 1
        e.lockFreezeTicks(true)
        ActionEngine.later(node.int("ticks", 200).toLong()) {
            e.lockFreezeTicks(false)
            e.freezeTicks = 0
        }
    }

    /** `invulnerable`: the entity takes no damage for `ticks`. */
    fun invulnerable(node: Node, ctx: Ctx) = AbilityState.invulnerable(ctx.entity, node.int("ticks", 60))

    /** `age`: turns an adult animal into a baby; age-lock keeps it one. */
    fun age(node: Node, ctx: Ctx) {
        val e = ctx.entity as? Ageable ?: return
        if (!e.isAdult) return
        e.setBaby()
        if (node.bool("age-lock", true)) (e as? Breedable)?.ageLock = true
        ActionEngine.runAll(node.nodes("on-success"), ctx)
    }

    /** `jump-buff`: for `ticks`, every jump the entity (a player) makes runs `on-jump`. */
    fun jumpBuff(node: Node, ctx: Ctx) {
        val p = ctx.entity as? Player ?: return
        AbilityState.jumpBuff(p, node.int("ticks", 200), node.nodes("on-jump"))
        ActionEngine.runAll(node.nodes("on-start"), ctx)
        ActionEngine.later(node.int("ticks", 200).toLong()) { ActionEngine.runAll(node.nodes("on-end"), ctx.at(p)) }
    }

    /** `enderchest`: opens the caster's ender chest. */
    fun enderchest(node: Node, ctx: Ctx) {
        ctx.caster.openInventory(ctx.caster.enderChest)
    }
}
