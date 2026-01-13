import React, { forwardRef, useRef, useCallback, useImperativeHandle } from 'react';
import {
  requireNativeComponent,
  ViewStyle,
  NativeSyntheticEvent,
  UIManager,
  findNodeHandle,
  Platform,
} from 'react-native';

// 类型定义
interface Source {
  uri?: string;
  html?: string;
}

interface NavigationState {
  canGoBack: boolean;
  canGoForward: boolean;
  hasUrl: boolean;
}

interface ErrorEvent {
  code: number;
  description: string;
}

// 原生事件数据类型
interface ProgressEventData {
  progress: number;
}

interface TitleEventData {
  title: string;
}

interface MessageEventData {
  data: string;
}

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

interface NativeWebViewProps {
  // 基础属性
  source: Source;
  javaScriptEnabled?: boolean;
  domStorageEnabled?: boolean;
  injectedJavaScript?: string;

  // UI 样式
  showsHorizontalScrollIndicator?: boolean;
  showsVerticalScrollIndicator?: boolean;
  style?: ViewStyle;

  // 缓存控制
  /** 缓存模式，默认 LOAD_DEFAULT */
  cacheMode?: CacheModeType;

  // 事件回调
  onProgress?: (progress: number) => void;
  onLoadEnd?: () => void;
  onError?: (error: ErrorEvent) => void;
  onTitle?: (title: string) => void;
  onNavigationStateChange?: (state: NavigationState) => void;
  /** JSBridge 消息回调 */
  onMessage?: (data: string) => void;
}

// 原生组件的内部 Props 类型
interface InternalNativeWebViewProps extends Omit<NativeWebViewProps, 'onProgress' | 'onLoadEnd' | 'onError' | 'onTitle' | 'onNavigationStateChange' | 'onMessage'> {
  onProgress?: (event: NativeSyntheticEvent<ProgressEventData>) => void;
  onLoadEnd?: (event: NativeSyntheticEvent<{}>) => void;
  onError?: (event: NativeSyntheticEvent<ErrorEvent>) => void;
  onTitle?: (event: NativeSyntheticEvent<TitleEventData>) => void;
  onNavigationStateChange?: (event: NativeSyntheticEvent<NavigationState>) => void;
  onMessage?: (event: NativeSyntheticEvent<MessageEventData>) => void;
}

// 原生组件引用
const RNNativeWebView = requireNativeComponent<InternalNativeWebViewProps>('NativeWebView');

export interface NativeWebViewRef {
  goBack: () => void;
  goForward: () => void;
  reload: () => void;
  clearCache: () => void;
  stopLoading: () => void;
  /** 发送消息到 WebView */
  postMessage: (message: string) => void;
  /** 执行 JavaScript */
  injectJavaScript: (script: string) => void;
}

/**
 * NativeWebView - 自研原生 WebView 组件
 */
const NativeWebViewComponent = forwardRef<NativeWebViewRef, NativeWebViewProps>(
  (
    {
      source,
      javaScriptEnabled = true,
      domStorageEnabled = true,
      injectedJavaScript,
      showsHorizontalScrollIndicator = true,
      showsVerticalScrollIndicator = true,
      cacheMode = CacheMode.LOAD_DEFAULT,
      style,
      onProgress,
      onLoadEnd,
      onError,
      onTitle,
      onNavigationStateChange,
      onMessage,
    },
    ref
  ) => {
    const nativeRef = useRef<React.Component>(null);

    // 调用原生命令
    const dispatchCommand = useCallback((commandName: string, args: any[] = []) => {
      const handle = findNodeHandle(nativeRef.current);
      if (handle && Platform.OS === 'android') {
        UIManager.dispatchViewManagerCommand(
          handle,
          (UIManager as any).NativeWebView.Commands[commandName],
          args
        );
      }
    }, []);

    // 暴露方法给父组件
    useImperativeHandle(ref, () => ({
      goBack: () => dispatchCommand('goBack'),
      goForward: () => dispatchCommand('goForward'),
      reload: () => dispatchCommand('reload'),
      clearCache: () => dispatchCommand('clearCache'),
      stopLoading: () => dispatchCommand('stopLoading'),
      postMessage: (message: string) => dispatchCommand('postMessage', [message]),
      injectJavaScript: (script: string) => {
        // 注入 JS 使用 evaluateJavascript，通过 props 方式
        nativeRef.current?.setNativeProps({ injectedJavaScript: script });
      },
    }), [dispatchCommand]);

    // 事件处理器
    const handleProgress = useCallback((event: NativeSyntheticEvent<ProgressEventData>) => {
      onProgress?.(event.nativeEvent.progress);
    }, [onProgress]);

    const handleLoadEnd = useCallback((event: NativeSyntheticEvent<{}>) => {
      onLoadEnd?.();
    }, [onLoadEnd]);

    const handleError = useCallback((event: NativeSyntheticEvent<ErrorEvent>) => {
      onError?.(event.nativeEvent);
    }, [onError]);

    const handleTitle = useCallback((event: NativeSyntheticEvent<TitleEventData>) => {
      onTitle?.(event.nativeEvent.title);
    }, [onTitle]);

    const handleNavigationStateChange = useCallback((event: NativeSyntheticEvent<NavigationState>) => {
      onNavigationStateChange?.(event.nativeEvent);
    }, [onNavigationStateChange]);

    const handleMessage = useCallback((event: NativeSyntheticEvent<MessageEventData>) => {
      onMessage?.(event.nativeEvent.data);
    }, [onMessage]);

    return (
      <RNNativeWebView
        ref={nativeRef}
        source={source}
        javaScriptEnabled={javaScriptEnabled}
        domStorageEnabled={domStorageEnabled}
        injectedJavaScript={injectedJavaScript}
        showsHorizontalScrollIndicator={showsHorizontalScrollIndicator}
        showsVerticalScrollIndicator={showsVerticalScrollIndicator}
        cacheMode={cacheMode}
        style={style}
        onProgress={handleProgress}
        onLoadEnd={handleLoadEnd}
        onError={handleError}
        onTitle={handleTitle}
        onNavigationStateChange={handleNavigationStateChange}
        onMessage={handleMessage}
      />
    );
  }
);

export default NativeWebViewComponent;
