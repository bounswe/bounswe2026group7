import axios, { InternalAxiosRequestConfig } from 'axios';
import * as SecureStore from 'expo-secure-store';
import { router } from 'expo-router';
import { clearBanNotice, storeBanNotice } from '../utils/banNotice';

declare module 'axios' {
  interface AxiosRequestConfig {
    silent?: boolean;
  }
}

const BASE_URL = process.env.EXPO_PUBLIC_API_URL
  ? `${process.env.EXPO_PUBLIC_API_URL}/api`
  : 'http://10.1.195.120:8080/api';

const apiClient = axios.create({
  baseURL: BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

apiClient.interceptors.request.use(
  async (config) => {
    if (config.url && config.url.includes('/auth/')) {
      return config;
    }
    const token = await SecureStore.getItemAsync('userToken');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    } else {
      console.warn('[apiClient] No token for:', config.method?.toUpperCase(), config.url);
    }
    return config;
  },
  (error) => Promise.reject(error)
);

let isRedirectingToLogin = false;
let isRedirectingToBlocked = false;

apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const status = error?.response?.status;
    const url = error?.config?.url ?? '';
    const method = error?.config?.method?.toUpperCase();
    const data = error?.response?.data;
    const isExpectedBanResponse = status === 403 && data?.code === 'BANNED_UNTIL';
    const logLine = `[apiClient] ${method} ${url} → ${status}`;
    if (isExpectedBanResponse) {
      console.warn(logLine, JSON.stringify(data));
    } else {
      console.error(logLine, JSON.stringify(data));
    }

    if (isExpectedBanResponse && !isRedirectingToBlocked) {
      isRedirectingToBlocked = true;
      await Promise.all([
        SecureStore.deleteItemAsync('userToken'),
        SecureStore.deleteItemAsync('userId'),
        SecureStore.deleteItemAsync('userRole'),
        storeBanNotice({
          reason: typeof data?.reason === 'string' ? data.reason : null,
          expiresAt: typeof data?.expiresAt === 'string' ? data.expiresAt : null,
        }),
      ]);
      router.replace('/blocked');
      setTimeout(() => { isRedirectingToBlocked = false; }, 3000);
    }

    // Token yoksa ya da süresi dolduysa otomatik çıkış yap
    if (status === 401 && !url.includes('/auth/') && !isRedirectingToLogin) {
      isRedirectingToLogin = true;
      await Promise.all([
        SecureStore.deleteItemAsync('userToken'),
        SecureStore.deleteItemAsync('userId'),
        SecureStore.deleteItemAsync('userRole'),
        clearBanNotice(),
      ]);
      router.replace('/onboarding');
      setTimeout(() => { isRedirectingToLogin = false; }, 3000);
    }

    return Promise.reject(error);
  }
);

export default apiClient;
