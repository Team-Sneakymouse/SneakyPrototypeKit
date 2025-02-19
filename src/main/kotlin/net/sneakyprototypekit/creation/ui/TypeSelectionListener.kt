package net.sneakyprototypekit.creation.ui

import net.sneakyprototypekit.SneakyPrototypeKit
import net.sneakyprototypekit.creation.ItemType
import net.sneakyprototypekit.util.TextUtility
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

class TypeSelectionListener : Listener {
    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val holder = event.inventory.holder as? CustomInventoryHolder ?: return
        val title = event.view.title()
        if (title != TextUtility.convertToComponent(TypeSelectionUI.TITLE)) return
        
        // Only handle clicks in our UI
        if (event.clickedInventory != event.view.topInventory) {
            event.isCancelled = true
            return
        }
        
        event.isCancelled = true
        
        val clickedItem = event.currentItem ?: return
        val meta = clickedItem.itemMeta ?: return
        
        // Verify this is one of our type buttons
        if (!meta.persistentDataContainer.has(
            SneakyPrototypeKit.getInstance().ITEM_TYPE_KEY,
            PersistentDataType.STRING
        )) return
        
        val player = event.whoClicked as? Player ?: return
        
        // Get prototype kit
        val prototypeKit = holder.getData("prototype_kit") as? ItemStack ?: return
        val kitMeta = prototypeKit.itemMeta ?: return
        
        // Get type from clicked item
        val typeStr = meta.persistentDataContainer.get(
            SneakyPrototypeKit.getInstance().ITEM_TYPE_KEY,
            PersistentDataType.STRING
        ) ?: return
        
        val type = ItemType.valueOf(typeStr)
        val plugin = SneakyPrototypeKit.getInstance()
        val container = kitMeta.persistentDataContainer
        
        // Clear existing data
        container.remove(plugin.LEFT_CLICK_ABILITY_KEY)
        container.remove(plugin.CONSUME_ABILITY_KEY)
        container.remove(plugin.ICON_DATA_KEY)
        
        // Store new type
        container.set(
            plugin.ITEM_TYPE_KEY,
            PersistentDataType.STRING,
            type.name
        )

        // Set default name only if no name is currently set
        if (!container.has(plugin.NAME_KEY, PersistentDataType.STRING)) {
            container.set(
                plugin.NAME_KEY,
                PersistentDataType.STRING,
                "Unnamed ${type.name.lowercase().replaceFirstChar { it.uppercase() }}"
            )
        }
        
        // Get first available ability for this type
        val abilitiesSection = plugin.config.getConfigurationSection("abilities") ?: return
        for (abilityKey in abilitiesSection.getKeys(false)) {
            val ability = abilitiesSection.getConfigurationSection(abilityKey) ?: continue
            val allowedTypes = ability.getStringList("allowed-types")
            if (allowedTypes.contains(type.name)) {
                // Found a valid ability, store it
                when (type) {
                    ItemType.ITEM -> container.set(
                        plugin.LEFT_CLICK_ABILITY_KEY,
                        PersistentDataType.STRING,
                        abilityKey
                    )
                    else -> container.set(
                        plugin.CONSUME_ABILITY_KEY,
                        PersistentDataType.STRING,
                        abilityKey
                    )
                }
                break
            }
        }
        
        // Get first available icon for this type
        val iconsSection = plugin.config.getConfigurationSection(when (type) {
            ItemType.FOOD -> "food-icons"
            ItemType.DRINK -> "drink-icons"
            else -> "item-icons"
        }) ?: return
        
        val firstMaterial = iconsSection.getKeys(false).firstOrNull()?.let { Material.matchMaterial(it) } ?: Material.STONE
        val modelData = iconsSection.getStringList(firstMaterial.name).firstOrNull()?.let { 
            if (it.contains("-")) it.split("-")[0].toIntOrNull() else it.toIntOrNull()
        } ?: 1
        
        // Store icon data
        container.set(
            plugin.ICON_DATA_KEY,
            PersistentDataType.STRING,
            "${firstMaterial.name},$modelData"
        )
        
        prototypeKit.itemMeta = kitMeta
        
        // Return to main menu
        player.closeInventory()
        MainCreationUI.open(player, prototypeKit)
    }
} 