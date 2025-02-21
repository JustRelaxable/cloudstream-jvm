package com.tahoshi.cloudstream_jvm.webserver

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.tahoshi.cloudstream_jvm.core.plugins.PluginManager
import com.tahoshi.cloudstream_jvm.core.plugins.RepositoryData
import com.tahoshi.cloudstream_jvm.core.plugins.RepositoryManager
import com.tahoshi.cloudstream_jvm.core.plugins.SitePlugin
import com.tahoshi.cloudstream_jvm.webserver.data.*
import kotlinx.coroutines.runBlocking
import org.springframework.web.bind.annotation.*
import java.io.File


@RestController
@RequestMapping("/api")
class ApiController {
    @GetMapping("/hello")
    fun sayHello(): String {
        return "Hello from Java!"
    }

    @PostMapping("/add-repository")
    fun addRepository(@RequestBody repositoryData: RepositoryData): ApiResponse<List<RepositoryData>> = runBlocking {
        val repository = RepositoryManager.parseRepository(repositoryData.url)
        if (repository != null) {
            RepositoryManager.addRepository(repositoryData)
            ApiResponse(status = "success", message = null, data = RepositoryManager.getRepositories().toList())
        } else {
            ApiResponse(status = "error", message = "Unable to parse repository, please check the given URL")
        }
    }

    @PostMapping("/remove-repository")
    fun removeRepository(@RequestBody repositoryData: RepositoryData) : ApiResponse<List<RepositoryData>> = runBlocking {
        RepositoryManager.removeRepository(repositoryData)
        ApiResponse(status = "success", message = null, data = RepositoryManager.getRepositories().toList())
    }

    @GetMapping("/repositories")
    fun getRepositories(): ApiResponse<List<RepositoryData>> = runBlocking {
        ApiResponse(status = "success", message = null, data = RepositoryManager.getRepositories().toList())
    }

    @PostMapping("/repository-plugins")
    fun getRepositoryPlugins(@RequestBody repositoryData: RepositoryData): ApiResponse<List<Pair<String, SitePlugin>>> =
        runBlocking {
            val result = RepositoryManager.getRepoPlugins(repositoryData.url)
            if (result != null) {
                ApiResponse(status = "success", message = null, data = result)
            } else {
                ApiResponse(status = "error", message = "Unable to parse repository plugins", data = null)
            }
        }

    @PostMapping("/download-plugin")
    fun downloadPlugin(@RequestBody onlinePluginIdentifier: OnlinePluginIdentifier) = runBlocking {
        val result = PluginManager.downloadPlugin(pluginUrl = onlinePluginIdentifier.pluginUrl, internalName = onlinePluginIdentifier.internalName, repositoryUrl = onlinePluginIdentifier.repositoryUrl,true)
        if(result){
            val onlinePlugins = PluginManager.getPluginsOnline()
            ApiResponse(status = "success", message = null, data = onlinePlugins)
        }else{
            ApiResponse(status = "error", message = "Something went wrong while loading the plugin", data = null)
        }
    }

    @PostMapping("/remove-plugin")
    fun removePlugin(@RequestBody onlinePluginIdentifier: OnlinePluginIdentifier) = runBlocking {
        val onlinePlugins = PluginManager.getPluginsOnline()

        val toBeRemovedPlugin = onlinePlugins.single { it.url == onlinePluginIdentifier.pluginUrl }
        val result = PluginManager.deletePlugin(File(toBeRemovedPlugin.filePath))

        if(result){
            ApiResponse(status = "success", message = null, data = PluginManager.getPluginsOnline())
        }else{
            ApiResponse(status = "error", message = "Something went wrong while deleting the plugin", data = PluginManager.getPluginsOnline())
        }
    }

    @GetMapping("/providers")
    fun getProviders(): ApiResponse<List<String>> {
        val allProviders = APIHolder.allProviders.map { it.name }
        return ApiResponse(status = "success", message = null, data = allProviders)
    }

    @GetMapping("/mainpagelist")
    fun getMainPageDataListOfProvider(@RequestParam provider: String): ApiResponse<List<MainPageData>> = runBlocking {
        val mainAPI = APIHolder.getApiFromNameNull(provider)
        if (mainAPI != null) {
            ApiResponse(status = "success", message = null, mainAPI.mainPage)
        } else {
            ApiResponse(status = "error", message = "MainAPI instance not found")
        }
    }

    @PostMapping("/mainpage")
    fun getMainPageOfProvider(@RequestBody apiMainPagePaginationRequest: APIMainPagePaginationRequest): ApiResponse<HomePageResponse> =
        runBlocking {
            val mainAPI = APIHolder.getApiFromNameNull(apiMainPagePaginationRequest.provider)
            if (mainAPI != null) {
                val homePageResponse = mainAPI.getMainPage(
                    page = apiMainPagePaginationRequest.page, request = MainPageRequest(
                        name = apiMainPagePaginationRequest.name,
                        data = apiMainPagePaginationRequest.data,
                        horizontalImages = false
                    )
                )
                ApiResponse(status = "success", message = null, homePageResponse)
            } else {
                ApiResponse(status = "error", message = "MainAPI instance not found")
            }
        }

    @PostMapping("/load-content-details")
    fun loadContentDetails(@RequestBody apiLoadRequest: APILoadRequest): ApiResponse<LoadResponse> = runBlocking {
        val mainAPI = APIHolder.getApiFromNameNull(apiLoadRequest.provider)
        if (mainAPI != null) {
            val loadResponse = mainAPI.load(apiLoadRequest.data)
            ApiResponse(status = "success", message = null, loadResponse)
        } else {
            ApiResponse(status = "error", message = "MainAPI instance not found")
        }
    }

    @PostMapping("load-content-links")
    fun loadContentLinks(@RequestBody apiLoadRequest: APILoadRequest): ApiResponse<LoadLinksResponse> = runBlocking {
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
                ApiResponse(
                    status = "success", message = null, data = LoadLinksResponse(
                        subtitleFiles = subtitleFileList, extractorLinks = extractorLinkList
                    )
                )
            } else {
                ApiResponse(status = "error", message = "No links were found")
            }
        } else {
            ApiResponse(status = "error", message = "MainAPI instance not found")
        }
    }
}