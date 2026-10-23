package ru.ytkab0bp.slicebeam

import android.annotation.SuppressLint
import android.app.Application
import android.content.Intent
import android.util.Log
import android.util.AtomicFile
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.instacart.truetime.time.TrueTimeImpl
import ru.ytkab0bp.eventbus.EventBus
import ru.ytkab0bp.slicebeam.boot.AppBoot
import ru.ytkab0bp.slicebeam.boot.BeamServerDataTask
import ru.ytkab0bp.slicebeam.boot.CheckUpdateJsonTask
import ru.ytkab0bp.slicebeam.boot.ClearModelCacheTask
import ru.ytkab0bp.slicebeam.boot.CloudInitTask
import ru.ytkab0bp.slicebeam.boot.EventBusTask
import ru.ytkab0bp.slicebeam.boot.LoadSlic3rConfigTask
import ru.ytkab0bp.slicebeam.boot.PrefsTask
import ru.ytkab0bp.slicebeam.boot.PrintConfigWarmupTask
import ru.ytkab0bp.slicebeam.boot.TrueTimeTask
import ru.ytkab0bp.slicebeam.boot.VibrationUtilsTask
import ru.ytkab0bp.slicebeam.cloud.CloudController
import ru.ytkab0bp.slicebeam.config.ConfigObject
import ru.ytkab0bp.slicebeam.config.TemporaryProjectSettings
import ru.ytkab0bp.slicebeam.events.TemporaryProjectSettingsChangedEvent
import ru.ytkab0bp.slicebeam.config.ProfileSettingsRepository
import ru.ytkab0bp.slicebeam.config.ProfileConfigSnapshot
import ru.ytkab0bp.slicebeam.config.ProfileSelectionTransaction
import ru.ytkab0bp.slicebeam.config.PrinterScopedProcessProfile
import ru.ytkab0bp.slicebeam.slic3r.ConfigOptionDef
import ru.ytkab0bp.slicebeam.slic3r.Model
import ru.ytkab0bp.slicebeam.slic3r.NativeEnumValueSanitizer
import ru.ytkab0bp.slicebeam.slic3r.PrintConfigDef
import ru.ytkab0bp.slicebeam.slic3r.Slic3rConfigWrapper
import ru.ytkab0bp.slicebeam.slic3r.Slic3rUtils
import ru.ytkab0bp.slicebeam.utils.FilamentSlot
import ru.ytkab0bp.slicebeam.utils.Prefs
import ru.ytkab0bp.slicebeam.utils.PrinterProfileSelectionStore
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.charset.StandardCharsets
import java.util.HashMap
import java.util.Locale

