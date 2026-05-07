import * as SecureStore from 'expo-secure-store';

export type NotificationPreferenceKey =
  | 'message'
  | 'meeting'
  | 'request'
  | 'task'
  | 'feedback';

export type NotificationPreferences = Record<NotificationPreferenceKey, boolean>;

export const DEFAULT_NOTIFICATION_PREFERENCES: NotificationPreferences = {
  message: true,
  meeting: true,
  request: true,
  task: true,
  feedback: true,
};

const STORAGE_PREFIX = 'notificationPreferences';

function getStorageKey(userId: string) {
  return `${STORAGE_PREFIX}_${userId}`;
}

export async function loadNotificationPreferences(userId: string): Promise<NotificationPreferences> {
  const stored = await SecureStore.getItemAsync(getStorageKey(userId));
  if (!stored) {
    return DEFAULT_NOTIFICATION_PREFERENCES;
  }

  try {
    const parsed = JSON.parse(stored);
    return {
      ...DEFAULT_NOTIFICATION_PREFERENCES,
      ...parsed,
    };
  } catch {
    return DEFAULT_NOTIFICATION_PREFERENCES;
  }
}

export async function saveNotificationPreferences(
  userId: string,
  preferences: NotificationPreferences,
) {
  await SecureStore.setItemAsync(getStorageKey(userId), JSON.stringify(preferences));
}
