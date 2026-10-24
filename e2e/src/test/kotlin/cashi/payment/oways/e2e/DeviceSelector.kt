package cashi.payment.oways.e2e

import java.io.File

/**
 * Picks the device to test: `ANDROID_SERIAL` if set, otherwise the single device that
 * `adb devices` reports as ready. Fails with a clear message for zero or several devices. Also
 * tells whether the app under test is installed, so a missing app fails with a clear message
 * instead of Appium's generic session error.
 */
internal object DeviceSelector {

    private const val SERIAL_ENV = "ANDROID_SERIAL"
    private const val ANDROID_HOME_ENV = "ANDROID_HOME"
    private const val READY_STATE = "device"
    private const val ADB_FALLBACK = "adb"
    private const val INSTALLED_PACKAGE_PREFIX = "package:"

    /** Whether [packageName] is installed on the device [udid] (`adb shell pm path`). */
    fun isAppInstalled(udid: String, packageName: String): Boolean {
        val process = ProcessBuilder(adbPath(), "-s", udid, "shell", "pm", "path", packageName)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        return process.waitFor() == 0 && output.lineSequence().any { it.startsWith(INSTALLED_PACKAGE_PREFIX) }
    }

    fun udid(): String {
        System.getenv(SERIAL_ENV)?.takeIf { it.isNotBlank() }?.let { return it }
        val devices = readyDevices()
        return when (devices.size) {
            1 -> devices.single()
            0 -> error("No Android device is connected and ready (adb devices). Connect one with USB debugging enabled.")
            else -> error("Several devices are connected; set $SERIAL_ENV to one of: ${devices.joinToString()}")
        }
    }

    private fun readyDevices(): List<String> {
        val process = ProcessBuilder(adbPath(), "devices").redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        check(process.waitFor() == 0) { "'adb devices' failed: $output" }
        return output.lineSequence()
            .drop(1) // "List of devices attached"
            .map { it.trim().split(Regex("\\s+")) }
            .filter { columns -> columns.size >= 2 && columns[1] == READY_STATE }
            .map { columns -> columns[0] }
            .toList()
    }

    private fun adbPath(): String =
        System.getenv(ANDROID_HOME_ENV)
            ?.let { File(it, "platform-tools/adb") }
            ?.takeIf { it.canExecute() }
            ?.path
            ?: ADB_FALLBACK
}