import { Client } from '@stomp/stompjs';
import * as SecureStore from 'expo-secure-store';

const BACKEND_HOST = process.env.EXPO_PUBLIC_API_URL?.replace(/^https?:\/\//, '') ?? 'localhost:8080';
const WS_PROTO = BACKEND_HOST.startsWith('localhost') ? 'ws' : 'wss';
const WS_URL = `${WS_PROTO}://${BACKEND_HOST}/ws/chat`;

let client: Client | null = null;

async function buildClient(): Promise<Client> {
  let token: string | null = null;
  try {
    token = await SecureStore.getItemAsync('userToken');
  } catch {
    // silently ignore
  }
  const c = new Client({
    brokerURL: WS_URL,
    connectHeaders: token ? { Authorization: `Bearer ${token}` } : {},
    reconnectDelay: 5000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    // React Native WebSocket is available globally
    webSocketFactory: () => new WebSocket(WS_URL),
    debug: () => {},
  });
  return c;
}

export async function getStompClient(): Promise<Client> {
  if (!client) {
    client = await buildClient();
    client.activate();
  }
  return client;
}

export function disconnectStomp() {
  if (client) {
    try { client.deactivate(); } catch { /* ignore */ }
    client = null;
  }
}
