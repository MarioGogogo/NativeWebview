package com.nativewebview.webview

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
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
import com.facebook.react.bridge.ActivityEventListener
import com.facebook.react.bridge.BaseActivityEventListener
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
class NativeWebViewView(private val reactContext: ReactContext) : ViewGroup(reactContext) {

    companion object {
        private const val REQUEST_CODE_FILE_CHOOSER = 1001
        private const val REQUEST_CODE_CAMERA = 1002
    }

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

    // 临时相机拍照 URI
    private var cameraPhotoUri: Uri? = null

    // WebView 配置
    private var javaScriptEnabled = true
    private var domStorageEnabled = true
    private var cacheMode = WebSettings.LOAD_DEFAULT
    private var allowFileAccess = true
    private var injectedJavaScript: String? = null

    // Activity 结果监听器
    private val activityEventListener: ActivityEventListener = object : BaseActivityEventListener() {
        override fun onActivityResult(activity: Activity?, requestCode: Int, resultCode: Int, data: Intent?) {
            android.util.Log.d("NativeWebView", "activityEventListener: onActivityResult - requestCode=$requestCode, resultCode=$resultCode")
            when (requestCode) {
                REQUEST_CODE_FILE_CHOOSER -> {
                    handleFileChooserResult(resultCode, data)
                }
                REQUEST_CODE_CAMERA -> {
                    handleCameraResult(resultCode)
                }
            }
        }
    }

    init {
        reactContext.addActivityEventListener(activityEventListener)
        createWebView()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView() {
        webView = WebView(context).apply {
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
                @Suppress("DEPRECATION")
                savePassword = false
                @Suppress("DEPRECATION")
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

            webViewClient = createWebViewClient()
            webChromeClient = createWebChromeClient()

            // 初始化 JSBridge
            initJSBridge(this)

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
                // 取消之前的回调
                fileChooserCallback?.onReceiveValue(null)
                fileChooserCallback = filePathCallback

                val acceptTypes = fileChooserParams?.acceptTypes?.joinToString(",") ?: "*/*"
                openFileChooser(acceptTypes)
                return true
            }
        }
    }

    /**
     * 打开文件选择器
     */
    private fun openFileChooser(acceptTypes: String) {
        val activity = reactContext.currentActivity ?: return
        
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = if (acceptTypes.isNotEmpty() && acceptTypes != "*/*") {
                acceptTypes.split(",").firstOrNull()?.trim() ?: "*/*"
            } else {
                "*/*"
            }
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        
        val chooserIntent = Intent.createChooser(intent, "选择文件")
        reactContext.startActivityForResult(chooserIntent, REQUEST_CODE_FILE_CHOOSER, null)
    }

    /**
     * 处理文件选择器结果
     */
    private fun handleFileChooserResult(resultCode: Int, data: Intent?) {
        android.util.Log.d("NativeWebView", "handleFileChooserResult: resultCode=$resultCode, data=$data")
        if (resultCode != Activity.RESULT_OK || data == null) {
            android.util.Log.d("NativeWebView", "File chooser cancelled or no data")
            fileChooserCallback?.onReceiveValue(null)
            fileChooserCallback = null
            return
        }

        val uris = mutableListOf<Uri>()
        
        // 处理多选 (ClipData)
        data.clipData?.let { clipData ->
            android.util.Log.d("NativeWebView", "Processing ClipData: count=${clipData.itemCount}")
            for (i in 0 until clipData.itemCount) {
                clipData.getItemAt(i).uri?.let { uris.add(it) }
            }
        }
        
        // 处理单选 (data.data)
        if (uris.isEmpty()) {
            data.data?.let { 
                android.util.Log.d("NativeWebView", "Processing single data Uri: $it")
                uris.add(it) 
            }
        }

        android.util.Log.d("NativeWebView", "Total Uris found: ${uris.size}")

        if (uris.isNotEmpty()) {
            // 发送到 H5
            if (uris.size == 1) {
                sendFileToH5(uris[0])
            } else {
                sendFilesToH5(uris)
            }
        }

        // WebView 文件输入框回调
        fileChooserCallback?.onReceiveValue(uris.toTypedArray())
        fileChooserCallback = null
    }

    /**
     * 打开相机拍照
     */
    fun openCamera() {
        val activity = reactContext.currentActivity ?: return
        
        cameraPhotoUri = createCameraUri()
        if (cameraPhotoUri == null || cameraPhotoUri == Uri.EMPTY) return
        
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, cameraPhotoUri)
        }
        
        reactContext.startActivityForResult(intent, REQUEST_CODE_CAMERA, null)
    }

    /**
     * 处理相机拍照结果
     */
    private fun handleCameraResult(resultCode: Int) {
        if (resultCode == Activity.RESULT_OK) {
            cameraPhotoUri?.let { uri ->
                sendFileToH5(uri)
            }
        }
        cameraPhotoUri = null
    }

    /**
     * 打开相册单选
     */
    fun openGallerySingle() {
        val activity = reactContext.currentActivity ?: return

        fileChooserCallback = null
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        reactContext.startActivityForResult(intent, REQUEST_CODE_FILE_CHOOSER, null)
    }

    /**
     * 打开相册多选
     */
    fun openGalleryMulti() {
        openFileChooser("image/*,video/*")
    }

    /**
     * 打开相册选择图片或视频
     */
    fun openMediaPicker() {
        openFileChooser("image/*,video/*")
    }

    /**
     * 发送单个文件到 H5
     */
    private fun sendFileToH5(uri: Uri) {
        android.util.Log.d("NativeWebView", "sendFileToH5: uri=$uri")
        val fileInfo = getFileInfo(uri)
        android.util.Log.d("NativeWebView", "sendFileToH5: fileInfo=$fileInfo")
        val message = "{\"type\":\"file_selected\",\"data\":${fileInfo}}"
        android.util.Log.d("NativeWebView", "sendFileToH5: message=$message")
        postMessageToWebView(message)
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
    private fun createCameraUri(): Uri? {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "IMG_${timeStamp}.jpg"

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ 使用 MediaStore
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/NativeWebView")
            }
            context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        } else {
            // Android 9 使用文件
            @Suppress("DEPRECATION")
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

    fun stopLoading() {
        webView?.stopLoading()
    }

    fun evaluateJavaScript(script: String, callback: ((String?) -> Unit)? = null) {
        webView?.evaluateJavascript(script) { result ->
            callback?.invoke(result)
        }
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
        jsBridge = WebViewJSBridge(
            webView = webView,
            onMessage = { message ->
                onMessage?.invoke(message)
            },
            onOpenCamera = {
                openCamera()
            },
            onOpenGallerySingle = {
                openGallerySingle()
            },
            onOpenGalleryMulti = {
                openGalleryMulti()
            }
        )
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
        reactContext.removeActivityEventListener(activityEventListener)
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
        // 不要在这里清理缓存，避免不必要的开销
    }
}
