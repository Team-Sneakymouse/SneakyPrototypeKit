package net.sneakyprototypekit.creation.ui

import net.sneakyprototypekit.SneakyPrototypeKit
import net.sneakyprototypekit.creation.ItemType
import net.sneakyprototypekit.creation.PrototypeKit
import net.sneakyprototypekit.util.TextUtility
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
        
        event.isCancelled = true
        
        val clickedItem = event.currentItem ?: return
        val meta = clickedItem.itemMeta ?: return
        val player = event.whoClicked as? Player ?: return
        
        // Get prototype kit
        val prototypeKit = holder.getData("prototype_kit") as? ItemStack ?: return
        val kitMeta = prototypeKit.itemMeta ?: return
        
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
                    val finalItem = PrototypeKit.finaliseKit(prototypeKit)
                    if (finalItem != null) {
                        // Remove one prototype kit
                        prototypeKit.amount = prototypeKit.amount - 1
                        
                        // Give the final item
                        player.inventory.addItem(finalItem)
                        player.closeInventory()
                        player.sendMessage(TextUtility.convertToComponent("&aYour item has been created!"))
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