package com.tahoshi.cloudstream_jvm.webserver.data

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorLink

data class LoadLinksResponse(
    val subtitleFiles: List<SubtitleFile>,
    val extractorLinks : List<ExtractorLink>
)