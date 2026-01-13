import React, { useRef, useState, useCallback } from 'react';
import {
  View,
  Text,
  StyleSheet,
  Pressable,
  SafeAreaView,
  StatusBar,
  Animated,
  ScrollView,
  TextInput,
} from 'react-native';
import NativeWebView, { NativeWebViewRef } from '../components/NativeWebView';

interface WebViewScreenProps {
  url: string;
  onBack: () => void;
  /** 是否显示导航栏，默认 true */
  showNavigationBar?: boolean;
  /** 自定义标题，为空则显示 URL */
  customTitle?: string;
}

/** 消息类型 */
interface MessageItem {
  id: string;
  type: 'received' | 'sent';
  data: string;
  timestamp: string;
}

export function WebViewScreen({ url, onBack, showNavigationBar = true, customTitle }: WebViewScreenProps) {
  const webViewRef = useRef<NativeWebViewRef>(null);
  const [progress, setProgress] = useState(0);
  const [title, setTitle] = useState<string>('');
  const [isLoading, setIsLoading] = useState(true);
  const [messages, setMessages] = useState<MessageItem[]>([]);
  const [inputText, setInputText] = useState('');
  const progressAnim = useState(new Animated.Value(0))[0];

  // 进度变化时动画
  React.useEffect(() => {
    Animated.timing(progressAnim, {
      toValue: progress,
      duration: 100,
      useNativeDriver: false,
    }).start();
  }, [progress]);

  const handleProgressChange = useCallback((p: number) => {
    setProgress(p);
  }, []);

  const handleLoadEnd = useCallback(() => {
    setIsLoading(false);
  }, []);

  const handleTitleChange = useCallback((t: string) => {
    setTitle(t);
  }, []);

  /** 接收 H5 发来的消息 */
  const handleMessage = useCallback((data: string) => {
    const newMessage: MessageItem = {
      id: Date.now().toString(),
      type: 'received',
      data,
      timestamp: new Date().toLocaleTimeString(),
    };
    setMessages(prev => [...prev, newMessage]);
  }, []);

  /** 发送消息到 H5 */
  const handleSendMessage = useCallback(() => {
    if (!inputText.trim()) return;

    const message = inputText.trim();
    webViewRef.current?.postMessage(message);

    const newMessage: MessageItem = {
      id: Date.now().toString(),
      type: 'sent',
      data: message,
      timestamp: new Date().toLocaleTimeString(),
    };
    setMessages(prev => [...prev, newMessage]);
    setInputText('');
  }, [inputText]);

  /** 清空消息 */
  const handleClearMessages = useCallback(() => {
    setMessages([]);
  }, []);

  const displayTitle = customTitle || title || url;

  // 是否显示消息面板（仅本地页面显示）
  const showMessagePanel = url.includes('android_asset');

  return (
    <SafeAreaView style={styles.container}>
      <StatusBar barStyle="dark-content" />

      {/* 导航栏 */}
      {showNavigationBar && (
        <View style={styles.navigationBar}>
          <Pressable style={styles.backButton} onPress={onBack}>
            <Text style={styles.backIcon}>‹</Text>
          </Pressable>

          <View style={styles.titleContainer}>
            <Text style={styles.title} numberOfLines={1}>
              {displayTitle}
            </Text>
          </View>

          <View style={styles.placeholder} />
        </View>
      )}

      {/* 进度条 */}
      {isLoading && (
        <View style={styles.progressContainer}>
          <Animated.View
            style={[
              styles.progressBar,
              {
                width: progressAnim.interpolate({
                  inputRange: [0, 100],
                  outputRange: ['0%', '100%'],
                }),
              },
            ]}
          />
        </View>
      )}

      {/* WebView 内容 */}
      <View style={styles.webViewContainer}>
        <NativeWebView
          ref={webViewRef}
          source={{ uri: url }}
          javaScriptEnabled={true}
          domStorageEnabled={true}
          style={styles.webView}
          onProgress={handleProgressChange}
          onLoadEnd={handleLoadEnd}
          onTitle={handleTitleChange}
          onMessage={handleMessage}
        />
      </View>

      {/* 消息通信面板（仅本地页面显示） */}
      {showMessagePanel && messages.length > 0 && (
        <View style={styles.messagePanel}>
          <View style={styles.messageHeader}>
            <Text style={styles.messageTitle}>📨 JSBridge 通信</Text>
            <Pressable onPress={handleClearMessages}>
              <Text style={styles.clearButton}>清空</Text>
            </Pressable>
          </View>

          <ScrollView style={styles.messageList} showsVerticalScrollIndicator={true}>
            {messages.map((msg) => (
              <View
                key={msg.id}
                style={[
                  styles.messageItem,
                  msg.type === 'sent' ? styles.messageSent : styles.messageReceived,
                ]}>
                <Text style={styles.messageLabel}>
                  {msg.type === 'sent' ? '📤 发送到 H5' : '📥 来自 H5'}
                </Text>
                <Text style={styles.messageText}>{msg.data}</Text>
                <Text style={styles.messageTime}>{msg.timestamp}</Text>
              </View>
            ))}
          </ScrollView>

          <View style={styles.inputRow}>
            <TextInput
              style={styles.input}
              value={inputText}
              onChangeText={setInputText}
              placeholder="发送消息到 H5..."
              placeholderTextColor="#999"
              onSubmitEditing={handleSendMessage}
              returnKeyType="send"
            />
            <Pressable style={styles.sendButton} onPress={handleSendMessage}>
              <Text style={styles.sendButtonText}>发送</Text>
            </Pressable>
          </View>
        </View>
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#fff',
  },
  navigationBar: {
    flexDirection: 'row',
    alignItems: 'center',
    height: 56,
    paddingHorizontal: 4,
    backgroundColor: '#fff',
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: '#e0e0e0',
    elevation: 2,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.08,
    shadowRadius: 2,
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
  titleContainer: {
    flex: 1,
    paddingHorizontal: 8,
  },
  title: {
    fontSize: 17,
    fontWeight: '600',
    color: '#333',
    textAlign: 'center',
  },
  placeholder: {
    width: 44,
  },
  progressContainer: {
    height: 3,
    backgroundColor: '#f0f0f0',
    overflow: 'hidden',
  },
  progressBar: {
    height: '100%',
    backgroundColor: '#007AFF',
  },
  webViewContainer: {
    flex: 1,
  },
  webView: {
    flex: 1,
  },
  // ============ JSBridge 通信面板 ============
  messagePanel: {
    position: 'absolute',
    bottom: 0,
    left: 0,
    right: 0,
    backgroundColor: '#fff',
    borderTopWidth: StyleSheet.hairlineWidth,
    borderTopColor: '#e0e0e0',
    elevation: 10,
    maxHeight: 300,
  },
  messageHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: 16,
    paddingVertical: 12,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: '#f0f0f0',
  },
  messageTitle: {
    fontSize: 15,
    fontWeight: '600',
    color: '#333',
  },
  clearButton: {
    fontSize: 13,
    color: '#007AFF',
  },
  messageList: {
    padding: 12,
    maxHeight: 180,
  },
  messageItem: {
    padding: 10,
    borderRadius: 8,
    marginBottom: 8,
  },
  messageSent: {
    backgroundColor: '#e3f2fd',
    alignSelf: 'flex-end',
    maxWidth: '85%',
  },
  messageReceived: {
    backgroundColor: '#f3e5f5',
    alignSelf: 'flex-start',
    maxWidth: '85%',
  },
  messageLabel: {
    fontSize: 11,
    color: '#666',
    marginBottom: 4,
  },
  messageText: {
    fontSize: 13,
    color: '#333',
  },
  messageTime: {
    fontSize: 10,
    color: '#999',
    marginTop: 4,
    textAlign: 'right',
  },
  inputRow: {
    flexDirection: 'row',
    padding: 12,
    borderTopWidth: StyleSheet.hairlineWidth,
    borderTopColor: '#e0e0e0',
    alignItems: 'center',
  },
  input: {
    flex: 1,
    height: 40,
    backgroundColor: '#f5f5f5',
    borderRadius: 8,
    paddingHorizontal: 12,
    fontSize: 14,
    color: '#333',
    marginRight: 8,
  },
  sendButton: {
    backgroundColor: '#007AFF',
    paddingHorizontal: 16,
    height: 40,
    borderRadius: 8,
    justifyContent: 'center',
  },
  sendButtonText: {
    color: '#fff',
    fontSize: 14,
    fontWeight: '600',
  },
});
