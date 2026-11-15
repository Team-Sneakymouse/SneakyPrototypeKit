package net.sneakyprototypekit.util

import com.google.gson.JsonParser
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import java.io.InputStreamReader

/**
 * Item-model ids for every look this plugin sets, plus the one-way bridge from the old integer custom model data.
 *
 * Nothing here writes integers. The legacy table (`legacy-item-models.json`, generated from the pack by
 * `tools/generate_legacy_item_models.py`) is only read to migrate values that already exist: old config keys,
 * the `icon_data` PDC stored on kits in players' inventories, and the SneakyItems legacy patterns of old
 * finalized items.
 */
object ItemModels {
    const val KIT = "lom:tools/mlmprototyper"
    const val GUI_MAIN = "lom:gui/prototypekit/prototypekit_main"
    const val GUI_TYPE = "lom:gui/prototypekit/prototypekit_type"
    const val GUI_ABILITY = "lom:gui/prototypekit/prototypekit_ability"
    const val GUI_ICON = "lom:gui/prototypekit/prototypekit_icon"

    /** An icon choice: carrier material plus the item model drawn on it. */
    data class Icon(val material: Material, val model: String)

    private val INTEGER = Regex("""\d+""")
    private val INTEGER_RANGE = Regex("""(\d+)-(\d+)""")

    /** carrier (lowercase material name) -> integer -> item model id */
    private val legacy: Map<String, Map<Int, String>> by lazy { loadLegacy() }

    /** carrier -> item model id -> integers that used to show it */
    private val legacyReverse: Map<String, Map<String, List<Int>>> by lazy {
        legacy.mapValues { (_, byInt) -> byInt.entries.groupBy({ it.value }, { it.key }) }
    }

    private fun loadLegacy(): Map<String, Map<Int, String>> {
        val stream = ItemModels::class.java.getResourceAsStream("/legacy-item-models.json") ?: return emptyMap()
        val root = stream.use { InputStreamReader(it, Charsets.UTF_8).use(JsonParser::parseReader) }.asJsonObject
        return root.entrySet().associate { (carrier, entries) ->
            carrier to entries.asJsonObject.entrySet().associate { (cmd, model) -> cmd.toInt() to model.asString }
        }
    }

    /** Item model that [cmd] meant on [carrier] in the old resource pack, or null when the pack never had one. */
    fun fromLegacy(carrier: Material, cmd: Int): String? = legacy[carrier.name.lowercase()]?.get(cmd)

    /** Integers that used to show [model] on [carrier] (empty for models that never had one). */
    fun legacyInts(carrier: Material, model: String): List<Int> =
        legacyReverse[carrier.name.lowercase()]?.get(model).orEmpty()

    fun key(id: String?): NamespacedKey? = id?.takeIf { it.isNotBlank() }?.let(NamespacedKey::fromString)

    fun setModel(meta: ItemMeta, id: String?) {
        key(id)?.let(meta::setItemModel)
    }

    /** Display-only GUI chrome: an invisible-tooltip jigsaw carrying [id]. */
    fun guiItem(id: String): ItemStack = ItemStack(Material.JIGSAW).apply {
        editMeta {
            it.isHideTooltip = true
            setModel(it, id)
        }
    }

    fun hasModel(stack: ItemStack?, id: String): Boolean {
        val meta = stack?.itemMeta ?: return false
        return meta.hasItemModel() && meta.itemModel == key(id)
    }

    /**
     * Reads `<prefix>item-model`, falling back to the old integer key resolved against [carrier].
     * Returns null when neither is set or the integer has no model in the pack.
     */
    fun read(section: ConfigurationSection?, itemModelKey: String, legacyKey: String, carrier: Material): String? {
        section ?: return null
        section.getString(itemModelKey)?.takeIf { it.isNotBlank() }?.let { return it }
        if (!section.contains(legacyKey)) return null
        return section.getInt(legacyKey, 0).takeIf { it > 0 }?.let { fromLegacy(carrier, it) }
    }

    /** Ability/icon model of an `abilities.<id>` section. */
    fun abilityIcon(section: ConfigurationSection?): String? {
        val carrier = Material.matchMaterial(section?.getString("icon-material") ?: "jigsaw") ?: Material.JIGSAW
        return read(section, "icon-item-model", "icon-custom-model-data", carrier)
    }

    /** Model of the `prototype-kit` section. */
    fun kitModel(section: ConfigurationSection?, carrier: Material): String? =
        read(section, "item-model", "model-data", carrier)

    /**
     * Expands one `item-icons` / `food-icons` / `drink-icons` list into item-model ids.
     * Entries are ids (`lom:icons/x`); old integer entries (`7`, `1-5`) still load through the legacy table,
     * and integers the pack never had a model for are dropped.
     */
    fun iconModels(carrier: Material, entries: List<String>): List<String> {
        val out = ArrayList<String>(entries.size)
        for (raw in entries) {
            val entry = raw.trim()
            when {
                INTEGER.matches(entry) -> fromLegacy(carrier, entry.toInt())?.let(out::add)
                INTEGER_RANGE.matches(entry) -> {
                    val (start, end) = INTEGER_RANGE.matchEntire(entry)!!.destructured
                    for (cmd in start.toInt()..end.toInt()) fromLegacy(carrier, cmd)?.let(out::add)
                }
                key(entry) != null -> out += entry
            }
        }
        return out
    }

    /** Every icon of a `*-icons` section, in config order. */
    fun icons(section: ConfigurationSection?): List<Icon> {
        section ?: return emptyList()
        val out = ArrayList<Icon>()
        for (materialKey in section.getKeys(false)) {
            val material = Material.matchMaterial(materialKey) ?: continue
            iconModels(material, section.getStringList(materialKey)).mapTo(out) { Icon(material, it) }
        }
        return out
    }

    /** `icon_data` PDC format: `MATERIAL,namespace:id`. */
    fun encode(icon: Icon): String = "${icon.material.name},${icon.model}"

    /**
     * Reads an `icon_data` PDC value. Kits stored before the item-model change hold `MATERIAL,<integer>`;
     * those resolve through the legacy table, so a half-built kit keeps its icon. Null when unreadable.
     */
    fun decode(data: String?): Icon? {
        data ?: return null
        val split = data.indexOf(',')
        if (split <= 0) return null
        val material = Material.matchMaterial(data.substring(0, split)) ?: return null
        val value = data.substring(split + 1).trim()
        val model = if (INTEGER.matches(value)) fromLegacy(material, value.toInt()) else value.takeIf { key(it) != null }
        return model?.let { Icon(material, it) }
    }
}
