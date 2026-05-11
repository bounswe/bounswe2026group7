import React, { useState, useEffect, useCallback } from 'react';
import { router, useLocalSearchParams } from 'expo-router';
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  StyleSheet,
  ScrollView,
  ActivityIndicator,
  RefreshControl,
  Linking,
  Alert,
} from 'react-native';
import apiClient from '../api/client';

function parseString(v: string | string[] | undefined) {
  return Array.isArray(v) ? v[0] : v ?? '';
}

type Meeting = {
  id: number;
  title: string;
  startTime: string;
  endTime: string;
  status: string;
  meetingType: string;
  meetingLink?: string;
  recurring: boolean;
  notes?: string | null;
};

function fmtDay(iso: string) {
  const d = new Date(iso);
  if (isNaN(d.getTime())) return '';
  return d.toLocaleDateString('en-US', { weekday: 'short' });
}

function fmtTime(iso: string) {
  const d = new Date(iso);
  if (isNaN(d.getTime())) return '';
  return d.toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit', hour12: false });
}

function fmtDate(iso: string) {
  const d = new Date(iso);
  if (isNaN(d.getTime())) return '';
  return d.toLocaleDateString('en-US', { month: 'long', day: 'numeric', year: 'numeric' });
}

function statusMeta(status: string) {
  switch (status) {
    case 'CONFIRMED':
      return { label: 'Confirmed', badgeStyle: styles.confirmedBadge, textStyle: styles.confirmedBadgeText };
    case 'PENDING_CONFIRMATION':
      return { label: 'Pending', badgeStyle: styles.pendingBadge, textStyle: styles.pendingBadgeText };
    case 'COMPLETED':
      return { label: 'Completed', badgeStyle: styles.scheduledBadge, textStyle: styles.scheduledBadgeText };
    case 'CANCELLED':
    case 'DECLINED':
      return { label: status === 'CANCELLED' ? 'Cancelled' : 'Declined', badgeStyle: styles.declinedBadge, textStyle: styles.declinedBadgeText };
    default:
      return { label: status, badgeStyle: styles.pendingBadge, textStyle: styles.pendingBadgeText };
  }
}

