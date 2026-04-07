import React, { useState, useEffect } from 'react';
import { router } from 'expo-router';
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  ScrollView,
  Alert,
  ActivityIndicator,
} from 'react-native';
import * as SecureStore from 'expo-secure-store';
import apiClient from '../api/client';

type DayItem = {
  key: string;
  short: string;
  active: boolean;
  start?: string;
  end?: string;
};

const TIME_OPTIONS = [
  '08:00',
  '09:00',
  '10:00',
  '11:00',
  '12:00',
  '13:00',
  '14:00',
  '15:00',
  '16:00',
  '17:00',
  '18:00',
  '19:00',
  '20:00',
];

const DAY_KEY_TO_API: Record<string, string> = {
  mon: 'MONDAY',
  tue: 'TUESDAY',
  wed: 'WEDNESDAY',
  thu: 'THURSDAY',
  fri: 'FRIDAY',
  sat: 'SATURDAY',
  sun: 'SUNDAY',
};


const DEFAULT_DAYS: DayItem[] = [
  { key: 'mon', short: 'Mon', active: false },
  { key: 'tue', short: 'Tue', active: false },
  { key: 'wed', short: 'Wed', active: false },
  { key: 'thu', short: 'Thu', active: false },
  { key: 'fri', short: 'Fri', active: false },
  { key: 'sat', short: 'Sat', active: false },
  { key: 'sun', short: 'Sun', active: false },
];

