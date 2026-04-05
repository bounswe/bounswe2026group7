import React, { useState, useEffect } from 'react';
import { router, useLocalSearchParams } from 'expo-router';
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  ScrollView,
  Alert,
  ActivityIndicator,
} from 'react-native';
import apiClient from '../api/client';

function parseString(value: string | string[] | undefined) {
  return Array.isArray(value) ? value[0] : value ?? '';
}

export default function RequestCandidateProfileScreen() {
  const params = useLocalSearchParams();

  const requestId = parseString(params.requestId);
  const menteeId = parseString(params.menteeId);
  const menteeFirstName = parseString(params.menteeFirstName);
  const hiddenInitial = parseString(params.hiddenInitial) || menteeFirstName.substring(0, 2).toUpperCase();
  const avatarBg = parseString(params.avatarBg) || '#D6E8DC';
  const avatarText = parseString(params.avatarText) || '#2F563C';
  const time = parseString(params.time);
  const message = parseString(params.message);

  const [profile, setProfile] = useState<any>(null);
  const [loading, setLoading] = useState(true);
  const [actionLoading, setActionLoading] = useState(false);

  useEffect(() => {
    if (!menteeId) { setLoading(false); return; }
    apiClient.get(`/users/${menteeId}`)
      .then((res) => setProfile(res.data))
      .catch((err) => console.error('Profile fetch error:', err))
      .finally(() => setLoading(false));
  }, [menteeId]);

  const handleAccept = () => {
    Alert.alert(
      'Choose Duration',
      'How long will this mentorship last?',
      [
        {
          text: '1 Month',
          onPress: () => acceptWithDuration(1),
        },
        {
          text: '3 Months',
          onPress: () => acceptWithDuration(3),
        },
        {
          text: '6 Months',
          onPress: () => acceptWithDuration(6),
        },
        { text: 'Cancel', style: 'cancel' },
      ],
    );
  };

  const acceptWithDuration = async (duration: number) => {
    setActionLoading(true);
    try {
      await apiClient.put(`/mentorship-requests/${requestId}/accept`, { duration });
      Alert.alert('Accepted', `${menteeFirstName}'s mentorship request has been accepted (${duration} month${duration > 1 ? 's' : ''}).`);
      router.back();
    } catch (error: any) {
      const msg = error.response?.data?.message || 'Could not accept request.';
      Alert.alert('Error', msg);
    } finally {
      setActionLoading(false);
    }
  };

  const handleReject = () => {
    Alert.alert(
      'Reject Request',
      `Are you sure you want to reject ${menteeFirstName}'s request?`,
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Reject',
          style: 'destructive',
          onPress: async () => {
            setActionLoading(true);
            try {
              await apiClient.put(`/mentorship-requests/${requestId}/reject`);
              Alert.alert('Rejected', `${menteeFirstName}'s mentorship request has been rejected.`);
              router.back();
            } catch (error: any) {
              const msg = error.response?.data?.message || 'Could not reject request.';
              Alert.alert('Error', msg);
            } finally {
              setActionLoading(false);
            }
          },
        },
      ],
    );
  };

  const interests: string[] = profile?.interests ?? [];
  const goals = profile?.goals ? [profile.goals] : [];
  const major = profile?.major || '';
  const backgroundInfo = profile?.backgroundInfo || '';

  return (
    <View style={styles.container}>
      <View style={styles.topStickyArea}>
        <View style={styles.header}>
          <View style={styles.topCircle} />

          <View style={styles.statusRow}>
            <Text style={styles.statusText}>{new Date().toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit', hour12: false })}</Text>
            <Text style={styles.statusIcons}>▲ ▮</Text>
          </View>

          <TouchableOpacity style={styles.backButton} onPress={() => router.back()}>
            <Text style={styles.backButtonText}>‹ Back</Text>
          </TouchableOpacity>

          <Text style={styles.title}>Candidate Profile</Text>
          <Text style={styles.subtitle}>Review before making a decision</Text>
        </View>

        <View style={styles.actionBar}>
          <TouchableOpacity
            style={[styles.acceptButton, actionLoading && styles.disabledButton]}
            onPress={handleAccept}
            disabled={actionLoading}
          >
            <Text style={styles.acceptButtonText}>{actionLoading ? 'Processing...' : 'Accept'}</Text>
          </TouchableOpacity>

          <TouchableOpacity
            style={[styles.rejectButton, actionLoading && styles.disabledButton]}
            onPress={handleReject}
            disabled={actionLoading}
          >
            <Text style={styles.rejectButtonText}>Reject</Text>
          </TouchableOpacity>
        </View>
      </View>

      <ScrollView
        style={styles.scrollArea}
        contentContainerStyle={styles.scrollContent}
        showsVerticalScrollIndicator={false}
      >
        <View style={styles.profileHero}>
          <View style={[styles.avatar, { backgroundColor: avatarBg }]}>
            <Text style={[styles.avatarText, { color: avatarText }]}>
              {hiddenInitial}
            </Text>
          </View>
          <Text style={styles.name}>{menteeFirstName}</Text>
          <Text style={styles.roleText}>
            Candidate Mentee{major ? ` • ${major}` : ''}
          </Text>
          {!!time && (
            <View style={styles.metaBadge}>
              <Text style={styles.metaBadgeText}>{time}</Text>
            </View>
          )}
        </View>

        {!!message && (
          <View style={styles.card}>
            <Text style={styles.sectionLabel}>REQUEST MESSAGE</Text>
            <Text style={styles.bodyText}>{message}</Text>
          </View>
        )}

        {loading ? (
          <ActivityIndicator size="large" color="#456B50" style={{ marginTop: 24 }} />
        ) : (
          <>
            {!!backgroundInfo && (
              <View style={styles.card}>
                <Text style={styles.sectionLabel}>ABOUT</Text>
                <Text style={styles.bodyText}>{backgroundInfo}</Text>
              </View>
            )}

            {(goals.length > 0 || interests.length > 0) && (
              <View style={styles.card}>
                {goals.length > 0 && (
                  <>
                    <Text style={styles.sectionLabel}>GOALS</Text>
                    <View style={styles.tokensWrap}>
                      {goals.map((item, idx) => (
                        <View key={idx} style={styles.tokenChip}>
                          <Text style={styles.tokenText}>{item}</Text>
                        </View>
                      ))}
                    </View>
                  </>
                )}

                {interests.length > 0 && (
                  <>
                    <Text style={styles.sectionLabel}>INTERESTS</Text>
                    <View style={styles.tokensWrap}>
                      {interests.map((item) => (
                        <View key={item} style={styles.tokenChip}>
                          <Text style={styles.tokenText}>{item}</Text>
                        </View>
                      ))}
                    </View>
                  </>
                )}
              </View>
            )}
          </>
        )}

        <View style={styles.bottomSpacer} />
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#ECE8E1' },
  topStickyArea: { backgroundColor: '#ECE8E1', zIndex: 10 },
  header: {
    backgroundColor: '#456B50',
    paddingTop: 54,
    paddingHorizontal: 24,
    paddingBottom: 18,
    overflow: 'hidden',
  },
  topCircle: {
    position: 'absolute',
    width: 280,
    height: 280,
    borderRadius: 140,
    backgroundColor: 'rgba(255,255,255,0.05)',
    top: -30,
    right: -70,
  },
  statusRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  statusText: { color: '#FFFFFF', fontSize: 16, fontWeight: '700' },
  statusIcons: { color: '#FFFFFF', fontSize: 18, fontWeight: '700' },
  backButton: {
    alignSelf: 'flex-start',
    marginTop: 16,
    marginBottom: 10,
    backgroundColor: 'rgba(255,255,255,0.10)',
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.16)',
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderRadius: 16,
  },
  backButtonText: { color: '#F7F4EE', fontSize: 14, fontWeight: '700' },
  title: { color: '#F7F4EE', fontSize: 30, fontWeight: '700', marginBottom: 4 },
  subtitle: { color: 'rgba(247,244,238,0.72)', fontSize: 14, fontWeight: '500' },
  actionBar: {
    flexDirection: 'row',
    paddingHorizontal: 24,
    paddingTop: 14,
    paddingBottom: 14,
    backgroundColor: '#ECE8E1',
    gap: 12,
    borderBottomWidth: 1,
    borderBottomColor: '#DDD5CA',
  },
  acceptButton: {
    flex: 1,
    backgroundColor: '#D7E8DA',
    borderRadius: 18,
    paddingVertical: 16,
    alignItems: 'center',
  },
  acceptButtonText: { color: '#2F563C', fontSize: 15, fontWeight: '700' },
  rejectButton: {
    flex: 1,
    backgroundColor: '#FDF0EF',
    borderRadius: 18,
    paddingVertical: 16,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: '#FAD4D4',
  },
  rejectButtonText: { color: '#D9534F', fontSize: 15, fontWeight: '700' },
  disabledButton: { opacity: 0.5 },
  scrollArea: { flex: 1 },
  scrollContent: { paddingHorizontal: 24, paddingTop: 18, paddingBottom: 24 },
  profileHero: {
    backgroundColor: '#F8F6F2',
    borderRadius: 26,
    padding: 22,
    alignItems: 'center',
    marginBottom: 18,
  },
  avatar: {
    width: 92,
    height: 92,
    borderRadius: 46,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 14,
  },
  avatarText: { fontSize: 28, fontWeight: '700' },
  name: { color: '#23372B', fontSize: 22, fontWeight: '700', marginBottom: 4, textAlign: 'center' },
  roleText: { color: '#8B8176', fontSize: 14, fontWeight: '500', marginBottom: 12, textAlign: 'center' },
  metaBadge: { backgroundColor: '#D7E8DA', paddingHorizontal: 14, paddingVertical: 8, borderRadius: 16 },
  metaBadgeText: { color: '#2F563C', fontSize: 12, fontWeight: '700' },
  card: { backgroundColor: '#F8F6F2', borderRadius: 26, padding: 20, marginBottom: 18 },
  sectionLabel: {
    color: '#8B8176',
    fontSize: 12,
    fontWeight: '700',
    letterSpacing: 1.2,
    marginBottom: 10,
    marginTop: 4,
  },
  bodyText: { color: '#4A4138', fontSize: 15, lineHeight: 22, marginBottom: 12 },
  tokensWrap: { flexDirection: 'row', flexWrap: 'wrap', gap: 8, marginBottom: 10 },
  tokenChip: {
    paddingHorizontal: 14,
    paddingVertical: 9,
    borderRadius: 18,
    backgroundColor: '#EEF3EE',
    borderWidth: 1,
    borderColor: '#D7E8DA',
  },
  tokenText: { color: '#2F563C', fontSize: 13, fontWeight: '600' },
  bottomSpacer: { height: 18 },
});
