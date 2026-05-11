import React, { useState, useEffect, useCallback } from 'react';
import { router } from 'expo-router';
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  ScrollView,
  ActivityIndicator,
} from 'react-native';
import { useRole } from '../../components/RoleContext';
import { useProtectedSession } from '../../components/useProtectedSession';
import apiClient from '../../api/client';

const AVATAR_COLORS = [
  { bg: '#DFD9C9', text: '#66582F' },
  { bg: '#CCD6E5', text: '#4A5D7A' },
  { bg: '#D7E8DA', text: '#2F563C' },
  { bg: '#E2D1E6', text: '#6D3F72' },
  { bg: '#D6E8DC', text: '#2F563C' },
  { bg: '#F1E1BB', text: '#8A5D12' },
];

const getAvatarColors = (id: number) => AVATAR_COLORS[id % AVATAR_COLORS.length];

const calcProgress = (startDate: string, endDate: string) => {
  const start = new Date(startDate).getTime();
  const end = new Date(endDate).getTime();
  const now = Date.now();
  if (now >= end) return 100;
  if (now <= start) return 0;
  return Math.round(((now - start) / (end - start)) * 100);
};

type ConnectionCard = {
  mentorshipId: number;
  connectedUserId: number;
  connectedUserFirstName: string;
  type: 'mentor' | 'mentee';
  progress: number;
  startDate: string;
  endDate: string;
  sharedGoal: string;
};

