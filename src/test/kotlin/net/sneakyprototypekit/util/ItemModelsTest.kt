package net.sneakyprototypekit.util

import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import java.io.InputStreamReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ItemModelsTest {
    private fun yaml(text: String) = YamlConfiguration().apply { loadFromString(text) }

    @Test
    fun `hardcoded integers map to the item models the pack ships`() {
        assertEquals(ItemModels.KIT, ItemModels.fromLegacy(Material.RABBIT_FOOT, 129))
        assertEquals(ItemModels.GUI_MAIN, ItemModels.fromLegacy(Material.JIGSAW, 3047))
        assertEquals(ItemModels.GUI_TYPE, ItemModels.fromLegacy(Material.JIGSAW, 3048))
        assertEquals(ItemModels.GUI_ABILITY, ItemModels.fromLegacy(Material.JIGSAW, 3049))
        assertEquals(ItemModels.GUI_ICON, ItemModels.fromLegacy(Material.JIGSAW, 3050))
    }

    @Test
    fun `a model reverses to the integers that used to show it`() {
        assertTrue(3050 in ItemModels.legacyInts(Material.JIGSAW, ItemModels.GUI_ICON))
        assertTrue(ItemModels.legacyInts(Material.JIGSAW, "lom:does/not_exist").isEmpty())
    }

    @Test
    fun `icon_data stored as an integer migrates to an id and ids round trip`() {
        assertEquals(ItemModels.Icon(Material.RABBIT_FOOT, ItemModels.KIT), ItemModels.decode("RABBIT_FOOT,129"))
        val icon = ItemModels.Icon(Material.POTATO, "lom:items/prototype-food/07_bread")
        assertEquals("POTATO,lom:items/prototype-food/07_bread", ItemModels.encode(icon))
        assertEquals(icon, ItemModels.decode(ItemModels.encode(icon)))
        assertEquals(icon, ItemModels.decode("POTATO,2"))
    }

    @Test
    fun `unreadable icon_data is rejected rather than guessed`() {
        assertNull(ItemModels.decode(null))
        assertNull(ItemModels.decode("POTATO"))
        assertNull(ItemModels.decode("NOT_A_MATERIAL,1"))
        assertNull(ItemModels.decode("RABBIT_FOOT,999999"))
        assertNull(ItemModels.decode("POTATO,Not A Key"))
    }

    @Test
    fun `icon lists take ids and still expand old integers and ranges`() {
        val expected = listOf(
            ItemModels.fromLegacy(Material.POTATO, 1)!!,
            ItemModels.fromLegacy(Material.POTATO, 2)!!,
            ItemModels.fromLegacy(Material.POTATO, 3)!!,
        )
        assertEquals(expected, ItemModels.iconModels(Material.POTATO, listOf("1-3")))
        assertEquals(listOf("lom:a/b") + expected.take(1), ItemModels.iconModels(Material.POTATO, listOf("lom:a/b", "1")))
        // integers without a pack model drop out instead of becoming a broken icon
        assertEquals(emptyList(), ItemModels.iconModels(Material.POTATO, listOf("999999")))
    }

    @Test
    fun `config keys prefer the item model and fall back to the old integer`() {
        val config = yaml(
            """
            a:
              icon-material: jigsaw
              icon-item-model: "lom:icons/x"
              icon-custom-model-data: 3050
            b:
              icon-material: jigsaw
              icon-custom-model-data: 3050
            c:
              icon-material: jigsaw
            kit:
              item-model: "lom:tools/kit"
            oldkit:
              model-data: 129
            """.trimIndent(),
        )
        assertEquals("lom:icons/x", ItemModels.abilityIcon(config.getConfigurationSection("a")))
        assertEquals(ItemModels.GUI_ICON, ItemModels.abilityIcon(config.getConfigurationSection("b")))
        assertNull(ItemModels.abilityIcon(config.getConfigurationSection("c")))
        assertNull(ItemModels.abilityIcon(null))
        assertEquals("lom:tools/kit", ItemModels.kitModel(config.getConfigurationSection("kit"), Material.RABBIT_FOOT))
        assertEquals(ItemModels.KIT, ItemModels.kitModel(config.getConfigurationSection("oldkit"), Material.RABBIT_FOOT))
    }

    @Test
    fun `bundled default config uses item models only and every listed id is a valid key`() {
        val config = YamlConfiguration.loadConfiguration(
            InputStreamReader(javaClass.classLoader.getResourceAsStream("config.yml")!!),
        )
        val raw = javaClass.classLoader.getResourceAsStream("config.yml")!!.readBytes().decodeToString()
        assertTrue(!raw.contains("custom-model-data") && !raw.contains("model-data"), "default config must not carry integer keys")
        assertNotNull(ItemModels.kitModel(config.getConfigurationSection("prototype-kit"), Material.RABBIT_FOOT))
        for (name in listOf("item-icons", "food-icons", "drink-icons")) {
            val section = config.getConfigurationSection(name)!!
            for (key in section.getKeys(false)) {
                val entries = section.getStringList(key)
                assertTrue(entries.isNotEmpty(), "$name.$key is empty")
                assertEquals(entries.size, ItemModels.iconModels(Material.matchMaterial(key)!!, entries).size, "$name.$key has invalid ids")
            }
        }
        for (key in config.getConfigurationSection("abilities")!!.getKeys(false)) {
            assertNotNull(ItemModels.abilityIcon(config.getConfigurationSection("abilities.$key")), "ability $key has no icon")
        }
    }
}
