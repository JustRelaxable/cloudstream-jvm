package com.tahoshi.cloudstream_jvm.webserver

import com.tahoshi.cloudstream_jvm.core.Testing
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
open class Application

fun main(args: Array<String>) {
    Testing.loadTestCrossPlugin()
    runApplication<Application>(*args)
}