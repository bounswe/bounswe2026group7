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
  slots: AvailabilitySlotItem[];
};

type AvailabilitySlotItem = {
  id: string;
  start: string;
  end: string;
};

type ApiAvailabilitySlot = {
  id?: number;
  dayOfWeek: string;
  startTime: string;
  endTime: string;
  recurring?: boolean;
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

const API_DAY_TO_KEY = Object.fromEntries(
  Object.entries(DAY_KEY_TO_API).map(([key, value]) => [value, key])
) as Record<string, string>;

const DEFAULT_DAYS: DayItem[] = [
  { key: 'mon', short: 'Mon', slots: [] },
  { key: 'tue', short: 'Tue', slots: [] },
  { key: 'wed', short: 'Wed', slots: [] },
  { key: 'thu', short: 'Thu', slots: [] },
  { key: 'fri', short: 'Fri', slots: [] },
  { key: 'sat', short: 'Sat', slots: [] },
  { key: 'sun', short: 'Sun', slots: [] },
];

const createSlot = (dayKey: string, start = '09:00', end = '10:00'): AvailabilitySlotItem => ({
  id: `${dayKey}-${Date.now()}-${Math.random().toString(36).slice(2)}`,
  start,
  end,
});

const normalizeTime = (value: string) => value.substring(0, 5);

const mapApiSlotsToDays = (slots: ApiAvailabilitySlot[]) =>
  DEFAULT_DAYS.map((day) => {
    const daySlots = slots
      .filter((slot) => API_DAY_TO_KEY[slot.dayOfWeek] === day.key)
      .sort((a, b) => normalizeTime(a.startTime).localeCompare(normalizeTime(b.startTime)))
      .map((slot, index) => ({
        id: slot.id ? String(slot.id) : `${day.key}-loaded-${index}`,
        start: normalizeTime(slot.startTime),
        end: normalizeTime(slot.endTime),
      }));

    return { ...day, slots: daySlots };
  });

const hasOverlap = (slots: AvailabilitySlotItem[]) => {
  const sorted = [...slots].sort(
    (a, b) => TIME_OPTIONS.indexOf(a.start) - TIME_OPTIONS.indexOf(b.start)
  );

  return sorted.some((slot, index) => {
    const next = sorted[index + 1];
    if (!next) return false;
    return TIME_OPTIONS.indexOf(slot.end) > TIME_OPTIONS.indexOf(next.start);
  });
};

export default function AvailabilitySchedulingScreen() {
  const [days, setDays] = useState<DayItem[]>(DEFAULT_DAYS);
  const [selectedDuration, setSelectedDuration] = useState('60 min');
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [loadError, setLoadError] = useState('');

  useEffect(() => {
    const loadAvailability = async () => {
      try {
        setLoadError('');
        const userId = await SecureStore.getItemAsync('userId');
        if (!userId) {
          setLoadError('Could not find your session. Please log in again.');
          return;
        }
        const res = await apiClient.get(`/availability/${userId}`);
        const slots: ApiAvailabilitySlot[] = res.data;
        setDays(mapApiSlotsToDays(slots));
      } catch (error: any) {
        console.error('Availability load error:', error);
        setLoadError(error.response?.data?.message || 'Could not load availability.');
      } finally {
        setLoading(false);
      }
    };
    loadAvailability();
  }, []);

  const toggleDay = (key: string) => {
    setDays((prev) =>
      prev.map((day) => {
        if (day.key !== key) return day;
        return {
          ...day,
          slots: day.slots.length > 0 ? [] : [createSlot(day.key, '09:00', '18:00')],
        };
      })
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

  const addSlot = (key: string) => {
    setDays((prev) =>
      prev.map((day) => {
        if (day.key !== key) return day;

        const availableStart = TIME_OPTIONS.find((time, index) => {
          const end = TIME_OPTIONS[index + 1];
          if (!end) return false;
          return !hasOverlap([...day.slots, createSlot(day.key, time, end)]);
        });

        if (!availableStart) {
          Alert.alert('No Available Time', `There is no free one-hour slot left for ${day.short}.`);
          return day;
        }

        const startIndex = TIME_OPTIONS.indexOf(availableStart);
        return {
          ...day,
          slots: [...day.slots, createSlot(day.key, availableStart, TIME_OPTIONS[startIndex + 1])],
        };
      })
    );
  };

  const removeSlot = (key: string, slotId: string) => {
    setDays((prev) =>
      prev.map((day) =>
        day.key === key
          ? { ...day, slots: day.slots.filter((slot) => slot.id !== slotId) }
          : day
      )
    );
  };

  const updateTime = (key: string, slotId: string, type: 'start' | 'end') => {
    setDays((prev) =>
      prev.map((day) => {
        if (day.key !== key) return day;

        return {
          ...day,
          slots: day.slots.map((slot) => {
            if (slot.id !== slotId) return slot;
            const nextValue = getNextTime(type === 'start' ? slot.start : slot.end, type);
            return type === 'start' ? { ...slot, start: nextValue } : { ...slot, end: nextValue };
          }),
        };
      })
    );
  };

  const handleUpdateAvailability = async () => {
    const invalidDay = days.find((day) =>
      day.slots.some((slot) => TIME_OPTIONS.indexOf(slot.start) >= TIME_OPTIONS.indexOf(slot.end))
    );

    if (invalidDay) {
      Alert.alert('Invalid Time Slot', `For ${invalidDay.short}, the end time must be after the start time.`);
      return;
    }

    const overlappingDay = days.find((day) => hasOverlap(day.slots));

    if (overlappingDay) {
      Alert.alert('Overlapping Time Slots', `For ${overlappingDay.short}, availability slots cannot overlap.`);
      return;
    }

    const slots = days
      .flatMap((day) => day.slots.map((slot) => ({
        dayOfWeek: DAY_KEY_TO_API[day.key],
        startTime: slot.start,
        endTime: slot.end,
        recurring: true,
      })));

    setSaving(true);
    try {
      const res = await apiClient.put('/availability', { slots });
      setDays(mapApiSlotsToDays(res.data));
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

        {!!loadError && (
          <View style={styles.errorCard}>
            <Text style={styles.errorTitle}>Could not load availability</Text>
            <Text style={styles.errorText}>{loadError}</Text>
          </View>
        )}

        <View style={styles.scheduleCard}>
          {days.map((day, index) => (
            <View key={day.key}>
              <View style={styles.dayHeaderRow}>
                <View
                  style={[
                    styles.dayCircle,
                    day.slots.length > 0 ? styles.dayCircleActive : styles.dayCircleInactive,
                  ]}
                >
                  <Text
                    style={[
                      styles.dayCircleText,
                      day.slots.length > 0
                        ? styles.dayCircleTextActive
                        : styles.dayCircleTextInactive,
                    ]}
                  >
                    {day.short}
                  </Text>
                </View>

                <View style={styles.timeArea}>
                  <Text style={styles.dayTitle}>{day.short}</Text>
                  <Text style={styles.notAvailableText}>
                    {day.slots.length > 0
                      ? `${day.slots.length} slot${day.slots.length > 1 ? 's' : ''} available`
                      : 'Not available'}
                  </Text>
                </View>

                <TouchableOpacity
                  style={[
                    styles.toggleTrack,
                    day.slots.length > 0 ? styles.toggleTrackActive : styles.toggleTrackInactive,
                  ]}
                  onPress={() => toggleDay(day.key)}
                  activeOpacity={0.8}
                  accessibilityRole="switch"
                  accessibilityState={{ checked: day.slots.length > 0 }}
                  accessibilityLabel={`${day.short} availability`}
                >
                  <View
                    style={[
                      styles.toggleThumb,
                      day.slots.length > 0 ? styles.toggleThumbRight : styles.toggleThumbLeft,
                    ]}
                  />
                </TouchableOpacity>
              </View>

              {day.slots.length > 0 && (
                <View style={styles.slotsArea}>
                  {day.slots.map((slot) => (
                    <View key={slot.id} style={styles.slotRow}>
                      <View style={styles.timeRow}>
                        <TouchableOpacity
                          style={styles.timeBadge}
                          onPress={() => updateTime(day.key, slot.id, 'start')}
                          activeOpacity={0.8}
                          accessibilityRole="button"
                          accessibilityLabel={`Change ${day.short} slot start time`}
                        >
                          <Text style={styles.timeBadgeText}>{slot.start}</Text>
                        </TouchableOpacity>

                        <Text style={styles.hyphen}>–</Text>

                        <TouchableOpacity
                          style={styles.timeBadge}
                          onPress={() => updateTime(day.key, slot.id, 'end')}
                          activeOpacity={0.8}
                          accessibilityRole="button"
                          accessibilityLabel={`Change ${day.short} slot end time`}
                        >
                          <Text style={styles.timeBadgeText}>{slot.end}</Text>
                        </TouchableOpacity>
                      </View>

                      <TouchableOpacity
                        style={styles.removeSlotButton}
                        onPress={() => removeSlot(day.key, slot.id)}
                        accessibilityRole="button"
                        accessibilityLabel={`Remove ${day.short} availability slot`}
                      >
                        <Text style={styles.removeSlotText}>Remove</Text>
                      </TouchableOpacity>
                    </View>
                  ))}

                  <TouchableOpacity
                    style={styles.addSlotButton}
                    onPress={() => addSlot(day.key)}
                    accessibilityRole="button"
                    accessibilityLabel={`Add availability slot for ${day.short}`}
                  >
                    <Text style={styles.addSlotText}>+ Add time slot</Text>
                  </TouchableOpacity>
                </View>
              )}

              {index !== days.length - 1 && <View style={styles.rowDivider} />}
            </View>
          ))}
        </View>

        <Text style={styles.helperText}>
          Toggle a day to make yourself available. Tap time boxes to adjust hours, add more slots, or remove slots.
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
  errorCard: {
    backgroundColor: '#FDF0EF',
    borderWidth: 1,
    borderColor: '#F4C7C3',
    borderRadius: 20,
    padding: 16,
    marginBottom: 16,
  },
  errorTitle: {
    color: '#8A2F2A',
    fontSize: 15,
    fontWeight: '700',
    marginBottom: 6,
  },
  errorText: {
    color: '#A85E58',
    fontSize: 13,
    lineHeight: 18,
    fontWeight: '500',
  },
  dayHeaderRow: {
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
  dayTitle: {
    color: '#23372B',
    fontSize: 16,
    fontWeight: '700',
    marginBottom: 4,
  },
  slotsArea: {
    paddingLeft: 70,
    paddingBottom: 6,
    gap: 10,
  },
  slotRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 10,
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
  addSlotButton: {
    alignSelf: 'flex-start',
    backgroundColor: '#EEF5EF',
    borderRadius: 14,
    borderWidth: 1,
    borderColor: '#CFE2D2',
    paddingHorizontal: 12,
    paddingVertical: 10,
  },
  addSlotText: {
    color: '#2F563C',
    fontSize: 13,
    fontWeight: '700',
  },
  removeSlotButton: {
    backgroundColor: '#FDF0EF',
    borderRadius: 14,
    paddingHorizontal: 10,
    paddingVertical: 10,
  },
  removeSlotText: {
    color: '#B84A45',
    fontSize: 12,
    fontWeight: '700',
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
