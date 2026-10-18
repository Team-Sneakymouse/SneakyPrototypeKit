package net.sneakyprototypekit.util

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.format.Style
import net.sneakyprototypekit.SneakyPrototypeKit
import org.bukkit.entity.Player
import com.sneakyutils.util.TextUtility as SharedTextUtility

/**
 * Prototype-kit text helpers. Shared convert/replace APIs live in SneakyUtils;
 * lore wrapping and permission-gated format checks stay here.
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

    fun convertToComponent(message: String): Component =
        SharedTextUtility.convertToComponent(message)

    /**
     * Checks if a string contains any format codes.
     * Returns true if format codes are found and the player doesn't have admin permission.
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
