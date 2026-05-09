import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { router, useLocalSearchParams } from 'expo-router';
import {
  ActivityIndicator,
  Alert,
  Linking,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native';

import apiClient from '../api/client';

function parseString(value: string | string[] | undefined) {
  return Array.isArray(value) ? value[0] : value ?? '';
}

type MeetingSummary = {
  id: number;
  mentorshipId: number;
  title: string;
  startTime: string;
  endTime: string;
  status: string;
  meetingType: string;
  meetingLink?: string | null;
  recurring: boolean;
  recurrenceRule?: string | null;
};

type MeetingDetail = MeetingSummary & {
  description?: string | null;
  confirmedAt?: string | null;
  confirmationDeadline?: string | null;
  notes?: string | null;
  createdAt?: string | null;
  actionItems?: { id: number; text: string; completed: boolean }[];
  pendingRescheduleRequest?: {
    id: number;
    proposedStart: string;
    proposedEnd: string;
    reason?: string | null;
    status: string;
    requestedById: number;
  } | null;
};

function formatStatusLabel(status: string) {
  return status
    .toLowerCase()
    .split('_')
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ');
}

function formatDay(iso: string) {
  return new Date(iso).toLocaleDateString('en-US', { weekday: 'short' });
}

function formatDate(iso: string) {
  return new Date(iso).toLocaleDateString('en-US', {
    month: 'long',
    day: 'numeric',
    year: 'numeric',
  });
}

function formatTime(iso: string) {
  return new Date(iso).toLocaleTimeString('en-GB', {
    hour: '2-digit',
    minute: '2-digit',
  });
}

function toApiDateTime(input: string) {
  const trimmed = input.trim();
  if (!trimmed) return '';
  if (trimmed.includes('T')) {
    const parsed = new Date(trimmed);
    return Number.isNaN(parsed.getTime()) ? '' : parsed.toISOString();
  }

  const normalized = trimmed.replace(' ', 'T');
  const parsed = new Date(normalized);
  return Number.isNaN(parsed.getTime()) ? '' : parsed.toISOString();
}

function statusStyles(status: string) {
  switch (status) {
    case 'CONFIRMED':
      return { bg: '#D7E8DA', text: '#2F563C' };
    case 'PENDING_CONFIRMATION':
    case 'PENDING':
      return { bg: '#F1E1BB', text: '#8A5D12' };
    case 'DECLINED':
    case 'REJECTED':
    case 'CANCELLED':
      return { bg: '#FDF0EF', text: '#D9534F' };
    default:
      return { bg: '#E8E1D6', text: '#6A5E52' };
  }
}

export default function MeetingsSessionsScreen() {
  const params = useLocalSearchParams();
  const connectedUserName = parseString(params.connectedUserName) || 'Your Mentor';
  const mentorshipId = parseString(params.mentorshipId);

  const [meetings, setMeetings] = useState<MeetingSummary[]>([]);
  const [selectedMeeting, setSelectedMeeting] = useState<MeetingDetail | null>(null);
  const [listLoading, setListLoading] = useState(true);
  const [detailLoading, setDetailLoading] = useState(false);
  const [showRescheduleForm, setShowRescheduleForm] = useState(false);
  const [rescheduleStart, setRescheduleStart] = useState('');
  const [rescheduleEnd, setRescheduleEnd] = useState('');
  const [rescheduleReason, setRescheduleReason] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const fetchMeetings = useCallback(async () => {
    if (!mentorshipId) {
      setListLoading(false);
      return;
    }

    try {
      setListLoading(true);
      const res = await apiClient.get(`/mentorships/${mentorshipId}/meetings`);
      setMeetings(res.data ?? []);
    } catch (error) {
      console.error('Failed to load meetings:', error);
      Alert.alert('Error', 'Could not load meetings.');
    } finally {
      setListLoading(false);
    }
  }, [mentorshipId]);

  useEffect(() => {
    void fetchMeetings();
  }, [fetchMeetings]);

  const openMeetingLink = async (meetingLink: string) => {
    try {
      const supported = await Linking.canOpenURL(meetingLink);
      if (!supported) {
        Alert.alert('Unavailable link', 'This meeting link cannot be opened on your device.');
        return;
      }

      await Linking.openURL(meetingLink);
    } catch {
      Alert.alert('Error', 'Could not open the meeting link.');
    }
  };

  const upcomingMeeting = useMemo(() => {
    const now = Date.now();
    return meetings
      .filter((meeting) => new Date(meeting.startTime).getTime() >= now)
      .sort((a, b) => new Date(a.startTime).getTime() - new Date(b.startTime).getTime())[0];
  }, [meetings]);
  const upcomingMeetingLink = upcomingMeeting?.meetingLink ?? null;

  const openMeeting = async (meetingId: number) => {
    try {
      setDetailLoading(true);
      const res = await apiClient.get(`/meetings/${meetingId}`);
      setSelectedMeeting(res.data);
      setShowRescheduleForm(false);
      setRescheduleStart('');
      setRescheduleEnd('');
      setRescheduleReason('');
    } catch (error) {
      console.error('Failed to load meeting detail:', error);
      Alert.alert('Error', 'Could not load meeting details.');
    } finally {
      setDetailLoading(false);
    }
  };

  const submitReschedule = async () => {
    if (!selectedMeeting) return;

    const proposedStart = toApiDateTime(rescheduleStart);
    const proposedEnd = toApiDateTime(rescheduleEnd);
    if (!proposedStart || !proposedEnd) {
      Alert.alert(
        'Invalid date',
        'Enter proposed start and end values in a valid format like 2026-05-15 14:00.'
      );
      return;
    }

    try {
      setSubmitting(true);
      await apiClient.post(`/meetings/${selectedMeeting.id}/reschedule-requests`, {
        proposedStart,
        proposedEnd,
        reason: rescheduleReason.trim() || undefined,
      });
      Alert.alert('Success', 'Reschedule request sent.');
      setShowRescheduleForm(false);
      setRescheduleStart('');
      setRescheduleEnd('');
      setRescheduleReason('');
      await openMeeting(selectedMeeting.id);
      await fetchMeetings();
    } catch (error: any) {
      const message =
        error.response?.data?.message ||
        error.response?.data?.error ||
        'Could not send the reschedule request.';
      Alert.alert('Error', message);
    } finally {
      setSubmitting(false);
    }
  };

  if (selectedMeeting) {
    const meetingBadge = statusStyles(selectedMeeting.status);
    const rescheduleBadge = selectedMeeting.pendingRescheduleRequest
      ? statusStyles(selectedMeeting.pendingRescheduleRequest.status)
      : null;

    return (
      <View style={styles.container}>
        <View style={styles.fixedHeader}>
          <View style={styles.topCircle} />
          <View style={styles.rightCircle} />

          <View style={styles.statusRow}>
            <Text style={styles.statusText}>
              {new Date().toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit', hour12: false })}
            </Text>
            <Text style={styles.statusIcons}>▲ ▮</Text>
          </View>

          <View style={styles.headerTopRow}>
            <TouchableOpacity style={styles.backButton} onPress={() => setSelectedMeeting(null)}>
              <Text style={styles.backButtonText}>‹ Back</Text>
            </TouchableOpacity>
          </View>

          <Text style={styles.title}>
            Meeting{'\n'}
            <Text style={styles.titleItalic}>Details.</Text>
          </Text>
        </View>

        {detailLoading ? (
          <View style={styles.centeredState}>
            <ActivityIndicator size="large" color="#456B50" />
          </View>
        ) : (
          <ScrollView style={styles.scrollArea} contentContainerStyle={styles.content}>
            <View style={styles.detailCard}>
              <View style={styles.detailTopRow}>
                <Text style={styles.detailTitle}>{selectedMeeting.title}</Text>
                <View style={[styles.statusBadge, { backgroundColor: meetingBadge.bg }]}>
                  <Text style={[styles.statusBadgeText, { color: meetingBadge.text }]}>
                    {formatStatusLabel(selectedMeeting.status)}
                  </Text>
                </View>
              </View>

              <Text style={styles.detailMeta}>{formatDate(selectedMeeting.startTime)}</Text>
              <Text style={styles.detailMeta}>
                {formatTime(selectedMeeting.startTime)} - {formatTime(selectedMeeting.endTime)}
              </Text>
              <Text style={styles.detailMeta}>Participants: You and {connectedUserName}</Text>
              <Text style={styles.detailMeta}>Type: {formatStatusLabel(selectedMeeting.meetingType)}</Text>

              {!!selectedMeeting.description && (
                <>
                  <Text style={styles.detailSectionLabel}>Agenda</Text>
                  <Text style={styles.detailBodyText}>{selectedMeeting.description}</Text>
                </>
              )}

              {!!selectedMeeting.notes && (
                <>
                  <Text style={styles.detailSectionLabel}>Notes</Text>
                  <Text style={styles.detailBodyText}>{selectedMeeting.notes}</Text>
                </>
              )}

              {selectedMeeting.pendingRescheduleRequest ? (
                <View style={styles.pendingCard}>
                  <View style={styles.detailTopRow}>
                    <Text style={styles.pendingTitle}>Pending Reschedule Request</Text>
                    {rescheduleBadge ? (
                      <View style={[styles.statusBadge, { backgroundColor: rescheduleBadge.bg }]}>
                        <Text style={[styles.statusBadgeText, { color: rescheduleBadge.text }]}>
                          {formatStatusLabel(selectedMeeting.pendingRescheduleRequest.status)}
                        </Text>
                      </View>
                    ) : null}
                  </View>
                  <Text style={styles.pendingText}>
                    Proposed: {formatDate(selectedMeeting.pendingRescheduleRequest.proposedStart)} ·{' '}
                    {formatTime(selectedMeeting.pendingRescheduleRequest.proposedStart)} -{' '}
                    {formatTime(selectedMeeting.pendingRescheduleRequest.proposedEnd)}
                  </Text>
                  {!!selectedMeeting.pendingRescheduleRequest.reason && (
                    <Text style={styles.pendingText}>
                      Reason: {selectedMeeting.pendingRescheduleRequest.reason}
                    </Text>
                  )}
                </View>
              ) : null}

              {!showRescheduleForm ? (
                <TouchableOpacity style={styles.rescheduleButton} onPress={() => setShowRescheduleForm(true)}>
                  <Text style={styles.rescheduleButtonText}>Request Reschedule</Text>
                </TouchableOpacity>
              ) : (
                <View style={styles.formCard}>
                  <Text style={styles.formTitle}>Request a Time Change</Text>
                  <Text style={styles.formHelp}>Use a value like `2026-05-15 14:00`.</Text>

                  <Text style={styles.label}>PROPOSED START</Text>
                  <TextInput
                    style={styles.input}
                    value={rescheduleStart}
                    onChangeText={setRescheduleStart}
                    placeholder="2026-05-15 14:00"
                    placeholderTextColor="#B5ADA3"
                  />

                  <Text style={styles.label}>PROPOSED END</Text>
                  <TextInput
                    style={styles.input}
                    value={rescheduleEnd}
                    onChangeText={setRescheduleEnd}
                    placeholder="2026-05-15 15:00"
                    placeholderTextColor="#B5ADA3"
                  />

                  <Text style={styles.label}>REASON (OPTIONAL)</Text>
                  <TextInput
                    style={[styles.input, styles.bigInput]}
                    value={rescheduleReason}
                    onChangeText={setRescheduleReason}
                    placeholder="Explain why you need to change the meeting time"
                    placeholderTextColor="#B5ADA3"
                    multiline
                    textAlignVertical="top"
                  />

                  <View style={styles.formActions}>
                    <TouchableOpacity
                      style={styles.formCancelButton}
                      onPress={() => setShowRescheduleForm(false)}
                      disabled={submitting}
                    >
                      <Text style={styles.formCancelButtonText}>Cancel</Text>
                    </TouchableOpacity>
                    <TouchableOpacity
                      style={[styles.primaryButton, submitting && { opacity: 0.6 }]}
                      onPress={submitReschedule}
                      disabled={submitting}
                    >
                      <Text style={styles.primaryButtonText}>
                        {submitting ? 'Sending...' : 'Submit Request'}
                      </Text>
                    </TouchableOpacity>
                  </View>
                </View>
              )}
            </View>
          </ScrollView>
        )}
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <View style={styles.fixedHeader}>
        <View style={styles.topCircle} />
        <View style={styles.rightCircle} />

        <View style={styles.statusRow}>
          <Text style={styles.statusText}>
            {new Date().toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit', hour12: false })}
          </Text>
          <Text style={styles.statusIcons}>▲ ▮</Text>
        </View>

        <View style={styles.headerTopRow}>
          <TouchableOpacity style={styles.backButton} onPress={() => router.back()}>
            <Text style={styles.backButtonText}>‹ Back</Text>
          </TouchableOpacity>
        </View>

        <Text style={styles.title}>
          Meetings{'\n'}
          <Text style={styles.titleItalic}>& Sessions.</Text>
        </Text>
      </View>

      {listLoading ? (
        <View style={styles.centeredState}>
          <ActivityIndicator size="large" color="#456B50" />
        </View>
      ) : (
        <ScrollView style={styles.scrollArea} contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
          {upcomingMeeting ? (
            <View style={styles.upcomingCard}>
              <View style={styles.upcomingCircle} />
              <Text style={styles.upcomingLabel}>UPCOMING</Text>
              <Text style={styles.upcomingTitle}>{upcomingMeeting.title}</Text>
              <Text style={styles.upcomingDate}>
                {formatDate(upcomingMeeting.startTime)} · {formatTime(upcomingMeeting.startTime)}
              </Text>

              <View style={styles.upcomingButtonsRow}>
                <TouchableOpacity style={styles.rescheduleButtonGhost} onPress={() => openMeeting(upcomingMeeting.id)}>
                  <Text style={styles.rescheduleButtonText}>View Details</Text>
                </TouchableOpacity>

                {!!upcomingMeetingLink && (
                  <TouchableOpacity
                    style={styles.joinButton}
                    onPress={() => openMeetingLink(upcomingMeetingLink)}
                  >
                    <Text style={styles.joinButtonText}>Open Link</Text>
                  </TouchableOpacity>
                )}
              </View>
            </View>
          ) : (
            <View style={styles.emptyState}>
              <Text style={styles.emptyStateTitle}>No meetings scheduled yet</Text>
              <Text style={styles.emptyStateText}>
                Once a meeting is scheduled for this mentorship, it will appear here.
              </Text>
            </View>
          )}

          <Text style={styles.sectionTitle}>ALL MEETINGS</Text>

          {meetings.length === 0 ? (
            <View style={styles.emptyState}>
              <Text style={styles.emptyStateText}>No meeting history for this mentorship.</Text>
            </View>
          ) : (
            meetings.map((meeting) => {
              const badge = statusStyles(meeting.status);
              return (
                <TouchableOpacity
                  key={meeting.id}
                  style={styles.meetingCard}
                  onPress={() => openMeeting(meeting.id)}
                >
                  <View style={styles.timeBlock}>
                    <Text style={styles.dayText}>{formatDay(meeting.startTime)}</Text>
                    <Text style={styles.timeText}>{formatTime(meeting.startTime)}</Text>
                  </View>

                  <View style={styles.cardDivider} />

                  <View style={styles.meetingInfo}>
                    <Text style={styles.meetingTitle}>{meeting.title}</Text>
                    <Text style={styles.meetingMentor}>{connectedUserName}</Text>
                    <Text style={styles.meetingDate}>{formatDate(meeting.startTime)}</Text>
                  </View>

                  <View style={[styles.statusBadge, { backgroundColor: badge.bg }]}>
                    <Text style={[styles.statusBadgeText, { color: badge.text }]}>
                      {formatStatusLabel(meeting.status)}
                    </Text>
                  </View>
                </TouchableOpacity>
              );
            })
          )}
        </ScrollView>
      )}
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
  backButton: {
    backgroundColor: 'rgba(255,255,255,0.10)',
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.16)',
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderRadius: 16,
  },
  backButtonText: { color: '#F7F4EE', fontSize: 14, fontWeight: '700' },
  title: {
    color: '#F7F4EE',
    fontSize: 34,
    lineHeight: 38,
    fontWeight: '700',
    marginTop: 10,
  },
  titleItalic: { fontStyle: 'italic', fontWeight: '700' },
  scrollArea: { flex: 1, backgroundColor: '#ECE8E1' },
  content: { paddingHorizontal: 24, paddingTop: 24, paddingBottom: 34 },
  centeredState: { flex: 1, justifyContent: 'center', alignItems: 'center' },
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
  upcomingTitle: { color: '#F7F4EE', fontSize: 28, fontWeight: '700', marginBottom: 8 },
  upcomingDate: { color: 'rgba(247,244,238,0.85)', fontSize: 16, fontWeight: '500', marginBottom: 24 },
  upcomingButtonsRow: { flexDirection: 'row', gap: 14 },
  rescheduleButtonGhost: {
    backgroundColor: 'rgba(255,255,255,0.10)',
    borderWidth: 1.5,
    borderColor: 'rgba(255,255,255,0.16)',
    paddingHorizontal: 24,
    paddingVertical: 16,
    borderRadius: 18,
  },
  rescheduleButtonText: { color: '#F7F4EE', fontSize: 16, fontWeight: '700' },
  joinButton: {
    backgroundColor: '#F8F6F2',
    paddingHorizontal: 28,
    paddingVertical: 16,
    borderRadius: 18,
  },
  joinButtonText: { color: '#2F563C', fontSize: 16, fontWeight: '700' },
  sectionTitle: { fontSize: 13, fontWeight: '700', letterSpacing: 2, color: '#8B8176', marginBottom: 18 },
  emptyState: {
    backgroundColor: '#F8F6F2',
    borderRadius: 26,
    padding: 22,
    marginBottom: 24,
    alignItems: 'center',
  },
  emptyStateTitle: { color: '#23372B', fontSize: 18, fontWeight: '700', marginBottom: 8 },
  emptyStateText: { color: '#8B8176', fontSize: 14, textAlign: 'center', lineHeight: 20 },
  meetingCard: {
    backgroundColor: '#F8F6F2',
    borderRadius: 26,
    paddingVertical: 24,
    paddingHorizontal: 20,
    marginBottom: 18,
    flexDirection: 'row',
    alignItems: 'center',
  },
  timeBlock: { width: 90, alignItems: 'center', justifyContent: 'center' },
  dayText: { fontSize: 14, color: '#9A8F82', marginBottom: 6, fontWeight: '500' },
  timeText: { fontSize: 20, color: '#23372B', fontWeight: '700' },
  cardDivider: { width: 1, alignSelf: 'stretch', backgroundColor: '#E1DBD3', marginHorizontal: 16 },
  meetingInfo: { flex: 1 },
  meetingTitle: { color: '#23372B', fontSize: 18, fontWeight: '700', marginBottom: 5 },
  meetingMentor: { color: '#8B8176', fontSize: 14, marginBottom: 4 },
  meetingDate: { color: '#9A8F82', fontSize: 13, fontWeight: '500' },
  statusBadge: { paddingHorizontal: 14, paddingVertical: 8, borderRadius: 14 },
  statusBadgeText: { fontSize: 12, fontWeight: '700' },
  detailCard: {
    backgroundColor: '#F8F6F2',
    borderRadius: 28,
    padding: 22,
  },
  detailTopRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-start', gap: 12 },
  detailTitle: { flex: 1, color: '#23372B', fontSize: 24, fontWeight: '700', marginBottom: 8 },
  detailMeta: { color: '#6A5E52', fontSize: 14, marginBottom: 6 },
  detailSectionLabel: {
    color: '#8B8176',
    fontSize: 12,
    fontWeight: '700',
    letterSpacing: 1.2,
    marginTop: 16,
    marginBottom: 8,
  },
  detailBodyText: { color: '#3C352E', fontSize: 15, lineHeight: 22 },
  pendingCard: {
    marginTop: 18,
    backgroundColor: '#FCFBF8',
    borderColor: '#E1DBD3',
    borderWidth: 1,
    borderRadius: 20,
    padding: 16,
  },
  pendingTitle: { color: '#23372B', fontSize: 16, fontWeight: '700' },
  pendingText: { color: '#6A5E52', fontSize: 14, lineHeight: 20, marginTop: 6 },
  rescheduleButton: {
    marginTop: 20,
    backgroundColor: '#4B7B57',
    borderRadius: 22,
    paddingVertical: 16,
    alignItems: 'center',
  },
  formCard: { marginTop: 20, backgroundColor: '#FCFBF8', borderRadius: 22, padding: 18, borderWidth: 1, borderColor: '#E1DBD3' },
  formTitle: { color: '#23372B', fontSize: 18, fontWeight: '700', marginBottom: 6 },
  formHelp: { color: '#8B8176', fontSize: 13, marginBottom: 16 },
  label: { color: '#7E7368', fontSize: 12, fontWeight: '700', marginBottom: 10 },
  input: {
    height: 58,
    borderRadius: 18,
    borderWidth: 1.5,
    borderColor: '#D8CEC0',
    backgroundColor: '#FFFFFF',
    paddingHorizontal: 16,
    fontSize: 15,
    color: '#4A4138',
    marginBottom: 18,
  },
  bigInput: { height: 110, paddingTop: 16 },
  formActions: { flexDirection: 'row', gap: 12, alignItems: 'center' },
  formCancelButton: {
    flex: 1,
    backgroundColor: '#EFE8DE',
    borderRadius: 20,
    paddingVertical: 16,
    alignItems: 'center',
  },
  formCancelButtonText: { color: '#6A5E52', fontSize: 15, fontWeight: '700' },
  primaryButton: {
    flex: 1.3,
    backgroundColor: '#4B7B57',
    borderRadius: 20,
    paddingVertical: 16,
    alignItems: 'center',
  },
  primaryButtonText: { color: '#F8F6F2', fontSize: 15, fontWeight: '700' },
});
