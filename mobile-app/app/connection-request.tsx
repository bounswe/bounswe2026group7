import React, { useMemo, useState } from 'react';
import { router, useLocalSearchParams } from 'expo-router';
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  StyleSheet,
  ScrollView,
  Alert,
  ActivityIndicator,
} from 'react-native';
import apiClient from '../api/client';

function parseString(value: string | string[] | undefined) {
  return Array.isArray(value) ? value[0] : value ?? '';
}

export default function ConnectionRequestScreen() {
  const params = useLocalSearchParams();

  const mode = parseString(params.mode) as 'meeting' | 'change' | 'end';
  const targetName = parseString(params.targetName);
  const targetType = parseString(params.targetType);
  const mentorshipId = parseString(params.mentorshipId);

  const [title, setTitle] = useState('');
  const [startTime, setStartTime] = useState('');
  const [endTime, setEndTime] = useState('');
  const [meetingType, setMeetingType] = useState<'ONLINE' | 'IN_PERSON'>('ONLINE');
  const [meetingLink, setMeetingLink] = useState('');
  const [details, setDetails] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const isMeetingMode = mode === 'meeting';

  const config = useMemo(() => {
    switch (mode) {
      case 'meeting':
        return {
          screenTitle: 'Schedule Meeting',
          buttonText: 'Schedule Meeting',
        };
      case 'change':
        return {
          screenTitle: 'Change Request',
          buttonText: 'Send Change Request',
        };
      default:
        return {
          screenTitle: 'End Mentorship',
          buttonText: 'Send End Request',
        };
    }
  }, [mode]);

  const parseIsoDateTime = (raw: string): string | null => {
    const trimmed = raw.trim();
    if (!trimmed) return null;
    const d = new Date(trimmed);
    if (isNaN(d.getTime())) return null;
    return d.toISOString();
  };

  const handleSubmit = async () => {
    if (!title.trim()) {
      Alert.alert('Missing Title', 'Please enter a title.');
      return;
    }

    if (isMeetingMode) {
      if (!mentorshipId) {
        Alert.alert('Error', 'Mentorship ID is missing.');
        return;
      }

      const parsedStart = parseIsoDateTime(startTime);
      if (!parsedStart) {
        Alert.alert('Invalid Start Time', 'Enter start time like: 2026-05-20 10:00');
        return;
      }

      let parsedEnd: string;
      if (endTime.trim()) {
        const e = parseIsoDateTime(endTime);
        if (!e) {
          Alert.alert('Invalid End Time', 'Enter end time like: 2026-05-20 11:00');
          return;
        }
        parsedEnd = e;
      } else {
        const startMs = new Date(parsedStart).getTime();
        parsedEnd = new Date(startMs + 60 * 60 * 1000).toISOString();
      }

      if (meetingType === 'ONLINE' && !meetingLink.trim()) {
        Alert.alert('Meeting Link Required', 'Please enter a meeting link for online meetings.');
        return;
      }

      setSubmitting(true);
      try {
        await apiClient.post(`/mentorships/${mentorshipId}/meetings`, {
          title: title.trim(),
          description: details.trim() || null,
          startTime: parsedStart,
          endTime: parsedEnd,
          meetingType,
          meetingLink: meetingType === 'ONLINE' ? meetingLink.trim() : null,
          recurring: false,
        });
        Alert.alert('Meeting Scheduled', `Meeting "${title.trim()}" has been scheduled.`);
        router.back();
      } catch (error: any) {
        const msg =
          error.response?.data?.message ||
          error.response?.data?.error ||
          'Could not schedule the meeting. Make sure a shared goal is set for the mentorship.';
        Alert.alert('Error', msg);
      } finally {
        setSubmitting(false);
      }
      return;
    }

    // Non-meeting modes: no backend endpoint, show confirmation
    Alert.alert(
      'Request Sent',
      `${config.screenTitle} has been sent to ${targetName || targetType || 'the user'}.`
    );
    router.back();
  };

  return (
    <View style={styles.container}>
      <ScrollView contentContainerStyle={styles.scrollContent} showsVerticalScrollIndicator={false}>
        <View style={styles.header}>
          <Text style={styles.statusText}>
            {new Date().toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit', hour12: false })}
          </Text>
          <TouchableOpacity onPress={() => router.back()}>
            <Text style={styles.backText}>‹ Back</Text>
          </TouchableOpacity>

          <Text style={styles.title}>{config.screenTitle}</Text>
          <Text style={styles.subtitle}>
            {isMeetingMode
              ? `Schedule a meeting with ${targetName || 'your connection'}.`
              : `Send a request regarding your connection with ${targetName || 'this user'}.`}
          </Text>
        </View>

        <View style={styles.formCard}>
          <Text style={styles.label}>TITLE</Text>
          <TextInput
            style={styles.input}
            value={title}
            onChangeText={setTitle}
            placeholder={isMeetingMode ? 'e.g. Weekly Sync' : 'Request title'}
            placeholderTextColor="#B5ADA3"
          />

          {isMeetingMode ? (
            <>
              <Text style={styles.label}>START TIME</Text>
              <TextInput
                style={styles.input}
                value={startTime}
                onChangeText={setStartTime}
                placeholder="e.g. 2026-05-20 10:00"
                placeholderTextColor="#B5ADA3"
              />

              <Text style={styles.label}>END TIME (optional, defaults to +1h)</Text>
              <TextInput
                style={styles.input}
                value={endTime}
                onChangeText={setEndTime}
                placeholder="e.g. 2026-05-20 11:00"
                placeholderTextColor="#B5ADA3"
              />

              <Text style={styles.label}>MEETING TYPE</Text>
              <View style={styles.toggleRow}>
                <TouchableOpacity
                  style={[styles.toggleOption, meetingType === 'ONLINE' && styles.toggleOptionActive]}
                  onPress={() => setMeetingType('ONLINE')}
                >
                  <Text style={[styles.toggleOptionText, meetingType === 'ONLINE' && styles.toggleOptionTextActive]}>
                    Online
                  </Text>
                </TouchableOpacity>
                <TouchableOpacity
                  style={[styles.toggleOption, meetingType === 'IN_PERSON' && styles.toggleOptionActive]}
                  onPress={() => setMeetingType('IN_PERSON')}
                >
                  <Text style={[styles.toggleOptionText, meetingType === 'IN_PERSON' && styles.toggleOptionTextActive]}>
                    In Person
                  </Text>
                </TouchableOpacity>
              </View>

              {meetingType === 'ONLINE' && (
                <>
                  <Text style={styles.label}>MEETING LINK</Text>
                  <TextInput
                    style={styles.input}
                    value={meetingLink}
                    onChangeText={setMeetingLink}
                    placeholder="https://meet.google.com/..."
                    placeholderTextColor="#B5ADA3"
                    autoCapitalize="none"
                    keyboardType="url"
                  />
                </>
              )}
            </>
          ) : (
            <>
              <Text style={styles.label}>DATE / SLOT</Text>
              <TextInput
                style={styles.input}
                value={startTime}
                onChangeText={setStartTime}
                placeholder="Preferred date / time"
                placeholderTextColor="#B5ADA3"
              />
            </>
          )}

          <Text style={styles.label}>DETAILS</Text>
          <TextInput
            style={[styles.input, styles.bigInput]}
            value={details}
            onChangeText={setDetails}
            placeholder={isMeetingMode ? 'Optional meeting notes or agenda' : 'Write details here'}
            placeholderTextColor="#B5ADA3"
            multiline
            textAlignVertical="top"
          />
        </View>

        <TouchableOpacity
          style={[styles.primaryButton, submitting && { opacity: 0.6 }]}
          onPress={handleSubmit}
          disabled={submitting}
        >
          {submitting ? (
            <ActivityIndicator color="#F8F6F2" />
          ) : (
            <Text style={styles.primaryButtonText}>{config.buttonText}</Text>
          )}
        </TouchableOpacity>
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#ECE8E1' },
  scrollContent: { padding: 24, paddingTop: 54, paddingBottom: 36 },
  header: { marginBottom: 20 },
  statusText: { color: '#456B50', fontSize: 16, fontWeight: '700', marginBottom: 14 },
  backText: { color: '#456B50', fontSize: 18, fontWeight: '700', marginBottom: 14 },
  title: { color: '#23372B', fontSize: 30, fontWeight: '700', marginBottom: 8 },
  subtitle: { color: '#8B8176', fontSize: 14, lineHeight: 21 },
  formCard: { backgroundColor: '#F8F6F2', borderRadius: 26, padding: 22, marginBottom: 20 },
  label: { color: '#7E7368', fontSize: 12, fontWeight: '700', marginBottom: 10 },
  input: {
    height: 64,
    borderRadius: 20,
    borderWidth: 1.5,
    borderColor: '#D8CEC0',
    backgroundColor: '#FCFBF8',
    paddingHorizontal: 18,
    fontSize: 16,
    color: '#4A4138',
    marginBottom: 22,
  },
  bigInput: { height: 130, paddingTop: 18, marginBottom: 0 },
  toggleRow: { flexDirection: 'row', gap: 10, marginBottom: 22 },
  toggleOption: {
    flex: 1,
    paddingVertical: 14,
    borderRadius: 18,
    borderWidth: 1.5,
    borderColor: '#D8CEC0',
    backgroundColor: '#FCFBF8',
    alignItems: 'center',
  },
  toggleOptionActive: { backgroundColor: '#456B50', borderColor: '#456B50' },
  toggleOptionText: { fontSize: 15, fontWeight: '700', color: '#7E7368' },
  toggleOptionTextActive: { color: '#F8F6F2' },
  primaryButton: {
    backgroundColor: '#4B7B57',
    borderRadius: 24,
    paddingVertical: 18,
    alignItems: 'center',
    minHeight: 58,
    justifyContent: 'center',
  },
  primaryButtonText: { color: '#F8F6F2', fontSize: 16, fontWeight: '700' },
});
