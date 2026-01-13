# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

React Native 0.77.0 原生项目，支持 Android 平台。自研原生 WebView 实现（未使用第三方库）。

## 常用命令

| 命令 | 说明 |
|------|------|
| `npm start` | 启动 Metro 开发服务器 |
| `npm run android` | 运行 Android 应用 |

## 架构概览

```
┌─────────────────────────────────────────────────────────┐
│                    React Native 层                       │
│  App.tsx → HomeScreen → WebViewScreen / CacheSettings   │
│       │              │                                   │
│       └──────────────┼───────────────────────────────────┘
│                      ▼                                   │
│         ┌─────────────────────┐                          │
│         │ NativeWebView 组件   │                          │
│         │ (src/components/)   │                          │
│         └─────────┬───────────┘                          │
│                   │                                      │
└───────────────────┼──────────────────────────────────────┘
                    │
┌───────────────────▼──────────────────────────────────────┐
│                Android 原生模块                           │
│  ┌─────────────────────────────────────────────────────┐  │
│  │ NativeWebViewViewManager                            │  │
│  │ (ViewManager, 暴露属性/事件/命令)                    │  │
│  └─────────────────────────────────────────────────────┘  │
│                   │                                       │
│  ┌─────────────────────────────────────────────────────┐  │
│  │ NativeWebViewView                                   │  │
│  │ (WebView 封装类)                                    │  │
│  └─────────────────────────────────────────────────────┘  │
│                   │                                       │
│  ┌─────────────────────────────────────────────────────┐  │
│  │ WebViewCacheManager / WebViewCacheModule            │  │
│  │ (缓存管理)                                          │  │
│  └─────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
```

## 文件结构

```
android/app/src/main/java/com/nativewebview/webview/
├── NativeWebViewView.kt         # WebView 封装视图
├── NativeWebViewViewManager.kt  # React Native ViewManager
├── WebViewPackage.kt            # 包注册
├── WebViewCacheManager.kt       # OkHttp 缓存拦截器
├── WebViewCacheModule.kt        # 缓存 NativeModule
└── WebViewModule.kt             # 通用 NativeModule

src/
├── App.tsx                      # 应用入口
├── components/
│   └── NativeWebView.tsx        # RN 组件封装
├── screens/
│   ├── HomeScreen.tsx           # 首页
│   ├── WebViewScreen.tsx        # WebView 页面
│   └── CacheSettingsScreen.tsx  # 缓存设置页面
└── utils/
    └── webviewCache.ts          # 缓存工具
```

## 功能特性

| 功能 | 状态 | 说明 |
|------|------|------|
| 加载 URL | ✅ | 支持 uri 和 html |
| 加载进度回调 | ✅ | 带动画效果 |
| 页面标题回调 | ✅ | 自动获取 H5 标题 |
| 错误处理 | ✅ | 错误码和描述 |
| 缓存策略 | ✅ | 4 种缓存模式 |
| 缓存设置 UI | ✅ | 查看/清理缓存 |
| 导航控制 | ✅ | 后退/前进/刷新 |

## 缓存模式

```typescript
import { CacheMode } from 'NativeWebView';

CacheMode.LOAD_DEFAULT          // 正常模式，优先网络
CacheMode.LOAD_CACHE_ELSE_NETWORK // 有缓存就用
CacheMode.LOAD_NO_CACHE         // 只用网络
CacheMode.LOAD_CACHE_ONLY       // 只用缓存（离线）
```

## WebView 使用示例

```tsx
import NativeWebView, { CacheMode } from './components/NativeWebView';

<NativeWebView
  source={{ uri: 'https://example.com' }}
  cacheMode={CacheMode.LOAD_DEFAULT}
  onProgress={(progress) => console.log(progress)}
  onLoadEnd={() => console.log('loaded')}
  onTitle={(title) => setTitle(title)}
/>
```

## NativeModule API

```typescript
import { WebViewCache } from './utils/webviewCache';

// 获取缓存大小
const size = await WebViewCache.getCacheSize(); // "12.5 MB"

// 清理缓存
await WebViewCache.clearAllCache();
```
