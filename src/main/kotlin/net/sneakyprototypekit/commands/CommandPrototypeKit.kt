package net.sneakyprototypekit.commands

import net.sneakyprototypekit.SneakyPrototypeKit
import net.sneakyprototypekit.creation.ItemType
import net.sneakyprototypekit.creation.ItemCreationManager
import net.sneakyprototypekit.creation.PrototypeKit
import net.sneakyprototypekit.util.TextUtility
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/**
 * Command to give a prototype kit to a player.
 * The prototype kit is used to create custom items or food with abilities.
 */
class CommandPrototypeKit : CommandBase("prototypekit") {
    init {
        description = "Get a prototype kit to create custom items"
        usage = "/prototypekit [player]"
        permission = "${SneakyPrototypeKit.IDENTIFIER}.command.prototypekit"
    }

    override fun execute(sender: CommandSender, commandLabel: String, args: Array<out String>): Boolean {
        val player: Player? = if (sender is Player) sender
        else if (args.isNotEmpty()) Bukkit.getPlayer(args[0]) else null

        if (player == null) {
            sender.sendMessage(TextUtility.convertToComponent(
                if (args.isEmpty()) {
                    "&cWhen running this command from the console, the first arg must be the target player."
                } else {
                    "&c${args[0]} is not a player name. When running this command from the console, the first arg must be the target player."
                }
            ))
            return false
        }

        if (!sender.hasPermission(permission ?: return false)) {
            sender.sendMessage(TextUtility.convertToComponent("&cYou don't have permission to use this command!"))
            return true
        }

        // Give the prototype kit
        val kit = PrototypeKit.createKit()
        player.inventory.addItem(kit)
        
        // If run by another player/console, inform them
        if (sender != player) {
            sender.sendMessage(TextUtility.convertToComponent("&aGave a Prototype Kit to &e${player.name}"))
        }
        
        return true
    }

    override fun tabComplete(sender: CommandSender, alias: String, args: Array<String>): List<String> {
        return when {
            args.size == 1 -> {
                Bukkit.getOnlinePlayers()
                    .filter { !it.name.equals("CMI-Fake-Operator", ignoreCase = true) }
                    .filter { it.name.startsWith(args[0], ignoreCase = true) }
                    .map { it.name }
            }
            else -> emptyList()
        }
    }
} 