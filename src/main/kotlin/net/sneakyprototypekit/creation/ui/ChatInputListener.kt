package net.sneakyprototypekit.creation.ui

import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.TextComponent
import net.sneakyprototypekit.SneakyPrototypeKit
import net.sneakyprototypekit.util.TextUtility
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent

/**
 * Base class for chat input listeners.
 * Handles common functionality like unregistering and format code checking.
 * Provides a framework for handling chat-based input during item creation.
 */
abstract class ChatInputListener(protected val player: Player) : Listener {
    
    /**
     * Handles player quit events to clean up listeners.
     * Automatically unregisters the listener when the player leaves.
     */
    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        if (event.player == player) {
            unregister()
        }
    }

    /**
     * Unregisters this listener from all events.
     */
    protected fun unregister() {
        HandlerList.unregisterAll(this)
    }

    companion object {
        /** Map of active chat input listeners per player */
        private val activeListeners = mutableMapOf<Player, Listener>()

        /**
         * Registers a new chat input listener for a player.
         * Automatically unregisters any existing listener for the player.
         * 
         * @param player The player to register the listener for
         * @param listener The listener to register
         */
        fun register(player: Player, listener: Listener) {
            // Unregister any existing listener for this player
            unregister(player)
            
            // Register the new listener
            activeListeners[player] = listener
            Bukkit.getPluginManager().registerEvents(listener, SneakyPrototypeKit.getInstance())
        }

        /**
         * Unregisters any active chat input listener for a player.
         * 
         * @param player The player to unregister listeners for
         */
        private fun unregister(player: Player) {
            activeListeners[player]?.let {
                HandlerList.unregisterAll(it)
                activeListeners.remove(player)
            }
        }
    }
}

/**
 * Listener for item name input.
 * Handles validation and processing of item names entered in chat.
 * 
 * @property player The player entering the name
 * @property onNameEntered Callback function called when a valid name is entered
 */
class NameInputListener(
    player: Player,
    private val onNameEntered: (String) -> Unit
) : ChatInputListener(player) {

    /**
     * Handles chat messages for name input.
     * Validates the name length and format codes before accepting.
     */
    @EventHandler
    fun onPlayerChat(event: AsyncChatEvent) {
        if (event.player != player) return
        event.isCancelled = true

        val name = (event.message() as TextComponent).content()
        
        // Check for format codes
        val (containsCodes, errorMessage) = TextUtility.containsFormatCodes(name, player)
        if (containsCodes) {
            player.sendMessage(TextUtility.convertToComponent(errorMessage!!))
            return
        }

        // Check length limit
        if (name.length > 30) {
            player.sendMessage(TextUtility.convertToComponent("&cName cannot be longer than 30 characters! Please try again."))
            return
        }

        onNameEntered(name)
        unregister()
    }
}

/**
 * Listener for item lore input.
 * Handles validation and processing of item lore entered in chat.
 * 
 * @property player The player entering the lore
 * @property onLoreEntered Callback function called when valid lore is entered
 */
class LoreInputListener(
    player: Player,
    private val onLoreEntered: (String) -> Unit
) : ChatInputListener(player) {

    /**
     * Handles chat messages for lore input.
     * Validates the lore length and format codes before accepting.
     */
    @EventHandler
    fun onPlayerChat(event: AsyncChatEvent) {
        if (event.player != player) return
        event.isCancelled = true

        val lore = (event.message() as TextComponent).content()
        
        // Check for format codes
        val (containsCodes, errorMessage) = TextUtility.containsFormatCodes(lore, player)
        if (containsCodes) {
            player.sendMessage(TextUtility.convertToComponent(errorMessage!!))
            return
        }

        // Check length limit
        if (lore.length > 100) {
            player.sendMessage(TextUtility.convertToComponent("&cLore cannot be longer than 100 characters! Please try again."))
            return
        }

        onLoreEntered(lore)
        unregister()
    }
} 