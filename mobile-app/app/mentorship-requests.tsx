import React from 'react';
import { router } from 'expo-router';
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  ScrollView,
} from 'react-native';

export default function MentorshipRequestsScreen() {
  const incomingRequests = [
    {
      initials: 'ÖA',
      avatarBg: '#D6E8DC',
      avatarText: '#2F563C',
      name: 'Övgü Su Afşar',
      time: '2 hours ago',
      message: 'Looking for mentorship on my React Native project.',
    },
    {
      initials: 'BK',
      avatarBg: '#E2D1E6',
      avatarText: '#6D3F72',
      name: 'Berkan Kılıç',
      time: '1 day ago',
      message: 'Seeking guidance in machine learning fundamentals.',
    },
  ];

  const activeMentorships = [
    {
      initials: 'ZD',
      avatarBg: '#DFD9C9',
      avatarText: '#66582F',
      name: 'Zeynep Demir',
      subtitle: 'Mentee · Week 3',
      progress: 65,
    },
    {
      initials: 'AC',
      avatarBg: '#CCD6E5',
      avatarText: '#4A5D7A',
      name: 'Ali Çetin',
      subtitle: 'Mentee · Week 1',
      progress: 20,
    },
  ];

  return (
    <View style={styles.container}>
      <View style={styles.fixedHeader}>
        <View style={styles.topCircle} />

        <View style={styles.statusRow}>
          <Text style={styles.statusText}>9:41</Text>
          <Text style={styles.statusIcons}>▲ ▮</Text>
        </View>

        <View style={styles.headerTopRow}>
          <TouchableOpacity
            style={styles.backButton}
            onPress={() => router.replace('/(tabs)/profile')}
          >
            <Text style={styles.backButtonText}>‹ Back</Text>
          </TouchableOpacity>
        </View>

        <Text style={styles.title}>
          Mentorship{'\n'}
          <Text style={styles.titleItalic}>Requests.</Text>
        </Text>
      </View>

      <ScrollView
        style={styles.scrollArea}
        contentContainerStyle={styles.scrollContent}
        showsVerticalScrollIndicator={false}
      >
        <Text style={styles.sectionTitle}>INCOMING</Text>

        {incomingRequests.map((item, index) => (
          <View key={index} style={styles.requestCard}>
            <View style={styles.topRow}>
              <View
                style={[
                  styles.avatar,
                  { backgroundColor: item.avatarBg },
                ]}
              >
                <Text
                  style={[
                    styles.avatarText,
                    { color: item.avatarText },
                  ]}
                >
                  {item.initials}
                </Text>
              </View>

              <View style={styles.infoArea}>
                <Text style={styles.name}>{item.name}</Text>
                <Text style={styles.time}>{item.time}</Text>
              </View>
            </View>

            <Text style={styles.message}>{item.message}</Text>

            <View style={styles.actionButtonsRow}>
              <TouchableOpacity style={[styles.actionButton, styles.acceptButton]}>
                <Text style={styles.acceptButtonText}>Accept</Text>
              </TouchableOpacity>
              
              <TouchableOpacity style={[styles.actionButton, styles.rejectButton]}>
                <Text style={styles.rejectButtonText}>Reject</Text>
              </TouchableOpacity>
            </View>
          </View>
        ))}

        <Text style={styles.sectionTitle}>ACTIVE MENTORSHIPS</Text>

        {activeMentorships.map((item, index) => (
          <View key={index} style={styles.activeCard}>
            <View style={styles.topRow}>
              <View
                style={[
                  styles.avatar,
                  { backgroundColor: item.avatarBg },
                ]}
              >
                <Text
                  style={[
                    styles.avatarText,
                    { color: item.avatarText },
                  ]}
                >
                  {item.initials}
                </Text>
              </View>

              <View style={styles.infoArea}>
                <Text style={styles.name}>{item.name}</Text>
                <Text style={styles.subtitle}>{item.subtitle}</Text>
              </View>

              <View style={styles.activeBadge}>
                <Text style={styles.activeBadgeText}>Active</Text>
              </View>
            </View>

            <View style={styles.progressTrack}>
              <View
                style={[
                  styles.progressFill,
                  { width: `${item.progress}%` },
                ]}
              />
            </View>

            <Text style={styles.progressText}>Progress: {item.progress}%</Text>

            <TouchableOpacity style={styles.endMentorshipButton}>
              <Text style={styles.endMentorshipText}>End Mentorship</Text>
            </TouchableOpacity>
          </View>
        ))}
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#ECE8E1',
  },
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
  backButtonText: {
    color: '#F7F4EE',
    fontSize: 14,
    fontWeight: '700',
  },
  title: {
    color: '#F7F4EE',
    fontSize: 34,
    lineHeight: 38,
    fontWeight: '700',
    marginTop: 6,
  },
  titleItalic: {
    fontStyle: 'italic',
    fontWeight: '700',
  },
  scrollArea: {
    flex: 1,
    backgroundColor: '#ECE8E1',
  },
  scrollContent: {
    paddingHorizontal: 24,
    paddingTop: 22,
    paddingBottom: 34,
  },
  sectionTitle: {
    color: '#8B8176',
    fontSize: 13,
    fontWeight: '700',
    letterSpacing: 2,
    marginBottom: 16,
  },
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
  topRow: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  avatar: {
    width: 78,
    height: 78,
    borderRadius: 39,
    justifyContent: 'center',
    alignItems: 'center',
    marginRight: 16,
  },
  avatarText: {
    fontSize: 22,
    fontWeight: '700',
  },
  infoArea: {
    flex: 1,
  },
  name: {
    color: '#23372B',
    fontSize: 17,
    fontWeight: '700',
    marginBottom: 4,
  },
  time: {
    color: '#9A8F82',
    fontSize: 13,
    fontWeight: '500',
  },
  subtitle: {
    color: '#9A8F82',
    fontSize: 13,
    fontWeight: '500',
  },
  message: {
    color: '#5A524A',
    fontSize: 15,
    lineHeight: 20,
    marginTop: 18,
    marginBottom: 18,
  },
  actionButtonsRow: {
    flexDirection: 'row',
    gap: 10,
  },
  actionButton: {
    flex: 1,
    borderRadius: 18,
    paddingVertical: 16,
    alignItems: 'center',
  },
  acceptButton: {
    backgroundColor: '#D7E8DA',
  },
  acceptButtonText: {
    color: '#2F563C',
    fontSize: 15,
    fontWeight: '700',
  },
  rejectButton: {
    backgroundColor: '#FDF0EF',
  },
  rejectButtonText: {
    color: '#D9534F',
    fontSize: 15,
    fontWeight: '700',
  },
  activeBadge: {
    backgroundColor: '#D7E8DA',
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderRadius: 16,
  },
  activeBadgeText: {
    color: '#2F563C',
    fontSize: 12,
    fontWeight: '700',
  },
  progressTrack: {
    height: 9,
    borderRadius: 999,
    backgroundColor: '#DDD8CF',
    overflow: 'hidden',
    marginTop: 18,
    marginBottom: 10,
  },
  progressFill: {
    height: '100%',
    borderRadius: 999,
    backgroundColor: '#5D8D66',
  },
  progressText: {
    color: '#8B8176',
    fontSize: 13,
    fontWeight: '500',
  },
  endMentorshipButton: {
    marginTop: 16,
    alignItems: 'center',
    paddingVertical: 12,
  },
  endMentorshipText: {
    color: '#D9534F',
    fontWeight: '600',
    fontSize: 14,
  },
});