import axios from 'axios';
import * as SecureStore from 'expo-secure-store';
import { router } from 'expo-router';

const apiClient = axios.create({
  baseURL: 'http://167.71.44.71:8080/api',
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

apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const status = error?.response?.status;
    const url = error?.config?.url ?? '';
    const method = error?.config?.method?.toUpperCase();
    const data = error?.response?.data;
    console.error(`[apiClient] ${method} ${url} → ${status}`, JSON.stringify(data));

    // Token yoksa ya da süresi dolduysa otomatik çıkış yap
    if (status === 401 && !url.includes('/auth/') && !isRedirectingToLogin) {
      isRedirectingToLogin = true;
      await SecureStore.deleteItemAsync('userToken');
      await SecureStore.deleteItemAsync('userId');
      await SecureStore.deleteItemAsync('userRole');
      router.replace('/onboarding');
      setTimeout(() => { isRedirectingToLogin = false; }, 3000);
    }

    return Promise.reject(error);
  }
);

export default apiClient;
