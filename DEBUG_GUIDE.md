# 相册选择调试指南

## 如何调试

### 1. 启动应用并查看日志

```bash
# 清除之前的日志
adb logcat -c

# 实时查看日志
adb logcat | grep -E "NativeWebView|WebViewJSBridge|WebViewConsole"
```

### 2. 操作步骤

1. 点击"相册单选"按钮
2. 选择一张图片
3. 观察日志输出

### 3. 预期的日志流程

#### 步骤 1: 点击相册按钮
```
NativeWebView: ========== openGallerySingle START ==========
NativeWebView: openGallerySingle: cleared fileChooserCallback
NativeWebView: openGallerySingle: starting activity for result, REQUEST_CODE_FILE_CHOOSER=1001
NativeWebView: ========== openGallerySingle END ==========
```

#### 步骤 2: 选择图片后返回
```
NativeWebView: ========== onActivityResult START ==========
NativeWebView: onActivityResult - requestCode=1001, resultCode=-1, data=Intent { ... }
NativeWebView: onActivityResult: handling file chooser result
NativeWebView: ========== handleFileChooserResult START ==========
NativeWebView: resultCode=-1, data=Intent { ... }
NativeWebView: fileChooserCallback exists: false  <-- 重要！应该是 false
NativeWebView: Processing single data Uri: content://...
NativeWebView: Total Uris found: 1
NativeWebView: No fileChooserCallback, using JSBridge  <-- 重要！走 JSBridge 路径
NativeWebView: Sending files to H5 via JSBridge
```

#### 步骤 3: 发送文件信息到 H5
```
NativeWebView: ========== sendFileToH5 START ==========
NativeWebView: uri=content://...
NativeWebView: uri scheme=content, path=...
NativeWebView: getFileInfo: uri=content://...
NativeWebView: getFileInfo: name=xxx.jpg
NativeWebView: getFileInfo: mimeType=image/jpeg
NativeWebView: getFileInfo: size=xxxxxx (xxxKB)
NativeWebView: getFileInfo: converting image to base64...
NativeWebView: getFileInfo: base64Data length=xxxxxx
NativeWebView: getFileInfo: using base64 data URI
NativeWebView: getFileInfo: result={"uri":"data:image/jpeg;base64,...","name":"xxx.jpg",...}
NativeWebView: message={"type":"file_selected","data":{...}}
NativeWebView: message length=xxxxx
NativeWebView: postMessageToWebView: message={"type":"file_selected",...}
NativeWebView: jsBridge exists: true
NativeWebView: postMessageToWebView: sent to jsBridge
NativeWebView: ========== sendFileToH5 END ==========
```

#### 步骤 4: JSBridge 发送消息到 H5
```
WebViewJSBridge: sendToWebView: {"type":"file_selected","data":{...}}
```

#### 步骤 5: H5 接收消息 (如果成功)
```
WebViewConsole: [...] [NativeBridge] Received: {"type":"file_selected",...}
WebViewConsole: [...] 📥 收到: {"type":"file_selected",...}
```

### 4. 问题诊断

#### 如果 `fileChooserCallback exists: true`
- 说明有残留的 `fileChooserCallback`
- 问题在于没有正确清除回调
- 检查是否还有 `<input type="file">` 元素触发了选择器

#### 如果 `Total Uris found: 0`
- 说明没有获取到 URI
- 检查 `data.data` 和 `data.clipData` 是否都为空
- 可能是权限问题或 Intent 问题

#### 如果 H5 页面没有显示图片
- 检查 H5 的 `window.onNativeMessage` 是否被调用
- 检查 `addMediaItem` 函数是否正常执行
- 检查 Base64 数据是否正确

### 5. 手动测试命令

在 H5 页面的控制台中执行：
```javascript
// 测试 JSBridge 是否可用
console.log('NativeBridge:', typeof window.NativeBridge);

// 测试消息接收
window.onNativeMessage = function(data) {
    console.log('Received:', data);
};

// 手动触发相册
window.NativeBridge.openGallerySingle();
```

## 常见问题

### Q: 为什么照片选择后没有显示？
A: 可能的原因：
1. `fileChooserCallback` 没有被清除，导致走了 input 回调路径
2. Base64 转换失败
3. H5 页面的 `window.onNativeMessage` 没有正确设置
4. JSON 解析失败

### Q: 如何查看详细的 Base64 数据？
A: 在 logcat 中搜索 "base64Data length"，如果长度很大（>100000），说明转换成功。

### Q: H5 的 console.log 不显示？
A: 现在所有的 H5 console.log 都会通过 `WebViewConsole` 标签输出到 logcat。
