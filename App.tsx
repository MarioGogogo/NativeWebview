/**
 * NativeWebview App
 * 自研原生 WebView 示例项目
 */
import React, { useState } from 'react';
import { HomeScreen } from './src/screens/HomeScreen';
import { WebViewScreen } from './src/screens/WebViewScreen';
import { CacheSettingsScreen } from './src/screens/CacheSettingsScreen';
import { CacheModeProvider } from './src/contexts/CacheModeContext';

type Screen = 'home' | 'webview' | 'cacheSettings';

export default function App(): React.JSX.Element {
  const [currentScreen, setCurrentScreen] = useState<Screen>('home');
  const [currentUrl, setCurrentUrl] = useState<string | null>(null);

  // 打开 WebView 页面
  const handleOpenWebView = (url: string) => {
    setCurrentUrl(url);
    setCurrentScreen('webview');
  };

  // 打开缓存设置
  const handleOpenCacheSettings = () => {
    setCurrentScreen('cacheSettings');
  };

  // 返回首页
  const handleGoBack = () => {
    setCurrentUrl(null);
    setCurrentScreen('home');
  };

  // 渲染当前页面
  const screenContent = (() => {
    switch (currentScreen) {
      case 'webview':
        if (currentUrl) {
          return <WebViewScreen url={currentUrl} onBack={handleGoBack} />;
        }
        return <HomeScreen onOpenWebView={handleOpenWebView} onOpenCacheSettings={handleOpenCacheSettings} />;

      case 'cacheSettings':
        return <CacheSettingsScreen onBack={handleGoBack} />;

      default:
        return <HomeScreen onOpenWebView={handleOpenWebView} onOpenCacheSettings={handleOpenCacheSettings} />;
    }
  })();

  return <CacheModeProvider>{screenContent}</CacheModeProvider>;
}
