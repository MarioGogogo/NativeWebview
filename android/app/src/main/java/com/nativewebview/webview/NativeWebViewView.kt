package com.nativewebview.webview

import android.annotation.SuppressLint
import android.content.ContentValues
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.ViewGroup
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.facebook.react.bridge.ReactContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Native WebView View - 封装原生 WebView，提供 React Native 桥接能力
 *
 * 使用 ViewGroup 作为容器，内部包含 WebView
 * 支持相机拍照、相册选择（单选/多选）
 */
class NativeWebViewView(context: ReactContext) : ViewGroup(context) {

    // 实际使用的 WebView
    private var webView: WebView? = null

    // 事件回调
    private var onProgressChanged: ((Int) -> Unit)? = null
    private var onLoadEnd: (() -> Unit)? = null
    private var onError: ((Int, String) -> Unit)? = null
    private var onTitleReceived: ((String) -> Unit)? = null
    private var onNavigationStateChange: ((Boolean, Boolean, Boolean) -> Unit)? = null
    private var onMessage: ((String) -> Unit)? = null

    // JSBridge 实例
    private var jsBridge: WebViewJSBridge? = null

    // 文件选择器回调
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null

    // 文件选择器 launchers
    private var gallerySingleLauncher: ActivityResultLauncher<String>? = null
    private var galleryMultiLauncher: ActivityResultLauncher<Array<String>>? = null
    private var cameraLauncher: ActivityResultLauncher<Uri>? = null

    // 临时相机拍照 URI
    private var cameraPhotoUri: Uri? = null

    // WebView 配置
    private var javaScriptEnabled = true
    private var domStorageEnabled = true
    private var cacheMode = WebSettings.LOAD_DEFAULT
    private var allowFileAccess = true
    private var injectedJavaScript: String? = null

    init {
        createWebView()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView() {
        webView = WebView(context).apply {
            // 基础设置
            settings.apply {
                javaScriptEnabled = this@NativeWebViewView.javaScriptEnabled
                domStorageEnabled = this@NativeWebViewView.domStorageEnabled
                this@NativeWebViewView.cacheMode.let { cacheMode = it }

                // 缓存设置
                domStorageEnabled = true
                databaseEnabled = true

                // 性能优化
                builtInZoomControls = false
                displayZoomControls = false
                savePassword = false
                saveFormData = false

                // 安全设置
                allowFileAccess = this@NativeWebViewView.allowFileAccess
                allowContentAccess = true

                // 混合内容模式
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                }

                // 硬件加速
                setLayerType(ViewGroup.LAYER_TYPE_HARDWARE, null)
            }

            // 设置 WebViewClient
            webViewClient = createWebViewClient()

            // 设置 WebChromeClient
            webChromeClient = createWebChromeClient()

            // 初始化文件选择器
            initFileChoosers()

            // 初始化 JSBridge
            initJSBridge(this)

            // 添加到容器
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            this@NativeWebViewView.addView(this)
        }
    }

