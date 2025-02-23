package com.tahoshi.cloudstream_jvm.webserver

import com.lagradost.api.Log
import jakarta.servlet.http.HttpServletRequest
import org.apache.http.HttpResponse
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.HttpClients
import org.apache.http.util.EntityUtils
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.io.IOException

@RestController
@RequestMapping("/proxy")
class ProxyController {
    val TAG = "ProxyController"

    @GetMapping("/**")
    fun proxy(request: HttpServletRequest): ResponseEntity<ByteArray> {
        val path = StringBuilder()
            .append(request.scheme) // e.g., "http" or "https"
            .append("://")
            .append(request.serverName) // e.g., "example.com"
            .append(":")
            .append(request.serverPort) // e.g., 8080
            .append(request.requestURI) // e.g., "/some/path"
            .append(if (request.queryString != null) "?${request.queryString}" else "") // e.g., "?param=value"
            .toString()
        val headerList = request.headerNames.toList().filter { it.startsWith("c-") }
        var url = path.substringAfter("/proxy/")

        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://$url"
        }

        Log.i(TAG,"Proxying request to: $url")

        try {
            HttpClients.createDefault().use { httpClient ->
                val httpGet = HttpGet(url)
                headerList.forEach {
                    val realHeaderName = it.split("c-")[1]
                    val realHeaderValue = request.getHeader(it)
                    httpGet.setHeader(realHeaderName,realHeaderValue)
                }
                httpGet.setHeader("User-Agent","Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Safari/537.36")
                val requestHeaders = httpGet.allHeaders
                println("Request headers:${requestHeaders.toList()}")
                val httpResponse: HttpResponse = httpClient.execute(httpGet)

                val status = HttpStatus.valueOf(httpResponse.statusLine.statusCode)

                val entity = httpResponse.entity
                return if (entity != null) {
                    ResponseEntity.status(status)
                        .header("Content-Type", entity.contentType.value)
                        .body(EntityUtils.toByteArray(entity))
                } else {
                    ResponseEntity.status(status).body(ByteArray(0))
                }
            }
        } catch (e: IOException) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(("Failed to fetch the resource: " + e.message).toByteArray())
        }
    }
}