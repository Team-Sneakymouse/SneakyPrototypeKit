package net.sneakyprototypekit.creation.ui

import net.sneakyprototypekit.SneakyPrototypeKit
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.persistence.PersistentDataType

/**
 * Listener for the ability selection UI interactions.
 */
class AbilitySelectionListener : Listener {

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val holder = event.inventory.holder as? AbilitySelectionUI ?: return
        event.isCancelled = true

        val clickedItem = event.currentItem ?: return
        val player = event.whoClicked as? Player ?: return
        val plugin = SneakyPrototypeKit.getInstance()

        // Check for navigation buttons
        clickedItem.itemMeta?.persistentDataContainer?.get(
            plugin.NAVIGATION_KEY,
            PersistentDataType.STRING
        )?.let { action ->
            when (action) {
                "prev_page" -> {
                    Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                        AbilitySelectionUI.open(player, holder.itemType, holder.page - 1, holder.prototypeKit, holder.callback)
                    }, 1L)
                }
                "next_page" -> {
                    Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                        AbilitySelectionUI.open(player, holder.itemType, holder.page + 1, holder.prototypeKit, holder.callback)
                    }, 1L)
                }
            }
            return
        }

        // Handle ability selection
        clickedItem.itemMeta?.persistentDataContainer?.get(
            plugin.LEFT_CLICK_ABILITY_KEY,
            PersistentDataType.STRING
        )?.let { abilityKey ->
            // First invoke the callback to store the ability
            holder.callback.invoke(abilityKey)
            
            // Return to main menu
            Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                MainCreationUI.open(player, holder.prototypeKit)
            }, 1L)
        }
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        if (event.inventory.holder !is AbilitySelectionUI) return
        // Additional cleanup if needed
    }
} 