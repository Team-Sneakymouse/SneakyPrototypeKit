package net.sneakyprototypekit.ability.actions

import net.sneakyprototypekit.SneakyPrototypeKit
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.entity.EntityType
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Mob
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.util.Vector
import kotlin.random.Random

/** Actions that put things in the world: items, mobs and temporary blocks. */
object WorldActions {
    private val plugin get() = SneakyPrototypeKit.getInstance()

    /** Temporary blocks -> the material they were placed as. Restored on expiry and on disable. */
    private val temporary = mutableMapOf<Block, Material>()

    private val magicItem by lazy {
        if (!Bukkit.getPluginManager().isPluginEnabled("SneakyItems")) null
        else runCatching {
            Class.forName("com.sneakyitems.util.magicitems.MagicItems").getMethod("getItemByInternalName", String::class.java)
        }.onFailure { plugin.logger.warning("SneakyItems API not usable, conjure falls back to vanilla items: $it") }.getOrNull()
    }

    /** A SneakyItems magic item id (`item-firePetal`), or a vanilla material. */
    fun item(id: String, amount: Int = 1): ItemStack? {
        val magic = magicItem?.let { m -> runCatching { m.invoke(null, id) as? ItemStack }.getOrNull() }
        val stack = magic?.clone() ?: Material.matchMaterial(id)?.let { ItemStack(it) } ?: return null
        stack.amount = amount.coerceAtLeast(1)
        return stack
    }

    /** `conjure`: items (`<id> [amount] [chance%]` each, rolled one by one) drop at the location. pickup-delay ticks. */
    fun conjure(node: Node, ctx: Ctx) {
        val at = ctx.place(node)
        node.strings("items").forEach { line ->
            val parts = line.trim().split(" ")
            val chance = parts.firstOrNull { it.endsWith("%") }?.removeSuffix("%")?.toDoubleOrNull() ?: 100.0
            if (Random.nextDouble(100.0) >= chance) return@forEach
            val amount = parts.getOrNull(1)?.takeUnless { it.endsWith("%") }?.toIntOrNull() ?: 1
            val stack = item(parts[0], amount) ?: run { plugin.logger.warning("conjure: unknown item '${parts[0]}'"); return@forEach }
            at.world.dropItem(at, stack) { it.pickupDelay = node.int("pickup-delay", 20) }
        }
    }

    /**
     * `summon`: count mobs (`entity`) around the location, one every `spawn-interval` ticks. Each lives
     * `lifetime` ticks, wears `helmet` (item id), gets `effects` ("effect ticks amplifier"), runs
     * `on-tick` every `tick-interval` ticks and `on-end` when it goes. Summons never target or hurt
     * their caster and drop nothing.
     */
    fun summon(node: Node, ctx: Ctx) {
        val type = runCatching { EntityType.valueOf(node.str("entity", "zombie").uppercase()) }.getOrNull() ?: return
        val spread = node.double("spread", 3.0)
        var left = node.int("count", 1)
        ActionEngine.timer(0, node.int("spawn-interval", 20).toLong()) { task ->
            if (left-- <= 0) { task.cancel(); return@timer }
            val loc = ctx.location.clone().add(Random.nextDouble(-spread, spread), 0.0, Random.nextDouble(-spread, spread))
            loc.y = loc.world.getHighestBlockYAt(loc).coerceAtMost(loc.blockY + 3).toDouble() + 1
            if (!loc.block.isPassable) loc.y = ctx.location.y
            val mob = loc.world.spawnEntity(loc, type) as? LivingEntity ?: return@timer
            AbilityState.summons[mob.uniqueId] = ctx.caster.uniqueId
            mob.isPersistent = false
            (mob as? Mob)?.setLootTable(null)
            node.strOrNull("helmet")?.let { id -> item(id)?.let { mob.equipment?.setItem(EquipmentSlot.HEAD, it); mob.equipment?.helmetDropChance = 0f } }
            node.strings("effects").forEach { e ->
                val p = e.split(" ")
                StatusActions.effectType(p[0])?.let { t ->
                    mob.addPotionEffect(PotionEffect(t, p.getOrNull(1)?.toIntOrNull() ?: 6000, p.getOrNull(2)?.toIntOrNull() ?: 0, true, false))
                }
            }
            val mobCtx = ctx.at(mob)
            ActionEngine.runAll(node.nodes("on-spawn"), mobCtx)
            val onTick = node.nodes("on-tick")
            var age = 0
            val lifetime = node.int("lifetime", 200)
            ActionEngine.timer(1, node.int("tick-interval", 20).toLong()) { t ->
                age += node.int("tick-interval", 20)
                if (!mob.isValid || age >= lifetime) {
                    if (mob.isValid) {
                        ActionEngine.runAll(node.nodes("on-end"), ctx.at(mob))
                        AbilityState.forget(mob)
                        mob.remove()
                    }
                    t.cancel(); return@timer
                }
                ActionEngine.runAll(onTick, ctx.at(mob))
            }
        }
    }

    private fun place(block: Block, material: Material, ticks: Long) {
        if (!block.type.isAir || block in temporary) return
        block.setType(material, false)
        temporary[block] = material
        ActionEngine.later(ticks) { restore(block) }
    }

    private fun restore(block: Block) {
        val placed = temporary.remove(block) ?: return
        if (block.type == placed) block.setType(Material.AIR, false)
    }

    /** Puts back every temporary block (plugin disable). */
    fun restoreAll() = temporary.keys.toList().forEach(::restore)

    /**
     * `wall`: a temporary wall in front of the caster. distance, width, height, material, duration
     * (ticks), y-offset, rise-interval (ticks between rows; 0 = all at once). Only fills air.
     */
    fun wall(node: Node, ctx: Ctx) {
        val caster = ctx.caster
        val flat = caster.location.direction.setY(0).let { if (it.lengthSquared() < 1e-6) Vector(1, 0, 0) else it.normalize() }
        val side = Vector(-flat.z, 0.0, flat.x)
        val center = caster.location.add(flat.clone().multiply(node.double("distance", 3.0))).add(0.0, node.double("y-offset", 0.0), 0.0)
        val material = Material.matchMaterial(node.str("material", "stone_bricks")) ?: Material.STONE_BRICKS
        val width = node.int("width", 5)
        val height = node.int("height", 3)
        val duration = node.int("duration", 100).toLong()
        val rise = node.int("rise-interval", 0).toLong()
        for (h in 0 until height) {
            val row = {
                for (w in -(width / 2)..(width - 1 - width / 2)) {
                    val loc: Location = center.clone().add(side.clone().multiply(w.toDouble())).add(0.0, h.toDouble(), 0.0)
                    place(loc.block, material, duration - rise * h)
                }
            }
            if (rise <= 0 || h == 0) row() else ActionEngine.later(rise * h, row)
        }
    }

    /** `entomb`: fills the entity's feet and head blocks (and the ring around them with `shell: true`). material, duration. */
    fun entomb(node: Node, ctx: Ctx) {
        val base = ctx.entity.location.block
        val material = Material.matchMaterial(node.str("material", "cobweb")) ?: Material.COBWEB
        val duration = node.int("duration", 200).toLong()
        val blocks = mutableListOf(base, base.getRelative(0, 1, 0))
        if (node.bool("shell", false)) {
            for (y in 0..1) for ((x, z) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) blocks += base.getRelative(x, y, z)
            blocks += base.getRelative(0, 2, 0)
        }
        blocks.forEach { place(it, material, duration) }
    }
}
