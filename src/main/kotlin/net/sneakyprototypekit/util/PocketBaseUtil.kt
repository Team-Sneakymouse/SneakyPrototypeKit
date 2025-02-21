@file:Suppress("PROVIDED_RUNTIME_TOO_LOW")

package net.sneakyprototypekit.util

import com.danidipp.sneakypocketbase.SneakyPocketbase
import com.danidipp.sneakypocketbase.PBRunnable
import io.github.agrevster.pocketbaseKotlin.models.utils.BaseModel
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.bukkit.Bukkit
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import net.sneakyprototypekit.SneakyPrototypeKit

@Serializable
data class PrototypeKitRecord(
    val type: String,
    val ability: String,
    val creator: String,
    val name: String,
    val material: String,
    val model_data: Int? = null,
    val lore: String? = null
) : BaseModel()

/**
 * Utility class for interacting with PocketBase.
 * Handles logging of finalized prototype kit items.
 */
object PocketBaseUtil {
    private const val COLLECTION_NAME = "lom2_prototype_kit_items"

    /**
     * Logs a finalized prototype kit item to PocketBase.
     * Records the item's type, ability, creator, and other metadata.
     * 
     * @param item The finalized item to log
     * @return true if the logging was successful
     */
    fun logFinalizedKit(item: ItemStack): Boolean {
        val meta = item.itemMeta ?: return false
        val container = meta.persistentDataContainer
        val plugin = SneakyPrototypeKit.getInstance()

        // Get required data
        val type = container.get(plugin.ITEM_TYPE_KEY, PersistentDataType.STRING) ?: return false
        val creator = container.get(plugin.CREATOR_KEY, PersistentDataType.STRING) ?: "Unknown"
        val name = container.get(plugin.NAME_KEY, PersistentDataType.STRING) ?: "Unnamed Item"
        val lore = container.get(plugin.LORE_KEY, PersistentDataType.STRING)
        
        // Get ability based on type
        val ability = when (type) {
            "ITEM" -> container.get(plugin.LEFT_CLICK_ABILITY_KEY, PersistentDataType.STRING)
            else -> container.get(plugin.CONSUME_ABILITY_KEY, PersistentDataType.STRING)
        } ?: return false

        // Create record data
        val record = PrototypeKitRecord(
            type = type,
            ability = ability,
            creator = creator,
            name = name,
            material = item.type.name,
            model_data = if (meta.hasCustomModelData()) meta.customModelData else null,
            lore = lore
        )

        // Serialize record to JSON
        val data = Json.encodeToString(serializer(), record)

        try {
            // Create record in PocketBase asynchronously
            Bukkit.getScheduler().runTaskAsynchronously(
                SneakyPrototypeKit.getInstance(),
                PBRunnable {
                    SneakyPocketbase.getInstance().pb().records.create<PrototypeKitRecord>(COLLECTION_NAME, data)
                }
            )
            return true
        } catch (e: Exception) {
            plugin.logger.warning("Failed to log finalized kit to PocketBase: ${e.message}")
            return false
        }
    }
} 