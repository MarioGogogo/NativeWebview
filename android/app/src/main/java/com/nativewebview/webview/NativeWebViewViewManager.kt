package com.nativewebview.webview

import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReactContext
import com.facebook.react.bridge.WritableMap
import com.facebook.react.common.MapBuilder
import com.facebook.react.uimanager.SimpleViewManager
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.UIManagerHelper
import com.facebook.react.uimanager.annotations.ReactProp
import com.facebook.react.uimanager.events.Event
import android.webkit.WebSettings

/**
 * WebView ViewManager - 将 NativeWebViewView 暴露给 React Native
 */
class NativeWebViewViewManager : SimpleViewManager<NativeWebViewView>() {

    companion object {
        const val REACT_CLASS = "NativeWebView"

        // 命令 ID
        private const val COMMAND_GO_BACK = 1
        private const val COMMAND_GO_FORWARD = 2
        private const val COMMAND_RELOAD = 3
        private const val COMMAND_CLEAR_CACHE = 4
        private const val COMMAND_STOP_LOADING = 5
        private const val COMMAND_POST_MESSAGE = 6
    }

    override fun getName(): String = REACT_CLASS

    override fun createViewInstance(reactContext: ThemedReactContext): NativeWebViewView {
        return NativeWebViewView(reactContext)
    }

    override fun addEventEmitters(reactContext: ThemedReactContext, view: NativeWebViewView) {
        super.addEventEmitters(reactContext, view)

        val viewId = view.id

        view.setOnProgressChanged { progress: Int ->
            dispatchEvent(reactContext, viewId, "topProgress", Arguments.createMap().apply {
                putInt("progress", progress)
            })
        }

        view.setOnLoadEnd {
            dispatchEvent(reactContext, viewId, "topLoadEnd", Arguments.createMap())
        }

        view.setOnError { code: Int, description: String ->
            dispatchEvent(reactContext, viewId, "topError", Arguments.createMap().apply {
                putInt("code", code)
                putString("description", description)
            })
        }

        view.setOnTitleReceived { title: String ->
            dispatchEvent(reactContext, viewId, "topTitle", Arguments.createMap().apply {
                putString("title", title)
            })
        }

        view.setOnNavigationStateChange { canGoBack: Boolean, canGoForward: Boolean, hasUrl: Boolean ->
            dispatchEvent(reactContext, viewId, "topNavigationStateChange", Arguments.createMap().apply {
                putBoolean("canGoBack", canGoBack)
                putBoolean("canGoForward", canGoForward)
                putBoolean("hasUrl", hasUrl)
            })
        }

        // JSBridge 消息回调
        view.setOnMessage { message: String ->
            dispatchEvent(reactContext, viewId, "topMessage", Arguments.createMap().apply {
                putString("data", message)
            })
        }
    }

    private fun dispatchEvent(reactContext: ReactContext, viewId: Int, eventName: String, eventData: WritableMap) {
        val dispatcher = UIManagerHelper.getEventDispatcherForReactTag(reactContext, viewId)
        dispatcher?.dispatchEvent(WebViewEvent(UIManagerHelper.getSurfaceId(reactContext), viewId, eventName, eventData))
    }

    // ============ Props ============

    @ReactProp(name = "source")
    fun setSource(view: NativeWebViewView, source: com.facebook.react.bridge.ReadableMap?) {
        source?.let {
            if (it.hasKey("uri")) {
                view.loadUrl(it.getString("uri")!!)
            } else if (it.hasKey("html")) {
                view.loadHtml(it.getString("html")!!)
            }
        }
    }

    @ReactProp(name = "javaScriptEnabled", defaultBoolean = true)
    fun setJavaScriptEnabled(view: NativeWebViewView, enabled: Boolean) {
        view.setJavaScriptEnabled(enabled)
    }

    @ReactProp(name = "domStorageEnabled", defaultBoolean = true)
    fun setDomStorageEnabled(view: NativeWebViewView, enabled: Boolean) {
        view.setDomStorageEnabled(enabled)
    }

    @ReactProp(name = "injectedJavaScript")
    fun setInjectedJavaScript(view: NativeWebViewView, script: String?) {
        view.setInjectedJavaScript(script)
    }

