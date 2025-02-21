package com.tahoshi.cloudstream_jvm.webserver.data

data class APIMainPagePaginationRequest(
    val provider: String,
    val page: Int,
    val name: String,
    val data: String
)