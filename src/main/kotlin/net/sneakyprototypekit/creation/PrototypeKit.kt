package net.sneakyprototypekit.creation

import com.sneakyitems.api.SneakyItemsAPI
import net.kyori.adventure.text.Component
import net.sneakyprototypekit.SneakyPrototypeKit
import net.sneakyprototypekit.integration.PrototypeMagicItems
import net.sneakyprototypekit.util.TextUtility
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType

/**
 * Manages the creation and updating of prototype kit items.
 */
object PrototypeKit {
    /**
     * Creates a new prototype kit item from the registered SneakyItems def (config look).
     */
    fun createKit(player: Player? = null): ItemStack {
        val plugin = SneakyPrototypeKit.getInstance()
        val fromMagic = if (player != null) {
            SneakyItemsAPI.getByInternalName(PrototypeMagicItems.KIT)?.createFor(player, 1)
        } else {
            SneakyItemsAPI.getItemStack(PrototypeMagicItems.KIT)?.clone()
        }
        val item = fromMagic ?: fallbackKit(plugin)
        item.editMeta { meta ->
            meta.persistentDataContainer.set(plugin.PROTOTYPE_KIT_KEY, PersistentDataType.BYTE, 1)
            meta.setMaxStackSize(1)
        }
        return item
    }

    private fun fallbackKit(plugin: SneakyPrototypeKit): ItemStack {
        val config = plugin.config.getConfigurationSection("prototype-kit")
        return ItemStack(
            Material.matchMaterial(config?.getString("material", "NETHER_STAR") ?: "NETHER_STAR")
                ?: Material.NETHER_STAR,
        ).apply {
            itemMeta = itemMeta?.also { meta ->
                meta.displayName(TextUtility.convertToComponent(config?.getString("name", "&ePrototype Kit")!!))
                config?.getInt("model-data", -1)?.takeIf { it > 0 }?.let { meta.setCustomModelData(it) }
                val lore = config?.getStringList("lore")
                    ?.filterNot { it.contains("Shift + Right Click") }
                    ?.map { TextUtility.convertToComponent(it) }
                    .orEmpty()
                meta.lore(lore)
                meta.persistentDataContainer.set(plugin.PROTOTYPE_KIT_KEY, PersistentDataType.BYTE, 1)
                meta.setMaxStackSize(1)
            }
        }
    }

