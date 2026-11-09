package net.sneakyprototypekit.creation.ui

import org.bukkit.event.inventory.InventoryAction
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CreationGuiGuardTest {
    @Test
    fun `drags touching any GUI slot are blocked`() {
        assertTrue(CreationGuiGuard.dragTouchesTop(listOf(30, 26), topSize = 27))
        assertTrue(CreationGuiGuard.dragTouchesTop(listOf(0), topSize = 27))
    }

    @Test
    fun `drags only within the player's inventory are allowed`() {
        assertFalse(CreationGuiGuard.dragTouchesTop(listOf(27, 40, 62), topSize = 27))
    }

    @Test
    fun `shift-click and collect-to-cursor from the player's inventory count as moving into the GUI`() {
        assertTrue(CreationGuiGuard.movesIntoOtherInventory(InventoryAction.MOVE_TO_OTHER_INVENTORY))
        assertTrue(CreationGuiGuard.movesIntoOtherInventory(InventoryAction.COLLECT_TO_CURSOR))
        assertFalse(CreationGuiGuard.movesIntoOtherInventory(InventoryAction.PICKUP_ALL))
        assertFalse(CreationGuiGuard.movesIntoOtherInventory(InventoryAction.PLACE_ALL))
    }
}
