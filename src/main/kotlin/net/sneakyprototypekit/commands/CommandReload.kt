package net.sneakyprototypekit.commands

import net.sneakyprototypekit.SneakyPrototypeKit
import net.sneakyprototypekit.util.TextUtility
import org.bukkit.command.CommandSender

/**
 * Command to reload the plugin configuration.
 */
class CommandReload : CommandBase("prototypekitreload") {
    init {
        description = "Reload the plugin configuration"
        usageMessage = "/prototypekitreload"
    }

    override fun execute(sender: CommandSender, commandLabel: String, args: Array<out String>): Boolean {
        if (!sender.hasPermission(permission ?: return false)) {
            sender.sendMessage(TextUtility.convertToComponent("&cYou don't have permission to use this command!"))
            return true
        }

        val plugin = SneakyPrototypeKit.getInstance()
        
        // Reload the configuration
        plugin.reloadConfig()
        
        sender.sendMessage(TextUtility.convertToComponent("&aConfiguration reloaded successfully!"))
        return true
    }
} 