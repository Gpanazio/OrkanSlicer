package ru.ytkab0bp.slicebeam.config

import org.json.JSONArray
import ru.ytkab0bp.slicebeam.slic3r.PrintConfigDef
import ru.ytkab0bp.slicebeam.slic3r.Slic3rConfigWrapper
import ru.ytkab0bp.slicebeam.slic3r.ConfigOptionDef
import ru.ytkab0bp.slicebeam.utils.Prefs
import ru.ytkab0bp.slicebeam.utils.MmuFilamentSync

/** A saved Orca filament preset for each T-index in the user's own palette. */
object SlotFilamentProfiles {
    private const val KEY = "filament_slot_profiles"
    var selectedSlot: Int = 0
        private set

    fun select(slot: Int) { selectedSlot = slot.coerceIn(0, 15) }

    fun titleFor(slot: Int, fallback: String?): String? {
        val raw = Prefs.getPrefs()?.getString(KEY, null) ?: return fallback
        return runCatching { JSONArray(raw).optString(slot).ifBlank { fallback } }.getOrNull() ?: fallback
    }

    fun assign(slot: Int, title: String) {
        val prefs = Prefs.getPrefs() ?: return
        val titles = runCatching { JSONArray(prefs.getString(KEY, null) ?: "[]") }.getOrElse { JSONArray() }
        while (titles.length() <= slot) titles.put("")
        titles.put(slot, title)
        prefs.edit().putString(KEY, titles.toString()).apply()
    }

    fun rename(oldTitle: String?, newTitle: String) {
        val prefs = Prefs.getPrefs() ?: return
        val titles = runCatching { JSONArray(prefs.getString(KEY, null) ?: "[]") }.getOrElse { return }
        var changed = false
        for (index in 0 until titles.length()) if (titles.optString(index) == oldTitle) {
            titles.put(index, newTitle)
            changed = true
        }
        if (changed) prefs.edit().putString(KEY, titles.toString()).apply()
    }

    fun remove(title: String?) {
        val prefs = Prefs.getPrefs() ?: return
        val titles = runCatching { JSONArray(prefs.getString(KEY, null) ?: "[]") }.getOrElse { return }
        var changed = false
        for (index in 0 until titles.length()) if (titles.optString(index) == title) {
            titles.put(index, "")
            changed = true
        }
        if (changed) prefs.edit().putString(KEY, titles.toString()).apply()
    }

    fun removeSlot(slot: Int) {
        val prefs = Prefs.getPrefs() ?: return
        val titles = runCatching { JSONArray(prefs.getString(KEY, null) ?: "[]") }.getOrElse { return }
        if (slot !in 0 until titles.length()) return
        val shifted = JSONArray()
        for (index in 0 until titles.length()) if (index != slot) shifted.put(titles.optString(index))
        prefs.edit().putString(KEY, shifted.toString()).apply()
        selectedSlot = selectedSlot.coerceAtMost((shifted.length() - 1).coerceAtLeast(0))
    }

    /** Only native per-filament vector options can differ by tool. */
    fun applyTo(target: ConfigObject, config: Slic3rConfigWrapper, currentTitle: String?, count: Int) {
        if (count <= 1) return
        val pending = ProfileSettingsRepository.pendingEntries(ConfigObject.PROFILE_LIST_FILAMENT)
        val slots = Prefs.getStoredFilamentSlots()
        val printerTitle = config.presets?.get("printer").orEmpty()
        val gates = if (Prefs.isMmuSyncedPrinter(printerTitle))
            MmuFilamentSync.getSelectedGates(printerTitle, count) else null
        val profiles = (0 until count).map { tool ->
            val slot = profileSlotForTool(tool, gates, slots.size)
            val assigned = titleFor(slot, currentTitle)
            val profile = slots.getOrNull(slot)?.type?.let { material ->
                OwnFilamentTemperatureProfiles.choose(config.filamentConfigs, assigned, currentTitle, material)
            } ?: config.findFilament(assigned) ?: config.findFilament(currentTitle)
            if (profile != null && profile.title == currentTitle && pending.isNotEmpty())
                ConfigObject(profile).apply { values.putAll(pending) } else profile
        }
        applyProfileVectors(target, profiles, PrintConfigDef.getInstance().options)
        if (slots.isNotEmpty()) {
            target.put("filament_colour", ProfileStringVectorCodec.serialize((0 until count).map { colorText(slots.getOrElse(it) { slots.last() }.color) }))
            target.put("filament_type", ProfileStringVectorCodec.serialize((0 until count).map { slots.getOrElse(it) { slots.last() }.type ?: "PLA" }))
        }
    }

    /** T numbers are logical tools; a selected ACE gate names the physical filament profile. */
    internal fun profileSlotForTool(tool: Int, gates: IntArray?, storedSlotCount: Int): Int =
        gates?.getOrNull(tool)?.takeIf { it in 0 until storedSlotCount } ?: tool

    /** An imported 3MF is temporary; the selected own slot owns heat and pressure compensation. */
    fun applyOwnFilamentSettingsTo(target: ConfigObject, config: Slic3rConfigWrapper, count: Int) {
        if (count <= 0) return
        val slots = Prefs.getStoredFilamentSlots()
        val printerTitle = config.presets?.get("printer").orEmpty()
        val gates = if (Prefs.isMmuSyncedPrinter(printerTitle))
            MmuFilamentSync.getSelectedGates(printerTitle, count) else null
        val profiles = (0 until count).map { tool ->
            val slot = profileSlotForTool(tool, gates, slots.size)
            val title = titleFor(slot, null)
            title?.let(config::findFilament)
        }
        applyProfileVectors(target, profiles, PrintConfigDef.getInstance().options, ownValuesOnly = true)
    }

    internal fun applyProfileVectors(target: ConfigObject, profiles: List<ConfigObject?>,
                                     definitions: Map<String, ConfigOptionDef>, ownValuesOnly: Boolean = false) {
        for ((key, definition) in definitions) {
            if (ownValuesOnly && !OwnFilamentTemperatureProfiles.isOwnFilamentOverrideKey(key)) continue
            if (!definition.type.list || profiles.none { it?.has(key) == true }) continue
            val fallback = TemporaryProjectSettings.splitValue(target[key].orEmpty(), definition.type)
            val values = profiles.mapIndexed { index, profile ->
                val raw = profile?.get(key)
                val source = raw?.let { TemporaryProjectSettings.splitValue(it, definition.type) }
                source?.firstOrNull() ?: fallback.getOrNull(index) ?: fallback.lastOrNull().orEmpty()
            }
            target.put(key, if (definition.type == ru.ytkab0bp.slicebeam.slic3r.ConfigOptionDef.ConfigOptionType.STRINGS)
                ProfileStringVectorCodec.serialize(values) else values.joinToString(","))
        }
    }

    private fun colorText(color: Int): String = "#%06X".format(java.util.Locale.ROOT, color and 0xFFFFFF)
}
