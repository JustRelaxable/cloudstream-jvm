package com.tahoshi.cloudstream_jvm.core.utils

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.tahoshi.cloudstream_jvm.core.AcraApplication.Companion.getKeyClass
import com.tahoshi.cloudstream_jvm.core.AcraApplication.Companion.removeKey
import com.tahoshi.cloudstream_jvm.core.AcraApplication.Companion.setKeyClass
import com.lagradost.cloudstream3.mvvm.logError
import java.io.File
import java.util.Properties
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

const val DOWNLOAD_HEADER_CACHE = "download_header_cache"

//const val WATCH_HEADER_CACHE = "watch_header_cache"
const val DOWNLOAD_EPISODE_CACHE = "download_episode_cache"
const val VIDEO_PLAYER_BRIGHTNESS = "video_player_alpha_key"
const val USER_SELECTED_HOMEPAGE_API = "home_api_used"
const val USER_PROVIDER_API = "user_custom_sites"

const val PREFERENCES_NAME = "rebuild_preference"

// TODO degelgate by value for get & set

class PreferenceDelegate<T : Any>(
    val key: String, val default: T //, private val klass: KClass<T>
) {
    private val klass: KClass<out T> = default::class
    // simple cache to make it not get the key every time it is accessed, however this requires
    // that ONLY this changes the key
    private var cache: T? = null

    operator fun getValue(self: Any?, property: KProperty<*>) =
        cache ?: getKeyClass(key, klass.java).also { newCache -> cache = newCache } ?: default

    operator fun setValue(
        self: Any?,
        property: KProperty<*>,
        t: T?
    ) {
        cache = t
        if (t == null) {
            removeKey(key)
        } else {
            setKeyClass(key, t)
        }
    }
}

/** When inserting many keys use this function, this is because apply for every key is very expensive on memory */
/* TODO: Muhtemelen editöre ihtiyacımız olmayacak diye düşünüyorum
data class Editor(
    val editor : SharedPreferences.Editor
) {
    /** Always remember to call apply after */
    fun<T> setKeyRaw(path: String, value: T) {
        @Suppress("UNCHECKED_CAST")
        if (isStringSet(value)) {
            editor.putStringSet(path, value as Set<String>)
        } else {
            when (value) {
                is Boolean -> editor.putBoolean(path, value)
                is Int -> editor.putInt(path, value)
                is String -> editor.putString(path, value)
                is Float -> editor.putFloat(path, value)
                is Long -> editor.putLong(path, value)
            }
        }
    }

    private fun isStringSet(value: Any?) : Boolean {
        if (value is Set<*>) {
            return value.filterIsInstance<String>().size == value.size
        }
        return false
    }

    fun apply() {
        editor.apply()
        System.gc()
    }
}

 */

object DataStore {
    val mapper: JsonMapper = JsonMapper.builder().addModule(kotlinModule())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).build()

    val properties = Properties()

    init {
        val preferencesFile = File(PREFERENCES_NAME)
        if(preferencesFile.exists()) {
            properties.load(preferencesFile.inputStream())
        }
        else{
            val fileCreationResult = preferencesFile.createNewFile()
            if(fileCreationResult){
                properties.load(preferencesFile.inputStream())
            }
            else{
                throw Exception("Couldn't create preferences file")
            }
        }
    }

    fun getSharedPrefs(): Properties {
        return properties
    }

    fun getFolderName(folder: String, path: String): String {
        return "${folder}/${path}"
    }

    /* TODO: Editör tarzı bir şeylere çok yüksek ihtimal ihtiyacımız olmayacak
    fun editor(context : Context, isEditingAppSettings: Boolean = false) : Editor {
        val editor: SharedPreferences.Editor =
            if (isEditingAppSettings) context.getDefaultSharedPrefs().edit() else context.getSharedPrefs().edit()
        return Editor(editor)
    }
     */

    /* TODO: getDefaultSharedPrefs belki ihtiyacım olabilecek bir şey, o yüzden dikkat edelim öyle bir şey görürsek
    fun Context.getDefaultSharedPrefs(): SharedPreferences {
        return PreferenceManager.getDefaultSharedPreferences(this)
    }
     */

    fun getKeys(folder: String): List<String> {
        return getSharedPrefs().keys().toList().map { it as String }.filter { it.startsWith(folder) }
    }

    fun removeKey(folder: String, path: String) {
        removeKey(getFolderName(folder, path))
    }

    fun containsKey(folder: String, path: String): Boolean {
        return containsKey(getFolderName(folder, path))
    }

    fun containsKey(path: String): Boolean {
        val prefs = getSharedPrefs()
        return prefs.contains(path)
    }

    fun removeKey(path: String) {
        try {
            val prefs = getSharedPrefs()
            if (prefs.contains(path)) {
                prefs.remove(path)
                prefs.store(File(PREFERENCES_NAME).outputStream(),"")
            }
        } catch (e: Exception) {
            logError(e)
        }
    }

    fun removeKeys(folder: String): Int {
        val keys = getKeys("$folder/")
        keys.forEach { value ->
            removeKey(value)
        }
        return keys.size
    }

    fun <T> setKey(path: String, value: T) {
        try {
            getSharedPrefs().setProperty(path, mapper.writeValueAsString(value))
            getSharedPrefs().store(File(PREFERENCES_NAME).outputStream(),"")
        } catch (e: Exception) {
            logError(e)
        }
    }

    fun <T> getKey(path: String, valueType: Class<T>): T? {
        try {
            val json: String = getSharedPrefs().getProperty(path, null) ?: return null
            return json.toKotlinObject(valueType)
        } catch (e: Exception) {
            return null
        }
    }

    fun <T> setKey(folder: String, path: String, value: T) {
        setKey(getFolderName(folder, path), value)
    }

    inline fun <reified T : Any> String.toKotlinObject(): T {
        return mapper.readValue(this, T::class.java)
    }

    fun <T> String.toKotlinObject(valueType: Class<T>): T {
        return mapper.readValue(this, valueType)
    }

    // GET KEY GIVEN PATH AND DEFAULT VALUE, NULL IF ERROR
    inline fun <reified T : Any> getKey(path: String, defVal: T?): T? {
        try {
            val json: String = getSharedPrefs().getProperty(path, null) ?: return defVal
            return json.toKotlinObject()
        } catch (e: Exception) {
            return null
        }
    }

    inline fun <reified T : Any> getKey(path: String): T? {
        return getKey(path, null)
    }

    inline fun <reified T : Any> getKey(folder: String, path: String): T? {
        return getKey(getFolderName(folder, path), null)
    }

    inline fun <reified T : Any> getKey(folder: String, path: String, defVal: T?): T? {
        return getKey(getFolderName(folder, path), defVal) ?: defVal
    }
}