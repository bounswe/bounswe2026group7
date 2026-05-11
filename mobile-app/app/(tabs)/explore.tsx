import React, { useState, useEffect } from 'react';
import { router } from 'expo-router';
import { useRole } from '../../components/RoleContext';
import apiClient from '../../api/client';
import * as SecureStore from 'expo-secure-store';
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  StyleSheet,
  ScrollView,
  ActivityIndicator,
  Alert,
} from 'react-native';

// Yardımcı Fonksiyon: Baş harfleri hesaplar
const getInitials = (name: string) => {
  if (!name) return 'U';
  const parts = name.trim().split(' ');
  if (parts.length > 1) {
    return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
  }
  return name.substring(0, 2).toUpperCase();
};

type MentorCard = {
  id: string;
  initials: string;
  avatarBg: string;
  avatarText: string;
  name: string;
  role: string;
  available: boolean;
  tags: string[];
  rating: string;
  reviews: string;
  about: string;
  mentoringGoals: string[];
  preferredMenteeCriteria: string[];
  availability: string[];
  following: boolean;
  followLoading: boolean;
};

export default function ExploreScreen() {
  const { role } = useRole();
  const isMentor = role === 'mentor';

  if (isMentor) {
    return <MentorRequestsContent />;
  }

  return <MenteeExploreContent />;
}

const PAGE_SIZE = 5;

function mapMentor(m: any, isMatch = false): MentorCard {
  const fullName = m.lastName ? `${m.firstName} ${m.lastName}` : m.firstName;
  const hasCapacity =
    m.maxMenteeCapacity == null
      ? true
      : (m.currentMenteeCount ?? 0) < m.maxMenteeCapacity;
  return {
    id: String(m.id),
    name: fullName,
    initials: getInitials(fullName),
    role: m.field || m.expertise || 'Mentor',
    avatarBg: isMatch ? '#D8E5F1' : '#D6E8DC',
    avatarText: isMatch ? '#315A7A' : '#2F563C',
    available: hasCapacity,
    tags: m.interests || [],
    rating: '5.0',
    reviews: '0',
    about: m.bio || 'No bio provided.',
    mentoringGoals: [],
    preferredMenteeCriteria: [],
    availability: [],
    following: false,
    followLoading: false,
  };
}

