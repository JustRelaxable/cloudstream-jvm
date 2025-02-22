package com.tahoshi.cloudstream_jvm.core.plugins

import com.fasterxml.jackson.annotation.JsonProperty
import com.google.gson.Gson
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.APIHolder.removePluginMapping
import com.tahoshi.cloudstream_jvm.core.AcraApplication.Companion.getKey
import com.tahoshi.cloudstream_jvm.core.AcraApplication.Companion.setKey
import com.lagradost.cloudstream3.MainAPI.Companion.settingsForProvider
import com.tahoshi.cloudstream_jvm.core.MainActivity.Companion.afterPluginsLoadedEvent
import com.lagradost.cloudstream3.mvvm.debugPrint
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.mvvm.normalSafeApiCall
import com.tahoshi.cloudstream_jvm.core.plugins.RepositoryManager.ONLINE_PLUGINS_FOLDER
import com.tahoshi.cloudstream_jvm.core.plugins.RepositoryManager.PREBUILT_REPOSITORIES
import com.tahoshi.cloudstream_jvm.core.plugins.RepositoryManager.downloadPluginToFile
import com.tahoshi.cloudstream_jvm.core.plugins.RepositoryManager.getRepoPlugins
import com.lagradost.cloudstream3.utils.Coroutines.main
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.tahoshi.cloudstream_jvm.core.utils.FileUtils.sanitizeFilename
import com.lagradost.cloudstream3.utils.extractorApis
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.InputStreamReader
import java.net.URLClassLoader
import java.util.*
import com.lagradost.api.Log
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.tahoshi.cloudstream_jvm.core.utils.ToastDuration
import com.tahoshi.cloudstream_jvm.core.utils.ToastManager.showToast
import java.util.zip.ZipFile

//TODO: Pluginleri BasePlugin ile değiştirme, ClassLoaderları sanırım URLClassLoader ile değiştirme
//TODO: context ve activityleri olabildiğince silmek, Android R class'ını yenisi ile değiştirmek, AssetManagerden bir şekilde kurtulmak

// Different keys for local and not since local can be removed at any time without app knowing, hence the local are getting rebuilt on every app start
const val PLUGINS_KEY = "PLUGINS_KEY"
const val PLUGINS_KEY_LOCAL = "PLUGINS_KEY_LOCAL"

const val EXTENSIONS_CHANNEL_ID = "cloudstream3.extensions"
const val EXTENSIONS_CHANNEL_NAME = "Extensions"
const val EXTENSIONS_CHANNEL_DESCRIPT = "Extension notification channel"

// Data class for internal storage
data class PluginData(
    @JsonProperty("internalName") val internalName: String,
    @JsonProperty("url") val url: String?,
    @JsonProperty("isOnline") val isOnline: Boolean,
    @JsonProperty("filePath") val filePath: String,
    @JsonProperty("version") val version: Int,
) {
    fun toSitePlugin(): SitePlugin {
        return SitePlugin(
            this.filePath,
            PROVIDER_STATUS_OK,
            maxOf(1, version),
            1,
            internalName,
            internalName,
            emptyList(),
            File(this.filePath).name,
            null,
            null,
            null,
            null,
            File(this.filePath).length()
        )
    }
}

// This is used as a placeholder / not set version
const val PLUGIN_VERSION_NOT_SET = Int.MIN_VALUE

// This always updates
const val PLUGIN_VERSION_ALWAYS_UPDATE = -1

object PluginManager {
    // Prevent multiple writes at once
    val lock = Mutex()

    const val TAG = "PluginManager"

    private var hasCreatedNotChanel = false

    /**
     * Store data about the plugin for fetching later
     * */
    private suspend fun setPluginData(data: PluginData){
        lock.withLock {
            if (data.isOnline) {
                val plugins = getPluginsOnline()
                val newPlugins = plugins.filter { it.filePath != data.filePath } + data
                setKey(PLUGINS_KEY, newPlugins)
            } else {
                val plugins = getPluginsLocal()
                setKey(PLUGINS_KEY_LOCAL, plugins.filter { it.filePath != data.filePath } + data)
            }
        }
    }

