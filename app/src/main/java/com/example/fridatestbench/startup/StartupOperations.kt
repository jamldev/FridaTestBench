package com.example.fridatestbench.startup

import android.annotation.SuppressLint
import android.content.Context
import dalvik.system.DexClassLoader
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream

data class StartupOperationsSnapshot(
    val fillerJson: String,
    val fileName: String,
    val fileContents: String,
    val systemLoadResult: String,
    val systemLoadLibraryResult: String,
    val dexPayloadFileName: String,
    val dexClassLoaderResult: String,
    val loadClassResult: String,
    val reflectMethodResult: String,
)

/** Performs simple startup operations that are useful as instrumentation hooks. */
class StartupOperations(private val context: Context) {

    @SuppressLint("UnsafeDynamicallyLoadedCode")
    fun run(): StartupOperationsSnapshot {
        val fillerJson = JSONObject()
            .put("SuperSecret", "DexClassLoader")
            .put("Payload", "Mossad Polymorphic agent")
            .put("Hotel", "Trivago")

        val textFile = File(context.filesDir, TEXT_FILE_NAME)
        textFile.writeText(RANDOM_SENTENCE, Charsets.UTF_8)

        // File has no open() method on Android, so FileInputStream performs the open.
        val openedContents = FileInputStream(textFile).bufferedReader(Charsets.UTF_8).use {
            it.readText()
        }

        val absoluteLibrary = File(
            context.applicationInfo.nativeLibraryDir,
            System.mapLibraryName(ABSOLUTE_LIBRARY_NAME),
        )
        val systemLoadResult = loadResult {
            System.load(absoluteLibrary.absolutePath)
            "Loaded ${absoluteLibrary.name}"
        }
        val systemLoadLibraryResult = loadResult {
            System.loadLibrary(NAMED_LIBRARY_NAME)
            "Loaded ${System.mapLibraryName(NAMED_LIBRARY_NAME)}"
        }

        val dexFile = prepareLocalDexFile()
        var dexClassLoader: DexClassLoader? = null
        var loadedClass: Class<*>? = null

        val dexClassLoaderResult = operationResult {
            dexClassLoader = DexClassLoader(
                dexFile.absolutePath,
                context.codeCacheDir.absolutePath,
                context.applicationInfo.nativeLibraryDir,
                context.classLoader.parent,
            )
            "Created from ${dexFile.name}"
        }
        val loadClassResult = operationResult {
            val loader = checkNotNull(dexClassLoader) {
                "DexClassLoader was not created"
            }
            loadedClass = loader.loadClass(DEX_PAYLOAD_CLASS_NAME)
            "Loaded from ${dexFile.name}"
        }
        val reflectMethodResult = operationResult {
            val payloadClass = checkNotNull(loadedClass) {
                "Payload class was not loaded"
            }
            val result = payloadClass
                .getDeclaredMethod(DEX_PAYLOAD_METHOD_NAME)
                .invoke(null)
            "$result (${dexFile.name})"
        }

        return StartupOperationsSnapshot(
            fillerJson = fillerJson.toString(),
            fileName = textFile.name,
            fileContents = openedContents,
            systemLoadResult = systemLoadResult,
            systemLoadLibraryResult = systemLoadLibraryResult,
            dexPayloadFileName = dexFile.name,
            dexClassLoaderResult = dexClassLoaderResult,
            loadClassResult = loadClassResult,
            reflectMethodResult = reflectMethodResult,
        )
    }

    private fun loadResult(load: () -> String): String =
        runCatching(load).getOrElse { error ->
            "Failed: ${error.message ?: error.javaClass.simpleName}"
        }

    private fun operationResult(operation: () -> String): String =
        runCatching(operation).getOrElse { error ->
            "Failed: ${error.message ?: error.javaClass.simpleName}"
        }

    private fun prepareLocalDexFile(): File {
        val localDexFile = File(context.filesDir, DEX_PAYLOAD_FILE_NAME)

        check(localDexFile.delete() || !localDexFile.exists()) {
            "Could not replace ${localDexFile.name}"
        }
        context.assets.open(DEX_PAYLOAD_ASSET_NAME).use { input ->
            localDexFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        check(localDexFile.setReadOnly()) {
            "Could not make ${localDexFile.name} read-only"
        }

        return localDexFile
    }

    private companion object {
        const val TEXT_FILE_NAME = "startup_sentence.txt"
        const val RANDOM_SENTENCE = "A curious fox watched the sunrise over the quiet harbor."
        const val ABSOLUTE_LIBRARY_NAME = "hello_absolute"
        const val NAMED_LIBRARY_NAME = "hello_named"
        const val DEX_PAYLOAD_ASSET_NAME = "local_dcl_payload.jar"
        const val DEX_PAYLOAD_FILE_NAME = DEX_PAYLOAD_ASSET_NAME
        const val DEX_PAYLOAD_CLASS_NAME =
            "com.example.fridatestbench.startup.DynamicDexPayload"
        const val DEX_PAYLOAD_METHOD_NAME = "execute"
    }
}
