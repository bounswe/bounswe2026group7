import React, { useState } from 'react';
import { router, useLocalSearchParams } from 'expo-router';
import apiClient from '../api/client';
import { TextInput } from 'react-native';
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  ScrollView,
  Alert,
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

  const mentorId = parseString(params.mentorId);
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

  const [requestStatus, setRequestStatus] = useState<'idle' | 'sending' | 'waiting'>('idle');
  const [requestMessage, setRequestMessage] = useState('');

  const handleSendRequest = async () => {
    if (!mentorId) {
      Alert.alert('Error', 'Mentor information is missing.');
      return;
    }
    setRequestStatus('sending');
    try {
      await apiClient.post('/mentorship-requests', {
        mentorId: Number(mentorId),
        message: requestMessage.trim() || undefined,
      });
      setRequestStatus('waiting');
    } catch (error: any) {
      setRequestStatus('idle');
      const msg = error.response?.data?.message || 'Could not send request. Please try again.';
      Alert.alert('Request Failed', msg);
    }
  };

  return (
    <View style={styles.container}>
      <View style={styles.stickyTopArea}>
        <View style={styles.header}>
          <View style={styles.topCircle} />

          <View style={styles.statusRow}>
            <Text style={styles.statusText}>{new Date().toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit', hour12: false })}</Text>
            <Text style={styles.statusIcons}>▲ ▮</Text>
          </View>

          <View style={styles.topActionRow}>
            <TouchableOpacity style={styles.backButton} onPress={() => router.back()}>
              <Text style={styles.backButtonText}>‹ Back</Text>
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

        <View style={styles.composeCard}>
          <Text style={styles.composeLabel}>YOUR MESSAGE (optional)</Text>
          <TextInput
            style={styles.composeInput}
            placeholder="Introduce yourself and explain why you'd like this mentor..."
            placeholderTextColor="#B5ADA3"
            value={requestMessage}
            onChangeText={setRequestMessage}
            multiline
            maxLength={500}
            editable={requestStatus === 'idle'}
          />
          <Text style={styles.charCount}>{requestMessage.length}/500</Text>
          <TouchableOpacity
            style={[styles.sendButton, requestStatus !== 'idle' && styles.sendButtonDisabled]}
            onPress={handleSendRequest}
            disabled={requestStatus !== 'idle'}
          >
            <Text style={styles.sendButtonText}>
              {requestStatus === 'sending' ? 'Sending…' : requestStatus === 'waiting' ? 'Request Sent' : 'Send Request'}
            </Text>
          </TouchableOpacity>
        </View>

        {requestStatus === 'waiting' && (
          <View style={styles.waitingCard}>
            <Text style={styles.waitingTitle}>Request Sent</Text>
            <Text style={styles.waitingText}>
              Your mentorship request is now pending. Waiting for mentor response.
            </Text>
          </View>
        )}

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
  requestButtonCancel: {
    backgroundColor: 'rgba(255,255,255,0.15)',
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.25)',
  },
  composeCard: {
    backgroundColor: '#F8F6F2',
    borderRadius: 26,
    padding: 20,
    marginBottom: 18,
  },
  composeLabel: {
    color: '#8B8176',
    fontSize: 12,
    fontWeight: '700',
    letterSpacing: 1.2,
    marginBottom: 10,
  },
  composeInput: {
    borderWidth: 1.5,
    borderColor: '#D8CEC0',
    borderRadius: 18,
    backgroundColor: '#FCFBF8',
    paddingHorizontal: 16,
    paddingVertical: 12,
    fontSize: 15,
    color: '#4A4138',
    minHeight: 100,
    textAlignVertical: 'top',
    marginBottom: 8,
  },
  charCount: {
    color: '#B5ADA3',
    fontSize: 12,
    textAlign: 'right',
    marginBottom: 14,
  },
  sendButton: {
    backgroundColor: '#456B50',
    borderRadius: 18,
    paddingVertical: 16,
    alignItems: 'center',
  },
  sendButtonText: {
    color: '#F8F6F2',
    fontSize: 15,
    fontWeight: '700',
  },
  sendButtonDisabled: {
    backgroundColor: '#8BAF93',
  },
});