    private suspend fun deletePluginData(data: PluginData?) {
        if (data == null) return
        lock.withLock {
            if (data.isOnline) {
                val plugins = getPluginsOnline().filter { it.url != data.url }
                setKey(PLUGINS_KEY, plugins)
            } else {
                val plugins = getPluginsLocal().filter { it.filePath != data.filePath }
                setKey(PLUGINS_KEY_LOCAL, plugins)
            }
        }
    }

    suspend fun deleteRepositoryData(repositoryPath: String) {
        lock.withLock {
            val plugins = getPluginsOnline().filter {
                !it.filePath.contains(repositoryPath)
            }
            val file = File(repositoryPath)
            normalSafeApiCall {
                if (file.exists()) file.deleteRecursively()
            }
            setKey(PLUGINS_KEY, plugins)
        }
    }

    fun getPluginsOnline(): Array<PluginData> {
        return getKey(PLUGINS_KEY) ?: emptyArray()
    }

    fun getPluginsLocal(): Array<PluginData> {
        return getKey(PLUGINS_KEY_LOCAL) ?: emptyArray()
    }

    private val CLOUD_STREAM_FOLDER = //TODO:Environment yerine direkt relative path kullandım olur inş.  Environment.getExternalStorageDirectory().absolutePath +
         "/Cloudstream3/"

    private val LOCAL_PLUGINS_PATH = CLOUD_STREAM_FOLDER + "plugins"

    var currentlyLoading: String? = null

    // Maps filepath to plugin
    val plugins: MutableMap<String, BasePlugin> =
        LinkedHashMap<String, BasePlugin>()

    // Maps urls to plugin
    val urlPlugins: MutableMap<String, BasePlugin> =
        LinkedHashMap<String, BasePlugin>()

    private val classLoaders: MutableMap<URLClassLoader, BasePlugin> =
        HashMap<URLClassLoader, BasePlugin>()

    var loadedLocalPlugins = false
        private set

    var loadedOnlinePlugins = false
        private set
    private val gson = Gson()

    private suspend fun maybeLoadPlugin(file: File) {
        val name = file.name
        if (file.extension == "zip" || file.extension == "cs3") {
            loadPlugin(
                file,
                PluginData(name, null, false, file.absolutePath, PLUGIN_VERSION_NOT_SET)
            )
        } else {
            Log.i(TAG, "Skipping invalid plugin file: $file")
        }
    }


    // Helper class for updateAllOnlinePluginsAndLoadThem
    data class OnlinePluginData(
        val savedData: PluginData,
        val onlineData: Pair<String, SitePlugin>,
    ) {
        val isOutdated =
            onlineData.second.version > savedData.version || onlineData.second.version == PLUGIN_VERSION_ALWAYS_UPDATE
        val isDisabled = onlineData.second.status == PROVIDER_STATUS_DOWN

        fun validOnlineData(): Boolean {
            return getPluginPath(
                savedData.internalName,
                onlineData.first
            ).absolutePath == savedData.filePath
        }
    }

    // var allCurrentOutDatedPlugins: Set<OnlinePluginData> = emptySet()

    suspend fun loadSinglePlugin(apiName: String): Boolean {
        return (getPluginsOnline().firstOrNull {
            // Most of the time the provider ends with Provider which isn't part of the api name
            it.internalName.replace("provider", "", ignoreCase = true) == apiName
        }
            ?: getPluginsLocal().firstOrNull {
                it.internalName.replace("provider", "", ignoreCase = true) == apiName
            })?.let { savedData ->
            // OnlinePluginData(savedData, onlineData)
            loadPlugin(
                File(savedData.filePath),
                savedData
            )
        } ?: false
    }

