package ru.ytkab0bp.slicebeam

/** Plate metadata and project settings are independent: a failure in either must preserve the other. */
internal object ThreeMfImportStages {
    data class Result<T>(val plateCount: Int, val settings: T?,
                         val plateCountError: Exception?, val settingsError: Exception?)

    fun <T> read(countPlates: () -> Int, readSettings: () -> T): Result<T> {
        var plateError: Exception? = null
        val count = try { countPlates() } catch (error: Exception) { plateError = error; 1 }
        var settingsError: Exception? = null
        val settings = try { readSettings() } catch (error: Exception) { settingsError = error; null }
        return Result(count, settings, plateError, settingsError)
    }
}
