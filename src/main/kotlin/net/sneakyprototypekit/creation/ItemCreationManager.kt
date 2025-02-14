package net.sneakyprototypekit.creation

import net.sneakyprototypekit.SneakyPrototypeKit
import net.sneakyprototypekit.creation.ui.TypeSelectionUI
import net.sneakyprototypekit.creation.ui.AbilitySelectionUI
import net.sneakyprototypekit.creation.ui.IconSelectionUI
import net.sneakyprototypekit.creation.ui.ChatInputListener
import net.sneakyprototypekit.creation.ui.NameInputListener
import net.sneakyprototypekit.creation.ui.LoreInputListener
import net.sneakyprototypekit.creation.ui.CreationSessionListener
import net.sneakyprototypekit.util.TextUtility
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import java.util.*
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.TextComponent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.HandlerList
import net.kyori.adventure.text.Component

/**
 * Manages the item creation process.
 * Handles the step-by-step creation of custom items and food.
 */
object ItemCreationManager {
    private val creationSessions = mutableMapOf<UUID, CreationSession>()
    private val activeChatListeners = mutableMapOf<UUID, Listener>()

    /**
     * Gets the current creation session for a player.
     */
    fun getSession(player: Player): CreationSession? = creationSessions[player.uniqueId]

    /**
     * Starts a new item creation session for a player.
     */
    fun startCreation(player: Player) {
        creationSessions[player.uniqueId] = CreationSession()
        showTypeSelection(player)
    }

    /**
     * Shows the type selection interface to the player.
     */
    private fun showTypeSelection(player: Player) {
        val session = creationSessions[player.uniqueId] ?: return
        session.state = CreationState.TYPE_SELECTION
        
        TypeSelectionUI.open(player) { type ->
            session.type = type
            showAbilitySelection(player)
        }
    }

    /**
     * Shows the ability selection interface to the player.
     */
    private fun showAbilitySelection(player: Player) {
        val session = creationSessions[player.uniqueId] ?: return
        session.state = CreationState.ABILITY_SELECTION
        
        AbilitySelectionUI.open(player, session.type!!, 0) { ability ->
            session.ability = ability
            showIconSelection(player)
        }
    }

    /**
     * Shows the icon selection interface to the player.
     */
    private fun showIconSelection(player: Player) {
        val session = creationSessions[player.uniqueId] ?: return
        session.state = CreationState.ICON_SELECTION
        
        // Clear any existing chat listeners before showing icon selection
        activeChatListeners[player.uniqueId]?.let {
            HandlerList.unregisterAll(it)
            activeChatListeners.remove(player.uniqueId)
        }
        
        CreationSessionListener.addPendingUiSwitch(player.uniqueId)
        Bukkit.getScheduler().runTaskLater(SneakyPrototypeKit.getInstance(), Runnable {
            IconSelectionUI.open(player, session.type!!, 0) { material, modelData ->
                session.material = material
                session.modelData = modelData
                startNameAndLoreInput(player)
            }
        }, 1L)
    }

    /**
     * Starts the name and lore input process.
     */
    fun startNameAndLoreInput(player: Player) {
        val session = creationSessions[player.uniqueId] ?: return
        session.state = CreationState.NAME_INPUT

        // Start with name input
        val nameListener = NameInputListener(player) { name ->
            session.name = name
            session.state = CreationState.LORE_INPUT
            
            // After name is entered, start lore input
            val loreListener = LoreInputListener(player) { lore ->
                if (lore.equals("done", ignoreCase = true)) {
                    completeItemCreation(player)
                } else {
                    session.lore = session.lore + listOf(lore)
                    player.sendMessage(TextUtility.convertToComponent("&aLore line added! Enter another line or type 'done' to finish."))
                }
            }
            
            ChatInputListener.register(player, loreListener)
            player.sendMessage(TextUtility.convertToComponent("&eEnter lore for your item (or type 'done' to finish):"))
        }

        ChatInputListener.register(player, nameListener)
        player.sendMessage(TextUtility.convertToComponent("&eEnter a name for your item:"))
    }

    /**
     * Completes the item creation process and gives the item to the player.
     */
    private fun completeItemCreation(player: Player) {
        val session = creationSessions[player.uniqueId] ?: return
        session.state = CreationState.COMPLETE
        
        val item = createItem(session)
        player.inventory.addItem(item)
        player.sendMessage(TextUtility.convertToComponent("&aYour item has been created!"))
        
        // Clear the session
        creationSessions.remove(player.uniqueId)
    }

