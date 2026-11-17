package net.sneakyprototypekit.commands

import net.sneakyprototypekit.SneakyPrototypeKit
import net.sneakyprototypekit.ability.AbilityManager
import net.sneakyprototypekit.util.TextUtility
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.util.function.BiPredicate
import java.util.function.Function
import java.util.function.Supplier

/**
 * `/sneakyprototypekit cast <player> <ability>`: runs an ability's native actions as the player
 * (no charges or cooldown, never `command-console`), for plugins without a code dependency.
 */
class CommandSneakyPrototypeKit(
    private val findPlayer: Function<String, Player?> = Function { Bukkit.getPlayerExact(it) },
    private val cast: BiPredicate<Player, String> = BiPredicate { p, id -> AbilityManager.castById(p, id) },
    private val abilityIds: Supplier<Collection<String>> = Supplier {
        SneakyPrototypeKit.getInstance().config.getConfigurationSection("abilities")?.getKeys(false) ?: emptySet()
    },
) : CommandBase(SneakyPrototypeKit.IDENTIFIER) {
    companion object {
        const val CAST_PERMISSION = "${SneakyPrototypeKit.IDENTIFIER}.command.cast"
    }

    init {
        description = "Cast a prototype ability as a player"
        usageMessage = "/sneakyprototypekit cast <player> <ability>"
        permission = CAST_PERMISSION
    }

    override fun execute(sender: CommandSender, commandLabel: String, args: Array<out String>): Boolean {
        if (!sender.hasPermission(CAST_PERMISSION)) {
            sender.sendMessage(TextUtility.convertToComponent("&cYou don't have permission to use this command!"))
            return true
        }
        if (args.size != 3 || !args[0].equals("cast", ignoreCase = true)) {
            sender.sendMessage(TextUtility.convertToComponent("&cUsage: $usageMessage"))
            return true
        }
        val player = findPlayer.apply(args[1])
        if (player == null) {
            sender.sendMessage(TextUtility.convertToComponent("&c${args[1]} is not online."))
            return true
        }
        if (!cast.test(player, args[2])) {
            sender.sendMessage(TextUtility.convertToComponent("&cUnknown ability or no native actions: ${args[2]}"))
        }
        return true
    }

    override fun tabComplete(sender: CommandSender, alias: String, args: Array<out String>): List<String> {
        if (!sender.hasPermission(CAST_PERMISSION)) return emptyList()
        val options = when (args.size) {
            1 -> listOf("cast")
            2 -> Bukkit.getOnlinePlayers().map { it.name }
            3 -> abilityIds.get().toList()
            else -> emptyList()
        }
        return options.filter { it.startsWith(args.last(), ignoreCase = true) }
    }
}