export default function HomeScreen() {
  const { role } = useRole();
  const { session, sessionLoading } = useProtectedSession('dashboard');
  const isMentor = role === 'mentor';

  const [connections, setConnections] = useState<ConnectionCard[]>([]);
  const [loading, setLoading] = useState(true);
  const [unreadCount, setUnreadCount] = useState(0);

  const fetchMentorships = useCallback(async () => {
    if (sessionLoading) return;
    if (!session) {
      console.log('[dashboard] skipping mentorship fetch because session is missing');
      setLoading(false);
      return;
    }
    try {
      setLoading(true);
      const res = await apiClient.get('/mentorships');
      const mentorships: any[] = res.data;

      const cards: ConnectionCard[] = mentorships
        .filter((m) => m.status === 'ACTIVE')
        .map((m) => {
          const isCurrentUserMentor = Number(m.mentorId) === session.userId;
          const connectedUserId = isCurrentUserMentor ? m.menteeId : m.mentorId;
          const connectedUserFirstName = isCurrentUserMentor ? m.menteeFirstName : m.mentorFirstName;
          const type: 'mentor' | 'mentee' = isCurrentUserMentor ? 'mentee' : 'mentor';

          return {
            mentorshipId: m.id,
            connectedUserId,
            connectedUserFirstName,
            type,
            progress: calcProgress(m.startDate, m.endDate),
            startDate: m.startDate,
            endDate: m.endDate,
            sharedGoal: m.sharedGoal || '',
          };
        });

      setConnections(cards);
    } catch (error) {
      console.error('Error fetching mentorships:', error);
    } finally {
      setLoading(false);
    }
  }, [session, sessionLoading]);

  useEffect(() => {
    if (sessionLoading) return;
    if (!session) {
      console.log('[dashboard] skipping notifications fetch because session is missing');
      return;
    }

    fetchMentorships();
    apiClient.get('/notifications?unreadOnly=true')
      .then((res) => setUnreadCount(res.data.length))
      .catch(() => {});
  }, [fetchMentorships, session, sessionLoading]);

  const openNotifications = async () => {
    router.push('/notifications' as any);

    if (unreadCount <= 0) return;

    setUnreadCount(0);
    try {
      await apiClient.patch('/notifications/read-all');
    } catch {
      apiClient.get('/notifications?unreadOnly=true')
        .then((res) => setUnreadCount(res.data.length))
        .catch(() => {});
    }
  };

  const openSocialFeed = () => {
    router.push('/social-feed' as any);
  };

  const openConnectionProfile = (item: ConnectionCard) => {
    const colors = getAvatarColors(item.connectedUserId);
    const initials = item.connectedUserFirstName.substring(0, 2).toUpperCase();

    console.log('[navigation] opening connection-profile', {
      sourceScreen: 'dashboard',
      currentUserId: session?.userId ?? null,
      currentRole: session?.role ?? role,
      mentorshipId: item.mentorshipId,
      targetScreen: 'connection-profile',
      connectedUserId: item.connectedUserId,
      connectedUserFirstName: item.connectedUserFirstName,
      connectionType: item.type,
    });

    router.push({
      pathname: '/connection-profile',
      params: {
        sourceScreen: 'dashboard',
        id: String(item.connectedUserId),
        mentorshipId: String(item.mentorshipId),
        type: item.type,
        name: item.connectedUserFirstName,
        initials,
        avatarBg: colors.bg,
        avatarText: colors.text,
        subtitle: item.sharedGoal || (item.type === 'mentor' ? 'Your Mentor' : 'Your Mentee'),
        progress: String(item.progress),
        about: '',
        interests: '[]',
        goals: item.sharedGoal ? JSON.stringify([item.sharedGoal]) : '[]',
        mentoringGoals: '[]',
        preferences: '[]',
        meetings: '[]',
        stat1Label: 'Progress',
        stat1Value: `${item.progress}%`,
        stat2Label: 'Duration',
        stat2Value: `${Math.round((new Date(item.endDate).getTime() - new Date(item.startDate).getTime()) / (1000 * 60 * 60 * 24 * 30))}mo`,
        stat3Label: 'Status',
        stat3Value: 'Active',
      },
    });
  };

  const sectionTitle = isMentor ? 'ACTIVE MENTEES' : 'ACTIVE MENTORS';
  const visibleConnections = isMentor
    ? connections.filter((c) => c.type === 'mentee')
    : connections.filter((c) => c.type === 'mentor');

  if (sessionLoading) {
    return (
      <View style={[styles.container, { justifyContent: 'center', alignItems: 'center' }]}>
        <ActivityIndicator size="large" color="#456B50" />
      </View>
    );
  }

  if (!session) {
    return null;
  }

  return (
    <View style={styles.container}>
      <View style={styles.fixedHeader}>
        <View style={styles.topCircle} />
        <View style={styles.rightCircle} />

        <View style={styles.statusRow}>
          <Text style={styles.statusText}>{new Date().toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit', hour12: false })}</Text>
          <Text style={styles.statusIcons}>▲ ▮</Text>
        </View>

        <View style={styles.headerTopRow}>
          <View style={styles.headerActionGroup}>
            <View style={styles.profileBadge}>
              <Text style={styles.profileBadgeText}>
                {isMentor ? 'Mentor Mode' : 'Mentee Mode'}
              </Text>
            </View>

            <TouchableOpacity
              style={styles.feedButton}
              onPress={openSocialFeed}
            >
              <Text style={styles.feedButtonText}>Feed</Text>
            </TouchableOpacity>
          </View>

          <TouchableOpacity
            style={styles.notificationButton}
            onPress={openNotifications}
          >
            <Text style={styles.notificationIcon}>🔔</Text>
            {unreadCount > 0 && (
              <View style={styles.notificationDot}>
                {unreadCount < 10 && (
                  <Text style={styles.notificationDotText}>{unreadCount}</Text>
                )}
              </View>
            )}
          </TouchableOpacity>
        </View>

        <Text style={styles.title}>
          Your{'\n'}
          <Text style={styles.titleItalic}>Dashboard.</Text>
        </Text>
      </View>

      <ScrollView
        style={styles.scrollArea}
        contentContainerStyle={styles.scrollContent}
        showsVerticalScrollIndicator={false}
      >
        <TouchableOpacity style={styles.feedEntryCard} onPress={openSocialFeed}>
          <View style={styles.feedEntryHeader}>
            <View style={styles.feedEntryInfo}>
              <Text style={styles.feedEntryEyebrow}>SOCIAL FEED</Text>
              <Text style={styles.feedEntryTitle}>Browse updates from your network</Text>
            </View>
            <View style={styles.feedEntryBadge}>
              <Text style={styles.feedEntryBadgeText}>New</Text>
            </View>
          </View>
          <Text style={styles.feedEntryText}>
            Open the mobile social feed to switch between `For You` and `Following`.
          </Text>
          <View style={styles.feedEntryButton}>
            <Text style={styles.feedEntryButtonText}>Open Feed</Text>
          </View>
        </TouchableOpacity>

        <Text style={styles.sectionTitle}>{sectionTitle}</Text>

        {loading ? (
          <ActivityIndicator size="large" color="#456B50" style={{ marginTop: 30 }} />
        ) : visibleConnections.length === 0 ? (
          <View style={styles.emptyStateContainer}>
            <Text style={styles.emptyStateText}>No active connections found.</Text>
          </View>
        ) : (
          visibleConnections.map((item) => {
            const colors = getAvatarColors(item.connectedUserId);
            const initials = item.connectedUserFirstName.substring(0, 2).toUpperCase();
            return (
              <View key={item.mentorshipId} style={styles.activeCard}>
                <TouchableOpacity activeOpacity={0.9} onPress={() => openConnectionProfile(item)}>
                  <View style={styles.topRow}>
                    <View style={[styles.avatar, { backgroundColor: colors.bg }]}>
                      <Text style={[styles.avatarText, { color: colors.text }]}>
                        {initials}
                      </Text>
                    </View>

                    <View style={styles.infoArea}>
                      <Text style={styles.name}>{item.connectedUserFirstName}</Text>
                      <Text style={styles.subtitle}>
                        {item.sharedGoal || (item.type === 'mentor' ? 'Your Mentor' : 'Your Mentee')}
                      </Text>
                    </View>

                    <View style={styles.activeBadge}>
                      <Text style={styles.activeBadgeText}>Active</Text>
                    </View>
                  </View>

                  <View style={styles.progressTrack}>
                    <View style={[styles.progressFill, { width: `${item.progress}%` }]} />
                  </View>
                  <Text style={styles.progressText}>Progress: {item.progress}%</Text>

                  <TouchableOpacity
                    style={styles.viewProfileButton}
                    onPress={() => openConnectionProfile(item)}
                  >
                    <Text style={styles.viewProfileButtonText}>Open Shared Space</Text>
                  </TouchableOpacity>
                </TouchableOpacity>
              </View>
            );
          })
        )}
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#ECE8E1' },
  fixedHeader: {
    backgroundColor: '#456B50',
    paddingTop: 54,
    paddingHorizontal: 24,
    paddingBottom: 24,
    overflow: 'hidden',
  },
  topCircle: {
    position: 'absolute',
    width: 300,
    height: 300,
    borderRadius: 150,
    backgroundColor: 'rgba(255,255,255,0.05)',
    top: -30,
    left: -30,
  },
  rightCircle: {
    position: 'absolute',
    width: 220,
    height: 220,
    borderRadius: 110,
    backgroundColor: 'rgba(255,255,255,0.04)',
    top: 70,
    right: -40,
  },
  statusRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  statusText: { color: '#FFFFFF', fontSize: 16, fontWeight: '700' },
  statusIcons: { color: '#FFFFFF', fontSize: 18, fontWeight: '700' },
  headerTopRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginTop: 18,
    marginBottom: 8,
  },
  profileBadge: {
    backgroundColor: 'rgba(255,255,255,0.15)',
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderRadius: 16,
  },
  profileBadgeText: { color: '#F7F4EE', fontSize: 14, fontWeight: '700' },
  notificationButton: {
    width: 44,
    height: 44,
    borderRadius: 22,
    backgroundColor: 'rgba(255,255,255,0.15)',
    justifyContent: 'center',
    alignItems: 'center',
    position: 'relative',
  },
  notificationIcon: { fontSize: 20 },
  notificationDot: {
    position: 'absolute',
    top: 6,
    right: 6,
    minWidth: 16,
    height: 16,
    borderRadius: 8,
    backgroundColor: '#E76F51',
    borderWidth: 1.5,
    borderColor: '#456B50',
    justifyContent: 'center',
    alignItems: 'center',
    paddingHorizontal: 3,
  },
  notificationDotText: {
    color: '#fff',
    fontSize: 9,
    fontWeight: '700',
  },
  headerActionGroup: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
  },
  feedButton: {
    backgroundColor: 'rgba(255,255,255,0.12)',
    borderRadius: 999,
    paddingHorizontal: 14,
    paddingVertical: 10,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.14)',
  },
  feedButtonText: {
    color: '#F7F4EE',
    fontSize: 13,
    fontWeight: '700',
    letterSpacing: 0.4,
  },
  title: {
    color: '#F7F4EE',
    fontSize: 34,
    lineHeight: 38,
    fontWeight: '700',
    marginTop: 10,
  },
  titleItalic: { fontStyle: 'italic', fontWeight: '700' },
  scrollArea: { flex: 1, backgroundColor: '#ECE8E1' },
  scrollContent: { paddingHorizontal: 24, paddingTop: 24, paddingBottom: 34 },
  feedEntryCard: {
    backgroundColor: '#F8F6F2',
    borderRadius: 24,
    padding: 20,
    marginBottom: 20,
    borderWidth: 1,
    borderColor: '#DDD5CA',
  },
  feedEntryHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    gap: 12,
  },
  feedEntryInfo: {
    flex: 1,
  },
  feedEntryEyebrow: {
    color: '#8B8176',
    fontSize: 11,
    fontWeight: '700',
    letterSpacing: 1.5,
    marginBottom: 6,
  },
  feedEntryTitle: {
    color: '#23372B',
    fontSize: 20,
    lineHeight: 26,
    fontWeight: '700',
  },
  feedEntryBadge: {
    backgroundColor: '#D7E8DA',
    borderRadius: 999,
    paddingHorizontal: 10,
    paddingVertical: 6,
  },
  feedEntryBadgeText: {
    color: '#2F563C',
    fontSize: 12,
    fontWeight: '700',
  },
  feedEntryText: {
    color: '#6F6459',
    fontSize: 14,
    lineHeight: 21,
    marginTop: 12,
    marginBottom: 16,
  },
  feedEntryButton: {
    alignSelf: 'flex-start',
    backgroundColor: '#456B50',
    borderRadius: 16,
    paddingHorizontal: 16,
    paddingVertical: 10,
  },
  feedEntryButtonText: {
    color: '#F8F6F2',
    fontSize: 14,
    fontWeight: '700',
  },
  sectionTitle: {
    fontSize: 13,
    fontWeight: '700',
    letterSpacing: 2,
    color: '#8B8176',
    marginBottom: 18,
  },
  activeCard: {
    backgroundColor: '#F8F6F2',
    borderRadius: 26,
    padding: 18,
    marginBottom: 18,
  },
  topRow: { flexDirection: 'row', alignItems: 'center' },
  avatar: {
    width: 68,
    height: 68,
    borderRadius: 34,
    justifyContent: 'center',
    alignItems: 'center',
    marginRight: 16,
  },
  avatarText: { fontSize: 20, fontWeight: '700' },
  infoArea: { flex: 1 },
  name: { color: '#23372B', fontSize: 17, fontWeight: '700', marginBottom: 4 },
  subtitle: { color: '#9A8F82', fontSize: 13, fontWeight: '500' },
  activeBadge: {
    backgroundColor: '#D7E8DA',
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 14,
  },
  activeBadgeText: { color: '#2F563C', fontSize: 11, fontWeight: '700' },
  progressTrack: {
    height: 9,
    borderRadius: 999,
    backgroundColor: '#DDD8CF',
    overflow: 'hidden',
    marginTop: 18,
    marginBottom: 10,
  },
  progressFill: { height: '100%', borderRadius: 999, backgroundColor: '#5D8D66' },
  progressText: { color: '#8B8176', fontSize: 13, fontWeight: '500', marginBottom: 14 },
  viewProfileButton: {
    backgroundColor: '#D7E8DA',
    borderRadius: 16,
    paddingVertical: 12,
    alignItems: 'center',
  },
  viewProfileButtonText: { color: '#2F563C', fontSize: 14, fontWeight: '700' },
  emptyStateContainer: { paddingVertical: 40, alignItems: 'center' },
  emptyStateText: { color: '#9A8F82', fontSize: 15, fontWeight: '500' },
});