export default function AvailabilitySchedulingScreen() {
  const [days, setDays] = useState<DayItem[]>(DEFAULT_DAYS);
  const [selectedDuration, setSelectedDuration] = useState('60 min');
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    const loadAvailability = async () => {
      try {
        const userId = await SecureStore.getItemAsync('userId');
        if (!userId) return;
        const res = await apiClient.get(`/availability/${userId}`);
        const slots: any[] = res.data;

        setDays((prev) =>
          prev.map((day) => {
            const apiDay = DAY_KEY_TO_API[day.key];
            const slot = slots.find((s) => s.dayOfWeek === apiDay);
            if (slot) {
              return {
                ...day,
                active: true,
                start: slot.startTime.substring(0, 5),
                end: slot.endTime.substring(0, 5),
              };
            }
            return { ...day, active: false, start: undefined, end: undefined };
          })
        );
      } catch (err) {
        console.error('Availability load error:', err);
      } finally {
        setLoading(false);
      }
    };
    loadAvailability();
  }, []);

  const toggleDay = (key: string) => {
    setDays((prev) =>
      prev.map((day) =>
        day.key === key
          ? {
              ...day,
              active: !day.active,
              start: !day.active ? '09:00' : undefined,
              end: !day.active ? '18:00' : undefined,
            }
          : day
      )
    );
  };

  const getNextTime = (current: string | undefined, type: 'start' | 'end') => {
    const fallback = type === 'start' ? '09:00' : '18:00';
    const value = current ?? fallback;
    const currentIndex = TIME_OPTIONS.indexOf(value);
    const nextIndex =
      currentIndex === -1 ? 0 : (currentIndex + 1) % TIME_OPTIONS.length;
    return TIME_OPTIONS[nextIndex];
  };

  const updateTime = (key: string, type: 'start' | 'end') => {
    setDays((prev) =>
      prev.map((day) => {
        if (day.key !== key || !day.active) return day;

        const nextValue = getNextTime(
          type === 'start' ? day.start : day.end,
          type
        );

        if (type === 'start') {
          return { ...day, start: nextValue };
        }

        return { ...day, end: nextValue };
      })
    );
  };

  const handleUpdateAvailability = async () => {
    const invalidDay = days.find((day) => {
      if (!day.active || !day.start || !day.end) return false;
      const startIndex = TIME_OPTIONS.indexOf(day.start);
      const endIndex = TIME_OPTIONS.indexOf(day.end);
      return startIndex >= endIndex;
    });

    if (invalidDay) {
      Alert.alert('Invalid Time Slot', `For ${invalidDay.short}, the end time must be after the start time.`);
      return;
    }

    const slots = days
      .filter((day) => day.active && day.start && day.end)
      .map((day) => ({
        dayOfWeek: DAY_KEY_TO_API[day.key],
        startTime: day.start,
        endTime: day.end,
        recurring: true,
      }));

    setSaving(true);
    try {
      await apiClient.put('/availability', { slots });
      Alert.alert('Success', 'Availability successfully updated!');
    } catch (error: any) {
      const msg = error.response?.data?.message || 'Could not save availability.';
      Alert.alert('Error', msg);
    } finally {
      setSaving(false);
    }
  };

  const durations = ['30 min', '45 min', '60 min', '90 min'];

  return (
    <View style={styles.container}>
      <View style={styles.fixedHeader}>
        <View style={styles.topCircle} />

        <View style={styles.statusRow}>
          <Text style={styles.statusText}>{new Date().toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit', hour12: false })}</Text>
          <Text style={styles.statusIcons}>▲ ▮</Text>
        </View>

        <View style={styles.headerTopRow}>
          <TouchableOpacity
            style={styles.backButton}
            onPress={() => router.back()}
          >
            <Text style={styles.backButtonText}>‹ Back</Text>
          </TouchableOpacity>

          <TouchableOpacity onPress={handleUpdateAvailability} disabled={saving}>
            <Text style={[styles.saveText, saving && { opacity: 0.5 }]}>
              {saving ? 'Saving...' : 'Save'}
            </Text>
          </TouchableOpacity>
        </View>

        <View style={styles.headerMainRow}>
          <Text style={styles.title}>
            Your{'\n'}
            <Text style={styles.titleItalic}>Availability.</Text>
          </Text>
        </View>
      </View>

      {loading ? (
        <ActivityIndicator size="large" color="#456B50" style={{ marginTop: 50 }} />
      ) : null}

      <ScrollView
        style={styles.scrollArea}
        contentContainerStyle={styles.scrollContent}
        showsVerticalScrollIndicator={false}
      >
        <Text style={styles.sectionTitle}>WEEKLY SCHEDULE</Text>

        <View style={styles.scheduleCard}>
          {days.map((day, index) => (
            <View key={day.key}>
              <View style={styles.dayRow}>
                <View
                  style={[
                    styles.dayCircle,
                    day.active ? styles.dayCircleActive : styles.dayCircleInactive,
                  ]}
                >
                  <Text
                    style={[
                      styles.dayCircleText,
                      day.active
                        ? styles.dayCircleTextActive
                        : styles.dayCircleTextInactive,
                    ]}
                  >
                    {day.short}
                  </Text>
                </View>

                <View style={styles.timeArea}>
                  {day.active ? (
                    <View style={styles.timeRow}>
                      <TouchableOpacity
                        style={styles.timeBadge}
                        onPress={() => updateTime(day.key, 'start')}
                        activeOpacity={0.8}
                      >
                        <Text style={styles.timeBadgeText}>{day.start}</Text>
                      </TouchableOpacity>

                      <Text style={styles.hyphen}>–</Text>

                      <TouchableOpacity
                        style={styles.timeBadge}
                        onPress={() => updateTime(day.key, 'end')}
                        activeOpacity={0.8}
                      >
                        <Text style={styles.timeBadgeText}>{day.end}</Text>
                      </TouchableOpacity>
                    </View>
                  ) : (
                    <Text style={styles.notAvailableText}>Not available</Text>
                  )}
                </View>

                <TouchableOpacity
                  style={[
                    styles.toggleTrack,
                    day.active ? styles.toggleTrackActive : styles.toggleTrackInactive,
                  ]}
                  onPress={() => toggleDay(day.key)}
                  activeOpacity={0.8}
                >
                  <View
                    style={[
                      styles.toggleThumb,
                      day.active ? styles.toggleThumbRight : styles.toggleThumbLeft,
                    ]}
                  />
                </TouchableOpacity>
              </View>

              {index !== days.length - 1 && <View style={styles.rowDivider} />}
            </View>
          ))}
        </View>

        <Text style={styles.helperText}>
          Tap the time boxes to change available hours.
        </Text>

        <Text style={styles.sectionTitle}>SESSION DURATION</Text>

        <View style={styles.durationCard}>
          <View style={styles.durationRow}>
            {durations.map((duration) => {
              const selected = selectedDuration === duration;

              return (
                <TouchableOpacity
                  key={duration}
                  style={[
                    styles.durationButton,
                    selected && styles.durationButtonSelected,
                  ]}
                  onPress={() => setSelectedDuration(duration)}
                >
                  <Text
                    style={[
                      styles.durationButtonText,
                      selected && styles.durationButtonTextSelected,
                    ]}
                  >
                    {duration}
                  </Text>
                </TouchableOpacity>
              );
            })}
          </View>
        </View>

        <TouchableOpacity
          style={[styles.updateButton, saving && { opacity: 0.6 }]}
          onPress={handleUpdateAvailability}
          disabled={saving}
        >
          <Text style={styles.updateButtonText}>
            {saving ? 'Saving...' : 'Update Availability'}
          </Text>
        </TouchableOpacity>
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
    paddingBottom: 22,
    overflow: 'hidden',
  },
  topCircle: {
    position: 'absolute',
    width: 300,
    height: 300,
    borderRadius: 150,
    backgroundColor: 'rgba(255,255,255,0.05)',
    top: -30,
    left: -40,
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
  headerMainRow: {
    flexDirection: 'row',
    justifyContent: 'flex-start',
    alignItems: 'flex-end',
    marginTop: 6,
  },
  title: {
    color: '#F7F4EE',
    fontSize: 34,
    lineHeight: 38,
    fontWeight: '700',
  },
  titleItalic: {
    fontStyle: 'italic',
    fontWeight: '700',
  },
  saveText: {
    color: '#BFE2C8',
    fontSize: 16,
    fontWeight: '700',
  },
  scrollArea: {
    flex: 1,
    backgroundColor: '#ECE8E1',
  },
  scrollContent: {
    paddingHorizontal: 24,
    paddingTop: 22,
    paddingBottom: 36,
  },
  sectionTitle: {
    color: '#8B8176',
    fontSize: 13,
    fontWeight: '700',
    letterSpacing: 2,
    marginBottom: 16,
  },
  scheduleCard: {
    backgroundColor: '#F8F6F2',
    borderRadius: 26,
    paddingHorizontal: 16,
    paddingVertical: 14,
    marginBottom: 12,
  },
  dayRow: {
    flexDirection: 'row',
    alignItems: 'center',
    minHeight: 72,
  },
  dayCircle: {
    width: 58,
    height: 58,
    borderRadius: 29,
    justifyContent: 'center',
    alignItems: 'center',
    marginRight: 12,
    flexShrink: 0,
  },
  dayCircleActive: {
    backgroundColor: '#3F7653',
  },
  dayCircleInactive: {
    backgroundColor: '#E8E2D9',
  },
  dayCircleText: {
    fontSize: 15,
    fontWeight: '700',
  },
  dayCircleTextActive: {
    color: '#F7F4EE',
  },
  dayCircleTextInactive: {
    color: '#B5ADA3',
  },
  timeArea: {
    flex: 1,
    marginRight: 8,
  },
  timeRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'flex-start',
  },
  timeBadge: {
    backgroundColor: '#D6E8DC',
    borderRadius: 14,
    paddingHorizontal: 10,
    paddingVertical: 10,
    minWidth: 74,
    alignItems: 'center',
  },
  timeBadgeText: {
    color: '#2F563C',
    fontSize: 14,
    fontWeight: '700',
  },
  hyphen: {
    color: '#B5ADA3',
    fontSize: 22,
    marginHorizontal: 8,
    fontWeight: '400',
  },
  notAvailableText: {
    color: '#B5ADA3',
    fontSize: 14,
    fontWeight: '500',
  },
  toggleTrack: {
    width: 46,
    height: 28,
    borderRadius: 14,
    justifyContent: 'center',
    paddingHorizontal: 3,
    flexShrink: 0,
    marginLeft: 0,
  },
  toggleTrackActive: {
    backgroundColor: '#3F7653',
  },
  toggleTrackInactive: {
    backgroundColor: '#DDDBD7',
  },
  toggleThumb: {
    width: 22,
    height: 22,
    borderRadius: 11,
    backgroundColor: '#F8F6F2',
    position: 'absolute',
    top: 3,
    shadowColor: '#000',
    shadowOpacity: 0.12,
    shadowRadius: 4,
    shadowOffset: { width: 0, height: 1 },
    elevation: 1,
  },
  toggleThumbLeft: {
    left: 3,
  },
  toggleThumbRight: {
    right: 3,
  },
  rowDivider: {
    height: 1,
    backgroundColor: '#E5DED4',
    marginVertical: 10,
  },
  helperText: {
    color: '#9A8F82',
    fontSize: 13,
    marginBottom: 22,
    marginLeft: 4,
  },
  durationCard: {
    backgroundColor: '#F8F6F2',
    borderRadius: 26,
    padding: 18,
    marginBottom: 26,
  },
  durationRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    gap: 10,
  },
  durationButton: {
    flex: 1,
    borderRadius: 18,
    borderWidth: 1.5,
    borderColor: '#E1D7CA',
    backgroundColor: '#FCFBF8',
    paddingVertical: 18,
    alignItems: 'center',
  },
  durationButtonSelected: {
    borderColor: '#3F7653',
    backgroundColor: '#E8F1EA',
  },
  durationButtonText: {
    color: '#A29689',
    fontSize: 14,
    fontWeight: '700',
  },
  durationButtonTextSelected: {
    color: '#2F563C',
  },
  updateButton: {
    backgroundColor: '#467853',
    borderRadius: 24,
    paddingVertical: 22,
    alignItems: 'center',
  },
  updateButtonText: {
    color: '#F8F6F2',
    fontSize: 17,
    fontWeight: '700',
  },
});
