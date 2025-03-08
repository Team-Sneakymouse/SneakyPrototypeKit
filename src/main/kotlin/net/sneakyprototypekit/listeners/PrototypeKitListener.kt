package net.sneakyprototypekit.listeners

import net.sneakyprototypekit.creation.PrototypeKit
import net.sneakyprototypekit.creation.ui.MainCreationUI
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerItemConsumeEvent

/**
 * Handles interactions with prototype kit items.
 * Manages right-click to open creation menu and shift-right-click to finalize items.
 */
class PrototypeKitListener : Listener {
    /**
     * Handles player interactions with prototype kit items.
     * - Left click or normal right click opens the creation menu
     * - Shift + right click finalizes the item
     */
    @EventHandler
    fun onPrototypeKitInteract(event: PlayerInteractEvent) {
        val item = event.item ?: return
        if (!PrototypeKit.isPrototypeKit(item)) return
        
        event.isCancelled = true
        
        when (event.action) {
            Action.RIGHT_CLICK_AIR, Action.RIGHT_CLICK_BLOCK, Action.LEFT_CLICK_AIR, Action.LEFT_CLICK_BLOCK -> {
                // Open main creation menu
                MainCreationUI.open(event.player, item)
                event.player.playSound(event.player.location, "lom:computer.ding", 999f, 1f)
            }
            else -> return
        }
    }

    /**
     * Prevents consuming prototype kit items and opens the creation menu instead.
     */
    @EventHandler
    fun onPrototypeKitConsume(event: PlayerItemConsumeEvent) {
        val item = event.item
        if (!PrototypeKit.isPrototypeKit(item)) return
        
        event.isCancelled = true
        MainCreationUI.open(event.player, item)
        event.player.playSound(event.player.location, "lom:computer.ding", 999f, 1f)
    }
} 