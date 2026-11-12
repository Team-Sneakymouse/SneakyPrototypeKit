package net.sneakyprototypekit.ability.actions

import org.bukkit.Location
import org.bukkit.entity.Item
import org.bukkit.entity.LivingEntity
import org.bukkit.util.Vector
import kotlin.math.min

/** Movement actions. Forces use the MagicSpells scale: config value / 10 = blocks per tick. */
object MoveActions {
    /** `leap`: forward, upward, cancel-fall (default true). Negative forward leaps backwards. */
    fun leap(node: Node, ctx: Ctx) {
        val e = ctx.entity
        val dir = e.location.direction.setY(0)
        val v = (if (dir.lengthSquared() < 1e-6) Vector() else dir.normalize().multiply(node.double("forward", 4.0) / 10))
            .setY(node.double("upward", 1.5) / 10)
        e.velocity = v
        if (node.bool("cancel-fall", true)) AbilityState.noFall(e)
    }

    /** `forcepush`: everyone within radius of the caster is pushed away. force, vertical, max-vertical. */
    fun forcepush(node: Node, ctx: Ctx) {
        val center = ctx.location
        val r = node.double("radius", 5.0)
        center.world.getNearbyLivingEntities(center, r).forEach { t ->
            if (t == ctx.caster || !ActionEngine.canTarget(node, ctx, t, allowSelf = false)) return@forEach
            val away = t.location.toVector().subtract(center.toVector())
            val v = (if (away.lengthSquared() < 1e-6) Vector(0, 0, 0) else away.normalize()).multiply(node.double("force", 20.0) / 10)
            v.y = min(v.y + node.double("vertical", 5.0) / 10, node.double("max-vertical", 5.0) / 10)
            t.velocity = t.velocity.add(v)
            ActionEngine.runAll(node.nodes("on-hit"), ctx.at(t))
        }
    }

    /** `toss`: throws the entity away from the caster. horizontal, vertical. */
    fun toss(node: Node, ctx: Ctx) {
        val t = ctx.entity
        if (t == ctx.caster) return
        val away = t.location.toVector().subtract(ctx.caster.location.toVector()).setY(0)
        if (away.lengthSquared() < 1e-6) return
        t.velocity = away.normalize().multiply(node.double("horizontal", 20.0) / 10).setY(node.double("vertical", 1.0) / 10)
    }

    /**
     * `levitate`: holds the entity in front of the caster (MagicSpells LevitateSpell). duration (ticks),
     * distance-change (blocks per tick, pulls it in), min-distance, max-distance, y-offset, cancel-on-damage.
     */
    fun levitate(node: Node, ctx: Ctx) {
        val t = ctx.entity
        val caster = ctx.caster
        if (t == caster) return
        var distance = caster.location.distance(t.location)
        val min = node.double("min-distance", 1.0)
        val maxSq = node.double("max-distance", 50.0).let { it * it }
        val change = node.double("distance-change", 0.0)
        val duration = node.int("duration", 100)
        val startHealth = t.health
        var ticks = 0
        ActionEngine.timer(0, 1) { task ->
            val stop = ticks++ >= duration || !t.isValid || !caster.isOnline || t.world != caster.world ||
                caster.location.distanceSquared(t.location) > maxSq ||
                (node.bool("cancel-on-damage", true) && t.health < startHealth)
            if (stop) { task.cancel(); return@timer }
            if (change != 0.0 && distance > min) distance = (distance - change).coerceAtLeast(min)
            val wanted = caster.eyeLocation.toVector().add(caster.location.direction.multiply(distance))
                .add(Vector(0.0, node.double("y-offset", 0.0), 0.0))
            t.velocity = wanted.subtract(t.location.toVector()).multiply(1 / 25.0 + 0.1)
            t.fallDistance = 0f
        }
    }

