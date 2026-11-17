package net.sneakyprototypekit.ability

import org.bukkit.configuration.file.YamlConfiguration
import java.io.InputStreamReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CastByIdTest {
    private val config = YamlConfiguration().apply {
        loadFromString(
            """
            abilities:
              native: {actions: [{type: burst}, {type: sound}]}
              console: {command-console: "cast forcecast [playerName] x"}
              both: {command-console: "x", actions: [{type: leap}]}
            """.trimIndent(),
        )
    }

    @Test fun nativeAbilityHasItsActions() {
        assertEquals(listOf("burst", "sound"), AbilityManager.nativeActions(config, "native").map { it.type })
        assertEquals(listOf("leap"), AbilityManager.nativeActions(config, "both").map { it.type })
    }

    @Test fun consoleOnlyUnknownAndBadIdsHaveNone() {
        assertTrue(AbilityManager.nativeActions(config, "console").isEmpty())
        assertTrue(AbilityManager.nativeActions(config, "missing").isEmpty())
        assertTrue(AbilityManager.nativeActions(config, "").isEmpty())
        assertTrue(AbilityManager.nativeActions(config, "native.actions").isEmpty())
    }

    @Test fun everyScienceAbilityIsNativeInTheServerConfig() {
        val server = YamlConfiguration.loadConfiguration(
            InputStreamReader(javaClass.classLoader.getResourceAsStream("server-config.yml")!!),
        )
        val science = listOf(
            "prototype-firebreath-beam", "prototype-effectSpell-splash", "prototype-effectSpell-selfDestruct",
            "prototype-jumpback-leap", "prototype-rocketjump-leap", "prototype-bigleap-leap", "prototype-icebeam-beam",
            "prototype-darkbubbles-beam", "prototype-dizzyblast-beam", "prototype-procbeam-beam",
            "prototype-sleepybeam-beam", "prototype-bubblebeam-beam", "prototype-levitation-beam",
            "prototype-healbeam-beam", "prototype-darknessbeam-beam", "prototype-poisonbreath-beam",
            "prototype-shieldingbeam-beam", "prototype-whippull-beam", "prototype-blinkstep-blink",
            "prototype-devolutionspray-multi", "prototype-conjurepetals-multi", "prototype-conjurebones-multi",
            "prototype-forcepush-solarflare", "prototype-magnet-vacuumlootMulti",
        )
        assertEquals(emptyList(), science.filter { AbilityManager.nativeActions(server, it).isEmpty() })
    }
}
