package com.tahoshi.cloudstream_jvm.webserver.data

data class ApiResponse<T>(
    val status: String,
    val message: String? = null,
    val data: T? = null
)