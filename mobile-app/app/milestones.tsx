import { router, useLocalSearchParams } from 'expo-router';
import React, { useState, useEffect, useCallback } from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  TouchableOpacity,
  Modal,
  TextInput,
  Alert,
  ActivityIndicator,
} from 'react-native';
import { useRole } from '../components/RoleContext';
import apiClient from '../api/client';

type MilestoneStatus = 'PENDING' | 'IN_PROGRESS' | 'COMPLETED';

type ActionItem = {
  id: number;
  text: string;
  isCompleted: boolean;
};

type Milestone = {
  id: number;
  title: string;
  description?: string;
  targetDate?: string;
  status: MilestoneStatus;
  orderIndex: number;
  actionItems: ActionItem[];
  expanded: boolean;
};

function parseString(value: string | string[] | undefined) {
  return Array.isArray(value) ? value[0] : value ?? '';
}

const STATUS_COLORS: Record<MilestoneStatus, { bg: string; text: string }> = {
  PENDING: { bg: '#EEE9E3', text: '#7E7368' },
  IN_PROGRESS: { bg: '#D9EEE1', text: '#2F563C' },
  COMPLETED: { bg: '#D4EBD4', text: '#1E5C1E' },
};

const STATUS_LABELS: Record<MilestoneStatus, string> = {
  PENDING: 'Pending',
  IN_PROGRESS: 'In Progress',
  COMPLETED: 'Completed',
};

