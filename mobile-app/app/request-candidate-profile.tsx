import React from 'react';
import { router, useLocalSearchParams } from 'expo-router';
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

export default function RequestCandidateProfileScreen() {
  const params = useLocalSearchParams();

  const firstName = parseString(params.firstName);
  const hiddenInitial = parseString(params.hiddenInitial);
  const avatarBg = parseString(params.avatarBg) || '#D6E8DC';
  const avatarText = parseString(params.avatarText) || '#2F563C';
  const time = parseString(params.time);
  const message = parseString(params.message);
  const department = parseString(params.department);
  const about = parseString(params.about);
  const background = parseString(params.background);
  const goals = parseJsonArray(params.goals);
  const interests = parseJsonArray(params.interests);

  const handleAccept = () => {
    Alert.alert('Accepted', `${firstName} mentorship request accepted.`);
    router.back();
  };

  const handleReject = () => {
    Alert.alert('Rejected', `${firstName} mentorship request rejected.`);
    router.back();
  };

  return (
    <View style={styles.container}>
      <View style={styles.topStickyArea}>
        <View style={styles.header}>
          <View style={styles.topCircle} />

          <View style={styles.statusRow}>
            <Text style={styles.statusText}>9:41</Text>
            <Text style={styles.statusIcons}>▲ ▮</Text>
          </View>

          <TouchableOpacity style={styles.backButton} onPress={() => router.back()}>
            <Text style={styles.backButtonText}>‹ Back</Text>
          </TouchableOpacity>

          <Text style={styles.title}>Candidate Profile</Text>
          <Text style={styles.subtitle}>Review before making a decision</Text>
        </View>

        <View style={styles.actionBar}>
          <TouchableOpacity style={styles.acceptButton} onPress={handleAccept}>
            <Text style={styles.acceptButtonText}>Accept</Text>
          </TouchableOpacity>

          <TouchableOpacity style={styles.rejectButton} onPress={handleReject}>
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

          <Text style={styles.name}>{firstName}</Text>
          <Text style={styles.roleText}>Candidate Mentee • {department}</Text>

          <View style={styles.metaBadge}>
            <Text style={styles.metaBadgeText}>{time}</Text>
          </View>
        </View>

        <View style={styles.card}>
          <Text style={styles.sectionLabel}>REQUEST MESSAGE</Text>
          <Text style={styles.bodyText}>{message}</Text>
        </View>

        <View style={styles.card}>
          <Text style={styles.sectionLabel}>ABOUT</Text>
          <Text style={styles.bodyText}>{about}</Text>

          <Text style={styles.sectionLabel}>BACKGROUND</Text>
          <Text style={styles.bodyText}>{background}</Text>
        </View>

        <View style={styles.card}>
          <Text style={styles.sectionLabel}>GOALS</Text>
          <View style={styles.tokensWrap}>
            {goals.map((item) => (
              <View key={item} style={styles.tokenChip}>
                <Text style={styles.tokenText}>{item}</Text>
              </View>
            ))}
          </View>

          <Text style={styles.sectionLabel}>INTERESTS</Text>
          <View style={styles.tokensWrap}>
            {interests.map((item) => (
              <View key={item} style={styles.tokenChip}>
                <Text style={styles.tokenText}>{item}</Text>
              </View>
            ))}
          </View>
        </View>

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
  topStickyArea: {
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
  backButtonText: {
    color: '#F7F4EE',
    fontSize: 14,
    fontWeight: '700',
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
  acceptButtonText: {
    color: '#2F563C',
    fontSize: 15,
    fontWeight: '700',
  },
  rejectButton: {
    flex: 1,
    backgroundColor: '#FDF0EF',
    borderRadius: 18,
    paddingVertical: 16,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: '#FAD4D4',
  },
  rejectButtonText: {
    color: '#D9534F',
    fontSize: 15,
    fontWeight: '700',
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
  metaBadge: {
    backgroundColor: '#D7E8DA',
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderRadius: 16,
  },
  metaBadgeText: {
    color: '#2F563C',
    fontSize: 12,
    fontWeight: '700',
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
    marginBottom: 12,
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
  bottomSpacer: {
    height: 18,
  },
});