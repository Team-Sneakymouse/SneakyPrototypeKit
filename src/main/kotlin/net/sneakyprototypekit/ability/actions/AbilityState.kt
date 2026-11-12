package net.sneakyprototypekit.ability.actions

import com.destroystokyo.paper.event.player.PlayerJumpEvent
import net.sneakyprototypekit.SneakyPrototypeKit
import org.bukkit.NamespacedKey
import org.bukkit.entity.AbstractArrow
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.EntityTargetEvent
import org.bukkit.persistence.PersistentDataType
import java.util.UUID

/** Timed state the actions leave behind (no fall damage, invulnerability, jump buffs, summons, arrows). */
object AbilityState : Listener {
    private val noFall = mutableMapOf<UUID, Long>()
    private val invulnerable = mutableMapOf<UUID, Long>()
    private val jumpBuffs = mutableMapOf<UUID, Pair<Long, List<Node>>>()

    /** Summoned mob -> its caster. Summons never target or hurt their caster and drop nothing. */
    val summons = mutableMapOf<UUID, UUID>()

    val ARROW_KEY by lazy { NamespacedKey(SneakyPrototypeKit.getInstance(), "rain_arrow") }

    private fun now() = System.currentTimeMillis()

    fun noFall(e: Entity, ticks: Int = 200) { noFall[e.uniqueId] = now() + ticks * 50L }
    fun invulnerable(e: Entity, ticks: Int) { invulnerable[e.uniqueId] = now() + ticks * 50L }
    fun jumpBuff(e: Entity, ticks: Int, actions: List<Node>) { jumpBuffs[e.uniqueId] = now() + ticks * 50L to actions }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onDamage(event: EntityDamageEvent) {
        val id = event.entity.uniqueId
        invulnerable[id]?.let { if (it > now()) { event.isCancelled = true; return } else invulnerable.remove(id) }
        if (event.cause == EntityDamageEvent.DamageCause.FALL) {
            noFall.remove(id)?.let { if (it > now()) event.isCancelled = true }
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onHitOwnCaster(event: EntityDamageByEntityEvent) {
        val damager = event.damager
        // Rain arrows never hit the caster who called them.
        if (damager is AbstractArrow && damager.persistentDataContainer.has(ARROW_KEY, PersistentDataType.STRING) &&
            damager.persistentDataContainer.get(ARROW_KEY, PersistentDataType.STRING) == event.entity.uniqueId.toString()) {
            event.isCancelled = true
        }
        if (summons[damager.uniqueId] == event.entity.uniqueId) event.isCancelled = true
    }

    @EventHandler(ignoreCancelled = true)
    fun onTarget(event: EntityTargetEvent) {
        val owner = summons[event.entity.uniqueId] ?: return
        if (event.target?.uniqueId == owner) event.isCancelled = true
    }

    @EventHandler
    fun onSummonDeath(event: EntityDeathEvent) {
        if (summons.remove(event.entity.uniqueId) == null) return
        event.drops.clear()
        event.droppedExp = 0
    }

    @EventHandler(ignoreCancelled = true)
    fun onJump(event: PlayerJumpEvent) {
        val p = event.player
        val (until, actions) = jumpBuffs[p.uniqueId] ?: return
        if (until < now()) { jumpBuffs.remove(p.uniqueId); return }
        ActionEngine.runAll(actions, Ctx(p, p, p.location))
    }

    /** Living entities only; used by summon cleanup. */
    fun forget(e: LivingEntity) = summons.remove(e.uniqueId)
}
