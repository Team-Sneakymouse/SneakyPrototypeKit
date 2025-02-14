package net.sneakyprototypekit.creation.ui

import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder

/**
 * Custom inventory holder that can store additional data.
 * Used by UIs to maintain state and pass data between event handlers.
 */
class CustomInventoryHolder : InventoryHolder {
    private val data = mutableMapOf<String, Any>()
    private var inventory: Inventory? = null
    
    /**
     * Gets the inventory associated with this holder.
     * @throws IllegalStateException if the inventory has not been set
     */
    override fun getInventory(): Inventory {
        return inventory ?: throw IllegalStateException("Inventory not set")
    }
    
    /**
     * Sets the inventory for this holder.
     * @param inventory The inventory to set
     */
    fun setInventory(inventory: Inventory) {
        this.inventory = inventory
    }
    
    /**
     * Stores a value in the holder's data map.
     * @param key The key to store the value under
     * @param value The value to store
     */
    fun setData(key: String, value: Any) {
        data[key] = value
    }
    
    /**
     * Retrieves a value from the holder's data map.
     * @param key The key to retrieve the value for
     * @return The stored value, or null if not found
     */
    fun getData(key: String): Any? {
        return data[key]
    }
    
    /**
     * Checks if a key exists in the holder's data map.
     * @param key The key to check for
     * @return true if the key exists
     */
    fun hasData(key: String): Boolean {
        return data.containsKey(key)
    }
    
    /**
     * Clears all stored data from the holder.
     */
    fun clearData() {
        data.clear()
    }
} 