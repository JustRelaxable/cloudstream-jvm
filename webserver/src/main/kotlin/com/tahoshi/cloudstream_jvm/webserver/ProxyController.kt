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
        val path = request.requestURI
        var url = path.substringAfter("/proxy/")

        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://$url"
        }

        Log.i(TAG,"Proxying request to: $url")

        try {
            HttpClients.createDefault().use { httpClient ->
                val httpGet = HttpGet(url)
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