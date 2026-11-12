package net.sneakyprototypekit.ability.actions

import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.title.Title
import net.sneakyprototypekit.SneakyPrototypeKit
import net.sneakyprototypekit.util.TextUtility
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Particle
import org.bukkit.Registry
import org.bukkit.SoundCategory
import org.bukkit.inventory.ItemStack
import java.time.Duration
import kotlin.math.cos
import kotlin.math.sin

/** Visual, sound and text actions (the MagicSpells `effects:` blocks). */
object EffectActions {
    private val plugin get() = SneakyPrototypeKit.getInstance()

    fun particle(name: String): Particle? =
        Registry.PARTICLE_TYPE.get(NamespacedKey.minecraft(name.lowercase().removePrefix("minecraft:")))

    private fun color(hex: String?): Color =
        hex?.removePrefix("#")?.toIntOrNull(16)?.let { Color.fromRGB(it and 0xFFFFFF) } ?: Color.WHITE

    /** Data a particle needs (colour, block, item, ...); null when it takes none. */
    private fun data(particle: Particle, node: Node): Any? {
        val type = particle.dataType
        val hex = node.strOrNull("color")
        val material = Material.matchMaterial(node.str("block", "stone")) ?: Material.STONE
        return when {
            type == Void::class.java -> null
            type == Color::class.java -> color(hex)
            type == Particle.DustOptions::class.java -> Particle.DustOptions(color(hex), node.double("size", 1.0).toFloat())
            type == Particle.DustTransition::class.java ->
                Particle.DustTransition(color(hex), color(node.strOrNull("to-color") ?: hex), node.double("size", 1.0).toFloat())
            type == org.bukkit.block.data.BlockData::class.java -> material.createBlockData()
            type == ItemStack::class.java -> ItemStack(material)
            type == java.lang.Float::class.java || type == Float::class.javaPrimitiveType -> 0f
            type == java.lang.Integer::class.java || type == Int::class.javaPrimitiveType -> 0
            type.simpleName == "Spell" -> Particle.Spell(color(hex), 1f)
            else -> null
        }
    }

    fun spawn(node: Node, loc: Location, count: Int = node.int("count", 10)) {
        val p = particle(node.str("particle", "crit")) ?: return
        val xz = node.double("spread-xz", node.double("spread", 0.2))
        val y = node.double("spread-y", node.double("spread", 0.2))
        loc.world.spawnParticle(p, loc, count, xz, y, xz, node.double("speed", 0.0), data(p, node), true)
    }

    /** `particles`: particle, count, spread-xz, spread-y, speed, color, block, size, at, height. */
    fun particles(node: Node, ctx: Ctx) = spawn(node, ctx.place(node))

    /**
     * `burst`: the 3-ring MagicSpells "effect spell" pattern. particle, sound, counts [a,b,c],
     * spreads [.2,.5,1], heights [1,1.5,2], gap (ticks between rings, default 3), speed.
     */
    fun burst(node: Node, ctx: Ctx) {
        node.strOrNull("sound")?.let { playSound(ctx.place(node), it, node, ctx) }
        val counts = node.doubles("counts", listOf(100.0, 100.0, 100.0))
        val spreads = node.doubles("spreads", listOf(0.2, 0.5, 1.0))
        val ys = node.doubles("spreads-y", spreads)
        val heights = node.doubles("heights", listOf(1.0, 1.5, 2.0))
        val gap = node.int("gap", 3).toLong()
        val p = particle(node.str("particle", "crit")) ?: return
        val pdata = data(p, node)
        for (i in counts.indices) {
            val ring = {
                val loc = ctx.place(node).add(0.0, heights.getOrElse(i) { 1.0 }, 0.0)
                val xz = spreads.getOrElse(i) { 1.0 }
                loc.world.spawnParticle(p, loc, counts[i].toInt(), xz, ys.getOrElse(i) { xz }, xz, node.double("speed", 0.1), pdata, true)
            }
            if (i == 0) ring() else ActionEngine.later(gap * i, ring)
        }
    }

