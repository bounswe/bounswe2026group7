import React, { useState, useEffect, useCallback } from 'react';
import { router } from 'expo-router';
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  ScrollView,
  ActivityIndicator,
  Alert,
} from 'react-native';
import apiClient from '../api/client';
import { useRole } from '../components/RoleContext';

const AVATAR_COLORS = [
  { bg: '#D6E8DC', text: '#2F563C' },
  { bg: '#DFD9C9', text: '#66582F' },
  { bg: '#CCD6E5', text: '#4A5D7A' },
  { bg: '#E2D1E6', text: '#6D3F72' },
  { bg: '#F1E1BB', text: '#8A5D12' },
  { bg: '#D8E5F1', text: '#315A7A' },
];

const getAvatarColors = (id: number) => AVATAR_COLORS[id % AVATAR_COLORS.length];

const formatTime = (isoString: string) => {
  const date = new Date(isoString);
  const now = new Date();
  const diffMs = now.getTime() - date.getTime();
  const diffHours = Math.floor(diffMs / (1000 * 60 * 60));
  const diffDays = Math.floor(diffHours / 24);
  if (diffDays > 0) return `${diffDays} day${diffDays > 1 ? 's' : ''} ago`;
  if (diffHours > 0) return `${diffHours} hour${diffHours > 1 ? 's' : ''} ago`;
  return 'Just now';
};

type IncomingRequest = {
  id: number;
  menteeId: number;
  menteeFirstName: string;
  message: string;
  createdAt: string;
};

type ActiveMentorship = {
  id: number;
  mentorId?: number;
  mentorFirstName?: string;
  menteeId?: number;
  menteeFirstName?: string;
  startDate: string;
  endDate: string;
};

