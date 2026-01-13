import { NativeModules } from 'react-native';

/**
 * 缓存模式
 * - LOAD_DEFAULT: 正常模式，优先网络
 * - LOAD_CACHE_ELSE_NETWORK: 有缓存就用缓存，否则网络
 * - LOAD_NO_CACHE: 只用网络
 * - LOAD_CACHE_ONLY: 只用缓存
 */
export const CacheMode = {
  LOAD_DEFAULT: 0,
  LOAD_CACHE_ELSE_NETWORK: 1,
  LOAD_NO_CACHE: 2,
  LOAD_CACHE_ONLY: 3,
} as const;

export type CacheModeType = typeof CacheMode[keyof typeof CacheMode];

/**
 * 缓存管理工具
 */
export const WebViewCache = {
  /**
   * 获取缓存大小
   * 返回格式化后的字符串，如 "12.5 MB"
   */
  async getCacheSize(): Promise<string> {
    try {
      const result = await NativeModules.WebViewCacheManager?.getCacheSize();
      return result || '0 B';
    } catch (error) {
      console.warn('Failed to get cache size:', error);
      return '0 B';
    }
  },

  /**
   * 清理所有缓存
   */
  async clearAllCache(): Promise<boolean> {
    try {
      await NativeModules.WebViewCacheManager?.clearAllCache();
      return true;
    } catch (error) {
      console.warn('Failed to clear cache:', error);
      return false;
    }
  },
};

/**
 * 缓存模式说明
 */
export const cacheModeDescriptions: Record<number, { label: string; description: string }> = {
  0: {
    label: '正常模式',
    description: '优先使用网络，缓存可用时也会使用',
  },
  1: {
    label: '优先缓存',
    description: '有缓存就用缓存，没有才请求网络',
  },
  2: {
    label: '禁用缓存',
    description: '只使用网络，不缓存任何内容',
  },
  3: {
    label: '仅用缓存',
    description: '只使用缓存，不请求网络（离线模式）',
  },
};
