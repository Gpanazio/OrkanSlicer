package ru.ytkab0bp.slicebeam.components.bed_menu

import ru.ytkab0bp.slicebeam.utils.GCodeToolMapper
import java.io.File
import java.io.FileReader
import java.io.IOException
import java.io.RandomAccessFile
import java.util.Locale

/** File and printer checks used while preparing a sliced job for upload. */
internal object SlicePreparationTools {
    data class PressureAdvanceCommands(val filament: List<String>, val custom: List<String>)

    private val klipperPa = Regex("^\\s*SET_PRESSURE_ADVANCE\\b.*?\\bADVANCE=([0-9]+(?:\\.[0-9]+)?)", RegexOption.IGNORE_CASE)
    private val marlinPa = Regex("^\\s*M900\\s+K([0-9]+(?:\\.[0-9]+)?)", RegexOption.IGNORE_CASE)
    private val supportedSendTypes = setOf("octoprint", "moonraker", "klipper")
    private val autoLevelCommand = Regex("(?is).*\\bG29\\b.*")

    fun isAnycubicKobra3Model(model: String?): Boolean =
        model?.lowercase(Locale.ROOT)?.startsWith("anycubic kobra 3") == true

    fun isSendTarget(hostType: String?, printHost: String?): Boolean =
        (hostType ?: "octoprint") in supportedSendTypes && !printHost.isNullOrEmpty()

    fun hasAutomaticBedLeveling(machineStartGcode: String?): Boolean =
        machineStartGcode?.matches(autoLevelCommand) == true

    fun didPrinterChange(originalTitle: String?, selectedTitle: String?): Boolean =
        selectedTitle != originalTitle

    fun materialMatchesSlicedFile(sliced: String?, loaded: String?): Boolean =
        !sliced.isNullOrBlank() && !loaded.isNullOrBlank() && sliced.trim().equals(loaded.trim(), true)

    enum class PrinterActivationResult { UNCHANGED, ACTIVATED, FAILED }

    /** A changed target must be active and saved before the job is re-sliced for it. */
    fun activateChangedPrinter(
        originalTitle: String?,
        selectedTitle: String?,
        activate: () -> Boolean,
    ): PrinterActivationResult {
        if (!didPrinterChange(originalTitle, selectedTitle)) return PrinterActivationResult.UNCHANGED
        return if (activate()) PrinterActivationResult.ACTIVATED else PrinterActivationResult.FAILED
    }

    @JvmStatic
    @Throws(IOException::class)
    fun readMaxTool(gcode: File?): Int {
        if (gcode == null || !gcode.isFile || gcode.length() == 0L) {
            throw IOException("Sliced G-code is missing or empty")
        }
        var max = -1
        FileReader(gcode).buffered().use { reader ->
            while (true) {
                val line = reader.readLine() ?: break
                max = maxOf(max, GCodeToolMapper.parseToolCommand(line))
            }
        }
        return max
    }

    /** Tools actually selected in the sliced file; T1-only jobs need no visible T0 row. */
    @JvmStatic
    @Throws(IOException::class)
    fun readUsedTools(gcode: File?): IntArray {
        if (gcode == null || !gcode.isFile || gcode.length() == 0L) {
            throw IOException("Sliced G-code is missing or empty")
        }
        val used = sortedSetOf<Int>()
        FileReader(gcode).buffered().use { reader ->
            while (true) {
                val line = reader.readLine() ?: break
                GCodeToolMapper.parseToolCommand(line).takeIf { it >= 0 }?.let(used::add)
            }
        }
        return if (used.isEmpty()) intArrayOf(0) else used.toIntArray()
    }

    /** Read material names from the sliced file, rather than from a palette edited after slicing. */
    @JvmStatic
    @Throws(IOException::class)
    fun readFilamentTypes(gcode: File?): List<String> {
        if (gcode == null || !gcode.isFile || gcode.length() == 0L) {
            throw IOException("Sliced G-code is missing or empty")
        }
        RandomAccessFile(gcode, "r").use { reader ->
            val start = (reader.length() - 512 * 1024L).coerceAtLeast(0L)
            reader.seek(start)
            val bytes = ByteArray((reader.length() - start).toInt())
            reader.readFully(bytes)
            val tail = String(bytes, Charsets.UTF_8)
            val match = Regex("(?m)^;\\s*filament_type\\s*=\\s*(.*)\\r?$")
                .findAll(tail).lastOrNull() ?: return emptyList()
            return match.groupValues[1].split(';').map(String::trim)
        }
    }

    /** Executable commands only. Metadata comments do not prove that PA reaches firmware. */
    @Throws(IOException::class)
    fun readPressureAdvanceCommands(gcode: File?): PressureAdvanceCommands {
        if (gcode == null || !gcode.isFile || gcode.length() == 0L) {
            throw IOException("Sliced G-code is missing or empty")
        }
        val filament = linkedSetOf<String>()
        val custom = linkedSetOf<String>()
        FileReader(gcode).buffered().useLines { lines ->
            lines.forEach { line ->
                val command = line.substringBefore(';')
                val value = klipperPa.find(command)?.groupValues?.get(1)
                    ?: marlinPa.find(command)?.groupValues?.get(1)
                    ?: return@forEach
                val target = if (line.contains("Override pressure advance value", true)) filament else custom
                if (target.size < 16) target.add(value)
            }
        }
        return PressureAdvanceCommands(filament.toList(), custom.toList())
    }
}