    /**
     * Restores a player's creation session after login or inventory close.
     */
    fun restoreSession(player: Player, delay: Long = 1L) {
        val session = creationSessions[player.uniqueId] ?: return
        
        // Clean up any existing chat listeners before restoring
        ChatInputListener.unregister(player)
        
        Bukkit.getScheduler().runTaskLater(SneakyPrototypeKit.getInstance(), Runnable {
            when (session.state) {
                CreationState.TYPE_SELECTION -> showTypeSelection(player)
                CreationState.ABILITY_SELECTION -> showAbilitySelection(player)
                CreationState.ICON_SELECTION -> showIconSelection(player)
                CreationState.NAME_INPUT -> startNameAndLoreInput(player)
                CreationState.LORE_INPUT -> {
                    // Resume lore input with a new listener
                    val loreListener = LoreInputListener(player) { lore ->
                        if (lore.equals("done", ignoreCase = true)) {
                            completeItemCreation(player)
                        } else {
                            session.lore = session.lore + listOf(lore)
                            player.sendMessage(TextUtility.convertToComponent("&aLore line added! Enter another line or type 'done' to finish."))
                        }
                    }
                    ChatInputListener.register(player, loreListener)
                    player.sendMessage(TextUtility.convertToComponent("&eEnter lore for your item (or type 'done' to finish):"))
                }
                CreationState.COMPLETE -> {} // Should not happen, but handle gracefully
            }
        }, delay)
    }

    /**
     * Represents a player's item creation session.
     */
    data class CreationSession(
        var state: CreationState = CreationState.TYPE_SELECTION,
        var type: ItemType? = null,
        var ability: String? = null,
        var material: Material? = null,
        var modelData: Int? = null,
        var name: String? = null,
        var lore: List<String> = listOf()
    )

    /**
     * Represents the current state of item creation.
     */
    enum class CreationState {
        TYPE_SELECTION,
        ABILITY_SELECTION,
        ICON_SELECTION,
        NAME_INPUT,
        LORE_INPUT,
        COMPLETE
    }

    /**
     * Creates the final item based on the session data.
     */
    private fun createItem(session: CreationSession): ItemStack {
        val item = ItemStack(session.material ?: Material.STONE)
        val meta = item.itemMeta
        val plugin = SneakyPrototypeKit.getInstance()
        val container = meta.persistentDataContainer

        // Set custom model data
        session.modelData?.let { meta.setCustomModelData(it) }

        // Set name with color codes
        meta.displayName(TextUtility.convertToComponent("&c${session.name ?: "Unnamed Item"}"))
        
        // Process and wrap lore
        val loreList = mutableListOf<Component>()
        
        // Add custom lore lines
        session.lore.forEach { loreLine ->
            val coloredLine = if (!loreLine.startsWith("&")) "&7$loreLine" else loreLine
            loreList.addAll(TextUtility.wrapLore(coloredLine))
        }

        // Get ability configuration
        val abilityConfig = plugin.config.getConfigurationSection("abilities.${session.ability}") ?: return item
        val charges = abilityConfig.getInt("charges", 1)
        val stackSize = abilityConfig.getInt("stack-size", 1).coerceAtMost(99)
        
        // Add ability description
        abilityConfig.getString("description")?.let { desc ->
            if (loreList.isNotEmpty()) {
                loreList.add(TextUtility.convertToComponent(""))
            }
            
            // Get the appropriate prefix based on item type
            val prefix = when (session.type) {
                ItemType.ITEM -> plugin.config.getString("prefix-left-click", "&e[Left Click] &7")
                ItemType.FOOD, ItemType.DRINK -> plugin.config.getString("prefix-right-click", "&e[Right Click] &7")
                else -> "&7"
            }
            
            // Add description with prefix
            loreList.addAll(TextUtility.wrapLore("$prefix$desc"))
        }
        
        // Add charges line and handle stack sizes based on type
        when (session.type) {
            ItemType.ITEM -> {
                if (loreList.isNotEmpty()) {
                    loreList.add(TextUtility.convertToComponent(""))
                }
                loreList.add(TextUtility.convertToComponent("&eCharges: &f$charges"))
                
                // Set stack size and store charges
                meta.setMaxStackSize(stackSize)
                item.amount = stackSize
                container.set(plugin.CHARGES_KEY, PersistentDataType.INTEGER, charges)
            }
            ItemType.FOOD, ItemType.DRINK -> {
                // For consumables, multiply stack size by charges
                val totalAmount = stackSize * charges
                meta.setMaxStackSize(totalAmount)
                item.amount = totalAmount
            }
            else -> item.amount = 1
        }
        
        meta.lore(loreList)

        // Store data
        container.set(plugin.ITEM_TYPE_KEY, PersistentDataType.STRING, session.type?.name ?: "ITEM")
        session.ability?.let { container.set(plugin.LEFT_CLICK_ABILITY_KEY, PersistentDataType.STRING, it) }

        item.itemMeta = meta
        return item
    }
} 