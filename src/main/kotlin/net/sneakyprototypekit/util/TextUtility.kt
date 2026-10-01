package net.sneakyprototypekit.util

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.format.Style
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import net.sneakyprototypekit.SneakyPrototypeKit
import org.bukkit.entity.Player

/**
 * Utility class for text formatting and manipulation.
 * Handles color code conversion and text wrapping for the plugin.
 */
object TextUtility {
    const val MAX_PLAYER_LORE_LINES = 8
    private const val LORE_LINE_LENGTH = 30

    /** Preserves explicit breaks and formatting, wrapping each line at 30 visible characters. */
    fun renderPlayerLore(text: String, color: String = "&7"): List<Component> = wrapLore(text, color)

    fun playerLoreError(text: String): String? =
        if (renderPlayerLore(text).size > MAX_PLAYER_LORE_LINES)
            "Lore cannot exceed $MAX_PLAYER_LORE_LINES lines after wrapping, including blank lines."
        else null

    /** Shared wrapping for player lore and ability descriptions. */
    fun wrapLore(text: String, color: String = "&7"): List<Component> {
        val lines = mutableListOf(mutableListOf<Component>())
        fun append(component: Component, inheritedStyle: Style) {
            val style = component.style().merge(inheritedStyle, Style.Merge.Strategy.IF_ABSENT_ON_TARGET)
            if (component is TextComponent) {
                component.content().codePoints().toArray().forEach { codePoint ->
                    if (codePoint == '\n'.code) {
                        lines.add(mutableListOf())
                    } else {
                        lines.last().add(Component.text(String(Character.toChars(codePoint))).style(style))
                    }
                }
            } else {
                lines.last().add(component.children(emptyList()).style(style))
            }
            component.children().forEach { append(it, style) }
        }
        val normalized = text.replace("\r\n", "\n").replace('\r', '\n')
        append(convertToComponent("$color$normalized"), Style.empty())
        return lines.flatMap { glyphs ->
            val wrapped = mutableListOf<Component>()
            fun line(start: Int, end: Int): Component = glyphs.subList(start, end)
                .fold(Component.empty()) { result, glyph -> result.append(glyph) }

            var start = 0
            while (glyphs.size - start > LORE_LINE_LENGTH) {
                // Prefer a word boundary, but split long words to keep tooltips narrow.
                val separator = (start + 1..start + LORE_LINE_LENGTH).lastOrNull { index ->
                    (glyphs[index] as? TextComponent)?.content()?.codePoints()
                        ?.allMatch(Character::isWhitespace) == true
                }
                val end = separator ?: (start + LORE_LINE_LENGTH)
                wrapped.add(line(start, end))
                start = end + if (separator != null) 1 else 0
            }
            if (start < glyphs.size || glyphs.isEmpty()) wrapped.add(line(start, glyphs.size))
            wrapped
        }
    }

    /** Patterns for matching different types of format codes */
    private val formatCodePatterns = listOf(
        "&[0-9a-fk-or]".toRegex(),           // & color codes
        "&#[A-Fa-f0-9]{6}".toRegex(),        // Hex color codes
        "<[^>]+>".toRegex()                  // MiniMessage tags
    )

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
        return normalizeLegacyMarkers(message)
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
        return containsFormatCodes(text, player.hasPermission("${SneakyPrototypeKit.IDENTIFIER}.admin"))
    }

    internal fun containsFormatCodes(text: String, allowFormatting: Boolean): Pair<Boolean, String?> {
        if (allowFormatting) {
            return Pair(false, null)
        }

        val normalized = normalizeLegacyMarkers(text)
        for (pattern in formatCodePatterns) {
            if (pattern.containsMatchIn(normalized)) {
                return Pair(true, "Format codes are not allowed in this text! Please try again without using color codes or formatting.")
            }
        }

        return Pair(false, null)
    }

    private fun normalizeLegacyMarkers(text: String): String =
        text.replace('\u00BA', '&').replace('\u00A7', '&')
}
