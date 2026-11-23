package com.example.fridatestbench.checks

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import java.io.File

data class DeviceSecuritySnapshot(
    val batteryPercentage: Int?,
    val rootDetected: Boolean,
    val emulatorDetected: Boolean,
    val developmentSettingsEnabled: Boolean,
    val adbEnabled: Boolean,
)

/**
 * Collects device-level signals in one place so they are easy to exercise from
 * an instrumentation script. Root and emulator checks are deliberately exposed
 * as heuristics: either result can be spoofed and false positives are possible.
 */
class DeviceSecurityChecks(private val context: Context) {

    fun collect(): DeviceSecuritySnapshot = DeviceSecuritySnapshot(
        batteryPercentage = batteryPercentage(),
        rootDetected = isRootDetected(),
        emulatorDetected = isEmulatorDetected(),
        developmentSettingsEnabled = isDevelopmentSettingsEnabled(),
        adbEnabled = isAdbEnabled(),
    )

    fun batteryPercentage(): Int? {
        val batteryStatus = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
        ) ?: return null

        val level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return null

        return ((level * 100f) / scale).toInt().coerceIn(0, 100)
    }

    fun isRootDetected(): Boolean {
        val hasTestKeys = Build.TAGS?.contains("test-keys") == true
        val hasKnownRootFile = ROOT_PATHS.any { File(it).exists() }
        val hasSuOnPath = findSuWithWhich()
        return hasTestKeys || hasKnownRootFile || hasSuOnPath
    }

    fun isEmulatorDetected(): Boolean {
        val fingerprint = Build.FINGERPRINT.lowercase()
        val model = Build.MODEL.lowercase()
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        val device = Build.DEVICE.lowercase()
        val product = Build.PRODUCT.lowercase()
        val hardware = Build.HARDWARE.lowercase()

        return fingerprint.startsWith("generic") ||
            fingerprint.startsWith("unknown") ||
            model.contains("google_sdk") ||
            model.contains("emulator") ||
            model.contains("android sdk built for") ||
            manufacturer.contains("genymotion") ||
            (brand.startsWith("generic") && device.startsWith("generic")) ||
            product in EMULATOR_PRODUCTS ||
            hardware.contains("goldfish") ||
            hardware.contains("ranchu") ||
            File("/dev/qemu_pipe").exists() ||
            File("/dev/socket/qemud").exists()
    }

    fun isDevelopmentSettingsEnabled(): Boolean = readGlobalFlag(
        Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
    )

    fun isAdbEnabled(): Boolean = readGlobalFlag(Settings.Global.ADB_ENABLED)

    private fun readGlobalFlag(name: String): Boolean = runCatching {
        Settings.Global.getInt(context.contentResolver, name, 0) == 1
    }.getOrDefault(false)

    private fun findSuWithWhich(): Boolean {
        return runCatching {
            val process = Runtime.getRuntime().exec(arrayOf("/system/xbin/which", "su"))
            try {
                process.inputStream.bufferedReader().use { it.readLine() != null }
            } finally {
                process.destroy()
            }
        }.getOrDefault(false)
    }

    private companion object {
        val ROOT_PATHS = listOf(
            "/system/app/Superuser.apk",
            "/system/app/SuperSU.apk",
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/su/bin/su",
            "/data/local/bin/su",
            "/data/local/xbin/su",
            "/data/local/su",
        )

        val EMULATOR_PRODUCTS = setOf(
            "sdk",
            "google_sdk",
            "sdk_gphone",
            "sdk_google_phone_x86",
            "sdk_google_phone_x86_64",
            "sdk_x86",
            "sdk_x86_64",
            "vbox86p",
            "emulator",
            "simulator",
        )
    }
}