    /** `blink`: teleports the caster to the block they look at. range, pass-through-ceiling. */
    fun blink(node: Node, ctx: Ctx) {
        val p = ctx.caster
        val hit = p.rayTraceBlocks(node.double("range", 50.0)) ?: return
        val block = hit.hitBlock ?: return
        val face = hit.hitBlockFace ?: return
        var spot = block.getRelative(face)
        if (face == org.bukkit.block.BlockFace.DOWN && node.bool("pass-through-ceiling", false)) spot = block.getRelative(org.bukkit.block.BlockFace.UP)
        if (face != org.bukkit.block.BlockFace.UP && face != org.bukkit.block.BlockFace.DOWN) {
            // Hit a wall: stand on whatever is below that spot.
            while (spot.y > spot.world.minHeight && spot.getRelative(org.bukkit.block.BlockFace.DOWN).isPassable) spot = spot.getRelative(org.bukkit.block.BlockFace.DOWN)
        }
        if (!spot.isPassable || !spot.getRelative(org.bukkit.block.BlockFace.UP).isPassable) {
            if (face == org.bukkit.block.BlockFace.DOWN && node.bool("pass-through-ceiling", false)) {
                var up = block.getRelative(org.bukkit.block.BlockFace.UP)
                var tries = 0
                while (tries++ < 32 && !(up.isPassable && up.getRelative(org.bukkit.block.BlockFace.UP).isPassable)) up = up.getRelative(org.bukkit.block.BlockFace.UP)
                if (tries >= 32) return
                spot = up
            } else return
        }
        val to = spot.location.add(0.5, 0.0, 0.5)
        to.yaw = p.location.yaw; to.pitch = p.location.pitch
        ActionEngine.runAll(node.nodes("on-depart"), ctx.at(p.location))
        p.fallDistance = 0f
        p.teleport(to)
    }

    /** `magnet`: pulls dropped items within radius toward the caster. radius, velocity. */
    fun magnet(node: Node, ctx: Ctx) {
        val p = ctx.caster
        val r = node.double("radius", 20.0)
        p.world.getNearbyEntitiesByType(Item::class.java, p.location, r).forEach { item ->
            val to = p.location.toVector().add(Vector(0.0, 0.5, 0.0)).subtract(item.location.toVector())
            if (to.lengthSquared() > 1e-6) item.velocity = to.normalize().multiply(node.double("velocity", 0.3))
        }
    }

    /**
     * `flight`: carries the caster to x/z in their world (MagicSpells FlightPathSpell): rises to
     * altitude, cruises at speed (blocks per tick), drops at the end without fall damage. trail actions
     * run at the caster every `trail-interval` ticks.
     */
    fun flight(node: Node, ctx: Ctx) {
        val p = ctx.caster
        val dest = Location(p.world, node.double("x", 0.0) + 0.5, 0.0, node.double("z", 0.0) + 0.5)
        val altitude = node.double("altitude", 100.0)
        val speed = node.double("speed", 1.0)
        val trail = node.nodes("trail")
        val trailEvery = node.int("trail-interval", 2)
        var ticks = 0
        AbilityState.noFall(p, 20 * 120)
        ActionEngine.timer(0, 1) { task ->
            if (!p.isOnline || p.isDead || ticks++ > 20 * 120 || p.world != dest.world) { task.cancel(); return@timer }
            val here = p.location
            val flat = Vector(dest.x - here.x, 0.0, dest.z - here.z)
            if (flat.length() < 1.5) {
                p.velocity = Vector(0.0, -0.5, 0.0)
                AbilityState.noFall(p)
                task.cancel(); return@timer
            }
            val climb = ((altitude - here.y) / 10).coerceIn(-speed, speed)
            val v = if (here.y < altitude - 3 && ticks < 200) Vector(0.0, speed, 0.0)
            else flat.normalize().multiply(speed).setY(climb)
            p.velocity = v
            p.fallDistance = 0f
            if (trail.isNotEmpty() && ticks % trailEvery == 0) ActionEngine.runAll(trail, ctx.at(p.location.subtract(v)))
        }
    }
}
