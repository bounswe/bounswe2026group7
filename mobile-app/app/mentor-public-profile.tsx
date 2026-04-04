import React, { useMemo, useState } from 'react';
import { router, useLocalSearchParams } from 'expo-router';
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  ScrollView,
} from 'react-native';

function parseString(value: string | string[] | undefined) {
  return Array.isArray(value) ? value[0] : value ?? '';
}

function parseJsonArray(value: string | string[] | undefined): string[] {
  try {
    const raw = parseString(value);
    return raw ? JSON.parse(raw) : [];
  } catch {
    return [];
  }
}

export default function MentorPublicProfileScreen() {
  const params = useLocalSearchParams();

  const initials = parseString(params.initials);
  const avatarBg = parseString(params.avatarBg) || '#D6E8DC';
  const avatarText = parseString(params.avatarText) || '#2F563C';
  const name = parseString(params.name);
  const role = parseString(params.role);
  const available = parseString(params.available) === 'true';
  const rating = parseString(params.rating);
  const reviews = parseString(params.reviews);
  const about = parseString(params.about);

  const tags = parseJsonArray(params.tags);
  const mentoringGoals = parseJsonArray(params.mentoringGoals);
  const preferredMenteeCriteria = parseJsonArray(params.preferredMenteeCriteria);
  const availability = parseJsonArray(params.availability);

  const [requestStatus, setRequestStatus] = useState<'idle' | 'waiting'>('idle');

  const topButtonText = useMemo(() => {
    return requestStatus === 'waiting' ? 'Waiting for Response' : 'Send Request';
  }, [requestStatus]);

  return (
    <View style={styles.container}>
      <View style={styles.stickyTopArea}>
        <View style={styles.header}>
          <View style={styles.topCircle} />

          <View style={styles.statusRow}>
            <Text style={styles.statusText}>9:41</Text>
            <Text style={styles.statusIcons}>▲ ▮</Text>
          </View>

          <View style={styles.topActionRow}>
            <TouchableOpacity style={styles.backButton} onPress={() => router.back()}>
              <Text style={styles.backButtonText}>‹ Back</Text>
            </TouchableOpacity>

            <TouchableOpacity
              style={[
                styles.requestButton,
                requestStatus === 'waiting' && styles.requestButtonWaiting,
              ]}
              disabled={requestStatus === 'waiting'}
              onPress={() => setRequestStatus('waiting')}
            >
              <Text
                style={[
                  styles.requestButtonText,
                  requestStatus === 'waiting' && styles.requestButtonTextWaiting,
                ]}
              >
                {topButtonText}
              </Text>
            </TouchableOpacity>
          </View>

          <Text style={styles.title}>Mentor Profile</Text>
          <Text style={styles.subtitle}>Review before sending a mentorship request</Text>
        </View>
      </View>

      <ScrollView
        style={styles.scrollArea}
        contentContainerStyle={styles.scrollContent}
        showsVerticalScrollIndicator={false}
      >
        <View style={styles.profileHero}>
          <View style={[styles.avatar, { backgroundColor: avatarBg }]}>
            <Text style={[styles.avatarText, { color: avatarText }]}>{initials}</Text>
          </View>

          <Text style={styles.name}>{name}</Text>
          <Text style={styles.roleText}>{role}</Text>

          <View
            style={[
              styles.availabilityBadge,
              available ? styles.availableBadge : styles.fullBadge,
            ]}
          >
            <Text
              style={[
                styles.availabilityBadgeText,
                available ? styles.availableBadgeText : styles.fullBadgeText,
              ]}
            >
              {available ? 'Available for Requests' : 'Currently Full'}
            </Text>
          </View>
        </View>

        <View style={styles.statsCard}>
          <View style={styles.statItem}>
            <Text style={styles.statNumber}>{rating}</Text>
            <Text style={styles.statLabel}>Rating</Text>
          </View>

          <View style={styles.statDivider} />

          <View style={styles.statItem}>
            <Text style={styles.statNumber}>{reviews}</Text>
            <Text style={styles.statLabel}>Reviews</Text>
          </View>

          <View style={styles.statDivider} />

          <View style={styles.statItem}>
            <Text style={styles.statNumber}>{tags.length}</Text>
            <Text style={styles.statLabel}>Topics</Text>
          </View>
        </View>

        <View style={styles.card}>
          <Text style={styles.sectionLabel}>ABOUT</Text>
          <Text style={styles.bodyText}>{about}</Text>
        </View>

        <View style={styles.card}>
          <Text style={styles.sectionLabel}>EXPERTISE</Text>
          <View style={styles.tokensWrap}>
            {tags.map((item) => (
              <View key={item} style={styles.tokenChip}>
                <Text style={styles.tokenText}>{item}</Text>
              </View>
            ))}
          </View>

          <Text style={styles.sectionLabel}>MENTORING GOALS</Text>
          <View style={styles.tokensWrap}>
            {mentoringGoals.map((item) => (
              <View key={item} style={styles.tokenChip}>
                <Text style={styles.tokenText}>{item}</Text>
              </View>
            ))}
          </View>

          <Text style={styles.sectionLabel}>PREFERRED MENTEE CRITERIA</Text>
          <View style={styles.tokensWrap}>
            {preferredMenteeCriteria.map((item) => (
              <View key={item} style={styles.tokenChip}>
                <Text style={styles.tokenText}>{item}</Text>
              </View>
            ))}
          </View>
        </View>

        <View style={styles.card}>
          <Text style={styles.sectionLabel}>AVAILABLE SLOTS</Text>
          {availability.map((slot) => (
            <View key={slot} style={styles.slotRow}>
              <Text style={styles.slotBullet}>•</Text>
              <Text style={styles.slotText}>{slot}</Text>
            </View>
          ))}
        </View>

        {requestStatus === 'waiting' ? (
          <View style={styles.waitingCard}>
            <Text style={styles.waitingTitle}>Request Sent</Text>
            <Text style={styles.waitingText}>
              Your mentorship request is now pending. Waiting for mentor response.
            </Text>
          </View>
        ) : null}

        <View style={styles.bottomSpacer} />
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#ECE8E1',
  },
  stickyTopArea: {
    backgroundColor: '#ECE8E1',
    zIndex: 10,
  },
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
  topActionRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginTop: 16,
    marginBottom: 12,
    gap: 12,
  },
  backButton: {
    backgroundColor: 'rgba(255,255,255,0.10)',
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.16)',
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderRadius: 16,
  },
  backButtonText: {
    color: '#F7F4EE',
    fontSize: 14,
    fontWeight: '700',
  },
  requestButton: {
    backgroundColor: '#F8F6F2',
    paddingHorizontal: 16,
    paddingVertical: 10,
    borderRadius: 16,
  },
  requestButtonWaiting: {
    backgroundColor: '#D7E8DA',
  },
  requestButtonText: {
    color: '#2F563C',
    fontSize: 14,
    fontWeight: '700',
  },
  requestButtonTextWaiting: {
    color: '#2F563C',
  },
  title: {
    color: '#F7F4EE',
    fontSize: 30,
    fontWeight: '700',
    marginBottom: 4,
  },
  subtitle: {
    color: 'rgba(247,244,238,0.72)',
    fontSize: 14,
    fontWeight: '500',
  },
  scrollArea: {
    flex: 1,
  },
  scrollContent: {
    paddingHorizontal: 24,
    paddingTop: 18,
    paddingBottom: 24,
  },
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
  avatarText: {
    fontSize: 28,
    fontWeight: '700',
  },
  name: {
    color: '#23372B',
    fontSize: 22,
    fontWeight: '700',
    marginBottom: 4,
    textAlign: 'center',
  },
  roleText: {
    color: '#8B8176',
    fontSize: 14,
    fontWeight: '500',
    marginBottom: 12,
    textAlign: 'center',
  },
  availabilityBadge: {
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
  availabilityBadgeText: {
    fontSize: 12,
    fontWeight: '700',
  },
  availableBadgeText: {
    color: '#2F563C',
  },
  fullBadgeText: {
    color: '#7E2F2F',
  },
  statsCard: {
    backgroundColor: '#F8F6F2',
    borderRadius: 26,
    flexDirection: 'row',
    marginBottom: 18,
    overflow: 'hidden',
  },
  statItem: {
    flex: 1,
    alignItems: 'center',
    paddingVertical: 20,
  },
  statNumber: {
    color: '#2F563C',
    fontSize: 22,
    fontWeight: '700',
    marginBottom: 4,
  },
  statLabel: {
    color: '#8B8176',
    fontSize: 13,
    fontWeight: '500',
  },
  statDivider: {
    width: 1,
    backgroundColor: '#DDD5CA',
  },
  card: {
    backgroundColor: '#F8F6F2',
    borderRadius: 26,
    padding: 20,
    marginBottom: 18,
  },
  sectionLabel: {
    color: '#8B8176',
    fontSize: 12,
    fontWeight: '700',
    letterSpacing: 1.2,
    marginBottom: 10,
    marginTop: 4,
  },
  bodyText: {
    color: '#4A4138',
    fontSize: 15,
    lineHeight: 22,
  },
  tokensWrap: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
    marginBottom: 10,
  },
  tokenChip: {
    paddingHorizontal: 14,
    paddingVertical: 9,
    borderRadius: 18,
    backgroundColor: '#EEF3EE',
    borderWidth: 1,
    borderColor: '#D7E8DA',
  },
  tokenText: {
    color: '#2F563C',
    fontSize: 13,
    fontWeight: '600',
  },
  slotRow: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 8,
  },
  slotBullet: {
    color: '#2F563C',
    fontSize: 18,
    marginRight: 8,
  },
  slotText: {
    color: '#4A4138',
    fontSize: 15,
  },
  waitingCard: {
    backgroundColor: '#D7E8DA',
    borderRadius: 22,
    padding: 18,
    marginBottom: 16,
  },
  waitingTitle: {
    color: '#2F563C',
    fontSize: 16,
    fontWeight: '700',
    marginBottom: 6,
  },
  waitingText: {
    color: '#2F563C',
    fontSize: 14,
    lineHeight: 20,
  },
  bottomSpacer: {
    height: 12,
  },
});