package net.sneakyprototypekit.creation.ui

import net.sneakyprototypekit.SneakyPrototypeKit
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.persistence.PersistentDataType

/**
 * Listener for the icon selection UI interactions.
 */
class IconSelectionListener : Listener {

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val holder = event.inventory.holder as? IconSelectionUI ?: return
        
        // Only handle clicks in our UI
        if (event.clickedInventory != event.view.topInventory) {
            event.isCancelled = true
            return
        }
        
        event.isCancelled = true

        val clickedItem = event.currentItem ?: return
        val meta = clickedItem.itemMeta ?: return
        val player = event.whoClicked as? Player ?: return
        val plugin = SneakyPrototypeKit.getInstance()
        
        // Ignore clicks on the GUI icon (jigsaw block)
        if (clickedItem.type == Material.JIGSAW && meta.hasCustomModelData() && meta.customModelData == 3050) {
            return
        }
        
        // Only handle clicks on our navigation buttons or icon buttons
        if (!meta.persistentDataContainer.has(plugin.NAVIGATION_KEY, PersistentDataType.STRING) &&
            !meta.persistentDataContainer.has(plugin.ICON_DATA_KEY, PersistentDataType.STRING)) {
            return
        }

        // Check for navigation buttons
        meta.persistentDataContainer.get(
            plugin.NAVIGATION_KEY,
            PersistentDataType.STRING
        )?.let { action ->
            when (action) {
                "prev_page" -> {
                    Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                        IconSelectionUI.open(player, holder.itemType, holder.page - 1, holder.prototypeKit, holder.callback)
                    }, 1L)
                }
                "next_page" -> {
                    Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                        IconSelectionUI.open(player, holder.itemType, holder.page + 1, holder.prototypeKit, holder.callback)
                    }, 1L)
                }
            }
            return
        }

        // Handle icon selection
        meta.persistentDataContainer.get(
            plugin.ICON_DATA_KEY,
            PersistentDataType.STRING
        )?.let { data ->
            val (materialName, modelData) = data.split(",")
            val material = Material.valueOf(materialName)
            // Call the callback with the selected material and model data
            holder.callback.invoke(material, modelData.toInt())
            // Return to main menu after 1 tick
            Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                MainCreationUI.open(player, holder.prototypeKit)
            }, 1L)
        }
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        if (event.inventory.holder !is IconSelectionUI) return
        // Additional cleanup if needed
    }
} 