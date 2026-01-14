import React, { createContext, Context, useContext, useState, ReactNode } from 'react';
import { CacheModeType } from '../utils/webviewCache';

interface CacheModeContextType {
  cacheMode: CacheModeType;
  setCacheMode: (mode: CacheModeType) => void;
}

const CacheModeContext: Context<CacheModeContextType | undefined> = createContext<CacheModeContextType | undefined>(undefined);

interface CacheModeProviderProps {
  children: ReactNode;
}

export function CacheModeProvider({ children }: CacheModeProviderProps) {
  const [cacheMode, setCacheMode] = useState<CacheModeType>(0); // 默认 LOAD_DEFAULT

  return (
    <CacheModeContext.Provider value={{ cacheMode, setCacheMode }}>
      {children}
    </CacheModeContext.Provider>
  );
}

export function useCacheMode() {
  const context = useContext(CacheModeContext);
  if (!context) {
    throw new Error('useCacheMode must be used within CacheModeProvider');
  }
  return context;
}
