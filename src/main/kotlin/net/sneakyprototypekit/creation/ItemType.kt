package net.sneakyprototypekit.creation

/**
 * Defines the types of items that can be created.
 * Each type has different behaviors and configurations.
 */
enum class ItemType {
    ITEM,
    FOOD,
    DRINK;

    companion object {
        fun fromString(value: String): ItemType? = values().find { it.name.equals(value, ignoreCase = true) }
    }
} 