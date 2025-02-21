package net.sneakyprototypekit

import net.sneakyprototypekit.commands.CommandPrototypeKit
import net.sneakyprototypekit.commands.CommandReload
import net.sneakyprototypekit.ability.AbilityListener
import net.sneakyprototypekit.creation.ui.TypeSelectionListener
import net.sneakyprototypekit.creation.ui.AbilitySelectionListener
import net.sneakyprototypekit.creation.ui.IconSelectionListener
import net.sneakyprototypekit.creation.ui.MainCreationListener
import net.sneakyprototypekit.listeners.PrototypeKitListener
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.NamespacedKey
import org.bukkit.permissions.Permission

class SneakyPrototypeKit : JavaPlugin() {
    companion object {
        const val IDENTIFIER = "sneakyprototypekit"
        private lateinit var instance: SneakyPrototypeKit

        fun getInstance(): SneakyPrototypeKit = instance

        fun log(msg: String) {
            instance.logger.info(msg)
        }
    }

    override fun onEnable() {
        instance = this
        logger.info("SneakyPrototypeKit has been enabled!")
        
        // Register listeners
        server.pluginManager.registerEvents(AbilityListener(), this)
        server.pluginManager.registerEvents(TypeSelectionListener(), this)
        server.pluginManager.registerEvents(AbilitySelectionListener(), this)
        server.pluginManager.registerEvents(IconSelectionListener(), this)
        server.pluginManager.registerEvents(PrototypeKitListener(), this)
        server.pluginManager.registerEvents(MainCreationListener(), this)

        // Register commands using Paper's command system
        server.commandMap.register(IDENTIFIER, CommandPrototypeKit())
        server.commandMap.register(IDENTIFIER, CommandReload())

        // Register permissions
        server.pluginManager.addPermission(Permission("$IDENTIFIER.*"))
        server.pluginManager.addPermission(Permission("$IDENTIFIER.admin"))
        server.pluginManager.addPermission(Permission("$IDENTIFIER.command.*"))

        // Load configuration
        saveDefaultConfig()
    }

    override fun onDisable() {
        logger.info("SneakyPrototypeKit has been disabled!")
    }

    // Keys for persistent data
    val ITEM_TYPE_KEY = NamespacedKey(this, "item_type")
    val LEFT_CLICK_ABILITY_KEY = NamespacedKey(this, "left_click_ability")
    val CONSUME_ABILITY_KEY = NamespacedKey(this, "consume_ability")
    val LEFT_CLICK_CHARGES_KEY = NamespacedKey(this, "left_click_charges")
    val NAVIGATION_KEY = NamespacedKey(this, "navigation")
    val ICON_DATA_KEY = NamespacedKey(this, "icon_data")
    val PROTOTYPE_KIT_KEY = NamespacedKey(this, "prototype_kit")
    val NAME_KEY = NamespacedKey(this, "name")
    val LORE_KEY = NamespacedKey(this, "lore")
    val CREATOR_KEY = NamespacedKey(this, "creator")
}