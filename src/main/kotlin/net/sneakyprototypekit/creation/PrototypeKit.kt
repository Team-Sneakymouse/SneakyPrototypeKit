package net.sneakyprototypekit.creation

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.sneakyprototypekit.SneakyPrototypeKit
import net.sneakyprototypekit.util.TextUtility
import org.bukkit.Material
import org.bukkit.entity.Player
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
     * @return The created item, or null if required data is missing
     */
    fun createItemFromPDC(meta: ItemMeta, additionalLoreLine: String? = null): ItemStack? {
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
        
        // Set icon
        val iconData = meta.persistentDataContainer.get(
            plugin.ICON_DATA_KEY,
            PersistentDataType.STRING
        )
        if (iconData != null) {
            val (material, modelData) = iconData.split(",")
            item.type = Material.valueOf(material)
            itemMeta.setCustomModelData(modelData.toInt())
        }
        
        // Set name
        val name = meta.persistentDataContainer.get(
            plugin.NAME_KEY,
            PersistentDataType.STRING
        )
        if (name != null) {
            itemMeta.displayName(TextUtility.convertToComponent("&c$name"))
        }
        
        // Set lore
        val lore = meta.persistentDataContainer.get(
            plugin.LORE_KEY,
            PersistentDataType.STRING
        )
        val loreList = mutableListOf<Component>()
        
        if (lore != null) {
            loreList.addAll(TextUtility.wrapLore(lore))
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
                    loreList.addAll(TextUtility.wrapLore("$prefix$desc"))
                }
                
                // Add charges line and handle stack sizes based on type
                when (type) {
                    ItemType.ITEM -> {
                        loreList.add(TextUtility.convertToComponent("&eCharges: &f$charges"))
                        
                        // Set stack size and store charges
                        itemMeta.setMaxStackSize(stackSize)
                        item.amount = stackSize
                        itemMeta.persistentDataContainer.set(
                            plugin.LEFT_CLICK_CHARGES_KEY,
                            PersistentDataType.INTEGER,
                            charges
                        )
                    }
                    ItemType.FOOD, ItemType.DRINK -> {
                        // For consumables, multiply stack size by charges
                        val totalAmount = (stackSize * charges).coerceAtMost(99)
                        itemMeta.setMaxStackSize(totalAmount)
                        item.amount = totalAmount
                    }
                    else -> item.amount = 1
                }
                
                // Store type and ability
                itemMeta.persistentDataContainer.set(
                    plugin.ITEM_TYPE_KEY,
                    PersistentDataType.STRING,
                    type.name
                )
                when (type) {
                    ItemType.ITEM -> itemMeta.persistentDataContainer.set(
                        plugin.LEFT_CLICK_ABILITY_KEY,
                        PersistentDataType.STRING,
                        abilityKey
                    )
                    else -> itemMeta.persistentDataContainer.set(
                        plugin.CONSUME_ABILITY_KEY,
                        PersistentDataType.STRING,
                        abilityKey
                    )
                }
            }
        }
        
        // Add additional lore line if provided
        if (additionalLoreLine != null) {
            loreList.add(TextUtility.convertToComponent(""))
            loreList.add(TextUtility.convertToComponent(additionalLoreLine))
        }
        
        itemMeta.lore(loreList)
        item.itemMeta = itemMeta
        return item
    }
    
    /**
     * Updates a prototype kit item with the current session data.
     */
    fun updateKit(item: ItemStack, session: ItemCreationManager.CreationSession) {
        val meta = item.itemMeta ?: return
        val plugin = SneakyPrototypeKit.getInstance()
        val container = meta.persistentDataContainer
        
        // Set custom model data from session or clear it
        session.modelData?.let { meta.setCustomModelData(it) } ?: meta.setCustomModelData(null)
        
        // Update material if set
        session.material?.let { 
            @Suppress("DEPRECATION")
            item.type = it
        }
        
        // Process lore
        val loreList = mutableListOf<Component>()
        
        // Add custom lore if set
        session.lore.forEach { loreLine ->
            val coloredLine = if (!loreLine.startsWith("&")) "&7$loreLine" else loreLine
            loreList.addAll(TextUtility.wrapLore(coloredLine))
        }
        
        // Add ability description if set
        session.ability?.let { ability ->
            val abilityConfig = plugin.config.getConfigurationSection("abilities.$ability") ?: return@let
            
            abilityConfig.getString("description")?.let { desc ->                
                // Get the appropriate prefix based on type
                val prefix = when (session.type) {
                    ItemType.ITEM -> plugin.config.getString("prefix-left-click", "&e[Left Click] &7")
                    ItemType.FOOD, ItemType.DRINK -> plugin.config.getString("prefix-right-click", "&e[Right Click] &7")
                    else -> "&7"
                }
                
                loreList.addAll(TextUtility.wrapLore("$prefix$desc"))
            }
        }
        
        // Add finalise instruction
        if (loreList.isNotEmpty()) {
            loreList.add(TextUtility.convertToComponent(""))
        }
        loreList.add(TextUtility.convertToComponent("&eShift + Right Click &7to finalise"))
        
        // Update name if set
        session.name?.let {
            meta.displayName(TextUtility.convertToComponent("&c$it"))
        }
        
        // Update lore
        meta.lore(loreList)
        
        // Store session data
        session.type?.let { container.set(plugin.ITEM_TYPE_KEY, PersistentDataType.STRING, it.name) }
        session.ability?.let { 
            when (session.type) {
                ItemType.ITEM -> container.set(plugin.LEFT_CLICK_ABILITY_KEY, PersistentDataType.STRING, it)
                ItemType.FOOD, ItemType.DRINK -> container.set(plugin.CONSUME_ABILITY_KEY, PersistentDataType.STRING, it)
                else -> container.set(plugin.LEFT_CLICK_ABILITY_KEY, PersistentDataType.STRING, it)
            }
        }
        
        item.itemMeta = meta
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
     */
    fun finaliseKit(item: ItemStack): ItemStack? {
        if (!isPrototypeKit(item)) return null
        val meta = item.itemMeta ?: return null
        return createItemFromPDC(meta)
    }
} 