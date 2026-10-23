package ru.ytkab0bp.slicebeam.fragment

import android.content.Context
import android.graphics.Color
import androidx.compose.runtime.Composable
import ru.ytkab0bp.slicebeam.R
import ru.ytkab0bp.slicebeam.SliceBeam
import ru.ytkab0bp.slicebeam.components.ColorChooserSheet
import ru.ytkab0bp.slicebeam.config.ProfilePointValueValidator
import ru.ytkab0bp.slicebeam.config.ProfileStringVectorCodec
import ru.ytkab0bp.slicebeam.config.LineWidthConversion
import ru.ytkab0bp.slicebeam.config.ProfileVectorValueValidator
import ru.ytkab0bp.slicebeam.slic3r.ConfigOptionDef
import ru.ytkab0bp.slicebeam.slic3r.Slic3rLocalization
import ru.ytkab0bp.slicebeam.config.ProfileOpenOptionResolver
import ru.ytkab0bp.slicebeam.ui.settings.ComposeProfileBooleanOption
import ru.ytkab0bp.slicebeam.ui.settings.ComposeProfileColorOption
import ru.ytkab0bp.slicebeam.ui.settings.ComposeProfileEnumOption
import ru.ytkab0bp.slicebeam.ui.settings.ComposeProfileEnumVectorOption
import ru.ytkab0bp.slicebeam.ui.settings.ComposeProfileFallbackOption
import ru.ytkab0bp.slicebeam.ui.settings.ComposeProfileNumericOption
import ru.ytkab0bp.slicebeam.ui.settings.ComposeLineWidthOption
import ru.ytkab0bp.slicebeam.ui.settings.ComposeProfileOpenOption
import ru.ytkab0bp.slicebeam.ui.settings.ComposeProfilePointOption
import ru.ytkab0bp.slicebeam.ui.settings.ComposeProfileStringOption
import ru.ytkab0bp.slicebeam.ui.settings.ComposeProfileStringVectorOption
import ru.ytkab0bp.slicebeam.ui.settings.ComposeProfileVectorOption
import ru.ytkab0bp.slicebeam.utils.Prefs
import java.util.Arrays
import java.util.function.Consumer
import java.util.function.Supplier

/** Builds Compose rows for every Orca setting type shown in profile screens. */
object ProfileOptionComposePresenter {
    @Suppress("UNUSED_PARAMETER")
    @JvmStatic
    fun supportsDirect(def: ConfigOptionDef): Boolean = true

