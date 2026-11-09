package net.sneakyprototypekit.util

import net.sneakyprototypekit.integration.Probe

/** Menu timing for SneakyDiagnostics (optional). The menus are static objects, so the plugin's probe is held here. */
internal object MenuTiming {
    /** Set while the plugin is enabled; null otherwise (and in tests). Server thread only. */
    var probe: Probe? = null

    /** Times [block] as one section, ended on every path so sections nest strictly. [section] must be a constant. */
    inline fun <T> timed(section: String, block: () -> T): T {
        val p = probe
        val token = p?.start() ?: 0L
        try {
            return block()
        } finally {
            if (token != 0L) p?.end(section, token)
        }
    }
}
