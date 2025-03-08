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
    private fun replaceFormatCodes(message: String): String {
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
     * Extracts all format codes from a string.
     * 
     * @param text The text to extract format codes from
     * @return List of format codes found
     */
    private fun extractFormatCodes(text: String): List<String> {
        val codes = mutableListOf<String>()
        val formatCodePatterns = listOf(
            "&[0-9a-fk-or]".toRegex(),           // & color codes
            "§[0-9a-fk-or]".toRegex(),           // § color codes
            "&#[A-Fa-f0-9]{6}".toRegex(),        // Hex color codes
            "<[^>]+>".toRegex()                  // MiniMessage tags
        )

        for (pattern in formatCodePatterns) {
            pattern.findAll(text).forEach { match ->
                codes.add(match.value)
            }
        }
        return codes
    }

    /**
     * Splits text into lines, aiming to distribute words as evenly as possible
     * while using the minimum number of lines needed.
     * 
     * @param text The text to split
     * @param maxLineLength The maximum length for each line
     * @return List of lines containing the split text, with format codes preserved
     */
    private fun splitIntoLines(text: String, maxLineLength: Int = 30): List<String> {
        val words = text.split("\\s+".toRegex())
        if (words.isEmpty()) return listOf(text)
        
        // Calculate total length and minimum lines needed
        val totalLength = words.sumOf { it.length }
        val spacesNeeded = words.size - 1 // Spaces between words
        val totalLengthWithSpaces = totalLength + spacesNeeded
        
        // Calculate minimum lines needed based on total length
        val minLines = (totalLengthWithSpaces + maxLineLength - 1) / maxLineLength
        
        // Target length for each line (including spaces)
        val targetLength = totalLengthWithSpaces / minLines
        
        val lines = mutableListOf<String>()
        var currentLine = StringBuilder()
        var currentLineWordCount = 0
        var currentLineLength = 0
        var accumulatedFormatCodes = mutableListOf<String>()
        
        for (word in words) {
            // Extract format codes from this word and add them to accumulated list
            val formatCodes = extractFormatCodes(word)
            accumulatedFormatCodes.addAll(formatCodes)
            
            val wordLength = word.length
            val spaceNeeded = if (currentLineWordCount > 0) 1 else 0
            val wouldExceedTarget = currentLineLength + spaceNeeded + wordLength > targetLength
            
            // Start a new line if:
            // 1. Adding this word would exceed target length AND we have at least one word already
            // 2. OR if adding this word would exceed max length
            // 3. UNLESS this is the last possible line (then we keep going until max length)
            if ((wouldExceedTarget && currentLineWordCount > 0 && lines.size < minLines - 1) ||
                (currentLineLength + spaceNeeded + wordLength > maxLineLength)) {
                
                if (currentLine.isNotEmpty()) {
                    lines.add(currentLine.toString())
                    currentLine = StringBuilder()
                    // Add accumulated format codes to start of new line
                    currentLine.append(accumulatedFormatCodes.joinToString(""))
                    currentLineWordCount = 0
                    currentLineLength = 0
                }
            }
            
            if (currentLineWordCount > 0) {
                currentLine.append(" ")
                currentLineLength++
            }
            
            currentLine.append(word)
            currentLineLength += wordLength
            currentLineWordCount++
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