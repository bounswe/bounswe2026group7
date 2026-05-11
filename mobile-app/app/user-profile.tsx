import React, { useState, useEffect, useCallback } from 'react';
import { router, useLocalSearchParams } from 'expo-router';
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  ScrollView,
  ActivityIndicator,
  Alert,
} from 'react-native';
import * as SecureStore from 'expo-secure-store';
import apiClient from '../api/client';
import ActionModal from '../components/ActionModal';

function parseString(v: string | string[] | undefined) {
  return Array.isArray(v) ? v[0] : v ?? '';
}

export default function UserProfileScreen() {
  const params = useLocalSearchParams();
  const userId = parseString(params.userId);

  const [profile, setProfile] = useState<any>(null);
  const [loading, setLoading] = useState(true);
  const [isFollowing, setIsFollowing] = useState(false);
  const [followLoading, setFollowLoading] = useState(false);
  const [myUserId, setMyUserId] = useState<string | null>(null);

  useEffect(() => {
    SecureStore.getItemAsync('userId').then((id) => setMyUserId(id));
  }, []);

  const fetchProfile = useCallback(async () => {
    if (!userId) return;
    try {
      const res = await apiClient.get(`/users/${userId}`);
      setProfile(res.data);
    } catch {
      Alert.alert('Error', 'Could not load profile.');
    } finally {
      setLoading(false);
    }
  }, [userId]);

  const checkFollowStatus = useCallback(async () => {
    if (!myUserId || !userId) return;
    try {
      const res = await apiClient.get(`/users/${myUserId}/following?size=200`);
      const list: any[] = res.data?.content ?? res.data ?? [];
      setIsFollowing(list.some((u: any) => String(u.id) === String(userId)));
    } catch {
      // silently ignore
    }
  }, [myUserId, userId]);

  useEffect(() => {
    fetchProfile();
  }, [fetchProfile]);

  useEffect(() => {
    if (myUserId) checkFollowStatus();
  }, [myUserId, checkFollowStatus]);

  const toggleFollow = async () => {
    setFollowLoading(true);
    try {
      if (isFollowing) {
        await apiClient.delete(`/users/${userId}/follow`);
        setIsFollowing(false);
        setProfile((prev: any) => prev ? { ...prev, followerCount: (prev.followerCount ?? 1) - 1 } : prev);
      } else {
        await apiClient.post(`/users/${userId}/follow`);
        setIsFollowing(true);
        setProfile((prev: any) => prev ? { ...prev, followerCount: (prev.followerCount ?? 0) + 1 } : prev);
      }
    } catch (err: any) {
      const msg = err?.response?.data?.message;
      if (msg) Alert.alert('Error', msg);
    } finally {
      setFollowLoading(false);
    }
  };

  const isOwnProfile = myUserId && String(userId) === String(myUserId);

  const [reportModalVisible, setReportModalVisible] = useState(false);
  const [reportReason, setReportReason] = useState('');

  const confirmReport = () => {
    if (!reportReason.trim()) return;
    setReportModalVisible(false);
    setReportReason('');
    Alert.alert('Report Submitted', 'Thank you. Our team will review this report.');
  };

  const firstName = profile?.firstName ?? '';
  const lastName = profile?.lastName ?? '';
  const fullName = [firstName, lastName].filter(Boolean).join(' ') || 'User';
  const role = profile?.role ?? '';
  const bio = profile?.backgroundInfo ?? profile?.goals ?? profile?.bio ?? '';
  const followerCount = profile?.followerCount ?? 0;
  const followingCount = profile?.followingCount ?? 0;

  return (
    <View style={styles.container}>
      <ActionModal
        visible={reportModalVisible}
        title="Report User"
        message="Help us understand the issue. Your report is anonymous."
        fields={[{
          label: 'Reason',
          placeholder: 'e.g. spam, harassment, inappropriate content',
          value: reportReason,
          onChange: setReportReason,
          multiline: true,
          required: true,
        }]}
        confirmLabel="Submit Report"
        danger
        onConfirm={confirmReport}
        onCancel={() => { setReportModalVisible(false); setReportReason(''); }}
      />
      <View style={styles.header}>
        <TouchableOpacity onPress={() => router.back()} style={styles.backButton}>
          <Text style={styles.backText}>‹ Back</Text>
        </TouchableOpacity>
      </View>

      {loading ? (
        <ActivityIndicator size="large" color="#456B50" style={{ marginTop: 60 }} />
      ) : (
        <ScrollView contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
          <View style={styles.avatarWrap}>
            <View style={styles.avatar}>
              <Text style={styles.avatarText}>
                {fullName.split(' ').map((n: string) => n[0]).join('').substring(0, 2).toUpperCase()}
              </Text>
            </View>
          </View>

          <Text style={styles.name}>{fullName}</Text>
          <View style={styles.roleBadge}>
            <Text style={styles.roleText}>{role === 'MENTOR' ? 'Mentor' : 'Mentee'}</Text>
          </View>

          <View style={styles.statsRow}>
            <View style={styles.stat}>
              <Text style={styles.statNum}>{followerCount}</Text>
              <Text style={styles.statLabel}>Followers</Text>
            </View>
            <View style={styles.statDivider} />
            <View style={styles.stat}>
              <Text style={styles.statNum}>{followingCount}</Text>
              <Text style={styles.statLabel}>Following</Text>
            </View>
          </View>

          {!isOwnProfile && (
            <TouchableOpacity
              style={[styles.followBtn, isFollowing && styles.followingBtn]}
              onPress={toggleFollow}
              disabled={followLoading}
            >
              {followLoading ? (
                <ActivityIndicator size="small" color={isFollowing ? '#456B50' : '#fff'} />
              ) : (
                <Text style={[styles.followBtnText, isFollowing && styles.followingBtnText]}>
                  {isFollowing ? 'Following' : 'Follow'}
                </Text>
              )}
            </TouchableOpacity>
          )}

          {bio ? (
            <View style={styles.bioCard}>
              <Text style={styles.bioLabel}>ABOUT</Text>
              <Text style={styles.bioText}>{bio}</Text>
            </View>
          ) : null}

          {!isOwnProfile && (
            <TouchableOpacity style={styles.reportBtn} onPress={() => setReportModalVisible(true)}>
              <Text style={styles.reportBtnText}>Report User</Text>
            </TouchableOpacity>
          )}
        </ScrollView>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#EEF0F4' },
  header: {
    backgroundColor: '#456B50',
    paddingTop: 54,
    paddingHorizontal: 24,
    paddingBottom: 20,
  },
  backButton: {
    backgroundColor: 'rgba(255,255,255,0.12)',
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.18)',
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderRadius: 16,
    alignSelf: 'flex-start',
  },
  backText: { color: '#F7F4EE', fontSize: 14, fontWeight: '700' },
  content: { paddingHorizontal: 24, paddingTop: 32, paddingBottom: 48, alignItems: 'center' },
  avatarWrap: { marginBottom: 16 },
  avatar: {
    width: 88,
    height: 88,
    borderRadius: 44,
    backgroundColor: '#D7E8DA',
    alignItems: 'center',
    justifyContent: 'center',
  },
  avatarText: { fontSize: 30, fontWeight: '700', color: '#2F563C' },
  name: { fontSize: 26, fontWeight: '700', color: '#1D1D38', marginBottom: 8 },
  roleBadge: {
    backgroundColor: '#D7E8DA',
    paddingHorizontal: 14,
    paddingVertical: 5,
    borderRadius: 12,
    marginBottom: 24,
  },
  roleText: { fontSize: 13, fontWeight: '700', color: '#2F563C' },
  statsRow: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#F8F8F7',
    borderRadius: 20,
    paddingVertical: 18,
    paddingHorizontal: 32,
    marginBottom: 24,
    gap: 24,
  },
  stat: { alignItems: 'center' },
  statNum: { fontSize: 22, fontWeight: '700', color: '#1D1D38' },
  statLabel: { fontSize: 12, color: '#8C8A8A', fontWeight: '500', marginTop: 2 },
  statDivider: { width: 1, height: 36, backgroundColor: '#E0DED9' },
  followBtn: {
    backgroundColor: '#456B50',
    borderRadius: 20,
    paddingVertical: 14,
    paddingHorizontal: 48,
    marginBottom: 28,
    minWidth: 160,
    alignItems: 'center',
  },
  followingBtn: {
    backgroundColor: '#F8F8F7',
    borderWidth: 1.5,
    borderColor: '#456B50',
  },
  followBtnText: { fontSize: 16, fontWeight: '700', color: '#fff' },
  followingBtnText: { color: '#456B50' },
  bioCard: {
    backgroundColor: '#F8F8F7',
    borderRadius: 20,
    padding: 20,
    width: '100%',
  },
  bioLabel: { fontSize: 11, fontWeight: '700', letterSpacing: 1.5, color: '#A0A0A0', marginBottom: 8 },
  bioText: { fontSize: 15, color: '#3A3A3A', lineHeight: 22 },
  reportBtn: {
    marginTop: 16,
    borderWidth: 1,
    borderColor: '#FAD4D4',
    borderRadius: 16,
    paddingVertical: 12,
    paddingHorizontal: 32,
    alignItems: 'center',
  },
  reportBtnText: { fontSize: 14, fontWeight: '600', color: '#D9534F' },
});
