package com.tahoshi.cloudstream_jvm.webserver

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.tahoshi.cloudstream_jvm.webserver.data.APILoadRequest
import com.tahoshi.cloudstream_jvm.webserver.data.APIMainPagePaginationRequest
import com.tahoshi.cloudstream_jvm.webserver.data.ApiResponse
import com.tahoshi.cloudstream_jvm.webserver.data.LoadLinksResponse
import jakarta.servlet.http.HttpServletRequest
import kotlinx.coroutines.runBlocking
import org.apache.http.HttpResponse
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.HttpClients
import org.apache.http.util.EntityUtils
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.io.IOException


@RestController
@RequestMapping("/api")
class ApiController {
    @GetMapping("/hello")
    fun sayHello(): String {
        return "Hello from Java!"
    }

    @GetMapping("/providers")
    fun getProviders(): String {
        val allProviders = APIHolder.allProviders.map { it.name }
        return allProviders.toJson()
    }

    @GetMapping("/mainpagelist")
    fun getMainPageDataListOfProvider(@RequestParam provider: String): ResponseEntity<ApiResponse<List<MainPageData>>> =
        runBlocking {
            val mainAPI = APIHolder.getApiFromNameNull(provider)
            if (mainAPI != null) {
                ResponseEntity.ok(ApiResponse(status = "success", message = null, mainAPI.mainPage))
            } else {
                ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse(status = "error", message = "MainAPI instance not found"))
            }
        }

    @PostMapping("/mainpage")
    fun getMainPageOfProvider(@RequestBody apiMainPagePaginationRequest: APIMainPagePaginationRequest): ResponseEntity<ApiResponse<HomePageResponse>> =
        runBlocking {
            val mainAPI = APIHolder.getApiFromNameNull(apiMainPagePaginationRequest.provider)
            if (mainAPI != null) {
                val homePageResponse = mainAPI.getMainPage(
                    page = apiMainPagePaginationRequest.page,
                    request = MainPageRequest(
                        name = apiMainPagePaginationRequest.name,
                        data = apiMainPagePaginationRequest.data,
                        horizontalImages = false
                    )
                )
                ResponseEntity.ok(ApiResponse(status = "success", message = null, homePageResponse))
            } else {
                ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse(status = "error", message = "MainAPI instance not found"))
            }
        }

    @PostMapping("/load-content-details")
    fun loadContentDetails(@RequestBody apiLoadRequest: APILoadRequest): ResponseEntity<ApiResponse<LoadResponse>> =
        runBlocking {
            val mainAPI = APIHolder.getApiFromNameNull(apiLoadRequest.provider)
            if (mainAPI != null) {
                val loadResponse = mainAPI.load(apiLoadRequest.data)
                ResponseEntity.ok(ApiResponse(status = "success", message = null, loadResponse))
            } else {
                ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse(status = "error", message = "MainAPI instance not found"))
            }
        }

    @PostMapping("load-content-links")
    fun loadContentLinks(@RequestBody apiLoadRequest: APILoadRequest): ResponseEntity<ApiResponse<LoadLinksResponse>> =
        runBlocking {
            val mainAPI = APIHolder.getApiFromNameNull(apiLoadRequest.provider)
            if (mainAPI != null) {
                val subtitleFileList = mutableListOf<SubtitleFile>()
                val extractorLinkList = mutableListOf<ExtractorLink>()
                val completed = mainAPI.loadLinks(
                    data = apiLoadRequest.data,
                    isCasting = false,
                    { subtitleFileList.add(it) },
                    { extractorLinkList.add(it) })
                if (completed) {
                    ResponseEntity.ok(
                        ApiResponse(
                            status = "success",
                            message = null,
                            data = LoadLinksResponse(
                                subtitleFiles = subtitleFileList,
                                extractorLinks = extractorLinkList
                            )
                        )
                    )
                } else {
                    ResponseEntity.status(HttpStatus.NO_CONTENT)
                        .body(ApiResponse(status = "error", message = "No links were found"))
                }
            } else {
                ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse(status = "error", message = "MainAPI instance not found"))
            }
        }
}