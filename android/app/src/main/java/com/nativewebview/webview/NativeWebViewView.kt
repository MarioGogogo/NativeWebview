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
import com.facebook.react.bridge.ReactApplicationContext
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
    
    // 当前图片压缩目标大小 (KB)
    private var currentMaxFileSizeKB = 2048

    // 相机拍照压缩配置
    private var cameraMaxWidth = 1920
    private var cameraQuality = 85

    // Activity 结果监听器
    private val activityEventListener: ActivityEventListener = object : BaseActivityEventListener() {
        override fun onActivityResult(activity: Activity?, requestCode: Int, resultCode: Int, data: Intent?) {
            android.util.Log.d("NativeWebView", "========== onActivityResult START ==========")
            android.util.Log.d("NativeWebView", "onActivityResult - requestCode=$requestCode, resultCode=$resultCode, data=$data")
            when (requestCode) {
                REQUEST_CODE_FILE_CHOOSER -> {
                    android.util.Log.d("NativeWebView", "onActivityResult: handling file chooser result")
                    handleFileChooserResult(resultCode, data)
                }
                REQUEST_CODE_CAMERA -> {
                    android.util.Log.d("NativeWebView", "onActivityResult: handling camera result")
                    handleCameraResult(resultCode)
                }
                else -> {
                    android.util.Log.d("NativeWebView", "onActivityResult: unknown request code $requestCode")
                }
            }
            android.util.Log.d("NativeWebView", "========== onActivityResult END ==========")
        }
    }

    init {
        android.util.Log.d("NativeWebView", "NativeWebViewView init: adding activity event listener")
        android.util.Log.d("NativeWebView", "reactContext type: ${reactContext::class.simpleName}")

        // ThemedReactContext 包装了 ReactApplicationContext，需要正确获取
        val appContext: ReactApplicationContext? = when (reactContext) {
            is ReactApplicationContext -> reactContext
            is com.facebook.react.uimanager.ThemedReactContext -> {
                // ThemedReactContext 可以通过 getReactApplicationContext() 获取
                (reactContext as com.facebook.react.uimanager.ThemedReactContext).reactApplicationContext
            }
            else -> null
        }

        if (appContext != null) {
            android.util.Log.d("NativeWebView", "SUCCESS: Adding ActivityEventListener to ReactApplicationContext")
            appContext.addActivityEventListener(activityEventListener)
        } else {
            android.util.Log.e("NativeWebView", "ERROR: Cannot get ReactApplicationContext!")
        }

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

            // 拦截 console 日志用于调试
            override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                consoleMessage?.let {
                    android.util.Log.d("WebViewConsole", "[${it.sourceId()}:${it.lineNumber()}] ${it.message()}")
                }
                return super.onConsoleMessage(consoleMessage)
            }

            // 支持 <input type="file">
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                android.util.Log.d("NativeWebView", "onShowFileChooser: file input requested")
                // 取消之前的回调
                fileChooserCallback?.onReceiveValue(null)
                fileChooserCallback = filePathCallback

                val acceptTypes = fileChooserParams?.acceptTypes?.joinToString(",") ?: "*/*"
                android.util.Log.d("NativeWebView", "onShowFileChooser: acceptTypes=$acceptTypes")
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
        android.util.Log.d("NativeWebView", "========== handleFileChooserResult START ==========")
        android.util.Log.d("NativeWebView", "resultCode=$resultCode, data=$data")
        android.util.Log.d("NativeWebView", "fileChooserCallback exists: ${fileChooserCallback != null}")

        if (resultCode != Activity.RESULT_OK || data == null) {
            android.util.Log.d("NativeWebView", "File chooser cancelled or no data")
            fileChooserCallback?.onReceiveValue(null)
            fileChooserCallback = null
            return
        }

        val uris = mutableListOf<Uri>()

        // 处理自定义相册选择器的多选结果
        data.getStringArrayExtra(GalleryPickerActivity.EXTRA_SELECTED_URIS)?.let { uriStrings ->
            android.util.Log.d("NativeWebView", "Processing selected URIs from gallery: count=${uriStrings.size}")
            uriStrings.forEach { uriString ->
                val uri = Uri.parse(uriString)
                android.util.Log.d("NativeWebView", "Gallery selected uri: $uri")
                uris.add(uri)
            }
        }

        // 处理多选 (ClipData) - 系统文件选择器
        if (uris.isEmpty()) {
            data.clipData?.let { clipData ->
                android.util.Log.d("NativeWebView", "Processing ClipData: count=${clipData.itemCount}")
                for (i in 0 until clipData.itemCount) {
                    val uri = clipData.getItemAt(i).uri
                    android.util.Log.d("NativeWebView", "ClipData[$i] uri: $uri")
                    uri?.let { uris.add(it) }
                }
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

        // 如果有文件选择回调（来自 <input type="file">），则回调它
        fileChooserCallback?.let { callback ->
            android.util.Log.d("NativeWebView", "Found fileChooserCallback, calling it with ${uris.size} uris")
            callback.onReceiveValue(uris.toTypedArray())
            fileChooserCallback = null
            android.util.Log.d("NativeWebView", "========== handleFileChooserResult END (input callback) ==========")
            return  // 如果是 input 触发的，不通过 JSBridge 发送
        }

        android.util.Log.d("NativeWebView", "No fileChooserCallback, using JSBridge")

        // 否则，通过 JSBridge 发送到 H5（相册选择触发的情况）
        if (uris.isNotEmpty()) {
            android.util.Log.d("NativeWebView", "Sending files to H5 via JSBridge")
            // 发送到 H5
            if (uris.size == 1) {
                sendFileToH5(uris[0])
            } else {
                sendFilesToH5(uris)
            }
        } else {
            android.util.Log.w("NativeWebView", "No URIs found to send to H5!")
        }

        android.util.Log.d("NativeWebView", "========== handleFileChooserResult END (JSBridge) ==========")
    }

    /**
     * 打开相机拍照（带参数）
     */
    fun openCameraWithOptions(optionsJson: String) {
        try {
            val json = org.json.JSONObject(optionsJson)
            cameraMaxWidth = json.optInt("maxWidth", 1920)
            cameraQuality = json.optInt("quality", 85)
            android.util.Log.d("NativeWebView", "openCameraWithOptions: maxWidth=$cameraMaxWidth, quality=$cameraQuality")
        } catch (e: Exception) {
            android.util.Log.e("NativeWebView", "Failed to parse camera options", e)
        }
        openCamera()
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
        android.util.Log.d("NativeWebView", "========== openGallerySingle START ==========")
        val activity = reactContext.currentActivity ?: run {
            android.util.Log.e("NativeWebView", "openGallerySingle: no current activity!")
            return
        }

        // 清除 WebView 的文件选择回调，因为我们通过 JSBridge 处理
        fileChooserCallback = null
        android.util.Log.d("NativeWebView", "openGallerySingle: cleared fileChooserCallback")

        // 使用自定义的相册选择界面
        val intent = Intent(activity, GalleryPickerActivity::class.java)
        android.util.Log.d("NativeWebView", "openGallerySingle: starting custom gallery activity, REQUEST_CODE_FILE_CHOOSER=$REQUEST_CODE_FILE_CHOOSER")
        // 使用 reactContext.startActivityForResult 确保 ActivityResult 正确路由
        reactContext.startActivityForResult(intent, REQUEST_CODE_FILE_CHOOSER, null)
        android.util.Log.d("NativeWebView", "========== openGallerySingle END ==========")
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
        android.util.Log.d("NativeWebView", "========== sendFileToH5 START ==========")
        android.util.Log.d("NativeWebView", "uri=$uri")
        android.util.Log.d("NativeWebView", "uri scheme=${uri.scheme}, path=${uri.path}")

        val fileInfo = getFileInfo(uri)
        android.util.Log.d("NativeWebView", "fileInfo=$fileInfo")

        val message = "{\"type\":\"file_selected\",\"data\":${fileInfo}}"
        android.util.Log.d("NativeWebView", "message=$message")
        android.util.Log.d("NativeWebView", "message length=${message.length}")

        postMessageToWebView(message)
        android.util.Log.d("NativeWebView", "========== sendFileToH5 END ==========")
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
        android.util.Log.d("NativeWebView", "getFileInfo: uri=$uri")
        val contentResolver = context.contentResolver

        var name = "未知文件"
        var size = 0L
        var mimeType = "unknown"
        var duration: Long? = null
        var base64Data: String? = null

        // 获取文件名
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    name = cursor.getString(nameIndex) ?: name
                }
                android.util.Log.d("NativeWebView", "getFileInfo: name=$name")
            }
        }

        // 获取 MIME 类型
        mimeType = contentResolver.getType(uri) ?: "unknown"
        android.util.Log.d("NativeWebView", "getFileInfo: mimeType=$mimeType")

        // 获取文件大小
        try {
            contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                size = pfd.statSize
            }
            android.util.Log.d("NativeWebView", "getFileInfo: size=$size (${size / 1024}KB)")
        } catch (e: Exception) {
            android.util.Log.e("NativeWebView", "getFileInfo: error getting size", e)
        }

        // 如果是图片，压缩后转换为 Base64（避免大图片导致 OOM）
        if (mimeType.startsWith("image/")) {
            android.util.Log.d("NativeWebView", "getFileInfo: converting image to base64 with compression...")
            try {
                // 使用相机配置（针对拍照）或相册默认配置
                val maxWidth = if (cameraPhotoUri != null && uri == cameraPhotoUri) cameraMaxWidth else 1920
                val quality = if (cameraPhotoUri != null && uri == cameraPhotoUri) cameraQuality else 85
                
                base64Data = compressImageToBase64(uri, maxWidth, quality)
                android.util.Log.d("NativeWebView", "getFileInfo: compressed base64Data length=${base64Data?.length ?: 0}")
            } catch (e: Exception) {
                android.util.Log.e("NativeWebView", "Failed to compress image to base64", e)
            }
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

        // 构建 JSON，如果有 Base64 数据则使用 Base64，否则使用原始 URI
        val uriField = if (base64Data != null) {
            android.util.Log.d("NativeWebView", "getFileInfo: using base64 data URI")
            "\"data:image/jpeg;base64,$base64Data\""
        } else {
            android.util.Log.d("NativeWebView", "getFileInfo: using original URI: $uri")
            "\"$uri\""
        }

        val result = """{"uri":$uriField,"name":"$name","size":$size,"type":"$mimeType","duration":${duration ?: "null"}}"""
        android.util.Log.d("NativeWebView", "getFileInfo: result length=${result.length}")
        return result
    }

    /**
     * 压缩图片并转换为 Base64
     * @param uri 图片 URI
     * @param maxWidth 最大宽度（按比例缩放）
     * @param quality JPEG 压缩质量 (0-100)
     */
    private fun compressImageToBase64(uri: Uri, maxWidth: Int, quality: Int): String? {
        return try {
            // 1. 先获取图片尺寸（不加载到内存）
            val options = android.graphics.BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use { input ->
                android.graphics.BitmapFactory.decodeStream(input, null, options)
            }

            val imageWidth = options.outWidth
            val imageHeight = options.outHeight
            android.util.Log.d("NativeWebView", "compressImage: original size ${imageWidth}x${imageHeight}")

            // 2. 计算采样率（降低内存占用）
            var inSampleSize = 1
            if (imageWidth > maxWidth) {
                inSampleSize = imageWidth / maxWidth
            }

            // 3. 加载缩放后的图片
            val decodeOptions = android.graphics.BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
            }
            val bitmap = context.contentResolver.openInputStream(uri)?.use { input ->
                android.graphics.BitmapFactory.decodeStream(input, null, decodeOptions)
            } ?: return null

            android.util.Log.d("NativeWebView", "compressImage: decoded size ${bitmap.width}x${bitmap.height}")

            // 4. 如果还是太大，进一步缩放
            val finalBitmap = if (bitmap.width > maxWidth) {
                val scale = maxWidth.toFloat() / bitmap.width
                val newHeight = (bitmap.height * scale).toInt()
                android.graphics.Bitmap.createScaledBitmap(bitmap, maxWidth, newHeight, true).also {
                    if (it != bitmap) bitmap.recycle()
                }
            } else {
                bitmap
            }

            android.util.Log.d("NativeWebView", "compressImage: final size ${finalBitmap.width}x${finalBitmap.height}")

            // 5. 压缩为 JPEG 并转 Base64
            val outputStream = java.io.ByteArrayOutputStream()
            finalBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, outputStream)
            finalBitmap.recycle()

            val base64 = android.util.Base64.encodeToString(outputStream.toByteArray(), android.util.Base64.NO_WRAP)
            android.util.Log.d("NativeWebView", "compressImage: base64 length=${base64.length} (~${base64.length / 1024}KB)")
            
            base64
        } catch (e: Exception) {
            android.util.Log.e("NativeWebView", "compressImage failed", e)
            null
        }
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
            },
            onOpenGalleryWithOptions = { optionsJson ->
                openGalleryWithOptions(optionsJson)
            },
            onOpenCameraWithOptions = { optionsJson ->
                openCameraWithOptions(optionsJson)
            }
        )
        webView.addJavascriptInterface(jsBridge!!, WebViewJSBridge.BRIDGE_NAME)
    }

    /**
     * 打开相册（可配置参数）
     * @param optionsJson JSON 配置: {"maxCount": 1, "maxSizeKB": 2048, "photosOnly": true}
     */
    fun openGalleryWithOptions(optionsJson: String) {
        android.util.Log.d("NativeWebView", "openGalleryWithOptions: $optionsJson")
        val activity = reactContext.currentActivity ?: run {
            android.util.Log.e("NativeWebView", "openGalleryWithOptions: no current activity!")
            return
        }

        // 清除 WebView 的文件选择回调，因为我们通过 JSBridge 处理
        fileChooserCallback = null

        // 解析 JSON 参数
        var maxCount = 1
        var maxSizeKB = 2048
        var photosOnly = true

        try {
            val json = org.json.JSONObject(optionsJson)
            maxCount = json.optInt("maxCount", 1)
            maxSizeKB = json.optInt("maxSizeKB", 2048)
            photosOnly = json.optBoolean("photosOnly", true)
        } catch (e: Exception) {
            android.util.Log.e("NativeWebView", "Failed to parse options JSON", e)
        }

        android.util.Log.d("NativeWebView", "openGalleryWithOptions: maxCount=$maxCount, maxSizeKB=$maxSizeKB, photosOnly=$photosOnly")

        // 保存最大文件大小用于压缩
        currentMaxFileSizeKB = maxSizeKB

        // 启动 GalleryPickerActivity
        val intent = Intent(activity, GalleryPickerActivity::class.java).apply {
            putExtra(GalleryPickerActivity.EXTRA_MAX_SELECTION, maxCount)
            putExtra(GalleryPickerActivity.EXTRA_PHOTOS_ONLY, photosOnly)
            putExtra(GalleryPickerActivity.EXTRA_MAX_FILE_SIZE, maxSizeKB)
        }
        reactContext.startActivityForResult(intent, REQUEST_CODE_FILE_CHOOSER, null)
    }

    fun postMessageToWebView(message: String) {
        android.util.Log.d("NativeWebView", "postMessageToWebView: message=$message")
        android.util.Log.d("NativeWebView", "jsBridge exists: ${jsBridge != null}")
        jsBridge?.sendToWebView(message)
        android.util.Log.d("NativeWebView", "postMessageToWebView: sent to jsBridge")
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
