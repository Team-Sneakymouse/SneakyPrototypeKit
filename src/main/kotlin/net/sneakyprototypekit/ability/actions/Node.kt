package net.sneakyprototypekit.ability.actions

import org.bukkit.Location
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player

/** One action from an ability's `actions:` list. Read-only view over the YAML map. */
class Node(private val map: Map<*, *>) {
    val type: String get() = str("type", "").lowercase()

    fun has(key: String) = map.containsKey(key)
    fun str(key: String, def: String): String = map[key]?.toString() ?: def
    fun strOrNull(key: String): String? = map[key]?.toString()
    fun double(key: String, def: Double): Double = when (val v = map[key]) {
        is Number -> v.toDouble()
        is String -> v.toDoubleOrNull() ?: def
        else -> def
    }
    fun int(key: String, def: Int): Int = double(key, def.toDouble()).toInt()
    fun bool(key: String, def: Boolean): Boolean = when (val v = map[key]) {
        is Boolean -> v
        is String -> v.toBooleanStrictOrNull() ?: def
        else -> def
    }
    fun strings(key: String): List<String> = when (val v = map[key]) {
        is List<*> -> v.mapNotNull { it?.toString() }
        null -> emptyList()
        else -> v.toString().split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }
    fun doubles(key: String, def: List<Double>): List<Double> = when (val v = map[key]) {
        is List<*> -> v.mapNotNull { (it as? Number)?.toDouble() ?: it?.toString()?.toDoubleOrNull() }
        else -> def
    }
    fun nodes(key: String): List<Node> = list(map[key])

    companion object {
        fun list(raw: Any?): List<Node> = (raw as? List<*>)?.filterIsInstance<Map<*, *>>()?.map(::Node) ?: emptyList()
    }
}

/**
 * Where an action runs. [entity] is who it applies to (the caster, or a hit target inside `on-hit`);
 * [location] is where effects show (the entity, or a beam/projectile point).
 */
data class Ctx(val caster: Player, val entity: LivingEntity, val location: Location) {
    fun at(entity: LivingEntity) = Ctx(caster, entity, entity.location)
    fun at(point: Location) = Ctx(caster, entity, point.clone())

    /** `at: here|caster|target` plus `height`. */
    fun place(node: Node): Location {
        val base = when (node.str("at", "here").lowercase()) {
            "caster" -> caster.location
            "target", "entity" -> entity.location
            else -> location.clone()
        }
        return base.add(0.0, node.double("height", 0.0), 0.0)
    }
}
