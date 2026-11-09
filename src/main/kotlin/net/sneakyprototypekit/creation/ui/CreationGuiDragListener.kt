package net.sneakyprototypekit.creation.ui

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.event.inventory.InventoryDragEvent

/**
 * Creation GUIs show icons only (they are display-only holders), so no drag may put
 * a real item into their slots.
 */
class CreationGuiDragListener : Listener {
    @EventHandler
    fun onInventoryDrag(event: InventoryDragEvent) {
        val top = event.view.topInventory
        val holder = top.holder
        if (holder !is CustomInventoryHolder && holder !is AbilitySelectionUI && holder !is IconSelectionUI) return
        if (CreationGuiGuard.dragTouchesTop(event.rawSlots, top.size)) event.isCancelled = true
    }
}

/** Pure rules that keep real items out of creation GUI slots. */
object CreationGuiGuard {
    /** Raw slots below the top inventory's size belong to the GUI. */
    fun dragTouchesTop(rawSlots: Collection<Int>, topSize: Int): Boolean = rawSlots.any { it < topSize }

    /** Bottom-inventory clicks that can move items into, or out of, the GUI slots. */
    fun movesIntoOtherInventory(action: InventoryAction): Boolean =
        action == InventoryAction.MOVE_TO_OTHER_INVENTORY || action == InventoryAction.COLLECT_TO_CURSOR
}
