import React from 'react';
import { router } from 'expo-router';
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  ScrollView,
} from 'react-native';

export default function MeetingsSessionsScreen() {
  const meetings = [
    {
      day: 'Mon',
      time: '10:00',
      title: 'Code Review',
      mentor: 'Burak Afşar',
      status: 'Confirmed',
      statusType: 'confirmed',
    },
    {
      day: 'Wed',
      time: '14:00',
      title: 'Architecture\nTalk',
      mentor: 'Burak Afşar',
      status: 'Scheduled',
      statusType: 'scheduled',
    },
    {
      day: 'Fri',
      time: '16:00',
      title: 'Weekly Wrap-up',
      mentor: 'Burak Afşar',
      status: 'Pending',
      statusType: 'pending',
    },
  ];

  return (
    <View style={styles.container}>
      <View style={styles.fixedHeader}>
        <View style={styles.topCircle} />
        <View style={styles.rightCircle} />

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

          <TouchableOpacity>
            <Text style={styles.scheduleText}>+ Schedule</Text>
          </TouchableOpacity>
        </View>

        <Text style={styles.title}>
          Meetings{'\n'}
          <Text style={styles.titleItalic}>& Sessions.</Text>
        </Text>
      </View>

      <ScrollView
        style={styles.scrollArea}
        contentContainerStyle={styles.content}
        showsVerticalScrollIndicator={false}
      >
        <View style={styles.upcomingCard}>
          <View style={styles.upcomingCircle} />
          <Text style={styles.upcomingLabel}>UPCOMING</Text>
          <Text style={styles.upcomingTitle}>Sprint Review</Text>
          <Text style={styles.upcomingDate}>March 29, 2026 · 15:00</Text>

          <View style={styles.upcomingButtonsRow}>
            <TouchableOpacity style={styles.rescheduleButton}>
              <Text style={styles.rescheduleButtonText}>Reschedule</Text>
            </TouchableOpacity>

            <TouchableOpacity style={styles.joinButton}>
              <Text style={styles.joinButtonText}>Join Now</Text>
            </TouchableOpacity>
          </View>
        </View>

        <Text style={styles.sectionTitle}>THIS WEEK</Text>

        {meetings.map((meeting, index) => (
          <View key={index} style={styles.meetingCard}>
            <View style={styles.timeBlock}>
              <Text style={styles.dayText}>{meeting.day}</Text>
              <Text style={styles.timeText}>{meeting.time}</Text>
            </View>

            <View style={styles.cardDivider} />

            <View style={styles.meetingInfo}>
              <Text style={styles.meetingTitle}>{meeting.title}</Text>
              <Text style={styles.meetingMentor}>{meeting.mentor}</Text>
            </View>

            <View
              style={[
                styles.statusBadge,
                meeting.statusType === 'confirmed' && styles.confirmedBadge,
                meeting.statusType === 'scheduled' && styles.scheduledBadge,
                meeting.statusType === 'pending' && styles.pendingBadge,
              ]}
            >
              <Text
                style={[
                  styles.statusBadgeText,
                  meeting.statusType === 'confirmed' && styles.confirmedBadgeText,
                  meeting.statusType === 'scheduled' && styles.scheduledBadgeText,
                  meeting.statusType === 'pending' && styles.pendingBadgeText,
                ]}
              >
                {meeting.status}
              </Text>
            </View>
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
    justifyContent: 'space-between',
    alignItems: 'center',
    marginTop: 18,
    marginBottom: 8,
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
  scheduleText: {
    color: '#BFE2C8',
    fontSize: 16,
    fontWeight: '700',
  },
  title: {
    color: '#F7F4EE',
    fontSize: 34,
    lineHeight: 38,
    fontWeight: '700',
    marginTop: 10,
  },
  titleItalic: {
    fontStyle: 'italic',
    fontWeight: '700',
  },
  scrollArea: {
    flex: 1,
    backgroundColor: '#ECE8E1',
  },
  content: {
    paddingHorizontal: 24,
    paddingTop: 24,
    paddingBottom: 34,
  },
  upcomingCard: {
    backgroundColor: '#4D845E',
    borderRadius: 32,
    padding: 24,
    marginBottom: 28,
    overflow: 'hidden',
  },
  upcomingCircle: {
    position: 'absolute',
    width: 170,
    height: 170,
    borderRadius: 85,
    backgroundColor: 'rgba(255,255,255,0.06)',
    right: -10,
    top: 20,
  },
  upcomingLabel: {
    color: 'rgba(247,244,238,0.72)',
    fontSize: 13,
    fontWeight: '700',
    letterSpacing: 2,
    marginBottom: 12,
  },
  upcomingTitle: {
    color: '#F7F4EE',
    fontSize: 28,
    fontWeight: '700',
    marginBottom: 8,
  },
  upcomingDate: {
    color: 'rgba(247,244,238,0.85)',
    fontSize: 16,
    fontWeight: '500',
    marginBottom: 24,
  },
  upcomingButtonsRow: {
    flexDirection: 'row',
    gap: 14,
  },
  rescheduleButton: {
    backgroundColor: 'rgba(255,255,255,0.10)',
    borderWidth: 1.5,
    borderColor: 'rgba(255,255,255,0.16)',
    paddingHorizontal: 28,
    paddingVertical: 16,
    borderRadius: 18,
  },
  rescheduleButtonText: {
    color: '#F7F4EE',
    fontSize: 16,
    fontWeight: '700',
  },
  joinButton: {
    backgroundColor: '#F8F6F2',
    paddingHorizontal: 28,
    paddingVertical: 16,
    borderRadius: 18,
  },
  joinButtonText: {
    color: '#2F563C',
    fontSize: 16,
    fontWeight: '700',
  },
  sectionTitle: {
    fontSize: 13,
    fontWeight: '700',
    letterSpacing: 2,
    color: '#8B8176',
    marginBottom: 18,
  },
  meetingCard: {
    backgroundColor: '#F8F6F2',
    borderRadius: 26,
    paddingVertical: 24,
    paddingHorizontal: 20,
    marginBottom: 18,
    flexDirection: 'row',
    alignItems: 'center',
  },
  timeBlock: {
    width: 90,
    alignItems: 'center',
    justifyContent: 'center',
  },
  dayText: {
    fontSize: 14,
    color: '#9A8F82',
    marginBottom: 6,
    fontWeight: '500',
  },
  timeText: {
    fontSize: 20,
    color: '#23372B',
    fontWeight: '700',
  },
  cardDivider: {
    width: 1,
    alignSelf: 'stretch',
    backgroundColor: '#E1D9CF',
    marginHorizontal: 18,
  },
  meetingInfo: {
    flex: 1,
  },
  meetingTitle: {
    fontSize: 18,
    fontWeight: '700',
    color: '#23372B',
    marginBottom: 6,
    lineHeight: 24,
  },
  meetingMentor: {
    fontSize: 14,
    color: '#9A8F82',
    fontWeight: '500',
  },
  statusBadge: {
    paddingHorizontal: 16,
    paddingVertical: 9,
    borderRadius: 16,
    marginLeft: 12,
  },
  statusBadgeText: {
    fontSize: 12,
    fontWeight: '700',
  },
  confirmedBadge: {
    backgroundColor: '#D7E8DA',
  },
  confirmedBadgeText: {
    color: '#2F563C',
  },
  scheduledBadge: {
    backgroundColor: '#DCEAF9',
  },
  scheduledBadgeText: {
    color: '#255FA8',
  },
  pendingBadge: {
    backgroundColor: '#F2E4C9',
  },
  pendingBadgeText: {
    color: '#9B6A1B',
  },
});