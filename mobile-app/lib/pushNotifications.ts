import * as Device from 'expo-device';
import * as Notifications from 'expo-notifications';
import * as SecureStore from 'expo-secure-store';

import apiClient from '../api/client';

const PUSH_TOKEN_STORAGE_KEY = 'nativePushToken';

Notifications.setNotificationHandler({
  handleNotification: async () => ({
    shouldShowBanner: true,
    shouldShowList: true,
    shouldPlaySound: true,
    shouldSetBadge: false,
  }),
});

export type PushPermissionState = 'granted' | 'denied' | 'undetermined' | 'unsupported';

export type PushRegistrationResult =
  | { status: 'registered'; token: string; tokenType: string }
  | { status: 'permission_denied' }
  | { status: 'simulator' }
  | { status: 'error'; message: string };

async function ensureAndroidChannel() {
  if (Device.osName !== 'Android') {
    return;
  }

  await Notifications.setNotificationChannelAsync('default', {
    name: 'default',
    importance: Notifications.AndroidImportance.DEFAULT,
  });
}

export async function getPushPermissionState(): Promise<PushPermissionState> {
  if (!Device.isDevice) {
    return 'unsupported';
  }

  const settings = await Notifications.getPermissionsAsync();
  if (settings.granted) {
    return 'granted';
  }

  if (settings.status === 'denied') {
    return 'denied';
  }

  return 'undetermined';
}

export async function getStoredPushToken() {
  return SecureStore.getItemAsync(PUSH_TOKEN_STORAGE_KEY);
}

export async function registerPushToken() : Promise<PushRegistrationResult> {
  if (!Device.isDevice) {
    return { status: 'simulator' };
  }

  await ensureAndroidChannel();

  let settings = await Notifications.getPermissionsAsync();
  if (!settings.granted) {
    settings = await Notifications.requestPermissionsAsync();
  }

  if (!settings.granted) {
    return { status: 'permission_denied' };
  }

  try {
    const tokenResponse = await Notifications.getDevicePushTokenAsync();
    const token = typeof tokenResponse.data === 'string'
      ? tokenResponse.data
      : JSON.stringify(tokenResponse.data);

    await apiClient.post('/users/me/devices', { token });
    await SecureStore.setItemAsync(PUSH_TOKEN_STORAGE_KEY, token);

    return {
      status: 'registered',
      token,
      tokenType: tokenResponse.type,
    };
  } catch (error: any) {
    return {
      status: 'error',
      message: error?.message || 'Could not register this device for push notifications.',
    };
  }
}

export async function unregisterStoredPushToken() {
  const token = await SecureStore.getItemAsync(PUSH_TOKEN_STORAGE_KEY);
  if (!token) {
    return;
  }

  try {
    await apiClient.delete(`/users/me/devices/${encodeURIComponent(token)}`);
  } finally {
    await SecureStore.deleteItemAsync(PUSH_TOKEN_STORAGE_KEY);
  }
}