    /**
     * Creates an item from PDC data with optional additional lore line.
     * Used by both preview and final item creation.
     *
     * @param meta The meta containing PDC data to create the item from
     * @param additionalLoreLine Optional line to add at the end of the lore
     * @param creator Optional name of the player creating the item
     * @param stamp when true, stamp as a SneakyItems created-item def (finalise only; previews stay unstamped)
     * @return The created item, or null if required data is missing
     */
    fun createItemFromPDC(
        meta: ItemMeta,
        additionalLoreLine: String? = null,
        creator: String? = null,
        stamp: Boolean = false,
    ): ItemStack? {
        val plugin = SneakyPrototypeKit.getInstance()

        val typeStr = meta.persistentDataContainer.get(
            plugin.ITEM_TYPE_KEY,
            PersistentDataType.STRING,
        ) ?: return null

        val type = ItemType.valueOf(typeStr)

        val item = ItemStack(Material.STONE)
        val itemMeta = item.itemMeta ?: return null
        val container = itemMeta.persistentDataContainer

        container.set(plugin.ITEM_TYPE_KEY, PersistentDataType.STRING, typeStr)

        if (creator != null) {
            container.set(plugin.CREATOR_KEY, PersistentDataType.STRING, creator)
        }

        val iconData = meta.persistentDataContainer.get(
            plugin.ICON_DATA_KEY,
            PersistentDataType.STRING,
        )
        if (iconData != null) {
            val (material, modelData) = iconData.split(",")
            item.type = Material.valueOf(material)
            itemMeta.setCustomModelData(modelData.toInt())
            container.set(plugin.ICON_DATA_KEY, PersistentDataType.STRING, iconData)
        }

        val name = meta.persistentDataContainer.get(
            plugin.NAME_KEY,
            PersistentDataType.STRING,
        )
        if (name != null) {
            itemMeta.displayName(TextUtility.convertToComponent("&c$name"))
            container.set(plugin.NAME_KEY, PersistentDataType.STRING, name)
        }

        val lore = meta.persistentDataContainer.get(
            plugin.LORE_KEY,
            PersistentDataType.STRING,
        )
        val loreList = mutableListOf<Component>()

        if (lore != null) {
            loreList.addAll(TextUtility.renderPlayerLore(lore))
            container.set(plugin.LORE_KEY, PersistentDataType.STRING, lore)
        }

        val abilityKey = when (type) {
            ItemType.ITEM -> meta.persistentDataContainer.get(
                plugin.LEFT_CLICK_ABILITY_KEY,
                PersistentDataType.STRING,
            )
            else -> meta.persistentDataContainer.get(
                plugin.CONSUME_ABILITY_KEY,
                PersistentDataType.STRING,
            )
        }

        if (abilityKey != null) {
            val abilityConfig = plugin.config.getConfigurationSection("abilities.$abilityKey")
            if (abilityConfig != null) {
                val charges = abilityConfig.getInt("charges", 1)
                val stackSize = abilityConfig.getInt("stack-size", 1).coerceAtMost(99)

                abilityConfig.getString("description")?.let { desc ->
                    val prefix = when (type) {
                        ItemType.ITEM -> plugin.config.getString("prefix-left-click", "&e[Left Click] &7")
                        else -> plugin.config.getString("prefix-right-click", "&e[Right Click] &7")
                    }
                    loreList.addAll(TextUtility.wrapLore("$prefix$desc"))
                }

                itemMeta.setMaxStackSize(99)

                when (type) {
                    ItemType.ITEM -> {
                        loreList.add(TextUtility.convertToComponent("&eCharges: &f$charges"))
                        item.amount = stackSize
                        container.set(plugin.LEFT_CLICK_CHARGES_KEY, PersistentDataType.INTEGER, charges)
                        container.set(plugin.LEFT_CLICK_ABILITY_KEY, PersistentDataType.STRING, abilityKey)
                    }
                    ItemType.FOOD, ItemType.DRINK -> {
                        val totalAmount = (stackSize * charges).coerceAtMost(99)
                        item.amount = totalAmount
                        container.set(plugin.CONSUME_ABILITY_KEY, PersistentDataType.STRING, abilityKey)
                    }
                }
            }
        }

        if (additionalLoreLine != null) {
            if (loreList.isNotEmpty()) {
                loreList.add(TextUtility.convertToComponent(""))
            }
            loreList.add(TextUtility.convertToComponent(additionalLoreLine))
        }

        itemMeta.lore(loreList)
        item.itemMeta = itemMeta
        if (stamp) {
            PrototypeMagicItems.stampCreated(item)
        }
        return item
    }

    /** True when the stack is a prototype kit (stamped magic item or legacy own key). */
    fun isPrototypeKit(item: ItemStack): Boolean {
        if (PrototypeMagicItems.isKit(item)) return true
        val meta = item.itemMeta ?: return false
        return meta.persistentDataContainer.has(
            SneakyPrototypeKit.getInstance().PROTOTYPE_KIT_KEY,
            PersistentDataType.BYTE,
        )
    }

    /**
     * Creates a final item from a prototype kit.
     *
     * @param item The prototype kit item
     * @param creator The name of the player creating the item
     * @return The created item, or null if required data is missing
     */
    fun finaliseKit(item: ItemStack, creator: String? = null): ItemStack? {
        if (!isPrototypeKit(item)) return null
        val meta = item.itemMeta ?: return null
        return createItemFromPDC(meta, creator = creator, stamp = true)
    }
}