    /** Draws every Orca option type in the parent LazyColumn without a nested ComposeView. */
    @Composable
    fun DirectContent(
        context: Context,
        def: ConfigOptionDef,
        elementIndex: Int,
        initialValue: String,
        onChanged: Consumer<String>,
        onLongClick: Runnable,
        onEditorStateChanged: (Boolean) -> Unit = {},
    ) {
        if (def.key in LineWidthConversion.keys &&
            def.type == ConfigOptionDef.ConfigOptionType.FLOAT_OR_PERCENT) {
            val printer = SliceBeam.CONFIG?.let { config ->
                config.presets?.get("printer")?.let(config::findPrinter)
            }
            val nozzle = LineWidthConversion.nozzle(printer?.get("nozzle_diameter"))
            if (nozzle != null) {
                ComposeLineWidthOption.Content(localized(def.getLabel()), initialValue, nozzle,
                    LineWidthConversion.maxPercent(def.key),
                    if (def.key == "bridge_line_width") localized(def.tooltip) else null, onChanged)
                return
            }
        }
        when (ProfileOptionDirectComposePolicy.kind(def, elementIndex, initialValue)) {
            ProfileOptionDirectComposePolicy.Kind.BOOLEAN -> ComposeProfileBooleanOption.Content(
                localized(def.getLabel()),
                initialValue == "1",
                Consumer { checked -> onChanged.accept(if (checked) "1" else "0") },
                onLongClick,
            )

            ProfileOptionDirectComposePolicy.Kind.NUMBER -> ComposeProfileNumericOption.Content(
                localized(def.getLabel()),
                initialValue,
                localized(def.sidetext),
                localized(def.tooltip),
                def.min,
                def.max,
                def.type == ConfigOptionDef.ConfigOptionType.INT,
                def.type == ConfigOptionDef.ConfigOptionType.PERCENT ||
                    def.type == ConfigOptionDef.ConfigOptionType.FLOAT_OR_PERCENT,
                def.type == ConfigOptionDef.ConfigOptionType.PERCENT,
                onChanged,
                onLongClick,
                onEditorStateChanged,
            )

            ProfileOptionDirectComposePolicy.Kind.OPEN_ENUM -> {
                val integerEnum = def.guiType == ConfigOptionDef.GUIType.I_ENUM_OPEN &&
                    def.type == ConfigOptionDef.ConfigOptionType.INT
                val choices = ProfileOpenOptionResolver.choices(
                    localizedCopy(def.enumLabels), def.enumValues, integerEnum,
                )
                ComposeProfileOpenOption.Content(
                    localized(def.getLabel()),
                    initialValue,
                    localized(def.tooltip),
                    localized(def.sidetext),
                    choices,
                    def.type == ConfigOptionDef.ConfigOptionType.INT,
                    def.type == ConfigOptionDef.ConfigOptionType.PERCENT,
                    def.min,
                    def.max,
                    onChanged,
                    onLongClick,
                    onEditorStateChanged,
                )
            }

            ProfileOptionDirectComposePolicy.Kind.COLOR -> ComposeProfileColorOption.Content(
                localized(def.getLabel()),
                initialValue,
                colorPicker(context, def, Supplier { initialValue }, onChanged),
                onLongClick,
            )

            ProfileOptionDirectComposePolicy.Kind.ENUM -> {
                val labels: Array<String>
                val values: Array<String>
                if (def.key == "host_type") {
                    labels = arrayOf(
                        context.getString(R.string.PrinterHostOctoPrint),
                        context.getString(R.string.PrinterHostMoonraker),
                    )
                    values = arrayOf("octoprint", "moonraker")
                } else {
                    val sourceLabels = def.enumLabels.orEmpty()
                    val sourceValues = def.enumValues.orEmpty()
                    val count = minOf(sourceLabels.size, sourceValues.size)
                    labels = Array(count) { localized(sourceLabels[it]) }
                    values = Arrays.copyOf(sourceValues, count)
                }
                ComposeProfileEnumOption.Content(
                    localized(def.getLabel()), initialValue, labels, values, onChanged, onLongClick,
                    onEditorStateChanged,
                )
            }

            ProfileOptionDirectComposePolicy.Kind.ENUM_VECTOR -> {
                val sourceLabels = def.enumLabels.orEmpty()
                val sourceValues = def.enumValues.orEmpty()
                val count = minOf(sourceLabels.size, sourceValues.size)
                ComposeProfileEnumVectorOption.Content(
                    localized(def.getLabel()),
                    initialValue,
                    Array(count) { localized(sourceLabels[it]) },
                    Arrays.copyOf(sourceValues, count),
                    onChanged,
                    onLongClick,
                    onEditorStateChanged,
                )
            }

            ProfileOptionDirectComposePolicy.Kind.VECTOR -> {
                val vectorKind = when (def.type) {
                    ConfigOptionDef.ConfigOptionType.INTS -> ProfileVectorValueValidator.Kind.INTS
                    ConfigOptionDef.ConfigOptionType.PERCENTS -> ProfileVectorValueValidator.Kind.PERCENTS
                    ConfigOptionDef.ConfigOptionType.FLOATS_OR_PERCENTS ->
                        ProfileVectorValueValidator.Kind.FLOATS_OR_PERCENTS
                    else -> ProfileVectorValueValidator.Kind.FLOATS
                }
                ComposeProfileVectorOption.Content(
                    localized(def.getLabel()), initialValue, localized(def.sidetext), localized(def.tooltip),
                    def.min, def.max, vectorKind, onChanged, onLongClick, def.nullable,
                    onEditorStateChanged,
                )
            }

            ProfileOptionDirectComposePolicy.Kind.POINT -> {
                val pointKind = when (def.type) {
                    ConfigOptionDef.ConfigOptionType.POINT -> ProfilePointValueValidator.Kind.POINT
                    ConfigOptionDef.ConfigOptionType.POINTS -> ProfilePointValueValidator.Kind.POINTS
                    else -> ProfilePointValueValidator.Kind.POINT3
                }
                ComposeProfilePointOption.Content(
                    localized(def.getLabel()),
                    initialValue,
                    localized(def.tooltip),
                    pointKind,
                    def.type == ConfigOptionDef.ConfigOptionType.POINTS && elementIndex == -1,
                    onChanged,
                    onLongClick,
                    onEditorStateChanged,
                )
            }

            ProfileOptionDirectComposePolicy.Kind.STRING_VECTOR -> ComposeProfileStringVectorOption.Content(
                localized(def.getLabel()),
                initialValue,
                localized(def.tooltip),
                def.multiline,
                def.height.takeIf { it > 0 } ?: 8,
                localizedCopy(def.enumLabels),
                def.enumValues,
                onChanged,
                onLongClick,
                onEditorStateChanged,
            )

            ProfileOptionDirectComposePolicy.Kind.STRING -> ComposeProfileStringOption.Content(
                localized(def.getLabel()),
                initialValue,
                localized(def.tooltip),
                def.guiType == ConfigOptionDef.GUIType.PASSWORD,
                def.multiline,
                def.height.takeIf { it > 0 } ?: 8,
                localizedCopy(def.enumLabels),
                def.enumValues,
                onChanged,
                onLongClick,
                onEditorStateChanged,
            )

            ProfileOptionDirectComposePolicy.Kind.FALLBACK -> ComposeProfileFallbackOption.Content(
                localized(def.getLabel()),
                localized(def.getFullLabel()),
                localized(def.tooltip),
                def,
                initialValue,
                fallbackChoices(context, def),
                onChanged,
                onLongClick,
                onEditorStateChanged,
            )

        }
    }

