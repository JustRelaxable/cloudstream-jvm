package com.tahoshi.cloudstream_jvm.core

import com.lagradost.cloudstream3.APIHolder
import com.tahoshi.cloudstream_jvm.core.plugins.PluginManager
import com.tahoshi.cloudstream_jvm.core.plugins.RepositoryData
import com.tahoshi.cloudstream_jvm.core.plugins.RepositoryManager
import kotlinx.coroutines.runBlocking

object Testing {
    fun loadTestCrossPlugin() {
        val repoUrl = "https://raw.githubusercontent.com/keyiflerolsun/Kekik-cloudstream/refs/heads/master/repo.json"
        val pluginUrl = "https://github.com/JustRelaxable/Kekik-cloudstream/raw/refs/heads/cross-platform-test/InatBox.cs3"
        runBlocking {
            RepositoryManager.addRepository(
                RepositoryData(
                    name = "KekikAkademi",
                    url = repoUrl
                )
            )
            val repositoriesList = RepositoryManager.getRepositories().toList()
            val repoPlugins = RepositoryManager.getRepoPlugins(repoUrl)
            PluginManager.downloadPlugin(pluginUrl = pluginUrl, internalName = "InatBox", repositoryUrl = repoUrl,true)
            val inatboxApi = APIHolder.getApiFromNameNull("InatBox")
            println(inatboxApi)
        }
    }
}