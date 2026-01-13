package com.nativewebview.webview

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import org.json.JSONObject

/**
 * WebView JSBridge - 高性能双向通信桥接
 *
 * H5 调用: window.NativeBridge.postMessage(JSON.stringify({type: 'xxx', data: {...}}))
 * RN 调用: webView.evaluateJavascript("window.onNativeMessage(...)")
 */
class WebViewJSBridge(
    private val webView: WebView,
    private val onMessage: (String) -> Unit
) {
    companion object {
        const val BRIDGE_NAME = "NativeBridge"
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * H5 → Native: 接收来自 WebView 的消息
     *
     * 在 H5 中调用: window.NativeBridge.postMessage(jsonString)
     */
    @JavascriptInterface
    fun postMessage(message: String) {
        // JavascriptInterface 在 WebView 线程执行，需切换到主线程
        mainHandler.post {
            onMessage(message)
        }
    }

    /**
     * H5 → Native: 同步获取设备信息
     */
    @JavascriptInterface
    fun getDeviceInfo(): String {
        return JSONObject().apply {
            put("platform", "android")
            put("version", android.os.Build.VERSION.SDK_INT)
            put("model", android.os.Build.MODEL)
            put("brand", android.os.Build.BRAND)
        }.toString()
    }

    /**
     * H5 → Native: 同步获取当前时间戳
     */
    @JavascriptInterface
    fun getTimestamp(): Long {
        return System.currentTimeMillis()
    }

    /**
     * Native → H5: 发送消息到 WebView
     *
     * 调用 H5 的 window.onNativeMessage(data) 方法
     */
    fun sendToWebView(message: String) {
        val escapedMessage = message
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")

        val script = """
            (function() {
                if (typeof window.onNativeMessage === 'function') {
                    window.onNativeMessage("$escapedMessage");
                } else {
                    window.dispatchEvent(new CustomEvent('nativeMessage', { detail: "$escapedMessage" }));
                }
            })();
        """.trimIndent()

        mainHandler.post {
            webView.evaluateJavascript(script, null)
        }
    }

    /**
     * Native → H5: 执行任意 JS 并获取结果
     */
    fun evaluateJS(script: String, callback: ((String?) -> Unit)? = null) {
        mainHandler.post {
            webView.evaluateJavascript(script) { result ->
                callback?.invoke(result)
            }
        }
    }
}
