import * as SecureStore from 'expo-secure-store';

const BAN_REASON_KEY = 'banReason';
const BAN_EXPIRES_AT_KEY = 'banExpiresAt';

export type BanNotice = {
  reason: string | null;
  expiresAt: string | null;
};

export async function storeBanNotice(notice: BanNotice) {
  await Promise.all([
    notice.reason
      ? SecureStore.setItemAsync(BAN_REASON_KEY, notice.reason)
      : SecureStore.deleteItemAsync(BAN_REASON_KEY),
    notice.expiresAt
      ? SecureStore.setItemAsync(BAN_EXPIRES_AT_KEY, notice.expiresAt)
      : SecureStore.deleteItemAsync(BAN_EXPIRES_AT_KEY),
  ]);
}

export async function readBanNotice(): Promise<BanNotice> {
  const [reason, expiresAt] = await Promise.all([
    SecureStore.getItemAsync(BAN_REASON_KEY),
    SecureStore.getItemAsync(BAN_EXPIRES_AT_KEY),
  ]);
  return { reason, expiresAt };
}

export async function clearBanNotice() {
  await Promise.all([
    SecureStore.deleteItemAsync(BAN_REASON_KEY),
    SecureStore.deleteItemAsync(BAN_EXPIRES_AT_KEY),
  ]);
}