    /**
     * Needs to be run before other plugin loading because plugin loading can not be overwritten
     * 1. Gets all online data about the downloaded plugins
     * 2. If disabled do nothing
     * 3. If outdated download and load the plugin
     * 4. Else load the plugin normally
     **/
    fun updateAllOnlinePluginsAndLoadThem() {
        // Load all plugins as fast as possible!
        loadAllOnlinePlugins()
        afterPluginsLoadedEvent.invoke(false)

        val urls = (getKey<Array<RepositoryData>>(REPOSITORIES_KEY)
            ?: emptyArray()) + PREBUILT_REPOSITORIES

        val onlinePlugins = urls.toList().apmap {
            getRepoPlugins(it.url)?.toList() ?: emptyList()
        }.flatten().distinctBy { it.second.url }

        // Iterates over all offline plugins, compares to remote repo and returns the plugins which are outdated
        val outdatedPlugins = getPluginsOnline().map { savedData ->
            onlinePlugins
                .filter { onlineData -> savedData.internalName == onlineData.second.internalName }
                .map { onlineData ->
                    OnlinePluginData(savedData, onlineData)
                }.filter {
                    it.validOnlineData()
                }
        }.flatten().distinctBy { it.onlineData.second.url }

        debugPrint {
            "Outdated plugins: ${outdatedPlugins.filter { it.isOutdated }}"
        }

        val updatedPlugins = mutableListOf<String>()

        outdatedPlugins.apmap { pluginData ->
            if (pluginData.isDisabled) {
                //updatedPlugins.add(activity.getString(R.string.single_plugin_disabled, pluginData.onlineData.second.name))
                unloadPlugin(pluginData.savedData.filePath)
            } else if (pluginData.isOutdated) {
                downloadPlugin(
                    pluginData.onlineData.second.url,
                    pluginData.savedData.internalName,
                    File(pluginData.savedData.filePath),
                    true
                ).let { success ->
                    if (success)
                        updatedPlugins.add(pluginData.onlineData.second.name)
                }
            }
        }

        val message = "Updated ${updatedPlugins.size} plugins"
        showToast(message, ToastDuration.LENGTH_LONG)

        // ioSafe {
        loadedOnlinePlugins = true
        afterPluginsLoadedEvent.invoke(false)
        // }

        Log.i(TAG, "Plugin update done!")
    }

    /**
     * Automatically download plugins not yet existing on local
     * 1. Gets all online data from online plugins repo
     * 2. Fetch all not downloaded plugins
     * 3. Download them and reload plugins
     **/
    fun downloadNotExistingPluginsAndLoad(mode: AutoDownloadMode) {
        val newDownloadPlugins = mutableListOf<String>()
        val urls = (getKey<Array<RepositoryData>>(REPOSITORIES_KEY)
            ?: emptyArray()) + PREBUILT_REPOSITORIES
        val onlinePlugins = urls.toList().apmap {
            getRepoPlugins(it.url)?.toList() ?: emptyList()
        }.flatten().distinctBy { it.second.url }

        val providerLang = hashSetOf<String>("universal") //TODO:Şu app prefs yazılana kadar şimdilik sabit değer verelim  AppContextUtils.getApiProviderLangSettings()
        //Log.i(TAG, "providerLang => ${providerLang.toJson()}")

        // Iterate online repos and returns not downloaded plugins
        val notDownloadedPlugins = onlinePlugins.mapNotNull { onlineData ->
            val sitePlugin = onlineData.second
            val tvtypes = sitePlugin.tvTypes ?: listOf()

            //Don't include empty urls
            if (sitePlugin.url.isBlank()) {
                return@mapNotNull null
            }
            if (sitePlugin.repositoryUrl.isNullOrBlank()) {
                return@mapNotNull null
            }

            //Omit already existing plugins
            if (getPluginPath(sitePlugin.internalName, onlineData.first).exists()) {
                Log.i(TAG, "Skip > ${sitePlugin.internalName}")
                return@mapNotNull null
            }

            //Omit non-NSFW if mode is set to NSFW only
            if (mode == AutoDownloadMode.NsfwOnly) {
                if (!tvtypes.contains(TvType.NSFW.name)) {
                    return@mapNotNull null
                }
            }
            //Omit NSFW, if disabled
            if (!settingsForProvider.enableAdult) {
                if (tvtypes.contains(TvType.NSFW.name)) {
                    return@mapNotNull null
                }
            }

            //Omit lang not selected on language setting
            if (mode == AutoDownloadMode.FilterByLang) {
                val lang = sitePlugin.language ?: return@mapNotNull null
                //If set to 'universal', don't skip any language
                if (!providerLang.contains(AllLanguagesName) && !providerLang.contains(lang)) {
                    return@mapNotNull null
                }
                //Log.i(TAG, "sitePlugin lang => $lang")
            }

            val savedData = PluginData(
                url = sitePlugin.url,
                internalName = sitePlugin.internalName,
                isOnline = true,
                filePath = "",
                version = sitePlugin.version
            )
            OnlinePluginData(savedData, onlineData)
        }
        //Log.i(TAG, "notDownloadedPlugins => ${notDownloadedPlugins.toJson()}")

        notDownloadedPlugins.apmap { pluginData ->
            downloadPlugin(
                pluginData.onlineData.second.url,
                pluginData.savedData.internalName,
                pluginData.onlineData.first,
                !pluginData.isDisabled
            ).let { success ->
                if (success)
                    newDownloadPlugins.add(pluginData.onlineData.second.name)
            }
        }

        main {
            //TODO: UIText implement edilene kadar ingilizce
            /*
            val uitext = txt(R.string.plugins_downloaded, newDownloadPlugins.size)
            createNotification(uitext, newDownloadPlugins)
             */
            val message = "Downloaded: ${newDownloadPlugins.size}"
            showToast(message, ToastDuration.LENGTH_LONG)
        }

        // ioSafe {
        afterPluginsLoadedEvent.invoke(false)
        // }

        Log.i(TAG, "Plugin download done!")
    }

