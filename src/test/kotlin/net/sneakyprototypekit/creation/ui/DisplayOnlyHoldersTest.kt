package net.sneakyprototypekit.creation.ui

import com.sneakyutils.util.DisplayOnlyInventoryHolder
import kotlin.test.Test
import kotlin.test.assertTrue

/** SPK GUIs show icons only; the marker tells SneakyItems not to scan them on open. */
class DisplayOnlyHoldersTest {
    @Test
    fun `every creation GUI holder is display-only`() {
        listOf(
            CustomInventoryHolder::class.java,
            AbilitySelectionUI::class.java,
            IconSelectionUI::class.java,
        ).forEach {
            assertTrue(DisplayOnlyInventoryHolder::class.java.isAssignableFrom(it), "${it.simpleName} must be display-only")
        }
    }
}
