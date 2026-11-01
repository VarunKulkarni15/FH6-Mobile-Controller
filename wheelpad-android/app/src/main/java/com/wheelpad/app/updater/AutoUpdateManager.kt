package com.wheelpad.app.updater

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val changelog: String
)

sealed class UpdateState {
    object Idle : UpdateState()
    object Checking : UpdateState()
    data class Available(val info: UpdateInfo) : UpdateState()
    data class Downloading(val progressPercent: Int) : UpdateState()
    data class ReadyToInstall(val apkFile: File) : UpdateState()
    data class Error(val message: String) : UpdateState()
}

class AutoUpdateManager(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private val endpoints = listOf(
        "https://horizoncontro.varunkulkarni.dpdns.org/version.json",
        "https://raw.githubusercontent.com/VarunKulkarni15/FH6-Mobile-Controller/master/site/version.json"
    )

    fun getCurrentVersionCode(): Int {
        return try {
            val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode
            }
        } catch (e: Exception) {
            1
        }
    }

    fun checkForUpdates(silent: Boolean = true) {
        scope.launch {
            if (!silent) _updateState.value = UpdateState.Checking
            val currentCode = getCurrentVersionCode()

            for (urlStr in endpoints) {
                try {
                    val url = URL(urlStr)
                    val conn = (url.openConnection() as HttpURLConnection).apply {
                        connectTimeout = 4000
                        readTimeout = 4000
                        requestMethod = "GET"
                        setRequestProperty("User-Agent", "HorizonController-Android")
                    }

                    if (conn.responseCode == 200) {
                        val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                        val root = JSONObject(jsonStr)
                        val androidObj = root.optJSONObject("android") ?: root

                        val remoteCode = androidObj.optInt("versionCode", 0)
                        val remoteName = androidObj.optString("versionName", "1.0.0")
                        val apkUrl = androidObj.optString("apkUrl", "")
                        val changelog = androidObj.optString("changelog", "Performance improvements & bug fixes")

                        if (remoteCode > currentCode && apkUrl.isNotBlank()) {
                            _updateState.value = UpdateState.Available(
                                UpdateInfo(
                                    versionCode = remoteCode,
                                    versionName = remoteName,
                                    apkUrl = apkUrl,
                                    changelog = changelog
                                )
                            )
                            return@launch
                        }
                    }
                } catch (e: Exception) {
                    // Try next fallback endpoint
                }
            }

            if (!silent) {
                _updateState.value = UpdateState.Error("You are running the latest version!")
                delay(3000)
                _updateState.value = UpdateState.Idle
            } else {
                _updateState.value = UpdateState.Idle
            }
        }
    }

    fun downloadAndInstall(info: UpdateInfo) {
        scope.launch {
            try {
                _updateState.value = UpdateState.Downloading(0)

                val downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
                val apkFile = File(downloadDir, "HorizonController_v${info.versionName}.apk")

                val url = URL(info.apkUrl)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 15000
                    requestMethod = "GET"
                }

                val totalBytes = conn.contentLength
                var downloadedBytes = 0

                conn.inputStream.use { input ->
                    FileOutputStream(apkFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var lastReportTime = System.currentTimeMillis()

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead

                            val now = System.currentTimeMillis()
                            if (totalBytes > 0 && (now - lastReportTime > 150)) {
                                lastReportTime = now
                                val progress = ((downloadedBytes.toFloat() / totalBytes) * 100).toInt()
                                _updateState.value = UpdateState.Downloading(progress)
                            }
                        }
                    }
                }

                _updateState.value = UpdateState.ReadyToInstall(apkFile)

                // Launch Android Package Installer via FileProvider
                installApk(apkFile)

            } catch (e: Exception) {
                _updateState.value = UpdateState.Error("Download failed: ${e.message}")
            }
        }
    }

    fun installApk(apkFile: File) {
        try {
            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            _updateState.value = UpdateState.Error("Install launch failed: ${e.message}")
        }
    }

    fun dismiss() {
        _updateState.value = UpdateState.Idle
    }
}

