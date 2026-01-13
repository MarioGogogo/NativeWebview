import React from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  SafeAreaView,
  StatusBar,
} from 'react-native';

/**
 * HomeScreen - 应用首页
 *
 * 提供按钮跳转到 WebView 页面
 */
interface HomeScreenProps {
  onOpenWebView: (url: string) => void;
  onOpenCacheSettings: () => void;
}

export function HomeScreen({ onOpenWebView, onOpenCacheSettings }: HomeScreenProps) {
  const demoUrls = [
    { name: '本地开发', url: 'file:///android_asset/web/index.html' },
    { name: 'React Native', url: 'https://reactnative.dev' },
    { name: '百度', url: 'https://www.baidu.com' },
    { name: 'GitHub', url: 'https://github.com' },
    { name: '空白页 (测试加载)', url: 'https://example.com' },
  ];

  return (
    <SafeAreaView style={styles.container}>
      <StatusBar barStyle="dark-content" />
      <View style={styles.content}>
        <Text style={styles.title}>Native WebView Demo</Text>
        <Text style={styles.subtitle}>点击下方链接打开 WebView</Text>

        <View style={styles.buttonList}>
          {demoUrls.map((item, index) => (
            <TouchableOpacity
              key={index}
              style={styles.button}
              onPress={() => onOpenWebView(item.url)}
              activeOpacity={0.7}>
              <Text style={styles.buttonText}>{item.name}</Text>
              <Text style={styles.buttonUrl} numberOfLines={1}>
                {item.url}
              </Text>
            </TouchableOpacity>
          ))}
        </View>

        {/* 缓存设置入口 */}
        <TouchableOpacity
          style={styles.cacheButton}
          onPress={onOpenCacheSettings}
          activeOpacity={0.7}>
          <Text style={styles.cacheButtonText}>⚙️ 缓存设置</Text>
        </TouchableOpacity>

        <View style={styles.infoSection}>
          <Text style={styles.infoTitle}>功能特性</Text>
          <Text style={styles.infoText}>✓ 原生 Android WebView (自研)</Text>
          <Text style={styles.infoText}>✓ 加载进度实时监听</Text>
          <Text style={styles.infoText}>✓ 缓存策略控制 (4种模式)</Text>
          <Text style={styles.infoText}>✓ JSBridge 双向通信</Text>
          <Text style={styles.infoText}>✓ 注入 JavaScript</Text>
          <Text style={styles.infoText}>✓ 前进/后退/刷新导航</Text>
        </View>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f5f5f5',
  },
  content: {
    flex: 1,
    padding: 20,
  },
  title: {
    fontSize: 28,
    fontWeight: 'bold',
    color: '#333',
    marginBottom: 8,
  },
  subtitle: {
    fontSize: 16,
    color: '#666',
    marginBottom: 24,
  },
  buttonList: {
    gap: 12,
    marginBottom: 16,
  },
  button: {
    backgroundColor: '#fff',
    padding: 16,
    borderRadius: 12,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 3,
  },
  buttonText: {
    fontSize: 18,
    fontWeight: '600',
    color: '#2196F3',
    marginBottom: 4,
  },
  buttonUrl: {
    fontSize: 12,
    color: '#999',
  },
  cacheButton: {
    backgroundColor: '#fff',
    padding: 16,
    borderRadius: 12,
    marginBottom: 16,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: '#e0e0e0',
  },
  cacheButtonText: {
    fontSize: 16,
    fontWeight: '600',
    color: '#666',
  },
  infoSection: {
    backgroundColor: '#fff',
    padding: 16,
    borderRadius: 12,
  },
  infoTitle: {
    fontSize: 16,
    fontWeight: '600',
    color: '#333',
    marginBottom: 12,
  },
  infoText: {
    fontSize: 14,
    color: '#666',
    marginBottom: 4,
  },
});
