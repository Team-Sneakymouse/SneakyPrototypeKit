package net.sneakyprototypekit.ability.actions

import org.bukkit.Location
import org.bukkit.entity.AbstractArrow
import org.bukkit.entity.Arrow
import org.bukkit.entity.LivingEntity
import org.bukkit.persistence.PersistentDataType
import org.bukkit.util.Vector
import kotlin.random.Random

/** Actions that find targets and run `on-hit` actions on them. */
object TargetActions {
    private fun hits(node: Node, ctx: Ctx, point: Location, radius: Double, done: MutableSet<LivingEntity>): List<LivingEntity> =
        point.world.getNearbyLivingEntities(point, radius)
            .filter { it !in done && ActionEngine.canTarget(node, ctx, it, allowSelf = false) }

    /**
     * `beam`: an instant line from the caster. max-distance, step, hit-radius, y-offset, gravity,
     * stop-on-ground (default true), stop-on-hit (default false), can-target, trail, on-hit.
     * Each entity is hit once; the caster never.
     */
    fun beam(node: Node, ctx: Ctx) {
        val caster = ctx.caster
        val start = caster.location.add(0.0, node.double("y-offset", 1.0), 0.0)
        val dir = caster.location.direction.normalize()
        val step = node.double("step", 0.5).coerceAtLeast(0.1)
        val max = node.double("max-distance", 15.0)
        val gravity = node.double("gravity", 0.0)
        val radius = node.double("hit-radius", 0.5)
        val trail = node.nodes("trail")
        val onHit = node.nodes("on-hit")
        val done = mutableSetOf<LivingEntity>()
        val point = start.clone()
        var travelled = 0.0
        while (travelled < max) {
            point.add(dir.clone().multiply(step))
            dir.y -= gravity
            travelled += step
            if (node.bool("stop-on-ground", true) && !point.block.isPassable) break
            ActionEngine.runAll(trail, ctx.at(point))
            val found = hits(node, ctx, point, radius, done)
            found.forEach { done += it; ActionEngine.runAll(onHit, ctx.at(it)) }
            if (found.isNotEmpty() && node.bool("stop-on-hit", false)) break
        }
    }

    /**
     * `projectile`: a particle bolt. velocity (blocks per second), max-distance, hit-radius,
     * controllable (follows the caster's aim), sneak-inverts, stop-on-hit (default true),
     * trail (every tick), on-hit, on-ground.
     */
    fun projectile(node: Node, ctx: Ctx) {
        val caster = ctx.caster
        val point = caster.eyeLocation.add(0.0, node.double("y-offset", -0.2), 0.0)
        var dir = caster.location.direction.normalize()
        val speed = node.double("velocity", 10.0) / 20
        val max = node.double("max-distance", 50.0)
        val radius = node.double("hit-radius", 1.0)
        val done = mutableSetOf<LivingEntity>()
        var travelled = 0.0
        ActionEngine.timer(0, 1) { task ->
            if (!caster.isOnline || travelled >= max) { task.cancel(); return@timer }
            if (node.bool("controllable", false)) {
                dir = caster.location.direction.normalize()
                if (node.bool("sneak-inverts", false) && caster.isSneaking) dir.multiply(-1)
            }
            point.add(dir.clone().multiply(speed))
            travelled += speed
            if (!point.block.isPassable) {
                ActionEngine.runAll(node.nodes("on-ground"), ctx.at(point))
                task.cancel(); return@timer
            }
            ActionEngine.runAll(node.nodes("trail"), ctx.at(point))
            val found = hits(node, ctx, point, radius, done)
            found.forEach { done += it; ActionEngine.runAll(node.nodes("on-hit"), ctx.at(it)) }
            if (found.isNotEmpty() && node.bool("stop-on-hit", true)) task.cancel()
        }
    }

