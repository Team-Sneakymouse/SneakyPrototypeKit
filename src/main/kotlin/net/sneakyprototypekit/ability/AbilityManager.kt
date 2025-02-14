package net.sneakyprototypekit.ability

import net.sneakyprototypekit.SneakyPrototypeKit
import net.sneakyprototypekit.util.TextUtility
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

/**
 * Manages the execution of abilities and handles charge management.
 * Provides functionality for cooldowns, charge tracking, and ability execution.
 */
object AbilityManager {
    /** Maps player UUIDs to their ability cooldowns (ability name -> last use time) */
    private val cooldowns = mutableMapOf<String, MutableMap<String, Long>>()
    
    /** Default cooldown between ability uses in milliseconds */
    const val DEFAULT_COOLDOWN_MS = 500L
    
    /**
     * Gets the cooldown map for a player.
     * Creates a new map if one doesn't exist.
     * 
     * @param player The player to get cooldowns for
     * @return The player's cooldown map
     */
    fun getCooldowns(player: Player): MutableMap<String, Long> {
        return cooldowns.getOrPut(player.uniqueId.toString()) { mutableMapOf() }
    }

    /**
     * Checks if an ability is on cooldown for a player.
     * 
     * @param player The player to check
     * @param ability The ability name to check
     * @param cooldownMs The cooldown duration in milliseconds
     * @return true if the ability is on cooldown
     */
    fun isOnCooldown(player: Player, ability: String, cooldownMs: Long): Boolean {
        val lastUse = getCooldowns(player)[ability] ?: return false
        return System.currentTimeMillis() - lastUse < cooldownMs
    }

    /**
     * Executes an ability from an item.
     * Handles charge consumption and cooldown management.
     * 
     * @param item The item containing the ability
     * @param player The player using the ability
     * @return true if the ability was executed successfully
     */
    fun executeAbility(item: ItemStack, player: Player): Boolean {
        val meta = item.itemMeta ?: return false
        val container = meta.persistentDataContainer
        val plugin = SneakyPrototypeKit.getInstance()

        // Get ability and type
        val type = container.get(plugin.ITEM_TYPE_KEY, PersistentDataType.STRING) ?: return false
        val ability = when (type) {
            "ITEM" -> container.get(plugin.LEFT_CLICK_ABILITY_KEY, PersistentDataType.STRING)
            "FOOD", "DRINK" -> container.get(plugin.CONSUME_ABILITY_KEY, PersistentDataType.STRING)
            else -> container.get(plugin.LEFT_CLICK_ABILITY_KEY, PersistentDataType.STRING)
        } ?: return false

        // Get ability configuration
        val abilityConfig = plugin.config.getConfigurationSection("abilities.$ability") ?: return false
        val command = abilityConfig.getString("command-console") ?: return false

        // Check cooldown for items
        val playerCooldowns = getCooldowns(player)
        val lastUse = playerCooldowns[ability]
        val cooldownMs = abilityConfig.getLong("cooldown", DEFAULT_COOLDOWN_MS)
        
        if (lastUse != null && System.currentTimeMillis() - lastUse < cooldownMs) {
            return false // Still on cooldown
        }

        // For items, check stacking and charges
        if (type == "ITEM") {
            // Check if item is stacked
            if (item.amount > 1) {
                player.sendMessage(TextUtility.convertToComponent("&cYou must unstack this item before using it!"))
                return false
            }

            val charges = container.get(plugin.LEFT_CLICK_CHARGES_KEY, PersistentDataType.INTEGER) ?: return false
            if (charges <= 0) {
                player.sendMessage(TextUtility.convertToComponent("&4This item has no charges remaining!"))
                return false
            }

            // Update charges
            val remainingCharges = charges - 1
            
            if (remainingCharges <= 0) {
                // Remove the item if this was the last charge
                item.amount = 0
            } else {
                // Update charges in PDC and lore
                container.set(plugin.LEFT_CLICK_CHARGES_KEY, PersistentDataType.INTEGER, remainingCharges)
                
                // Update lore
                val lore = meta.lore() ?: mutableListOf()
                if (lore.isNotEmpty()) {
                    // Find and update the charges line
                    for (i in lore.indices) {
                        val line = lore[i].toString()
                        if (line.contains("Charges:")) {
                            lore[i] = TextUtility.convertToComponent("&eCharges: &f$remainingCharges")
                            break
                        }
                    }
                }
                meta.lore(lore)
                item.itemMeta = meta
            }
        }

        // Set cooldown for this ability
        playerCooldowns[ability] = System.currentTimeMillis()

        // Execute the command
        val finalCommand = command.replace("[playerName]", player.name)
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommand)
        return true
    }
} 