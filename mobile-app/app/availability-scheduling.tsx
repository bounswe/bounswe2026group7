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
import { useRole } from '../components/RoleContext';

type TimeSlot = { start: string; end: string };

type DayItem = {
  key: string;
  short: string;
  active: boolean;
  slots: TimeSlot[];
};

const TIME_OPTIONS = [
  '08:00', '09:00', '10:00', '11:00', '12:00',
  '13:00', '14:00', '15:00', '16:00', '17:00',
  '18:00', '19:00', '20:00',
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
  { key: 'mon', short: 'Mon', active: false, slots: [] },
  { key: 'tue', short: 'Tue', active: false, slots: [] },
  { key: 'wed', short: 'Wed', active: false, slots: [] },
  { key: 'thu', short: 'Thu', active: false, slots: [] },
  { key: 'fri', short: 'Fri', active: false, slots: [] },
  { key: 'sat', short: 'Sat', active: false, slots: [] },
  { key: 'sun', short: 'Sun', active: false, slots: [] },
];

const DEFAULT_SLOT: TimeSlot = { start: '09:00', end: '18:00' };

export default function AvailabilitySchedulingScreen() {
  const { role } = useRole();
  const isMentor = role === 'mentor';
  const [days, setDays] = useState<DayItem[]>(DEFAULT_DAYS);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [bannerMessage, setBannerMessage] = useState<string | null>(null);
  const [bannerTone, setBannerTone] = useState<'success' | 'error' | null>(null);

  useEffect(() => {
    const loadAvailability = async () => {
      try {
        let apiSlots: any[];
        if (isMentor) {
          const userId = await SecureStore.getItemAsync('userId');
          if (!userId) {
            setBannerTone('error');
            setBannerMessage('Could not identify your account. Please sign in again.');
            setLoading(false);
            return;
          }
          const res = await apiClient.get(`/availability/${userId}`);
          apiSlots = res.data;
        } else {
          const res = await apiClient.get('/mentee-availability');
          apiSlots = res.data;
        }

        setDays((prev) =>
          prev.map((day) => {
            const apiDay = DAY_KEY_TO_API[day.key];
            const daySlots = apiSlots
              .filter((s) => s.dayOfWeek === apiDay)
              .map((s) => ({
                start: s.startTime.substring(0, 5),
                end: s.endTime.substring(0, 5),
              }));
            if (daySlots.length > 0) {
              return { ...day, active: true, slots: daySlots };
            }
            return { ...day, active: false, slots: [] };
          })
        );
      } catch (err) {
        console.error('Availability load error:', err);
        setBannerTone('error');
        setBannerMessage('Could not load your current availability.');
      } finally {
        setLoading(false);
      }
    };
    loadAvailability();
  }, [isMentor]);

  const toggleDay = (key: string) => {
    setBannerMessage(null);
    setBannerTone(null);
    setDays((prev) =>
      prev.map((day) =>
        day.key === key
          ? { ...day, active: !day.active, slots: !day.active ? [{ ...DEFAULT_SLOT }] : [] }
          : day
      )
    );
  };

  const addSlot = (key: string) => {
    setBannerMessage(null);
    setBannerTone(null);
    setDays((prev) =>
      prev.map((day) =>
        day.key === key && day.active
          ? { ...day, slots: [...day.slots, { ...DEFAULT_SLOT }] }
          : day
      )
    );
  };

  const removeSlot = (key: string, index: number) => {
    setBannerMessage(null);
    setBannerTone(null);
    setDays((prev) =>
      prev.map((day) => {
        if (day.key !== key) return day;
        const newSlots = day.slots.filter((_, i) => i !== index);
        if (newSlots.length === 0) {
          return { ...day, active: false, slots: [] };
        }
        return { ...day, slots: newSlots };
      })
    );
  };

  const cycleTime = (current: string, type: 'start' | 'end') => {
    const fallback = type === 'start' ? '09:00' : '18:00';
    const value = current || fallback;
    const idx = TIME_OPTIONS.indexOf(value);
    return TIME_OPTIONS[idx === -1 ? 0 : (idx + 1) % TIME_OPTIONS.length];
  };

  const updateSlotTime = (key: string, index: number, type: 'start' | 'end') => {
    setBannerMessage(null);
    setBannerTone(null);
    setDays((prev) =>
      prev.map((day) => {
        if (day.key !== key || !day.active) return day;
        const newSlots = day.slots.map((slot, i) => {
          if (i !== index) return slot;
          return { ...slot, [type]: cycleTime(slot[type], type) };
        });
        return { ...day, slots: newSlots };
      })
    );
  };

  const handleUpdateAvailability = async () => {
    const activeDays = days.filter((d) => d.active && d.slots.length > 0);
    if (activeDays.length === 0) {
      Alert.alert('No Availability Selected', 'Please enable at least one time slot before saving.');
      return;
    }

    for (const day of activeDays) {
      for (const slot of day.slots) {
        const si = TIME_OPTIONS.indexOf(slot.start);
        const ei = TIME_OPTIONS.indexOf(slot.end);
        if (si >= ei) {
          Alert.alert('Invalid Time Slot', `For ${day.short}, the end time must be after the start time.`);
          return;
        }
      }
    }

    const slots = days.flatMap((day) =>
      day.active
        ? day.slots.map((slot) => ({
            dayOfWeek: DAY_KEY_TO_API[day.key],
            startTime: slot.start,
            endTime: slot.end,
            recurring: true,
          }))
        : []
    );

    setSaving(true);
    setBannerMessage(null);
    setBannerTone(null);
    try {
      const endpoint = isMentor ? '/availability' : '/mentee-availability';
      await apiClient.put(endpoint, { slots });
      setBannerTone('success');
      setBannerMessage('Availability successfully updated and reflected on your profile.');
    } catch (error: any) {
      const msg = error.response?.data?.message || 'Could not save availability.';
      setBannerTone('error');
      setBannerMessage(msg);
      Alert.alert('Error', msg);
    } finally {
      setSaving(false);
    }
  };

  const totalSlots = days.reduce((sum, d) => sum + (d.active ? d.slots.length : 0), 0);

  return (
    <View style={styles.container}>
      <View style={styles.fixedHeader}>
        <View style={styles.topCircle} />

        <View style={styles.statusRow}>
          <Text style={styles.statusText}>{new Date().toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit', hour12: false })}</Text>
          <Text style={styles.statusIcons}>▲ ▮</Text>
        </View>

        <View style={styles.headerTopRow}>
          <TouchableOpacity style={styles.backButton} onPress={() => router.back()}>
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

      <ScrollView
        style={styles.scrollArea}
        contentContainerStyle={styles.scrollContent}
        showsVerticalScrollIndicator={false}
      >
        {loading ? (
          <ActivityIndicator size="large" color="#456B50" style={styles.loader} />
        ) : (
          <>
            {bannerMessage ? (
              <View style={[styles.banner, bannerTone === 'success' ? styles.bannerSuccess : styles.bannerError]}>
                <Text style={[styles.bannerText, bannerTone === 'success' ? styles.bannerTextSuccess : styles.bannerTextError]}>
                  {bannerMessage}
                </Text>
              </View>
            ) : null}

            <View style={styles.summaryCard}>
              <Text style={styles.summaryLabel}>ACTIVE SLOTS</Text>
              <Text style={styles.summaryValue}>{totalSlots}</Text>
              <Text style={styles.summaryHint}>
                Tap a day to enable it. Use + to add multiple slots per day, and tap times to cycle through hours.
              </Text>
            </View>

            <Text style={styles.sectionTitle}>WEEKLY SCHEDULE</Text>

            <View style={styles.scheduleCard}>
              {days.map((day, dayIndex) => (
                <View key={day.key}>
                  <View style={styles.dayBlock}>
                    <View style={styles.dayHeaderRow}>
                      <TouchableOpacity
                        activeOpacity={0.82}
                        onPress={() => toggleDay(day.key)}
                        style={[styles.dayCircle, day.active ? styles.dayCircleActive : styles.dayCircleInactive]}
                      >
                        <Text style={[styles.dayCircleText, day.active ? styles.dayCircleTextActive : styles.dayCircleTextInactive]}>
                          {day.short}
                        </Text>
                      </TouchableOpacity>

                      <View style={styles.dayHeaderMiddle}>
                        {!day.active && (
                          <Text style={styles.notAvailableText}>Not available</Text>
                        )}
                        {day.active && (
                          <TouchableOpacity style={styles.addSlotButton} onPress={() => addSlot(day.key)}>
                            <Text style={styles.addSlotText}>+ Add slot</Text>
                          </TouchableOpacity>
                        )}
                      </View>

                      <TouchableOpacity
                        style={[styles.toggleTrack, day.active ? styles.toggleTrackActive : styles.toggleTrackInactive]}
                        onPress={() => toggleDay(day.key)}
                        activeOpacity={0.8}
                      >
                        <View style={[styles.toggleThumb, day.active ? styles.toggleThumbRight : styles.toggleThumbLeft]} />
                      </TouchableOpacity>
                    </View>

                    {day.active && day.slots.map((slot, slotIndex) => (
                      <View key={slotIndex} style={styles.slotRow}>
                        <TouchableOpacity
                          style={styles.timeBadge}
                          onPress={() => updateSlotTime(day.key, slotIndex, 'start')}
                          activeOpacity={0.8}
                        >
                          <Text style={styles.timeBadgeText}>{slot.start}</Text>
                        </TouchableOpacity>

                        <Text style={styles.hyphen}>–</Text>

                        <TouchableOpacity
                          style={styles.timeBadge}
                          onPress={() => updateSlotTime(day.key, slotIndex, 'end')}
                          activeOpacity={0.8}
                        >
                          <Text style={styles.timeBadgeText}>{slot.end}</Text>
                        </TouchableOpacity>

                        <TouchableOpacity style={styles.removeSlotButton} onPress={() => removeSlot(day.key, slotIndex)}>
                          <Text style={styles.removeSlotText}>✕</Text>
                        </TouchableOpacity>
                      </View>
                    ))}
                  </View>

                  {dayIndex !== days.length - 1 && <View style={styles.rowDivider} />}
                </View>
              ))}
            </View>

            <Text style={styles.helperText}>
              Removing all slots for a day disables it. The backend rejects overlapping or reversed time ranges.
            </Text>

            <TouchableOpacity
              style={[styles.updateButton, saving && { opacity: 0.6 }]}
              onPress={handleUpdateAvailability}
              disabled={saving}
            >
              <Text style={styles.updateButtonText}>
                {saving ? 'Saving...' : 'Update Availability'}
              </Text>
            </TouchableOpacity>
          </>
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
  statusRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  statusText: { color: '#FFFFFF', fontSize: 16, fontWeight: '700' },
  statusIcons: { color: '#FFFFFF', fontSize: 18, fontWeight: '700' },
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
  backButtonText: { color: '#F7F4EE', fontSize: 14, fontWeight: '700' },
  headerMainRow: { flexDirection: 'row', justifyContent: 'flex-start', alignItems: 'flex-end', marginTop: 6 },
  title: { color: '#F7F4EE', fontSize: 34, lineHeight: 38, fontWeight: '700' },
  titleItalic: { fontStyle: 'italic', fontWeight: '700' },
  saveText: { color: '#BFE2C8', fontSize: 16, fontWeight: '700' },
  scrollArea: { flex: 1, backgroundColor: '#ECE8E1' },
  scrollContent: { paddingHorizontal: 24, paddingTop: 22, paddingBottom: 36 },
  loader: { marginTop: 50 },
  banner: { borderRadius: 20, paddingHorizontal: 16, paddingVertical: 14, marginBottom: 16 },
  bannerSuccess: { backgroundColor: '#E6F2E8', borderWidth: 1, borderColor: '#BFD9C4' },
  bannerError: { backgroundColor: '#F6E6E2', borderWidth: 1, borderColor: '#E7C3BA' },
  bannerText: { fontSize: 14, fontWeight: '600', lineHeight: 20 },
  bannerTextSuccess: { color: '#2F563C' },
  bannerTextError: { color: '#8C3E35' },
  summaryCard: { backgroundColor: '#F8F6F2', borderRadius: 26, padding: 20, marginBottom: 18 },
  summaryLabel: { color: '#8B8176', fontSize: 12, fontWeight: '700', letterSpacing: 1.5, marginBottom: 6 },
  summaryValue: { color: '#23372B', fontSize: 28, fontWeight: '800', marginBottom: 8 },
  summaryHint: { color: '#7C7267', fontSize: 14, lineHeight: 20, fontWeight: '500' },
  sectionTitle: { color: '#8B8176', fontSize: 13, fontWeight: '700', letterSpacing: 2, marginBottom: 16 },
  scheduleCard: {
    backgroundColor: '#F8F6F2',
    borderRadius: 26,
    paddingHorizontal: 16,
    paddingVertical: 14,
    marginBottom: 12,
  },
  dayBlock: { paddingVertical: 4 },
  dayHeaderRow: { flexDirection: 'row', alignItems: 'center', minHeight: 64 },
  dayCircle: {
    width: 54,
    height: 54,
    borderRadius: 27,
    justifyContent: 'center',
    alignItems: 'center',
    marginRight: 12,
    flexShrink: 0,
  },
  dayCircleActive: { backgroundColor: '#3F7653' },
  dayCircleInactive: { backgroundColor: '#E8E2D9' },
  dayCircleText: { fontSize: 14, fontWeight: '700' },
  dayCircleTextActive: { color: '#F7F4EE' },
  dayCircleTextInactive: { color: '#B5ADA3' },
  dayHeaderMiddle: {
    flex: 1,
    alignSelf: 'stretch',
    alignItems: 'center',
    justifyContent: 'center',
  },
  notAvailableText: { color: '#B5ADA3', fontSize: 14, fontWeight: '500', flex: 1 },
  addSlotButton: {
    backgroundColor: '#D6E8DC',
    borderRadius: 14,
    paddingHorizontal: 16,
    paddingVertical: 8,
  },
  addSlotText: { color: '#2F563C', fontSize: 13, fontWeight: '700' },
  toggleTrack: {
    width: 46,
    height: 28,
    borderRadius: 14,
    justifyContent: 'center',
    paddingHorizontal: 3,
    flexShrink: 0,
  },
  toggleTrackActive: { backgroundColor: '#3F7653' },
  toggleTrackInactive: { backgroundColor: '#DDDBD7' },
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
  toggleThumbLeft: { left: 3 },
  toggleThumbRight: { right: 3 },
  slotRow: {
    flexDirection: 'row',
    alignItems: 'center',
    marginTop: 8,
    marginLeft: 66,
    gap: 8,
  },
  timeBadge: {
    backgroundColor: '#D6E8DC',
    borderRadius: 14,
    paddingHorizontal: 12,
    paddingVertical: 9,
    minWidth: 68,
    alignItems: 'center',
  },
  timeBadgeText: { color: '#2F563C', fontSize: 14, fontWeight: '700' },
  hyphen: { color: '#B5ADA3', fontSize: 20, fontWeight: '400' },
  removeSlotButton: {
    width: 28,
    height: 28,
    borderRadius: 14,
    backgroundColor: '#F0E8E4',
    justifyContent: 'center',
    alignItems: 'center',
    marginLeft: 4,
  },
  removeSlotText: { color: '#A0544A', fontSize: 12, fontWeight: '700' },
  rowDivider: { height: 1, backgroundColor: '#E5DED4', marginVertical: 8 },
  helperText: { color: '#9A8F82', fontSize: 13, marginBottom: 22, marginLeft: 4, lineHeight: 18 },
  updateButton: { backgroundColor: '#467853', borderRadius: 24, paddingVertical: 22, alignItems: 'center' },
  updateButtonText: { color: '#F8F6F2', fontSize: 17, fontWeight: '700' },
});
