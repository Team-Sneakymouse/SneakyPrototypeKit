package net.sneakyprototypekit.commands

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.lang.reflect.Proxy
import java.util.function.BiPredicate
import java.util.function.Function
import java.util.function.Supplier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CommandSneakyPrototypeKitTest {
    private val messages = mutableListOf<String>()
    private val casts = mutableListOf<Pair<String, String>>()

    private fun sender(permitted: Boolean): CommandSender =
        Proxy.newProxyInstance(javaClass.classLoader, arrayOf(CommandSender::class.java)) { _, method, args ->
            when (method.name) {
                "hasPermission" -> permitted
                "sendMessage" -> {
                    (args?.firstOrNull() as? Component)?.let { messages += PlainTextComponentSerializer.plainText().serialize(it) }
                    null
                }
                else -> null
            }
        } as CommandSender

    private fun player(name: String): Player =
        Proxy.newProxyInstance(javaClass.classLoader, arrayOf(Player::class.java)) { _, method, _ ->
            if (method.name == "getName") name else null
        } as Player

    private val command = CommandSneakyPrototypeKit(
        Function { name -> if (name == "Alex") player("Alex") else null },
        BiPredicate { p, id -> casts += p.name to id; id == "known" },
        Supplier { listOf("known") },
    )

    private fun run(permitted: Boolean, vararg args: String) =
        command.execute(sender(permitted), "sneakyprototypekit", arrayOf(*args))

    @Test fun permissionIsCastNode() = assertEquals("sneakyprototypekit.command.cast", command.permission)

    @Test fun castsKnownAbility() {
        assertTrue(run(true, "cast", "Alex", "known"))
        assertEquals(listOf("Alex" to "known"), casts)
        assertEquals(emptyList(), messages)
    }

    @Test fun reportsUnknownAbility() {
        run(true, "cast", "Alex", "nope")
        assertEquals(listOf("Alex" to "nope"), casts)
        assertTrue(messages.single().startsWith("Unknown ability"))
    }

    @Test fun refusesWithoutPermissionOfflinePlayerAndBadArgs() {
        run(false, "cast", "Alex", "known")
        run(true, "cast", "Nobody", "known")
        run(true, "cast", "Alex")
        run(true, "give", "Alex", "known")
        assertEquals(emptyList(), casts)
        assertEquals(4, messages.size)
    }
}
