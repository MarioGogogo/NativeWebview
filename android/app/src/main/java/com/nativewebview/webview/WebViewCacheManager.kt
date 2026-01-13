package com.nativewebview.webview

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * WebView 缓存配置管理器
 *
 * 提供：
 * 1. OkHttp 缓存拦截器
 * 2. 缓存策略配置
 * 3. 缓存清理功能
 */
object WebViewCacheManager {

    private const val CACHE_SIZE = 20L * 1024 * 1024  // 20MB
    private const val CACHE_DIR_NAME = "webview_cache"
    private const val MAX_AGE = 24 * 60 * 60         // 24小时
    private const val MAX_STALE = 7 * 24 * 60 * 60   // 7天

    private var httpCache: Cache? = null

    /**
     * 获取 OkHttpClient（带缓存功能）
     */
    fun getOkHttpClient(context: Context): OkHttpClient {
        val cacheDir = File(context.cacheDir, CACHE_DIR_NAME)
        httpCache = Cache(cacheDir, CACHE_SIZE)

        return OkHttpClient.Builder()
            .cache(httpCache)
            .addInterceptor(CacheInterceptor(context))
            .addNetworkInterceptor(CacheInterceptor(context))
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * 缓存拦截器
     * - 有网络：优先网络，遵循服务端缓存策略
     * - 无网络：强制使用缓存
     */
    class CacheInterceptor(private val context: Context) : Interceptor {

        override fun intercept(chain: Interceptor.Chain): Response {
            var request = chain.request()

            // 根据网络状态选择策略
            if (!isNetworkAvailable()) {
                // 无网络：强制使用缓存
                request = request.newBuilder()
                    .cacheControl(CacheControl.FORCE_CACHE)
                    .build()
            }

            val response = chain.proceed(request)

            return if (isNetworkAvailable()) {
                // 有网络：缓存 HTML 1小时，静态资源 7天
                val cacheControl = if (request.url.toString().endsWith(".html") ||
                                     request.url.toString().endsWith(".htm")) {
                    CacheControl.Builder()
                        .maxAge(MAX_AGE, TimeUnit.SECONDS)
                        .build()
                } else {
                    CacheControl.Builder()
                        .maxAge(7 * 24 * 60 * 60, TimeUnit.SECONDS)  // 7天
                        .build()
                }

                response.newBuilder()
                    .header("Cache-Control", cacheControl.toString())
                    .removeHeader("Pragma")
                    .build()
            } else {
                // 无网络：缓存可用 7 天
                response.newBuilder()
                    .header("Cache-Control", "public, only-if-cached, max-stale=$MAX_STALE")
                    .build()
            }
        }

        private fun isNetworkAvailable(): Boolean {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
    }

    /**
     * 清理所有缓存
     */
    fun clearAllCache(context: Context) {
        httpCache?.evictAll()
        val cacheDir = File(context.cacheDir, CACHE_DIR_NAME)
        cacheDir.deleteRecursively()
    }

    /**
     * 获取缓存大小
     */
    fun getCacheSize(context: Context): Long {
        val cacheDir = File(context.cacheDir, CACHE_DIR_NAME)
        return cacheDir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
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
