package net.sneakyprototypekit.creation.ui

import net.sneakyprototypekit.SneakyPrototypeKit
import net.sneakyprototypekit.creation.ItemType
import net.sneakyprototypekit.util.ItemModels
import net.sneakyprototypekit.util.TextUtility
import net.sneakyprototypekit.util.MenuTiming
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import com.sneakyutils.util.DisplayOnlyInventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

/**
 * UI for selecting an icon for the item being created.
 * Supports pagination for browsing through available icons.
 */
class IconSelectionUI(
    val itemType: ItemType,
    val page: Int = 0,
    val callback: ((ItemModels.Icon) -> Unit),
    val prototypeKit: ItemStack
) : DisplayOnlyInventoryHolder {
    private val inventory: Inventory = Bukkit.createInventory(this, 54, TextUtility.convertToComponent("&6Select Icon"))
    private val icons = mutableListOf<ItemModels.Icon>()

    init {
        loadIcons()
        updateInventory()
    }

    override fun getInventory(): Inventory = inventory

    /**
     * Loads available icons from the plugin configuration: item-model ids per carrier material
     * (old integer entries and ranges still resolve through the legacy table).
     */
    private fun loadIcons() {
        val config = SneakyPrototypeKit.getInstance().config
        val iconsSection = config.getConfigurationSection(
            when (itemType) {
                ItemType.FOOD -> "food-icons"
                ItemType.DRINK -> "drink-icons"
                else -> "item-icons"
            }
        )
        icons += ItemModels.icons(iconsSection)
    }

    /**
     * Updates the inventory with icons and navigation buttons.
     */
    private fun updateInventory() {
        inventory.clear()

        // Add icons
        val startIndex = page * 45
        icons.drop(startIndex).take(45).forEachIndexed { index, icon ->
            inventory.setItem(index + 9, createIconButton(icon))
        }

        // Add navigation buttons
        val hasNextPage = icons.size > (page + 1) * 45
        val hasPrevPage = page > 0

        // Previous page button
        if (hasPrevPage) {
            inventory.setItem(1, createNavigationButton("prev_page", "&ePrevious Page"))
        }

        // Next page button
        if (hasNextPage) {
            inventory.setItem(7, createNavigationButton("next_page", "&eNext Page"))
        }

        // GUI elements
        inventory.setItem(0, ItemModels.guiItem(ItemModels.GUI_ICON))
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

    private fun createIconButton(icon: ItemModels.Icon): ItemStack {
        return ItemStack(icon.material).apply {
            itemMeta = itemMeta?.also { meta ->
                ItemModels.setModel(meta, icon.model)
                meta.persistentDataContainer.set(
                    SneakyPrototypeKit.getInstance().ICON_DATA_KEY,
                    PersistentDataType.STRING,
                    ItemModels.encode(icon)
                )
                meta.isHideTooltip = true
            }
        }
    }

    companion object {
        fun open(
            player: Player,
            itemType: ItemType,
            page: Int = 0,
            prototypeKit: ItemStack,
            callback: (ItemModels.Icon) -> Unit
        ): Unit = MenuTiming.timed("menu.icon-selection.open") {
            val ui = MenuTiming.timed("menu.icon-selection.build") { IconSelectionUI(itemType, page, callback, prototypeKit) }
            MenuTiming.timed("menu.icon-selection.open-inventory") { player.openInventory(ui.inventory) }
        }
    }
}
