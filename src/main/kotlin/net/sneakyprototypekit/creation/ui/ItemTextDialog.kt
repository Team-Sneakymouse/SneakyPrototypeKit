package net.sneakyprototypekit.creation.ui

import io.papermc.paper.dialog.Dialog
import io.papermc.paper.registry.data.dialog.ActionButton
import io.papermc.paper.registry.data.dialog.DialogBase
import io.papermc.paper.registry.data.dialog.action.DialogAction
import io.papermc.paper.registry.data.dialog.body.DialogBody
import io.papermc.paper.registry.data.dialog.input.DialogInput
import io.papermc.paper.registry.data.dialog.input.TextDialogInput
import io.papermc.paper.registry.data.dialog.type.DialogType
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickCallback
import net.kyori.adventure.text.format.NamedTextColor
import net.sneakyprototypekit.SneakyPrototypeKit
import net.sneakyprototypekit.util.TextUtility
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

object ItemTextDialog {
    // Width is in GUI pixels, approximately 30 ordinary glyphs plus field padding.
    private const val INPUT_WIDTH = 188
    private const val INPUT_KEY = "text"

    fun openName(player: Player, prototypeKit: ItemStack) {
        open(player, prototypeKit, SneakyPrototypeKit.getInstance().NAME_KEY, "Name", 30, false)
    }

    fun openLore(player: Player, prototypeKit: ItemStack) {
        open(player, prototypeKit, SneakyPrototypeKit.getInstance().LORE_KEY, "Lore", 100, true)
    }

    private fun open(
        player: Player,
        prototypeKit: ItemStack,
        key: NamespacedKey,
        label: String,
        maxLength: Int,
        multiline: Boolean,
        draft: String = prototypeKit.itemMeta?.persistentDataContainer
            ?.get(key, PersistentDataType.STRING).orEmpty(),
        error: String? = null
    ) {
        val plugin = SneakyPrototypeKit.getInstance()
        val editorInventory = player.openInventory.topInventory
        val holder = editorInventory.holder as? CustomInventoryHolder ?: return
        if (holder.getData("prototype_kit") !== prototypeKit) return
        val dialogToken = Any()
        holder.setData("text_dialog", dialogToken)

        fun isCurrentEditor(): Boolean = player.isOnline &&
            player.openInventory.topInventory === editorInventory &&
            holder.getData("text_dialog") === dialogToken &&
            player.inventory.containsAtLeast(prototypeKit, 1)

        fun callback(action: (String?) -> Unit): DialogAction = DialogAction.customClick(
            { response, audience ->
                if ((audience as? Player)?.uniqueId == player.uniqueId) {
                    val text = response.getText(INPUT_KEY)
                    Bukkit.getScheduler().runTask(plugin, Runnable {
                        if (isCurrentEditor()) action(text)
                    })
                }
            },
            ClickCallback.Options.builder().uses(1).build()
        )

        val input = DialogInput.text(INPUT_KEY, Component.text(label))
            .width(INPUT_WIDTH)
            .initial(draft)
            .maxLength(maxLength)
        if (multiline) {
            input.multiline(TextDialogInput.MultilineOptions.create(null, 60))
        }

        val body = mutableListOf(DialogBody.plainMessage(Component.text(
            if (multiline) "Max $maxLength characters and ${TextUtility.MAX_PLAYER_LORE_LINES} tooltip lines. Press Enter to add line breaks. Long lines wrap automatically."
            else "Max $maxLength characters."
        )))
        if (error != null) {
            body.add(DialogBody.plainMessage(Component.text(error, NamedTextColor.RED)))
        }

        val save = ActionButton.builder(Component.text("Save")).width(90).action(callback { submitted ->
            val text = submitted?.replace("\r\n", "\n")?.replace('\r', '\n')
            val validationError = when {
                text == null -> "No text was received. Please try again."
                text.length > maxLength -> "$label cannot be longer than $maxLength characters."
                !multiline && '\n' in text -> "Names must be on one line."
                else -> TextUtility.containsFormatCodes(text, player).second
                    ?: if (multiline) TextUtility.playerLoreError(text) else null
            }
            if (validationError != null) {
                open(player, prototypeKit, key, label, maxLength, multiline, text ?: draft, validationError)
            } else {
                val meta = prototypeKit.itemMeta ?: return@callback
                if (text.isNullOrEmpty()) {
                    meta.persistentDataContainer.remove(key)
                } else {
                    meta.persistentDataContainer.set(key, PersistentDataType.STRING, text)
                }
                prototypeKit.itemMeta = meta
                MainCreationUI.open(player, prototypeKit)
            }
        }).build()
        val cancel = ActionButton.builder(Component.text("Cancel")).width(90).action(callback {
            MainCreationUI.open(player, prototypeKit)
        }).build()

        player.showDialog(Dialog.create { builder ->
            builder.empty()
                .base(DialogBase.builder(Component.text("Item $label"))
                    .canCloseWithEscape(true)
                    .body(body)
                    .inputs(listOf(input.build()))
                    .build())
                .type(DialogType.confirmation(save, cancel))
        })
    }
}
