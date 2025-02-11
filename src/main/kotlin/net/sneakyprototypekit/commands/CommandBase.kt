package net.sneakyprototypekit.commands

import net.sneakyprototypekit.SneakyPrototypeKit
import org.bukkit.command.Command

/**
 * Base class for all plugin commands.
 * Provides common setup and permission handling.
 *
 * @property name The name of the command
 */
abstract class CommandBase(name: String) : Command(name) {

    init {
        this.permission = "${SneakyPrototypeKit.IDENTIFIER}.command.$name"
    }

} 