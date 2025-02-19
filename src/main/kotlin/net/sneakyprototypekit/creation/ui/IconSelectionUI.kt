package net.sneakyprototypekit.creation.ui

import net.sneakyprototypekit.SneakyPrototypeKit
import net.sneakyprototypekit.creation.ItemType
import net.sneakyprototypekit.util.TextUtility
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

/**
 * UI for selecting an icon for the item being created.
 * Supports pagination for browsing through available icons.
 */
class IconSelectionUI(
    val itemType: ItemType,
    val page: Int = 0,
    val callback: ((Material, Int) -> Unit),
    val prototypeKit: ItemStack
) : InventoryHolder {
    private val inventory: Inventory = Bukkit.createInventory(this, 54, TextUtility.convertToComponent("&6Select Icon"))
    private val icons = mutableListOf<IconData>()

    init {
        loadIcons()
        updateInventory()
    }

    override fun getInventory(): Inventory = inventory

    /**
     * Loads available icons from the plugin configuration.
     * Supports both individual model data values and ranges.
     */
    private fun loadIcons() {
        val config = SneakyPrototypeKit.getInstance().config
        val iconsSection = config.getConfigurationSection(
            when (itemType) {
                ItemType.FOOD -> "food-icons"
                ItemType.DRINK -> "drink-icons"
                else -> "item-icons"
            }
        ) ?: return

        for (materialKey in iconsSection.getKeys(false)) {
            val material = Material.matchMaterial(materialKey) ?: continue
            val modelDataList = iconsSection.getStringList(materialKey)

            for (modelDataEntry in modelDataList) {
                if (modelDataEntry.contains("-")) {
                    // Handle range
                    val parts = modelDataEntry.split("-")
                    val start = parts[0].toIntOrNull() ?: continue
                    val end = parts[1].toIntOrNull() ?: continue
                    for (modelData in start..end) {
                        icons.add(IconData(material, modelData))
                    }
                } else {
                    // Handle single value
                    val modelData = modelDataEntry.toIntOrNull() ?: continue
                    icons.add(IconData(material, modelData))
                }
            }
        }
    }

    /**
     * Updates the inventory with icons and navigation buttons.
     */
    private fun updateInventory() {
        inventory.clear()

        // Add icons
        val startIndex = page * 45 // Leave bottom row for navigation
        icons.drop(startIndex).take(45).forEachIndexed { index, icon ->
            inventory.setItem(index, createIconButton(icon))
        }

        // Add navigation buttons
        val hasNextPage = icons.size > (page + 1) * 45
        val hasPrevPage = page > 0

        // Previous page button
        if (hasPrevPage) {
            inventory.setItem(45, createNavigationButton("prev_page", "&ePrevious Page"))
        }

        // Next page button
        if (hasNextPage) {
            inventory.setItem(53, createNavigationButton("next_page", "&eNext Page"))
        }
    }

    private fun createNavigationButton(id: String, name: String): ItemStack {
        return ItemStack(Material.ARROW).apply {
            itemMeta = itemMeta?.also { meta ->
                meta.displayName(TextUtility.convertToComponent(name))
                meta.persistentDataContainer.set(
                    SneakyPrototypeKit.getInstance().NAVIGATION_KEY,
                    PersistentDataType.STRING,
                    id
                )
            }
        }
    }

    private fun createIconButton(icon: IconData): ItemStack {
        return ItemStack(icon.material).apply {
            itemMeta = itemMeta?.also { meta ->
                meta.setCustomModelData(icon.modelData)
                meta.persistentDataContainer.set(
                    SneakyPrototypeKit.getInstance().ICON_DATA_KEY,
                    PersistentDataType.STRING,
                    "${icon.material.name},${icon.modelData}"
                )
                meta.setHideTooltip(true)
            }
        }
    }

    companion object {
        fun open(
            player: Player,
            itemType: ItemType,
            page: Int = 0,
            prototypeKit: ItemStack,
            callback: (Material, Int) -> Unit
        ) {
            val ui = IconSelectionUI(itemType, page, callback, prototypeKit)
            player.openInventory(ui.inventory)
        }
    }

    data class IconData(val material: Material, val modelData: Int)
} 