function MenteeExploreContent() {
  const [mentors, setMentors] = useState<MentorCard[]>([]);
  const [loading, setLoading] = useState(true);
  const [currentPage, setCurrentPage] = useState(0);
  const [isMatchMode, setIsMatchMode] = useState(false);
  const [matchLoading, setMatchLoading] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const scrollRef = React.useRef<ScrollView>(null);

  useEffect(() => {
    const fetchMentors = async () => {
      try {
        const [mentorsRes, myId] = await Promise.all([
          apiClient.get('/users/mentors'),
          SecureStore.getItemAsync('userId'),
        ]);
        const data: any[] = mentorsRes.data.content ?? mentorsRes.data;
        const mapped = data.map((m) => mapMentor(m, false));
        if (myId) {
          try {
            const followRes = await apiClient.get(`/users/${myId}/following?size=100`);
            const followingIds = new Set(
              (followRes.data.content ?? followRes.data).map((u: any) => String(u.id))
            );
            setMentors(mapped.map((m) => ({ ...m, following: followingIds.has(m.id) })));
          } catch {
            setMentors(mapped);
          }
        } else {
          setMentors(mapped);
        }
      } catch (error) {
        console.error('Mentorları çekerken hata oluştu:', error);
      } finally {
        setLoading(false);
      }
    };
    fetchMentors();
  }, []);

  const handleFollow = async (mentorId: string) => {
    const mentor = mentors.find((m) => m.id === mentorId);
    if (!mentor || mentor.followLoading) return;
    setMentors((prev) => prev.map((m) => m.id === mentorId ? { ...m, followLoading: true } : m));
    try {
      if (mentor.following) {
        await apiClient.delete(`/users/${mentorId}/follow`);
        setMentors((prev) => prev.map((m) => m.id === mentorId ? { ...m, following: false, followLoading: false } : m));
      } else {
        await apiClient.post(`/users/${mentorId}/follow`, {});
        setMentors((prev) => prev.map((m) => m.id === mentorId ? { ...m, following: true, followLoading: false } : m));
      }
    } catch (err: any) {
      setMentors((prev) => prev.map((m) => m.id === mentorId ? { ...m, followLoading: false } : m));
      const status = err?.response?.status ? ` (${err.response.status})` : '';
      const msg = err?.response?.data?.message || err?.response?.data?.error || err?.message || 'Could not update follow status.';
      Alert.alert('Follow Error' + status, msg);
    }
  };

  const toggleMatchMode = async () => {
    if (isMatchMode) {
      setIsMatchMode(false);
      setCurrentPage(0);
      return;
    }
    setMatchLoading(true);
    try {
      const res = await apiClient.get('/matching/mentors/all');
      setMentors((res.data as any[]).map((m) => mapMentor(m, true)));
      setIsMatchMode(true);
      setCurrentPage(0);
      scrollRef.current?.scrollTo({ y: 0, animated: true });
    } catch (err: any) {
      if (err?.response?.status === 403) {
        Alert.alert(
          'Matching Unavailable',
          'You already have an active mentor. End your current mentorship first to find new matches.'
        );
      } else {
        console.error('Matching error:', err);
      }
    } finally {
      setMatchLoading(false);
    }
  };

  const filteredMentors = searchQuery.trim()
    ? mentors.filter((m) => {
        const q = searchQuery.toLowerCase();
        return (
          m.name.toLowerCase().includes(q) ||
          m.role.toLowerCase().includes(q) ||
          m.tags.some((t) => t.toLowerCase().includes(q))
        );
      })
    : mentors;
  const totalPages = Math.ceil(filteredMentors.length / PAGE_SIZE);
  const pagedMentors = filteredMentors.slice(currentPage * PAGE_SIZE, (currentPage + 1) * PAGE_SIZE);

  const goToPage = (page: number) => {
    setCurrentPage(page);
    scrollRef.current?.scrollTo({ y: 0, animated: true });
  };

  const openMentorProfile = (mentor: MentorCard) => {
    router.push({
      pathname: '/mentor-public-profile',
      params: {
        mentorId: mentor.id,
        id: mentor.id,
        initials: mentor.initials,
        name: mentor.name,
        role: mentor.role,
        available: mentor.available ? 'true' : 'false',
        tags: JSON.stringify(mentor.tags),
        rating: mentor.rating,
        reviews: mentor.reviews,
        about: mentor.about,
      },
    });
  };

  return (
    <View style={styles.container}>
      <View style={styles.fixedHeader}>
        <View style={styles.topCircle} />
        <Text style={styles.title}>Find a{'\n'}<Text style={styles.titleItalic}>Mentor.</Text></Text>
        <View style={styles.searchBox}>
          <Text style={styles.searchIcon}>🔍</Text>
          <TextInput
            placeholder="Search topics or mentors..."
            placeholderTextColor="rgba(255,255,255,0.45)"
            style={styles.searchInput}
            value={searchQuery}
            onChangeText={(q) => { setSearchQuery(q); setCurrentPage(0); }}
            returnKeyType="search"
            autoCapitalize="none"
          />
        </View>
        <TouchableOpacity style={[styles.matchButton, isMatchMode && styles.matchButtonActive]} onPress={toggleMatchMode} disabled={matchLoading}>
          {matchLoading
            ? <ActivityIndicator size="small" color="#F8F6F2" />
            : <Text style={[styles.matchButtonText, isMatchMode && styles.matchButtonTextActive]}>
                {isMatchMode ? '✕  Show All Mentors' : '✦  Find Best Matches'}
              </Text>}
        </TouchableOpacity>
      </View>

      {loading ? (
        <ActivityIndicator size="large" color="#456B50" style={{ marginTop: 50 }} />
      ) : (
        <ScrollView ref={scrollRef} style={styles.listArea} contentContainerStyle={styles.listContent} showsVerticalScrollIndicator={false}>
          {pagedMentors.map((mentor) => (
            <View key={mentor.id} style={styles.card}>
              <View style={styles.cardTopRow}>
                <View style={[styles.avatar, { backgroundColor: mentor.avatarBg }]}>
                  <Text style={[styles.avatarText, { color: mentor.avatarText }]}>{mentor.initials}</Text>
                </View>
                <View style={styles.cardInfo}>
                  <Text style={styles.cardName}>{mentor.name}</Text>
                  <Text style={styles.cardRole}>{mentor.role}</Text>
                </View>
                <View style={styles.badgeColumn}>
                  {isMatchMode && (
                    <View style={styles.matchBadge}>
                      <Text style={styles.matchBadgeText}>✦ Match</Text>
                    </View>
                  )}
                  <View style={[styles.statusBadge, mentor.available ? styles.availableBadge : styles.fullBadge]}>
                    <Text style={[styles.statusBadgeText, mentor.available ? styles.availableBadgeText : styles.fullBadgeText]}>
                      {mentor.available ? 'Available' : 'Full'}
                    </Text>
                  </View>
                </View>
              </View>
              <View style={styles.tagsRow}>
                {mentor.tags.map((tag, idx) => (
                  <View key={idx} style={styles.tag}><Text style={styles.tagText}>{tag}</Text></View>
                ))}
              </View>
              <View style={styles.divider} />
              <View style={styles.cardBottomRow}>
                <View style={styles.ratingRow}>
                  <Text style={styles.stars}>★★★★★</Text>
                  <Text style={styles.ratingText}>{mentor.rating} ({mentor.reviews})</Text>
                </View>
                <View style={{ flexDirection: 'row', gap: 8 }}>
                  <TouchableOpacity
                    style={[styles.followButton, mentor.following && styles.followButtonActive]}
                    onPress={() => handleFollow(mentor.id)}
                    disabled={mentor.followLoading}
                  >
                    {mentor.followLoading
                      ? <ActivityIndicator size="small" color="#456B50" />
                      : <Text style={[styles.followButtonText, mentor.following && styles.followButtonTextActive]}>
                          {mentor.following ? '✓' : '+ Follow'}
                        </Text>}
                  </TouchableOpacity>
                  <TouchableOpacity style={styles.viewButton} onPress={() => openMentorProfile(mentor)}>
                    <Text style={styles.viewButtonText}>View</Text>
                  </TouchableOpacity>
                </View>
              </View>
            </View>
          ))}

          {totalPages > 1 && (
            <View style={styles.paginationRow}>
              {Array.from({ length: totalPages }, (_, i) => (
                <TouchableOpacity
                  key={i}
                  style={[styles.pageButton, currentPage === i && styles.pageButtonActive]}
                  onPress={() => goToPage(i)}
                >
                  <Text style={[styles.pageButtonText, currentPage === i && styles.pageButtonTextActive]}>
                    {i + 1}
                  </Text>
                </TouchableOpacity>
              ))}
            </View>
          )}
        </ScrollView>
      )}
    </View>
  );
}

