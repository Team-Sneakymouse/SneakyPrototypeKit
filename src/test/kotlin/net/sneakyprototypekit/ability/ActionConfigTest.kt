package net.sneakyprototypekit.ability

import net.sneakyprototypekit.ability.actions.ActionEngine
import net.sneakyprototypekit.ability.actions.Node
import org.bukkit.configuration.file.YamlConfiguration
import java.io.InputStreamReader
import kotlin.test.Test
import kotlin.test.assertTrue

class ActionConfigTest {
    private val nested = listOf(
        "trail", "on-hit", "on-target", "on-tick", "on-spawn", "on-end", "on-jump", "on-start",
        "on-volley", "line", "on-success", "on-ground", "on-depart",
    )

    private fun walk(nodes: List<Node>, path: String, bad: MutableList<String>) {
        nodes.forEachIndexed { i, n ->
            if (n.type !in ActionEngine.types) bad += "$path[$i]: '${n.type}'"
            nested.forEach { key -> walk(n.nodes(key), "$path[$i].$key", bad) }
        }
    }

    private fun check(resource: String, minimumNative: Int) {
        val yaml = YamlConfiguration.loadConfiguration(InputStreamReader(javaClass.classLoader.getResourceAsStream(resource)!!))
        val abilities = yaml.getConfigurationSection("abilities")!!
        val bad = mutableListOf<String>()
        var native = 0
        for (key in abilities.getKeys(false)) {
            val section = abilities.getConfigurationSection(key)!!
            val actions = Node.list(section.getList("actions"))
            assertTrue(actions.isNotEmpty() || section.contains("command-console"), "$key has neither actions nor command-console")
            if (actions.isNotEmpty()) native++
            walk(actions, key, bad)
        }
        assertTrue(bad.isEmpty(), "unknown action types: $bad")
        assertTrue(native >= minimumNative, "$resource: only $native native abilities")
    }

    @Test fun defaultConfigUsesKnownActions() = check("config.yml", 3)

    @Test fun serverConfigIsFullyPorted() = check("server-config.yml", 80)
}