    /**
     * 缓存模式
     * LOAD_DEFAULT = 0 (正常模式)
     * LOAD_CACHE_ELSE_NETWORK = 1 (有缓存就用缓存，否则网络)
     * LOAD_NO_CACHE = 2 (只用网络)
     * LOAD_CACHE_ONLY = 3 (只用缓存)
     */
    @ReactProp(name = "cacheMode", defaultInt = WebSettings.LOAD_DEFAULT)
    fun setCacheMode(view: NativeWebViewView, mode: Int) {
        view.setCacheMode(mode)
    }

    @ReactProp(name = "allowsFileAccess", defaultBoolean = true)
    fun setAllowsFileAccess(view: NativeWebViewView, enabled: Boolean) {
        view.setAllowFileAccess(enabled)
    }

    @ReactProp(name = "showsHorizontalScrollIndicator", defaultBoolean = true)
    fun setShowsHorizontalScrollIndicator(view: NativeWebViewView, show: Boolean) {
        // 可扩展
    }

    @ReactProp(name = "showsVerticalScrollIndicator", defaultBoolean = true)
    fun setShowsVerticalScrollIndicator(view: NativeWebViewView, show: Boolean) {
        // 可扩展
    }

    // ============ Commands ============

    override fun getCommandsMap(): Map<String, Int> {
        return mapOf(
            "goBack" to COMMAND_GO_BACK,
            "goForward" to COMMAND_GO_FORWARD,
            "reload" to COMMAND_RELOAD,
            "clearCache" to COMMAND_CLEAR_CACHE,
            "stopLoading" to COMMAND_STOP_LOADING,
            "postMessage" to COMMAND_POST_MESSAGE
        )
    }

    override fun receiveCommand(view: NativeWebViewView, commandId: Int, args: com.facebook.react.bridge.ReadableArray?) {
        when (commandId) {
            COMMAND_GO_BACK -> view.goBack()
            COMMAND_GO_FORWARD -> view.goForward()
            COMMAND_RELOAD -> view.reload()
            COMMAND_CLEAR_CACHE -> view.clearCache()
            COMMAND_STOP_LOADING -> view.getWebView()?.stopLoading()
            COMMAND_POST_MESSAGE -> {
                args?.getString(0)?.let { message ->
                    view.postMessageToWebView(message)
                }
            }
        }
    }

    override fun getExportedCustomDirectEventTypeConstants(): Map<String, Any>? {
        return MapBuilder.builder<String, Any>()
            .put("topProgress", MapBuilder.of("registrationName", "onProgress"))
            .put("topLoadEnd", MapBuilder.of("registrationName", "onLoadEnd"))
            .put("topError", MapBuilder.of("registrationName", "onError"))
            .put("topTitle", MapBuilder.of("registrationName", "onTitle"))
            .put("topNavigationStateChange", MapBuilder.of("registrationName", "onNavigationStateChange"))
            .put("topMessage", MapBuilder.of("registrationName", "onMessage"))
            .build()
    }

    override fun getExportedViewConstants(): Map<String, Any>? {
        return MapBuilder.builder<String, Any>()
            .put("CacheMode", MapBuilder.of(
                "LOAD_DEFAULT", WebSettings.LOAD_DEFAULT,
                "LOAD_CACHE_ELSE_NETWORK", WebSettings.LOAD_CACHE_ELSE_NETWORK,
                "LOAD_NO_CACHE", WebSettings.LOAD_NO_CACHE,
                "LOAD_CACHE_ONLY", WebSettings.LOAD_CACHE_ONLY
            ))
            .put("ErrorCodeUndefined", -1)
            .build()
    }

    override fun onDropViewInstance(view: NativeWebViewView) {
        super.onDropViewInstance(view)
        view.destroy()
    }
}

/**
 * 自定义 WebView 事件类
 */
class WebViewEvent(
    surfaceId: Int,
    viewId: Int,
    private val eventName: String,
    private val eventData: WritableMap
) : Event<WebViewEvent>(surfaceId, viewId) {

    override fun getEventName(): String = eventName

    override fun getEventData(): WritableMap = eventData
}
