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
 *
 * H5 可调用的方法:
 * - openCamera() 打开相机拍照
 * - openGallerySingle() 打开相册单选
 * - openGalleryMulti() 打开相册多选
 */
class WebViewJSBridge(
    private val webView: WebView,
    private val onMessage: (String) -> Unit,
    private val onOpenCamera: (() -> Unit)? = null,
    private val onOpenGallerySingle: (() -> Unit)? = null,
    private val onOpenGalleryMulti: (() -> Unit)? = null,
    private val onOpenGalleryWithOptions: ((String) -> Unit)? = null,
    private val onOpenCameraWithOptions: ((String) -> Unit)? = null
) {
    companion object {
        const val BRIDGE_NAME = "NativeBridge"
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * H5 → Native: 接收来自 WebView 的消息
     */
    @JavascriptInterface
    fun postMessage(message: String) {
        mainHandler.post {
            onMessage(message)
        }
    }

    /**
     * H5 → Native: 打开相机拍照
     */
    @JavascriptInterface
    fun openCamera() {
        mainHandler.post {
            onOpenCamera?.invoke()
        }
    }

    /**
     * H5 → Native: 打开相机拍照（带参数）
     * @param optionsJson JSON 格式的配置: {"maxWidth": 1024, "quality": 80}
     */
    @JavascriptInterface
    fun openCameraWithOptions(optionsJson: String) {
        android.util.Log.d("WebViewJSBridge", "openCameraWithOptions: $optionsJson")
        mainHandler.post {
            onOpenCameraWithOptions?.invoke(optionsJson)
        }
    }

    /**
     * H5 → Native: 打开相册单选
     */
    @JavascriptInterface
    fun openGallerySingle() {
        mainHandler.post {
            onOpenGallerySingle?.invoke()
        }
    }

    /**
     * H5 → Native: 打开相册多选
     */
    @JavascriptInterface
    fun openGalleryMulti() {
        mainHandler.post {
            onOpenGalleryMulti?.invoke()
        }
    }

    /**
     * H5 → Native: 打开相册（可配置参数）
     * @param optionsJson JSON 格式的配置: {"maxCount": 1, "maxSizeKB": 2048, "photosOnly": true}
     */
    @JavascriptInterface
    fun openGalleryWithOptions(optionsJson: String) {
        android.util.Log.d("WebViewJSBridge", "openGalleryWithOptions: $optionsJson")
        mainHandler.post {
            onOpenGalleryWithOptions?.invoke(optionsJson)
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
     */
    fun sendToWebView(message: String) {
        android.util.Log.d("WebViewJSBridge", "sendToWebView: $message")
        
        val escapedMessage = message
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")

        val script = """
            (function() {
                console.log('[NativeBridge] Received:', "$escapedMessage");
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
     * Native → H5: 执行任意 JS
     */
    fun evaluateJS(script: String, callback: ((String?) -> Unit)? = null) {
        mainHandler.post {
            webView.evaluateJavascript(script) { result ->
                callback?.invoke(result)
            }
        }
    }
}
