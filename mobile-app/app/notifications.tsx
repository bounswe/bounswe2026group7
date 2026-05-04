import React, { useState, useEffect, useCallback } from 'react';
import { router } from 'expo-router';
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  ScrollView,
  ActivityIndicator,
  RefreshControl,
} from 'react-native';
import apiClient from '../api/client';

type Notification = {
  id: number;
  type: string;
  title: string;
  body: string;
  isRead: boolean;
  createdAt: string;
};

function timeAgo(dateStr: string): string {
  const diff = Math.floor((Date.now() - new Date(dateStr).getTime()) / 1000);
  if (diff < 60) return 'Just now';
  if (diff < 3600) return `${Math.floor(diff / 60)}m ago`;
  if (diff < 86400) return `${Math.floor(diff / 3600)}h ago`;
  return `${Math.floor(diff / 86400)}d ago`;
}

const TYPE_ICON: Record<string, string> = {
  REQUEST_ACCEPTED: '✅',
  REQUEST_REJECTED: '❌',
  MATCH_FOUND: '🤝',
  NEW_MESSAGE: '💬',
  MEETING_REMINDER: '📅',
};

export default function NotificationsScreen() {
  const [notifications, setNotifications] = useState<Notification[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);

  const fetchNotifications = useCallback(async (markAllAfter = false) => {
    try {
      const res = await apiClient.get('/notifications');
      setNotifications(res.data);
      if (markAllAfter && res.data.some((n: Notification) => !n.isRead)) {
        await apiClient.patch('/notifications/read-all');
        setNotifications(res.data.map((n: Notification) => ({ ...n, isRead: true })));
      }
    } catch (err) {
      console.error('Failed to fetch notifications:', err);
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  useEffect(() => {
    fetchNotifications(true);
  }, [fetchNotifications]);

  const markAsRead = async (id: number) => {
    try {
      await apiClient.patch(`/notifications/${id}/read`);
      setNotifications((prev) =>
        prev.map((n) => (n.id === id ? { ...n, isRead: true } : n))
      );
    } catch (err) {
      console.error('Failed to mark as read:', err);
    }
  };

  const markAllAsRead = async () => {
    try {
      await apiClient.patch('/notifications/read-all');
      setNotifications((prev) => prev.map((n) => ({ ...n, isRead: true })));
    } catch (err) {
      console.error('Failed to mark all as read:', err);
    }
  };

  const handleNotificationPress = async (n: Notification) => {
    if (!n.isRead) await markAsRead(n.id);
    switch (n.type) {
      case 'REQUEST_ACCEPTED':
      case 'MEETING_REMINDER':
        router.push('/(tabs)/' as any);
        break;
      case 'REQUEST_REJECTED':
        router.push('/(tabs)/profile' as any);
        break;
      case 'MATCH_FOUND':
        router.push('/(tabs)/explore' as any);
        break;
      case 'NEW_MESSAGE':
        router.push('/(tabs)/messages' as any);
        break;
    }
  };

  const unreadCount = notifications.filter((n) => !n.isRead).length;

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <View style={styles.topCircle} />
        <View style={styles.statusRow}>
          <Text style={styles.statusText}>{new Date().toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit', hour12: false })}</Text>
          <Text style={styles.statusIcons}>▲ ▮</Text>
        </View>
        <View style={styles.headerRow}>
          <TouchableOpacity style={styles.backButton} onPress={() => router.back()}>
            <Text style={styles.backButtonText}>‹ Back</Text>
          </TouchableOpacity>
          {unreadCount > 0 && (
            <TouchableOpacity style={styles.markAllButton} onPress={markAllAsRead}>
              <Text style={styles.markAllText}>Mark all read</Text>
            </TouchableOpacity>
          )}
        </View>
        <Text style={styles.title}>Notifications</Text>
        {unreadCount > 0 && (
          <Text style={styles.subtitle}>{unreadCount} unread</Text>
        )}
      </View>

      {loading ? (
        <ActivityIndicator size="large" color="#456B50" style={{ marginTop: 50 }} />
      ) : (
        <ScrollView
          style={styles.list}
          contentContainerStyle={styles.listContent}
          showsVerticalScrollIndicator={false}
          refreshControl={
            <RefreshControl
              refreshing={refreshing}
              onRefresh={() => { setRefreshing(true); fetchNotifications(); }}
              tintColor="#456B50"
            />
          }
        >
          {notifications.length === 0 ? (
            <View style={styles.emptyState}>
              <Text style={styles.emptyIcon}>🔔</Text>
              <Text style={styles.emptyText}>No notifications yet.</Text>
            </View>
          ) : (
            notifications.map((n) => (
              <TouchableOpacity
                key={n.id}
                style={[styles.card, !n.isRead && styles.cardUnread]}
                onPress={() => handleNotificationPress(n)}
                activeOpacity={0.85}
              >
                <View style={styles.cardLeft}>
                  <Text style={styles.cardIcon}>{TYPE_ICON[n.type] ?? '🔔'}</Text>
                </View>
                <View style={styles.cardBody}>
                  <View style={styles.cardTopRow}>
                    <Text style={styles.cardTitle}>{n.title}</Text>
                    {!n.isRead && <View style={styles.unreadDot} />}
                  </View>
                  <Text style={styles.cardText}>{n.body}</Text>
                  <Text style={styles.cardTime}>{timeAgo(n.createdAt)}</Text>
                </View>
              </TouchableOpacity>
            ))
          )}
        </ScrollView>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#ECE8E1' },
  header: {
    backgroundColor: '#456B50',
    paddingTop: 54,
    paddingHorizontal: 24,
    paddingBottom: 24,
    overflow: 'hidden',
  },
  topCircle: {
    position: 'absolute',
    width: 280,
    height: 280,
    borderRadius: 140,
    backgroundColor: 'rgba(255,255,255,0.05)',
    top: -30,
    right: -60,
  },
  statusRow: { flexDirection: 'row', justifyContent: 'space-between' },
  statusText: { color: '#fff', fontSize: 16, fontWeight: '700' },
  statusIcons: { color: '#fff', fontSize: 18, fontWeight: '700' },
  headerRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginTop: 16,
    marginBottom: 10,
  },
  backButton: {
    backgroundColor: 'rgba(255,255,255,0.10)',
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.16)',
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderRadius: 16,
  },
  backButtonText: { color: '#F7F4EE', fontSize: 14, fontWeight: '700' },
  markAllButton: {
    backgroundColor: 'rgba(255,255,255,0.10)',
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.16)',
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderRadius: 16,
  },
  markAllText: { color: '#F7F4EE', fontSize: 13, fontWeight: '600' },
  title: { color: '#F7F4EE', fontSize: 30, fontWeight: '700' },
  subtitle: { color: 'rgba(247,244,238,0.72)', fontSize: 14, fontWeight: '500', marginTop: 4 },
  list: { flex: 1 },
  listContent: { padding: 20, paddingBottom: 36 },
  emptyState: { paddingVertical: 60, alignItems: 'center' },
  emptyIcon: { fontSize: 40, marginBottom: 12 },
  emptyText: { color: '#9A8F82', fontSize: 15, fontWeight: '500' },
  card: {
    backgroundColor: '#F8F6F2',
    borderRadius: 22,
    padding: 16,
    marginBottom: 12,
    flexDirection: 'row',
    gap: 14,
  },
  cardUnread: {
    backgroundColor: '#EEF5EF',
    borderWidth: 1,
    borderColor: '#C8DEC9',
  },
  cardLeft: { paddingTop: 2 },
  cardIcon: { fontSize: 24 },
  cardBody: { flex: 1 },
  cardTopRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: 4 },
  cardTitle: { color: '#23372B', fontSize: 15, fontWeight: '700', flex: 1 },
  unreadDot: {
    width: 8,
    height: 8,
    borderRadius: 4,
    backgroundColor: '#456B50',
    marginLeft: 8,
  },
  cardText: { color: '#5A5048', fontSize: 14, lineHeight: 20, marginBottom: 6 },
  cardTime: { color: '#9A8F82', fontSize: 12, fontWeight: '500' },
});