export default function MentorshipRequestsScreen() {
  const { role } = useRole();
  const isMentor = role === 'mentor';
  const [incomingRequests, setIncomingRequests] = useState<IncomingRequest[]>([]);
  const [activeMentorships, setActiveMentorships] = useState<ActiveMentorship[]>([]);
  const [loading, setLoading] = useState(true);

  const fetchData = useCallback(async () => {
    try {
      setLoading(true);
      console.log('[mentorship-requests] loading request lists', {
        currentRole: role,
        sentEndpoint: isMentor ? '/mentorship-requests/received' : '/mentorship-requests/sent',
        activeMentorshipsEndpoint: '/mentorships',
        note: 'This screen currently lists mentorship requests only, not change or end requests.',
      });
      const [requestsRes, mentorshipsRes] = await Promise.all([
        apiClient.get(isMentor ? '/mentorship-requests/received' : '/mentorship-requests/sent'),
        apiClient.get('/mentorships'),
      ]);

      const pending = (requestsRes.data.content ?? requestsRes.data).filter(
        (r: any) => r.status === 'PENDING'
      );
      console.log('[mentorship-requests] loaded mentorship requests', {
        count: pending.length,
        rawStatuses: (requestsRes.data.content ?? requestsRes.data).map((r: any) => r.status),
      });
      setIncomingRequests(pending);
      setActiveMentorships(mentorshipsRes.data);
    } catch (error: any) {
      console.error('Error fetching mentorship data:', error);
      Alert.alert('Error', 'Could not load mentorship data.');
    } finally {
      setLoading(false);
    }
  }, [isMentor]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  const openCandidateProfile = (item: IncomingRequest) => {
    if (!isMentor) return;
    const colors = getAvatarColors(item.menteeId);
    router.push({
      pathname: '/request-candidate-profile',
      params: {
        requestId: String(item.id),
        menteeId: String(item.menteeId),
        menteeFirstName: item.menteeFirstName,
        hiddenInitial: item.menteeFirstName.substring(0, 2).toUpperCase(),
        avatarBg: colors.bg,
        avatarText: colors.text,
        time: formatTime(item.createdAt),
        message: item.message || '',
      },
    });
  };

  if (loading) {
    return (
      <View style={[styles.container, { justifyContent: 'center', alignItems: 'center' }]}>
        <ActivityIndicator size="large" color="#456B50" />
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <View style={styles.fixedHeader}>
        <View style={styles.topCircle} />

        <View style={styles.statusRow}>
          <Text style={styles.statusText}>{new Date().toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit', hour12: false })}</Text>
          <Text style={styles.statusIcons}>▲ ▮</Text>
        </View>

        <View style={styles.headerTopRow}>
          <TouchableOpacity
            style={styles.backButton}
            onPress={() => router.back()}
          >
            <Text style={styles.backButtonText}>‹ Back</Text>
          </TouchableOpacity>
        </View>

        <Text style={styles.title}>
          {isMentor ? 'Mentorship' : 'My'}{'\n'}
          <Text style={styles.titleItalic}>Requests.</Text>
        </Text>
      </View>

      <ScrollView
        style={styles.scrollArea}
        contentContainerStyle={styles.scrollContent}
        showsVerticalScrollIndicator={false}
      >
        <Text style={styles.sectionTitle}>{isMentor ? 'INCOMING' : 'PENDING'}</Text>

        {incomingRequests.length === 0 ? (
          <View style={styles.emptyState}>
            <Text style={styles.emptyStateText}>No pending requests.</Text>
          </View>
        ) : (
          incomingRequests.map((item) => {
            const displayName = isMentor ? item.menteeFirstName : 'Request Pending';
            const colors = getAvatarColors(isMentor ? item.menteeId : item.id);
            return (
              <View key={item.id} style={styles.requestCard}>
                <View style={styles.topRow}>
                  <View style={[styles.avatar, { backgroundColor: colors.bg }]}>
                    <Text style={[styles.avatarText, { color: colors.text }]}>
                      {displayName.substring(0, 2).toUpperCase()}
                    </Text>
                  </View>
                  <View style={styles.infoArea}>
                    <Text style={styles.name}>{displayName}</Text>
                    <Text style={styles.time}>{formatTime(item.createdAt)}</Text>
                  </View>
                </View>

                {!!item.message && (
                  <Text style={styles.message}>{item.message}</Text>
                )}

                {isMentor ? (
                  <TouchableOpacity
                    style={styles.viewProfileButton}
                    onPress={() => openCandidateProfile(item)}
                  >
                    <Text style={styles.viewProfileButtonText}>View Profile</Text>
                  </TouchableOpacity>
                ) : (
                  <View style={styles.pendingBadge}>
                    <Text style={styles.pendingBadgeText}>Awaiting mentor response</Text>
                  </View>
                )}
              </View>
            );
          })
        )}

        <Text style={styles.sectionTitle}>ACTIVE MENTORSHIPS</Text>

        {activeMentorships.length === 0 ? (
          <View style={styles.emptyState}>
            <Text style={styles.emptyStateText}>No active mentorships.</Text>
          </View>
        ) : (
          activeMentorships.map((item) => {
            const counterpartId = isMentor ? item.menteeId ?? item.id : item.mentorId ?? item.id;
            const counterpartName = isMentor
              ? item.menteeFirstName || 'Mentee'
              : item.mentorFirstName || 'Mentor';
            const colors = getAvatarColors(counterpartId);
            const start = new Date(item.startDate).getTime();
            const end = new Date(item.endDate).getTime();
            const now = Date.now();
            const progress = Math.min(
              100,
              Math.max(0, Math.round(((now - start) / (end - start)) * 100))
            );
            return (
              <View key={item.id} style={styles.activeCard}>
                <View style={styles.topRow}>
                  <View style={[styles.avatar, { backgroundColor: colors.bg }]}>
                    <Text style={[styles.avatarText, { color: colors.text }]}>
                      {counterpartName.substring(0, 2).toUpperCase()}
                    </Text>
                  </View>
                  <View style={styles.infoArea}>
                    <Text style={styles.name}>{counterpartName}</Text>
                    <Text style={styles.subtitle}>{isMentor ? 'Mentee · Active' : 'Mentor · Active'}</Text>
                  </View>
                  <View style={styles.activeBadge}>
                    <Text style={styles.activeBadgeText}>Active</Text>
                  </View>
                </View>

                <View style={styles.progressTrack}>
                  <View style={[styles.progressFill, { width: `${progress}%` }]} />
                </View>
                <Text style={styles.progressText}>Progress: {progress}%</Text>
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
    right: -70,
  },
  statusRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  statusText: { color: '#FFFFFF', fontSize: 16, fontWeight: '700' },
  statusIcons: { color: '#FFFFFF', fontSize: 18, fontWeight: '700' },
  headerTopRow: {
    flexDirection: 'row',
    justifyContent: 'flex-start',
    alignItems: 'center',
    marginTop: 18,
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
  title: { color: '#F7F4EE', fontSize: 34, lineHeight: 38, fontWeight: '700', marginTop: 6 },
  titleItalic: { fontStyle: 'italic', fontWeight: '700' },
  scrollArea: { flex: 1, backgroundColor: '#ECE8E1' },
  scrollContent: { paddingHorizontal: 24, paddingTop: 22, paddingBottom: 34 },
  sectionTitle: {
    color: '#8B8176',
    fontSize: 13,
    fontWeight: '700',
    letterSpacing: 2,
    marginBottom: 16,
  },
  emptyState: { paddingVertical: 20, alignItems: 'center', marginBottom: 24 },
  emptyStateText: { color: '#9A8F82', fontSize: 15, fontWeight: '500' },
  requestCard: {
    backgroundColor: '#F8F6F2',
    borderRadius: 26,
    padding: 18,
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
    width: 78,
    height: 78,
    borderRadius: 39,
    justifyContent: 'center',
    alignItems: 'center',
    marginRight: 16,
  },
  avatarText: { fontSize: 22, fontWeight: '700' },
  infoArea: { flex: 1 },
  name: { color: '#23372B', fontSize: 17, fontWeight: '700', marginBottom: 4 },
  time: { color: '#9A8F82', fontSize: 13, fontWeight: '500' },
  subtitle: { color: '#9A8F82', fontSize: 13, fontWeight: '500' },
  message: {
    color: '#5A524A',
    fontSize: 15,
    lineHeight: 20,
    marginTop: 18,
    marginBottom: 18,
  },
  viewProfileButton: {
    backgroundColor: '#D7E8DA',
    borderRadius: 18,
    paddingVertical: 16,
    alignItems: 'center',
  },
  viewProfileButtonText: { color: '#2F563C', fontSize: 15, fontWeight: '700' },
  pendingBadge: {
    backgroundColor: '#F1E1BB',
    borderRadius: 18,
    paddingVertical: 16,
    alignItems: 'center',
  },
  pendingBadgeText: { color: '#8A5D12', fontSize: 14, fontWeight: '700' },
  activeBadge: {
    backgroundColor: '#D7E8DA',
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderRadius: 16,
  },
  activeBadgeText: { color: '#2F563C', fontSize: 12, fontWeight: '700' },
  progressTrack: {
    height: 9,
    borderRadius: 999,
    backgroundColor: '#DDD8CF',
    overflow: 'hidden',
    marginTop: 18,
    marginBottom: 10,
  },
  progressFill: { height: '100%', borderRadius: 999, backgroundColor: '#5D8D66' },
  progressText: { color: '#8B8176', fontSize: 13, fontWeight: '500' },
});
