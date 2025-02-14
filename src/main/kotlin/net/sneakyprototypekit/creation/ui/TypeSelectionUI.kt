package net.sneakyprototypekit.creation.ui

import net.sneakyprototypekit.SneakyPrototypeKit
import net.sneakyprototypekit.creation.ItemType
import net.sneakyprototypekit.creation.PrototypeKit
import net.sneakyprototypekit.util.TextUtility
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

/**
 * UI for selecting the type of item to create.
 * Provides options for creating items, food, or drinks, each with different behaviors.
 * The UI is displayed in a 3x9 inventory with centered buttons for each type.
 */
object TypeSelectionUI {
    /** Title of the type selection inventory */
    const val TITLE = "&6Select Type"
    
    /** Size of the inventory (3 rows) */
    private const val SIZE = 27

    /**
     * Opens the type selection UI for a player.
     * Displays buttons for each available item type.
     * 
     * @param player The player to show the UI to
     * @param prototypeKit The prototype kit being configured
     */
    fun open(player: Player, prototypeKit: ItemStack) {
        val holder = CustomInventoryHolder()
        val inventory = Bukkit.createInventory(holder, SIZE, TextUtility.convertToComponent(TITLE))
        holder.setInventory(inventory)
        
        // Add type options
        inventory.setItem(11, createTypeButton(Material.DIAMOND_SWORD, "Item", ItemType.ITEM))
        inventory.setItem(13, createTypeButton(Material.COOKED_BEEF, "Food", ItemType.FOOD))
        inventory.setItem(15, createTypeButton(Material.POTION, "Drink", ItemType.DRINK))
        
        // Store prototype kit reference
        holder.setData("prototype_kit", prototypeKit)
        
        player.openInventory(inventory)
    }

    /**
     * Creates a button for selecting an item type.
     * 
     * @param material The material to use for the button
     * @param name The display name of the button
     * @param type The item type this button represents
     * @return The created button item stack
     */
    private fun createTypeButton(material: Material, name: String, type: ItemType): ItemStack {
        return ItemStack(material).apply {
            itemMeta = itemMeta?.also { meta ->
                meta.displayName(TextUtility.convertToComponent("&e$name"))
                meta.lore(listOf(
                    TextUtility.convertToComponent("&7Create a new $name"),
                    TextUtility.convertToComponent(when (type) {
                        ItemType.ITEM -> "&7with left-click ability"
                        else -> "&7with consumption ability"
                    })
                ))
                
                // Store type data
                meta.persistentDataContainer.set(
                    SneakyPrototypeKit.getInstance().ITEM_TYPE_KEY,
                    PersistentDataType.STRING,
                    type.name
                )
            }
        }
    }
} 