type MenteeCard = {
  id: string;
  initials: string;
  firstName: string;
  major: string;
  goals: string;
  careerInterest: string;
  interests: string[];
  skills: string[];
  meetingFreqPref: string;
};

const AVATAR_COLORS_LIST = [
  { bg: '#D6E8DC', text: '#2F563C' },
  { bg: '#DFD9C9', text: '#66582F' },
  { bg: '#CCD6E5', text: '#4A5D7A' },
  { bg: '#E2D1E6', text: '#6D3F72' },
  { bg: '#F1E1BB', text: '#8A5D12' },
  { bg: '#D8E5F1', text: '#315A7A' },
];

const formatTime = (isoString: string) => {
  const date = new Date(isoString);
  const diffMs = Date.now() - date.getTime();
  const diffHours = Math.floor(diffMs / (1000 * 60 * 60));
  const diffDays = Math.floor(diffHours / 24);
  if (diffDays > 0) return `${diffDays} day${diffDays > 1 ? 's' : ''} ago`;
  if (diffHours > 0) return `${diffHours} hour${diffHours > 1 ? 's' : ''} ago`;
  return 'Just now';
};

function MentorRequestsContent() {
  const [view, setView] = useState<'requests' | 'discover' | 'settings'>('requests');
  const [incomingRequests, setIncomingRequests] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [actionLoading, setActionLoading] = useState<Record<string, boolean>>({});
  const [discoverMentors, setDiscoverMentors] = useState<MentorCard[]>([]);
  const [discoverLoading, setDiscoverLoading] = useState(false);
  const [discoverLoaded, setDiscoverLoaded] = useState(false);
  const [capacityValue, setCapacityValue] = useState('');
  const [capacitySaving, setCapacitySaving] = useState(false);
  const [capacityLoaded, setCapacityLoaded] = useState(false);

  useEffect(() => {
    apiClient.get('/mentorship-requests/received')
      .then((res) => {
        const pending = (res.data.content ?? res.data).filter((r: any) => r.status === 'PENDING');
        setIncomingRequests(pending);
      })
      .catch((err) => console.error('Requests fetch error:', err))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    if (view === 'discover' && !discoverLoaded) {
      loadDiscover();
    }
    if (view === 'settings' && !capacityLoaded) {
      apiClient.get('/users/me').then((res) => {
        const cap = res.data?.maxMenteeCapacity;
        if (cap != null) setCapacityValue(String(cap));
        setCapacityLoaded(true);
      }).catch(() => setCapacityLoaded(true));
    }
  }, [view]);

  const loadDiscover = async () => {
    setDiscoverLoading(true);
    try {
      const [mentorsRes, myId] = await Promise.all([
        apiClient.get('/users/mentors'),
        SecureStore.getItemAsync('userId'),
      ]);
      const data: any[] = mentorsRes.data.content ?? mentorsRes.data;
      const mapped = data.map((m) => mapMentor(m, false));
      if (myId) {
        try {
          const followRes = await apiClient.get(`/users/${myId}/following?size=100`);
          const followingIds = new Set(
            (followRes.data.content ?? followRes.data).map((u: any) => String(u.id))
          );
          setDiscoverMentors(mapped.map((m) => ({ ...m, following: followingIds.has(m.id) })));
        } catch {
          setDiscoverMentors(mapped);
        }
      } else {
        setDiscoverMentors(mapped);
      }
      setDiscoverLoaded(true);
    } catch {
      // ignore
    } finally {
      setDiscoverLoading(false);
    }
  };

  const saveCapacity = async () => {
    const num = parseInt(capacityValue, 10);
    if (isNaN(num) || num < 1) {
      Alert.alert('Invalid', 'Please enter a number ≥ 1.');
      return;
    }
    setCapacitySaving(true);
    try {
      await apiClient.patch('/users/me/mentor', { maxMenteeCapacity: num });
      Alert.alert('Saved', `Max mentee capacity set to ${num}.`);
    } catch (err: any) {
      Alert.alert('Error', err?.response?.data?.message || 'Could not save capacity.');
    } finally {
      setCapacitySaving(false);
    }
  };

  const handleRequest = async (requestId: string, action: 'accept' | 'reject') => {
    if (actionLoading[requestId]) return;
    if (action === 'accept') {
      Alert.prompt(
        'Accept Request',
        'Enter mentorship duration (1, 3, or 6 months):',
        async (input) => {
          const duration = parseInt(input ?? '3', 10);
          if (![1, 3, 6].includes(duration)) {
            Alert.alert('Invalid', 'Please enter 1, 3, or 6.');
            return;
          }
          setActionLoading((prev) => ({ ...prev, [requestId]: true }));
          try {
            await apiClient.put(`/mentorship-requests/${requestId}/accept`, { duration });
            setIncomingRequests((prev) => prev.filter((r) => String(r.id) !== requestId));
          } catch (err: any) {
            Alert.alert('Error', err?.response?.data?.message || 'Could not accept request.');
          } finally {
            setActionLoading((prev) => ({ ...prev, [requestId]: false }));
          }
        },
        'plain-text',
        '3'
      );
    } else {
      Alert.alert('Reject Request', 'Are you sure?', [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Reject',
          style: 'destructive',
          onPress: async () => {
            setActionLoading((prev) => ({ ...prev, [requestId]: true }));
            try {
              await apiClient.put(`/mentorship-requests/${requestId}/reject`);
              setIncomingRequests((prev) => prev.filter((r) => String(r.id) !== requestId));
            } catch (err: any) {
              Alert.alert('Error', err?.response?.data?.message || 'Could not reject request.');
            } finally {
              setActionLoading((prev) => ({ ...prev, [requestId]: false }));
            }
          },
        },
      ]);
    }
  };

  const handleDiscoverFollow = async (mentorId: string) => {
    const mentor = discoverMentors.find((m) => m.id === mentorId);
    if (!mentor || mentor.followLoading) return;
    setDiscoverMentors((prev) => prev.map((m) => m.id === mentorId ? { ...m, followLoading: true } : m));
    try {
      if (mentor.following) {
        await apiClient.delete(`/users/${mentorId}/follow`);
        setDiscoverMentors((prev) => prev.map((m) => m.id === mentorId ? { ...m, following: false, followLoading: false } : m));
      } else {
        await apiClient.post(`/users/${mentorId}/follow`, {});
        setDiscoverMentors((prev) => prev.map((m) => m.id === mentorId ? { ...m, following: true, followLoading: false } : m));
      }
    } catch (err: any) {
      setDiscoverMentors((prev) => prev.map((m) => m.id === mentorId ? { ...m, followLoading: false } : m));
      const status = err?.response?.status ? ` (${err.response.status})` : '';
      const msg = err?.response?.data?.message || err?.response?.data?.error || err?.message || 'Could not update follow status.';
      Alert.alert('Follow Error' + status, msg);
    }
  };

  const openCandidateProfile = (item: any) => {
    const colors = AVATAR_COLORS_LIST[item.menteeId % AVATAR_COLORS_LIST.length];
    router.push({
      pathname: '/request-candidate-profile',
      params: {
        requestId: String(item.id),
        menteeId: String(item.menteeId),
        menteeFirstName: item.menteeFirstName,
        hiddenInitial: item.menteeFirstName?.substring(0, 2).toUpperCase(),
        avatarBg: colors.bg,
        avatarText: colors.text,
        time: formatTime(item.createdAt),
        message: item.message || '',
      },
    });
  };

  return (
    <View style={styles.container}>
      <View style={styles.fixedHeader}>
        <View style={styles.topCircle} />
        <Text style={styles.title}>
          {view === 'requests' ? 'Mentee\n' : 'Discover\n'}
          <Text style={styles.titleItalic}>{view === 'requests' ? 'Requests.' : 'People.'}</Text>
        </Text>
        <View style={{ flexDirection: 'row', gap: 10, marginBottom: 4 }}>
          <TouchableOpacity
            style={[styles.matchButton, { flex: 1 }, view === 'requests' && styles.matchButtonActive]}
            onPress={() => setView('requests')}
          >
            <Text style={[styles.matchButtonText, view === 'requests' && styles.matchButtonTextActive]}>📋 Requests</Text>
          </TouchableOpacity>
          <TouchableOpacity
            style={[styles.matchButton, { flex: 1 }, view === 'discover' && styles.matchButtonActive]}
            onPress={() => setView('discover')}
          >
            <Text style={[styles.matchButtonText, view === 'discover' && styles.matchButtonTextActive]}>🔍 Discover</Text>
          </TouchableOpacity>
          <TouchableOpacity
            style={[styles.matchButton, { flex: 1 }, view === 'settings' && styles.matchButtonActive]}
            onPress={() => setView('settings')}
          >
            <Text style={[styles.matchButtonText, view === 'settings' && styles.matchButtonTextActive]}>⚙️ Settings</Text>
          </TouchableOpacity>
        </View>
      </View>

      {view === 'requests' ? (
        loading ? (
          <ActivityIndicator size="large" color="#456B50" style={{ marginTop: 50 }} />
        ) : (
          <ScrollView style={styles.listArea} contentContainerStyle={styles.listContent} showsVerticalScrollIndicator={false}>
            {incomingRequests.length === 0 && (
              <View style={{ paddingVertical: 40, alignItems: 'center' }}>
                <Text style={{ color: '#9A8F82', fontSize: 15 }}>No pending requests.</Text>
              </View>
            )}
            {incomingRequests.map((item, idx) => {
              const colors = AVATAR_COLORS_LIST[idx % AVATAR_COLORS_LIST.length];
              return (
                <View key={item.id} style={styles.card}>
                  <View style={styles.cardTopRow}>
                    <View style={[styles.avatar, { backgroundColor: colors.bg }]}>
                      <Text style={[styles.avatarText, { color: colors.text }]}>
                        {item.menteeFirstName?.substring(0, 2).toUpperCase()}
                      </Text>
                    </View>
                    <View style={styles.cardInfo}>
                      <Text style={styles.cardName}>{item.menteeFirstName}</Text>
                      <Text style={styles.cardRole}>{formatTime(item.createdAt)}</Text>
                    </View>
                    <View style={[styles.statusBadge, { backgroundColor: '#F1E1BB' }]}>
                      <Text style={[styles.statusBadgeText, { color: '#8A5D12' }]}>Pending</Text>
                    </View>
                  </View>
                  {!!item.message && (
                    <>
                      <View style={styles.divider} />
                      <Text style={{ color: '#7E7368', fontSize: 13, lineHeight: 18 }} numberOfLines={2}>
                        {item.message}
                      </Text>
                    </>
                  )}
                  <TouchableOpacity
                    style={[styles.viewButton, { marginTop: 14 }]}
                    onPress={() => openCandidateProfile(item)}
                  >
                    <Text style={styles.viewButtonText}>View Profile</Text>
                  </TouchableOpacity>
                  <View style={{ flexDirection: 'row', gap: 10, marginTop: 10 }}>
                    <TouchableOpacity
                      style={{ flex: 1, backgroundColor: '#456B50', borderRadius: 12, paddingVertical: 12, alignItems: 'center' }}
                      onPress={() => handleRequest(String(item.id), 'accept')}
                      disabled={!!actionLoading[String(item.id)]}
                    >
                      <Text style={{ color: '#fff', fontWeight: '700', fontSize: 14 }}>
                        {actionLoading[String(item.id)] ? '...' : 'Accept'}
                      </Text>
                    </TouchableOpacity>
                    <TouchableOpacity
                      style={{ flex: 1, backgroundColor: '#F5D9D6', borderRadius: 12, paddingVertical: 12, alignItems: 'center' }}
                      onPress={() => handleRequest(String(item.id), 'reject')}
                      disabled={!!actionLoading[String(item.id)]}
                    >
                      <Text style={{ color: '#9B3A35', fontWeight: '700', fontSize: 14 }}>Reject</Text>
                    </TouchableOpacity>
                  </View>
                </View>
              );
            })}
          </ScrollView>
        )
      ) : view === 'settings' ? (
        <ScrollView style={styles.listArea} contentContainerStyle={styles.listContent} showsVerticalScrollIndicator={false}>
          <View style={styles.card}>
            <Text style={{ fontSize: 16, fontWeight: '700', color: '#1D1D38', marginBottom: 8 }}>Max Mentee Capacity</Text>
            <Text style={{ fontSize: 14, color: '#7E7368', marginBottom: 14, lineHeight: 20 }}>
              Set the maximum number of mentees you can mentor at the same time.
            </Text>
            <TextInput
              style={{ borderWidth: 1.5, borderColor: '#C8D9CA', borderRadius: 14, padding: 14, fontSize: 16, color: '#2B2B2B', backgroundColor: '#FCFBF8', marginBottom: 14 }}
              value={capacityValue}
              onChangeText={setCapacityValue}
              placeholder="e.g. 5"
              placeholderTextColor="#B0A89E"
              keyboardType="number-pad"
            />
            <TouchableOpacity
              style={{ backgroundColor: '#456B50', borderRadius: 14, paddingVertical: 14, alignItems: 'center', opacity: capacitySaving ? 0.6 : 1 }}
              onPress={saveCapacity}
              disabled={capacitySaving}
            >
              <Text style={{ color: '#fff', fontWeight: '700', fontSize: 15 }}>
                {capacitySaving ? 'Saving...' : 'Save Capacity'}
              </Text>
            </TouchableOpacity>
          </View>
        </ScrollView>
      ) : (
        discoverLoading ? (
          <ActivityIndicator size="large" color="#456B50" style={{ marginTop: 50 }} />
        ) : (
          <ScrollView style={styles.listArea} contentContainerStyle={styles.listContent} showsVerticalScrollIndicator={false}>
            {discoverMentors.map((mentor) => (
              <View key={mentor.id} style={styles.card}>
                <View style={styles.cardTopRow}>
                  <View style={[styles.avatar, { backgroundColor: mentor.avatarBg }]}>
                    <Text style={[styles.avatarText, { color: mentor.avatarText }]}>{mentor.initials}</Text>
                  </View>
                  <View style={styles.cardInfo}>
                    <Text style={styles.cardName}>{mentor.name}</Text>
                    <Text style={styles.cardRole}>{mentor.role}</Text>
                  </View>
                  <View style={[styles.statusBadge, mentor.available ? styles.availableBadge : styles.fullBadge]}>
                    <Text style={[styles.statusBadgeText, mentor.available ? styles.availableBadgeText : styles.fullBadgeText]}>
                      {mentor.available ? 'Available' : 'Full'}
                    </Text>
                  </View>
                </View>
                <View style={styles.tagsRow}>
                  {mentor.tags.map((tag, idx) => (
                    <View key={idx} style={styles.tag}><Text style={styles.tagText}>{tag}</Text></View>
                  ))}
                </View>
                <View style={styles.divider} />
                <View style={styles.cardBottomRow}>
                  <View style={styles.ratingRow}>
                    <Text style={styles.stars}>★★★★★</Text>
                    <Text style={styles.ratingText}>{mentor.rating}</Text>
                  </View>
                  <TouchableOpacity
                    style={[styles.followButton, mentor.following && styles.followButtonActive]}
                    onPress={() => handleDiscoverFollow(mentor.id)}
                    disabled={mentor.followLoading}
                  >
                    {mentor.followLoading
                      ? <ActivityIndicator size="small" color="#456B50" />
                      : <Text style={[styles.followButtonText, mentor.following && styles.followButtonTextActive]}>
                          {mentor.following ? '✓ Following' : '+ Follow'}
                        </Text>}
                  </TouchableOpacity>
                </View>
              </View>
            ))}
          </ScrollView>
        )
      )}
    </View>
  );
}

