package net.sneakyprototypekit.ability

import net.sneakyprototypekit.SneakyPrototypeKit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerItemConsumeEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.persistence.PersistentDataType
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.EventPriority
import org.bukkit.event.entity.EntityDamageEvent

/**
 * Listens for item and food interactions to trigger abilities.
 */
class AbilityListener : Listener {
    private val dropTimeouts = mutableMapOf<String, Long>()
    private val DROP_TIMEOUT_MS = 150L // How long to disable abilities after dropping

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerDropItem(event: PlayerDropItemEvent) {
        val item = event.itemDrop.itemStack
        val meta = item.itemMeta ?: return
        val container = meta.persistentDataContainer
        val plugin = SneakyPrototypeKit.getInstance()

        // If this is an item with a left click ability, set a timeout
        if (container.has(plugin.LEFT_CLICK_ABILITY_KEY, PersistentDataType.STRING)) {
            dropTimeouts[event.player.uniqueId.toString()] = System.currentTimeMillis() + DROP_TIMEOUT_MS
        }
    }

    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        if (event.action != Action.LEFT_CLICK_AIR && event.action != Action.LEFT_CLICK_BLOCK) return
        if (!event.hasItem()) return
        
        // Check if we're in the drop timeout period
        val timeout = dropTimeouts[event.player.uniqueId.toString()]
        if (timeout != null && System.currentTimeMillis() < timeout) {
            return
        }
        
        val item = event.item ?: return
        val meta = item.itemMeta ?: return
        val container = meta.persistentDataContainer
        val plugin = SneakyPrototypeKit.getInstance()

        // Check if this is a prototype item
        val type = container.get(plugin.ITEM_TYPE_KEY, PersistentDataType.STRING) ?: return
        if (type != "ITEM") return

        // Only cancel if there's a left click ability
        if (!container.has(plugin.LEFT_CLICK_ABILITY_KEY, PersistentDataType.STRING)) return

        // Execute ability
        if (AbilityManager.executeAbility(item, event.player)) {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onEntityDamage(event: EntityDamageByEntityEvent) {
        // Only handle player left-click attacks
        if (event.cause != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return
        if (event.entity == event.damager) return
        
        val damager = event.damager as? Player ?: return
        val item = damager.inventory.itemInMainHand
        val meta = item.itemMeta ?: return
        val container = meta.persistentDataContainer
        val plugin = SneakyPrototypeKit.getInstance()

        // Check if this is a prototype item
        val type = container.get(plugin.ITEM_TYPE_KEY, PersistentDataType.STRING) ?: return
        if (type != "ITEM") return

        // Only cancel if there's a left click ability
        if (!container.has(plugin.LEFT_CLICK_ABILITY_KEY, PersistentDataType.STRING)) return

        // Execute ability and cancel damage
        if (AbilityManager.executeAbility(item, damager)) {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onPlayerConsume(event: PlayerItemConsumeEvent) {
        val item = event.item
        val meta = item.itemMeta ?: return
        val container = meta.persistentDataContainer
        val plugin = SneakyPrototypeKit.getInstance()

        // Check if this is a prototype consumable
        val type = container.get(plugin.ITEM_TYPE_KEY, PersistentDataType.STRING) ?: return
        if (type != "FOOD" && type != "DRINK") return

        // Only cancel if there's a consume ability
        if (!container.has(plugin.CONSUME_ABILITY_KEY, PersistentDataType.STRING)) return

        // Get ability and config
        val ability = container.get(plugin.CONSUME_ABILITY_KEY, PersistentDataType.STRING) ?: return
        val abilityConfig = plugin.config.getConfigurationSection("abilities.$ability") ?: return
        val cooldownMs = abilityConfig.getLong("cooldown", AbilityManager.DEFAULT_COOLDOWN_MS)

        // Check cooldown
        if (AbilityManager.isOnCooldown(event.player, ability, cooldownMs)) {
            event.isCancelled = true
            return
        }

        // Execute ability
        AbilityManager.executeAbility(item, event.player)
    }
} 