    @JvmStatic
    fun create(
        context: Context,
        def: ConfigOptionDef,
        elementIndex: Int,
        valueProvider: Supplier<String>,
        onChanged: Consumer<String>,
        onLongClick: Runnable,
    ): ProfileSettingsComposeItem {
        if (def.type == ConfigOptionDef.ConfigOptionType.BOOL ||
            def.type == ConfigOptionDef.ConfigOptionType.BOOLS
        ) {
            return ComposeProfileBooleanOption.create(
                localized(def.label),
                { valueProvider.get() == "1" },
                Consumer { checked -> onChanged.accept(if (checked) "1" else "0") },
                onLongClick,
            )
        }

        val integerOpenEnum = def.guiType == ConfigOptionDef.GUIType.I_ENUM_OPEN &&
            def.type == ConfigOptionDef.ConfigOptionType.INT
        val floatOpenEnum = def.guiType == ConfigOptionDef.GUIType.F_ENUM_OPEN &&
            (def.type == ConfigOptionDef.ConfigOptionType.FLOAT ||
                def.type == ConfigOptionDef.ConfigOptionType.PERCENT ||
                def.type == ConfigOptionDef.ConfigOptionType.FLOAT_OR_PERCENT)
        if (integerOpenEnum || floatOpenEnum) {
            val labels = localizedCopy(def.enumLabels)
            return ComposeProfileOpenOption.create(
                localized(def.getLabel()), normalizedProvider(valueProvider), localized(def.tooltip),
                localized(def.sidetext), labels, def.enumValues,
                integerOpenEnum, integerOpenEnum,
                def.type == ConfigOptionDef.ConfigOptionType.PERCENT,
                def.min, def.max, onChanged, onLongClick,
            )
        }

        if (def.guiType == ConfigOptionDef.GUIType.COLOR) {
            return ComposeProfileColorOption.create(
                localized(def.getLabel()),
                normalizedProvider(valueProvider),
                colorPicker(context, def, normalizedProvider(valueProvider), onChanged),
                onLongClick,
            )
        }

        if (def.type == ConfigOptionDef.ConfigOptionType.ENUM) {
            val labels: Array<String>
            val values: Array<String>
            if (def.key == "host_type") {
                labels = arrayOf(
                    context.getString(R.string.PrinterHostOctoPrint),
                    context.getString(R.string.PrinterHostMoonraker),
                )
                values = arrayOf("octoprint", "moonraker")
            } else {
                val sourceLabels = def.enumLabels ?: return fallback(
                    context, def, valueProvider, onChanged, onLongClick,
                )
                val sourceValues = def.enumValues ?: return fallback(
                    context, def, valueProvider, onChanged, onLongClick,
                )
                val count = minOf(sourceLabels.size, sourceValues.size)
                if (count == 0) return fallback(context, def, valueProvider, onChanged, onLongClick)
                labels = Array(count) { localized(sourceLabels[it]) }
                values = Arrays.copyOf(sourceValues, count)
            }
            return ComposeProfileEnumOption.create(
                localized(def.getLabel()), normalizedProvider(valueProvider), labels, values, onChanged, onLongClick,
            )
        }

        if (def.type == ConfigOptionDef.ConfigOptionType.ENUMS) {
            val sourceLabels = def.enumLabels ?: return fallback(context, def, valueProvider, onChanged, onLongClick)
            val sourceValues = def.enumValues ?: return fallback(context, def, valueProvider, onChanged, onLongClick)
            val count = minOf(sourceLabels.size, sourceValues.size)
            if (count == 0) return fallback(context, def, valueProvider, onChanged, onLongClick)
            val labels = Array(count) { localized(sourceLabels[it]) }
            val values = Arrays.copyOf(sourceValues, count)
            return ComposeProfileEnumVectorOption.create(
                localized(def.getLabel()), normalizedProvider(valueProvider), labels, values, onChanged, onLongClick,
            )
        }

        val vectorKind = when (def.type) {
            ConfigOptionDef.ConfigOptionType.INTS -> ProfileVectorValueValidator.Kind.INTS
            ConfigOptionDef.ConfigOptionType.PERCENTS -> ProfileVectorValueValidator.Kind.PERCENTS
            ConfigOptionDef.ConfigOptionType.FLOATS_OR_PERCENTS ->
                ProfileVectorValueValidator.Kind.FLOATS_OR_PERCENTS
            ConfigOptionDef.ConfigOptionType.FLOATS -> ProfileVectorValueValidator.Kind.FLOATS
            else -> null
        }
        if (vectorKind != null) {
            return ComposeProfileVectorOption.create(
                localized(def.getLabel()), normalizedProvider(valueProvider), localized(def.sidetext),
                localized(def.tooltip), def.min, def.max,
                vectorKind, onChanged, onLongClick, def.nullable,
            )
        }

        val pointKind = when (def.type) {
            ConfigOptionDef.ConfigOptionType.POINT -> ProfilePointValueValidator.Kind.POINT
            ConfigOptionDef.ConfigOptionType.POINTS -> ProfilePointValueValidator.Kind.POINTS
            ConfigOptionDef.ConfigOptionType.POINT3 -> ProfilePointValueValidator.Kind.POINT3
            else -> null
        }
        if (pointKind != null) {
            return ComposeProfilePointOption.create(
                localized(def.getLabel()), normalizedProvider(valueProvider), localized(def.tooltip), pointKind,
                def.type == ConfigOptionDef.ConfigOptionType.POINTS && elementIndex == -1,
                onChanged, onLongClick,
            )
        }

        if (def.type == ConfigOptionDef.ConfigOptionType.FLOAT ||
            def.type == ConfigOptionDef.ConfigOptionType.INT ||
            def.type == ConfigOptionDef.ConfigOptionType.PERCENT ||
            def.type == ConfigOptionDef.ConfigOptionType.FLOAT_OR_PERCENT
        ) {
            return ComposeProfileNumericOption.create(
                localized(def.getLabel()), normalizedProvider(valueProvider), localized(def.sidetext),
                localized(def.tooltip), def.min, def.max,
                def.type == ConfigOptionDef.ConfigOptionType.INT,
                def.type == ConfigOptionDef.ConfigOptionType.PERCENT ||
                    def.type == ConfigOptionDef.ConfigOptionType.FLOAT_OR_PERCENT,
                def.type == ConfigOptionDef.ConfigOptionType.PERCENT,
                onChanged, onLongClick,
            )
        }

        if (def.type == ConfigOptionDef.ConfigOptionType.STRINGS &&
            def.guiType != ConfigOptionDef.GUIType.ONE_STRING &&
            elementIndex == -1 && ProfileStringVectorCodec.parse(valueProvider.get().orEmpty()) != null
        ) {
            return ComposeProfileStringVectorOption.create(
                localized(def.getLabel()), normalizedProvider(valueProvider), localized(def.tooltip), def.multiline,
                def.height.takeIf { it > 0 } ?: 8,
                localizedCopy(def.enumLabels), def.enumValues, onChanged, onLongClick,
            )
        }

        if ((def.type == ConfigOptionDef.ConfigOptionType.STRING ||
                (def.type == ConfigOptionDef.ConfigOptionType.STRINGS &&
                    def.guiType == ConfigOptionDef.GUIType.ONE_STRING)) &&
            elementIndex == -1 && def.guiType != ConfigOptionDef.GUIType.COLOR
        ) {
            return ComposeProfileStringOption.create(
                localized(def.getLabel()), normalizedProvider(valueProvider), localized(def.tooltip),
                def.guiType == ConfigOptionDef.GUIType.PASSWORD,
                def.multiline, def.height.takeIf { it > 0 } ?: 8,
                localizedCopy(def.enumLabels), def.enumValues, onChanged, onLongClick,
            )
        }

        return fallback(context, def, valueProvider, onChanged, onLongClick)
    }

