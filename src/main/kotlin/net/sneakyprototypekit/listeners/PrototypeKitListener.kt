package net.sneakyprototypekit.listeners

import net.sneakyprototypekit.creation.PrototypeKit
import net.sneakyprototypekit.creation.ui.MainCreationUI
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerItemConsumeEvent
import org.bukkit.inventory.EquipmentSlot

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
    // Paper pre-cancels air interactions when there is no vanilla action to perform.
    // Apply the kit's interaction results after normal-priority plugin handlers.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onPrototypeKitInteract(event: PlayerInteractEvent) {
        if (event.hand != EquipmentSlot.HAND) return
        val item = event.item ?: return
        if (!PrototypeKit.isPrototypeKit(item)) return
        
        event.setUseInteractedBlock(Event.Result.DENY)
        event.setUseItemInHand(Event.Result.DENY)
        
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
    @EventHandler(ignoreCancelled = true)
    fun onPrototypeKitConsume(event: PlayerItemConsumeEvent) {
        val item = event.item
        if (!PrototypeKit.isPrototypeKit(item)) return
        
        event.isCancelled = true
        MainCreationUI.open(event.player, item)
        event.player.playSound(event.player.location, "lom:computer.ding", 999f, 1f)
    }
}