    /**
     * `chain`: hits what the caster looks at (or the caster, when `self` is allowed and nothing is),
     * then jumps to the nearest new target. range, bounces, bounce-range, bounce-delay, line
     * (particles action fields drawn between targets), on-hit.
     */
    fun chain(node: Node, ctx: Ctx) {
        val caster = ctx.caster
        val first = (caster.getTargetEntity(node.int("range", 30)) as? LivingEntity)
            ?.takeIf { ActionEngine.canTarget(node, ctx, it) }
            ?: caster.takeIf { ActionEngine.canTarget(node, ctx, it) } ?: return
        val done = mutableSetOf<LivingEntity>()
        val line = node.nodes("line").firstOrNull()
        fun hop(from: Location, target: LivingEntity, left: Int) {
            done += target
            line?.let { drawLine(it, from, target.location.add(0.0, 1.0, 0.0)) }
            ActionEngine.runAll(node.nodes("on-hit"), ctx.at(target))
            if (left <= 0) return
            val next = target.world.getNearbyLivingEntities(target.location, node.double("bounce-range", 8.0))
                .filter { it !in done && ActionEngine.canTarget(node, ctx, it) }
                .minByOrNull { it.location.distanceSquared(target.location) } ?: return
            val at = target.location.add(0.0, 1.0, 0.0)
            ActionEngine.later(node.int("bounce-delay", 3).toLong()) { if (next.isValid) hop(at, next, left - 1) }
        }
        hop(caster.eyeLocation, first, node.int("bounces", 5))
    }

    private fun drawLine(particles: Node, from: Location, to: Location) {
        if (from.world != to.world) return
        val diff = to.toVector().subtract(from.toVector())
        val steps = (diff.length() / 0.4).toInt().coerceIn(1, 200)
        val stepV = diff.multiply(1.0 / steps)
        val p = from.clone()
        repeat(steps) { EffectActions.spawn(particles, p.add(stepV), particles.int("count", 2)) }
    }

    /**
     * `area`: everything around the action's location. radius, vertical-radius, can-target,
     * entity-types (optional filter), on-target.
     */
    fun area(node: Node, ctx: Ctx) {
        val r = node.double("radius", 5.0)
        val types = node.strings("entity-types").map { it.uppercase() }
        ctx.location.world.getNearbyLivingEntities(ctx.location, r, node.double("vertical-radius", r), r)
            .filter { ActionEngine.canTarget(node, ctx, it) && (types.isEmpty() || it.type.name in types) }
            .forEach { ActionEngine.runAll(node.nodes("on-target"), ctx.at(it)) }
    }

    /**
     * `arrow-rain`: rains arrows on the spot the caster looks at. range, radius, height, volleys,
     * interval (ticks), arrows (per volley), damage, speed, on-volley (actions at the spot).
     * Arrows can't be picked up, vanish after 5 s and never hit the caster.
     */
    fun arrowRain(node: Node, ctx: Ctx) {
        val caster = ctx.caster
        val range = node.int("range", 40)
        val spot = (caster.getTargetBlockExact(range)?.location?.add(0.5, 1.0, 0.5)
            ?: caster.location.add(caster.location.direction.setY(0).normalize().multiply(range.toDouble())))
        val radius = node.double("radius", 4.0)
        var volleys = node.int("volleys", 10)
        ActionEngine.timer(0, node.int("interval", 10).toLong()) { task ->
            if (volleys-- <= 0 || !caster.isOnline) { task.cancel(); return@timer }
            ActionEngine.runAll(node.nodes("on-volley"), ctx.at(spot))
            repeat(node.int("arrows", 15)) {
                val from = spot.clone().add(Random.nextDouble(-radius, radius), node.double("height", 20.0), Random.nextDouble(-radius, radius))
                val arrow = spot.world.spawn(from, Arrow::class.java) { a ->
                    a.shooter = caster
                    a.pickupStatus = AbstractArrow.PickupStatus.DISALLOWED
                    a.damage = node.double("damage", 1.0)
                    a.isCritical = node.bool("critical", true)
                    a.velocity = Vector(Random.nextDouble(-0.05, 0.05), -node.double("speed", 2.0), Random.nextDouble(-0.05, 0.05))
                    a.persistentDataContainer.set(AbilityState.ARROW_KEY, PersistentDataType.STRING, caster.uniqueId.toString())
                }
                ActionEngine.later(100) { if (arrow.isValid) arrow.remove() }
            }
        }
    }
}
