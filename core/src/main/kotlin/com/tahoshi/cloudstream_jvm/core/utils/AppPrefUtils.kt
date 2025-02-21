package com.tahoshi.cloudstream_jvm.core.utils

import java.io.File
import java.util.Properties

object AppPrefUtils {
    private const val APP_PREFERENCES = "app_preference"

    private val properties = Properties()

    init {
        val preferencesFile = File(APP_PREFERENCES)
        if(preferencesFile.exists()) {
            DataStore.properties.load(preferencesFile.inputStream())
        }
        else{
            val fileCreationResult = preferencesFile.createNewFile()
            if(fileCreationResult){
                DataStore.properties.load(preferencesFile.inputStream())
            }
            else{
                throw Exception("Couldn't create preferences file")
            }
        }
    }
}