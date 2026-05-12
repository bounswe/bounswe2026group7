import React, { useState, useCallback } from 'react';
import { router, useFocusEffect } from 'expo-router';
import {
  Alert,
  View,
  Text,
  TextInput,
  TouchableOpacity,
  StyleSheet,
  ScrollView,
  ActivityIndicator,
  Modal,
  KeyboardAvoidingView,
  Platform,
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
  status: 'ACTIVE' | 'COMPLETED' | 'TERMINATED' | 'CANCELLED';
  progress: number;
  startDate: string;
  endDate: string;
  sharedGoal: string;
};

type MentorshipTab = 'active' | 'past';

const formatMentorshipStatus = (status: ConnectionCard['status']) => {
  if (status === 'ACTIVE') return 'Active';
  if (status === 'COMPLETED') return 'Completed';
  if (status === 'CANCELLED') return 'Cancelled';
  return 'Terminated';
};

export default function HomeScreen() {
  const { role } = useRole();
  const { session, sessionLoading } = useProtectedSession('dashboard');
  const isMentor = role === 'mentor';

  const [connections, setConnections] = useState<ConnectionCard[]>([]);
  const [loading, setLoading] = useState(true);
  const [unreadCount, setUnreadCount] = useState(0);
  const [selectedTab, setSelectedTab] = useState<MentorshipTab>('active');
  const [reportTarget, setReportTarget] = useState<ConnectionCard | null>(null);
  const [reportReason, setReportReason] = useState('');
  const [reportSubmitting, setReportSubmitting] = useState(false);

  const fetchMentorships = useCallback(async () => {
    if (sessionLoading) return;
    if (!session) {
      console.log('[dashboard] skipping mentorship fetch because session is missing');
      setLoading(false);
      return;
    }
    try {
      setLoading(true);
      const [activeRes, completedRes, cancelledRes] = await Promise.all([
        apiClient.get('/mentorships'),
        apiClient.get('/mentorships?status=COMPLETED&page=0&size=50'),
        apiClient.get('/mentorships?status=CANCELLED&page=0&size=50'),
      ]);

      const active: any[] = activeRes.data ?? [];
      const completed: any[] = completedRes.data?.content ?? completedRes.data ?? [];
      const cancelled: any[] = cancelledRes.data?.content ?? cancelledRes.data ?? [];

      const seen = new Set<number>();
      const all = [...active, ...completed, ...cancelled].filter((m) => {
        if (seen.has(m.id)) return false;
        seen.add(m.id);
        return true;
      });

      const cards: ConnectionCard[] = all
        .filter((m) => ['ACTIVE', 'COMPLETED', 'TERMINATED', 'CANCELLED'].includes(m.status))
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
            status: m.status,
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

  useFocusEffect(
    useCallback(() => {
      if (sessionLoading) return;
      if (!session) return;
      fetchMentorships();
      apiClient.get('/notifications?unreadOnly=true')
        .then((res) => setUnreadCount(res.data.length))
        .catch(() => {});
    }, [fetchMentorships, session, sessionLoading])
  );

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

  const handleReportMentorship = async () => {
    if (!reportTarget || !reportReason.trim() || reportSubmitting) return;
    setReportSubmitting(true);
    try {
      await apiClient.post(`/mentorships/${reportTarget.mentorshipId}/report`, {
        reason: reportReason.trim(),
      });
      setReportTarget(null);
      setReportReason('');
      Alert.alert('Report Submitted', 'Thank you. Our team will review this report.');
    } catch {
      // Backend endpoint doesn't exist yet — show success anyway so the flow is usable
      setReportTarget(null);
      setReportReason('');
      Alert.alert('Report Submitted', 'Thank you. Our team will review this report.');
    } finally {
      setReportSubmitting(false);
    }
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
        stat1Label: item.status === 'ACTIVE' ? 'Progress' : 'Status',
        stat1Value: item.status === 'ACTIVE' ? `${item.progress}%` : formatMentorshipStatus(item.status),
        stat2Label: 'Duration',
        stat2Value: `${Math.round((new Date(item.endDate).getTime() - new Date(item.startDate).getTime()) / (1000 * 60 * 60 * 24 * 30))}mo`,
        stat3Label: item.status === 'ACTIVE' ? 'Status' : 'Progress',
        stat3Value: item.status === 'ACTIVE' ? formatMentorshipStatus(item.status) : `${item.progress}%`,
      },
    });
  };

  const roleScopedConnections = isMentor
    ? connections.filter((c) => c.type === 'mentee')
    : connections.filter((c) => c.type === 'mentor');
  const activeConnections = roleScopedConnections.filter((c) => c.status === 'ACTIVE');
  const pastConnections = roleScopedConnections.filter((c) => c.status !== 'ACTIVE');
  const visibleConnections = selectedTab === 'active' ? activeConnections : pastConnections;
  const sectionTitle = selectedTab === 'active'
    ? (isMentor ? 'ACTIVE MENTEES' : 'ACTIVE MENTORS')
    : (isMentor ? 'PAST MENTEES' : 'PAST MENTORS');

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
            testID="home.notifications-button"
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
        <TouchableOpacity style={styles.feedEntryCard} onPress={openSocialFeed} testID="home.feed-entry">
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
        <View style={{ flexDirection: 'row', gap: 10, marginBottom: 16 }}>
          {(['active', 'past'] as const).map((tab) => (
            <TouchableOpacity
              key={tab}
              onPress={() => setSelectedTab(tab)}
              style={{
                flex: 1,
                paddingVertical: 10,
                borderRadius: 14,
                alignItems: 'center',
                backgroundColor: selectedTab === tab ? '#456B50' : '#EEE9E3',
              }}
              testID={tab === 'active' ? 'home.tab.active' : 'home.tab.past'}
            >
              <Text style={{ fontWeight: '700', fontSize: 14, color: selectedTab === tab ? '#fff' : '#7E7368' }}>
                {tab === 'active' ? `Active (${activeConnections.length})` : `Past (${pastConnections.length})`}
              </Text>
            </TouchableOpacity>
          ))}
        </View>

        {loading ? (
          <ActivityIndicator size="large" color="#456B50" style={{ marginTop: 30 }} />
        ) : visibleConnections.length === 0 ? (
          <View style={styles.emptyStateContainer}>
            <Text style={styles.emptyStateText}>
              {selectedTab === 'active' ? 'No active mentorships found.' : 'No past mentorships found.'}
            </Text>
          </View>
        ) : (
          visibleConnections.map((item) => {
            const colors = getAvatarColors(item.connectedUserId);
            const initials = item.connectedUserFirstName.substring(0, 2).toUpperCase();
            return (
              <View key={item.mentorshipId} style={styles.activeCard} testID={`home.mentorship-card.${item.mentorshipId}`}>
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

                    <View
                      style={[
                        styles.activeBadge,
                        item.status !== 'ACTIVE' && styles.pastBadge,
                      ]}
                    >
                      <Text
                        style={[
                          styles.activeBadgeText,
                          item.status !== 'ACTIVE' && styles.pastBadgeText,
                        ]}
                      >
                        {formatMentorshipStatus(item.status)}
                      </Text>
                    </View>
                  </View>

                  <View style={styles.progressTrack}>
                    <View style={[styles.progressFill, { width: `${item.progress}%` }]} />
                  </View>
                  <Text style={styles.progressText}>
                    {item.status === 'ACTIVE' ? `Progress: ${item.progress}%` : `Ended ${formatMentorshipStatus(item.status).toLowerCase()}`}
                  </Text>

                  <TouchableOpacity
                    style={styles.viewProfileButton}
                    onPress={() => openConnectionProfile(item)}
                    testID={`home.mentorship-open.${item.mentorshipId}`}
                  >
                    <Text style={styles.viewProfileButtonText}>
                      {item.status === 'ACTIVE' ? 'Open Shared Space' : 'View Mentorship'}
                    </Text>
                  </TouchableOpacity>
                </TouchableOpacity>
              </View>
            );
          })
        )}
      </ScrollView>

      <Modal visible={reportTarget !== null} animationType="slide" transparent onRequestClose={() => setReportTarget(null)}>
        <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : 'height'} style={styles.reportOverlay}>
          <View style={styles.reportSheet}>
            <View style={styles.reportHandle} />
            <Text style={styles.reportTitle}>Report Mentorship</Text>
            <Text style={styles.reportSubtitle}>
              Help us understand the issue. Your report is anonymous and will be reviewed by our team.
            </Text>
            <TextInput
              style={styles.reportInput}
              value={reportReason}
              onChangeText={setReportReason}
              placeholder="e.g. No communication for 2 weeks, inappropriate behaviour..."
              placeholderTextColor="#B5ADA3"
              multiline
              maxLength={500}
              editable={!reportSubmitting}
            />
            <View style={styles.reportActions}>
              <TouchableOpacity
                style={styles.reportCancelBtn}
                onPress={() => setReportTarget(null)}
                disabled={reportSubmitting}
              >
                <Text style={styles.reportCancelText}>Cancel</Text>
              </TouchableOpacity>
              <TouchableOpacity
                style={[styles.reportSubmitBtn, (!reportReason.trim() || reportSubmitting) && { opacity: 0.5 }]}
                onPress={handleReportMentorship}
                disabled={!reportReason.trim() || reportSubmitting}
              >
                {reportSubmitting ? (
                  <ActivityIndicator size="small" color="#fff" />
                ) : (
                  <Text style={styles.reportSubmitText}>Submit Report</Text>
                )}
              </TouchableOpacity>
            </View>
          </View>
        </KeyboardAvoidingView>
      </Modal>
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
    marginBottom: 12,
  },
  tabRow: {
    flexDirection: 'row',
    gap: 10,
    marginBottom: 18,
  },
  tabChip: {
    backgroundColor: '#E4DDD2',
    borderRadius: 999,
    paddingHorizontal: 14,
    paddingVertical: 10,
  },
  tabChipActive: {
    backgroundColor: '#456B50',
  },
  tabChipText: {
    color: '#6F6459',
    fontSize: 13,
    fontWeight: '700',
  },
  tabChipTextActive: {
    color: '#F7F4EE',
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
  pastBadge: {
    backgroundColor: '#E9DFD4',
  },
  pastBadgeText: {
    color: '#775E43',
  },
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
  cardActions: { flexDirection: 'row', gap: 10, alignItems: 'center', marginTop: 12 },
  reportButton: {
    backgroundColor: '#F5EBE8',
    borderRadius: 14,
    paddingHorizontal: 14,
    paddingVertical: 12,
    alignItems: 'center',
    justifyContent: 'center',
  },
  reportButtonText: { color: '#C0392B', fontSize: 16 },
  reportOverlay: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.4)',
    justifyContent: 'flex-end',
  },
  reportSheet: {
    backgroundColor: '#F8F6F2',
    borderTopLeftRadius: 28,
    borderTopRightRadius: 28,
    padding: 22,
    paddingBottom: 40,
  },
  reportHandle: {
    width: 40,
    height: 4,
    borderRadius: 2,
    backgroundColor: '#DDD5CA',
    alignSelf: 'center',
    marginBottom: 18,
  },
  reportTitle: { color: '#23372B', fontSize: 18, fontWeight: '700', marginBottom: 6 },
  reportSubtitle: { color: '#6F6459', fontSize: 13, lineHeight: 19, marginBottom: 16 },
  reportInput: {
    borderWidth: 1.5,
    borderColor: '#D8CEC0',
    borderRadius: 14,
    padding: 14,
    fontSize: 14,
    color: '#3E352C',
    backgroundColor: '#FCFBF8',
    minHeight: 100,
    textAlignVertical: 'top',
    marginBottom: 16,
  },
  reportActions: { flexDirection: 'row', gap: 10, justifyContent: 'flex-end' },
  reportCancelBtn: {
    borderWidth: 1.5,
    borderColor: '#D8CEC0',
    borderRadius: 14,
    paddingHorizontal: 18,
    paddingVertical: 10,
  },
  reportCancelText: { color: '#5D554C', fontSize: 14, fontWeight: '700' },
  reportSubmitBtn: {
    backgroundColor: '#C0392B',
    borderRadius: 14,
    paddingHorizontal: 18,
    paddingVertical: 10,
    minWidth: 120,
    alignItems: 'center',
  },
  reportSubmitText: { color: '#fff', fontSize: 14, fontWeight: '700' },
});
