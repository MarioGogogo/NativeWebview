package com.nativewebview.webview

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.webkit.CookieManager
import android.webkit.WebStorage
import java.io.File

/**
 * WebView 缓存配置管理器
 *
 * 提供：
 * 1. 缓存策略配置
 * 2. 缓存清理功能
 * 3. 缓存大小统计
 */
object WebViewCacheManager {

    private const val MAX_AGE = 24 * 60 * 60         // 24小时
    private const val MAX_STALE = 7 * 24 * 60 * 60   // 7天

    /**
     * 获取网络是否可用
     */
    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * 清理所有 WebView 缓存
     */
    fun clearAllCache(context: Context) {
        android.util.Log.d("WebViewCacheManager", "========== Starting cache cleanup ==========")

        // 1. 清理 Cookies
        try {
            CookieManager.getInstance().removeAllCookies(null)
            android.util.Log.d("WebViewCacheManager", "Cleared cookies")
        } catch (e: Exception) {
            android.util.Log.e("WebViewCacheManager", "Error clearing cookies", e)
        }

        // 2. 清理 Web Storage (localStorage, sessionStorage)
        try {
            WebStorage.getInstance().deleteAllData()
            android.util.Log.d("WebViewCacheManager", "Cleared WebStorage")
        } catch (e: Exception) {
            android.util.Log.e("WebViewCacheManager", "Error clearing WebStorage", e)
        }

        // 3. 清理应用缓存目录中的所有缓存
        try {
            val cacheDir = context.cacheDir
            android.util.Log.d("WebViewCacheManager", "Scanning cache dir: ${cacheDir.absolutePath}")

            cacheDir.listFiles()?.forEach { file ->
                android.util.Log.d("WebViewCacheManager", "Found in cache: ${file.name} (${getDirSize(file)} bytes)")

                // 删除所有缓存相关目录
                file.deleteRecursively()
            }

            android.util.Log.d("WebViewCacheManager", "Cleared cache directory")
        } catch (e: Exception) {
            android.util.Log.e("WebViewCacheManager", "Error clearing cache dir", e)
        }

        // 4. 清理 code_cache 目录
        try {
            val codeCacheDir = context.codeCacheDir
            android.util.Log.d("WebViewCacheManager", "Scanning code_cache dir: ${codeCacheDir.absolutePath}")

            codeCacheDir.listFiles()?.forEach { file ->
                android.util.Log.d("WebViewCacheManager", "Found in code_cache: ${file.name}")
                file.deleteRecursively()
            }

            android.util.Log.d("WebViewCacheManager", "Cleared code_cache directory")
        } catch (e: Exception) {
            android.util.Log.e("WebViewCacheManager", "Error clearing code_cache dir", e)
        }

        // 5. 清理 WebView databases
        try {
            val appDir = context.applicationInfo.dataDir
            val databasesDir = File(appDir, "databases")

            if (databasesDir.exists()) {
                android.util.Log.d("WebViewCacheManager", "Scanning databases dir: ${databasesDir.absolutePath}")

                databasesDir.listFiles()?.forEach { file ->
                    if (file.name.contains("webview") ||
                        file.name.contains("http") ||
                        file.name.endsWith(".db") ||
                        file.name.endsWith("-journal")) {
                        android.util.Log.d("WebViewCacheManager", "Deleting database: ${file.name}")
                        file.deleteRecursively()
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("WebViewCacheManager", "Error clearing databases", e)
        }

        // 6. 清理 app_webview 目录 (Chrome WebView 数据)
        try {
            val appDir = context.applicationInfo.dataDir
            val appWebViewDir = File(appDir, "app_webview")

            if (appWebViewDir.exists()) {
                android.util.Log.d("WebViewCacheManager", "Deleting app_webview: ${appWebViewDir.absolutePath}")
                appWebViewDir.deleteRecursively()
            }
        } catch (e: Exception) {
            android.util.Log.e("WebViewCacheManager", "Error clearing app_webview", e)
        }

        android.util.Log.d("WebViewCacheManager", "========== Cache cleanup complete ==========")
    }

    /**
     * 获取缓存大小（包括所有 WebView 相关缓存）
     */
    fun getCacheSize(context: Context): Long {
        var totalSize = 0L

        android.util.Log.d("WebViewCacheManager", "========== Calculating cache size ==========")

        try {
            // 1. 应用缓存目录 (cacheDir) - WebView 主要缓存位置
            val cacheDir = context.cacheDir
            android.util.Log.d("WebViewCacheManager", "Scanning cache dir: ${cacheDir.absolutePath}")

            if (cacheDir.exists()) {
                val cacheSize = getDirSize(cacheDir)
                totalSize += cacheSize
                android.util.Log.d("WebViewCacheManager", "cacheDir total: $cacheSize bytes (${cacheSize / 1024} KB)")

                // 详细列出每个子目录
                cacheDir.listFiles()?.forEach { file ->
                    val size = getDirSize(file)
                    android.util.Log.d("WebViewCacheManager", "  - ${file.name}: $size bytes")
                }
            }

            // 2. code_cache 目录 - WebView 代码缓存
            val codeCacheDir = context.codeCacheDir
            android.util.Log.d("WebViewCacheManager", "Scanning code_cache dir: ${codeCacheDir.absolutePath}")

            if (codeCacheDir.exists()) {
                val codeCacheSize = getDirSize(codeCacheDir)
                totalSize += codeCacheSize
                android.util.Log.d("WebViewCacheManager", "code_cacheDir total: $codeCacheSize bytes")

                codeCacheDir.listFiles()?.forEach { file ->
                    val size = getDirSize(file)
                    android.util.Log.d("WebViewCacheManager", "  - ${file.name}: $size bytes")
                }
            }

            // 3. app_webview 目录 - Chrome WebView 数据（包括缓存、cookies、localStorage等）
            val appDir = context.applicationInfo.dataDir
            val appWebViewDir = File(appDir, "app_webview")

            if (appWebViewDir.exists()) {
                android.util.Log.d("WebViewCacheManager", "Scanning app_webview dir: ${appWebViewDir.absolutePath}")

                val appWebViewSize = getDirSize(appWebViewDir)
                totalSize += appWebViewSize
                android.util.Log.d("WebViewCacheManager", "app_webview total: $appWebViewSize bytes")

                appWebViewDir.listFiles()?.forEach { file ->
                    val size = getDirSize(file)
                    android.util.Log.d("WebViewCacheManager", "  - ${file.name}: $size bytes")
                }
            } else {
                android.util.Log.d("WebViewCacheManager", "app_webview dir does not exist")
            }

            // 4. databases 目录 - WebView 数据库（IndexedDB 等）
            val databasesDir = File(appDir, "databases")

            if (databasesDir.exists()) {
                android.util.Log.d("WebViewCacheManager", "Scanning databases dir: ${databasesDir.absolutePath}")

                databasesDir.listFiles()?.forEach { file ->
                    if (file.name.contains("webview") ||
                        file.name.contains("http") ||
                        file.name.endsWith(".db") ||
                        file.name.endsWith("-journal")) {

                        val size = getDirSize(file)
                        totalSize += size
                        android.util.Log.d("WebViewCacheManager", "  - database ${file.name}: $size bytes")
                    }
                }
            }

            // 5. shared_prefs 目录 - WebView 可能存储的配置
            val sharedPrefsDir = File(appDir, "shared_prefs")

            if (sharedPrefsDir.exists()) {
                sharedPrefsDir.listFiles()?.forEach { file ->
                    if (file.name.contains("webview") || file.name.contains("http")) {
                        val size = file.length()
                        totalSize += size
                        android.util.Log.d("WebViewCacheManager", "  - shared_pref ${file.name}: $size bytes")
                    }
                }
            }

        } catch (e: Exception) {
            android.util.Log.e("WebViewCacheManager", "Error calculating cache size", e)
        }

        android.util.Log.d("WebViewCacheManager", "========== Total cache size: $totalSize bytes (${totalSize / 1024} KB) ==========")
        return totalSize
    }

    /**
     * 递归计算目录大小
     */
    private fun getDirSize(dir: File): Long {
        if (!dir.exists()) return 0L

        return try {
            if (dir.isFile) {
                dir.length()
            } else {
                dir.walkTopDown()
                    .filter { it.isFile }
                    .map { it.length() }
                    .sum()
            }
        } catch (e: Exception) {
            android.util.Log.e("WebViewCacheManager", "Error calculating size for ${dir.path}", e)
            0L
        }
    }

    /**
     * 格式化缓存大小
     */
    fun formatCacheSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> "${bytes / (1024 * 1024 * 1024)} GB"
        }
    }
}