    /**
     * `ring`: stand-in for the effectlib Circle/Vortex effects. particle, radius, points, period,
     * iterations, rise (blocks per iteration, makes a vortex), spin (radians per iteration).
     */
    fun ring(node: Node, ctx: Ctx) {
        val radius = node.double("radius", 1.0)
        val points = node.int("points", 12)
        val rise = node.double("rise", 0.0)
        val spin = node.double("spin", 0.3)
        var i = 0
        val iterations = node.int("iterations", 20)
        ActionEngine.timer(0, node.int("period", 1).toLong()) { task ->
            if (i >= iterations || !ctx.entity.isValid) { task.cancel(); return@timer }
            val center = ctx.place(node).add(0.0, rise * i, 0.0)
            for (k in 0 until points) {
                val a = spin * i + 2 * Math.PI * k / points
                spawn(node, center.clone().add(cos(a) * radius, 0.0, sin(a) * radius), node.int("count", 1))
            }
            i++
        }
    }

    private fun playSound(loc: Location, sound: String, node: Node, ctx: Ctx) {
        val volume = node.double("volume", 1.0).toFloat()
        val pitch = node.double("pitch", 1.0).toFloat()
        if (node.bool("personal", false)) ctx.caster.playSound(loc, sound, SoundCategory.PLAYERS, volume, pitch)
        else loc.world.playSound(loc, sound, SoundCategory.PLAYERS, volume, pitch)
    }

    /** `sound`: sound, volume, pitch, personal (only the caster hears it), at. */
    fun sound(node: Node, ctx: Ctx) = playSound(ctx.place(node), node.str("sound", "entity.experience_orb.pickup"), node, ctx)

    /**
     * `title-animation`: the MagicSpells fontanimation effect. font (`lom:voidhole`), start-frame,
     * interval, duration (ticks), fade-out. Frame n is glyph U+E000+n in that font, shown as subtitle.
     */
    fun titleAnimation(node: Node, ctx: Ctx) {
        val player = ctx.entity as? org.bukkit.entity.Player ?: return
        val font = Key.key(node.str("font", "minecraft:default"))
        val interval = node.int("interval", 1).coerceAtLeast(1)
        val frames = (node.int("duration", 20) / interval).coerceAtLeast(1)
        val fadeOut = node.int("fade-out", 0)
        var frame = node.int("start-frame", 0)
        var shown = 0
        fun ticks(t: Int) = Duration.ofMillis(t * 50L)
        ActionEngine.timer(0, interval.toLong()) { task ->
            if (!player.isOnline) { task.cancel(); return@timer }
            if (shown >= frames) {
                if (fadeOut < 1) player.clearTitle()
                task.cancel(); return@timer
            }
            val glyph = Component.text((0xE000 + frame % 1000).toChar().toString()).font(font)
            val out = if (shown == frames - 1) fadeOut else 20
            player.showTitle(Title.title(Component.empty(), glyph, Title.Times.times(ticks(0), ticks(interval + 19), ticks(out))))
            frame++; shown++
        }
    }

    private fun fill(text: String, ctx: Ctx) =
        text.replace("[playerName]", ctx.caster.name).replace("[targetName]", ctx.entity.name)

    /** `message`: text (`&` colours) sent to the entity. */
    fun message(node: Node, ctx: Ctx) = ctx.entity.sendMessage(TextUtility.convertToComponent(fill(node.str("text", ""), ctx)))

    /** `command`: console command from config. [playerName] = caster, [targetName] = entity. */
    fun command(node: Node, ctx: Ctx) {
        // Console dispatch: the command comes from config only, never from player-typed text.
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), fill(node.str("command", ""), ctx))
    }

    /** `player-command`: the entity (a player) runs the command itself, like CMI `sudo`. */
    fun playerCommand(node: Node, ctx: Ctx) {
        (ctx.entity as? org.bukkit.entity.Player)?.performCommand(fill(node.str("command", ""), ctx).removePrefix("/"))
    }
}
