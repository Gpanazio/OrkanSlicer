package ru.ytkab0bp.slicebeam.slic3r

import java.io.File

/** Java-friendly result for one native model import. A zero pointer always means failure. */
class OrcaModelLoadResult internal constructor(
    val pointer: Long,
    val errorMessage: String?,
    val cause: Throwable?,
) {
    fun isSuccess(): Boolean = pointer != 0L
}

/** Narrow Kotlin boundary for loading one STL/3MF plate through the existing Orca JNI entry point. */
object OrcaModelBridge {
    @JvmStatic
    fun load(path: String, plateId: Int, progress: ImportProgressCallback? = null): OrcaModelLoadResult {
        val source = File(path)
        val validationError = validateRequest(path, plateId, source.isFile, source.canRead())
        if (validationError != null) {
            return failed(Slic3rRuntimeError(validationError), validationError)
        }

        return try {
            val pointer = Native.model_read_from_file(path, baseName(source.name), plateId, progress)
            if (pointer == 0L) {
                val error = Slic3rRuntimeError("The Orca core returned no model.")
                failed(error, errorMessage(error))
            } else {
                OrcaModelLoadResult(pointer, null, null)
            }
        } catch (error: Slic3rRuntimeError) {
            failed(error, errorMessage(error))
        } catch (error: LinkageError) {
            failed(error, errorMessage(error))
        } catch (cancelled: java.util.concurrent.CancellationException) {
            throw cancelled
        } catch (error: RuntimeException) {
            failed(error, errorMessage(error))
        }
    }

    @JvmStatic
    fun validateRequest(path: String?, plateId: Int, isFile: Boolean, canRead: Boolean): String? {
        if (path.isNullOrBlank()) return "No model file path was provided."
        if (plateId < 0) return "The selected build plate is invalid."
        if (!isFile) return "The model file was not found or is not a regular file."
        if (!canRead) return "The model file cannot be read. Check storage access."
        return null
    }

    @JvmStatic
    fun baseName(fileName: String): String {
        val separator = maxOf(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'))
        val name = fileName.substring(separator + 1)
        val extension = name.lastIndexOf('.')
        return if (extension > 0) name.substring(0, extension) else name
    }

    @JvmStatic
    fun errorMessage(error: Throwable): String = when (error) {
        is LinkageError -> "The native Orca core could not load the model. Check the device architecture and bundled libraries."
        is Slic3rRuntimeError -> if (error.message == "STL_NO_CONNECTED_FACETS")
            "The STL has no connected, printable facets after repair. Repair it in a desktop slicer and export it again as STL or 3MF."
        else error.message?.takeIf { it.isNotBlank() }
            ?: "The model could not be loaded. Check whether the file is corrupt or incomplete."
        else -> error.message?.takeIf { it.isNotBlank() }
            ?: "The model could not be loaded. Check whether the file is corrupt or incomplete."
    }

    private fun failed(error: Throwable, message: String) = OrcaModelLoadResult(0L, message, error)
}
