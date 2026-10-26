package net.sneakyprototypekit.util

import net.sneakyprototypekit.SneakyPrototypeKit
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

data class PrototypeKitRecord(
    val type: String,
    val ability: String,
    val creator: String,
    val name: String,
    val material: String,
    val model_data: Int? = null,
    val lore: String? = null
)

object DatabaseUtil {
    /**
     * Snapshot Bukkit metadata on the server thread, then submit an asynchronous, best-effort log.
     * True means accepted for logging, not confirmation of a committed INSERT.
     */
    fun logFinalizedKit(item: ItemStack): Boolean {
        val meta = item.itemMeta ?: return false
        val container = meta.persistentDataContainer
        val plugin = SneakyPrototypeKit.getInstance()
        val type = container.get(plugin.ITEM_TYPE_KEY, PersistentDataType.STRING) ?: return false
        val ability = when (type) {
            "ITEM" -> container.get(plugin.LEFT_CLICK_ABILITY_KEY, PersistentDataType.STRING)
            else -> container.get(plugin.CONSUME_ABILITY_KEY, PersistentDataType.STRING)
        } ?: return false

        val record = PrototypeKitRecord(
            type = type,
            ability = ability,
            creator = container.get(plugin.CREATOR_KEY, PersistentDataType.STRING) ?: "Unknown",
            name = container.get(plugin.NAME_KEY, PersistentDataType.STRING) ?: "Unnamed Item",
            material = item.type.name,
            model_data = if (meta.hasCustomModelData()) meta.customModelData else null,
            lore = container.get(plugin.LORE_KEY, PersistentDataType.STRING)
        )
        val result = plugin.itemLog.log(record)
        // Completion may run on a SneakyDB worker. Only the thread-safe logger is used here.
        result.whenComplete { _, failure ->
            if (failure != null) {
                plugin.logger.warning(
                    "Failed to log finalized kit through SneakyDB; check database readiness, " +
                        "INSERT privileges and connectivity. Write may be uncertain; not retried."
                )
            }
        }
        return !result.isCompletedExceptionally
    }
}
