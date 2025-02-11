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
 */
object AbilityManager {
    private val cooldowns = mutableMapOf<String, Long>() // Player UUID -> Last use time
    private const val COOLDOWN_MS = 500L // 500ms cooldown
    
    /**
     * Executes an ability from an item.
     * @return true if the ability was executed successfully
     */
    fun executeAbility(item: ItemStack, player: Player): Boolean {
        // Check cooldown for items
        val lastUse = cooldowns[player.uniqueId.toString()]
        if (lastUse != null && System.currentTimeMillis() - lastUse < COOLDOWN_MS) {
            return false // Still on cooldown
        }

        val meta = item.itemMeta ?: return false
        val container = meta.persistentDataContainer
        val plugin = SneakyPrototypeKit.getInstance()

        // Get ability and type
        val ability = container.get(plugin.LEFT_CLICK_ABILITY_KEY, PersistentDataType.STRING) ?: return false
        val type = container.get(plugin.ITEM_TYPE_KEY, PersistentDataType.STRING) ?: return false

        // Get ability configuration
        val abilityConfig = plugin.config.getConfigurationSection("abilities.$ability") ?: return false
        val command = abilityConfig.getString("command-console") ?: return false

        // For items, check stacking and charges
        if (type == "ITEM") {
            // Check if item is stacked
            if (item.amount > 1) {
                player.sendMessage(TextUtility.convertToComponent("&cYou must unstack this item before using it!"))
                return false
            }

            val charges = container.get(plugin.CHARGES_KEY, PersistentDataType.INTEGER) ?: return false
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
                container.set(plugin.CHARGES_KEY, PersistentDataType.INTEGER, remainingCharges)
                
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

            // Set cooldown
            cooldowns[player.uniqueId.toString()] = System.currentTimeMillis()
        }

        // Execute the command
        val finalCommand = command.replace("[playerName]", player.name)
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommand)
        return true
    }
} 