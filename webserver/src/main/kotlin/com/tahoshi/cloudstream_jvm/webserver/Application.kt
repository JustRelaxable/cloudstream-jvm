package com.tahoshi.cloudstream_jvm.webserver

import com.tahoshi.cloudstream_jvm.core.plugins.PluginManager
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
open class Application

fun main(args: Array<String>) {
    //Testing.loadTestCrossPlugin()
    PluginManager.updateAllOnlinePluginsAndLoadThem()
    runApplication<Application>(*args)
}