package net.sneakyprototypekit.creation.ui

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import net.sneakyprototypekit.SneakyPrototypeKit
import net.sneakyprototypekit.creation.ItemCreationManager
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import net.sneakyprototypekit.creation.ui.TypeSelectionUI
import net.sneakyprototypekit.creation.ui.AbilitySelectionUI
import net.sneakyprototypekit.creation.ui.IconSelectionUI
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.persistence.PersistentDataType
import java.util.UUID

/**
 * Handles session persistence and UI reopening for item creation.
 */
class CreationSessionListener : Listener {
    private val pendingUiSwitches = mutableSetOf<String>()

    init {
        CreationSessionListener.instance = this
    }

    companion object {
        private var instance: CreationSessionListener? = null
        
        fun addPendingUiSwitch(playerId: UUID) {
            instance?.pendingUiSwitches?.add(playerId.toString())
            Bukkit.getScheduler().runTaskLater(SneakyPrototypeKit.getInstance(), Runnable {
                instance?.pendingUiSwitches?.remove(playerId.toString())
            }, 5L)
        }
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        // Wait 5 seconds for resource pack to load
        ItemCreationManager.restoreSession(event.player, 100L)
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val clickedItem = event.currentItem ?: return
        val holder = event.inventory.holder
        
        // Only check navigation in our UIs
        if (holder !is TypeSelectionUI && holder !is AbilitySelectionUI && holder !is IconSelectionUI) {
            return
        }
        
        // Check if this was any navigation button click
        clickedItem.itemMeta?.persistentDataContainer?.get(
            SneakyPrototypeKit.getInstance().NAVIGATION_KEY,
            PersistentDataType.STRING
        )?.let { action ->
            if (action == "back" || action == "prev_page" || action == "next_page") {
                CreationSessionListener.addPendingUiSwitch(player.uniqueId)
            }
        }
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return
        val holder = event.inventory.holder
        
        // Only reopen if it's one of our UIs and not a navigation button press
        if (holder is TypeSelectionUI || holder is AbilitySelectionUI || holder is IconSelectionUI) {
            val session = ItemCreationManager.getSession(player) ?: return
            
            // Don't reopen if we're in name/lore input or complete state
            if (session.state == ItemCreationManager.CreationState.NAME_INPUT ||
                session.state == ItemCreationManager.CreationState.LORE_INPUT ||
                session.state == ItemCreationManager.CreationState.COMPLETE) {
                return
            }

            pendingUiSwitches.forEach {
            }

            // Check navigation flag or pending UI switch
            if (pendingUiSwitches.any { it.equals(player.uniqueId.toString()) }) {
                return
            }
            
            // Reopen after 1 tick
            Bukkit.getScheduler().runTaskLater(SneakyPrototypeKit.getInstance(), Runnable {
                ItemCreationManager.restoreSession(player)
            }, 1L)
        }
    }
} 