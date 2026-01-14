import React, { useState, useEffect, useCallback } from 'react';
import {
  View,
  Text,
  StyleSheet,
  Pressable,
  StatusBar,
  ActivityIndicator,
  Alert,
  ScrollView,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { WebViewCache, CacheMode, cacheModeDescriptions, CacheModeType } from '../utils/webviewCache';
import { useCacheMode } from '../contexts/CacheModeContext';

interface CacheSettingsScreenProps {
  onBack: () => void;
}

export function CacheSettingsScreen({ onBack }: CacheSettingsScreenProps) {
  const { cacheMode, setCacheMode } = useCacheMode();
  const [cacheSize, setCacheSize] = useState('计算中...');
  const [isClearing, setIsClearing] = useState(false);

  // 获取缓存大小
  const fetchCacheSize = useCallback(async () => {
    const size = await WebViewCache.getCacheSize();
    setCacheSize(size);
  }, []);

  useEffect(() => {
    fetchCacheSize();
  }, [fetchCacheSize]);

  // 清理缓存
  const handleClearCache = useCallback(async () => {
    Alert.alert(
      '清理缓存',
      '确定要清理所有 WebView 缓存吗？此操作不可恢复。',
      [
        { text: '取消', style: 'cancel' },
        {
          text: '清理',
          style: 'destructive',
          onPress: async () => {
            setIsClearing(true);
            const success = await WebViewCache.clearAllCache();
            setIsClearing(false);
            if (success) {
              await fetchCacheSize();
              Alert.alert('成功', '缓存已清理');
            } else {
              Alert.alert('失败', '清理缓存失败');
            }
          },
        },
      ]
    );
  }, [fetchCacheSize]);

  return (
    <View style={styles.outerContainer}>
      <StatusBar barStyle="dark-content" backgroundColor="#f5f5f5" />

      <SafeAreaView style={styles.safeArea} edges={['bottom']}>
        {/* 顶部栏 */}
        <View style={styles.header}>
          <Pressable style={styles.backButton} onPress={onBack}>
            <Text style={styles.backIcon}>‹</Text>
          </Pressable>
          <Text style={styles.title}>缓存设置</Text>
          <View style={styles.placeholder} />
        </View>

        <ScrollView style={styles.content}>
          {/* 当前缓存状态 */}
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>当前缓存</Text>
            <View style={styles.cacheInfoCard}>
              <View style={styles.cacheRow}>
                <Text style={styles.cacheLabel}>已使用缓存</Text>
                <Text style={styles.cacheValue}>{cacheSize}</Text>
              </View>
              <Pressable
                style={[styles.clearButton, isClearing && styles.clearButtonDisabled]}
                onPress={handleClearCache}
                disabled={isClearing}>
                {isClearing ? (
                  <ActivityIndicator color="#fff" />
                ) : (
                  <Text style={styles.clearButtonText}>清理缓存</Text>
                )}
              </Pressable>
            </View>
          </View>

          {/* 缓存模式 */}
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>缓存模式</Text>
            <View style={styles.modeList}>
              {Object.entries(cacheModeDescriptions).map(([key, value]) => (
                <Pressable
                  key={key}
                  style={[
                    styles.modeItem,
                    cacheMode === Number(key) && styles.modeItemSelected,
                  ]}
                  onPress={() => setCacheMode(Number(key) as CacheModeType)}>
                  <View style={styles.modeContent}>
                    <Text style={styles.modeLabel}>{value.label}</Text>
                    <Text style={styles.modeDescription}>{value.description}</Text>
                  </View>
                  <View style={styles.radio}>
                    {cacheMode === Number(key) && <View style={styles.radioInner} />}
                  </View>
                </Pressable>
              ))}
            </View>
          </View>

          {/* 缓存说明 */}
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>说明</Text>
            <View style={styles.tipCard}>
              <Text style={styles.tipText}>
                • 静态资源（图片、CSS、JS）会被缓存以加速二次访问{'\n'}
                • 清理缓存后会重新下载所有资源{'\n'}
                • 离线模式下仍可访问已缓存的页面
              </Text>
            </View>
          </View>
        </ScrollView>
      </SafeAreaView>
    </View>
  );
}

const styles = StyleSheet.create({
  // 外层容器，背景色会延伸到非安全区域
  outerContainer: {
    flex: 1,
    backgroundColor: '#f5f5f5',
  },
  safeArea: {
    flex: 1,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    height: 56,
    paddingHorizontal: 4,
    backgroundColor: '#fff',
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: '#e0e0e0',
  },
  backButton: {
    width: 44,
    height: 44,
    justifyContent: 'center',
    alignItems: 'center',
    borderRadius: 22,
  },
  backIcon: {
    fontSize: 28,
    color: '#333',
    marginLeft: -2,
  },
  title: {
    flex: 1,
    fontSize: 17,
    fontWeight: '600',
    color: '#333',
    textAlign: 'center',
  },
  placeholder: {
    width: 44,
  },
  content: {
    flex: 1,
    padding: 16,
  },
  section: {
    marginBottom: 24,
  },
  sectionTitle: {
    fontSize: 14,
    fontWeight: '600',
    color: '#666',
    marginBottom: 12,
  },
  cacheInfoCard: {
    backgroundColor: '#fff',
    borderRadius: 12,
    padding: 16,
  },
  cacheRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 16,
  },
  cacheLabel: {
    fontSize: 15,
    color: '#333',
  },
  cacheValue: {
    fontSize: 15,
    fontWeight: '600',
    color: '#007AFF',
  },
  clearButton: {
    backgroundColor: '#FF3B30',
    borderRadius: 8,
    paddingVertical: 12,
    alignItems: 'center',
  },
  clearButtonDisabled: {
    opacity: 0.7,
  },
  clearButtonText: {
    color: '#fff',
    fontSize: 16,
    fontWeight: '600',
  },
  modeList: {
    backgroundColor: '#fff',
    borderRadius: 12,
    overflow: 'hidden',
  },
  modeItem: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: 16,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: '#f0f0f0',
  },
  modeItemSelected: {
    backgroundColor: '#f0f8ff',
  },
  modeContent: {
    flex: 1,
  },
  modeLabel: {
    fontSize: 15,
    fontWeight: '600',
    color: '#333',
    marginBottom: 4,
  },
  modeDescription: {
    fontSize: 13,
    color: '#999',
  },
  radio: {
    width: 20,
    height: 20,
    borderRadius: 10,
    borderWidth: 2,
    borderColor: '#007AFF',
    justifyContent: 'center',
    alignItems: 'center',
  },
  radioInner: {
    width: 10,
    height: 10,
    borderRadius: 5,
    backgroundColor: '#007AFF',
  },
  tipCard: {
    backgroundColor: '#fff',
    borderRadius: 12,
    padding: 16,
  },
  tipText: {
    fontSize: 14,
    color: '#666',
    lineHeight: 20,
  },
});
