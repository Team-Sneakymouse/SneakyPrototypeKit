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

/**
 * Listens for item and food interactions to trigger abilities.
 */
class AbilityListener : Listener {

    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        if (event.action != Action.LEFT_CLICK_AIR && event.action != Action.LEFT_CLICK_BLOCK) return
        
        val item = event.item ?: return
        val meta = item.itemMeta ?: return
        val container = meta.persistentDataContainer
        val plugin = SneakyPrototypeKit.getInstance()

        // Check if this is a prototype item
        val type = container.get(plugin.ITEM_TYPE_KEY, PersistentDataType.STRING) ?: return
        if (type != "ITEM") return

        // Execute ability
        if (AbilityManager.executeAbility(item, event.player)) {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onEntityDamage(event: EntityDamageByEntityEvent) {
        val damager = event.damager as? Player ?: return
        val item = damager.inventory.itemInMainHand
        val meta = item.itemMeta ?: return
        val container = meta.persistentDataContainer
        val plugin = SneakyPrototypeKit.getInstance()

        // Check if this is a prototype item
        val type = container.get(plugin.ITEM_TYPE_KEY, PersistentDataType.STRING) ?: return
        if (type != "ITEM") return

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