    /**
     * Use updateAllOnlinePluginsAndLoadThem
     * */
    fun loadAllOnlinePlugins() {
        // Load all plugins as fast as possible!
        (getPluginsOnline()).toList().apmap { pluginData ->
            loadPlugin(
                File(pluginData.filePath),
                pluginData
            )
        }
    }

    /**
     * Reloads all local plugins and forces a page update, used for hot reloading with deployWithAdb
     **/
    fun hotReloadAllLocalPlugins() {
        Log.d(TAG, "Reloading all local plugins!")
        getPluginsLocal().forEach {
            unloadPlugin(it.filePath)
        }
        loadAllLocalPlugins(true)
    }

    /**
     * @param forceReload see afterPluginsLoadedEvent, basically a way to load all local plugins
     * and reload all pages even if they are previously valid
     **/
    fun loadAllLocalPlugins(forceReload: Boolean) {
        val dir = File(LOCAL_PLUGINS_PATH)

        if (!dir.exists()) {
            val res = dir.mkdirs()
            if (!res) {
                Log.w(TAG, "Failed to create local directories")
                return
            }
        }

        val sortedPlugins = dir.listFiles()
        // Always sort plugins alphabetically for reproducible results

        Log.d(TAG, "Files in '$LOCAL_PLUGINS_PATH' folder: $sortedPlugins")

        sortedPlugins?.sortedBy { it.name }?.apmap { file ->
            maybeLoadPlugin(file)
        }

        loadedLocalPlugins = true
        afterPluginsLoadedEvent.invoke(forceReload)
    }

    /**
     * This can be used to override any extension loading to fix crashes!
     * @return true if safe mode file is present
     **/
    fun checkSafeModeFile(): Boolean {
        return normalSafeApiCall {
            val folder = File(CLOUD_STREAM_FOLDER)
            if (!folder.exists()) return@normalSafeApiCall false
            val files = folder.listFiles { _, name ->
                name.equals("safe", ignoreCase = true)
            }
            files?.any()
        } ?: false
    }

