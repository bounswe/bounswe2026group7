import React, { useState } from 'react';
import { router, useLocalSearchParams } from 'expo-router';
import {
  Alert,
  Modal,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native';

type MeetingStatus = 'SCHEDULED' | 'CONFIRMED' | 'COMPLETED' | 'CANCELLED' | 'RESCHEDULED';

type MeetingItem = {
  id: string;
  title: string;
  mentor: string;
  startsAt: string;
  durationMinutes: number;
  agenda: string;
  location: string;
  status: MeetingStatus;
  isRecurring: boolean;
  rescheduleRequested?: boolean;
};

function parseString(value: string | string[] | undefined) {
  return Array.isArray(value) ? value[0] : value ?? '';
}

function parseMeetings(value: string | string[] | undefined, fallbackMentor: string): MeetingItem[] {
  const raw = parseString(value);
  if (!raw) return createFallbackMeetings(fallbackMentor);

  try {
    const parsed = JSON.parse(raw);
    if (!Array.isArray(parsed)) return createFallbackMeetings(fallbackMentor);

    return parsed.map((meeting, index) => ({
      id: String(meeting.id ?? `meeting-${index}`),
      title: String(meeting.title ?? 'Mentorship Meeting'),
      mentor: String(meeting.mentor ?? fallbackMentor),
      startsAt: String(meeting.startsAt ?? meeting.date ?? new Date().toISOString()),
      durationMinutes: Number(meeting.durationMinutes ?? 45),
      agenda: String(meeting.agenda ?? 'Review progress, blockers, and next steps.'),
      location: String(meeting.location ?? 'Online'),
      status: normalizeStatus(meeting.status),
      isRecurring: Boolean(meeting.isRecurring ?? true),
      rescheduleRequested: Boolean(meeting.rescheduleRequested),
    }));
  } catch {
    return createFallbackMeetings(fallbackMentor);
  }
}

function normalizeStatus(status: unknown): MeetingStatus {
  const normalized = String(status ?? '').toUpperCase();
  if (
    normalized === 'SCHEDULED' ||
    normalized === 'CONFIRMED' ||
    normalized === 'COMPLETED' ||
    normalized === 'CANCELLED' ||
    normalized === 'RESCHEDULED'
  ) {
    return normalized;
  }

  return 'SCHEDULED';
}

function addDays(days: number, hour: number, minute = 0) {
  const date = new Date();
  date.setDate(date.getDate() + days);
  date.setHours(hour, minute, 0, 0);
  return date.toISOString();
}

function createFallbackMeetings(mentor: string): MeetingItem[] {
  return [
    {
      id: 'weekly-review',
      title: 'Weekly Progress Review',
      mentor,
      startsAt: addDays(1, 15),
      durationMinutes: 45,
      agenda: 'Review completed goals, discuss blockers, and define the next weekly objective.',
      location: 'Online meeting room',
      status: 'CONFIRMED',
      isRecurring: true,
    },
    {
      id: 'career-roadmap',
      title: 'Career Roadmap Session',
      mentor,
      startsAt: addDays(4, 11, 30),
      durationMinutes: 60,
      agenda: 'Refine the mentee roadmap and decide which skills should be prioritized next.',
      location: 'MentorNet video call',
      status: 'SCHEDULED',
      isRecurring: false,
    },
    {
      id: 'completed-kickoff',
      title: 'Mentorship Kickoff',
      mentor,
      startsAt: addDays(-5, 14),
      durationMinutes: 30,
      agenda: 'Initial expectations, communication preferences, and first milestone discussion.',
      location: 'Online meeting room',
      status: 'COMPLETED',
      isRecurring: false,
    },
  ];
}

function formatDay(dateString: string) {
  return new Date(dateString).toLocaleDateString('en-US', { weekday: 'short' });
}

function formatDate(dateString: string) {
  return new Date(dateString).toLocaleDateString('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
  });
}

function formatTime(dateString: string) {
  return new Date(dateString).toLocaleTimeString('en-US', {
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  });
}