export default function MeetingsSessionsScreen() {
  const params = useLocalSearchParams();
  const connectedUserName = parseString(params.connectedUserName) || 'Your Connection';
  const mentorshipId = parseString(params.mentorshipId);

  const [meetings, setMeetings] = useState<Meeting[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [expandedId, setExpandedId] = useState<number | null>(null);
  const [notesDraft, setNotesDraft] = useState('');
  const [notesEditing, setNotesEditing] = useState(false);
  const [notesSaving, setNotesSaving] = useState(false);
  const [notesLoading, setNotesLoading] = useState(false);

  const loadMeetings = useCallback(async () => {
    if (!mentorshipId) { setLoading(false); return; }
    try {
      const res = await apiClient.get(`/mentorships/${mentorshipId}/meetings`);
      setMeetings(res.data ?? []);
    } catch {
      // silently show empty state
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, [mentorshipId]);

  useEffect(() => { loadMeetings(); }, [loadMeetings]);

  const now = new Date();
  const upcoming = [...meetings]
    .filter(m => new Date(m.startTime) > now && m.status !== 'CANCELLED' && m.status !== 'DECLINED')
    .sort((a, b) => new Date(a.startTime).getTime() - new Date(b.startTime).getTime())[0];

  const toggleExpand = async (meeting: Meeting) => {
    if (expandedId === meeting.id) {
      setExpandedId(null);
      setNotesEditing(false);
      return;
    }
    setExpandedId(meeting.id);
    setNotesEditing(false);
    setNotesDraft('');
    setNotesLoading(true);
    try {
      const res = await apiClient.get(`/meetings/${meeting.id}`);
      const fetchedNotes = res.data?.notes ?? '';
      setMeetings((prev) => prev.map((m) => m.id === meeting.id ? { ...m, notes: fetchedNotes } : m));
    } catch {
      // keep card open with whatever we have
    } finally {
      setNotesLoading(false);
    }
  };

  const saveNotes = async (meetingId: number) => {
    setNotesSaving(true);
    try {
      const res = await apiClient.patch(`/meetings/${meetingId}/notes`, { notes: notesDraft });
      const saved = res.data?.notes ?? notesDraft;
      setMeetings((prev) => prev.map((m) => m.id === meetingId ? { ...m, notes: saved } : m));
      setNotesEditing(false);
    } catch (err: any) {
      const msg = err?.response?.data?.message || 'Could not save notes.';
      Alert.alert('Error', msg);
    } finally {
      setNotesSaving(false);
    }
  };

  const openSchedule = () => {
    router.push({
      pathname: '/connection-request',
      params: { mode: 'meeting', targetName: connectedUserName, mentorshipId },
    } as any);
  };

  return (
    <View style={styles.container}>
      <View style={styles.fixedHeader}>
        <View style={styles.topCircle} />
        <View style={styles.rightCircle} />

        <View style={styles.statusRow}>
          <Text style={styles.statusText}>{new Date().toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit', hour12: false })}</Text>
          <Text style={styles.statusIcons}>▲ ▮</Text>
        </View>

        <View style={styles.headerTopRow}>
          <TouchableOpacity style={styles.backButton} onPress={() => router.back()}>
            <Text style={styles.backButtonText}>‹ Back</Text>
          </TouchableOpacity>
          <TouchableOpacity onPress={openSchedule}>
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
        refreshControl={
          <RefreshControl
            refreshing={refreshing}
            onRefresh={() => { setRefreshing(true); loadMeetings(); }}
            tintColor="#456B50"
          />
        }
      >
        {upcoming && (
          <View style={styles.upcomingCard}>
            <View style={styles.upcomingCircle} />
            <Text style={styles.upcomingLabel}>UPCOMING</Text>
            <Text style={styles.upcomingTitle}>{upcoming.title}</Text>
            <Text style={styles.upcomingDate}>
              {fmtDate(upcoming.startTime)} · {fmtTime(upcoming.startTime)}
            </Text>

            <View style={styles.upcomingButtonsRow}>
              <TouchableOpacity style={styles.rescheduleButton} onPress={openSchedule}>
                <Text style={styles.rescheduleButtonText}>+ Schedule New</Text>
              </TouchableOpacity>
              {upcoming.meetingLink ? (
                <TouchableOpacity
                  style={styles.joinButton}
                  onPress={() => upcoming.meetingLink && Linking.openURL(upcoming.meetingLink)}
                >
                  <Text style={styles.joinButtonText}>Join Now</Text>
                </TouchableOpacity>
              ) : null}
            </View>
          </View>
        )}

        <Text style={styles.sectionTitle}>
          {meetings.length > 0 ? 'ALL MEETINGS' : 'MEETINGS'}
        </Text>

        {loading ? (
          <ActivityIndicator size="large" color="#456B50" style={{ marginTop: 20 }} />
        ) : meetings.length === 0 ? (
          <View style={styles.emptyState}>
            <Text style={styles.emptyText}>No meetings yet.</Text>
            <TouchableOpacity style={styles.scheduleEmptyButton} onPress={openSchedule}>
              <Text style={styles.scheduleEmptyButtonText}>Schedule a Meeting</Text>
            </TouchableOpacity>
          </View>
        ) : (
          [...meetings]
            .sort((a, b) => new Date(b.startTime).getTime() - new Date(a.startTime).getTime())
            .map((meeting) => {
              const meta = statusMeta(meeting.status);
              const isExpanded = expandedId === meeting.id;
              return (
                <View key={meeting.id} style={styles.meetingCard}>
                  <TouchableOpacity
                    activeOpacity={0.7}
                    onPress={() => toggleExpand(meeting)}
                    style={styles.meetingCardHeader}
                  >
                    <View style={styles.timeBlock}>
                      <Text style={styles.dayText}>{fmtDay(meeting.startTime)}</Text>
                      <Text style={styles.timeText}>{fmtTime(meeting.startTime)}</Text>
                    </View>

                    <View style={styles.cardDivider} />

                    <View style={styles.meetingInfo}>
                      <Text style={styles.meetingTitle}>{meeting.title}</Text>
                      <Text style={styles.meetingMentor}>{connectedUserName}</Text>
                      {meeting.meetingLink ? (
                        <TouchableOpacity onPress={() => meeting.meetingLink && Linking.openURL(meeting.meetingLink)}>
                          <Text style={styles.meetingLinkText}>Join link ↗</Text>
                        </TouchableOpacity>
                      ) : null}
                    </View>

                    <View style={[styles.statusBadge, meta.badgeStyle]}>
                      <Text style={[styles.statusBadgeText, meta.textStyle]}>{meta.label}</Text>
                    </View>
                  </TouchableOpacity>

                  {isExpanded && (
                    <View style={styles.notesSection}>
                      <Text style={styles.notesLabel}>NOTES</Text>
                      {notesLoading ? (
                        <ActivityIndicator size="small" color="#456B50" />
                      ) : notesEditing ? (
                        <>
                          <TextInput
                            style={styles.notesInput}
                            value={notesDraft}
                            onChangeText={setNotesDraft}
                            placeholder="Add notes from this meeting..."
                            placeholderTextColor="#B0A89E"
                            multiline
                            maxLength={2000}
                            autoFocus
                          />
                          <Text style={styles.notesCharCount}>{notesDraft.length}/2000</Text>
                          <View style={styles.notesButtonRow}>
                            <TouchableOpacity
                              style={styles.notesCancelButton}
                              onPress={() => setNotesEditing(false)}
                              disabled={notesSaving}
                            >
                              <Text style={styles.notesCancelText}>Cancel</Text>
                            </TouchableOpacity>
                            <TouchableOpacity
                              style={[styles.notesSaveButton, notesSaving && { opacity: 0.5 }]}
                              onPress={() => saveNotes(meeting.id)}
                              disabled={notesSaving}
                            >
                              {notesSaving
                                ? <ActivityIndicator size="small" color="#F8F6F2" />
                                : <Text style={styles.notesSaveText}>Save</Text>}
                            </TouchableOpacity>
                          </View>
                        </>
                      ) : (
                        <>
                          <Text style={styles.notesText}>
                            {meeting.notes && meeting.notes.trim().length > 0
                              ? meeting.notes
                              : 'No notes yet. Tap Edit to add notes from this meeting.'}
                          </Text>
                          <TouchableOpacity
                            style={styles.notesEditButton}
                            onPress={() => {
                              setNotesDraft(meeting.notes ?? '');
                              setNotesEditing(true);
                            }}
                          >
                            <Text style={styles.notesEditText}>{meeting.notes ? 'Edit Notes' : 'Add Notes'}</Text>
                          </TouchableOpacity>
                        </>
                      )}
                    </View>
                  )}
                </View>
              );
            })
        )}
      </ScrollView>
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
  scheduleText: { color: '#BFE2C8', fontSize: 16, fontWeight: '700' },
  title: { color: '#F7F4EE', fontSize: 34, lineHeight: 38, fontWeight: '700', marginTop: 10 },
  titleItalic: { fontStyle: 'italic', fontWeight: '700' },
  scrollArea: { flex: 1, backgroundColor: '#ECE8E1' },
  content: { paddingHorizontal: 24, paddingTop: 24, paddingBottom: 34 },
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
  rescheduleButton: {
    backgroundColor: 'rgba(255,255,255,0.10)',
    borderWidth: 1.5,
    borderColor: 'rgba(255,255,255,0.16)',
    paddingHorizontal: 20,
    paddingVertical: 14,
    borderRadius: 18,
  },
  rescheduleButtonText: { color: '#F7F4EE', fontSize: 15, fontWeight: '700' },
  joinButton: {
    backgroundColor: '#F8F6F2',
    paddingHorizontal: 20,
    paddingVertical: 14,
    borderRadius: 18,
  },
  joinButtonText: { color: '#2F563C', fontSize: 15, fontWeight: '700' },
  sectionTitle: {
    fontSize: 13,
    fontWeight: '700',
    letterSpacing: 2,
    color: '#8B8176',
    marginBottom: 18,
  },
  emptyState: { paddingVertical: 32, alignItems: 'center' },
  emptyText: { color: '#9A8F82', fontSize: 15, fontWeight: '500', marginBottom: 16 },
  scheduleEmptyButton: {
    backgroundColor: '#456B50',
    borderRadius: 18,
    paddingHorizontal: 20,
    paddingVertical: 12,
  },
  scheduleEmptyButtonText: { color: '#F8F6F2', fontSize: 14, fontWeight: '700' },
  meetingCard: {
    backgroundColor: '#F8F6F2',
    borderRadius: 26,
    marginBottom: 18,
    overflow: 'hidden',
  },
  meetingCardHeader: {
    paddingVertical: 24,
    paddingHorizontal: 20,
    flexDirection: 'row',
    alignItems: 'center',
  },
  notesSection: {
    paddingHorizontal: 20,
    paddingTop: 4,
    paddingBottom: 22,
    borderTopWidth: 1,
    borderTopColor: '#E5DDD1',
  },
  notesLabel: {
    color: '#8B8176',
    fontSize: 12,
    fontWeight: '700',
    letterSpacing: 2,
    marginTop: 14,
    marginBottom: 12,
  },
  notesText: {
    color: '#23372B',
    fontSize: 14,
    lineHeight: 20,
    marginBottom: 12,
  },
  notesInput: {
    backgroundColor: '#FFFFFF',
    borderRadius: 16,
    borderWidth: 1,
    borderColor: '#E1D9CF',
    padding: 14,
    minHeight: 100,
    textAlignVertical: 'top',
    color: '#23372B',
    fontSize: 14,
    lineHeight: 20,
  },
  notesCharCount: {
    color: '#9A8F82',
    fontSize: 12,
    fontWeight: '500',
    alignSelf: 'flex-end',
    marginTop: 6,
    marginBottom: 12,
  },
  notesButtonRow: {
    flexDirection: 'row',
    justifyContent: 'flex-end',
    gap: 10,
  },
  notesCancelButton: {
    paddingHorizontal: 18,
    paddingVertical: 12,
    borderRadius: 16,
    borderWidth: 1.5,
    borderColor: '#D8CEC0',
    backgroundColor: '#F0EDE8',
  },
  notesCancelText: {
    color: '#7E7368',
    fontSize: 14,
    fontWeight: '700',
  },
  notesSaveButton: {
    paddingHorizontal: 22,
    paddingVertical: 12,
    borderRadius: 16,
    backgroundColor: '#456B50',
  },
  notesSaveText: {
    color: '#F8F6F2',
    fontSize: 14,
    fontWeight: '700',
  },
  notesEditButton: {
    alignSelf: 'flex-start',
    paddingHorizontal: 18,
    paddingVertical: 10,
    borderRadius: 14,
    backgroundColor: '#D7E8DA',
  },
  notesEditText: {
    color: '#2F563C',
    fontSize: 13,
    fontWeight: '700',
  },
  timeBlock: { width: 90, alignItems: 'center', justifyContent: 'center' },
  dayText: { fontSize: 14, color: '#9A8F82', marginBottom: 6, fontWeight: '500' },
  timeText: { fontSize: 20, color: '#23372B', fontWeight: '700' },
  cardDivider: {
    width: 1,
    alignSelf: 'stretch',
    backgroundColor: '#E1D9CF',
    marginHorizontal: 18,
  },
  meetingInfo: { flex: 1 },
  meetingTitle: { fontSize: 18, fontWeight: '700', color: '#23372B', marginBottom: 6, lineHeight: 24 },
  meetingMentor: { fontSize: 14, color: '#9A8F82', fontWeight: '500' },
  meetingLinkText: { fontSize: 13, color: '#456B50', fontWeight: '700', marginTop: 4 },
  statusBadge: { paddingHorizontal: 16, paddingVertical: 9, borderRadius: 16, marginLeft: 12 },
  statusBadgeText: { fontSize: 12, fontWeight: '700' },
  confirmedBadge: { backgroundColor: '#D7E8DA' },
  confirmedBadgeText: { color: '#2F563C' },
  scheduledBadge: { backgroundColor: '#DCEAF9' },
  scheduledBadgeText: { color: '#255FA8' },
  pendingBadge: { backgroundColor: '#F2E4C9' },
  pendingBadgeText: { color: '#9B6A1B' },
  declinedBadge: { backgroundColor: '#F5D9D6' },
  declinedBadgeText: { color: '#9B3A35' },
});