    private fun fallback(
        context: Context,
        def: ConfigOptionDef,
        valueProvider: Supplier<String>,
        onChanged: Consumer<String>,
        onLongClick: Runnable,
    ): ProfileSettingsComposeItem {
        val choices = fallbackChoices(context, def)
        return ComposeProfileFallbackOption.create(
            localized(def.getLabel()),
            localized(def.getFullLabel()),
            localized(def.tooltip),
            def,
            normalizedProvider(valueProvider),
            choices,
            onChanged,
            onLongClick,
        )
    }

    private fun fallbackChoices(
        context: Context,
        def: ConfigOptionDef,
    ): List<ProfileOpenOptionResolver.Choice> = if (def.type != ConfigOptionDef.ConfigOptionType.ENUM) {
            emptyList()
        } else if (def.key == "host_type") {
            listOf(
                ProfileOpenOptionResolver.Choice(context.getString(R.string.PrinterHostOctoPrint), "octoprint"),
                ProfileOpenOptionResolver.Choice(context.getString(R.string.PrinterHostMoonraker), "moonraker"),
            )
        } else {
            val labels = localizedCopy(def.enumLabels)
            val values = def.enumValues
            val count = minOf(labels?.size ?: 0, values?.size ?: 0)
            (0 until count).map { index ->
                ProfileOpenOptionResolver.Choice(labels?.get(index).orEmpty(), values?.get(index).orEmpty())
            }
        }

    private fun colorPicker(
        context: Context,
        def: ConfigOptionDef,
        valueProvider: Supplier<String>,
        onChanged: Consumer<String>,
    ): Runnable = Runnable {
        val defaultColor = try {
            (Color.parseColor(valueProvider.get().orEmpty()) and 0xFFFFFF) or 0xFF000000.toInt()
        } catch (_: Exception) {
            Prefs.getAccentColor()
        }
        ColorChooserSheet(
            context,
            defaultColor,
            ColorChooserSheet.OnColorChosen { color ->
                onChanged.accept(String.format("#%06X", color and 0xFFFFFF))
            },
        ).show()
    }

    private fun localized(value: String?): String =
        if (value.isNullOrEmpty()) "" else Slic3rLocalization.getString(value)

    private fun normalizedProvider(provider: Supplier<String>): Supplier<String> =
        Supplier { provider.get().orEmpty() }

    private fun localizedCopy(values: Array<String>?): Array<String>? =
        values?.map(::localized)?.toTypedArray()
}