function getStatusLabel(status: MeetingStatus, rescheduleRequested?: boolean) {
  if (rescheduleRequested) return 'Reschedule requested';

  switch (status) {
    case 'CONFIRMED':
      return 'Confirmed';
    case 'COMPLETED':
      return 'Completed';
    case 'CANCELLED':
      return 'Cancelled';
    case 'RESCHEDULED':
      return 'Rescheduled';
    default:
      return 'Scheduled';
  }
}

function StatusBadge({
  status,
  rescheduleRequested,
}: {
  status: MeetingStatus;
  rescheduleRequested?: boolean;
}) {
  const visualStatus = rescheduleRequested ? 'RESCHEDULED' : status;

  return (
    <View
      style={[
        styles.statusBadge,
        visualStatus === 'CONFIRMED' && styles.confirmedBadge,
        visualStatus === 'SCHEDULED' && styles.scheduledBadge,
        visualStatus === 'COMPLETED' && styles.completedBadge,
        visualStatus === 'CANCELLED' && styles.cancelledBadge,
        visualStatus === 'RESCHEDULED' && styles.rescheduledBadge,
      ]}
    >
      <Text
        style={[
          styles.statusBadgeText,
          visualStatus === 'CONFIRMED' && styles.confirmedBadgeText,
          visualStatus === 'SCHEDULED' && styles.scheduledBadgeText,
          visualStatus === 'COMPLETED' && styles.completedBadgeText,
          visualStatus === 'CANCELLED' && styles.cancelledBadgeText,
          visualStatus === 'RESCHEDULED' && styles.rescheduledBadgeText,
        ]}
      >
        {getStatusLabel(status, rescheduleRequested)}
      </Text>
    </View>
  );
}

