package net.sneakyprototypekit.creation.ui

import net.sneakyprototypekit.SneakyPrototypeKit
import net.sneakyprototypekit.creation.PrototypeKit
import net.sneakyprototypekit.util.TextUtility
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

/**
 * Main UI for item creation.
 * Shows a preview of the item being created and buttons for each customization option.
 */
object MainCreationUI {
    const val TITLE = "&8Item Creation"
    
    /**
     * Opens the main creation UI for a player.
     * 
     * @param player The player to show the UI to
     * @param prototypeKit The prototype kit being used
     */
    fun open(player: Player, prototypeKit: ItemStack) {
        // Remove MagicItem PDC
        prototypeKit.apply { itemMeta = itemMeta?.also { meta ->
            meta.persistentDataContainer.remove(NamespacedKey("magicspells", "magicitem"))
        } }

        val inventory = Bukkit.createInventory(
            CustomInventoryHolder().apply { 
                setData("prototype_kit", prototypeKit)
            },
            27,
            TextUtility.convertToComponent(TITLE)
        )
        
        // Add preview item
        val previewItem = createPreviewItem(prototypeKit)
        if (previewItem != null) {
            inventory.setItem(17, previewItem)
        } else {
            // Show empty preview with instructions
            inventory.setItem(17, createEmptyPreview())
        }
        
        // Add buttons
        inventory.setItem(11, createButton(Material.COMPASS, "Type", "Select the item's type", "type"))
        inventory.setItem(12, createButton(Material.BLAZE_POWDER, "Ability", "Select the item's ability", "ability"))
        inventory.setItem(13, createButton(Material.PAINTING, "Icon", "Select the item's appearance", "icon"))
        inventory.setItem(14, createButton(Material.NAME_TAG, "Name", "Set the item's name", "name"))
        inventory.setItem(15, createButton(Material.BOOK, "Lore", "Set the item's description", "lore"))
        
        // Add GUI elements
        inventory.setItem(26, ItemStack(Material.JIGSAW).apply {
            itemMeta = itemMeta?.also { meta ->
                meta.isHideTooltip = true
                meta.setCustomModelData(3047)
            }
        })

        player.openInventory(inventory)
    }
    
    /**
     * Creates an empty preview item shown when no type is selected.
     */
    private fun createEmptyPreview(): ItemStack {
        return ItemStack(Material.BARRIER).apply {
            itemMeta = itemMeta?.also { meta ->
                meta.displayName(TextUtility.convertToComponent("&cNo Type Selected"))
                meta.lore(listOf(
                    TextUtility.convertToComponent("&7Select a type to start"),
                    TextUtility.convertToComponent("&7creating your item!")
                ))
                meta.persistentDataContainer.set(
                    SneakyPrototypeKit.getInstance().NAVIGATION_KEY,
                    PersistentDataType.STRING,
                    "preview"
                )
            }
        }
    }
    
    /**
     * Creates a preview item from the prototype kit's PDC data.
     * 
     * @param prototypeKit The prototype kit to create the preview from
     * @return The preview item, or null if required data is missing
     */
    private fun createPreviewItem(prototypeKit: ItemStack): ItemStack? {
        val meta = prototypeKit.itemMeta ?: return null
        
        // Get type
        meta.persistentDataContainer.get(
            SneakyPrototypeKit.getInstance().ITEM_TYPE_KEY,
            PersistentDataType.STRING
        ) ?: return null
        
        // Create preview item
        val previewItem = PrototypeKit.createItemFromPDC(meta, "&eClick to create this item!")
        if (previewItem != null) {
            // Mark as preview item
            previewItem.itemMeta = previewItem.itemMeta?.also { previewMeta ->
                previewMeta.persistentDataContainer.set(
                    SneakyPrototypeKit.getInstance().NAVIGATION_KEY,
                    PersistentDataType.STRING,
                    "preview"
                )
            }
        }
        return previewItem
    }
    
    /**
     * Creates a button for the UI.
     * 
     * @param material The material to use for the button
     * @param name The name of the button
     * @param description The description shown in the lore
     * @param action The action ID stored in the PDC
     */
    private fun createButton(material: Material, name: String, description: String, action: String): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta ?: return item
        
        meta.displayName(TextUtility.convertToComponent("&e$name"))
        meta.lore(listOf(TextUtility.convertToComponent("&7$description")))
        
        meta.persistentDataContainer.set(
            SneakyPrototypeKit.getInstance().NAVIGATION_KEY,
            PersistentDataType.STRING,
            action
        )
        
        item.itemMeta = meta
        return item
    }
} 