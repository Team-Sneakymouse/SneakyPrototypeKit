package net.sneakyprototypekit.creation

import net.kyori.adventure.text.Component
import net.sneakyprototypekit.SneakyPrototypeKit
import net.sneakyprototypekit.util.TextUtility
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType

/**
 * Manages the creation and updating of prototype kit items.
 */
object PrototypeKit {
    /**
     * Creates a new prototype kit item from config.
     */
    fun createKit(): ItemStack {
        val plugin = SneakyPrototypeKit.getInstance()
        val config = plugin.config.getConfigurationSection("prototype-kit") ?: return ItemStack(Material.NETHER_STAR)
        
        return ItemStack(
            Material.valueOf(config.getString("material", "NETHER_STAR")?.uppercase() ?: "NETHER_STAR")
        ).apply {
            itemMeta = itemMeta?.also { meta ->
                // Set name
                meta.displayName(TextUtility.convertToComponent(config.getString("name", "&ePrototype Kit")!!))
                
                // Set model data
                config.getInt("model-data", -1).takeIf { it > 0 }?.let {
                    meta.setCustomModelData(it)
                }
                
                // Set lore
                val lore = config.getStringList("lore")
                    .filterNot { it.contains("Shift + Right Click") } // Remove shift+right click instruction
                    .map { TextUtility.convertToComponent(it) }
                meta.lore(lore)
                
                // Add prototype tag
                meta.persistentDataContainer.set(
                    plugin.PROTOTYPE_KIT_KEY,
                    PersistentDataType.BYTE,
                    1
                )
                
                // Set max stack size to 1
                meta.setMaxStackSize(1)
            }
        }
    }
    
    /**
     * Creates an item from PDC data with optional additional lore line.
     * Used by both preview and final item creation.
     * 
     * @param meta The meta containing PDC data to create the item from
     * @param additionalLoreLine Optional line to add at the end of the lore
     * @param creator Optional name of the player creating the item
     * @return The created item, or null if required data is missing
     */
    fun createItemFromPDC(meta: ItemMeta, additionalLoreLine: String? = null, creator: String? = null): ItemStack? {
        val plugin = SneakyPrototypeKit.getInstance()
        
        // Get type
        val typeStr = meta.persistentDataContainer.get(
            plugin.ITEM_TYPE_KEY,
            PersistentDataType.STRING
        ) ?: return null
        
        val type = ItemType.valueOf(typeStr)
        
        // Create item
        val item = ItemStack(Material.STONE)
        val itemMeta = item.itemMeta ?: return null
        val container = itemMeta.persistentDataContainer
        
        // Copy type
        container.set(plugin.ITEM_TYPE_KEY, PersistentDataType.STRING, typeStr)
        
        // Store creator if provided
        if (creator != null) {
            container.set(plugin.CREATOR_KEY, PersistentDataType.STRING, creator)
        }
        
        // Set and store icon data
        val iconData = meta.persistentDataContainer.get(
            plugin.ICON_DATA_KEY,
            PersistentDataType.STRING
        )
        if (iconData != null) {
            val (material, modelData) = iconData.split(",")
            item.type = Material.valueOf(material)
            itemMeta.setCustomModelData(modelData.toInt())
            container.set(plugin.ICON_DATA_KEY, PersistentDataType.STRING, iconData)
        }
        
        // Set and store name
        val name = meta.persistentDataContainer.get(
            plugin.NAME_KEY,
            PersistentDataType.STRING
        )
        if (name != null) {
            itemMeta.displayName(TextUtility.convertToComponent("&c$name"))
            container.set(plugin.NAME_KEY, PersistentDataType.STRING, name)
        }
        
        // Set and store lore
        val lore = meta.persistentDataContainer.get(
            plugin.LORE_KEY,
            PersistentDataType.STRING
        )
        val loreList = mutableListOf<Component>()
        
        if (lore != null) {
            loreList.addAll(TextUtility.wrapLore(lore))
            container.set(plugin.LORE_KEY, PersistentDataType.STRING, lore)
        }
        
        // Get ability configuration
        val abilityKey = when (type) {
            ItemType.ITEM -> meta.persistentDataContainer.get(
                plugin.LEFT_CLICK_ABILITY_KEY,
                PersistentDataType.STRING
            )
            else -> meta.persistentDataContainer.get(
                plugin.CONSUME_ABILITY_KEY,
                PersistentDataType.STRING
            )
        }
        
        if (abilityKey != null) {
            val abilityConfig = plugin.config.getConfigurationSection("abilities.$abilityKey")
            if (abilityConfig != null) {
                val charges = abilityConfig.getInt("charges", 1)
                val stackSize = abilityConfig.getInt("stack-size", 1).coerceAtMost(99)
                
                // Add ability description
                abilityConfig.getString("description")?.let { desc ->
                    // Get the appropriate prefix based on type
                    val prefix = when (type) {
                        ItemType.ITEM -> plugin.config.getString("prefix-left-click", "&e[Left Click] &7")
                        else -> plugin.config.getString("prefix-right-click", "&e[Right Click] &7")
                    }
                    
                    // Add description with prefix
                    loreList.add(TextUtility.convertToComponent("$prefix$desc"))
                }
                
                // Add charges line and handle stack sizes based on type
                when (type) {
                    ItemType.ITEM -> {
                        loreList.add(TextUtility.convertToComponent("&eCharges: &f$charges"))
                        
                        // Set stack size and store charges
                        itemMeta.setMaxStackSize(stackSize)
                        item.amount = stackSize
                        container.set(plugin.LEFT_CLICK_CHARGES_KEY, PersistentDataType.INTEGER, charges)
                        container.set(plugin.LEFT_CLICK_ABILITY_KEY, PersistentDataType.STRING, abilityKey)
                    }
                    ItemType.FOOD, ItemType.DRINK -> {
                        // For consumables, multiply stack size by charges
                        val totalAmount = (stackSize * charges).coerceAtMost(99)
                        itemMeta.setMaxStackSize(totalAmount)
                        item.amount = totalAmount
                        container.set(plugin.CONSUME_ABILITY_KEY, PersistentDataType.STRING, abilityKey)
                    }
                }
            }
        }
        
        // Add additional lore line if provided
        if (additionalLoreLine != null) {
            if (loreList.isNotEmpty()) {
                loreList.add(TextUtility.convertToComponent(""))
            }
            loreList.add(TextUtility.convertToComponent(additionalLoreLine))
        }
        
        itemMeta.lore(loreList)
        item.itemMeta = itemMeta
        return item
    }
    
    /**
     * Checks if an item is a prototype kit.
     */
    fun isPrototypeKit(item: ItemStack): Boolean {
        val meta = item.itemMeta ?: return false
        return meta.persistentDataContainer.has(
            SneakyPrototypeKit.getInstance().PROTOTYPE_KIT_KEY,
            PersistentDataType.BYTE
        )
    }
    
    /**
     * Creates a final item from a prototype kit.
     * 
     * @param item The prototype kit item
     * @param creator The name of the player creating the item
     * @return The created item, or null if required data is missing
     */
    fun finaliseKit(item: ItemStack, creator: String? = null): ItemStack? {
        if (!isPrototypeKit(item)) return null
        val meta = item.itemMeta ?: return null
        return createItemFromPDC(meta, creator = creator)
    }
} 