    /**
     * @return True if successful, false if not
     * */
    private suspend fun loadPlugin(file: File, data: PluginData): Boolean {
        val fileName = file.nameWithoutExtension
        val filePath = file.absolutePath
        currentlyLoading = fileName
        Log.i(TAG, "Loading plugin: $data")

        return try {
            // in case of android 14 then
            try {
                File(filePath).setReadOnly()
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to set dex as readonly")
                logError(t)
            }

            lateinit var loader : URLClassLoader
            var manifest: BasePlugin.Manifest

            ZipFile(file).use { zip ->
                val manifestZipEntry = zip.getEntry("manifest.json")
                val basePluginZipEntry = zip.getEntry("base.jar")

                zip.getInputStream(manifestZipEntry).use { stream ->
                    if (stream == null) {
                        Log.e(TAG, "Failed to load plugin  $fileName: No manifest found")
                        return false
                    }
                    InputStreamReader(stream).use { reader ->
                        manifest = gson.fromJson(
                            reader,
                            BasePlugin.Manifest::class.java
                        )
                    }
                }

                val tempJarFile = manifest.pluginClassName?.let {
                    File.createTempFile(it, ".jar").apply {
                        deleteOnExit()
                        outputStream().use { output ->
                            zip.getInputStream(basePluginZipEntry).use { input ->
                                input.copyTo(output)
                            }
                        }
                    }
                }

                if (tempJarFile != null) {
                    loader = URLClassLoader(arrayOf(tempJarFile.toURI().toURL()), this::class.java.classLoader)
                }
            }

            val name: String = manifest.name ?: "NO NAME".also {
                Log.d(TAG, "No manifest name for ${data.internalName}")
            }
            val version: Int = manifest.version ?: PLUGIN_VERSION_NOT_SET.also {
                Log.d(TAG, "No manifest version for ${data.internalName}")
            }

            @Suppress("UNCHECKED_CAST")
            val pluginClass: Class<*> =
                loader.loadClass(manifest.pluginClassName) as Class<out BasePlugin?>
            val pluginInstance: BasePlugin =
                pluginClass.getDeclaredConstructor().newInstance() as BasePlugin

            // Sets with the proper version
            setPluginData(data.copy(version = version))

            if (plugins.containsKey(filePath)) {
                Log.i(TAG, "Plugin with name $name already exists")
                return true
            }

            pluginInstance.filename = file.absolutePath
            //TODO: manifest requiresResources, şimdilik burayı kapatıyorum
            /*
            if (manifest.requiresResources) {
                Log.d(TAG, "Loading resources for ${data.internalName}")
                // based on https://stackoverflow.com/questions/7483568/dynamic-resource-loading-from-other-apk
                val assets = AssetManager::class.java.getDeclaredConstructor().newInstance()
                val addAssetPath =
                    AssetManager::class.java.getMethod("addAssetPath", String::class.java)
                addAssetPath.invoke(assets, file.absolutePath)

                @Suppress("DEPRECATION")
                pluginInstance.resources = Resources(
                    assets,
                    context.resources.displayMetrics,
                    context.resources.configuration
                )
            }
             */
            plugins[filePath] = pluginInstance
            classLoaders[loader] = pluginInstance
            urlPlugins[data.url ?: filePath] = pluginInstance
            pluginInstance.load()
            Log.i(TAG, "Loaded plugin ${data.internalName} successfully")
            currentlyLoading = null
            true
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to load $file")
            //TODO:UIText implement edilene kadar ingilizce
            showToast(
                //context.getString(R.string.plugin_load_fail).format(fileName),
                "Couldn't load: ${fileName}",
                ToastDuration.LENGTH_LONG
            )
            currentlyLoading = null
            false
        }
    }

    fun unloadPlugin(absolutePath: String) {
        Log.i(TAG, "Unloading plugin: $absolutePath")
        val plugin = plugins[absolutePath]
        if (plugin == null) {
            Log.w(TAG, "Couldn't find plugin $absolutePath")
            return
        }

        try {
            plugin.beforeUnload()
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to run beforeUnload $absolutePath")
        }

        // remove all registered apis
        synchronized(APIHolder.apis) {
            APIHolder.apis.filter { api -> api.sourcePlugin == plugin.filename }.forEach {
                removePluginMapping(it)
            }
        }
        synchronized(APIHolder.allProviders) {
            APIHolder.allProviders.removeIf { provider: MainAPI -> provider.sourcePlugin == plugin.filename }
        }

        extractorApis.removeIf { provider: ExtractorApi -> provider.sourcePlugin == plugin.filename }

        /* TODO:Video click action holder ne işe yarıyor şu an bilmiyorum o yüzden sonra bakarım
        synchronized(VideoClickActionHolder.allVideoClickActions) {
            VideoClickActionHolder.allVideoClickActions.removeIf { action: VideoClickAction -> action.sourcePlugin == plugin.filename }
        }

         */

        classLoaders.values.removeIf { v -> v == plugin }

        plugins.remove(absolutePath)
        urlPlugins.values.removeIf { v -> v == plugin }
    }

