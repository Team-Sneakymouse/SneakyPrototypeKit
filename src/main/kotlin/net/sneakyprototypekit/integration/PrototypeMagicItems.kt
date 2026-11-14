package net.sneakyprototypekit.integration

import com.sneakyitems.api.SneakyItemsAPI
import com.sneakyitems.util.magicitems.MagicItemData.MagicItemAttribute
import net.sneakyprototypekit.SneakyPrototypeKit
import net.sneakyprototypekit.ability.TestKit
import net.sneakyprototypekit.util.TextUtility
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

/**
 * SneakyItems registration for the prototype kit, test kit, and player-finalized items.
 *
 * Finalized items vary by material (icon config), and [MagicItemAttribute.TYPE] cannot be ignored, so each
 * icon material gets its own def (`item-prototypekit-created-<material>`). Name, lore, custom model data and
 * max stack stay per-copy via ignored-attributes; ability/creator PDC rides as extra keys.
 */
object PrototypeMagicItems {
    const val PDC_NAMESPACE = "magicspells"
    const val KIT = "item-prototypekit"
    const val TEST_KIT = "item-prototypekit-test"
    const val CREATED_PREFIX = "item-prototypekit-created-"

    fun createdName(material: Material): String = CREATED_PREFIX + material.name.lowercase()

    fun isCreated(stack: ItemStack?): Boolean {
        val key = SneakyItemsAPI.keyOf(stack) ?: return false
        return key.startsWith(CREATED_PREFIX)
    }

    fun isKit(stack: ItemStack?): Boolean = SneakyItemsAPI.isItem(stack, KIT)

    fun isTestKit(stack: ItemStack?): Boolean = SneakyItemsAPI.isItem(stack, TEST_KIT)

    /** Registers (or replaces) this plugin's defs and legacy mappings from the live config. */
    fun register(plugin: SneakyPrototypeKit) {
        SneakyItemsAPI.clearOwner(plugin)
        var n = 0

        if (registerKit(plugin)) n++
        if (registerTestKit(plugin)) n++
        n += registerCreatedMaterials(plugin)

        SneakyItemsAPI.registerLegacyKey(plugin, plugin.PROTOTYPE_KIT_KEY, KIT)
        SneakyItemsAPI.registerLegacyKey(plugin, TestKit.INDEX_KEY, TEST_KIT)
        // Finalized copies: stamped in [stampCreated]. Pre-migration stacks match material+CMD patterns above.
        // CREATED_ITEM_KEY is per-copy marker only (TYPE differs per material, so one legacy key cannot map them).

        plugin.logger.info("Registered $n prototype-kit magic item(s) with SneakyItems.")
    }

    fun clear(plugin: SneakyPrototypeKit) {
        SneakyItemsAPI.clearOwner(plugin)
    }

    /**
     * Stamps a finalized stack as the material's created def and marks [SneakyPrototypeKit.CREATED_ITEM_KEY].
     * No-op when SneakyItems has no matching def (icon material not in config yet).
     */
    fun stampCreated(stack: ItemStack) {
        val name = createdName(stack.type)
        if (SneakyItemsAPI.getByInternalName(name) == null) return
        stack.editMeta { meta ->
            meta.persistentDataContainer.set(
                SneakyPrototypeKit.getInstance().CREATED_ITEM_KEY,
                PersistentDataType.BYTE,
                1,
            )
            meta.persistentDataContainer.set(
                com.sneakyitems.util.magicitems.MagicItemBehaviorKeys.magicItem(),
                PersistentDataType.STRING,
                name,
            )
        }
    }

    private fun registerKit(plugin: SneakyPrototypeKit): Boolean {
        val template = kitTemplate(plugin)
        val magic = SneakyItemsAPI.registerFromStack(plugin, PDC_NAMESPACE, KIT, template) ?: return false
        // Draft name/lore/icon live in plugin PDC on the kit; look stays the config template.
        magic.magicItemData.ignoredAttributes.add(MagicItemAttribute.MAX_STACK_SIZE)
        return true
    }

