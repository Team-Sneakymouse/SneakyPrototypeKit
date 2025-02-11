package net.sneakyprototypekit.util

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import net.sneakyprototypekit.SneakyPrototypeKit
import org.bukkit.entity.Player

/**
 * Utility class for text formatting and manipulation.
 * Handles color code conversion and text wrapping for the plugin.
 */
object TextUtility {

    /**
     * Converts a string with legacy color codes to a Component.
     * Automatically disables italic formatting.
     * 
     * @param message The message to convert
     * @return A Component with the formatted text
     */
    fun convertToComponent(message: String): Component {
        return MiniMessage.miniMessage()
                .deserialize(replaceFormatCodes(message))
                .decoration(TextDecoration.ITALIC, false)
    }

    /**
     * Replaces legacy color codes with MiniMessage format.
     * Handles both standard color codes and hex colors.
     * 
     * @param message The message containing legacy color codes
     * @return The message with MiniMessage formatting
     */
    fun replaceFormatCodes(message: String): String {
        return message.replace("\u00BA", "&")
                .replace("\u00A7", "&")
                .replace("&1", "<dark_blue>")
                .replace("&2", "<dark_green>")
                .replace("&3", "<dark_aqua>")
                .replace("&4", "<dark_red>")
                .replace("&5", "<dark_purple>")
                .replace("&6", "<gold>")
                .replace("&7", "<gray>")
                .replace("&8", "<dark_gray>")
                .replace("&9", "<blue>")
                .replace("&0", "<black>")
                .replace("&a", "<green>")
                .replace("&b", "<aqua>")
                .replace("&c", "<red>")
                .replace("&d", "<light_purple>")
                .replace("&e", "<yellow>")
                .replace("&f", "<white>")
                .replace("&k", "<obf>")
                .replace("&l", "<b>")
                .replace("&m", "<st>")
                .replace("&n", "<u>")
                .replace("&o", "<i>")
                .replace("&r", "<reset>")
                .replace("&#([A-Fa-f0-9]{6})".toRegex(), "<color:#$1>")
    }

    /**
     * Checks if a string contains any format codes.
     * Returns true if format codes are found and the player doesn't have admin permission.
     * 
     * @param text The text to check
     * @param player The player to check admin permission for
     * @return A pair of (containsFormatCodes, errorMessage)
     */
    fun containsFormatCodes(text: String, player: Player): Pair<Boolean, String?> {
        // If player has admin permission, allow format codes
        if (player.hasPermission("${SneakyPrototypeKit.IDENTIFIER}.admin")) {
            return Pair(false, null)
        }

        val formatCodePatterns = listOf(
            "&[0-9a-fk-or]".toRegex(),           // & color codes
            "§[0-9a-fk-or]".toRegex(),           // § color codes
            "&#[A-Fa-f0-9]{6}".toRegex(),        // Hex color codes
            "<[^>]+>".toRegex()                  // MiniMessage tags
        )

        for (pattern in formatCodePatterns) {
            if (pattern.containsMatchIn(text)) {
                return Pair(true, "&cFormat codes are not allowed in this text! Please try again without using color codes or formatting.")
            }
        }

        return Pair(false, null)
    }

    /**
     * Splits text into lines of a maximum length while trying to maintain even line lengths.
     * Attempts to split at word boundaries when possible.
     * 
     * @param text The text to split
     * @param maxLineLength The maximum length for each line
     * @return List of lines containing the split text
     */
    fun splitIntoLines(text: String, maxLineLength: Int = 18): List<String> {
        val words = text.split("\\s+".toRegex())
        if (words.isEmpty()) return listOf(text)
        
        // Calculate total length and minimum lines needed
        val totalLength = words.sumOf { it.length } + (words.size - 1) // Add spaces between words
        val minLinesNeeded = (totalLength + maxLineLength - 1) / maxLineLength
        
        // Target length per line (slightly less than max to allow flexibility)
        val targetLength = totalLength / minLinesNeeded
        
        val lines = mutableListOf<String>()
        var currentLine = StringBuilder()
        var currentLineLength = 0
        
        for (word in words) {
            val wordLength = word.length
            val spaceNeeded = if (currentLineLength > 0) 1 else 0
            
            if (currentLineLength + spaceNeeded + wordLength <= targetLength ||  // Within target length
                (lines.size == minLinesNeeded - 1 && currentLine.isNotEmpty())) { // Last line, keep adding
                if (currentLine.isNotEmpty()) {
                    currentLine.append(" ")
                    currentLineLength++
                }
                currentLine.append(word)
                currentLineLength += wordLength
            } else {
                // Start new line
                if (currentLine.isNotEmpty()) {
                    lines.add(currentLine.toString())
                }
                currentLine = StringBuilder(word)
                currentLineLength = wordLength
            }
        }
        
        // Add the last line if not empty
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine.toString())
        }
        
        return lines
    }

    /**
     * Wraps item lore text to fit nicely in the item tooltip.
     * Applies color codes and formatting to each line.
     * 
     * @param text The lore text to wrap
     * @param color The color code to apply to each line (defaults to gray)
     * @return List of Components for the item lore
     */
    fun wrapLore(text: String, color: String = "&7"): List<Component> {
        return splitIntoLines(text, 30).map { line ->
            convertToComponent("$color$line")
        }
    }
} 