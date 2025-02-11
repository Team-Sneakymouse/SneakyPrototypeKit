package net.sneakyprototypekit.creation.ui

import net.sneakyprototypekit.SneakyPrototypeKit
import net.sneakyprototypekit.creation.ItemType
import net.sneakyprototypekit.util.TextUtility
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack

/**
 * UI for selecting the type of item to create (Item or Food).
 */
class TypeSelectionUI(val onTypeSelected: (ItemType) -> Unit) : InventoryHolder {
    private val inventory: Inventory = Bukkit.createInventory(this, 9, TextUtility.convertToComponent("&6Select Type"))

    init {
        setupInventory()
    }

    private fun setupInventory() {
        // Item button
        val itemButton = ItemStack(Material.DIAMOND_SWORD).apply {
            itemMeta = itemMeta?.also { meta ->
                meta.displayName(TextUtility.convertToComponent("&eItem"))
                meta.lore(listOf(
                    TextUtility.convertToComponent("&7Create a new item"),
                    TextUtility.convertToComponent("&7with left-click ability")
                ))
            }
        }
        inventory.setItem(2, itemButton)

        // Food button
        val foodButton = ItemStack(Material.GOLDEN_APPLE).apply {
            itemMeta = itemMeta?.also { meta ->
                meta.displayName(TextUtility.convertToComponent("&eFood"))
                meta.lore(listOf(
                    TextUtility.convertToComponent("&7Create a new food item"),
                    TextUtility.convertToComponent("&7with consumption ability")
                ))
            }
        }
        inventory.setItem(4, foodButton)

        // Drink button
        val drinkButton = ItemStack(Material.POTION).apply {
            itemMeta = itemMeta?.also { meta ->
                meta.displayName(TextUtility.convertToComponent("&eDrink"))
                meta.lore(listOf(
                    TextUtility.convertToComponent("&7Create a new drink item"),
                    TextUtility.convertToComponent("&7with consumption ability")
                ))
            }
        }
        inventory.setItem(6, drinkButton)
    }

    override fun getInventory(): Inventory = inventory

    companion object {
        fun open(player: Player, callback: (ItemType) -> Unit) {
            val ui = TypeSelectionUI(callback)
            player.openInventory(ui.inventory)
        }
    }
}

/**
 * Listener for the type selection UI interactions.
 */
class TypeSelectionListener : Listener {

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val holder = event.inventory.holder as? TypeSelectionUI ?: return
        event.isCancelled = true

        val player = event.whoClicked as? Player ?: return
        val clickedItem = event.currentItem ?: return

        val type = when (event.slot) {
            2 -> ItemType.ITEM
            4 -> ItemType.FOOD
            6 -> ItemType.DRINK
            else -> return
        }

        // Mark as pending UI switch BEFORE closing inventory
        CreationSessionListener.addPendingUiSwitch(player.uniqueId)

        // Schedule ability selector to open after 1 tick
        Bukkit.getScheduler().runTaskLater(SneakyPrototypeKit.getInstance(), Runnable {
            holder.onTypeSelected.invoke(type)
        }, 1L)
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        if (event.inventory.holder !is TypeSelectionUI) return
        // Additional cleanup if needed
    }
} 