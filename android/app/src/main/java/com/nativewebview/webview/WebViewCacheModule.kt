package com.nativewebview.webview

import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.Promise

/**
 * WebView 缓存管理模块
 *
 * 提供：
 * - 获取缓存大小
 * - 清理缓存
 */
class WebViewCacheModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {

    override fun getName(): String = "WebViewCacheManager"

    @ReactMethod
    fun getCacheSize(promise: Promise) {
        try {
            val size = WebViewCacheManager.getCacheSize(reactApplicationContext)
            val formattedSize = WebViewCacheManager.formatCacheSize(size)
            promise.resolve(formattedSize)
        } catch (e: Exception) {
            promise.reject("CACHE_ERROR", "Failed to get cache size", e)
        }
    }

    @ReactMethod
    fun clearAllCache(promise: Promise) {
        try {
            WebViewCacheManager.clearAllCache(reactApplicationContext)
            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("CACHE_ERROR", "Failed to clear cache", e)
        }
    }
}