export default function MeetingsSessionsScreen() {
  const params = useLocalSearchParams();
  const connectedUserName = parseString(params.connectedUserName) || 'Your Mentor';
  const connectedUserType = parseString(params.connectedUserType) || 'mentor';

  const [meetings, setMeetings] = useState<MeetingItem[]>(() =>
    parseMeetings(params.meetings, connectedUserName)
  );
  const [selectedMeetingId, setSelectedMeetingId] = useState(() => meetings[0]?.id ?? '');
  const [isRescheduleVisible, setIsRescheduleVisible] = useState(false);
  const [requestedDate, setRequestedDate] = useState('');
  const [requestedTime, setRequestedTime] = useState('');
  const [rescheduleReason, setRescheduleReason] = useState('');
  const [notice, setNotice] = useState('Meeting reminder: your next session is coming up soon.');

  const now = new Date();
  const upcomingMeetings = meetings
    .filter(
      (meeting) =>
        new Date(meeting.startsAt) >= now &&
        meeting.status !== 'CANCELLED' &&
        meeting.status !== 'COMPLETED'
    )
    .sort((a, b) => new Date(a.startsAt).getTime() - new Date(b.startsAt).getTime());
  const pastMeetings = meetings
    .filter((meeting) => new Date(meeting.startsAt) < now || meeting.status === 'COMPLETED')
    .sort((a, b) => new Date(b.startsAt).getTime() - new Date(a.startsAt).getTime());
  const selectedMeeting =
    meetings.find((meeting) => meeting.id === selectedMeetingId) ?? upcomingMeetings[0] ?? meetings[0];
  const nextMeeting = upcomingMeetings[0];

  const openReschedule = (meeting: MeetingItem) => {
    setSelectedMeetingId(meeting.id);
    setRequestedDate('');
    setRequestedTime('');
    setRescheduleReason('');
    setIsRescheduleVisible(true);
  };

  const submitReschedule = () => {
    if (!selectedMeeting) return;

    if (!requestedDate.trim() || !requestedTime.trim() || !rescheduleReason.trim()) {
      Alert.alert('Missing information', 'Please enter a preferred date, time, and reason.');
      return;
    }

    setMeetings((prev) =>
      prev.map((meeting) =>
        meeting.id === selectedMeeting.id
          ? { ...meeting, status: 'RESCHEDULED', rescheduleRequested: true }
          : meeting
      )
    );
    setNotice(`Reschedule request sent for ${selectedMeeting.title}.`);
    setIsRescheduleVisible(false);
  };

  return (
    <View style={styles.container}>
      <View style={styles.fixedHeader}>
        <View style={styles.topCircle} />
        <View style={styles.rightCircle} />

        <View style={styles.statusRow}>
          <Text style={styles.statusText}>
            {new Date().toLocaleTimeString('en-US', {
              hour: '2-digit',
              minute: '2-digit',
              hour12: false,
            })}
          </Text>
          <Text style={styles.statusIcons}>▲ ▮</Text>
        </View>

        <View style={styles.headerTopRow}>
          <TouchableOpacity style={styles.backButton} onPress={() => router.back()}>
            <Text style={styles.backButtonText}>‹ Back</Text>
          </TouchableOpacity>

          <View style={styles.rolePill}>
            <Text style={styles.rolePillText}>
              {connectedUserType === 'mentor' ? 'Mentee view' : 'Shared view'}
            </Text>
          </View>
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
        <View style={styles.noticeCard}>
          <Text style={styles.noticeIcon}>🔔</Text>
          <View style={styles.noticeTextArea}>
            <Text style={styles.noticeTitle}>Meeting notification</Text>
            <Text style={styles.noticeBody}>{notice}</Text>
          </View>
        </View>

        {nextMeeting ? (
          <View style={styles.upcomingCard}>
            <View style={styles.upcomingCircle} />
            <Text style={styles.upcomingLabel}>NEXT MEETING</Text>
            <Text style={styles.upcomingTitle}>{nextMeeting.title}</Text>
            <Text style={styles.upcomingDate}>
              {formatDate(nextMeeting.startsAt)} · {formatTime(nextMeeting.startsAt)} ·{' '}
              {nextMeeting.durationMinutes} min
            </Text>

            <View style={styles.upcomingMetaRow}>
              <Text style={styles.upcomingMeta}>Agenda: {nextMeeting.agenda}</Text>
            </View>

            <View style={styles.upcomingButtonsRow}>
              <TouchableOpacity style={styles.rescheduleButton} onPress={() => openReschedule(nextMeeting)}>
                <Text style={styles.rescheduleButtonText}>Reschedule</Text>
              </TouchableOpacity>

              <TouchableOpacity
                style={styles.joinButton}
                onPress={() => Alert.alert('Meeting link', 'Meeting link will be available before the session.')}
              >
                <Text style={styles.joinButtonText}>Join Now</Text>
              </TouchableOpacity>
            </View>
          </View>
        ) : (
          <View style={styles.emptyCard}>
            <Text style={styles.emptyTitle}>No upcoming meetings</Text>
            <Text style={styles.emptyText}>Scheduled meetings will appear here once your mentor confirms them.</Text>
          </View>
        )}

        {selectedMeeting && (
          <View style={styles.detailCard}>
            <View style={styles.detailHeader}>
              <View>
                <Text style={styles.sectionEyebrow}>DETAIL</Text>
                <Text style={styles.detailTitle}>{selectedMeeting.title}</Text>
              </View>
              <StatusBadge
                status={selectedMeeting.status}
                rescheduleRequested={selectedMeeting.rescheduleRequested}
              />
            </View>

            <View style={styles.detailGrid}>
              <View style={styles.detailItem}>
                <Text style={styles.detailLabel}>Date</Text>
                <Text style={styles.detailValue}>{formatDate(selectedMeeting.startsAt)}</Text>
              </View>
              <View style={styles.detailItem}>
                <Text style={styles.detailLabel}>Time</Text>
                <Text style={styles.detailValue}>{formatTime(selectedMeeting.startsAt)}</Text>
              </View>
              <View style={styles.detailItem}>
                <Text style={styles.detailLabel}>Location</Text>
                <Text style={styles.detailValue}>{selectedMeeting.location}</Text>
              </View>
              <View style={styles.detailItem}>
                <Text style={styles.detailLabel}>Repeats</Text>
                <Text style={styles.detailValue}>{selectedMeeting.isRecurring ? 'Weekly' : 'No'}</Text>
              </View>
            </View>

            <Text style={styles.agendaLabel}>Agenda</Text>
            <Text style={styles.agendaText}>{selectedMeeting.agenda}</Text>

            <TouchableOpacity
              style={[
                styles.detailRescheduleButton,
                selectedMeeting.status === 'COMPLETED' && styles.disabledButton,
              ]}
              disabled={selectedMeeting.status === 'COMPLETED'}
              onPress={() => openReschedule(selectedMeeting)}
            >
              <Text style={styles.detailRescheduleText}>
                {selectedMeeting.status === 'COMPLETED' ? 'Completed meeting' : 'Request reschedule'}
              </Text>
            </TouchableOpacity>
          </View>
        )}

        <Text style={styles.sectionTitle}>UPCOMING</Text>

        {upcomingMeetings.map((meeting) => (
          <TouchableOpacity
            key={meeting.id}
            style={[
              styles.meetingCard,
              selectedMeeting?.id === meeting.id && styles.meetingCardSelected,
            ]}
            activeOpacity={0.84}
            onPress={() => setSelectedMeetingId(meeting.id)}
          >
            <View style={styles.timeBlock}>
              <Text style={styles.dayText}>{formatDay(meeting.startsAt)}</Text>
              <Text style={styles.timeText}>{formatTime(meeting.startsAt)}</Text>
            </View>

            <View style={styles.cardDivider} />

            <View style={styles.meetingInfo}>
              <Text style={styles.meetingTitle}>{meeting.title}</Text>
              <Text style={styles.meetingMentor}>{meeting.mentor}</Text>
              <Text style={styles.meetingDate}>{formatDate(meeting.startsAt)}</Text>
            </View>

            <StatusBadge status={meeting.status} rescheduleRequested={meeting.rescheduleRequested} />
          </TouchableOpacity>
        ))}

        <Text style={styles.sectionTitle}>PAST</Text>

        {pastMeetings.map((meeting) => (
          <TouchableOpacity
            key={meeting.id}
            style={styles.meetingCard}
            activeOpacity={0.84}
            onPress={() => setSelectedMeetingId(meeting.id)}
          >
            <View style={styles.timeBlock}>
              <Text style={styles.dayText}>{formatDay(meeting.startsAt)}</Text>
              <Text style={styles.timeText}>{formatTime(meeting.startsAt)}</Text>
            </View>

            <View style={styles.cardDivider} />

            <View style={styles.meetingInfo}>
              <Text style={styles.meetingTitle}>{meeting.title}</Text>
              <Text style={styles.meetingMentor}>{meeting.mentor}</Text>
              <Text style={styles.meetingDate}>{formatDate(meeting.startsAt)}</Text>
            </View>

            <StatusBadge status={meeting.status} />
          </TouchableOpacity>
        ))}
      </ScrollView>

      <Modal visible={isRescheduleVisible} transparent animationType="fade">
        <View style={styles.modalBackdrop}>
          <View style={styles.modalCard}>
            <Text style={styles.modalEyebrow}>RESCHEDULE REQUEST</Text>
            <Text style={styles.modalTitle}>{selectedMeeting?.title}</Text>
            <Text style={styles.modalText}>
              Send your preferred date and time. Your mentor will confirm or suggest another slot.
            </Text>

            <TextInput
              style={styles.input}
              value={requestedDate}
              onChangeText={setRequestedDate}
              placeholder="Preferred date, e.g. Apr 28"
              placeholderTextColor="#AFA79C"
            />
            <TextInput
              style={styles.input}
              value={requestedTime}
              onChangeText={setRequestedTime}
              placeholder="Preferred time, e.g. 16:30"
              placeholderTextColor="#AFA79C"
            />
            <TextInput
              style={[styles.input, styles.reasonInput]}
              value={rescheduleReason}
              onChangeText={setRescheduleReason}
              placeholder="Reason for rescheduling"
              placeholderTextColor="#AFA79C"
              multiline
            />

            <View style={styles.modalActions}>
              <TouchableOpacity style={styles.cancelButton} onPress={() => setIsRescheduleVisible(false)}>
                <Text style={styles.cancelButtonText}>Cancel</Text>
              </TouchableOpacity>
              <TouchableOpacity style={styles.submitButton} onPress={submitReschedule}>
                <Text style={styles.submitButtonText}>Send request</Text>
              </TouchableOpacity>
            </View>
          </View>
        </View>
      </Modal>
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
  rolePill: {
    backgroundColor: 'rgba(255,255,255,0.10)',
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.14)',
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderRadius: 18,
  },
  rolePillText: {
    color: '#D7E8DA',
    fontSize: 13,
    fontWeight: '800',
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
  noticeCard: {
    backgroundColor: '#FFF9ED',
    borderWidth: 1,
    borderColor: '#EBD9B8',
    borderRadius: 24,
    padding: 18,
    marginBottom: 18,
    flexDirection: 'row',
    alignItems: 'center',
  },
  noticeIcon: {
    fontSize: 24,
    marginRight: 14,
  },
  noticeTextArea: {
    flex: 1,
  },
  noticeTitle: {
    color: '#7B5D2A',
    fontSize: 13,
    fontWeight: '800',
    letterSpacing: 1,
    textTransform: 'uppercase',
    marginBottom: 4,
  },
  noticeBody: {
    color: '#5E5040',
    fontSize: 14,
    lineHeight: 20,
    fontWeight: '600',
  },
  upcomingCard: {
    backgroundColor: '#4D845E',
    borderRadius: 32,
    padding: 24,
    marginBottom: 22,
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
    fontWeight: '600',
    marginBottom: 12,
  },
  upcomingMetaRow: {
    backgroundColor: 'rgba(255,255,255,0.10)',
    padding: 14,
    borderRadius: 18,
    marginBottom: 20,
  },
  upcomingMeta: {
    color: 'rgba(247,244,238,0.90)',
    fontSize: 14,
    lineHeight: 20,
    fontWeight: '600',
  },
  upcomingButtonsRow: {
    flexDirection: 'row',
    gap: 14,
  },
  rescheduleButton: {
    flex: 1,
    backgroundColor: 'rgba(255,255,255,0.10)',
    borderWidth: 1.5,
    borderColor: 'rgba(255,255,255,0.16)',
    paddingHorizontal: 18,
    paddingVertical: 16,
    borderRadius: 18,
    alignItems: 'center',
  },
  rescheduleButtonText: {
    color: '#F7F4EE',
    fontSize: 16,
    fontWeight: '700',
  },
  joinButton: {
    flex: 1,
    backgroundColor: '#F8F6F2',
    paddingHorizontal: 18,
    paddingVertical: 16,
    borderRadius: 18,
    alignItems: 'center',
  },
  joinButtonText: {
    color: '#2F563C',
    fontSize: 16,
    fontWeight: '700',
  },
  emptyCard: {
    backgroundColor: '#F8F6F2',
    borderRadius: 28,
    padding: 24,
    marginBottom: 22,
  },
  emptyTitle: {
    color: '#23372B',
    fontSize: 20,
    fontWeight: '800',
    marginBottom: 8,
  },
  emptyText: {
    color: '#8B8176',
    fontSize: 15,
    lineHeight: 22,
    fontWeight: '600',
  },
  detailCard: {
    backgroundColor: '#F8F6F2',
    borderRadius: 30,
    padding: 22,
    marginBottom: 26,
    borderWidth: 1,
    borderColor: '#E5DDD2',
  },
  detailHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    gap: 12,
    marginBottom: 18,
  },
  sectionEyebrow: {
    color: '#8B8176',
    fontSize: 12,
    fontWeight: '800',
    letterSpacing: 2,
    marginBottom: 5,
  },
  detailTitle: {
    color: '#23372B',
    fontSize: 22,
    fontWeight: '800',
    lineHeight: 28,
    maxWidth: 190,
  },
  detailGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 12,
    marginBottom: 18,
  },
  detailItem: {
    width: '47%',
    backgroundColor: '#ECE8E1',
    borderRadius: 18,
    padding: 14,
  },
  detailLabel: {
    color: '#9A8F82',
    fontSize: 12,
    fontWeight: '800',
    marginBottom: 5,
    textTransform: 'uppercase',
  },
  detailValue: {
    color: '#23372B',
    fontSize: 15,
    fontWeight: '700',
  },
  agendaLabel: {
    color: '#23372B',
    fontSize: 16,
    fontWeight: '800',
    marginBottom: 8,
  },
  agendaText: {
    color: '#6F665B',
    fontSize: 15,
    lineHeight: 22,
    fontWeight: '600',
    marginBottom: 18,
  },
  detailRescheduleButton: {
    backgroundColor: '#456B50',
    borderRadius: 18,
    paddingVertical: 16,
    alignItems: 'center',
  },
  disabledButton: {
    backgroundColor: '#C9C1B6',
  },
  detailRescheduleText: {
    color: '#F8F6F2',
    fontSize: 16,
    fontWeight: '800',
  },
  sectionTitle: {
    fontSize: 13,
    fontWeight: '700',
    letterSpacing: 2,
    color: '#8B8176',
    marginBottom: 18,
    marginTop: 4,
  },
  meetingCard: {
    backgroundColor: '#F8F6F2',
    borderRadius: 26,
    paddingVertical: 22,
    paddingHorizontal: 18,
    marginBottom: 18,
    flexDirection: 'row',
    alignItems: 'center',
    borderWidth: 1,
    borderColor: 'transparent',
  },
  meetingCardSelected: {
    borderColor: '#6EA37A',
  },
  timeBlock: {
    width: 76,
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
    marginHorizontal: 16,
  },
  meetingInfo: {
    flex: 1,
  },
  meetingTitle: {
    fontSize: 17,
    fontWeight: '700',
    color: '#23372B',
    marginBottom: 5,
    lineHeight: 22,
  },
  meetingMentor: {
    fontSize: 14,
    color: '#9A8F82',
    fontWeight: '600',
    marginBottom: 3,
  },
  meetingDate: {
    fontSize: 13,
    color: '#B0A59A',
    fontWeight: '600',
  },
  statusBadge: {
    paddingHorizontal: 12,
    paddingVertical: 9,
    borderRadius: 16,
    marginLeft: 8,
    maxWidth: 108,
  },
  statusBadgeText: {
    fontSize: 11,
    fontWeight: '800',
    textAlign: 'center',
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
  completedBadge: {
    backgroundColor: '#E2DDD6',
  },
  completedBadgeText: {
    color: '#6F665B',
  },
  cancelledBadge: {
    backgroundColor: '#F0D4D0',
  },
  cancelledBadgeText: {
    color: '#9E3B31',
  },
  rescheduledBadge: {
    backgroundColor: '#F2E4C9',
  },
  rescheduledBadgeText: {
    color: '#9B6A1B',
  },
  modalBackdrop: {
    flex: 1,
    backgroundColor: 'rgba(35,55,43,0.55)',
    justifyContent: 'center',
    paddingHorizontal: 24,
  },
  modalCard: {
    backgroundColor: '#F8F6F2',
    borderRadius: 30,
    padding: 24,
  },
  modalEyebrow: {
    color: '#8B8176',
    fontSize: 12,
    fontWeight: '800',
    letterSpacing: 2,
    marginBottom: 8,
  },
  modalTitle: {
    color: '#23372B',
    fontSize: 24,
    fontWeight: '800',
    marginBottom: 8,
  },
  modalText: {
    color: '#7B7167',
    fontSize: 15,
    lineHeight: 22,
    fontWeight: '600',
    marginBottom: 18,
  },
  input: {
    backgroundColor: '#ECE8E1',
    borderRadius: 18,
    paddingHorizontal: 16,
    paddingVertical: 14,
    color: '#23372B',
    fontSize: 15,
    fontWeight: '600',
    marginBottom: 12,
  },
  reasonInput: {
    minHeight: 96,
    textAlignVertical: 'top',
  },
  modalActions: {
    flexDirection: 'row',
    gap: 12,
    marginTop: 4,
  },
  cancelButton: {
    flex: 1,
    backgroundColor: '#ECE8E1',
    borderRadius: 18,
    paddingVertical: 15,
    alignItems: 'center',
  },
  cancelButtonText: {
    color: '#6F665B',
    fontSize: 15,
    fontWeight: '800',
  },
  submitButton: {
    flex: 1,
    backgroundColor: '#456B50',
    borderRadius: 18,
    paddingVertical: 15,
    alignItems: 'center',
  },
  submitButtonText: {
    color: '#F8F6F2',
    fontSize: 15,
    fontWeight: '800',
  },
});
