package net.sneakyprototypekit.creation.ui

import net.sneakyprototypekit.SneakyPrototypeKit
import net.sneakyprototypekit.creation.ItemType
import net.sneakyprototypekit.creation.PrototypeKit
import net.sneakyprototypekit.util.TextUtility
import net.sneakyprototypekit.util.PocketBaseUtil
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

class MainCreationListener : Listener {
    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val holder = event.inventory.holder as? CustomInventoryHolder ?: return
        val title = event.view.title()
        if (title != TextUtility.convertToComponent(MainCreationUI.TITLE)) return
        
        // Get prototype kit
        val prototypeKit = holder.getData("prototype_kit") as? ItemStack ?: return
        val kitMeta = prototypeKit.itemMeta ?: return
        
        // Handle item drag into the UI
        if (event.action.name.contains("PLACE")) {
            val draggedItem = event.cursor
            val draggedMeta = draggedItem.itemMeta ?: return
            val plugin = SneakyPrototypeKit.getInstance()
            
            // Check if this is an item created by our plugin (has type and ability)
            val typeStr = draggedMeta.persistentDataContainer.get(
                plugin.ITEM_TYPE_KEY,
                PersistentDataType.STRING
            ) ?: return
            
            val hasAbility = draggedMeta.persistentDataContainer.has(plugin.LEFT_CLICK_ABILITY_KEY, PersistentDataType.STRING) ||
                            draggedMeta.persistentDataContainer.has(plugin.CONSUME_ABILITY_KEY, PersistentDataType.STRING)
            
            if (!hasAbility) return
            
            // Cancel the drag
            event.isCancelled = true
            
            if (event.clickedInventory == event.view.topInventory) {
                // Copy settings from dragged item to prototype kit
                val container = kitMeta.persistentDataContainer
                
                // Clear existing data
                container.remove(plugin.LEFT_CLICK_ABILITY_KEY)
                container.remove(plugin.CONSUME_ABILITY_KEY)
                container.remove(plugin.ICON_DATA_KEY)
                container.remove(plugin.NAME_KEY)
                container.remove(plugin.LORE_KEY)
                
                // Copy type
                container.set(
                    plugin.ITEM_TYPE_KEY,
                    PersistentDataType.STRING,
                    typeStr
                )
                
                // Copy abilities
                draggedMeta.persistentDataContainer.get(plugin.LEFT_CLICK_ABILITY_KEY, PersistentDataType.STRING)?.let {
                    container.set(plugin.LEFT_CLICK_ABILITY_KEY, PersistentDataType.STRING, it)
                }
                draggedMeta.persistentDataContainer.get(plugin.CONSUME_ABILITY_KEY, PersistentDataType.STRING)?.let {
                    container.set(plugin.CONSUME_ABILITY_KEY, PersistentDataType.STRING, it)
                }
                
                // Copy name
                draggedMeta.persistentDataContainer.get(plugin.NAME_KEY, PersistentDataType.STRING)?.let {
                    container.set(plugin.NAME_KEY, PersistentDataType.STRING, it)
                }
                
                // Copy lore
                draggedMeta.persistentDataContainer.get(plugin.LORE_KEY, PersistentDataType.STRING)?.let {
                    container.set(plugin.LORE_KEY, PersistentDataType.STRING, it)
                }
                
                // Copy icon data
                draggedMeta.persistentDataContainer.get(plugin.ICON_DATA_KEY, PersistentDataType.STRING)?.let {
                    container.set(plugin.ICON_DATA_KEY, PersistentDataType.STRING, it)
                }
                
                // Update prototype kit
                prototypeKit.itemMeta = kitMeta
            }
                
            // Reopen UI after 1 tick to update preview
            Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                MainCreationUI.open(event.whoClicked as Player, prototypeKit)
            }, 1L)

            return
        }

        // Allow picking up items that have a type
        if (event.clickedInventory != event.view.topInventory && event.currentItem != null) {
            val clickedMeta = event.currentItem?.itemMeta ?: return
            val plugin = SneakyPrototypeKit.getInstance()
            
            // If the item has a type, allow the interaction
            if (clickedMeta.persistentDataContainer.has(plugin.ITEM_TYPE_KEY, PersistentDataType.STRING)) {
                return
            }
        }
        
        event.isCancelled = true
        
        val clickedItem = event.currentItem ?: return
        val meta = clickedItem.itemMeta ?: return
        val player = event.whoClicked as? Player ?: return
        
        // Get action
        val action = meta.persistentDataContainer.get(
            SneakyPrototypeKit.getInstance().NAVIGATION_KEY,
            PersistentDataType.STRING
        ) ?: return
        
        // Handle action
        Bukkit.getScheduler().runTaskLater(SneakyPrototypeKit.getInstance(), Runnable {
            when (action) {
                "preview" -> {
                    // Check if type is selected
                    val typeStr = kitMeta.persistentDataContainer.get(
                        SneakyPrototypeKit.getInstance().ITEM_TYPE_KEY,
                        PersistentDataType.STRING
                    )
                    if (typeStr == null) {
                        player.sendMessage(TextUtility.convertToComponent("&cPlease select a type first!"))
                        MainCreationUI.open(player, prototypeKit)
                        return@Runnable
                    }
                    
                    // Check if the prototype kit is still in the player's inventory
                    if (!player.inventory.containsAtLeast(prototypeKit, 1)) {
                        player.sendMessage(TextUtility.convertToComponent("&cThe prototype kit is no longer in your inventory!"))
                        player.closeInventory()
                        return@Runnable
                    }
                    
                    // Create the final item
                    val finalItem = PrototypeKit.finaliseKit(prototypeKit, creator = player.name)
                    if (finalItem != null) {
                        // Remove one prototype kit
                        prototypeKit.amount -= 1
                        
                        // Give the final item
                        player.inventory.addItem(finalItem)
                        player.closeInventory()
                        player.sendMessage(TextUtility.convertToComponent("&aYour item has been created!"))

                        // Log to PocketBase
                        PocketBaseUtil.logFinalizedKit(finalItem)
                    } else {
                        player.sendMessage(TextUtility.convertToComponent("&cFailed to create item. Please try again."))
                        MainCreationUI.open(player, prototypeKit)
                    }
                }
                "type" -> TypeSelectionUI.open(player, prototypeKit)
                "ability" -> {
                    // Check if type is selected
                    val typeStr = kitMeta.persistentDataContainer.get(
                        SneakyPrototypeKit.getInstance().ITEM_TYPE_KEY,
                        PersistentDataType.STRING
                    )
                    if (typeStr != null) {
                        val type = ItemType.valueOf(typeStr)
                        AbilitySelectionUI.open(player, type, 0, prototypeKit) { ability ->
                            val updatedMeta = prototypeKit.itemMeta ?: return@open
                            when (type) {
                                ItemType.ITEM -> updatedMeta.persistentDataContainer.set(
                                    SneakyPrototypeKit.getInstance().LEFT_CLICK_ABILITY_KEY,
                                    PersistentDataType.STRING,
                                    ability
                                )
                                else -> updatedMeta.persistentDataContainer.set(
                                    SneakyPrototypeKit.getInstance().CONSUME_ABILITY_KEY,
                                    PersistentDataType.STRING,
                                    ability
                                )
                            }
                            prototypeKit.itemMeta = updatedMeta
                            MainCreationUI.open(player, prototypeKit)
                        }
                    } else {
                        player.sendMessage(TextUtility.convertToComponent("&cPlease select a type first!"))
                        MainCreationUI.open(player, prototypeKit)
                    }
                }
                "icon" -> {
                    // Check if type is selected
                    val typeStr = kitMeta.persistentDataContainer.get(
                        SneakyPrototypeKit.getInstance().ITEM_TYPE_KEY,
                        PersistentDataType.STRING
                    )
                    if (typeStr != null) {
                        val type = ItemType.valueOf(typeStr)
                        IconSelectionUI.open(player, type, 0, prototypeKit) { material, modelData ->
                            val updatedMeta = prototypeKit.itemMeta ?: return@open
                            updatedMeta.persistentDataContainer.set(
                                SneakyPrototypeKit.getInstance().ICON_DATA_KEY,
                                PersistentDataType.STRING,
                                "${material.name},${modelData}"
                            )
                            prototypeKit.itemMeta = updatedMeta
                            MainCreationUI.open(player, prototypeKit)
                        }
                    } else {
                        player.sendMessage(TextUtility.convertToComponent("&cPlease select a type first!"))
                        MainCreationUI.open(player, prototypeKit)
                    }
                }
                "name" -> {
                    player.closeInventory()
                    player.sendMessage(TextUtility.convertToComponent("&eEnter a name for your item (max 30 characters):"))
                    ChatInputListener.register(player, NameInputListener(player) { name ->
                        val updatedMeta = prototypeKit.itemMeta ?: return@NameInputListener
                        updatedMeta.persistentDataContainer.set(
                            SneakyPrototypeKit.getInstance().NAME_KEY,
                            PersistentDataType.STRING,
                            name
                        )
                        prototypeKit.itemMeta = updatedMeta
                        // Schedule inventory opening on main thread
                        Bukkit.getScheduler().runTask(SneakyPrototypeKit.getInstance(), Runnable {
                            MainCreationUI.open(player, prototypeKit)
                        })
                    })
                }
                "lore" -> {
                    player.closeInventory()
                    player.sendMessage(TextUtility.convertToComponent("&eEnter lore for your item (max 100 characters):"))
                    ChatInputListener.register(player, LoreInputListener(player) { lore ->
                        val updatedMeta = prototypeKit.itemMeta ?: return@LoreInputListener
                        updatedMeta.persistentDataContainer.set(
                            SneakyPrototypeKit.getInstance().LORE_KEY,
                            PersistentDataType.STRING,
                            lore
                        )
                        prototypeKit.itemMeta = updatedMeta
                        // Schedule inventory opening on main thread
                        Bukkit.getScheduler().runTask(SneakyPrototypeKit.getInstance(), Runnable {
                            MainCreationUI.open(player, prototypeKit)
                        })
                    })
                }
            }
        }, 1L)
    }
} 