    private fun createWebViewClient(): WebViewClient {
        return object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                emitNavigationState()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                onLoadEnd?.invoke()
                injectedJavaScript?.let { script ->
                    view?.evaluateJavascript(script, null)
                }
                emitNavigationState()
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return false
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: android.webkit.WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    error?.let {
                        onError?.invoke(it.errorCode, it.description?.toString() ?: "Unknown error")
                    }
                }
            }

            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                super.doUpdateVisitedHistory(view, url, isReload)
                emitNavigationState()
            }
        }
    }

    private fun createWebChromeClient(): WebChromeClient {
        return object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                onProgressChanged?.invoke(newProgress)
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                super.onReceivedTitle(view, title)
                title?.let { onTitleReceived?.invoke(it) }
            }

            // 支持 <input type="file">
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                fileChooserCallback = filePathCallback
                val acceptTypes = fileChooserParams?.acceptTypes ?: arrayOf("image/*")
                galleryMultiLauncher?.launch(acceptTypes)
                return true
            }
        }
    }

    @SuppressLint("ObsoleteSdkInt")
    private fun initFileChoosers() {
        val activity = (context as? ReactContext)?.currentActivity ?: return

        // 单选图片/视频 (Gallery)
        gallerySingleLauncher = activity.registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            uri?.let { sendFileToH5(it) }
        }

        // 多选图片/视频 (Gallery)
        galleryMultiLauncher = activity.registerForActivityResult(
            ActivityResultContracts.OpenMultipleDocuments()
        ) { uris: List<Uri> ->
            if (uris.isNotEmpty()) {
                // 发送第一个文件到 H5（主文件）
                sendFileToH5(uris[0])
                // 发送所有文件列表
                sendFilesToH5(uris)
            }
            fileChooserCallback?.onReceiveValue(uris.toTypedArray())
            fileChooserCallback = null
        }

        // 相机拍照
        cameraLauncher = activity.registerForActivityResult(
            ActivityResultContracts.TakePicture()
        ) { success: Boolean ->
            if (success) {
                cameraPhotoUri?.let { uri ->
                    sendFileToH5(uri)
                }
            }
        }
    }

    /**
     * 发送单个文件到 H5
     */
    private fun sendFileToH5(uri: Uri) {
        val fileInfo = getFileInfo(uri)
        postMessageToWebView("{\"type\":\"file_selected\",\"data\":${fileInfo}}")
    }

    /**
     * 发送多个文件到 H5
     */
    private fun sendFilesToH5(uris: List<Uri>) {
        val filesJson = uris.map { getFileInfo(it) }.joinToString(",", "[", "]")
        postMessageToWebView("{\"type\":\"files_selected\",\"data\":$filesJson}")
    }

    /**
     * 获取文件信息
     */
    private fun getFileInfo(uri: Uri): String {
        val context = context ?: return "{}"
        val contentResolver = context.contentResolver

        var name = "未知文件"
        var size = 0L
        var mimeType = "unknown"
        var duration: Long? = null

        // 获取文件名
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    name = cursor.getString(nameIndex) ?: name
                }
            }
        }

        // 获取 MIME 类型
        mimeType = contentResolver.getType(uri) ?: "unknown"

        // 获取文件大小
        try {
            contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                size = pfd.statSize
            }
        } catch (e: Exception) {
            // 忽略
        }

        // 如果是视频，获取时长
        if (mimeType.startsWith("video/")) {
            try {
                val retriever = android.media.MediaMetadataRetriever()
                retriever.setDataSource(context, uri)
                val durationStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                duration = durationStr?.toLongOrNull()
                retriever.release()
            } catch (e: Exception) {
                // 忽略
            }
        }

        return """{"uri":"$uri","name":"$name","size":$size,"type":"$mimeType","duration":${duration ?: "null"}}"""
    }

    /**
     * 创建相机拍照 URI
     */
    private fun createCameraUri(): Uri {
        val context = context ?: return Uri.EMPTY
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "IMG_${timeStamp}.jpg"

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ 使用 MediaStore
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/NativeWebView")
            }
            context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues) ?: Uri.EMPTY
        } else {
            // Android 9 使用文件
            val storageDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "NativeWebView")
            if (!storageDir.exists()) {
                storageDir.mkdirs()
            }
            Uri.fromFile(File(storageDir, fileName))
        }
    }

    private fun emitNavigationState() {
        webView?.let { wv ->
            onNavigationStateChange?.invoke(
                wv.canGoBack(),
                wv.canGoForward(),
                !wv.url.isNullOrEmpty()
            )
        }
    }

    // ==================== 公开 API ====================

    fun loadUrl(url: String) {
        webView?.loadUrl(url)
    }

    fun loadHtml(html: String) {
        webView?.loadData(html, "text/html", "UTF-8")
    }

    fun reload() {
        webView?.reload()
    }

    fun goBack() {
        webView?.goBack()
    }

    fun goForward() {
        webView?.goForward()
    }

    fun evaluateJavaScript(script: String, callback: ((String?) -> Unit)? = null) {
        webView?.evaluateJavascript(script) { result ->
            callback?.invoke(result)
        }
    }

    // ============ 相机/相册 API (供 JSBridge 调用) ============

    /**
     * 打开相机拍照
     */
    fun openCamera() {
        cameraPhotoUri = createCameraUri()
        cameraLauncher?.launch(cameraPhotoUri)
    }

    /**
     * 打开相册单选
     */
    fun openGallerySingle() {
        gallerySingleLauncher?.launch("image/*")
    }

    /**
     * 打开相册多选
     */
    fun openGalleryMulti() {
        galleryMultiLauncher?.launch(arrayOf("image/*", "video/*"))
    }

    /**
     * 打开相册选择图片或视频
     */
    fun openMediaPicker() {
        galleryMultiLauncher?.launch(arrayOf("image/*", "video/*"))
    }

    // ============ 事件回调设置 ============

    fun setOnProgressChanged(callback: (Int) -> Unit) {
        onProgressChanged = callback
    }

    fun setOnLoadEnd(callback: () -> Unit) {
        onLoadEnd = callback
    }

    fun setOnError(callback: (Int, String) -> Unit) {
        onError = callback
    }

    fun setOnTitleReceived(callback: (String) -> Unit) {
        onTitleReceived = callback
    }

    fun setOnNavigationStateChange(callback: (canGoBack: Boolean, canGoForward: Boolean, hasUrl: Boolean) -> Unit) {
        onNavigationStateChange = callback
    }

    fun setOnMessage(callback: (String) -> Unit) {
        onMessage = callback
    }

    // ============ JSBridge ============

    @SuppressLint("JavascriptInterface")
    private fun initJSBridge(webView: WebView) {
        jsBridge = WebViewJSBridge(webView) { message ->
            onMessage?.invoke(message)
        }
        webView.addJavascriptInterface(jsBridge!!, WebViewJSBridge.BRIDGE_NAME)
    }

    fun postMessageToWebView(message: String) {
        jsBridge?.sendToWebView(message)
    }

    // ============ 配置方法 ============

    fun setJavaScriptEnabled(enabled: Boolean) {
        javaScriptEnabled = enabled
        webView?.settings?.javaScriptEnabled = enabled
    }

    fun setDomStorageEnabled(enabled: Boolean) {
        domStorageEnabled = enabled
        webView?.settings?.domStorageEnabled = enabled
    }

    fun setCacheMode(mode: Int) {
        cacheMode = mode
        webView?.settings?.cacheMode = mode
    }

    fun setAllowFileAccess(enabled: Boolean) {
        allowFileAccess = enabled
        webView?.settings?.allowFileAccess = enabled
    }

    fun setInjectedJavaScript(script: String?) {
        injectedJavaScript = script
    }

    fun clearCache() {
        webView?.clearCache(true)
        webView?.clearHistory()
        webView?.clearFormData()
    }

    fun getWebView(): WebView? = webView

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        webView?.layout(0, 0, r - l, b - t)
    }

    fun destroy() {
        webView?.apply {
            stopLoading()
            clearHistory()
            clearCache(true)
            removeAllViews()
            destroy()
        }
        webView = null
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        clearCache()
    }
}