export default function MilestonesScreen() {
  const params = useLocalSearchParams();
  const { role } = useRole();
  const isMentor = role === 'mentor';

  const mentorshipId = parseString(params.mentorshipId);

  const [milestones, setMilestones] = useState<Milestone[]>([]);
  const [loading, setLoading] = useState(true);
  const [showAddModal, setShowAddModal] = useState(false);
  const [newTitle, setNewTitle] = useState('');
  const [newDescription, setNewDescription] = useState('');
  const [newTargetDate, setNewTargetDate] = useState('');
  const [saving, setSaving] = useState(false);

  const fetchMilestones = useCallback(async () => {
    if (!mentorshipId) { setLoading(false); return; }
    try {
      const res = await apiClient.get(`/mentorships/${mentorshipId}/milestones`);
      setMilestones(
        (res.data as any[]).map((m) => ({
          id: m.id,
          title: m.title,
          description: m.description,
          targetDate: m.targetDate,
          status: m.status as MilestoneStatus,
          orderIndex: m.orderIndex ?? 0,
          actionItems: [],
          expanded: false,
        }))
      );
    } catch {
      Alert.alert('Error', 'Could not load milestones.');
    } finally {
      setLoading(false);
    }
  }, [mentorshipId]);

  useEffect(() => { fetchMilestones(); }, [fetchMilestones]);

  const fetchMilestoneDetail = async (milestoneId: number) => {
    try {
      const res = await apiClient.get(`/milestones/${milestoneId}`);
      const detail = res.data as any;
      setMilestones((prev) =>
        prev.map((m) =>
          m.id === milestoneId
            ? {
                ...m,
                actionItems: (detail.actionItems ?? []).map((a: any) => ({
                  id: a.id,
                  text: a.text,
                  isCompleted: a.isCompleted,
                })),
                expanded: true,
              }
            : m
        )
      );
    } catch {
      Alert.alert('Error', 'Could not load milestone details.');
    }
  };

  const toggleExpand = (milestone: Milestone) => {
    if (milestone.expanded) {
      setMilestones((prev) =>
        prev.map((m) => (m.id === milestone.id ? { ...m, expanded: false } : m))
      );
    } else {
      fetchMilestoneDetail(milestone.id);
    }
  };

  const toggleActionItem = async (milestoneId: number, item: ActionItem) => {
    const newVal = !item.isCompleted;
    setMilestones((prev) =>
      prev.map((m) =>
        m.id === milestoneId
          ? {
              ...m,
              actionItems: m.actionItems.map((a) =>
                a.id === item.id ? { ...a, isCompleted: newVal } : a
              ),
            }
          : m
      )
    );
    try {
      await apiClient.patch(`/milestone-action-items/${item.id}`, { isCompleted: newVal });
    } catch {
      setMilestones((prev) =>
        prev.map((m) =>
          m.id === milestoneId
            ? {
                ...m,
                actionItems: m.actionItems.map((a) =>
                  a.id === item.id ? { ...a, isCompleted: item.isCompleted } : a
                ),
              }
            : m
        )
      );
      Alert.alert('Error', 'Could not update action item.');
    }
  };

  const handleAddMilestone = async () => {
    if (!newTitle.trim()) { Alert.alert('Title required'); return; }
    setSaving(true);
    try {
      const body: any = { title: newTitle.trim() };
      if (newDescription.trim()) body.description = newDescription.trim();
      if (newTargetDate.trim()) {
        const parsed = new Date(newTargetDate.trim());
        if (isNaN(parsed.getTime())) {
          Alert.alert('Invalid date format. Use YYYY-MM-DD');
          setSaving(false);
          return;
        }
        body.targetDate = parsed.toISOString();
      }
      await apiClient.post(`/mentorships/${mentorshipId}/milestones`, body);
      setNewTitle('');
      setNewDescription('');
      setNewTargetDate('');
      setShowAddModal(false);
      fetchMilestones();
    } catch {
      Alert.alert('Error', 'Could not create milestone.');
    } finally {
      setSaving(false);
    }
  };

  const formatDate = (iso?: string) => {
    if (!iso) return '';
    return new Date(iso).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
  };

  return (
    <View style={styles.container}>
      <ScrollView style={styles.scrollArea} contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
        <View style={styles.statusRow}>
          <Text style={styles.statusText}>
            {new Date().toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit', hour12: false })}
          </Text>
          <Text style={styles.statusIcons}>●●●</Text>
        </View>

        <View style={styles.headerRow}>
          <View style={{ flexDirection: 'row', alignItems: 'center' }}>
            <TouchableOpacity onPress={() => router.back()} style={{ marginRight: 12, padding: 5 }}>
              <Text style={{ fontSize: 24, color: '#1D1D38', fontWeight: 'bold' }}>←</Text>
            </TouchableOpacity>
            <Text style={styles.title}>Milestones</Text>
          </View>
          {isMentor && (
            <TouchableOpacity onPress={() => setShowAddModal(true)}>
              <Text style={styles.addText}>+ Add</Text>
            </TouchableOpacity>
          )}
        </View>

        {loading ? (
          <ActivityIndicator size="large" color="#456B50" style={{ marginTop: 40 }} />
        ) : milestones.length === 0 ? (
          <View style={styles.emptyState}>
            <Text style={styles.emptyIcon}>🎯</Text>
            <Text style={styles.emptyTitle}>No milestones yet</Text>
            {isMentor && (
              <Text style={styles.emptySubtitle}>{'Tap "+ Add" to create your first milestone'}</Text>
            )}
          </View>
        ) : (
          milestones
            .sort((a, b) => a.orderIndex - b.orderIndex)
            .map((milestone) => {
              const sc = STATUS_COLORS[milestone.status];
              return (
                <TouchableOpacity
                  key={milestone.id}
                  style={styles.card}
                  onPress={() => toggleExpand(milestone)}
                  activeOpacity={0.85}
                >
                  <View style={styles.cardHeader}>
                    <View style={styles.cardHeaderLeft}>
                      <View style={[styles.statusBadge, { backgroundColor: sc.bg }]}>
                        <Text style={[styles.statusBadgeText, { color: sc.text }]}>
                          {STATUS_LABELS[milestone.status]}
                        </Text>
                      </View>
                      <Text style={styles.cardTitle}>{milestone.title}</Text>
                      {milestone.targetDate && (
                        <Text style={styles.cardDate}>Target: {formatDate(milestone.targetDate)}</Text>
                      )}
                    </View>
                    <Text style={styles.chevron}>{milestone.expanded ? '▲' : '▼'}</Text>
                  </View>

                  {milestone.expanded && (
                    <View style={styles.cardBody}>
                      {milestone.description ? (
                        <Text style={styles.cardDescription}>{milestone.description}</Text>
                      ) : null}

                      {milestone.actionItems.length > 0 && (
                        <>
                          <Text style={styles.actionItemsLabel}>ACTION ITEMS</Text>
                          {milestone.actionItems.map((item) => (
                            <TouchableOpacity
                              key={item.id}
                              style={styles.actionItemRow}
                              onPress={() => toggleActionItem(milestone.id, item)}
                              activeOpacity={0.7}
                            >
                              <View style={[styles.actionCheck, item.isCompleted && styles.actionCheckDone]}>
                                {item.isCompleted && <Text style={styles.actionCheckMark}>✓</Text>}
                              </View>
                              <Text style={[styles.actionItemText, item.isCompleted && styles.actionItemTextDone]}>
                                {item.text}
                              </Text>
                            </TouchableOpacity>
                          ))}
                        </>
                      )}

                      {milestone.actionItems.length === 0 && (
                        <Text style={styles.noActionItems}>No action items</Text>
                      )}
                    </View>
                  )}
                </TouchableOpacity>
              );
            })
        )}
      </ScrollView>

      <Modal visible={showAddModal} transparent animationType="slide">
        <View style={styles.modalOverlay}>
          <View style={styles.modalBox}>
            <Text style={styles.modalTitle}>New Milestone</Text>

            <Text style={styles.modalLabel}>Title *</Text>
            <TextInput
              style={styles.modalInput}
              value={newTitle}
              onChangeText={setNewTitle}
              placeholder="Milestone title"
              placeholderTextColor="#B0A898"
            />

            <Text style={styles.modalLabel}>Description</Text>
            <TextInput
              style={[styles.modalInput, { height: 80 }]}
              value={newDescription}
              onChangeText={setNewDescription}
              placeholder="Optional description"
              placeholderTextColor="#B0A898"
              multiline
            />

            <Text style={styles.modalLabel}>Target Date (YYYY-MM-DD)</Text>
            <TextInput
              style={styles.modalInput}
              value={newTargetDate}
              onChangeText={setNewTargetDate}
              placeholder="e.g. 2025-07-01"
              placeholderTextColor="#B0A898"
            />

            <View style={styles.modalButtons}>
              <TouchableOpacity
                style={styles.modalCancel}
                onPress={() => {
                  setShowAddModal(false);
                  setNewTitle('');
                  setNewDescription('');
                  setNewTargetDate('');
                }}
              >
                <Text style={styles.modalCancelText}>Cancel</Text>
              </TouchableOpacity>
              <TouchableOpacity style={styles.modalSubmit} onPress={handleAddMilestone} disabled={saving}>
                <Text style={styles.modalSubmitText}>{saving ? 'Saving…' : 'Create'}</Text>
              </TouchableOpacity>
            </View>
          </View>
        </View>
      </Modal>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#EEF0F4' },
  scrollArea: { flex: 1 },
  content: { paddingHorizontal: 24, paddingTop: 54, paddingBottom: 36 },
  statusRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 24 },
  statusText: { fontSize: 16, fontWeight: '700', color: '#2B2B2B' },
  statusIcons: { fontSize: 16, fontWeight: '700', color: '#666666' },
  headerRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 28 },
  title: { fontSize: 28, fontWeight: '700', color: '#1D1D38' },
  addText: { fontSize: 16, fontWeight: '700', color: '#3FA06F' },
  emptyState: { alignItems: 'center', marginTop: 60 },
  emptyIcon: { fontSize: 48, marginBottom: 16 },
  emptyTitle: { fontSize: 20, fontWeight: '700', color: '#1D1D38', marginBottom: 8 },
  emptySubtitle: { fontSize: 14, color: '#8C8A8A', textAlign: 'center' },
  card: { backgroundColor: '#F8F8F7', borderRadius: 20, padding: 18, marginBottom: 16 },
  cardHeader: { flexDirection: 'row', alignItems: 'flex-start', justifyContent: 'space-between' },
  cardHeaderLeft: { flex: 1 },
  statusBadge: { alignSelf: 'flex-start', paddingHorizontal: 12, paddingVertical: 5, borderRadius: 12, marginBottom: 8 },
  statusBadgeText: { fontSize: 12, fontWeight: '700' },
  cardTitle: { fontSize: 18, fontWeight: '700', color: '#1D1D38', marginBottom: 4 },
  cardDate: { fontSize: 13, color: '#8C8A8A' },
  chevron: { fontSize: 14, color: '#8C8A8A', marginLeft: 8, marginTop: 2 },
  cardBody: { marginTop: 14, borderTopWidth: 1, borderTopColor: '#ECEAE6', paddingTop: 14 },
  cardDescription: { fontSize: 14, color: '#5A5A5A', lineHeight: 21, marginBottom: 12 },
  actionItemsLabel: { fontSize: 11, fontWeight: '700', letterSpacing: 1.5, color: '#A0A0A0', marginBottom: 10 },
  actionItemRow: { flexDirection: 'row', alignItems: 'center', marginBottom: 10 },
  actionCheck: { width: 24, height: 24, borderRadius: 12, borderWidth: 2, borderColor: '#C8C0B8', alignItems: 'center', justifyContent: 'center', marginRight: 12 },
  actionCheckDone: { backgroundColor: '#456B50', borderColor: '#456B50' },
  actionCheckMark: { color: '#FFFFFF', fontSize: 13, fontWeight: '700' },
  actionItemText: { fontSize: 15, color: '#1D1D38', flex: 1 },
  actionItemTextDone: { color: '#B0B0B0', textDecorationLine: 'line-through' },
  noActionItems: { fontSize: 13, color: '#B0A898', fontStyle: 'italic' },
  modalOverlay: { flex: 1, backgroundColor: 'rgba(0,0,0,0.45)', justifyContent: 'flex-end' },
  modalBox: { backgroundColor: '#FFFFFF', borderTopLeftRadius: 24, borderTopRightRadius: 24, padding: 28, paddingBottom: 40 },
  modalTitle: { fontSize: 22, fontWeight: '700', color: '#1D1D38', marginBottom: 20 },
  modalLabel: { fontSize: 13, fontWeight: '600', color: '#7E7368', marginBottom: 6 },
  modalInput: { backgroundColor: '#F5F3EF', borderRadius: 12, padding: 14, fontSize: 15, color: '#1D1D38', marginBottom: 16 },
  modalButtons: { flexDirection: 'row', gap: 12, marginTop: 4 },
  modalCancel: { flex: 1, backgroundColor: '#EEE9E3', borderRadius: 14, padding: 16, alignItems: 'center' },
  modalCancelText: { fontSize: 15, fontWeight: '600', color: '#7E7368' },
  modalSubmit: { flex: 1, backgroundColor: '#456B50', borderRadius: 14, padding: 16, alignItems: 'center' },
  modalSubmitText: { fontSize: 15, fontWeight: '700', color: '#FFFFFF' },
});