// Stilleri (styles) dosyanın sonuna eklemeyi unutma...

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#ECE8E1',
  },

  fixedHeader: {
    backgroundColor: '#456B50',
    paddingTop: 54,
    paddingHorizontal: 24,
    paddingBottom: 18,
    overflow: 'hidden',
  },

  fixedHeaderMentor: {
    backgroundColor: '#456B50',
    paddingTop: 54,
    paddingHorizontal: 24,
    paddingBottom: 18,
    overflow: 'hidden',
  },

  topCircle: {
    position: 'absolute',
    width: 310,
    height: 310,
    borderRadius: 155,
    backgroundColor: 'rgba(255,255,255,0.05)',
    top: -20,
    right: -70,
  },

  mentorTopCircle: {
    position: 'absolute',
    width: 320,
    height: 320,
    borderRadius: 160,
    backgroundColor: 'rgba(255,255,255,0.05)',
    top: -30,
    right: -70,
  },

  mentorLeftCircle: {
    position: 'absolute',
    width: 230,
    height: 230,
    borderRadius: 115,
    backgroundColor: 'rgba(255,255,255,0.04)',
    top: 40,
    left: -60,
  },

  statusRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },

  statusText: {
    color: '#FFFFFF',
    fontSize: 16,
    fontWeight: '700',
  },

  statusIcons: {
    color: '#FFFFFF',
    fontSize: 18,
    fontWeight: '700',
  },

  title: {
    color: '#F7F4EE',
    fontSize: 34,
    lineHeight: 38,
    fontWeight: '700',
    marginTop: 20,
    marginBottom: 20,
  },

  titleItalic: {
    fontStyle: 'italic',
    fontWeight: '700',
  },

  searchBox: {
    height: 66,
    borderRadius: 22,
    borderWidth: 1.5,
    borderColor: 'rgba(255,255,255,0.16)',
    backgroundColor: 'rgba(255,255,255,0.06)',
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 18,
    marginBottom: 18,
  },

  searchBoxMentor: {
    height: 66,
    borderRadius: 22,
    borderWidth: 1.5,
    borderColor: 'rgba(255,255,255,0.16)',
    backgroundColor: 'rgba(255,255,255,0.06)',
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 18,
    marginBottom: 18,
  },

  searchIcon: {
    fontSize: 18,
    marginRight: 10,
  },

  searchInput: {
    flex: 1,
    color: '#FFFFFF',
    fontSize: 16,
    fontWeight: '500',
  },

  filterRow: {
    paddingRight: 20,
    gap: 12,
  },

  filterButton: {
    paddingHorizontal: 22,
    paddingVertical: 12,
    borderRadius: 22,
    borderWidth: 1.5,
    borderColor: 'rgba(255,255,255,0.16)',
    backgroundColor: 'rgba(255,255,255,0.06)',
  },

  filterButtonActive: {
    backgroundColor: '#F8F6F2',
    borderColor: '#F8F6F2',
  },

  filterText: {
    color: 'rgba(255,255,255,0.65)',
    fontSize: 14,
    fontWeight: '600',
  },

  filterTextActive: {
    color: '#2F563C',
  },

  listArea: {
    flex: 1,
    backgroundColor: '#ECE8E1',
  },

  listContent: {
    padding: 20,
    paddingBottom: 40,
  },

  mentorListContent: {
    paddingTop: 16,
    paddingHorizontal: 20,
    paddingBottom: 40,
  },

  mentorFilterRow: {
    paddingRight: 20,
    gap: 12,
    marginBottom: 18,
  },

  pillButton: {
    paddingHorizontal: 24,
    paddingVertical: 12,
    borderRadius: 24,
    backgroundColor: '#FBFAF7',
    borderWidth: 1.5,
    borderColor: '#E5DDD1',
  },

  pillButtonActive: {
    backgroundColor: '#D7E8DA',
    borderColor: '#3F7653',
  },

  pillText: {
    color: '#9A8F82',
    fontSize: 14,
    fontWeight: '600',
  },

  pillTextActive: {
    color: '#2F563C',
  },

  sectionTitle: {
    color: '#8B8176',
    fontSize: 13,
    fontWeight: '700',
    letterSpacing: 2,
    marginBottom: 16,
  },

  featuredCard: {
    backgroundColor: '#3F7653',
    borderRadius: 30,
    padding: 22,
    overflow: 'hidden',
    marginBottom: 26,
  },

  featuredCircle: {
    position: 'absolute',
    width: 170,
    height: 170,
    borderRadius: 85,
    backgroundColor: 'rgba(255,255,255,0.06)',
    right: -10,
    top: 20,
  },

  editorPill: {
    alignSelf: 'flex-start',
    backgroundColor: 'rgba(255,255,255,0.10)',
    borderWidth: 1.5,
    borderColor: 'rgba(255,255,255,0.14)',
    paddingHorizontal: 16,
    paddingVertical: 10,
    borderRadius: 18,
    marginBottom: 18,
  },

  editorPillText: {
    color: '#F7F4EE',
    fontSize: 13,
    fontWeight: '700',
  },

  featuredTitle: {
    color: '#F7F4EE',
    fontSize: 24,
    lineHeight: 34,
    fontWeight: '700',
    marginBottom: 10,
  },

  featuredMeta: {
    color: 'rgba(247,244,238,0.8)',
    fontSize: 14,
    fontWeight: '500',
  },

  learningCard: {
    backgroundColor: '#F8F6F2',
    borderRadius: 26,
    padding: 18,
    marginBottom: 16,
    flexDirection: 'row',
    alignItems: 'center',
  },

  learningIconBox: {
    width: 86,
    height: 86,
    borderRadius: 22,
    justifyContent: 'center',
    alignItems: 'center',
    marginRight: 16,
  },

  learningIcon: {
    fontSize: 34,
  },

  learningTextArea: {
    flex: 1,
  },

  learningTitle: {
    color: '#23372B',
    fontSize: 18,
    fontWeight: '700',
    lineHeight: 26,
    marginBottom: 14,
  },

  learningBottomRow: {
    flexDirection: 'row',
    alignItems: 'center',
  },

  progressTrack: {
    flex: 1,
    height: 8,
    borderRadius: 999,
    backgroundColor: '#E2DACE',
    overflow: 'hidden',
    marginRight: 12,
  },

  progressFill: {
    height: '100%',
    borderRadius: 999,
    backgroundColor: '#5B9565',
  },

  learningTime: {
    color: '#9A8F82',
    fontSize: 14,
    fontWeight: '500',
  },

  recommendCard: {
    backgroundColor: '#F8F6F2',
    borderRadius: 26,
    padding: 18,
    marginBottom: 18,
    flexDirection: 'row',
    alignItems: 'center',
  },

  recommendIconBox: {
    width: 86,
    height: 86,
    borderRadius: 22,
    justifyContent: 'center',
    alignItems: 'center',
    marginRight: 16,
  },

  recommendIcon: {
    fontSize: 34,
  },

  recommendTextArea: {
    flex: 1,
  },

  recommendTitle: {
    color: '#23372B',
    fontSize: 18,
    fontWeight: '700',
    lineHeight: 26,
    marginBottom: 10,
  },

  metaRow: {
    flexDirection: 'row',
    alignItems: 'center',
    flexWrap: 'wrap',
  },

  metaBadge: {
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderRadius: 16,
    marginRight: 10,
  },

  metaBadgeGreen: {
    backgroundColor: '#D7E8DA',
  },

  metaBadgeRose: {
    backgroundColor: '#EAD7D3',
  },

  metaBadgeGold: {
    backgroundColor: '#F1E1BB',
  },

  metaBadgeBlue: {
    backgroundColor: '#D8E5F1',
  },

  metaBadgeText: {
    fontSize: 13,
    fontWeight: '700',
  },

  metaBadgeTextGreen: {
    color: '#2F563C',
  },

  metaBadgeTextRose: {
    color: '#8A4B2E',
  },

  metaBadgeTextGold: {
    color: '#8A5D12',
  },

  metaBadgeTextBlue: {
    color: '#315A7A',
  },

  metaText: {
    color: '#A49A8E',
    fontSize: 14,
    fontWeight: '500',
  },

  chevron: {
    fontSize: 28,
    color: '#B9B0A5',
    marginLeft: 10,
    fontWeight: '400',
  },

  card: {
    backgroundColor: '#F8F6F2',
    borderRadius: 28,
    padding: 18,
    marginBottom: 18,
    shadowColor: '#000',
    shadowOpacity: 0.04,
    shadowRadius: 10,
    shadowOffset: { width: 0, height: 3 },
    elevation: 2,
  },

  cardTopRow: {
    flexDirection: 'row',
    alignItems: 'center',
  },

  avatar: {
    width: 64,
    height: 64,
    borderRadius: 32,
    justifyContent: 'center',
    alignItems: 'center',
    marginRight: 14,
  },

  avatarText: {
    fontSize: 20,
    fontWeight: '700',
  },

  cardInfo: {
    flex: 1,
  },

  cardName: {
    color: '#21372A',
    fontSize: 18,
    fontWeight: '700',
    marginBottom: 4,
  },

  cardRole: {
    color: '#9A8F82',
    fontSize: 13,
    fontWeight: '500',
  },

  statusBadge: {
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderRadius: 16,
  },

  availableBadge: {
    backgroundColor: '#D7E8DA',
  },

  fullBadge: {
    backgroundColor: '#F0D6D7',
  },

  statusBadgeText: {
    fontSize: 12,
    fontWeight: '700',
  },

  availableBadgeText: {
    color: '#2F563C',
  },

  fullBadgeText: {
    color: '#7E2F2F',
  },

  tagsRow: {
    flexDirection: 'row',
    gap: 10,
    marginTop: 16,
    marginBottom: 16,
  },

  tag: {
    backgroundColor: '#D8E5F1',
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderRadius: 14,
  },

  tagText: {
    color: '#315A7A',
    fontSize: 12,
    fontWeight: '600',
  },

  divider: {
    height: 1,
    backgroundColor: '#E2DACE',
    marginBottom: 16,
  },

  cardBottomRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },

  ratingRow: {
    flexDirection: 'row',
    alignItems: 'center',
  },

  stars: {
    color: '#D09541',
    fontSize: 18,
    marginRight: 8,
  },

  ratingText: {
    color: '#7E7368',
    fontSize: 13,
    fontWeight: '500',
  },

  viewButton: {
    backgroundColor: '#D7E8DA',
    paddingHorizontal: 20,
    paddingVertical: 12,
    borderRadius: 18,
  },

  viewButtonText: {
    color: '#2F563C',
    fontSize: 14,
    fontWeight: '700',
  },

  paginationRow: {
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center',
    gap: 10,
    marginTop: 8,
    marginBottom: 12,
  },

  pageButton: {
    width: 40,
    height: 40,
    borderRadius: 20,
    backgroundColor: '#F8F6F2',
    borderWidth: 1.5,
    borderColor: '#D8CEC0',
    justifyContent: 'center',
    alignItems: 'center',
  },

  pageButtonActive: {
    backgroundColor: '#456B50',
    borderColor: '#456B50',
  },

  pageButtonText: {
    color: '#7E7368',
    fontSize: 14,
    fontWeight: '700',
  },

  pageButtonTextActive: {
    color: '#F8F6F2',
  },
  matchButton: {
    height: 50,
    borderRadius: 18,
    borderWidth: 1.5,
    borderColor: 'rgba(255,255,255,0.30)',
    backgroundColor: 'rgba(255,255,255,0.10)',
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: 18,
  },
  matchButtonActive: {
    backgroundColor: 'rgba(255,255,255,0.20)',
    borderColor: 'rgba(255,255,255,0.50)',
  },
  matchButtonText: {
    color: 'rgba(255,255,255,0.85)',
    fontSize: 15,
    fontWeight: '700',
  },
  matchButtonTextActive: {
    color: '#FFFFFF',
  },
  badgeColumn: {
    alignItems: 'flex-end',
    gap: 6,
  },
  matchBadge: {
    backgroundColor: '#D8E5F1',
    paddingHorizontal: 10,
    paddingVertical: 5,
    borderRadius: 12,
  },
  matchBadgeText: {
    color: '#315A7A',
    fontSize: 11,
    fontWeight: '700',
  },
  followButton: {
    backgroundColor: '#F0EDE8',
    paddingHorizontal: 16,
    paddingVertical: 10,
    borderRadius: 16,
    borderWidth: 1.5,
    borderColor: '#D8CEC0',
    minWidth: 80,
    alignItems: 'center',
  },
  followButtonActive: {
    backgroundColor: '#D7E8DA',
    borderColor: '#456B50',
  },
  followButtonText: {
    color: '#7E7368',
    fontSize: 13,
    fontWeight: '700',
  },
  followButtonTextActive: {
    color: '#2F563C',
  },
});