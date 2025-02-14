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
 */
abstract class ChatInputListener(protected val player: Player) : Listener {
    
    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        if (event.player == player) {
            unregister()
        }
    }

    protected fun unregister() {
        HandlerList.unregisterAll(this)
    }

    companion object {
        private val activeListeners = mutableMapOf<Player, Listener>()

        fun register(player: Player, listener: Listener) {
            // Unregister any existing listener for this player
            unregister(player)
            
            // Register the new listener
            activeListeners[player] = listener
            Bukkit.getPluginManager().registerEvents(listener, SneakyPrototypeKit.getInstance())
        }

        fun unregister(player: Player) {
            activeListeners[player]?.let {
                HandlerList.unregisterAll(it)
                activeListeners.remove(player)
            }
        }
    }
}

/**
 * Listener for item name input.
 */
class NameInputListener(
    player: Player,
    private val onNameEntered: (String) -> Unit
) : ChatInputListener(player) {

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
 */
class LoreInputListener(
    player: Player,
    private val onLoreEntered: (String) -> Unit
) : ChatInputListener(player) {

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