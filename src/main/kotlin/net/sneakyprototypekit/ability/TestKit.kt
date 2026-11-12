package net.sneakyprototypekit.ability

import net.kyori.adventure.text.Component
import net.sneakyprototypekit.SneakyPrototypeKit
import net.sneakyprototypekit.ability.actions.ActionEngine
import net.sneakyprototypekit.ability.actions.Node
import net.sneakyprototypekit.util.TextUtility
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

/**
 * Prototype Test Kit: one admin wand that holds every ability in the config.
 * Left-click casts the selected ability (no charges, no cooldown, food and drink abilities too),
 * right-click selects the next one, sneak + right-click the previous one.
 */
object TestKit : Listener {
    private val KEY by lazy { NamespacedKey(SneakyPrototypeKit.getInstance(), "test_kit_index") }

    private fun abilities(): List<String> =
        SneakyPrototypeKit.getInstance().config.getConfigurationSection("abilities")?.getKeys(false)?.toList() ?: emptyList()

    fun create(): ItemStack {
        val item = ItemStack(Material.RABBIT_FOOT)
        item.editMeta { it.persistentDataContainer.set(KEY, PersistentDataType.INTEGER, 0) }
        refresh(item, 0)
        return item
    }

    private fun refresh(item: ItemStack, index: Int) {
        val list = abilities()
        val id = list.getOrNull(index) ?: "none"
        val section = SneakyPrototypeKit.getInstance().config.getConfigurationSection("abilities.$id")
        val name = section?.getString("name") ?: id
        item.editMeta { meta ->
            meta.persistentDataContainer.set(KEY, PersistentDataType.INTEGER, index)
            meta.setCustomModelData(section?.getInt("icon-custom-model-data")?.takeIf { it > 0 } ?: 129)
            meta.displayName(TextUtility.convertToComponent("&dTest Kit &7[${index + 1}/${list.size}] &f$name"))
            meta.lore(listOf(
                TextUtility.convertToComponent("&7${section?.getString("description") ?: ""}"),
                TextUtility.convertToComponent("&8$id &7(${section?.getStringList("allowed-types")?.joinToString() ?: "?"})"),
                TextUtility.convertToComponent("&f&7 Cast  &eRight-click&7 next  &eSneak + right-click&7 previous"),
            ))
        }
    }

    private fun indexOf(item: ItemStack?): Int? =
        item?.itemMeta?.persistentDataContainer?.get(KEY, PersistentDataType.INTEGER)

    private fun cast(player: Player, item: ItemStack) {
        val list = abilities()
        val index = indexOf(item) ?: return
        val id = list.getOrNull(index) ?: return
        val section = SneakyPrototypeKit.getInstance().config.getConfigurationSection("abilities.$id") ?: return
        val actions = Node.list(section.getList("actions"))
        if (actions.isNotEmpty()) {
            ActionEngine.run(player, actions)
            return
        }
        val command = section.getString("command-console") ?: return
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("[playerName]", player.name))
    }

    @EventHandler(priority = EventPriority.LOW)
    fun onInteract(event: PlayerInteractEvent) {
        if (event.hand != EquipmentSlot.HAND) return
        val item = event.item ?: return
        val index = indexOf(item) ?: return
        event.isCancelled = true
        val player = event.player
        when (event.action) {
            Action.LEFT_CLICK_AIR, Action.LEFT_CLICK_BLOCK -> cast(player, item)
            Action.RIGHT_CLICK_AIR, Action.RIGHT_CLICK_BLOCK -> {
                val size = abilities().size.coerceAtLeast(1)
                val next = Math.floorMod(index + if (player.isSneaking) -1 else 1, size)
                refresh(item, next)
                player.playSound(player.location, Sound.UI_BUTTON_CLICK, 0.5f, 1.5f)
                player.sendActionBar(item.itemMeta?.displayName() ?: Component.empty())
            }
            else -> {}
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    fun onHit(event: EntityDamageByEntityEvent) {
        if (event.cause != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return
        val player = event.damager as? Player ?: return
        val item = player.inventory.itemInMainHand
        if (indexOf(item) == null) return
        event.isCancelled = true
        cast(player, item)
    }
}
