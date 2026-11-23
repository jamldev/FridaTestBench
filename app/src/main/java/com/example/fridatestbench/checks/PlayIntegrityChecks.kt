package com.example.fridatestbench.checks

import android.content.Context
import android.util.Base64
import com.example.fridatestbench.BuildConfig
import com.google.android.gms.tasks.Task
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.StandardIntegrityManager.PrepareIntegrityTokenRequest
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.URL
import java.security.MessageDigest
import java.util.UUID
import javax.net.ssl.HttpsURLConnection
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class PlayIntegritySnapshot(
    val meetsStrongIntegrity: Boolean? = null,
    val meetsDeviceIntegrity: Boolean? = null,
    val meetsBasicIntegrity: Boolean? = null,
    val configurationMessage: String? = null,
)

class PlayIntegrityChecks(private val context: Context) {
    suspend fun collect(): PlayIntegritySnapshot {
        val projectNumber = BuildConfig.PLAY_INTEGRITY_CLOUD_PROJECT_NUMBER
        val backendUrl = BuildConfig.PLAY_INTEGRITY_BACKEND_URL
        if (projectNumber <= 0 || backendUrl.isBlank()) {
            return PlayIntegritySnapshot(
                configurationMessage = "Set playIntegrityCloudProjectNumber and playIntegrityBackendUrl " +
                    "in Gradle properties. See PLAY_INTEGRITY.md for Google and backend setup.",
            )
        }
        val url = URL(backendUrl)
        require(url.protocol == "https" && url.host.isNotBlank() && url.userInfo == null) {
            "Play Integrity backend URL must use HTTPS without embedded credentials."
        }
        return withTimeoutOrNull(120_000L) {
            // A fresh provider on each manual run also avoids reusing an expired provider.
            val provider = IntegrityManagerFactory.createStandard(context)
                .prepareIntegrityToken(
                    PrepareIntegrityTokenRequest.builder().setCloudProjectNumber(projectNumber).build(),
                ).awaitResult()
            val requestHash = Base64.encodeToString(
                MessageDigest.getInstance("SHA-256").digest(
                    "${context.packageName}:${UUID.randomUUID()}".toByteArray(Charsets.UTF_8),
                ),
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
            )
            val token = provider.request(
                StandardIntegrityTokenRequest.builder().setRequestHash(requestHash).build(),
            ).awaitResult().token()
            withContext(Dispatchers.IO) { verifyToken(url, token, requestHash) }
        } ?: error("Play Integrity request timed out. Try again.")
    }

    private fun verifyToken(url: URL, token: String, requestHash: String): PlayIntegritySnapshot {
        val connection = url.openConnection() as HttpsURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = false
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            val body = JSONObject()
                .put("integrityToken", token)
                .put("requestHash", requestHash)
                .toString().toByteArray(Charsets.UTF_8)
            connection.setFixedLengthStreamingMode(body.size)
            connection.outputStream.use { it.write(body) }
            check(connection.responseCode in 200..299) {
                "Play Integrity backend returned HTTP ${connection.responseCode}."
            }
            val bytes = connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    check(output.size() + count <= 64 * 1024) {
                        "Play Integrity backend response is too large."
                    }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            val response = JSONObject(String(bytes, Charsets.UTF_8))
            check(response.getString("requestHash") == requestHash) {
                "Play Integrity backend response does not match this request."
            }
            fun verdict(label: String): Boolean {
                val value = response.get(label)
                check(value is Boolean) { "Invalid backend verdict for $label." }
                return value
            }
            return PlayIntegritySnapshot(
                meetsStrongIntegrity = verdict("MEETS_STRONG_INTEGRITY"),
                meetsDeviceIntegrity = verdict("MEETS_DEVICE_INTEGRITY"),
                meetsBasicIntegrity = verdict("MEETS_BASIC_INTEGRITY"),
            )
        } finally {
            connection.disconnect()
        }
    }
}

private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { value -> if (continuation.isActive) continuation.resume(value) }
    addOnFailureListener { error -> if (continuation.isActive) continuation.resumeWithException(error) }
    addOnCanceledListener { continuation.cancel() }
}
