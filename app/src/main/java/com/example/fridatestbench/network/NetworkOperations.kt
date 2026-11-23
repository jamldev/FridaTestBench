package com.example.fridatestbench.network

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

data class NetworkRequestSnapshot(
    val transport: String,
    val url: String,
    val statusCode: Int?,
    val urlPayloadJson: JSONObject?,
    val error: String?,
)

data class NetworkOperationsSnapshot(
    val requests: List<NetworkRequestSnapshot>,
) {
    companion object {
        fun pending(): NetworkOperationsSnapshot = NetworkOperationsSnapshot(emptyList())
    }
}

/**
 * Repeats the same HTTPS GET through URLConnection and a raw TLS socket.
 * The explicit methods and transports are intentionally kept separate so they
 * are straightforward instrumentation targets.
 */
class NetworkOperations {

    fun run(): NetworkOperationsSnapshot {
        val requests = listOf(
            capture(TRANSPORT_URL_CONNECTION, API_URL, ::getWithOpenConnection),
            capture(TRANSPORT_TLS_SOCKET, API_URL, ::getWithSocket),
        )
        return NetworkOperationsSnapshot(requests)
    }

    fun getWithOpenConnection(url: String): HttpResponse {
        val connection = URL(url).openConnection() as HttpsURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", USER_AGENT)

            val statusCode = connection.responseCode
            val responseStream = if (statusCode >= 400) {
                connection.errorStream
            } else {
                connection.inputStream
            }
            val body = responseStream
                ?.bufferedReader(StandardCharsets.UTF_8)
                ?.use { it.readText() }
                .orEmpty()

            HttpResponse(statusCode = statusCode, body = body)
        } finally {
            connection.disconnect()
        }
    }

    fun getWithSocket(url: String): HttpResponse {
        val target = URL(url)
        require(target.protocol.equals("https", ignoreCase = true)) {
            "Only HTTPS URLs are supported"
        }

        val port = if (target.port == -1) DEFAULT_HTTPS_PORT else target.port
        val socket = SSLSocketFactory
            .getDefault()
            .createSocket(target.host, port) as SSLSocket

        socket.use { tlsSocket ->
            tlsSocket.soTimeout = TIMEOUT_MILLIS
            tlsSocket.enabledProtocols = tlsSocket.supportedProtocols
                .filter { it == "TLSv1.2" || it == "TLSv1.3" }
                .toTypedArray()
            tlsSocket.sslParameters = tlsSocket.sslParameters.apply {
                endpointIdentificationAlgorithm = "HTTPS"
            }
            tlsSocket.startHandshake()

            val path = buildString {
                append(target.path.ifEmpty { "/" })
                if (!target.query.isNullOrEmpty()) {
                    append('?')
                    append(target.query)
                }
            }
            val hostHeader = if (port == DEFAULT_HTTPS_PORT) target.host else "${target.host}:$port"
            val request = buildString {
                append("GET $path HTTP/1.1\r\n")
                append("Host: $hostHeader\r\n")
                append("Accept: application/json\r\n")
                append("Accept-Encoding: gzip\r\n")
                append("User-Agent: $USER_AGENT\r\n")
                append("Connection: close\r\n")
                append("\r\n")
            }

            tlsSocket.outputStream.write(request.toByteArray(StandardCharsets.US_ASCII))
            tlsSocket.outputStream.flush()
            return readHttpResponse(tlsSocket.inputStream)
        }
    }

    private fun capture(
        transport: String,
        url: String,
        request: (String) -> HttpResponse,
    ): NetworkRequestSnapshot = runCatching {
        val response = request(url)
        check(response.statusCode in 200..299) {
            "HTTP ${response.statusCode}: ${response.body.take(ERROR_BODY_LIMIT)}"
        }
        NetworkRequestSnapshot(
            transport = transport,
            url = url,
            statusCode = response.statusCode,
            urlPayloadJson = extractUrlPayload(response.body),
            error = null,
        )
    }.getOrElse { error ->
        NetworkRequestSnapshot(
            transport = transport,
            url = url,
            statusCode = null,
            urlPayloadJson = null,
            error = error.message ?: error.javaClass.simpleName,
        )
    }

    private fun extractUrlPayload(body: String): JSONObject {
        val trimmedBody = body.trim()
        val resource = when {
            trimmedBody.startsWith('[') -> {
                val array = JSONArray(trimmedBody)
                check(array.length() > 0) { "Response JSON array is empty" }
                array.getJSONObject(0)
            }
            trimmedBody.startsWith('{') -> JSONObject(trimmedBody)
            else -> error("Response is not a JSON object or array")
        }

        check(resource.has("URL") && resource.has("payload")) {
            "Response JSON does not contain URL and payload"
        }
        return JSONObject()
            .put("URL", resource.get("URL"))
            .put("payload", resource.get("payload"))
    }

    private fun readHttpResponse(input: InputStream): HttpResponse {
        val buffered = BufferedInputStream(input)
        val statusLine = readAsciiLine(buffered)
        val statusCode = statusLine
            .split(' ')
            .getOrNull(1)
            ?.toIntOrNull()
            ?: error("Invalid HTTP status line: $statusLine")

        val headers = mutableMapOf<String, String>()
        while (true) {
            val line = readAsciiLine(buffered)
            if (line.isEmpty()) break
            val separator = line.indexOf(':')
            if (separator > 0) {
                headers[line.substring(0, separator).trim().lowercase()] =
                    line.substring(separator + 1).trim()
            }
        }

        val responseBytes = when {
            headers["transfer-encoding"]?.contains("chunked", ignoreCase = true) == true ->
                readChunkedBody(buffered)
            headers["content-length"] != null ->
                readExactly(buffered, headers.getValue("content-length").toInt())
            else -> buffered.readBytes()
        }
        val decodedBytes = if (
            headers["content-encoding"]?.contains("gzip", ignoreCase = true) == true
        ) {
            GZIPInputStream(ByteArrayInputStream(responseBytes)).use { it.readBytes() }
        } else {
            responseBytes
        }

        return HttpResponse(
            statusCode = statusCode,
            body = decodedBytes.toString(StandardCharsets.UTF_8),
        )
    }

    private fun readChunkedBody(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        while (true) {
            val sizeLine = readAsciiLine(input)
            val chunkSize = sizeLine.substringBefore(';').trim().toInt(16)
            if (chunkSize == 0) {
                while (readAsciiLine(input).isNotEmpty()) {
                    // Consume optional trailer headers.
                }
                return output.toByteArray()
            }
            output.write(readExactly(input, chunkSize))
            check(readAsciiLine(input).isEmpty()) { "Invalid chunk terminator" }
        }
    }

    private fun readExactly(input: InputStream, byteCount: Int): ByteArray {
        val bytes = ByteArray(byteCount)
        var offset = 0
        while (offset < byteCount) {
            val read = input.read(bytes, offset, byteCount - offset)
            if (read == -1) throw EOFException("Unexpected end of HTTP response")
            offset += read
        }
        return bytes
    }

    private fun readAsciiLine(input: InputStream): String {
        val output = ByteArrayOutputStream()
        while (true) {
            val next = input.read()
            if (next == -1) {
                if (output.size() == 0) throw EOFException("Unexpected end of HTTP response")
                break
            }
            if (next == '\n'.code) break
            if (next != '\r'.code) output.write(next)
        }
        return output.toString(StandardCharsets.US_ASCII.name())
    }

    data class HttpResponse(
        val statusCode: Int,
        val body: String,
    )

    companion object {
        // Randomly generated dummy API keys for instrumentation testing.
        const val API_KEY_1: String = "383e4f3862e987f8979f55c66da9c9acb4af2c80ed072bcc221751aacdd27c3f"
        const val API_KEY_2: String = "d78dc2b0e89d8f4a1801d720f839762cc0492c0cb76daddc3aa98cb6a1c91373"
        const val API_KEY_3: String = "6b469fb285ebf8355072da2d67c5f65fdfeced6ca80a81de857913b1b94a4ac5"

        internal const val API_URL =
            "https://6a7f6a6f3183f5fd884b186d.mockapi.io/dankcasino/payload"
        internal const val TRANSPORT_URL_CONNECTION = "HttpsURLConnection.openConnection"
        internal const val TRANSPORT_TLS_SOCKET = "SSLSocket"
        private const val DEFAULT_HTTPS_PORT = 443
        private const val TIMEOUT_MILLIS = 10_000
        private const val ERROR_BODY_LIMIT = 200
        private const val USER_AGENT = "FridaTestBench/1.0"
    }
}
