package com.tahoshi.cloudstream_jvm.core.utils

object FileUtils {
    private const val RESERVED_CHARS = "|\\?*<\":>+[]/\'"
    fun sanitizeFilename(name: String, removeSpaces: Boolean = false): String {
        var tempName = name
        for (c in RESERVED_CHARS) {
            tempName = tempName.replace(c, ' ')
        }
        if (removeSpaces) tempName = tempName.replace(" ", "")
        return tempName.replace("  ", " ").trim(' ')
    }
}