    /**
     * Spits out a unique and safe filename based on name.
     * Used for repo folders (using repo url) and plugin file names (using internalName)
     * */
    fun getPluginSanitizedFileName(name: String): String {
        return sanitizeFilename(
            name,
            true
        ) + "." + name.hashCode()
    }

    /**
     * This should not be changed as it is used to also detect if a plugin is installed!
     **/
    fun getPluginPath(
        internalName: String,
        repositoryUrl: String
    ): File {
        val folderName = getPluginSanitizedFileName(repositoryUrl) // Guaranteed unique
        val fileName = getPluginSanitizedFileName(internalName)
        return File("${ONLINE_PLUGINS_FOLDER}/${folderName}/$fileName.cs3")
    }

    suspend fun downloadPlugin(
        pluginUrl: String,
        internalName: String,
        repositoryUrl: String,
        loadPlugin: Boolean
    ): Boolean {
        val file = getPluginPath(internalName, repositoryUrl)
        return downloadPlugin(pluginUrl, internalName, file, loadPlugin)
    }

    suspend fun downloadPlugin(
        pluginUrl: String,
        internalName: String,
        file: File,
        loadPlugin: Boolean
    ): Boolean {
        try {
            Log.d(TAG, "Downloading plugin: $pluginUrl to ${file.absolutePath}")
            // The plugin file needs to be salted with the repository url hash as to allow multiple repositories with the same internal plugin names
            val newFile = downloadPluginToFile(pluginUrl, file) ?: return false

            val data = PluginData(
                internalName,
                pluginUrl,
                true,
                newFile.absolutePath,
                PLUGIN_VERSION_NOT_SET
            )

            return if (loadPlugin) {
                unloadPlugin(file.absolutePath)
                loadPlugin(
                    newFile,
                    data
                )
            } else {
                setPluginData(data)
                true
            }
        } catch (e: Exception) {
            logError(e)
            return false
        }
    }

    suspend fun deletePlugin(file: File): Boolean {
        val list =
            (getPluginsLocal() + getPluginsOnline()).filter { it.filePath == file.absolutePath }

        return try {
            if (File(file.absolutePath).delete()) {
                unloadPlugin(file.absolutePath)
                list.forEach { deletePluginData(it) }
                return true
            }
            false
        } catch (e: Exception) {
            false
        }
    }

    /* TODO: Notifications diye bir şey şu anlık için yok, register yapmamız gerekmiyor en azından
    private fun Context.createNotificationChannel() {
        hasCreatedNotChanel = true
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = EXTENSIONS_CHANNEL_NAME //getString(R.string.channel_name)
            val descriptionText =
                EXTENSIONS_CHANNEL_DESCRIPT//getString(R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(EXTENSIONS_CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            // Register the channel with the system
            val notificationManager: NotificationManager =
                this.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
     */

    /* TODO: Şimdilik remove ettim
    private fun createNotification(
        uitext: UiText,
        extensions: List<String>
    ) {

        /* TODO:Buranın içeriği değiştirilip web için yazılması gerekiyor, ya da event mantığı da olabilir
        try {

            if (extensions.isEmpty()) return null

            val content = extensions.joinToString(", ")
//        main { // DON'T WANT TO SLOW IT DOWN
            val builder = NotificationCompat.Builder(context, EXTENSIONS_CHANNEL_ID)
                .setAutoCancel(false)
                .setColorized(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setColor(context.colorFromAttribute(R.attr.colorPrimary))
                .setContentTitle(uitext.asString(context))
                //.setContentTitle(context.getString(title, extensionNames.size))
                .setSmallIcon(R.drawable.ic_baseline_extension_24)
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(content)
                )
                .setContentText(content)

            if (!hasCreatedNotChanel) {
                context.createNotificationChannel()
            }

            val notification = builder.build()
            // notificationId is a unique int for each notification that you must define
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                NotificationManagerCompat.from(context)
                    .notify((System.currentTimeMillis() / 1000).toInt(), notification)
            }
            return notification
        } catch (e: Exception) {
            logError(e)
            return null
        }

         */
        showToast("${uitext.toString()}: ${extensions.joinToString(separator = ",") { it }}",ToastDuration.LENGTH_LONG)
    }

     */
}