    private fun registerTestKit(plugin: SneakyPrototypeKit): Boolean {
        val template = ItemStack(Material.RABBIT_FOOT).apply {
            editMeta {
                it.setCustomModelData(129)
                it.displayName(TextUtility.convertToComponent("&dTest Kit"))
                it.lore(listOf(TextUtility.convertToComponent("&7Ability selector")))
                it.setMaxStackSize(1)
            }
        }
        val magic = SneakyItemsAPI.registerFromStack(plugin, PDC_NAMESPACE, TEST_KIT, template) ?: return false
        magic.magicItemData.ignoredAttributes.add(MagicItemAttribute.NAME)
        magic.magicItemData.ignoredAttributes.add(MagicItemAttribute.LORE)
        magic.magicItemData.ignoredAttributes.add(MagicItemAttribute.CUSTOM_MODEL_DATA)
        magic.magicItemData.ignoredAttributes.add(MagicItemAttribute.MAX_STACK_SIZE)
        return true
    }

    private fun registerCreatedMaterials(plugin: SneakyPrototypeKit): Int {
        val materials = linkedSetOf<Material>()
        val cmdByMaterial = linkedMapOf<Material, MutableSet<Int>>()
        for (sectionName in listOf("item-icons", "food-icons", "drink-icons")) {
            val section = plugin.config.getConfigurationSection(sectionName) ?: continue
            for (materialKey in section.getKeys(false)) {
                val material = Material.matchMaterial(materialKey) ?: continue
                materials += material
                val cmds = cmdByMaterial.getOrPut(material) { linkedSetOf() }
                for (entry in section.getStringList(materialKey)) {
                    if (entry.contains("-")) {
                        val parts = entry.split("-", limit = 2)
                        val start = parts[0].toIntOrNull() ?: continue
                        val end = parts[1].toIntOrNull() ?: continue
                        for (cmd in start..end) cmds += cmd
                    } else {
                        entry.toIntOrNull()?.let { cmds += it }
                    }
                }
            }
        }
        // Always have a fallback def so an unknown/legacy material can still be stamped by hand.
        materials += Material.STONE

        var count = 0
        for (material in materials) {
            val name = createdName(material)
            val template = ItemStack(material).apply {
                editMeta {
                    it.displayName(TextUtility.convertToComponent("&cPrototype Item"))
                    it.lore(listOf(TextUtility.convertToComponent("&7Player-designed ability item")))
                    it.setMaxStackSize(99)
                }
            }
            val magic = SneakyItemsAPI.registerFromStack(plugin, PDC_NAMESPACE, name, template) ?: continue
            magic.magicItemData.ignoredAttributes.add(MagicItemAttribute.NAME)
            magic.magicItemData.ignoredAttributes.add(MagicItemAttribute.LORE)
            magic.magicItemData.ignoredAttributes.add(MagicItemAttribute.CUSTOM_MODEL_DATA)
            magic.magicItemData.ignoredAttributes.add(MagicItemAttribute.MAX_STACK_SIZE)
            count++

            // Legacy: pre-migration copies are known by material + custom model data from the icon lists.
            // Patterns require CMD so plain vanilla stacks of the same material are not stamped.
            val cmds = cmdByMaterial[material].orEmpty()
            for (cmd in cmds) {
                val matKey = material.name.lowercase()
                SneakyItemsAPI.registerLegacyPattern(
                    plugin,
                    name,
                    """$matKey{"custom-model-data":$cmd}""",
                )
            }
        }
        return count
    }

    private fun kitTemplate(plugin: SneakyPrototypeKit): ItemStack {
        val config = plugin.config.getConfigurationSection("prototype-kit")
        val material = Material.matchMaterial(config?.getString("material", "NETHER_STAR") ?: "NETHER_STAR")
            ?: Material.NETHER_STAR
        return ItemStack(material).apply {
            editMeta { meta ->
                meta.displayName(
                    TextUtility.convertToComponent(config?.getString("name", "&ePrototype Kit") ?: "&ePrototype Kit"),
                )
                config?.getInt("model-data", -1)?.takeIf { it > 0 }?.let { meta.setCustomModelData(it) }
                val lore = config?.getStringList("lore")
                    ?.filterNot { it.contains("Shift + Right Click") }
                    ?.map { TextUtility.convertToComponent(it) }
                    .orEmpty()
                meta.lore(lore)
                meta.setMaxStackSize(1)
            }
        }
    }
}
