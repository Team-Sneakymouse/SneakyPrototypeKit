package net.sneakyprototypekit.listeners

import com.sneakyitems.events.MagicItemClickEvent
import net.sneakyprototypekit.creation.PrototypeKit
import net.sneakyprototypekit.creation.ui.MainCreationUI
import net.sneakyprototypekit.integration.PrototypeMagicItems
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
 * Prefers [MagicItemClickEvent]; [PlayerInteractEvent] covers unstamped legacy copies.
 */
class PrototypeKitListener : Listener {
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onPrototypeKitClick(event: MagicItemClickEvent) {
        if (event.hand != EquipmentSlot.HAND) return
        if (event.itemKey != PrototypeMagicItems.KIT && !PrototypeKit.isPrototypeKit(event.item)) return
        event.isCancelled = true
        if (event.hasInteractResults()) {
            event.setUseInteractedBlock(Event.Result.DENY)
            event.setUseItemInHand(Event.Result.DENY)
        }
        open(event.player, event.item)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onPrototypeKitInteract(event: PlayerInteractEvent) {
        if (event.hand != EquipmentSlot.HAND) return
        val item = event.item ?: return
        if (PrototypeMagicItems.isKit(item)) return // MagicItemClickEvent handles stamped kits
        if (!PrototypeKit.isPrototypeKit(item)) return

        event.setUseInteractedBlock(Event.Result.DENY)
        event.setUseItemInHand(Event.Result.DENY)

        when (event.action) {
            Action.RIGHT_CLICK_AIR, Action.RIGHT_CLICK_BLOCK, Action.LEFT_CLICK_AIR, Action.LEFT_CLICK_BLOCK ->
                open(event.player, item)
            else -> return
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onPrototypeKitConsume(event: PlayerItemConsumeEvent) {
        val item = event.item
        if (!PrototypeKit.isPrototypeKit(item)) return

        event.isCancelled = true
        open(event.player, item)
    }

    private fun open(player: org.bukkit.entity.Player, item: org.bukkit.inventory.ItemStack) {
        MainCreationUI.open(player, item)
        player.playSound(player.location, "lom:computer.ding", 999f, 1f)
    }
}
