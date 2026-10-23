package ru.ytkab0bp.slicebeam
import android.app.Activity
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Process
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.util.SparseArray
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.annotation.NonNull
import androidx.annotation.Nullable
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.ArrayList
import java.util.HashMap
import java.util.Locale
import java.util.Objects
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import ru.ytkab0bp.sapil.APICallback
import ru.ytkab0bp.slicebeam.cloud.CloudAPI
import ru.ytkab0bp.slicebeam.cloud.CloudController
import ru.ytkab0bp.slicebeam.components.BeamAlertDialogBuilder
import ru.ytkab0bp.slicebeam.components.ChangeLogBottomSheet
import ru.ytkab0bp.slicebeam.components.UnfoldMenu
import ru.ytkab0bp.slicebeam.config.ConfigObject
import ru.ytkab0bp.slicebeam.config.ThreeMfProjectSettings
import ru.ytkab0bp.slicebeam.config.ThreeMfProjectSettingsReader
import ru.ytkab0bp.slicebeam.config.TemporaryProjectSettings
import ru.ytkab0bp.slicebeam.config.ProfileConfigImportValidator
import ru.ytkab0bp.slicebeam.config.ProfileConfigLoadWarning
import ru.ytkab0bp.slicebeam.config.ProfileConfigSnapshot
import ru.ytkab0bp.slicebeam.events.NeedDismissAIGeneratorMenu
import ru.ytkab0bp.slicebeam.events.NeedDismissSnackbarEvent
import ru.ytkab0bp.slicebeam.events.NeedSnackbarEvent
import ru.ytkab0bp.slicebeam.events.ObjectsListChangedEvent
import ru.ytkab0bp.slicebeam.fragment.BedFragment
import ru.ytkab0bp.slicebeam.navigation.Fragment
import ru.ytkab0bp.slicebeam.navigation.MobileNavigationDelegate
import ru.ytkab0bp.slicebeam.navigation.NavigationDelegate
import ru.ytkab0bp.slicebeam.slic3r.Model
import ru.ytkab0bp.slicebeam.slic3r.Native
import ru.ytkab0bp.slicebeam.slic3r.PrintConfigDef
import ru.ytkab0bp.slicebeam.slic3r.Slic3rConfigWrapper
import ru.ytkab0bp.slicebeam.slic3r.Slic3rRuntimeError
import ru.ytkab0bp.slicebeam.theme.ThemesRepo
import ru.ytkab0bp.slicebeam.utils.IOUtils
import ru.ytkab0bp.slicebeam.utils.FilamentSlot
import ru.ytkab0bp.slicebeam.utils.Prefs
import ru.ytkab0bp.slicebeam.utils.ViewUtils
import ru.ytkab0bp.slicebeam.view.SnackbarsLayout
class MainActivity:AppCompatActivity() {
private var id:Int = 0
@get:NonNull
val navigationDelegate:NavigationDelegate
get() = delegate ?: throw IllegalStateException("Navigation is not initialized")
private var delegate: NavigationDelegate? = null
private var unfoldMenu: UnfoldMenu? = null
private var exitConfirmationDialog: AlertDialog? = null
private val activityResultLaunchers = HashMap<Int, ActivityResultLauncher<Intent>>()
private fun interface BundleProfileFinder {
fun find(name:String):ConfigObject?
}
override fun onCreate(savedInstanceState:Bundle?) {
super.onCreate(savedInstanceState)
registerActivityResultLaunchers()
if (Prefs.getPrefs()!!.contains("crash"))
{
startActivity(Intent(this, SafeStartActivity::class.java))
finish()
return
}
if (SliceBeam.CONFIG == null)
{
Prefs.setLastCommit()
startActivity(Intent(this, SetupActivity::class.java))
finish()
return
}
if (activeInstance == null)
{
activeInstance = this
}
else
{
val i = Intent(this, MainActivity::class.java)
i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
if (getIntent() != null)
{
i.setAction(getIntent().getAction())
i.putExtras(getIntent())
i.setDataAndType(getIntent().getData(), getIntent().getType())
}
startActivity(i)
finish()
return
}
id = if (savedInstanceState == null) lastId++ else savedInstanceState.getInt("id")
if (delegate == null)
{
val saved = liveDelegate.get(id)
liveDelegate.remove(id)
if (saved != null && isCompatible(saved))
{
delegate = saved
}
else
{
delegate = onCreateDelegate()
}
}
navigationDelegate.setContext(this)
navigationDelegate.onCreate()
val v = navigationDelegate.onCreateView(this)
if (navigationDelegate.getContainerView() == null || navigationDelegate.getContainerView()!!.getParent() == null)
{
throw IllegalArgumentException("Delegate hasn't created container view!")
}
WindowCompat.setDecorFitsSystemWindows(getWindow(), false)
ViewCompat.setOnApplyWindowInsetsListener(v, { v2, insets->
val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
v2.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
insets })
setContentView(v)
onBackPressedDispatcher.addCallback(this, object:OnBackPressedCallback(true) {
override fun handleOnBackPressed() {
if (!handleAppBack()) finishAfterTransition()
}
})
if (getIntent() != null && getIntent().getAction() != null && getIntent().getAction().equals(Intent.ACTION_VIEW))
{
getIntent().getData()?.let { loadFile(it) }
setIntent(null)
}
val decorView = getWindow().getDecorView()
decorView.setBackgroundColor(ThemesRepo.getColor(android.R.attr.windowBackground))
if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM)
{
configureLegacySystemBarColors()
}
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
{
getWindow().getAttributes().layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
}
applyOrientationSystemBars(getResources().getConfiguration().orientation)
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
&& Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM)
{
disableLegacySystemBarContrast()
}
applySystemBarAppearance()
if (!Objects.equals(Prefs.getLastCommit(), BuildConfig.COMMIT) && SliceBeam.hasUpdateInfo)
{
Prefs.setLastCommit()
BeamServerData.load()
ChangeLogBottomSheet(this).show()
}
}
protected override fun onNewIntent(@NonNull intent:Intent) {
super.onNewIntent(intent)
intent.getData()?.let { loadFile(it) }
setIntent(null)
}
private fun dispatchActivityResult(requestCode:Int, resultCode:Int, data:Intent?) {
if (ru.ytkab0bp.slicebeam.components.KromacutEditorMenu.handleActivityResult(requestCode, resultCode, data))
{
return
}
if ((requestCode == REQUEST_CODE_CONVERTER_ROOT_FIRST_LAUNCH
|| requestCode == REQUEST_CODE_CONVERTER_ROOT_SETTINGS
|| requestCode == REQUEST_CODE_CONVERTER_ROOT_SAVE))
{
if (resultCode == Activity.RESULT_OK && data != null && data.getData() != null)
{
try
{
ru.ytkab0bp.slicebeam.utils.ConverterStorage.persistSelectedRoot(this, data)
val current = navigationDelegate.getCurrentFragment()
if (current is ru.ytkab0bp.slicebeam.fragment.SettingsFragment)
{
(current as ru.ytkab0bp.slicebeam.fragment.SettingsFragment).refreshConverterFolderSetting()
}
if (requestCode == REQUEST_CODE_CONVERTER_ROOT_SAVE)
{
ru.ytkab0bp.slicebeam.components.ImageToStlConverter.finishSaveToRoot(this)
}
}
catch (error:SecurityException) {
android.widget.Toast.makeText(this, R.string.ConverterFolderPermissionFailed, android.widget.Toast.LENGTH_LONG).show()
if (requestCode == REQUEST_CODE_CONVERTER_ROOT_SAVE)
    ru.ytkab0bp.slicebeam.components.ImageToStlConverter.onConverterRootCancelled(this)
}
}
else if (requestCode == REQUEST_CODE_CONVERTER_ROOT_SAVE)
{
ru.ytkab0bp.slicebeam.components.ImageToStlConverter.onConverterRootCancelled(this)
}
return
}
if (requestCode == REQUEST_CODE_IMAGE_TO_STL && resultCode != Activity.RESULT_OK)
{
ru.ytkab0bp.slicebeam.components.ImageToStlConverter.onImagePickerCancelled()
}
if (requestCode == REQUEST_CODE_SAVE_CONVERTED_FILE && resultCode != Activity.RESULT_OK)
{
ru.ytkab0bp.slicebeam.components.ImageToStlConverter.finishSave(this, null)
return
}
if (resultCode == Activity.RESULT_OK)
{
if (requestCode == REQUEST_CODE_IMAGE_TO_STL)
{
val current = navigationDelegate.getCurrentFragment()
if (current is BedFragment && data != null)
{
val selectedImages = ArrayList<Uri>()
if (data.getClipData() != null)
{
for (i in 0 until data.getClipData()!!.getItemCount())
{
val uri = data.getClipData()!!.getItemAt(i).getUri()
if (uri != null) selectedImages.add(uri)
}
}
else if (data.getData() != null)
{
selectedImages.add(data.getData()!!)
}
if (!selectedImages.isEmpty())
{
ru.ytkab0bp.slicebeam.components.ImageToStlConverter.handlePickedImages(
this, selectedImages, current as BedFragment)
}
}
}
else if (requestCode == REQUEST_CODE_SAVE_CONVERTED_FILE)
{
ru.ytkab0bp.slicebeam.components.ImageToStlConverter.finishSave(this, if (data == null) null else data.getData())
}
else if (requestCode == MainActivity.REQUEST_CODE_EXPORT_3MF)
{
val fragment = navigationDelegate.getCurrentFragment()
if (fragment is BedFragment)
{
try
{
val out = getContentResolver().openOutputStream(data?.getData() ?: return)
val model = (fragment as BedFragment).glView!!.renderer.model
val tempFile = File.createTempFile("temp_project", ".3mf")
SliceBeam.genCurrentConfig()
val cfg = SliceBeam.getCurrentConfigFile()
model!!.export3mf(cfg.getAbsolutePath(), tempFile.getAbsolutePath())
val `in` = FileInputStream(tempFile)
val buffer = ByteArray(10240)
while (true)
{
val c = `in`.read(buffer)
if (c == -1) break
out!!.write(buffer, 0, c)
}
`in`.close()
out!!.close()
tempFile.delete()
SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(R.string.MenuFileExport3mfSuccess))
}
catch (e:IOException) {
throw RuntimeException(e)
}
catch (e:Slic3rRuntimeError) {
throw RuntimeException(e)
}
}
}
else if (requestCode == MainActivity.REQUEST_CODE_EXPORT_GCODE)
{
try
{
val out = getContentResolver().openOutputStream(data?.getData() ?: return)
val `in` = FileInputStream(BedFragment.tempGCodePath)
val buffer = ByteArray(10240)
while (true)
{
val c = `in`.read(buffer)
if (c == -1) break
out!!.write(buffer, 0, c)
}
`in`.close()
out!!.close()
}
catch (e:IOException) {
throw RuntimeException(e)
}
}
else if (requestCode == MainActivity.REQUEST_CODE_OPEN_FILE)
{
val selectedFiles = ArrayList<Uri>()
if (data != null && data.getClipData() != null)
{
for (i in 0 until data.getClipData()!!.getItemCount())
{
val uri = data.getClipData()!!.getItemAt(i).getUri()
if (uri != null) selectedFiles.add(uri)
}
}
else if (data != null && data.getData() != null)
{
selectedFiles.add(data.getData()!!)
}
if (selectedFiles.size > 1)
{
val stlFiles = ArrayList<Uri>()
for (uri in selectedFiles)
{
val name = IOUtils.getDisplayName(uri)
if (name != null && name.lowercase(Locale.ROOT).endsWith(".stl")) stlFiles.add(uri)
}
if (stlFiles.size != selectedFiles.size)
{
Toast.makeText(this, R.string.MenuFileOpenMultipleStlOnly, Toast.LENGTH_LONG).show()
}
if (stlFiles.isEmpty())
{
BeamAlertDialogBuilder(this).setTitle(R.string.MenuFileOpenFileFailed)
.setMessage(R.string.MenuFileOpenMultipleStlOnly)
.setPositiveButton(android.R.string.ok, null).show()
}
else
{
loadMultipleStlFiles(stlFiles)
}
}
else if (!selectedFiles.isEmpty())
{
loadFile(selectedFiles.get(0))
}
}
else if (requestCode == MainActivity.REQUEST_CODE_EXPORT_PROFILES)
{
try
{
val w = Slic3rConfigWrapper()
w.printConfigs.addAll(EXPORTING_PRINTS.orEmpty())
w.filamentConfigs.addAll(EXPORTING_FILAMENTS.orEmpty())
w.printerConfigs.addAll(EXPORTING_PRINTERS.orEmpty())
EXPORTING_PRINTS = null
EXPORTING_FILAMENTS = null
EXPORTING_PRINTERS = null
w.presets = ConfigObject()
if (w.findPrint(SliceBeam.CONFIG!!.presets!!.get("print")) != null)
{
w.presets!!.put("print", SliceBeam.CONFIG!!.presets!!.get("print"))
}
if (w.findFilament(SliceBeam.CONFIG!!.presets!!.get("filament")) != null)
{
w.presets!!.put("filament", SliceBeam.CONFIG!!.presets!!.get("filament"))
}
if (w.findPrinter(SliceBeam.CONFIG!!.presets!!.get("printer")) != null)
{
w.presets!!.put("printer", SliceBeam.CONFIG!!.presets!!.get("printer"))
}
val out = getContentResolver().openOutputStream(data?.getData() ?: return)
out!!.write(w.serialize().toByteArray(StandardCharsets.UTF_8))
out!!.close()
SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(R.string.MenuFileExportProfilesSuccess))
}
catch (e:IOException) {
throw RuntimeException(e)
}
}
else if (requestCode == MainActivity.REQUEST_CODE_IMPORT_PROFILES)
{
val uri = data?.getData() ?: return
val fileName = IOUtils.getDisplayName(uri)
if (fileName == null)
{
BeamAlertDialogBuilder(this)
.setTitle(R.string.MenuFileImportProfilesFailed)
.setMessage(R.string.MenuFileOpenFileFailedNullName)
.setPositiveButton(android.R.string.ok, null)
.show()
return
}
if (isOrcaBundleFile(fileName))
{
loadConvertedProfile(uri)
return
}
if (!fileName.endsWith(".ini"))
{
BeamAlertDialogBuilder(this)
.setTitle(R.string.MenuFileImportProfilesFailed)
.setMessage(R.string.MenuFileImportProfilesFailedNotIni)
.setPositiveButton(android.R.string.ok, null)
.show()
return
}
try
{
loadIniForImport(getContentResolver().openInputStream(uri) ?: return)
}
catch (e:FileNotFoundException) {
BeamAlertDialogBuilder(this)
.setTitle(R.string.MenuFileImportProfilesFailed)
.setMessage(e.toString())
.setPositiveButton(android.R.string.ok, null)
.show()
}
}
else if (requestCode == REQUEST_CODE_AI_GENERATOR_TAKE_PHOTO)
{
SliceBeam.EVENT_BUS.fireEvent(NeedDismissAIGeneratorMenu())
val bm = BitmapFactory.decodeFile(aiTempFile!!.getAbsolutePath())
generateAiModel(bm)
aiTempFile!!.delete()
aiTempFile = null
}
else if (requestCode == REQUEST_CODE_AI_GENERATOR_CHOOSE_PHOTO)
{
SliceBeam.EVENT_BUS.fireEvent(NeedDismissAIGeneratorMenu())
try
{
val `in` = getContentResolver().openInputStream(data?.getData() ?: return)
val bm = BitmapFactory.decodeStream(`in`)
generateAiModel(bm)
}
catch (e:Exception) {
Log.e("ai_generator", "Failed to write to downloads", e)
}
}
}
}
private fun registerActivityResultLaunchers() {
    ACTIVITY_RESULT_REQUEST_CODES.forEach { requestCode ->
        activityResultLaunchers[requestCode] = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            dispatchActivityResult(requestCode, result.resultCode, result.data)
        }
    }
}

