package net.sneakyprototypekit.creation.ui

import net.sneakyprototypekit.SneakyPrototypeKit
import net.sneakyprototypekit.creation.ItemType
import net.sneakyprototypekit.util.TextUtility
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import net.kyori.adventure.text.Component

/**
 * UI for selecting an ability for the item being created.
 */
class AbilitySelectionUI(
    val itemType: ItemType,
    val page: Int = 0,
    val callback: (String) -> Unit,
    val prototypeKit: ItemStack
) : InventoryHolder {
    private val inventory: Inventory = Bukkit.createInventory(this, 54, TextUtility.convertToComponent("&6Select Ability"))
    private val abilities = mutableListOf<AbilityData>()

    init {
        loadAbilities()
        updateInventory()
    }

    private fun loadAbilities() {
        val plugin = SneakyPrototypeKit.getInstance()
        val abilitiesSection = plugin.config.getConfigurationSection("abilities") ?: return

        for (abilityKey in abilitiesSection.getKeys(false)) {
            val ability = abilitiesSection.getConfigurationSection(abilityKey) ?: continue
            
            // Check if this ability is allowed for the current item type
            val allowedTypes = ability.getStringList("allowed-types")
            if (!allowedTypes.contains(itemType.name)) continue
            
            val name = ability.getString("name") ?: abilityKey
            val description = ability.getString("description") ?: ""
            val charges = ability.getInt("charges", 1)
            val material = Material.valueOf((ability.getString("icon-material") ?: "BARRIER").uppercase())
            val modelData = ability.getInt("icon-custom-model-data", -1)
            val stackSize = ability.getInt("stack-size", 1)
            
            abilities.add(AbilityData(abilityKey, name, description, charges, material, modelData, stackSize))
        }
    }

    private fun updateInventory() {
        inventory.clear()

        // Add abilities
        val startIndex = page * 45 // Leave bottom row for navigation
        abilities.drop(startIndex).take(45).forEachIndexed { index, ability ->
            inventory.setItem(index, createAbilityButton(ability))
        }

        // Add navigation buttons
        val hasNextPage = abilities.size > (page + 1) * 45
        val hasPrevPage = page > 0

        // Previous page button
        if (hasPrevPage) {
            inventory.setItem(45, createNavigationButton("prev_page", "&ePrevious Page"))
        }

        // Next page button
        if (hasNextPage) {
            inventory.setItem(53, createNavigationButton("next_page", "&eNext Page"))
        }

        // GUI elements
        inventory.setItem(52, ItemStack(Material.JIGSAW).apply {
            itemMeta = itemMeta?.also { meta ->
                meta.isHideTooltip = true
                meta.setCustomModelData(3049)
            }
        })
    }

    private fun createNavigationButton(id: String, name: String): ItemStack {
        return ItemStack(Material.ARROW).apply {
            itemMeta = itemMeta?.also { meta ->
                meta.displayName(TextUtility.convertToComponent(name))
                meta.persistentDataContainer.set(
                    SneakyPrototypeKit.getInstance().NAVIGATION_KEY,
                    PersistentDataType.STRING,
                    id
                )
            }
        }
    }

    private fun createAbilityButton(ability: AbilityData): ItemStack {
        return ItemStack(ability.material).apply {
            itemMeta = itemMeta?.also { meta ->
                meta.displayName(TextUtility.convertToComponent("&e${ability.name}"))
                
                val lore = mutableListOf<Component>()
                
                // Add description with text wrapping
                TextUtility.wrapLore("&7${ability.description}").forEach { lore.add(it) }

                lore.add(TextUtility.convertToComponent(""))

                // Show stack size info based on item type
                when (itemType) {
                    ItemType.ITEM -> {
                        lore.add(TextUtility.convertToComponent("&eCharges per item: &f${ability.charges}"))
                        lore.add(TextUtility.convertToComponent("&eStack size: &f${ability.stackSize}"))
                    }
                    ItemType.FOOD, ItemType.DRINK -> {
                        val totalAmount = (ability.stackSize * ability.charges).coerceAtMost(99)
                        lore.add(TextUtility.convertToComponent("&eTotal amount: &f$totalAmount"))
                    }
                }
                
                meta.lore(lore)
                
                if (ability.modelData > 0) {
                    meta.setCustomModelData(ability.modelData)
                }

                meta.persistentDataContainer.set(
                    SneakyPrototypeKit.getInstance().LEFT_CLICK_ABILITY_KEY,
                    PersistentDataType.STRING,
                    ability.key
                )
            }
        }
    }

    override fun getInventory(): Inventory = inventory

    companion object {
        fun open(player: Player, itemType: ItemType, page: Int = 0, prototypeKit: ItemStack, callback: (String) -> Unit) {
            val ui = AbilitySelectionUI(itemType, page, callback, prototypeKit)
            player.openInventory(ui.inventory)
        }
    }

    data class AbilityData(
        val key: String,
        val name: String,
        val description: String,
        val charges: Int,
        val material: Material,
        val modelData: Int,
        val stackSize: Int
    )
} 