open class SliceBeam : Application() {
    companion object {
        @JvmField
        var INSTANCE: SliceBeam? = null

        @JvmField
        var EVENT_BUS: EventBus = EventBus.newBus("main")

        @JvmField
        var TRUE_TIME: TrueTimeImpl? = null

        @JvmField
        var CONFIG: Slic3rConfigWrapper? = null

        @JvmField
        var CONFIG_UID: Int = 0

        /** The selected automatic brim is restored for native slicing after bed-render sanitation. */
        @JvmField
        var AUTO_BRIM_SELECTED: Boolean = false

        /** Pending Orca calibration; BedFragment consumes and resets these values after slicing. */
        @JvmField
        var PENDING_CALIB_MODE: Int = 0

        @JvmField
        var PENDING_CALIB_START: Double = 0.0

        @JvmField
        var PENDING_CALIB_END: Double = 0.0

        @JvmField
        var PENDING_CALIB_STEP: Double = 0.0

        @JvmField
        var SERVER_DATA: BeamServerData? = null

        @JvmField
        var hasUpdateInfo: Boolean = false

        @JvmStatic
        fun clearLiveDiffs() {
            ProfileSettingsRepository.clearAllDrafts()
        }

        @JvmStatic
        fun requireInstance(): SliceBeam = requireNotNull(INSTANCE) { "Application has not started" }

        @JvmStatic
        fun requireConfig(): Slic3rConfigWrapper = requireNotNull(CONFIG) { "Printer configuration has not loaded" }

        @JvmStatic
        @Synchronized
        fun saveConfig(): Boolean {
            val file = getConfigFile()
            val atomicFile = AtomicFile(file)
            var output: FileOutputStream? = null
            try {
                output = atomicFile.startWrite()
                output.write(requireNotNull(CONFIG).serialize().toByteArray(StandardCharsets.UTF_8))
                output.fd.sync()
                atomicFile.finishWrite(output)
                output = null
                CONFIG_UID++
                getCurrentConfigFile().delete()
            } catch (error: Exception) {
                try {
                    atomicFile.failWrite(output)
                } catch (cleanupError: Exception) {
                    error.addSuppressed(cleanupError)
                    Log.w("Config", "Could not fully clean up the failed config write", cleanupError)
                }
                Log.e("Config", "Failed to save config", error)
                return false
            }
            try {
                CloudController.notifyDataChanged()
            } catch (error: Exception) {
                Log.w("Config", "Saved configuration, but cloud sync notification failed", error)
            }
            return true
        }

        @JvmStatic
        fun getModelCacheDir(): File {
            val directory = File(requireInstance().cacheDir, "model")
            if (!directory.exists()) directory.mkdirs()
            return directory
        }

        @JvmStatic
        fun getConfigFile(): File = File(requireInstance().filesDir, "slic3r.ini")

        /** Recover a pending AtomicFile write before migrations or direct profile readers access the file. */
        @JvmStatic
        @Throws(IOException::class)
        fun recoverConfigFile(): File {
            val file = getConfigFile()
            if (file.exists() || File(file.path + ".bak").exists()) {
                AtomicFile(file).openRead().use { }
            }
            return file
        }

        /**
         * Ensure the selected process and filament are compatible with the printer and nozzle.
         * If not, switch to the first compatible preset before the native slicer sees the profile.
         */
        @JvmStatic
        fun ensureCompatibleSelection() {
            val presets = CONFIG?.presets ?: return
            val previousPresets = HashMap(presets.values)
            if (synchronizeCompatibleSelection() && !saveConfig()) {
                presets.values.clear()
                presets.values.putAll(previousPresets)
                Log.w("Config", "Compatible profile selection was not persisted; restored the previous selection")
            }
        }

        /** Select profiles as one transaction so every screen refreshes the same active setup. */
        @JvmStatic
        @Synchronized
        fun applyProfileSelection(
            printer: String? = null,
            filament: String? = null,
            process: String? = null,
        ): Boolean {
            val config = CONFIG ?: return false
            val presets = config.presets ?: return false
            if (printer != null && config.findPrinter(printer) == null ||
                process != null && config.findPrint(process) == null ||
                filament != null && config.findFilament(filament) == null) return false
            if (!TemporaryProjectSettings.active && !ensurePrinterScopedProcess()) return false
            val projectProcess = if (TemporaryProjectSettings.active) process?.let(config::findPrint) else null
            val projectFilament = if (TemporaryProjectSettings.active) filament?.let(config::findFilament) else null
            if (TemporaryProjectSettings.active && printer == null && (process != null || filament != null)) {
                if (process != null && projectProcess == null || filament != null && projectFilament == null) return false
                projectProcess?.let { TemporaryProjectSettings.useProfile(ConfigObject.PROFILE_LIST_PRINT, it) }
                projectFilament?.let { TemporaryProjectSettings.useProfile(ConfigObject.PROFILE_LIST_FILAMENT, it) }
                return true
            }
            val previousPrinter = presets["printer"]
            val previousProcess = presets["print"]
            val previousFilament = presets["filament"]
            val targetPrinter = printer ?: previousPrinter
            val savedTargetSelection = if (
                targetPrinter != null && targetPrinter != previousPrinter
            ) {
                PrinterProfileSelectionStore.read(targetPrinter)
            } else {
                null
            }
            val restoredProcess = (if (TemporaryProjectSettings.active) null else process) ?: savedTargetSelection?.process
                ?.takeIf { config.findPrint(it) != null }
            val restoredFilament = (if (TemporaryProjectSettings.active) null else filament) ?: savedTargetSelection?.filament
                ?.takeIf { config.findFilament(it) != null }

            val applied = ProfileSelectionTransaction.apply(
                config = config,
                printer = printer,
                filament = restoredFilament,
                process = restoredProcess,
                synchronizeCompatible = ::synchronizeCompatibleSelection,
                persist = ::saveConfig,
            )
            if (!applied) return false
            if (!TemporaryProjectSettings.active && !ensurePrinterScopedProcess()) return false
            if (previousPrinter != presets["printer"]) TemporaryProjectSettings.physicalPrinterChanged()
            projectProcess?.let { TemporaryProjectSettings.useProfile(ConfigObject.PROFILE_LIST_PRINT, it) }
            projectFilament?.let { TemporaryProjectSettings.useProfile(ConfigObject.PROFILE_LIST_FILAMENT, it) }

            // Keep each printer's last process and filament choice. Otherwise returning to a
            // previously used printer picks the first compatible preset and silently replaces
            // the user's quality/material selection.
            var saved = true
            if (!previousPrinter.isNullOrBlank()) {
                saved = PrinterProfileSelectionStore.write(
                    previousPrinter,
                    previousProcess,
                    previousFilament,
                ) && saved
            }
            val activePrinter = presets["printer"]
            if (!activePrinter.isNullOrBlank()) {
                saved = PrinterProfileSelectionStore.write(
                    activePrinter,
                    presets["print"],
                    presets["filament"],
                ) && saved
            }
            if (!saved) {
                Log.w("Config", "Profile selection was saved, but its per-printer restore point could not be updated")
            }
            return true
        }

        /** A printer owns an editable process copy, even when its starting preset is shared. */
        @JvmStatic
        @Synchronized
        fun ensurePrinterScopedProcess(): Boolean {
            if (TemporaryProjectSettings.active) return true
            val config = CONFIG ?: return false
            val presets = config.presets ?: return false
            val printer = presets["printer"]?.takeIf(String::isNotBlank) ?: return true
            val source = config.findPrint(presets["print"]) ?: return true
            if (PrinterScopedProcessProfile.belongsTo(source, printer)) return true
            val targetTitle = PrinterScopedProcessProfile.title(source.title.orEmpty(), printer)
            val existing = config.findPrint(targetTitle)
            val copy = if (existing == null) PrinterScopedProcessProfile.copy(source, printer) else null
            if (copy != null) config.printConfigs.add(copy)
            val previous = presets["print"]
            presets.put("print", targetTitle)
            if (!saveConfig()) {
                if (previous == null) presets.remove("print") else presets.put("print", previous)
                if (copy != null) config.printConfigs.remove(copy)
                return false
            }
            return true
        }

        /** Persist pending profile edits when the activity leaves the foreground. */
        @JvmStatic
        @Synchronized
        fun savePendingProfileSettings(): Boolean {
            val pendingTypes = listOf(
                ConfigObject.PROFILE_LIST_PRINTER,
                ConfigObject.PROFILE_LIST_PRINT,
                ConfigObject.PROFILE_LIST_FILAMENT,
            ).filter { !TemporaryProjectSettings.owns(it) && ProfileSettingsRepository.pendingCountFor(it) > 0 }
            if (pendingTypes.isEmpty()) return true

            val config = CONFIG ?: return false
            val presets = config.presets ?: return false
            val activeProfiles = pendingTypes.map { profileType ->
                val presetKey = when (profileType) {
                    ConfigObject.PROFILE_LIST_PRINTER -> "printer"
                    ConfigObject.PROFILE_LIST_PRINT -> "print"
                    ConfigObject.PROFILE_LIST_FILAMENT -> "filament"
                    else -> return false
                }
                val profile = when (profileType) {
                    ConfigObject.PROFILE_LIST_PRINTER -> config.findPrinter(presets[presetKey])
                    ConfigObject.PROFILE_LIST_PRINT -> config.findPrint(presets[presetKey])
                    ConfigObject.PROFILE_LIST_FILAMENT -> config.findFilament(presets[presetKey])
                    else -> null
                } ?: run {
                    Log.e("Config", "Cannot persist pending profile edits: active profile '$presetKey' is missing")
                    return false
                }
                profileType to profile
            }

            val snapshot = ProfileConfigSnapshot(config)
            try {
                activeProfiles.forEach { (profileType, profile) ->
                    ProfileSettingsRepository.applyPendingFor(profileType, profile)
                }
                if (!saveConfig()) {
                    snapshot.restore()
                    return false
                }
                activeProfiles.forEach { (profileType, _) -> ProfileSettingsRepository.clearDraft(profileType) }
                return true
            } catch (error: Exception) {
                snapshot.restore()
                Log.e("Config", "Failed to persist pending profile edits", error)
                return false
            }
        }

        private fun synchronizeCompatibleSelection(): Boolean {
            val config = CONFIG ?: return false
            val printer = config.findPrinter(config.presets!!["printer"]) ?: return false

            val printerName = printer.title
            val model = printer["printer_model"]
            val nozzle = printer["printer_variant"] ?: Slic3rUtils.firstNozzleDiameter(printer["nozzle_diameter"])
            var changed = false
            val checker = Slic3rUtils.ConfigChecker(printer.serialize())
            try {
                val print = config.findPrint(config.presets!!["print"])
                val printIsCompatible = print != null &&
                    Slic3rUtils.isPrinterCompatible(
                        print.title,
                        print["compatible_printers"],
                        print["compatible_printers_condition"],
                        printerName,
                        model,
                        nozzle,
                        checker
                    ) && Slic3rUtils.layerHeightFitsNozzle(print["layer_height"], nozzle) &&
                    Slic3rUtils.lineWidthFitsNozzle(print["line_width"], nozzle)
                if (!printIsCompatible) {
                    for (candidate in config.printConfigs) {
                        if (Slic3rUtils.isPrinterCompatible(
                                candidate.title,
                                candidate["compatible_printers"],
                                candidate["compatible_printers_condition"],
                                printerName,
                                model,
                                nozzle,
                                checker
                            ) && Slic3rUtils.layerHeightFitsNozzle(candidate["layer_height"], nozzle) &&
                            Slic3rUtils.lineWidthFitsNozzle(candidate["line_width"], nozzle)
                        ) {
                            config.presets!!.put("print", candidate.title)
                            changed = true
                            break
                        }
                    }
                }

                val filament = config.findFilament(config.presets!!["filament"])
                val filamentIsCompatible = filament != null && Slic3rUtils.isPrinterCompatible(
                    filament.title,
                    filament["compatible_printers"],
                    filament["compatible_printers_condition"],
                    printerName,
                    model,
                    nozzle,
                    checker
                )
                if (!filamentIsCompatible) {
                    for (candidate in config.filamentConfigs) {
                        if (Slic3rUtils.isPrinterCompatible(
                                candidate.title,
                                candidate["compatible_printers"],
                                candidate["compatible_printers_condition"],
                                printerName,
                                model,
                                nozzle,
                                checker
                            )
                        ) {
                            config.presets!!.put("filament", candidate.title)
                            changed = true
                            break
                        }
                    }
                }
            } finally {
                checker.release()
            }

            return changed
        }

        @JvmStatic
        fun buildCurrentConfigObject(): ConfigObject = buildCurrentConfigObject(-1)

        /** Build a slice config whose synced ACE vectors contain only tools used by this model. */
        @JvmStatic
        fun buildCurrentConfigObject(activeFilamentCount: Int): ConfigObject {
            val current = requireConfig()
            val singleObject = ConfigObject()
            val printer = current.findPrinter(current.presets!!["printer"])
                ?: current.printerConfigs.firstOrNull()
                ?: ConfigObject.createCustomPrinterProfile()
            singleObject.values.putAll(printer.values)

            current.findPrint(current.presets!!["print"])?.let { singleObject.values.putAll(it.values) }
            current.findFilament(current.presets!!["filament"])?.let { singleObject.values.putAll(it.values) }

            // Apply unsaved profile edits last so a slice uses the currently visible setting values.
            ProfileSettingsRepository.applyPendingTo(singleObject)

            if (!TemporaryProjectSettings.active) {
                ru.ytkab0bp.slicebeam.config.SlotFilamentProfiles.applyTo(
                    singleObject, current, current.presets!!["filament"], activeFilamentCount,
                )
            }

            val definition = PrintConfigDef.getInstance()
            for ((key, option) in definition.options) {
                if (singleObject[key] == null && key !in PrintConfigDef.SKIP_DEFAULT_OPTIONS && option.defaultValue != null) {
                    singleObject.put(key, option.defaultValue)
                }
            }

            if (Prefs.isMmuSyncedPrinter(printer.title)) {
                val slots = Prefs.getFilamentSlots()
                val colors = StringBuilder()
                val types = StringBuilder()
                val paletteCount = if (activeFilamentCount > 0) {
                    minOf(activeFilamentCount, slots.size)
                } else {
                    minOf(4, slots.size)
                }
                for (index in 0 until paletteCount) {
                    if (index > 0) {
                        colors.append(';')
                        types.append(';')
                    }
                    val slot = slots[index]
                    colors.append(String.format(Locale.ROOT, "#%06X", slot.color and 0xFFFFFF))
                    types.append(slot.type)
                }
                singleObject.put("filament_colour", colors.toString())
                singleObject.put("filament_type", types.toString())
            }
            TemporaryProjectSettings.applyTo(singleObject, activeFilamentCount)
            if (TemporaryProjectSettings.active) {
                ru.ytkab0bp.slicebeam.config.SlotFilamentProfiles.applyOwnTemperaturesTo(
                    singleObject, current,
                    if (activeFilamentCount > 0) activeFilamentCount else Prefs.getFilamentSlots().size,
                )
            }
            ru.ytkab0bp.slicebeam.config.SliceTemperatureNormalizer.fillMissingBedSurfaceTemperatures(singleObject)
            if (singleObject["curr_bed_type"].isNullOrBlank()) {
                singleObject.put("curr_bed_type", ru.ytkab0bp.slicebeam.config.BedSurfaceTemperaturePolicy
                    .printerDefaultValue(singleObject["default_bed_type"]) ?: "High Temp Plate")
            }
            return singleObject
        }

        @JvmStatic
        @Throws(IOException::class)
        fun genCurrentConfig() {
            genCurrentConfig(null)
        }

        @JvmStatic
        @Throws(IOException::class)
        fun genCurrentConfig(model: Model?) {
            genCurrentConfig(model, false)
        }

        /** Adds a slicer-rendered PNG thumbnail to temporary config for printers that require it. */
        @JvmStatic
        @Throws(IOException::class)
        fun genCurrentConfig(model: Model?, includePreviewThumbnail: Boolean) {
            val outputFile = getCurrentConfigFile()
            val config = if (model == null) {
                buildCurrentConfigObject()
            } else {
                buildCurrentConfigObject(model.requiredFilamentCount)
            }
            if (includePreviewThumbnail) {
                config.put("thumbnails", "320x320/PNG")
                config.put("thumbnails_format", "PNG")
            }

            // Keep auto_brim for Model.slice(); Bed3D does not understand that native enum value.
            AUTO_BRIM_SELECTED = config["brim_type"] == "auto_brim"
            if (AUTO_BRIM_SELECTED) config.put("brim_type", "outer_only")

            sanitizeNativeConfigForEngine(config)
            FileOutputStream(outputFile).use { output ->
                output.write(config.serialize().toByteArray(StandardCharsets.UTF_8))
            }
        }

        private fun sanitizeNativeConfigForEngine(config: ConfigObject) {
            val definition = PrintConfigDef.getInstance()
            val compatibleValues = HashMap<String, String>()

            // Prefer current Orca keys when a current key and an older alias are both stored.
            for ((key, value) in config.values) {
                val engineKey = ConfigObject.migrateKey(key)
                if (engineKey == key && engineKey in definition.options) {
                    compatibleValues[engineKey] = value
                }
            }
            // Apply aliases only to the temporary slice config; never overwrite user profiles.
            for ((key, value) in config.values) {
                val engineKey = ConfigObject.migrateKey(key)
                if (engineKey !in definition.options) {
                    Log.w("SliceBeam", "Ignoring unsupported stored config option $key")
                    continue
                }
                if (engineKey != key && engineKey !in compatibleValues) {
                    val migrated = ConfigObject().apply { put(key, value) }
                    compatibleValues[engineKey] = requireNotNull(migrated[engineKey])
                }
            }
            config.values.clear()
            config.values.putAll(compatibleValues)

            for ((key, option) in definition.options) {
                if (option.type != ConfigOptionDef.ConfigOptionType.ENUM) continue
                val value = config[key]
                val normalized = NativeEnumValueSanitizer.normalize(value, option.enumValues, option.defaultValue)
                if (value != null && value != normalized) {
                    Log.w("SliceBeam", "Ignoring unsupported $key value from stored profile while preparing native config")
                    config.put(key, normalized)
                }
            }
        }

        @JvmStatic
        fun getCurrentConfigFile(): File = File(requireInstance().filesDir, "slic3r_current.ini")
    }

    @SuppressLint("ApplySharedPref")
    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en"))
        INSTANCE = this
        TemporaryProjectSettings.onChanged = { settingsChanged ->
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                ProfileSettingsRepository.notifyProjectChanged()
                CONFIG_UID++
                EVENT_BUS.fireEvent(TemporaryProjectSettingsChangedEvent(settingsChanged))
            }
        }
        AppBoot.run(
            listOf(
                EventBusTask(),
                PrefsTask(),
                VibrationUtilsTask(),
                TrueTimeTask(),
                BeamServerDataTask(),
                PrintConfigWarmupTask(),
                CheckUpdateJsonTask(),
                ClearModelCacheTask(),
                LoadSlic3rConfigTask(),
                CloudInitTask()
            )
        )
        Thread.setDefaultUncaughtExceptionHandler { _, error ->
            val trace = StringWriter().also { writer ->
                PrintWriter(writer).use(error::printStackTrace)
            }.toString()
            Prefs.getPrefs()?.edit()?.putString("crash", trace)?.commit()
            val intent = Intent(this, SafeStartActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            startActivity(intent)
            Runtime.getRuntime().exit(0)
        }
    }
}
