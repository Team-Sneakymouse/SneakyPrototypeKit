package net.sneakyprototypekit.util

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.format.NamedTextColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TextUtilityTest {
    private fun plainText(component: Component): String =
        (component as? TextComponent)?.content().orEmpty() + component.children().joinToString("") { plainText(it) }

    @Test
    fun `explicit breaks and blank lines survive rendering`() {
        assertEquals(
            listOf("First  line", "", "Third line", ""),
            TextUtility.renderPlayerLore("First  line\r\n\r\nThird line\n").map(::plainText)
        )
    }

    @Test
    fun `long player lines wrap at word boundaries`() {
        val text = "Players decide where this rather long sentence should break."
        assertEquals(
            listOf("Players decide where this", "rather long sentence should", "break."),
            TextUtility.renderPlayerLore(text).map(::plainText)
        )
    }

    @Test
    fun `automatic wrapping stays within explicit lines`() {
        val text = "This line has enough words to need wrapping.\nShort line\n\nAnother short line"
        assertEquals(
            listOf("This line has enough words to", "need wrapping.", "Short line", "", "Another short line"),
            TextUtility.renderPlayerLore(text).map(::plainText)
        )
    }

    @Test
    fun `long words split without breaking supplementary characters`() {
        val text = "\uD83D\uDE00".repeat(65)
        val lines = TextUtility.renderPlayerLore(text).map(::plainText)
        assertEquals(listOf(30, 30, 5), lines.map { it.codePointCount(0, it.length) })
        assertEquals(text, lines.joinToString(""))
    }

    @Test
    fun `formatting survives automatic breaks without counting toward width`() {
        val lines = TextUtility.renderPlayerLore("<red>${"x".repeat(31)}</red>tail")
        assertEquals(listOf("x".repeat(30), "xtail"), lines.map(::plainText))
        val glyphs = lines[1].children().filterIsInstance<TextComponent>()
        assertEquals(NamedTextColor.RED, glyphs.first().color())
        assertTrue(glyphs.drop(1).all { it.color() == NamedTextColor.GRAY })
    }

    @Test
    fun `formatting continues across explicit breaks and respects closing tags`() {
        val lines = TextUtility.renderPlayerLore("<red>First\nSecond</red>\nThird")
        fun glyphs(component: Component): List<TextComponent> =
            listOfNotNull((component as? TextComponent)?.takeIf { it.content().isNotEmpty() }) +
                component.children().flatMap(::glyphs)

        assertEquals(listOf("First", "Second", "Third"), lines.map(::plainText))
        assertTrue(glyphs(lines[0]).all { it.color() == NamedTextColor.RED })
        assertTrue(glyphs(lines[1]).all { it.color() == NamedTextColor.RED })
        assertTrue(glyphs(lines[2]).all { it.color() == NamedTextColor.GRAY })
    }

    @Test
    fun `ability descriptions still wrap automatically`() {
        assertTrue(TextUtility.wrapLore("This ability description has enough words to require more than one tooltip line.").size > 1)
    }
}