internal fun launchRegisteredActivityResult(intent: Intent, requestCode: Int) {
    val launcher = activityResultLaunchers[requestCode]
        ?: throw IllegalArgumentException("No activity-result launcher registered for request code $requestCode")
    launcher.launch(intent)
}

private fun loadConvertedProfile(uri:Uri) {
val tag = UUID.randomUUID().toString()
SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(SnackbarsLayout.Type.LOADING, R.string.OrcaConversionPleaseWait).tag(tag))
IOUtils.IO_POOL.submit({ var copiedArchive:File? = null
val extractDir = File(SliceBeam.getModelCacheDir(), "orca_conv_" + UUID.randomUUID())
try
{
copiedArchive = File.createTempFile("orca_conv_", ".zip", SliceBeam.getModelCacheDir())
getContentResolver().openInputStream(uri).use({ `in`-> FileOutputStream(copiedArchive!!).use({ fos->
if (`in` == null)
{
throw FileNotFoundException(java.lang.String.valueOf(uri))
}
val buffer = ByteArray(10240)
while (true)
{
val c = `in`.read(buffer)
if (c == -1) break
fos.write(buffer, 0, c)
}
}) })
if (!extractDir.mkdirs() && !extractDir.isDirectory())
{
throw IOException("Failed to create temporary extraction directory")
}
val manifest = Native.orca_bundle_read(copiedArchive!!.getAbsolutePath(), extractDir.getAbsolutePath())
val root = JSONObject(manifest)
val bundle = JSONObject(root.getString("bundle_structure_json"))
if (!bundle.optString("bundle_type", "").endsWith("config bundle"))
{
SliceBeam.EVENT_BUS.fireEvent(NeedDismissSnackbarEvent(tag))
ViewUtils.postOnMainThread({ BeamAlertDialogBuilder(this)
.setTitle(R.string.MenuFileImportProfilesFailed)
.setMessage(R.string.OrcaConversionNotAConfigBundle)
.setPositiveButton(android.R.string.ok, null)
.show() })
return@submit
}
importOrcaBundle(root)
SliceBeam.EVENT_BUS.fireEvent(NeedDismissSnackbarEvent(tag))
}
catch (ep:IOUtils.MissingProfileException) {
SliceBeam.EVENT_BUS.fireEvent(NeedDismissSnackbarEvent(tag))
ViewUtils.postOnMainThread({ BeamAlertDialogBuilder(this)
.setTitle(R.string.MenuFileImportProfilesFailed)
.setMessage(getString(R.string.MenuFileImportProfilesFailedBaseProfileNotFound, ep.profile))
.setPositiveButton(android.R.string.ok, null)
.show() })
}
catch (e:IOException) {
SliceBeam.EVENT_BUS.fireEvent(NeedDismissSnackbarEvent(tag))
ViewUtils.postOnMainThread({ BeamAlertDialogBuilder(this)
.setTitle(R.string.MenuFileImportProfilesFailed)
.setMessage(e.toString())
.setPositiveButton(android.R.string.ok, null)
.show() })
}
catch (e:Exception) {
SliceBeam.EVENT_BUS.fireEvent(NeedDismissSnackbarEvent(tag))
ViewUtils.postOnMainThread({ BeamAlertDialogBuilder(this)
.setTitle(R.string.MenuFileImportProfilesFailed)
.setMessage(e.toString())
.setPositiveButton(android.R.string.ok, null)
.show() })
}
finally
{
copiedArchive?.delete()
deleteRecursively(extractDir)
} })
}
private fun isOrcaBundleFile(fileName:String):Boolean {
val lower = fileName.lowercase(Locale.ROOT)
return lower.endsWith(".orca_printer") || lower.endsWith(".orca_filament") || lower.endsWith(".zip")
}
private fun is3mfFile(fileName:String):Boolean {
return fileName.lowercase(Locale.ROOT).endsWith(".3mf")
}
private fun isGcodeFile(fileName:String):Boolean {
val lower = fileName.lowercase(Locale.ROOT)
return lower.endsWith(".gcode") || lower.endsWith(".g") || lower.endsWith(".gco") || lower.endsWith(".bgcode")
}
private fun isSupportedModelFile(fileName:String):Boolean {
val lower = fileName.lowercase(Locale.ROOT)
// Keep this list in sync with libslic3r::Model::read_from_file() on Android.
 return (lower.endsWith(".stl") || lower.endsWith(".oltp") || lower.endsWith(".obj") ||
lower.endsWith(".3mf") || lower.endsWith(".amf") || lower.endsWith(".svg") ||
lower.endsWith(".drc") || isGcodeFile(lower))
}
private fun getSafeCachedModelFile(fileName:String):File {
var safe = Regex("[\\\\/:*?\"<>|]").replace(fileName, "_")
if (safe.trim().isEmpty())
{
safe = "model"
}
val f = File(SliceBeam.getModelCacheDir(), safe)
if (!f.exists()) return f
val dot = safe.lastIndexOf('.')
val base = if (dot > 0) safe.substring(0, dot) else safe
val ext = if (dot > 0) safe.substring(dot) else ""
return File(SliceBeam.getModelCacheDir(), base + "_" + UUID.randomUUID() + ext)
}
private class Project3mfImportResult {
internal var importedProfiles:Int = 0
internal var plateCount:Int = 1
internal var settings: ThreeMfProjectSettings? = null
internal var settingsError:Boolean = false
internal var plateCountError:Boolean = false
internal var missingProfileIds:List<String> = emptyList()
}
@Throws(IOException::class)
private fun readZipEntryText(zip:ZipFile, entry:ZipEntry):String {
if (entry.size > 8L * 1024 * 1024) throw IOException("3MF plate metadata exceeds the size limit")
zip.getInputStream(entry).use({ `in`-> ByteArrayOutputStream().use({ out->
val buffer = ByteArray(10240)
while (true)
{
val c = `in`.read(buffer)
if (c == -1) break
if (out.size().toLong() + c > 8L * 1024 * 1024) throw IOException("3MF plate metadata exceeds the size limit")
out.write(buffer, 0, c)
}
return out.toString("UTF-8") }) })
}
private fun embeddedPresetFallbackName(path:String):String {
var name = path
val slash = name.lastIndexOf('/')
if (slash >= 0) name = name.substring(slash + 1)
if (name.endsWith(".config")) name = name.substring(0, name.length - ".config".length)
return name
}
private fun jsonProfileName(obj:JSONObject, fallback:String):String {
val resolvedName = sequenceOf("name", "print_settings_id", "filament_settings_id", "printer_settings_id")
    .map { obj.optString(it) }
    .firstOrNull { it.isNotBlank() }
    ?: fallback
return if (resolvedName.length > 3 && resolvedName[0] == '[' && resolvedName[1] == '"' &&
    resolvedName[resolvedName.length - 2] == '"' && resolvedName.last() == ']') {
    resolvedName.substring(2, resolvedName.length - 2)
} else {
    resolvedName
}
}
@Throws(Exception::class)
private fun embeddedJsonToConfig(obj:JSONObject, type:String, supportedKeys:List<String>, inBundle:List<String>, fallbackName:String):ConfigObject {
val cfg = IOUtils.configJsonToIni(obj, type, supportedKeys, inBundle)
val title = cfg.title
if (title == null || title.trim().isEmpty())
{
cfg.setTitle(jsonProfileName(obj, fallbackName))
}
return cfg
}
private fun hasConfigTitle(configs:List<ConfigObject>, title:String):Boolean {
return configs.any { it.title == title }
}
@Throws(Exception::class)
private fun projectSettingsToConfig(projectSettings:JSONObject, type:String, supportedKeys:List<String>, idKey:String, fallbackName:String):ConfigObject {
val selectedName = firstProjectValue(projectSettings, idKey)?.takeIf { it.trim().isNotEmpty() } ?: fallbackName
val profile = JSONObject(projectSettings.toString())
profile.remove("print_settings_id")
profile.remove("filament_settings_id")
profile.remove("printer_settings_id")
profile.put(idKey, selectedName)
val inBundle = ArrayList<String>()
inBundle.add(selectedName)
return embeddedJsonToConfig(profile, type, supportedKeys, inBundle, selectedName)
}
private fun addProjectSettingsFallbackProfiles(wrapper:Slic3rConfigWrapper, projectSettings:JSONObject?) {
if (projectSettings == null) return
try
{
val printName = firstProjectValue(projectSettings, "print_settings_id")
if (!printName.isNullOrEmpty() && !hasConfigTitle(wrapper.printConfigs, printName))
{
wrapper.printConfigs.add(projectSettingsToConfig(projectSettings, "process", Slic3rConfigWrapper.PRINT_CONFIG_KEYS, "print_settings_id", printName))
}
}
catch (e:Exception) {
Log.w("MainActivity", "Skipping project_settings print profile fallback", e)
}
try
{
val filamentName = firstProjectValue(projectSettings, "filament_settings_id")
if (!filamentName.isNullOrEmpty() && !hasConfigTitle(wrapper.filamentConfigs, filamentName))
{
wrapper.filamentConfigs.add(projectSettingsToConfig(projectSettings, "filament", Slic3rConfigWrapper.FILAMENT_CONFIG_KEYS, "filament_settings_id", filamentName))
}
}
catch (e:Exception) {
Log.w("MainActivity", "Skipping project_settings filament profile fallback", e)
}
try
{
val printerName = firstProjectValue(projectSettings, "printer_settings_id")
if (!printerName.isNullOrEmpty() && !hasConfigTitle(wrapper.printerConfigs, printerName))
{
wrapper.printerConfigs.add(projectSettingsToConfig(projectSettings, "machine", Slic3rConfigWrapper.PRINTER_CONFIG_KEYS, "printer_settings_id", printerName))
}
}
catch (e:Exception) {
Log.w("MainActivity", "Skipping project_settings printer profile fallback", e)
}
}
private fun firstProjectValue(project:JSONObject?, key:String):String? {
if (project == null || !project.has(key)) return null
try
{
val value = project.get(key)
if (value is JSONArray)
{
val arr = value as JSONArray
return if (arr.length() > 0) arr.optString(0, null) else null
}
val s = java.lang.String.valueOf(value)
if (s.startsWith("[") && s.endsWith("]"))
{
val arr = JSONArray(s)
return if (arr.length() > 0) arr.optString(0, null) else null
}
return s
}
catch (e:Exception) {
Log.w("MainActivity", "Failed to read project setting " + key, e)
return null
}
}
@Throws(IOException::class)
private fun countPlates(zip:ZipFile):Int {
val modelSettings = zip.getEntry("Metadata/model_settings.config")
val xml = if (modelSettings != null) readZipEntryText(zip, modelSettings) else null
val names = ArrayList<String>()
val entries = zip.entries()
while (entries.hasMoreElements()) names.add(entries.nextElement().name)
return ThreeMfPlateCountDetector.detect(xml, names)
}
private fun importEmbedded3mfProfiles(file:File):Project3mfImportResult {
val result = Project3mfImportResult()
val metadata = ThreeMfImportStages.read(
    countPlates = { ZipFile(file).use { countPlates(it) } },
    readSettings = {
val processKeys = Slic3rConfigWrapper.PRINT_CONFIG_KEYS.toSet() +
    ru.ytkab0bp.slicebeam.ui.settings.OrcaProcessSettingsCatalog.categories().flatMap { it.sections }.flatMap { it.keys }
val filamentKeys = Slic3rConfigWrapper.FILAMENT_CONFIG_KEYS.toSet() +
    ru.ytkab0bp.slicebeam.ui.settings.OrcaFilamentSettingsCatalog.categories().flatMap { it.sections }.flatMap { it.keys }
val config = SliceBeam.requireConfig()
val read = ThreeMfProjectSettingsReader.read(file, PrintConfigDef.getInstance().options, processKeys, filamentKeys,
    config.printConfigs.associate { it.title.orEmpty() to HashMap(it.values) },
    config.filamentConfigs.associate { it.title.orEmpty() to HashMap(it.values) },
    preferredExtruderVariant = config.findPrinter(config.presets?.get("printer"))?.get("extruder_variant")?.substringBefore(','))
result.missingProfileIds = read.missingProfileIds
val fallback = Prefs.getStoredFilamentSlots()
val projectFilaments = read.filaments.ifEmpty { fallback.map { emptyMap<String, String>() } }
if (!read.hasSettings) null else read.copy(filaments = projectFilaments.mapIndexed { index, values ->
    val slot = fallback.getOrElse(index) { fallback.last() }
    mapOf("filament_colour" to TemporaryProjectSettings.colorText(slot.color), "filament_type" to (slot.type ?: "PLA")) + values
})
    })
result.plateCount = metadata.plateCount
result.settings = metadata.settings
metadata.plateCountError?.let {
    result.plateCountError = true
    Log.w("MainActivity", "Failed to count 3MF build plates from " + file, it)
}
metadata.settingsError?.let {
    result.settingsError = true
    Log.w("MainActivity", "Failed to import embedded 3MF profiles from " + file, it)
}
return result
}
private fun deleteRecursively(file:File) {
if (!file.exists())
{
return
}
if (file.isDirectory())
{
val children = file.listFiles()
if (children != null)
{
for (child in children)
{
deleteRecursively(child)
}
}
}
 file.delete()
}
private fun addInstalledProfileTitles(names:MutableList<String>, installed:List<ConfigObject>?) {
if (installed == null)
{
return
}
for (obj in installed)
{
val title = obj.title ?: continue
if (!names.contains(title)) names.add(title)
}
}
@Throws(IOUtils.MissingProfileException::class)
private fun resolveBundleInherits(configs:List<ConfigObject>, bundleFinder:BundleProfileFinder, fallbackFinder:BundleProfileFinder?) {
for (obj in configs)
{
var inherit = obj.get("inherits")
while (inherit != null)
{
var base = bundleFinder.find(inherit)
if (base == null && fallbackFinder != null)
{
base = fallbackFinder.find(inherit)
}
if (base == null)
{
obj.values.remove("inherits")
break
}
obj.values.remove("inherits")
val newMap = HashMap<String, String>()
newMap.putAll(base.values)
newMap.putAll(obj.values)
obj.values = newMap
inherit = obj.values.get("inherits")
}
}
}
@Throws(Exception::class)
private fun importOrcaBundle(root:JSONObject) {
val bundle = JSONObject(root.getString("bundle_structure_json"))
val files = HashMap<String, String>()
val fileArray = root.getJSONArray("files")
for (i in 0 until fileArray.length())
{
val file = fileArray.getJSONObject(i)
files.put(file.getString("path"), file.getString("content"))
}
val w = Slic3rConfigWrapper()
if (bundle.has("process_config"))
{
val arr = bundle.getJSONArray("process_config")
val names = ArrayList<String>()
val stripped = ArrayList<String>()
for (i in 0 until arr.length())
{
val name = arr.getString(i)
names.add(name)
stripped.add(name.substring(name.indexOf('/') + 1, name.length - 5))
}
addInstalledProfileTitles(stripped, SliceBeam.CONFIG!!.printConfigs)
for (name in names)
{
val content = files.get(name)
if (content == null)
{
throw FileNotFoundException(name)
}
w.printConfigs.add(IOUtils.configJsonToIni(JSONObject(content), "process", Slic3rConfigWrapper.PRINT_CONFIG_KEYS, stripped))
}
resolveBundleInherits(w.printConfigs, BundleProfileFinder({ name -> w.findPrint(name) }), { name-> SliceBeam.CONFIG!!.findPrint(name) })
}
if (bundle.has("filament_config"))
{
val arr = bundle.getJSONArray("filament_config")
val names = ArrayList<String>()
val stripped = ArrayList<String>()
for (i in 0 until arr.length())
{
val name = arr.getString(i)
names.add(name)
stripped.add(name.substring(name.indexOf('/') + 1, name.length - 5))
}
addInstalledProfileTitles(stripped, SliceBeam.CONFIG!!.filamentConfigs)
for (name in names)
{
val content = files.get(name)
if (content == null)
{
throw FileNotFoundException(name)
}
w.filamentConfigs.add(IOUtils.configJsonToIni(JSONObject(content), "filament", Slic3rConfigWrapper.FILAMENT_CONFIG_KEYS, stripped))
}
resolveBundleInherits(w.filamentConfigs, BundleProfileFinder({ name -> w.findFilament(name) }), { name-> SliceBeam.CONFIG!!.findFilament(name) })
}
if (bundle.has("printer_config"))
{
val arr = bundle.getJSONArray("printer_config")
val names = ArrayList<String>()
val stripped = ArrayList<String>()
for (i in 0 until arr.length())
{
val name = arr.getString(i)
names.add(name)
stripped.add(name.substring(name.indexOf('/') + 1, name.length - 5))
}
addInstalledProfileTitles(stripped, SliceBeam.CONFIG!!.printerConfigs)
for (name in names)
{
val content = files.get(name)
if (content == null)
{
throw FileNotFoundException(name)
}
w.printerConfigs.add(IOUtils.configJsonToIni(JSONObject(content), "machine", Slic3rConfigWrapper.PRINTER_CONFIG_KEYS, stripped))
}
resolveBundleInherits(w.printerConfigs, BundleProfileFinder({ name -> w.findPrinter(name) }), { name-> SliceBeam.CONFIG!!.findPrinter(name) })
}
loadIniForImport(ByteArrayInputStream(w.serialize().toByteArray(StandardCharsets.UTF_8)))
}
private fun generateAiModel(bm:Bitmap) {
IS_GENERATING_AI_MODEL = true
val uploadTag = UUID.randomUUID().toString()
SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(SnackbarsLayout.Type.LOADING, R.string.MenuFileAIGeneratorUploading).tag(uploadTag))
IOUtils.IO_POOL.submit({ val scaled:Bitmap
if (bm.getWidth() > 1024 || bm.getHeight() > 1024)
{
if (bm.getWidth() > bm.getHeight())
{
val w = 1024
val h = ((w.toFloat() * bm.getHeight() / bm.getWidth())).toInt()
scaled = Bitmap.createScaledBitmap(bm, w, h, true)
}
else
{
val h = 1024
val w = ((h.toFloat() * bm.getWidth() / bm.getHeight())).toInt()
scaled = Bitmap.createScaledBitmap(bm, w, h, true)
}
bm.recycle()
}
else
{
scaled = bm
}
val out = ByteArrayOutputStream()
scaled.compress(Bitmap.CompressFormat.PNG, 100, out)
scaled.recycle()
val processTag = UUID.randomUUID().toString()
CloudAPI.INSTANCE.modelsGenerate(Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP), "image/png", object:APICallback<InputStream> {
override fun onResponse(`in`:InputStream) {
SliceBeam.EVENT_BUS.fireEvent(NeedDismissSnackbarEvent(processTag))
val downloadTag = UUID.randomUUID().toString()
SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(SnackbarsLayout.Type.LOADING, R.string.MenuFileAIGeneratorDownloading).tag(downloadTag))
val fileName = "generated_" + UUID.randomUUID() + ".stl"
val f = File(SliceBeam.getModelCacheDir(), fileName)
try
{
val fos = FileOutputStream(f)
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
{
val contentValues = ContentValues()
contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "application/vnd.ms-pki.stl")
contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
val uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
if (uri != null)
{
try
{
val out = getContentResolver().openOutputStream(uri)
val buf = ByteArray(10240)
while (true)
{
val c = `in`.read(buf)
if (c == -1) break
out!!.write(buf, 0, c)
fos.write(buf, 0, c)
}
out!!.close()
}
catch (e:IOException) {
Log.e("ai_generator", "Failed to write to downloads", e)
}
}
}
else
{
val downloadsDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
val file = File(downloadsDirectory, fileName)
try
{
val out = FileOutputStream(file)
val buf = ByteArray(10240)
while (true)
{
val c = `in`.read(buf)
if (c == -1) break
out.write(buf, 0, c)
fos.write(buf, 0, c)
}
out.close()
}
catch (e:IOException) {
Log.e("ai_generator", "Failed to write to downloads", e)
}
}
fos.close()
}
catch (e:Exception) {
Log.e("ai_generator", "Failed to write to downloads", e)
}
SliceBeam.EVENT_BUS.fireEvent(NeedDismissSnackbarEvent(downloadTag))
SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(R.string.MenuFileAIGeneratorSavedAs, fileName))
loadFile(f, true)
CloudController.checkGeneratorRemaining()
IS_GENERATING_AI_MODEL = false
}
override fun onException(e:Exception) {
SliceBeam.EVENT_BUS.fireEvent(NeedDismissSnackbarEvent(processTag))
ViewUtils.postOnMainThread({ BeamAlertDialogBuilder(this@MainActivity)
.setTitle(R.string.MenuFileAIGeneratorError)
.setMessage(e.toString())
.setPositiveButton(android.R.string.ok, null)
.show() })
IS_GENERATING_AI_MODEL = false
}
})
SliceBeam.EVENT_BUS.fireEvent(NeedDismissSnackbarEvent(uploadTag))
SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(SnackbarsLayout.Type.LOADING, R.string.MenuFileAIGeneratorProcessing).tag(processTag)) })
}
private fun loadIniForImport(`in`:InputStream) {
IOUtils.IO_POOL.submit {
    try {
        val imported = Slic3rConfigWrapper(`in`)
        try {
            val profiles = imported.printConfigs + imported.filamentConfigs + imported.printerConfigs
            imported.loadWarnings.addAll(
                ProfileConfigImportValidator.validate(profiles, PrintConfigDef.getInstance().options),
            )
        } catch (e: Exception) {
            Log.w("MainActivity", "Could not validate imported profiles against Orca option definitions", e)
            imported.loadWarnings.add(ProfileConfigLoadWarning.ValidationUnavailable)
        } catch (e: LinkageError) {
            Log.w("MainActivity", "Native Orca option definitions are unavailable for profile validation", e)
            imported.loadWarnings.add(ProfileConfigLoadWarning.ValidationUnavailable)
        }
        ViewUtils.postOnMainThread {
            val current = SliceBeam.CONFIG
            if (current == null) {
                BeamAlertDialogBuilder(this)
                    .setTitle(R.string.MenuFileImportProfilesFailed)
                    .setMessage(R.string.MenuFileImportProfilesFailedEmpty)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
                return@postOnMainThread
            }

            fun profileLabels(profiles: List<ConfigObject>, kind: Int): Array<CharSequence> =
                profiles.map { profile ->
                    val title = profile.title.orEmpty()
                    val exists = when (kind) {
                        ConfigObject.PROFILE_LIST_PRINT -> current.findPrint(title) != null
                        ConfigObject.PROFILE_LIST_FILAMENT -> current.findFilament(title) != null
                        else -> current.findPrinter(title) != null
                    }
                    if (exists) getString(R.string.MenuFileImportProfilesExisting, title) else title
                }.toTypedArray()

            val printLabels = profileLabels(imported.printConfigs, ConfigObject.PROFILE_LIST_PRINT)
            val filamentLabels = profileLabels(imported.filamentConfigs, ConfigObject.PROFILE_LIST_FILAMENT)
            val printerLabels = profileLabels(imported.printerConfigs, ConfigObject.PROFILE_LIST_PRINTER)
            val selectedPrints = BooleanArray(printLabels.size) { true }
            val selectedFilaments = BooleanArray(filamentLabels.size) { true }
            val selectedPrinters = BooleanArray(printerLabels.size) { true }

            if (printLabels.isEmpty() && filamentLabels.isEmpty() && printerLabels.isEmpty()) {
                BeamAlertDialogBuilder(this)
                    .setTitle(R.string.MenuFileImportProfilesFailed)
                    .setMessage(R.string.MenuFileImportProfilesFailedEmpty)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
                return@postOnMainThread
            }

            val applyImport: (Boolean) -> Unit = applyImport@{ replaceExisting ->
                val snapshot = ProfileConfigSnapshot(current)
                var importedCount = 0
                var skippedCount = 0
                for (index in selectedPrints.indices) {
                    if (!selectedPrints[index]) continue
                    val profile = imported.printConfigs[index]
                    if (replaceExisting || current.findPrint(profile.title) == null) {
                        current.importPrint(profile)
                        importedCount++
                    } else skippedCount++
                }
                for (index in selectedFilaments.indices) {
                    if (!selectedFilaments[index]) continue
                    val profile = imported.filamentConfigs[index]
                    if (replaceExisting || current.findFilament(profile.title) == null) {
                        current.importFilament(profile)
                        importedCount++
                    } else skippedCount++
                }
                for (index in selectedPrinters.indices) {
                    if (!selectedPrinters[index]) continue
                    val profile = imported.printerConfigs[index]
                    if (replaceExisting || current.findPrinter(profile.title) == null) {
                        current.importPrinter(profile)
                        importedCount++
                    } else skippedCount++
                }
                if (importedCount > 0 && !SliceBeam.saveConfig()) {
                    snapshot.restore()
                    Toast.makeText(this, R.string.ConfigSaveFailed, Toast.LENGTH_LONG).show()
                    return@applyImport
                }
                if (importedCount > 0 || skippedCount > 0) {
                    val resultLines = ArrayList<String>(2)
                    if (importedCount > 0) {
                        resultLines.add(
                            resources.getQuantityString(
                                R.plurals.MenuFileImportProfilesImportedCount,
                                importedCount,
                                importedCount,
                            ),
                        )
                    }
                    if (skippedCount > 0) {
                        resultLines.add(
                            resources.getQuantityString(
                                R.plurals.MenuFileImportProfilesSkippedCount,
                                skippedCount,
                                skippedCount,
                            ),
                        )
                    }
                    Toast.makeText(
                        this,
                        resultLines.joinToString("\n"),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            val finish: () -> Unit = {
                val conflicts = ArrayList<String>()
                for (index in selectedPrints.indices) {
                    val profile = imported.printConfigs[index]
                    if (selectedPrints[index] && current.findPrint(profile.title) != null) {
                        conflicts.add("${getString(R.string.MenuFileExportProfilesPrints)}: ${profile.title.orEmpty()}")
                    }
                }
                for (index in selectedFilaments.indices) {
                    val profile = imported.filamentConfigs[index]
                    if (selectedFilaments[index] && current.findFilament(profile.title) != null) {
                        conflicts.add("${getString(R.string.MenuFileExportProfilesFilaments)}: ${profile.title.orEmpty()}")
                    }
                }
                for (index in selectedPrinters.indices) {
                    val profile = imported.printerConfigs[index]
                    if (selectedPrinters[index] && current.findPrinter(profile.title) != null) {
                        conflicts.add("${getString(R.string.MenuFileExportProfilesPrinters)}: ${profile.title.orEmpty()}")
                    }
                }
                if (conflicts.isEmpty()) {
                    applyImport(false)
                } else {
                    BeamAlertDialogBuilder(this)
                        .setTitle(R.string.MenuFileImportProfilesConflictTitle)
                        .setMessage(getString(R.string.MenuFileImportProfilesConflictMessage, conflicts.joinToString("\n")))
                        .setPositiveButton(R.string.MenuFileImportProfilesOverwrite) { _, _ -> applyImport(true) }
                        .setNegativeButton(R.string.MenuFileImportProfilesSkipExisting) { _, _ -> applyImport(false) }
                        .setNeutralButton(android.R.string.cancel, null)
                        .show()
                }
            }

            val showPrinters: () -> Unit = {
                if (printerLabels.isEmpty()) {
                    finish()
                } else {
                    BeamAlertDialogBuilder(this)
                        .setTitle(R.string.MenuFileExportProfilesPrinters)
                        .setMultiChoiceItems(printerLabels, selectedPrinters) { _, which, checked ->
                            selectedPrinters[which] = checked
                        }
                        .setPositiveButton(android.R.string.ok) { _, _ -> finish() }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                }
            }
            val showFilaments: () -> Unit = {
                if (filamentLabels.isEmpty()) {
                    showPrinters()
                } else {
                    BeamAlertDialogBuilder(this)
                        .setTitle(R.string.MenuFileExportProfilesFilaments)
                        .setMultiChoiceItems(filamentLabels, selectedFilaments) { _, which, checked ->
                            selectedFilaments[which] = checked
                        }
                        .setPositiveButton(android.R.string.ok) { _, _ -> showPrinters() }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                }
            }
            val showSelection: () -> Unit = {
                if (printLabels.isEmpty()) {
                    showFilaments()
                } else {
                    BeamAlertDialogBuilder(this)
                        .setTitle(R.string.MenuFileExportProfilesPrints)
                        .setMultiChoiceItems(printLabels, selectedPrints) { _, which, checked ->
                            selectedPrints[which] = checked
                        }
                        .setPositiveButton(android.R.string.ok) { _, _ -> showFilaments() }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                }
            }

            if (imported.loadWarnings.isEmpty()) {
                showSelection()
            } else {
                val warningLines = imported.loadWarnings.take(20).map { warning ->
                    when (warning) {
                        is ProfileConfigLoadWarning.MissingParent -> getString(
                            R.string.MenuFileImportProfilesWarningMissingParent,
                            warning.profile,
                            warning.parent
                        )
                        is ProfileConfigLoadWarning.UnknownOption -> getString(
                            R.string.MenuFileImportProfilesWarningUnknownKey,
                            warning.profile,
                            warning.key
                        )
                        is ProfileConfigLoadWarning.InvalidValue -> getString(
                            R.string.MenuFileImportProfilesWarningInvalidValue,
                            warning.profile,
                            warning.key,
                            warning.value,
                            getString(
                                when (warning.issue) {
                                    ProfileConfigLoadWarning.ValueIssue.INVALID_NUMBER ->
                                        R.string.MenuFileImportProfilesWarningInvalidNumber
                                    ProfileConfigLoadWarning.ValueIssue.OUT_OF_RANGE ->
                                        R.string.MenuFileImportProfilesWarningOutOfRange
                                    ProfileConfigLoadWarning.ValueIssue.UNKNOWN_ENUM ->
                                        R.string.MenuFileImportProfilesWarningUnknownEnum
                                    ProfileConfigLoadWarning.ValueIssue.INVALID_BOOLEAN ->
                                        R.string.MenuFileImportProfilesWarningInvalidBoolean
                                    ProfileConfigLoadWarning.ValueIssue.INVALID_POINT ->
                                        R.string.MenuFileImportProfilesWarningInvalidPoint
                                }
                            )
                        )
                        ProfileConfigLoadWarning.ValidationUnavailable ->
                            getString(R.string.MenuFileImportProfilesWarningValidationUnavailable)
                    }
                }.toMutableList()
                if (imported.loadWarnings.size > 20) {
                    val omittedWarningCount = imported.loadWarnings.size - 20
                    warningLines.add(
                        resources.getQuantityString(
                            R.plurals.MenuFileImportProfilesWarningsMore,
                            omittedWarningCount,
                            omittedWarningCount,
                        ),
                    )
                }
                BeamAlertDialogBuilder(this)
                    .setTitle(R.string.MenuFileImportProfilesWarningsTitle)
                    .setMessage(getString(R.string.MenuFileImportProfilesWarningsMessage, warningLines.joinToString("\n\n")))
                    .setPositiveButton(android.R.string.ok) { _, _ -> showSelection() }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        }
    } catch (e: Exception) {
        Log.e("MainActivity", "Failed to read file", e)
        ViewUtils.postOnMainThread {
            BeamAlertDialogBuilder(this)
                .setTitle(R.string.MenuFileImportProfilesFailed)
                .setMessage(e.toString())
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
    }
}
}
private fun loadFile(f:File, autoorient:Boolean) {
val tag = UUID.randomUUID().toString()
SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(SnackbarsLayout.Type.LOADING, R.string.MenuFileOpenFileLoading).tag(tag))
IOUtils.IO_POOL.submit({ Process.setThreadPriority(-20)
if (navigationDelegate.getCurrentFragment() is BedFragment)
{
val fragment = navigationDelegate.getCurrentFragment() as BedFragment
try
{
if (f.getName().lowercase(Locale.ROOT).endsWith(".svg"))
{
val svgProblem = ru.ytkab0bp.slicebeam.components.SvgImportGuard.validate(f)
if (svgProblem != null)
{
SliceBeam.EVENT_BUS.fireEvent(NeedDismissSnackbarEvent(tag))
ViewUtils.postOnMainThread({ showSvgSimplifyPrompt(f, autoorient, svgProblem) })
return@submit
}
}
val gcode = isGcodeFile(f.getName())
val project3mf = is3mfFile(f.getName())
val projectImport = if (project3mf) importEmbedded3mfProfiles(f) else Project3mfImportResult()
val plateCount = ModelImportPlateCountPolicy.resolve(project3mf, projectImport.plateCount)
if (gcode)
{
fragment.loadGCode(f)
fragment.glView!!.queueEvent(object:Runnable {
override fun run() {
val model = fragment.glView!!.renderer.model
if (model == null || fragment.glView!!.renderer.bed == null)
{
fragment.glView!!.queueEvent(this)
return
}
SliceBeam.EVENT_BUS.fireEvent(NeedDismissSnackbarEvent(tag))
SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(R.string.MenuFileOpenFileLoaded))
}
})
}
else
{
fragment.loadModel(f, project3mf, plateCount, object : BedFragment.ModelLoadCallback {
private var lastReadStep = -1
override fun onProgress(fraction: Float) {
    val step = if (fraction >= 0.5f) 10 else ((fraction.coerceAtLeast(0f) * 20f).toInt()).coerceIn(0, 9)
    if (step == lastReadStep) return
    lastReadStep = step
    val title = if (step == 10) getString(R.string.MenuFileOpenStlPreparing)
        else getString(R.string.MenuFileOpenStlReading, step * 10)
    SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(SnackbarsLayout.Type.LOADING, title).tag(tag))
}
override fun onLoaded(model: Model, firstNewObject: Int, addedObjects: Int) {
android.util.Log.e("MainActivity", "loadModel callback fired: model=" + model.pointer + " firstNew=" + firstNewObject + " added=" + addedObjects)
if (fragment.glView!!.renderer.bed == null)
{
android.util.Log.e("MainActivity", "Returning early because the print bed is not ready")
return
}
SliceBeam.EVENT_BUS.fireEvent(ObjectsListChangedEvent())
var bigObject = false
for (i in firstNewObject until firstNewObject + addedObjects)
{
if (autoorient && !project3mf)
{
model.autoOrient(i)
fragment.glView!!.renderer.invalidateGlModel(i)
}
if (model.isBigObject(i))
{
bigObject = true
}
}
if (autoorient && !project3mf)
{
fragment.glView!!.requestRender()
}
SliceBeam.EVENT_BUS.fireEvent(NeedDismissSnackbarEvent(tag))
if (project3mf)
{
ViewUtils.postOnMainThread {
    val ownProfiles = projectImport.settings?.filaments?.mapIndexed { index, imported ->
        val config = SliceBeam.requireConfig()
        val selected = config.presets?.get("filament")
        val assigned = ru.ytkab0bp.slicebeam.config.SlotFilamentProfiles.titleFor(index, selected)
        ru.ytkab0bp.slicebeam.config.OwnFilamentTemperatureProfiles.choose(
            config.filamentConfigs, assigned, selected, imported["filament_type"])
    }.orEmpty()
    TemporaryProjectSettings.activate(projectImport.settings, ownProfiles)
    fragment.refreshProfileStatus()
    fragment.glView?.requestRender()
    SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(if (TemporaryProjectSettings.active)
        R.string.Project3mfLoadedTemporary else R.string.Project3mfLoadedGeometry))
    projectImport.settings?.ignoredValues?.takeIf { it > 0 }?.let { count ->
        SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(SnackbarsLayout.Type.WARNING,
            getString(R.string.ProjectSettingsInvalidValues, count)))
    }
    projectImport.missingProfileIds.takeIf { it.isNotEmpty() }?.let { ids ->
        val names = ids.take(3).joinToString(", ") { it.take(60) } + if (ids.size > 3) " …" else ""
        SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(SnackbarsLayout.Type.WARNING,
            getString(R.string.ProjectSettingsMissingProfiles, names)))
    }
    if (projectImport.plateCountError) {
        SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(SnackbarsLayout.Type.WARNING,
            R.string.ProjectPlateCountFailed))
    }
    if (projectImport.settingsError) {
        SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(SnackbarsLayout.Type.WARNING,
            R.string.ProjectSettingsImportFailed))
    }
}
}
else
{
SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(R.string.MenuFileOpenFileLoaded))
}
if (bigObject)
{
SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(SnackbarsLayout.Type.WARNING, R.string.MenuFileOpenFileBigObject))
}
}
override fun onCancelled() {
    SliceBeam.EVENT_BUS.fireEvent(NeedDismissSnackbarEvent(tag))
    f.delete()
}
override fun onFailed(error: Exception) {
    SliceBeam.EVENT_BUS.fireEvent(NeedDismissSnackbarEvent(tag))
    f.delete()
    if (!isFinishing() && !isDestroyed()) {
        BeamAlertDialogBuilder(this@MainActivity)
            .setTitle(R.string.MenuFileOpenFileFailed)
            .setMessage(error.message ?: getString(R.string.MenuFileOpenFileFailed))
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }
}
})
}
}
catch (e:Slic3rRuntimeError) {
android.util.Log.e("MainActivity", "Failed to load model", e)
f.delete()
SliceBeam.EVENT_BUS.fireEvent(NeedDismissSnackbarEvent(tag))
ViewUtils.postOnMainThread({ BeamAlertDialogBuilder(this@MainActivity)
.setTitle(R.string.MenuFileOpenFileFailed)
.setMessage(e.toString())
.setPositiveButton(android.R.string.ok, null)
.show() })
}
catch (e:Exception) {
android.util.Log.e("MainActivity", "Silent crash in loadFile", e)
SliceBeam.EVENT_BUS.fireEvent(NeedDismissSnackbarEvent(tag))
ViewUtils.postOnMainThread({ BeamAlertDialogBuilder(this@MainActivity)
.setTitle(R.string.MenuFileOpenFileFailed)
.setMessage(e.toString())
.setPositiveButton(android.R.string.ok, null)
.show() })
}
} })
}
private fun showSvgSimplifyPrompt(source:File, autoorient:Boolean, problem:String) {
if (isFinishing() || isDestroyed()) return
val canSimplify = ru.ytkab0bp.slicebeam.components.SvgImportGuard.canSimplify(source)
val dialog = BeamAlertDialogBuilder(this)
if (canSimplify)
dialog.setTitle("SVG vereinfachen?")
else
    dialog.setTitle(R.string.MenuFileOpenFileFailed)
dialog.setMessage(if (canSimplify)
problem + "\n\nSoll die App die Konturen automatisch reduzieren und die vereinfachte Datei laden? Das Original bleibt unverÃ¤ndert."
else
problem + "\n\nDateien Ã¼ber 25 MB kÃ¶nnen auf dem GerÃ¤t nicht automatisch vereinfacht werden.")
if (canSimplify)
{
dialog.setPositiveButton("Vereinfachen und laden", { d, w-> simplifyAndLoadSvg(source, autoorient) })
dialog.setNegativeButton("Abbrechen", null)
}
else
{
dialog.setPositiveButton(android.R.string.ok, null)
}
dialog.show()
}
private fun simplifyAndLoadSvg(source:File, autoorient:Boolean) {
val simplified = File(getCacheDir(), "simplified-" + UUID.randomUUID() + ".svg")
val tag = UUID.randomUUID().toString()
SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(SnackbarsLayout.Type.LOADING, R.string.MenuFileOpenFileLoading).tag(tag))
IOUtils.IO_POOL.submit({ try
{
val removed = ru.ytkab0bp.slicebeam.components.SvgImportGuard.simplify(source, simplified)
val remainingProblem = ru.ytkab0bp.slicebeam.components.SvgImportGuard.validate(simplified)
if (remainingProblem != null) throw java.io.IOException(remainingProblem)
SliceBeam.EVENT_BUS.fireEvent(NeedDismissSnackbarEvent(tag))
ViewUtils.postOnMainThread({ if (isFinishing() || isDestroyed()) {
simplified.delete()
return@postOnMainThread
}
val resultMessage = if (removed > 0)
"SVG vereinfacht (" + removed + " Konturpunkte entfernt)."
else
"SVG fÃ¼r den Import optimiert."
SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(resultMessage))
loadFile(simplified, autoorient) })
}
catch (e:Exception) {
simplified.delete()
android.util.Log.e("MainActivity", "Failed to simplify SVG", e)
SliceBeam.EVENT_BUS.fireEvent(NeedDismissSnackbarEvent(tag))
ViewUtils.postOnMainThread({ if (isFinishing() || isDestroyed()) return@postOnMainThread
BeamAlertDialogBuilder(this@MainActivity)
.setTitle("SVG konnte nicht vereinfacht werden")
.setMessage(if (e.message == null) "Die Konturen konnten nicht sicher reduziert werden." else e.message)
.setPositiveButton(android.R.string.ok, null).show() })
}
})
}
private fun loadFile(uri:Uri) {
val resolver = getContentResolver()
var fileName = IOUtils.getDisplayName(uri)
if (fileName == null && "file".equals(uri.getScheme()))
{
fileName = uri.getLastPathSegment()
}
if (fileName == null)
{
BeamAlertDialogBuilder(this)
.setTitle(R.string.MenuFileOpenFileFailed)
.setMessage(R.string.MenuFileOpenFileFailedNullName)
.setPositiveButton(android.R.string.ok, null)
.show()
return
}
if (isOrcaBundleFile(fileName))
{
loadConvertedProfile(uri)
return
}
if (fileName.endsWith(".ini"))
{
try
{
loadIniForImport(resolver.openInputStream(uri) ?: return)
}
catch (e:FileNotFoundException) {
BeamAlertDialogBuilder(this)
.setTitle(R.string.MenuFileImportProfilesFailed)
.setMessage(e.toString())
.setPositiveButton(android.R.string.ok, null)
.show()
}
return
}
if (!isSupportedModelFile(fileName))
{
BeamAlertDialogBuilder(this)
.setTitle(R.string.MenuFileOpenFileFailed)
.setMessage(getString(R.string.UnsupportedFileType, fileName))
.setPositiveButton(android.R.string.ok, null)
.show()
return
}
val f = getSafeCachedModelFile(fileName)
IOUtils.IO_POOL.submit({ try
{
val `in` = resolver.openInputStream(uri)
if (`in` == null)
{
throw FileNotFoundException(java.lang.String.valueOf(uri))
}
val fos = FileOutputStream(f)
val buffer = ByteArray(10240)
while (true)
{
val c = `in`.read(buffer)
if (c == -1) break
fos.write(buffer, 0, c)
}
fos.close()
`in`.close()
loadFile(f, false)
}
catch (e:Exception) {
Log.e("MainActivity", "Failed to write cache file", e)
f.delete()
ViewUtils.postOnMainThread({ BeamAlertDialogBuilder(this)
.setTitle(R.string.MenuFileOpenFileFailed)
.setMessage(e.toString())
.setPositiveButton(android.R.string.ok, null)
.show() })
}
})
}
private fun loadMultipleStlFiles(files:List<Uri>) {
if (files.isEmpty()) return
val tag = UUID.randomUUID().toString()
SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(SnackbarsLayout.Type.LOADING,
getString(R.string.MenuFileOpenMultipleStlProgress, 1, files.size)).tag(tag))
loadMultipleStlFileAt(files, 0, 0, 0, tag)
}
/** Copies and imports one STL at a time so native model loading never overlaps. */
 private fun loadMultipleStlFileAt(files:List<Uri>, index:Int, loaded:Int, failed:Int, tag:String) {
if (isFinishing() || isDestroyed())
{
SliceBeam.EVENT_BUS.fireEvent(NeedDismissSnackbarEvent(tag))
return
}
if (index >= files.size)
{
SliceBeam.EVENT_BUS.fireEvent(NeedDismissSnackbarEvent(tag))
if (loaded > 0)
{
SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(getString(
R.string.MenuFileOpenMultipleStlComplete, loaded, files.size)))
}
if (failed > 0 || loaded == 0)
{
SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(SnackbarsLayout.Type.ERROR,
getString(R.string.MenuFileOpenMultipleStlFailed, failed, files.size)))
}
return
}
val uri = files.get(index)
val fileName = IOUtils.getDisplayName(uri)
if (fileName == null)
{
loadMultipleStlFileAt(files, index + 1, loaded, failed + 1, tag)
return
}
val cachedFile = getSafeCachedModelFile(fileName)
SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(SnackbarsLayout.Type.LOADING,
getString(R.string.MenuFileOpenMultipleStlProgress, index + 1, files.size)).tag(tag))
IOUtils.IO_POOL.submit({ try
{
getContentResolver().openInputStream(uri).use({ `in`-> FileOutputStream(cachedFile).use({ out->
if (`in` == null) throw FileNotFoundException(java.lang.String.valueOf(uri))
val buffer = ByteArray(64 * 1024)
var read:Int
while (true) { val read = `in`.read(buffer); if (read == -1) break; out.write(buffer, 0, read) }
val current = navigationDelegate.getCurrentFragment()
if (!(current is BedFragment)) throw IOException("Die Druckplattenansicht ist nicht verfÃ¼gbar.")
val bed = current
bed.loadModel(cachedFile, false, 1, object:BedFragment.ModelLoadCallback {
private var lastReadStep = -1
override fun onProgress(fraction: Float) {
    val step = if (fraction >= 0.5f) 10 else ((fraction.coerceAtLeast(0f) * 20f).toInt()).coerceIn(0, 9)
    if (step == lastReadStep) return
    lastReadStep = step
    val title = if (step == 10) getString(R.string.MenuFileOpenStlPreparing)
        else getString(R.string.MenuFileOpenStlReading, step * 10)
    SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(SnackbarsLayout.Type.LOADING, title).tag(tag))
}
override fun onLoaded(loadedModel:Model, firstNewObject:Int, addedObjects:Int) {
ViewUtils.postOnMainThread({ cachedFile.delete()
if (addedObjects > 0)
{
SliceBeam.EVENT_BUS.fireEvent(ObjectsListChangedEvent())
loadMultipleStlFileAt(files, index + 1, loaded + 1, failed, tag)
}
else
{
loadMultipleStlFileAt(files, index + 1, loaded, failed + 1, tag)
} })
}
override fun onFailed(error:Exception) {
cachedFile.delete()
loadMultipleStlFileAt(files, index + 1, loaded, failed + 1, tag)
}
override fun onCancelled() {
cachedFile.delete()
SliceBeam.EVENT_BUS.fireEvent(NeedDismissSnackbarEvent(tag))
}
}) }) })
}
catch (error:Exception) {
cachedFile.delete()
Log.e("MainActivity", "Failed to load selected STL: " + fileName, error)
ViewUtils.postOnMainThread({ loadMultipleStlFileAt(files, index + 1,
loaded, failed + 1, tag) })
}
})
}
override fun onConfigurationChanged(@NonNull newConfig:Configuration) {
super.onConfigurationChanged(newConfig)
(delegate as? MobileNavigationDelegate)?.onConfigurationChanged(newConfig)
delegate?.getCurrentFragment()?.onConfigurationChanged(newConfig)
applyOrientationSystemBars(newConfig.orientation)
if ((newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_UNDEFINED)
{
ThemesRepo.resetSystemResolvedTheme()
ThemesRepo.invalidate(this)
}
}
fun onApplyTheme() {
navigationDelegate.onApplyTheme()
applySystemBarAppearance()
getWindow().getDecorView().setBackgroundColor(ThemesRepo.getColor(android.R.attr.windowBackground))
}
private fun applySystemBarAppearance() {
val lightBackground = ColorUtils.calculateLuminance(ThemesRepo.getColor(android.R.attr.windowBackground)) >= 0.9f
val controller = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
controller.setAppearanceLightStatusBars(lightBackground)
controller.setAppearanceLightNavigationBars(lightBackground)
}
@Suppress("DEPRECATION")
private fun configureLegacySystemBarColors() {
getWindow().setStatusBarColor(Color.TRANSPARENT)
getWindow().setNavigationBarColor(Color.TRANSPARENT)
}
@Suppress("DEPRECATION")
@RequiresApi(Build.VERSION_CODES.Q)
private fun disableLegacySystemBarContrast() {
getWindow().setStatusBarContrastEnforced(false)
getWindow().setNavigationBarContrastEnforced(false)
}
private fun applyOrientationSystemBars(orientation:Int) {
var hideBars = orientation == Configuration.ORIENTATION_LANDSCAPE
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInMultiWindowMode())
{
hideBars = false
}
val controller = WindowCompat.getInsetsController(
getWindow(), getWindow().getDecorView())
controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE)
if (hideBars)
{
controller.hide(WindowInsetsCompat.Type.systemBars())
}
else
{
controller.show(WindowInsetsCompat.Type.systemBars())
}
}
override fun onSaveInstanceState(@NonNull outState:Bundle) {
super.onSaveInstanceState(outState)
if (isChangingConfigurations())
{
outState.putInt("id", id)
delegate?.let { liveDelegate.put(id, it) }
}
}
private fun isCompatible(delegate:NavigationDelegate):Boolean {
return true
}
private fun onCreateDelegate():NavigationDelegate {
return MobileNavigationDelegate()
}
fun showUnfoldMenu(menu:UnfoldMenu, v:View) {
if (unfoldMenu != null) return
menu.setOnDismiss({ unfoldMenu = null })
menu.show(v, navigationDelegate.getOverlayView() ?: return)
unfoldMenu = menu
}
private fun handleAppBack():Boolean {
if (unfoldMenu != null)
{
unfoldMenu!!.dismiss()
return true
}
if (delegate?.onBackPressed() == true) return true
if (exitConfirmationDialog?.isShowing == true) return true
exitConfirmationDialog = BeamAlertDialogBuilder(this)
.setTitle(R.string.AppCloseConfirmTitle)
.setMessage(R.string.AppCloseConfirmMessage)
.setNegativeButton(android.R.string.cancel, null)
.setPositiveButton(R.string.AppCloseConfirmYes, { dialog, which->
Prefs.getPrefs()!!.edit().putInt("last_session_plate_count", 0).apply()
TemporaryProjectSettings.clear()
finishAfterTransition() })
.setOnDismissListener { exitConfirmationDialog = null }
.show()
return true
}
protected override fun onResume() {
super.onResume()
delegate?.onResume()
if (SliceBeam.CONFIG != null && ru.ytkab0bp.slicebeam.utils.ConverterStorage.markAndShouldPromptFirstLaunch())
{
try
{
launchRegisteredActivityResult(
    ru.ytkab0bp.slicebeam.utils.ConverterStorage.createFolderPickerIntent(),
    REQUEST_CODE_CONVERTER_ROOT_FIRST_LAUNCH,
)
}
catch (error:Exception) {
android.widget.Toast.makeText(this, R.string.ConverterFolderPickerUnavailable, android.widget.Toast.LENGTH_LONG).show()
}
}
}
protected override fun onPause() {
super.onPause()
delegate?.onPause()
}
protected override fun onStop() {
if (SliceBeam.CONFIG != null && !SliceBeam.savePendingProfileSettings()) {
Log.e("MainActivity", "Pending profile edits could not be saved while leaving the app")
}
super.onStop()
}
protected override fun onDestroy() {
super.onDestroy()
exitConfirmationDialog?.dismiss()
exitConfirmationDialog = null
if (activeInstance === this)
{
activeInstance = null
}
delegate?.onDestroy()
}
companion object {
// Activity result
 @JvmField
 val REQUEST_CODE_OPEN_FILE = 1
@JvmField
val REQUEST_CODE_EXPORT_GCODE = 2
@JvmField
val REQUEST_CODE_IMPORT_PROFILES = 3
@JvmField
val REQUEST_CODE_EXPORT_PROFILES = 4
@JvmField
val REQUEST_CODE_EXPORT_3MF = 5
@JvmField
val REQUEST_CODE_AI_GENERATOR_TAKE_PHOTO = 6
@JvmField
val REQUEST_CODE_AI_GENERATOR_CHOOSE_PHOTO = 7
@JvmField
val REQUEST_CODE_IMAGE_TO_STL = 8
@JvmField
val REQUEST_CODE_SAVE_CONVERTED_FILE = 9
@JvmField
val REQUEST_CODE_CONVERTER_ROOT_FIRST_LAUNCH = 12
@JvmField
val REQUEST_CODE_CONVERTER_ROOT_SETTINGS = 13
@JvmField
val REQUEST_CODE_CONVERTER_ROOT_SAVE = 14
private val ACTIVITY_RESULT_REQUEST_CODES = intArrayOf(
    REQUEST_CODE_OPEN_FILE,
    REQUEST_CODE_EXPORT_GCODE,
    REQUEST_CODE_IMPORT_PROFILES,
    REQUEST_CODE_EXPORT_PROFILES,
    REQUEST_CODE_EXPORT_3MF,
    REQUEST_CODE_AI_GENERATOR_TAKE_PHOTO,
    REQUEST_CODE_AI_GENERATOR_CHOOSE_PHOTO,
    REQUEST_CODE_IMAGE_TO_STL,
    REQUEST_CODE_SAVE_CONVERTED_FILE,
    REQUEST_CODE_CONVERTER_ROOT_FIRST_LAUNCH,
    REQUEST_CODE_CONVERTER_ROOT_SETTINGS,
    REQUEST_CODE_CONVERTER_ROOT_SAVE,
    ru.ytkab0bp.slicebeam.components.KromacutEditorMenu.REQUEST_CODE_FILE,
)
private var activeInstance: MainActivity? = null
@JvmField
var EXPORTING_PRINTS: List<ConfigObject>? = null
@JvmField
var EXPORTING_FILAMENTS: List<ConfigObject>? = null
@JvmField
var EXPORTING_PRINTERS: List<ConfigObject>? = null
@JvmField
var IS_GENERATING_AI_MODEL:Boolean = false
@JvmField
var aiTempFile: File? = null
private var liveDelegate = SparseArray<NavigationDelegate>()
private var lastId:Int = 0
}
}
