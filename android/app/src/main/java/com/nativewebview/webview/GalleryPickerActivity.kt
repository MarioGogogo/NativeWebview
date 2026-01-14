package com.nativewebview.webview

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 微信风格自定义相册选择界面
 *
 * 功能：
 * - 4列网格布局
 * - 圆形选择器（右上角，选中显示序号）
 * - 多选支持
 * - 视频时长显示
 * - 底部操作栏（预览、完成）
 */
class GalleryPickerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SELECTED_URIS = "selected_uris"
        const val EXTRA_MAX_SELECTION = "max_selection"
        const val EXTRA_PHOTOS_ONLY = "photos_only"
        const val EXTRA_MAX_FILE_SIZE = "max_file_size"  // 单位: KB
        const val REQUEST_CODE_PERMISSIONS = 1001
        private const val TAG = "GalleryPicker"
        private const val DEFAULT_MAX_SELECTION = 9
        private const val DEFAULT_MAX_FILE_SIZE = 2048  // 默认 2MB
    }

    private lateinit var recyclerView: RecyclerView
    private lateinit var titleText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyText: TextView
    private lateinit var confirmButton: TextView
    private lateinit var previewButton: TextView

    private val mediaList = mutableListOf<MediaItem>()
    private val selectedItems = LinkedHashMap<Long, Int>()  // id -> selection order
    private var maxSelection = DEFAULT_MAX_SELECTION
    private var photosOnly = true  // 默认只显示图片
    private var maxFileSizeKB = DEFAULT_MAX_FILE_SIZE  // 最大文件大小 (KB)
    private var job: Job? = null
    private lateinit var adapter: MediaAdapter

    // 主题颜色
    private var isDarkTheme = false
    private var backgroundColor: Int = Color.WHITE
    private var surfaceColor: Int = Color.parseColor("#F7F7F7")
    private var textColor: Int = Color.BLACK
    private var textSecondaryColor: Int = Color.GRAY
    private val wechatGreen: Int = Color.parseColor("#07C160")

    data class MediaItem(
        val uri: android.net.Uri,
        val id: Long,
        val name: String,
        val isVideo: Boolean = false,
        val duration: Long = 0,  // 毫秒
        val sizeKB: Long = 0     // 文件大小 (KB)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        maxSelection = intent.getIntExtra(EXTRA_MAX_SELECTION, DEFAULT_MAX_SELECTION)
        photosOnly = intent.getBooleanExtra(EXTRA_PHOTOS_ONLY, true)
        maxFileSizeKB = intent.getIntExtra(EXTRA_MAX_FILE_SIZE, DEFAULT_MAX_FILE_SIZE)
        
        android.util.Log.d(TAG, "onCreate: maxSelection=$maxSelection, photosOnly=$photosOnly, maxFileSizeKB=$maxFileSizeKB")
        
        setupThemeColors()
        createLayout()

        if (checkPermissions()) {
            loadMedia()
        } else {
            requestPermissions()
        }
    }

    private fun setupThemeColors() {
        val nightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        isDarkTheme = nightMode == Configuration.UI_MODE_NIGHT_YES
        
        if (isDarkTheme) {
            backgroundColor = Color.parseColor("#1A1A1A")
            surfaceColor = Color.parseColor("#2A2A2A")
            textColor = Color.WHITE
            textSecondaryColor = Color.parseColor("#AAAAAA")
        } else {
            backgroundColor = Color.WHITE
            surfaceColor = Color.parseColor("#F7F7F7")
            textColor = Color.BLACK
            textSecondaryColor = Color.GRAY
        }

        // 设置状态栏颜色与顶部导航栏一致
        window.statusBarColor = surfaceColor
        
        // 设置状态栏图标颜色
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val decorView = window.decorView
            var flags = decorView.systemUiVisibility
            if (!isDarkTheme) {
                // 亮色模式下使用深色图标
                flags = flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            } else {
                // 暗色模式下使用浅色图标
                flags = flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
            }
            decorView.systemUiVisibility = flags
        }
    }

    private fun createLayout() {
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(backgroundColor)
        }

        // 顶部栏
        rootLayout.addView(createTopBar())

        // 主内容区域（使用 FrameLayout 层叠）
        val contentFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        // RecyclerView
        recyclerView = RecyclerView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(backgroundColor)
            layoutManager = GridLayoutManager(this@GalleryPickerActivity, 4)
            clipToPadding = false
            setPadding(dpToPx(2), dpToPx(2), dpToPx(2), dpToPx(2))
        }
        contentFrame.addView(recyclerView)

        // 进度条
        progressBar = ProgressBar(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                dpToPx(48),
                dpToPx(48)
            ).apply {
                gravity = Gravity.CENTER
            }
            visibility = View.GONE
        }
        contentFrame.addView(progressBar)

        // 空状态文本
        emptyText = TextView(this).apply {
            text = "没有找到图片或视频"
            textSize = 16f
            setTextColor(textSecondaryColor)
            gravity = Gravity.CENTER
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        contentFrame.addView(emptyText)

        rootLayout.addView(contentFrame)

        // 底部操作栏
        rootLayout.addView(createBottomBar())

        setContentView(rootLayout)

        // 初始化 Adapter
        adapter = MediaAdapter()
        recyclerView.adapter = adapter
    }

    private fun createTopBar(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(56)
            )
            setBackgroundColor(surfaceColor)
            setPadding(dpToPx(16), 0, dpToPx(16), 0)
            gravity = Gravity.CENTER_VERTICAL

            // 关闭按钮
            val closeButton = TextView(this@GalleryPickerActivity).apply {
                text = "✕"
                textSize = 20f
                setTextColor(textColor)
                setPadding(dpToPx(8), dpToPx(8), dpToPx(16), dpToPx(8))
                setOnClickListener { finish() }
            }
            addView(closeButton)

            // 标题（根据模式显示不同文字）
            titleText = TextView(this@GalleryPickerActivity).apply {
                text = if (photosOnly) "选择图片" else "图片和视频 ▾"
                textSize = 17f
                setTextColor(textColor)
                typeface = Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                gravity = Gravity.CENTER
            }
            addView(titleText)

            // 搜索图标占位
            val placeholder = View(this@GalleryPickerActivity).apply {
                layoutParams = LinearLayout.LayoutParams(dpToPx(40), dpToPx(40))
            }
            addView(placeholder)
        }
    }

    private fun createBottomBar(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(56)
            )
            setBackgroundColor(surfaceColor)
            setPadding(dpToPx(16), 0, dpToPx(16), 0)
            gravity = Gravity.CENTER_VERTICAL

            // 预览按钮
            previewButton = TextView(this@GalleryPickerActivity).apply {
                text = "预览"
                textSize = 16f
                setTextColor(textSecondaryColor)
                isEnabled = false
                setPadding(dpToPx(8), dpToPx(12), dpToPx(8), dpToPx(12))
            }
            addView(previewButton)

            // 空白占位
            val spacer = View(this@GalleryPickerActivity).apply {
                layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
            }
            addView(spacer)

            // 完成按钮
            confirmButton = TextView(this@GalleryPickerActivity).apply {
                text = "完成"
                textSize = 15f
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dpToPx(20), dpToPx(8), dpToPx(20), dpToPx(8))
                gravity = Gravity.CENTER
                isEnabled = false
                alpha = 0.5f
                background = GradientDrawable().apply {
                    cornerRadius = dpToPx(4).toFloat()
                    setColor(wechatGreen)
                }
                setOnClickListener { confirmSelection() }
            }
            addView(confirmButton)
        }
    }

    private fun checkPermissions(): Boolean {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        return permissions.isEmpty()
    }

    private fun requestPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        ActivityCompat.requestPermissions(this, permissions, REQUEST_CODE_PERMISSIONS)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadMedia()
            } else {
                Toast.makeText(this, "需要权限才能访问相册", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun loadMedia() {
        progressBar.visibility = View.VISIBLE
        emptyText.visibility = View.GONE
        mediaList.clear()

        job = CoroutineScope(Dispatchers.Main).launch {
            try {
                val media = withContext(Dispatchers.IO) {
                    loadMediaFromGallery()
                }

                progressBar.visibility = View.GONE

                if (media.isEmpty()) {
                    emptyText.visibility = View.VISIBLE
                } else {
                    mediaList.addAll(media)
                    adapter.notifyDataSetChanged()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading media", e)
                progressBar.visibility = View.GONE
                emptyText.visibility = View.VISIBLE
                Toast.makeText(this@GalleryPickerActivity, "加载失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadMediaFromGallery(): List<MediaItem> {
        val list = mutableListOf<MediaItem>()

        // 加载图片
        loadImages(list)

        // 仅当 photosOnly=false 时加载视频
        if (!photosOnly) {
            loadVideos(list)
        }

        // 按日期排序
        list.sortByDescending { it.id }

        return list.take(500)  // 限制数量
    }

    private fun loadImages(list: MutableList<MediaItem>) {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.SIZE
        )

        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)

            while (cursor.moveToNext() && list.size < 500) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn) ?: ""
                val sizeBytes = cursor.getLong(sizeColumn)
                val sizeKB = sizeBytes / 1024
                val uri = android.net.Uri.withAppendedPath(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id.toString()
                )
                list.add(MediaItem(uri, id, name, false, 0, sizeKB))
            }
        }
    }

    private fun loadVideos(list: MutableList<MediaItem>) {
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.DATE_ADDED
        )

        contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            "${MediaStore.Video.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)

            while (cursor.moveToNext() && list.size < 500) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn) ?: ""
                val duration = cursor.getLong(durationColumn)
                val uri = android.net.Uri.withAppendedPath(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    id.toString()
                )
                list.add(MediaItem(uri, id, name, true, duration))
            }
        }
    }

    private fun toggleSelection(item: MediaItem) {
        if (selectedItems.containsKey(item.id)) {
            // 取消选择
            selectedItems.remove(item.id)
            // 重新排序
            reorderSelection()
        } else {
            // 检查文件大小
            if (item.sizeKB > maxFileSizeKB) {
                val sizeMB = String.format("%.1f", item.sizeKB / 1024.0)
                val limitMB = String.format("%.1f", maxFileSizeKB / 1024.0)
                Toast.makeText(this, "图片过大 (${sizeMB}MB)，请选择小于 ${limitMB}MB 的图片", Toast.LENGTH_SHORT).show()
                return
            }
            // 检查选择数量
            if (selectedItems.size >= maxSelection) {
                Toast.makeText(this, "最多选择 $maxSelection 项", Toast.LENGTH_SHORT).show()
                return
            }
            selectedItems[item.id] = selectedItems.size + 1
        }

        updateBottomBar()
        adapter.notifyDataSetChanged()
    }

    private fun reorderSelection() {
        val entries = selectedItems.entries.toList()
        selectedItems.clear()
        entries.forEachIndexed { index, entry ->
            selectedItems[entry.key] = index + 1
        }
    }

    private fun updateBottomBar() {
        val count = selectedItems.size
        if (count > 0) {
            confirmButton.text = "完成($count)"
            confirmButton.isEnabled = true
            confirmButton.alpha = 1f
            previewButton.isEnabled = true
            previewButton.setTextColor(textColor)
        } else {
            confirmButton.text = "完成"
            confirmButton.isEnabled = false
            confirmButton.alpha = 0.5f
            previewButton.isEnabled = false
            previewButton.setTextColor(textSecondaryColor)
        }
    }

    private fun confirmSelection() {
        if (selectedItems.isEmpty()) return

        val selectedUris = selectedItems.keys
            .mapNotNull { id -> mediaList.find { it.id == id }?.uri?.toString() }
            .toTypedArray()

        val intent = intent.apply {
            putExtra(EXTRA_SELECTED_URIS, selectedUris)
        }
        setResult(RESULT_OK, intent)
        finish()
    }

    private fun formatDuration(millis: Long): String {
        val seconds = (millis / 1000) % 60
        val minutes = (millis / 1000) / 60
        return "$minutes:${String.format("%02d", seconds)}"
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    override fun onDestroy() {
        super.onDestroy()
        job?.cancel()
    }

    // ==================== RecyclerView Adapter ====================

    inner class MediaAdapter : RecyclerView.Adapter<MediaViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaViewHolder {
            val itemSize = (resources.displayMetrics.widthPixels - dpToPx(10)) / 4

            val container = FrameLayout(parent.context).apply {
                layoutParams = ViewGroup.MarginLayoutParams(itemSize, itemSize).apply {
                    val margin = dpToPx(1)
                    setMargins(margin, margin, margin, margin)
                }
            }

            // 图片
            val imageView = ImageView(parent.context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                scaleType = ImageView.ScaleType.CENTER_CROP
            }
            container.addView(imageView)

            // 选中遮罩
            val overlay = View(parent.context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(Color.parseColor("#40000000"))
                visibility = View.GONE
            }
            container.addView(overlay)

            // 视频时长标签
            val durationLabel = TextView(parent.context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.BOTTOM or Gravity.START
                    setMargins(dpToPx(4), 0, 0, dpToPx(4))
                }
                textSize = 11f
                setTextColor(Color.WHITE)
                visibility = View.GONE
                setShadowLayer(2f, 1f, 1f, Color.BLACK)
            }
            container.addView(durationLabel)

            // 选择圆圈容器
            val checkSize = dpToPx(24)
            val checkContainer = FrameLayout(parent.context).apply {
                layoutParams = FrameLayout.LayoutParams(checkSize, checkSize).apply {
                    gravity = Gravity.TOP or Gravity.END
                    setMargins(0, dpToPx(6), dpToPx(6), 0)
                }
            }

            // 空心圆
            val emptyCircle = View(parent.context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setStroke(dpToPx(2), Color.parseColor("#B0FFFFFF"))
                    setColor(Color.parseColor("#40000000"))
                }
            }
            checkContainer.addView(emptyCircle)

            // 选中数字
            val numberText = TextView(parent.context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                gravity = Gravity.CENTER
                textSize = 12f
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
                visibility = View.GONE
            }
            checkContainer.addView(numberText)

            container.addView(checkContainer)

            return MediaViewHolder(container, imageView, overlay, durationLabel, checkContainer, emptyCircle, numberText)
        }

        override fun onBindViewHolder(holder: MediaViewHolder, position: Int) {
            val item = mediaList[position]
            
            // 加载图片
            Glide.with(holder.imageView)
                .load(item.uri)
                .apply(RequestOptions().transform(CenterCrop()))
                .into(holder.imageView)

            // 视频时长
            if (item.isVideo) {
                holder.durationLabel.text = formatDuration(item.duration)
                holder.durationLabel.visibility = View.VISIBLE
            } else {
                holder.durationLabel.visibility = View.GONE
            }

            // 选中状态
            val selectionOrder = selectedItems[item.id]
            if (selectionOrder != null) {
                holder.overlay.visibility = View.VISIBLE
                holder.emptyCircle.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(wechatGreen)
                    setStroke(dpToPx(1), Color.WHITE)
                }
                holder.numberText.text = selectionOrder.toString()
                holder.numberText.visibility = View.VISIBLE
            } else {
                holder.overlay.visibility = View.GONE
                holder.emptyCircle.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setStroke(dpToPx(2), Color.parseColor("#B0FFFFFF"))
                    setColor(Color.parseColor("#40000000"))
                }
                holder.numberText.visibility = View.GONE
            }

            // 点击事件
            holder.container.setOnClickListener {
                toggleSelection(item)
            }
        }

        override fun getItemCount(): Int = mediaList.size
    }

    class MediaViewHolder(
        val container: FrameLayout,
        val imageView: ImageView,
        val overlay: View,
        val durationLabel: TextView,
        val checkContainer: FrameLayout,
        val emptyCircle: View,
        val numberText: TextView
    ) : RecyclerView.ViewHolder(container)
}
