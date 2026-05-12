import React, { useState, useEffect, useCallback, useMemo } from 'react';
import { router, useLocalSearchParams } from 'expo-router';
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  StyleSheet,
  ScrollView,
  ActivityIndicator,
  Alert,
  Modal,
} from 'react-native';
import { useRole } from '../components/RoleContext';
import { useProtectedSession } from '../components/useProtectedSession';
import apiClient from '../api/client';
import ActionModal from '../components/ActionModal';

type AvailabilitySlot = {
  dayOfWeek: string;
  startTime: string;
  endTime: string;
};

type MilestoneStatus = 'PENDING' | 'IN_PROGRESS' | 'COMPLETED';

type MilestoneSummary = {
  id: number;
  title: string;
  targetDate: string | null;
  status: MilestoneStatus;
  orderIndex: number;
};

type MeetingSummary = {
  id: number;
  title: string;
  startTime: string | null;
  status: string;
};

type TaskSummary = {
  id: number;
  title: string;
  dueDate: string | null;
  status: string;
};

type TimelineEventKind = 'start' | 'end' | 'today' | 'milestone' | 'meeting' | 'task';

type TimelineEvent = {
  kind: TimelineEventKind;
  date: string; // YYYY-MM-DD
  label: string;
  refId?: number;
  status?: string;
};

type MilestoneActionItem = {
  id: number;
  text: string;
  isCompleted: boolean;
  orderIndex: number;
  completedAt: string | null;
  completedById: number | null;
  createdById: number | null;
  createdAt: string | null;
};

type MilestoneDetail = {
  id: number;
  mentorshipId: number;
  title: string;
  description: string;
  targetDate: string | null;
  status: MilestoneStatus;
  orderIndex: number;
  completedAt: string | null;
  createdAt: string | null;
  actionItems: MilestoneActionItem[];
};

const DAY_LIST = [
  { api: 'MONDAY',    short: 'Mon' },
  { api: 'TUESDAY',   short: 'Tue' },
  { api: 'WEDNESDAY', short: 'Wed' },
  { api: 'THURSDAY',  short: 'Thu' },
  { api: 'FRIDAY',    short: 'Fri' },
  { api: 'SATURDAY',  short: 'Sat' },
  { api: 'SUNDAY',    short: 'Sun' },
];

function parseString(value: string | string[] | undefined) {
  return Array.isArray(value) ? value[0] : value ?? '';
}

function parseJsonList(value: string | string[] | undefined): string[] {
  try {
    const raw = parseString(value);
    return raw ? JSON.parse(raw) : [];
  } catch {
    return [];
  }
}

function formatDateLabel(iso: string | null | undefined): string {
  if (!iso) return '';
  return new Date(iso).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
}

function formatStatusLabel(status: MilestoneStatus): string {
  return status === 'IN_PROGRESS' ? 'In Progress' : status.charAt(0) + status.slice(1).toLowerCase();
}

function toIsoDateOrNull(dateStr: string): string | null {
  if (!dateStr.trim()) return null;
  const d = new Date(dateStr.trim());
  if (isNaN(d.getTime())) return null;
  return d.toISOString();
}

export default function ConnectionProfileScreen() {
  const { role } = useRole();
  const { session, sessionLoading } = useProtectedSession('connection-profile');
  const isMentorViewer = role === 'mentor';
  const params = useLocalSearchParams();

  const id = parseString(params.id);
  const mentorshipId = parseString(params.mentorshipId);
  const type = parseString(params.type);
  const name = parseString(params.name);
  const initials = parseString(params.initials);
  const avatarBg = parseString(params.avatarBg) || '#D7E8DA';
  const avatarText = parseString(params.avatarText) || '#2F563C';
  const subtitle = parseString(params.subtitle);
  const sourceScreen = parseString(params.sourceScreen);

  const [about, setAbout] = useState(parseString(params.about));
  const [department, setDepartment] = useState(parseString(params.department));
  const [title, setTitle] = useState(parseString(params.title));
  const [interests, setInterests] = useState(parseJsonList(params.interests));
  const [goals, setGoals] = useState(parseJsonList(params.goals));
  const mentoringGoals = parseJsonList(params.mentoringGoals);
  const preferences = parseJsonList(params.preferences);

  const [mentorSlots, setMentorSlots] = useState<AvailabilitySlot[]>([]);
  const [availabilityLoading, setAvailabilityLoading] = useState(false);

  const [sharedGoal, setSharedGoal] = useState('');
  const [goalDraft, setGoalDraft] = useState('');
  const [mentorshipStartDate, setMentorshipStartDate] = useState<string | null>(null);
  const [mentorshipEndDate, setMentorshipEndDate] = useState<string | null>(null);
  const [meetings, setMeetings] = useState<MeetingSummary[]>([]);
  const [tasks, setTasks] = useState<TaskSummary[]>([]);
  const [timelineModalEvent, setTimelineModalEvent] = useState<TimelineEvent | null>(null);
  const [goalEditing, setGoalEditing] = useState(false);
  const [goalSaving, setGoalSaving] = useState(false);
  const [milestones, setMilestones] = useState<MilestoneSummary[]>([]);
  const [milestoneDetails, setMilestoneDetails] = useState<Record<number, MilestoneDetail>>({});
  const [milestonesLoading, setMilestonesLoading] = useState(false);
  const [milestonesRefreshing, setMilestonesRefreshing] = useState(false);
  const [selectedMilestoneId, setSelectedMilestoneId] = useState<number | null>(null);
  const [selectedMilestoneLoading, setSelectedMilestoneLoading] = useState(false);
  const [actionItemSavingId, setActionItemSavingId] = useState<number | null>(null);
  const [milestoneComposerOpen, setMilestoneComposerOpen] = useState(false);
  const [milestoneTitleDraft, setMilestoneTitleDraft] = useState('');
  const [milestoneDescriptionDraft, setMilestoneDescriptionDraft] = useState('');
  const [milestoneDateDraft, setMilestoneDateDraft] = useState('');
  const [milestoneCreating, setMilestoneCreating] = useState(false);
  const [actionItemDraft, setActionItemDraft] = useState('');
  const [actionItemCreating, setActionItemCreating] = useState(false);
  const [mentorshipActionLoading, setMentorshipActionLoading] = useState(false);
  const [endModalVisible, setEndModalVisible] = useState(false);
  const [endReason, setEndReason] = useState('');

  useEffect(() => {
    console.log('[connection-profile] route context', {
      id,
      mentorshipId,
      sourceScreen,
      type,
      name,
      role,
      isMentorViewer,
      sessionUserId: session?.userId ?? null,
      sessionRole: session?.role ?? null,
    });
  }, [id, mentorshipId, sourceScreen, type, name, role, isMentorViewer, session?.role, session?.userId]);

  useEffect(() => {
    if (sessionLoading) return;
    if (!session) {
      console.log('[connection-profile] skipping protected profile fetch because session is missing');
      return;
    }
    if (!id) return;
    apiClient.get(`/users/${id}`).then((res) => {
      const d = res.data;
      if (d.bio) setAbout(d.bio);
      else if (d.backgroundInfo) setAbout(d.backgroundInfo);
      if (d.major) setDepartment(d.major);
      if (d.field) setTitle(d.field);
      if (d.interests?.length) setInterests(d.interests);
      if (d.goals) setGoals([d.goals]);
    }).catch(() => {});
  }, [id, session, sessionLoading]);

  useEffect(() => {
    if (sessionLoading) return;
    if (!session) {
      console.log('[connection-profile] skipping mentorship fetch because session is missing');
      return;
    }
    if (!mentorshipId) return;
    apiClient.get(`/mentorships/${mentorshipId}`).then((res) => {
      if (res.data.sharedGoal) setSharedGoal(res.data.sharedGoal);
      if (res.data.startDate) setMentorshipStartDate(String(res.data.startDate).slice(0, 10));
      if (res.data.endDate) setMentorshipEndDate(String(res.data.endDate).slice(0, 10));
    }).catch(() => {});

    apiClient.get(`/mentorships/${mentorshipId}/meetings`)
      .then((res) => setMeetings((res.data ?? []) as MeetingSummary[]))
      .catch(() => setMeetings([]));

    apiClient.get(`/mentorships/${mentorshipId}/tasks`)
      .then((res) => setTasks((res.data ?? []) as TaskSummary[]))
      .catch(() => setTasks([]));
  }, [mentorshipId, session, sessionLoading]);

  const fetchMilestoneDetail = async (milestoneId: number) => {
    const res = await apiClient.get(`/milestones/${milestoneId}`);
    const data = res.data as MilestoneDetail;
    return {
      ...data,
      actionItems: (data.actionItems ?? []).map((raw) => {
        const item = raw as MilestoneActionItem & { completed?: boolean };
        return {
          ...item,
          isCompleted: item.isCompleted ?? item.completed ?? false,
        };
      }),
    } as MilestoneDetail;
  };

  const loadMilestones = useCallback(async (keepSelection = true) => {
    if (sessionLoading) return;
    if (!session) {
      console.log('[connection-profile] skipping milestone fetch because session is missing');
      setMilestonesLoading(false);
      setMilestonesRefreshing(false);
      return;
    }
    if (!mentorshipId) return;

    setMilestonesLoading(true);
    try {
      console.log('[connection-profile] loading milestones', {
        mentorshipId,
        keepSelection,
      });
      const res = await apiClient.get(`/mentorships/${mentorshipId}/milestones`);
      const summaryItems = ((res.data ?? []) as MilestoneSummary[]).sort(
        (a, b) => (a.orderIndex ?? 0) - (b.orderIndex ?? 0)
      );
      setMilestones(summaryItems);

      if (summaryItems.length === 0) {
        setMilestoneDetails({});
        setSelectedMilestoneId(null);
        return;
      }

      const details = await Promise.all(
        summaryItems.map(async (item) => [item.id, await fetchMilestoneDetail(item.id)] as const)
      );

      const detailMap = Object.fromEntries(details);
      setMilestoneDetails(detailMap);
      setSelectedMilestoneId((current) => {
        if (keepSelection && current && detailMap[current]) return current;
        return summaryItems[0].id;
      });
    } catch (error: any) {
      console.error('[connection-profile] failed to load milestones', {
        status: error?.response?.status,
        data: error?.response?.data,
        mentorshipId,
        id,
        role,
      });
      Alert.alert('Error', 'Could not load milestones right now.');
    } finally {
      setMilestonesLoading(false);
      setMilestonesRefreshing(false);
    }
  }, [id, mentorshipId, role, session, sessionLoading]);

  useEffect(() => {
    if (sessionLoading || !session) return;
    if (!mentorshipId) return;
    loadMilestones(false);
  }, [mentorshipId, loadMilestones, session, sessionLoading]);

  const refreshSelectedMilestone = async (milestoneId: number) => {
    setSelectedMilestoneLoading(true);
    try {
      const detail = await fetchMilestoneDetail(milestoneId);
      setMilestoneDetails((prev) => ({ ...prev, [milestoneId]: detail }));
    } catch {
      Alert.alert('Error', 'Could not refresh the milestone details.');
    } finally {
      setSelectedMilestoneLoading(false);
    }
  };

  const saveSharedGoal = async () => {
    if (!mentorshipId || !goalDraft.trim()) return;
    setGoalSaving(true);
    try {
      await apiClient.put(`/mentorships/${mentorshipId}/goal`, { sharedGoal: goalDraft.trim() });
      setSharedGoal(goalDraft.trim());
      setGoalEditing(false);
    } catch {
      Alert.alert('Error', 'Could not save the shared goal. Please try again.');
    } finally {
      setGoalSaving(false);
    }
  };

  const stat1Label = parseString(params.stat1Label);
  const stat1Value = parseString(params.stat1Value);
  const stat2Label = parseString(params.stat2Label);
  const stat2Value = parseString(params.stat2Value);
  const stat3Label = parseString(params.stat3Label);
  const stat3Value = parseString(params.stat3Value);

  const isViewingMentor = type === 'mentor';
  const selectedMilestone = selectedMilestoneId ? milestoneDetails[selectedMilestoneId] : null;

  const timelineEvents: TimelineEvent[] = useMemo(() => {
    if (!mentorshipStartDate || !mentorshipEndDate) return [];
    const list: TimelineEvent[] = [
      { kind: 'start', date: mentorshipStartDate, label: 'Program Start' },
      { kind: 'end', date: mentorshipEndDate, label: 'Program Completion' },
    ];
    const today = new Date().toISOString().slice(0, 10);
    if (today >= mentorshipStartDate && today <= mentorshipEndDate) {
      list.push({ kind: 'today', date: today, label: 'Today' });
    }
    milestones.forEach((m) => {
      if (m.targetDate) {
        list.push({
          kind: 'milestone',
          date: String(m.targetDate).slice(0, 10),
          label: m.title,
          refId: m.id,
          status: m.status,
        });
      }
    });
    meetings.forEach((m) => {
      if (m.startTime) {
        list.push({
          kind: 'meeting',
          date: String(m.startTime).slice(0, 10),
          label: m.title || 'Meeting',
          refId: m.id,
          status: m.status,
        });
      }
    });
    tasks.forEach((t) => {
      if (t.dueDate) {
        list.push({
          kind: 'task',
          date: String(t.dueDate).slice(0, 10),
          label: t.title || 'Task',
          refId: t.id,
          status: t.status,
        });
      }
    });
    list.sort((a, b) => a.date.localeCompare(b.date));
    return list;
  }, [mentorshipStartDate, mentorshipEndDate, milestones, meetings, tasks]);

  const handleTimelineEventPress = (event: TimelineEvent) => {
    if (event.kind === 'milestone' && event.refId) {
      setSelectedMilestoneId(event.refId);
      setTimelineModalEvent(null);
      openMilestonesScreen(event.refId);
      return;
    }
    if (event.kind === 'meeting') {
      setTimelineModalEvent(null);
      openMeetings();
      return;
    }
    if (event.kind === 'task') {
      setTimelineModalEvent(null);
      openTasks();
      return;
    }
    setTimelineModalEvent(event);
  };
  const totalActionItems = Object.values(milestoneDetails).reduce(
    (sum, milestone) => sum + milestone.actionItems.length,
    0
  );
  const completedActionItems = Object.values(milestoneDetails).reduce(
    (sum, milestone) => sum + milestone.actionItems.filter((item) => item.isCompleted).length,
    0
  );
  const overallProgressPercent = totalActionItems
    ? Math.round((completedActionItems / totalActionItems) * 100)
    : 0;
  const selectedMilestoneCompletedCount = selectedMilestone?.actionItems.filter((item) => item.isCompleted).length ?? 0;
  const selectedMilestoneProgressPercent = selectedMilestone?.actionItems.length
    ? Math.round((selectedMilestoneCompletedCount / selectedMilestone.actionItems.length) * 100)
    : 0;
  const getStatusChipStyle = (status: MilestoneStatus) => {
    if (status === 'COMPLETED') return styles.statusCompleted;
    if (status === 'IN_PROGRESS') return styles.statusInProgress;
    return styles.statusPending;
  };

  useEffect(() => {
    if (!isViewingMentor || !id) return;
    setAvailabilityLoading(true);
    apiClient.get(`/availability/${id}`)
      .then((res) => setMentorSlots(res.data ?? []))
      .catch(() => {})
      .finally(() => setAvailabilityLoading(false));
  }, [isViewingMentor, id]);

  const handleEndMentorship = () => {
    setEndReason('');
    setEndModalVisible(true);
  };

  const confirmEndMentorship = async () => {
    if (!isMentorViewer && !endReason.trim()) {
      Alert.alert('Required', 'Please enter a reason for cancellation.');
      return;
    }
    setMentorshipActionLoading(true);
    try {
      if (isMentorViewer) {
        await apiClient.patch(`/mentorships/${mentorshipId}/end`, { reason: endReason.trim() });
      } else {
        await apiClient.post(`/mentorships/${mentorshipId}/cancel`, { reason: endReason.trim() });
      }
      setEndModalVisible(false);
      Alert.alert('Done', `Mentorship ${isMentorViewer ? 'ended' : 'cancelled'} successfully.`, [
        { text: 'OK', onPress: () => router.back() },
      ]);
    } catch (err: any) {
      Alert.alert('Error', err?.response?.data?.message || 'Could not complete the action.');
    } finally {
      setMentorshipActionLoading(false);
    }
  };

  const openRequest = (mode: 'meeting' | 'change' | 'end') => {
    const mentorId = session?.role === 'mentor' ? session.userId : Number(id);
    const menteeId = session?.role === 'mentee' ? session.userId : Number(id);
    console.log('[navigation] opening connection-request', {
      sourceScreen: 'connection-profile',
      currentUserId: session?.userId ?? null,
      currentRole: session?.role ?? role,
      mentorshipId,
      mentorId,
      menteeId,
      mode,
      targetScreen: 'connection-request',
    });
    router.push({
      pathname: '/connection-request',
      params: {
        mode,
        targetName: name,
        targetType: type,
        mentorshipId,
      },
    });
  };

  const openMeetings = () => {
    console.log('[navigation] opening meetings-sessions', {
      sourceScreen: 'connection-profile',
      currentUserId: session?.userId ?? null,
      currentRole: session?.role ?? role,
      mentorshipId,
      targetScreen: 'meetings-sessions',
    });
    router.push({
      pathname: '/meetings-sessions',
      params: { connectedUserName: name, connectedUserType: type, mentorshipId, sourceScreen: 'connection-profile' },
    });
  };

  const openTasks = () => {
    console.log('[navigation] opening task-tracker', {
      sourceScreen: 'connection-profile',
      currentUserId: session?.userId ?? null,
      currentRole: session?.role ?? role,
      mentorshipId,
      targetScreen: 'task-tracker',
    });
    router.push({
      pathname: '/task-tracker',
      params: {
        connectedUserName: name,
        connectedUserType: type,
        mentorshipId,
        sourceScreen: 'connection-profile',
      },
    });
  };

  const openMilestonesScreen = (milestoneId?: number) => {
    router.push({
      pathname: '/milestones',
      params: {
        connectedUserName: name,
        connectedUserType: type,
        mentorshipId,
        sourceScreen: 'connection-profile',
        ...(milestoneId ? { focusMilestoneId: String(milestoneId) } : {}),
      },
    });
  };

  const openMessages = () => {
    console.log('[navigation] opening messages', {
      sourceScreen: 'connection-profile',
      currentUserId: session?.userId ?? null,
      currentRole: session?.role ?? role,
      mentorshipId,
      targetScreen: 'messages',
      openWith: name,
    });
    router.navigate({
      pathname: '/messages',
      params: { openWith: name, mentorshipId, sourceScreen: 'connection-profile' },
    });
  };

  const createMilestone = async () => {
    if (!mentorshipId || !milestoneTitleDraft.trim()) return;
    const targetDate = toIsoDateOrNull(milestoneDateDraft);
    if (targetDate && mentorshipStartDate && mentorshipEndDate
        && (targetDate < mentorshipStartDate || targetDate > mentorshipEndDate)) {
      Alert.alert(
        'Invalid date',
        `Target date must be between ${mentorshipStartDate} and ${mentorshipEndDate}.`
      );
      return;
    }
    setMilestoneCreating(true);
    try {
      await apiClient.post(`/mentorships/${mentorshipId}/milestones`, {
        title: milestoneTitleDraft.trim(),
        description: milestoneDescriptionDraft.trim() || null,
        targetDate,
      });
      setMilestoneTitleDraft('');
      setMilestoneDescriptionDraft('');
      setMilestoneDateDraft('');
      setMilestoneComposerOpen(false);
      await loadMilestones(false);
    } catch (err: any) {
      const msg = err?.response?.data?.message || 'Could not create the milestone.';
      Alert.alert('Error', msg);
    } finally {
      setMilestoneCreating(false);
    }
  };

  const addActionItem = async () => {
    if (!selectedMilestone || !actionItemDraft.trim()) return;
    setActionItemCreating(true);
    try {
      await apiClient.post(`/milestones/${selectedMilestone.id}/action-items`, {
        text: actionItemDraft.trim(),
      });
      setActionItemDraft('');
      await refreshSelectedMilestone(selectedMilestone.id);
      await loadMilestones();
    } catch {
      Alert.alert('Error', 'Could not add the action item.');
    } finally {
      setActionItemCreating(false);
    }
  };

  const updateMilestoneStatus = async (status: MilestoneStatus) => {
    if (!selectedMilestone || selectedMilestone.status === status) return;
    setSelectedMilestoneLoading(true);
    try {
      await apiClient.patch(`/milestones/${selectedMilestone.id}`, { status });
      await loadMilestones();
    } catch {
      Alert.alert('Error', 'Could not update the milestone status.');
    } finally {
      setSelectedMilestoneLoading(false);
    }
  };

  const toggleActionItem = async (item: MilestoneActionItem) => {
    setActionItemSavingId(item.id);
    try {
      await apiClient.patch(`/milestone-action-items/${item.id}`, {
        completed: !item.isCompleted,
      });
      if (selectedMilestoneId) {
        await refreshSelectedMilestone(selectedMilestoneId);
      }
      await loadMilestones();
    } catch {
      Alert.alert('Error', 'Could not update the action item.');
    } finally {
      setActionItemSavingId(null);
    }
  };

  if (sessionLoading) {
    return (
      <View style={[styles.container, { justifyContent: 'center', alignItems: 'center' }]}>
        <ActivityIndicator size="large" color="#456B50" />
      </View>
    );
  }

  if (!session) {
    return null;
  }

  return (
    <View style={styles.container}>
      <ActionModal
        visible={endModalVisible}
        title={isMentorViewer ? 'End Mentorship' : 'Cancel Mentorship'}
        message={isMentorViewer
          ? 'Add a wrap-up note for your mentee (optional).'
          : 'Please provide a reason for cancelling this mentorship.'}
        fields={[{
          label: isMentorViewer ? 'Wrap-up note' : 'Reason',
          placeholder: isMentorViewer ? 'e.g. Goal achieved — great work!' : 'e.g. Schedules no longer align',
          value: endReason,
          onChange: setEndReason,
          multiline: true,
          required: !isMentorViewer,
        }]}
        confirmLabel={isMentorViewer ? 'End Mentorship' : 'Cancel Mentorship'}
        danger
        loading={mentorshipActionLoading}
        onConfirm={confirmEndMentorship}
        onCancel={() => setEndModalVisible(false)}
      />
      <ScrollView contentContainerStyle={styles.scrollContent} showsVerticalScrollIndicator={false}>
        <View style={styles.header}>
          <View style={styles.topCircle} />
          <View style={styles.leftCircle} />

          <View style={styles.statusRow}>
            <Text style={styles.statusText}>{new Date().toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit', hour12: false })}</Text>
            <Text style={styles.statusIcons}>▲ ▮</Text>
          </View>

          <TouchableOpacity onPress={() => router.back()} style={styles.backButton} testID="connection-profile.back">
            <Text style={styles.backText}>‹</Text>
          </TouchableOpacity>

          <View style={[styles.avatarCircle, { backgroundColor: avatarBg }]}>
            <Text style={[styles.avatarText, { color: avatarText }]}>{initials}</Text>
          </View>

          <Text style={styles.name}>{name}</Text>
          <Text style={styles.roleText}>
            {isViewingMentor ? title || subtitle : department ? `Mentee • ${department}` : subtitle}
          </Text>

          <View style={styles.headerBadge}>
            <Text style={styles.headerBadgeText}>
              {isMentorViewer
                ? isViewingMentor
                  ? 'Peer Mentor View'
                  : 'Your Mentee'
                : isViewingMentor
                ? 'Mentor View'
                : 'Your Mentor'}
            </Text>
          </View>
        </View>

        <View style={styles.statsCard}>
          <View style={styles.statItem}>
            <Text style={styles.statNumber}>{stat1Value || '-'}</Text>
            <Text style={styles.statLabel}>{stat1Label || 'Stat'}</Text>
          </View>
          <View style={styles.statDivider} />
          <View style={styles.statItem}>
            <Text style={styles.statNumber}>{stat2Value || '-'}</Text>
            <Text style={styles.statLabel}>{stat2Label || 'Stat'}</Text>
          </View>
          <View style={styles.statDivider} />
          <View style={styles.statItem}>
            <Text style={styles.statNumber}>{stat3Value || '-'}</Text>
            <Text style={styles.statLabel}>{stat3Label || 'Stat'}</Text>
          </View>
        </View>

        <View style={styles.body}>
          <Text style={styles.sectionTitle}>PROFILE</Text>

          <View style={styles.card}>
            <Text style={styles.cardLabel}>About</Text>
            <Text style={styles.cardText}>{about || 'No bio added yet.'}</Text>

            {!isViewingMentor ? (
              <>
                <Text style={styles.cardLabel}>Department</Text>
                <Text style={styles.cardText}>{department || 'Not specified'}</Text>

                <Text style={styles.cardLabel}>Goals</Text>
                <View style={styles.tokensWrap}>
                  {goals.map((item) => (
                    <View key={item} style={styles.tokenChip}>
                      <Text style={styles.tokenChipText}>{item}</Text>
                    </View>
                  ))}
                </View>

                <Text style={styles.cardLabel}>Interests</Text>
                <View style={styles.tokensWrap}>
                  {interests.map((item) => (
                    <View key={item} style={styles.tokenChip}>
                      <Text style={styles.tokenChipText}>{item}</Text>
                    </View>
                  ))}
                </View>
              </>
            ) : (
              <>
                <Text style={styles.cardLabel}>Expertise / Interests</Text>
                <View style={styles.tokensWrap}>
                  {interests.map((item) => (
                    <View key={item} style={styles.tokenChip}>
                      <Text style={styles.tokenChipText}>{item}</Text>
                    </View>
                  ))}
                </View>

                <Text style={styles.cardLabel}>Mentoring Goals</Text>
                <View style={styles.tokensWrap}>
                  {mentoringGoals.map((item) => (
                    <View key={item} style={styles.tokenChip}>
                      <Text style={styles.tokenChipText}>{item}</Text>
                    </View>
                  ))}
                </View>

                <Text style={styles.cardLabel}>Preferred Mentee Criteria</Text>
                <View style={styles.tokensWrap}>
                  {preferences.map((item) => (
                    <View key={item} style={styles.tokenChip}>
                      <Text style={styles.tokenChipText}>{item}</Text>
                    </View>
                  ))}
                </View>
              </>
            )}
          </View>

          {timelineEvents.length > 0 && (
            <>
              <Text style={styles.sectionTitle}>PROGRESS TIMELINE</Text>
              <View style={styles.card}>
                <Text style={styles.timelineSubtitle}>
                  Program duration: {mentorshipStartDate} – {mentorshipEndDate}
                </Text>
                <ScrollView
                  horizontal
                  showsHorizontalScrollIndicator={false}
                  contentContainerStyle={styles.timelineScroll}
                >
                  <View style={[styles.timelineContainer, { minWidth: Math.max(600, timelineEvents.length * 140) }]}>
                    <View style={styles.timelineTrack} />
                    {timelineEvents.map((event, idx) => {
                      const total = timelineEvents.length;
                      const left = total > 1 ? (idx / (total - 1)) * 100 : 50;
                      const color =
                        event.kind === 'today' ? '#E5A035'
                        : event.kind === 'milestone' ? '#3B7DD8'
                        : event.kind === 'meeting' ? '#2F563C'
                        : event.kind === 'task' ? '#8A5D12'
                        : '#23372B';
                      const dateLabel = new Date(event.date).toLocaleDateString('en-US', {
                        month: 'short', day: 'numeric',
                      });
                      return (
                        <View key={`${event.kind}-${event.refId ?? idx}`} style={[styles.timelineSlot, { left: `${left}%` }]} testID={`connection-profile.timeline.${event.kind}-${event.refId ?? idx}`}>
                          <TouchableOpacity
                            style={[styles.timelinePill, { backgroundColor: color }]}
                            onPress={() => handleTimelineEventPress(event)}
                          >
                            <Text style={styles.timelinePillText} numberOfLines={2}>{event.label}</Text>
                          </TouchableOpacity>
                          <Text style={styles.timelineDateLabel}>{dateLabel}</Text>
                          <TouchableOpacity
                            style={[styles.timelineDot, { backgroundColor: color }]}
                            onPress={() => handleTimelineEventPress(event)}
                          />
                        </View>
                      );
                    })}
                  </View>
                </ScrollView>
                <View style={styles.timelineLegend}>
                  <View style={styles.timelineLegendItem}>
                    <View style={[styles.timelineLegendDot, { backgroundColor: '#3B7DD8' }]} />
                    <Text style={styles.timelineLegendText}>Milestone</Text>
                  </View>
                  <View style={styles.timelineLegendItem}>
                    <View style={[styles.timelineLegendDot, { backgroundColor: '#2F563C' }]} />
                    <Text style={styles.timelineLegendText}>Meeting</Text>
                  </View>
                  <View style={styles.timelineLegendItem}>
                    <View style={[styles.timelineLegendDot, { backgroundColor: '#8A5D12' }]} />
                    <Text style={styles.timelineLegendText}>Task</Text>
                  </View>
                  <View style={styles.timelineLegendItem}>
                    <View style={[styles.timelineLegendDot, { backgroundColor: '#E5A035' }]} />
                    <Text style={styles.timelineLegendText}>Today</Text>
                  </View>
                </View>
              </View>
            </>
          )}

          <Text style={styles.sectionTitle}>SHARED GOAL</Text>

          <View style={styles.card}>
            {goalEditing ? (
              <>
                <TextInput
                  style={styles.goalInput}
                  value={goalDraft}
                  onChangeText={setGoalDraft}
                  placeholder="Describe your shared mentorship goal..."
                  placeholderTextColor="#B0A89E"
                  multiline
                  maxLength={500}
                  autoFocus
                />
                <Text style={styles.goalCharCount}>{goalDraft.length}/500</Text>
                <View style={styles.goalButtonRow}>
                  <TouchableOpacity
                    style={styles.goalCancelButton}
                    onPress={() => setGoalEditing(false)}
                    disabled={goalSaving}
                  >
                    <Text style={styles.goalCancelText}>Cancel</Text>
                  </TouchableOpacity>
                  <TouchableOpacity
                    style={[styles.goalSaveButton, (!goalDraft.trim() || goalSaving) && { opacity: 0.5 }]}
                    onPress={saveSharedGoal}
                    disabled={!goalDraft.trim() || goalSaving}
                    testID="connection-profile.goal-save"
                  >
                    {goalSaving
                      ? <ActivityIndicator size="small" color="#F8F6F2" />
                      : <Text style={styles.goalSaveText}>Save Goal</Text>}
                  </TouchableOpacity>
                </View>
              </>
            ) : (
              <>
                <Text style={styles.cardText}>
                  {sharedGoal || 'No shared goal set yet. Tap Edit to define one together.'}
                </Text>
                <TouchableOpacity
                  style={styles.goalEditButton}
                  onPress={() => { setGoalDraft(sharedGoal); setGoalEditing(true); }}
                  testID="connection-profile.goal-edit"
                >
                  <Text style={styles.goalEditText}>Edit Goal</Text>
                </TouchableOpacity>
              </>
            )}
          </View>

          <Text style={styles.sectionTitle}>MILESTONES</Text>

          <View style={styles.card}>
            <View style={styles.goalSummaryHeader}>
              <View>
                <Text style={styles.cardLabel}>Overall progress</Text>
                <Text style={styles.progressHeadline}>{overallProgressPercent}% complete</Text>
                <Text style={styles.progressSubtext}>
                  {completedActionItems}/{totalActionItems} action items completed
                </Text>
              </View>
              <TouchableOpacity
                style={styles.goalEditButton}
                onPress={() => {
                  setMilestonesRefreshing(true);
                  loadMilestones();
                }}
                disabled={milestonesRefreshing}
              >
                <Text style={styles.goalEditText}>{milestonesRefreshing ? 'Refreshing...' : 'Refresh'}</Text>
              </TouchableOpacity>
            </View>

            <View style={styles.progressTrack}>
              <View style={[styles.progressFill, { width: `${overallProgressPercent}%` }]} />
            </View>

            {isMentorViewer && (
              <>
                <TouchableOpacity
                  style={styles.actionButtonSecondary}
                  onPress={() => setMilestoneComposerOpen((current) => !current)}
                  testID="connection-profile.milestone-add"
                >
                  <Text style={styles.actionButtonSecondaryText}>
                    {milestoneComposerOpen ? 'Hide Milestone Form' : '+ Add Milestone'}
                  </Text>
                </TouchableOpacity>

                {milestoneComposerOpen && (
                  <View style={styles.milestoneComposer}>
                    <TextInput
                      style={styles.milestoneInput}
                      value={milestoneTitleDraft}
                      onChangeText={setMilestoneTitleDraft}
                      placeholder="Milestone title"
                      placeholderTextColor="#B0A89E"
                    />
                    <TextInput
                      style={styles.goalInput}
                      value={milestoneDescriptionDraft}
                      onChangeText={setMilestoneDescriptionDraft}
                      placeholder="Description (optional)"
                      placeholderTextColor="#B0A89E"
                      multiline
                    />
                    <TextInput
                      style={styles.milestoneInput}
                      value={milestoneDateDraft}
                      onChangeText={setMilestoneDateDraft}
                      placeholder={
                        mentorshipStartDate && mentorshipEndDate
                          ? `Target date (${mentorshipStartDate} – ${mentorshipEndDate})`
                          : 'Target date (YYYY-MM-DD)'
                      }
                      placeholderTextColor="#B0A89E"
                      autoCapitalize="none"
                    />
                    <TouchableOpacity
                      style={[styles.goalSaveButton, (!milestoneTitleDraft.trim() || milestoneCreating) && { opacity: 0.5 }]}
                      onPress={createMilestone}
                      disabled={!milestoneTitleDraft.trim() || milestoneCreating}
                    >
                      {milestoneCreating
                        ? <ActivityIndicator size="small" color="#F8F6F2" />
                        : <Text style={styles.goalSaveText}>Create Milestone</Text>}
                    </TouchableOpacity>
                  </View>
                )}
              </>
            )}

            {milestonesLoading ? (
              <ActivityIndicator size="small" color="#456B50" style={styles.inlineLoader} />
            ) : milestones.length === 0 ? (
              <Text style={styles.cardText}>No milestones added yet.</Text>
            ) : (
              <>
                <ScrollView
                  horizontal
                  showsHorizontalScrollIndicator={false}
                  contentContainerStyle={styles.milestoneList}
                >
                  {milestones.map((milestone) => {
                    const detail = milestoneDetails[milestone.id];
                    const completedCount = detail?.actionItems.filter((item) => item.isCompleted).length ?? 0;
                    const progressPercent = detail?.actionItems.length
                      ? Math.round((completedCount / detail.actionItems.length) * 100)
                      : 0;

                    return (
                      <TouchableOpacity
                        key={milestone.id}
                        style={[
                          styles.milestoneCard,
                          selectedMilestoneId === milestone.id && styles.milestoneCardActive,
                        ]}
                        onPress={() => setSelectedMilestoneId(milestone.id)}
                        testID={`connection-profile.milestone-card.${milestone.id}`}
                      >
                        <View style={styles.milestoneCardHeader}>
                          <Text style={styles.milestoneCardTitle}>{milestone.title}</Text>
                          <View style={[styles.statusChip, getStatusChipStyle(milestone.status)]}>
                            <Text style={styles.statusChipText}>{formatStatusLabel(milestone.status)}</Text>
                          </View>
                        </View>
                        <Text style={styles.milestoneMeta}>{formatDateLabel(milestone.targetDate)}</Text>
                        <Text style={styles.milestoneMeta}>{progressPercent}% complete</Text>
                      </TouchableOpacity>
                    );
                  })}
                </ScrollView>

                {selectedMilestone ? (
                  <View style={styles.milestoneDetailCard}>
                    <View style={styles.milestoneDetailHeader}>
                      <View style={styles.flexOne}>
                        <Text style={styles.cardLabel}>Selected objective</Text>
                        <Text style={styles.milestoneDetailTitle}>{selectedMilestone.title}</Text>
                        <Text style={styles.milestoneMeta}>{formatDateLabel(selectedMilestone.targetDate)}</Text>
                      </View>
                      <TouchableOpacity
                        style={styles.goalEditButton}
                        onPress={() => refreshSelectedMilestone(selectedMilestone.id)}
                      >
                        <Text style={styles.goalEditText}>Refresh</Text>
                      </TouchableOpacity>
                    </View>

                    {!!selectedMilestone.description && (
                      <Text style={styles.cardText}>{selectedMilestone.description}</Text>
                    )}

                    <Text style={styles.progressSubtext}>
                      {selectedMilestoneCompletedCount}/{selectedMilestone.actionItems.length} action items complete
                    </Text>
                    <View style={styles.progressTrack}>
                      <View style={[styles.progressFill, { width: `${selectedMilestoneProgressPercent}%` }]} />
                    </View>

                    {isMentorViewer && (
                      <View style={styles.statusFilterRow}>
                        {(['PENDING', 'IN_PROGRESS', 'COMPLETED'] as MilestoneStatus[]).map((status) => (
                          <TouchableOpacity
                            key={status}
                            style={[
                              styles.statusFilterChip,
                              selectedMilestone.status === status && styles.statusFilterChipActive,
                            ]}
                            onPress={() => updateMilestoneStatus(status)}
                          >
                            <Text
                              style={[
                                styles.statusFilterText,
                                selectedMilestone.status === status && styles.statusFilterTextActive,
                              ]}
                            >
                              {formatStatusLabel(status)}
                            </Text>
                          </TouchableOpacity>
                        ))}
                      </View>
                    )}

                    {selectedMilestoneLoading && (
                      <ActivityIndicator size="small" color="#456B50" style={styles.inlineLoader} />
                    )}

                    {selectedMilestone.actionItems.length === 0 ? (
                      <Text style={styles.cardText}>No action items defined for this milestone yet.</Text>
                    ) : (
                      selectedMilestone.actionItems
                        .sort((a, b) => (a.orderIndex ?? 0) - (b.orderIndex ?? 0))
                        .map((item) => (
                          <TouchableOpacity
                            key={item.id}
                            style={styles.actionItemRow}
                            onPress={() => toggleActionItem(item)}
                            disabled={actionItemSavingId === item.id}
                          >
                            <View style={[styles.actionItemCheckbox, item.isCompleted && styles.actionItemCheckboxActive]}>
                              {actionItemSavingId === item.id ? (
                                <ActivityIndicator size="small" color={item.isCompleted ? '#F8F6F2' : '#456B50'} />
                              ) : item.isCompleted ? (
                                <Text style={styles.actionItemCheckmark}>✓</Text>
                              ) : null}
                            </View>
                            <View style={styles.actionItemContent}>
                              <Text style={[styles.actionItemText, item.isCompleted && styles.actionItemTextDone]}>
                                {item.text}
                              </Text>
                              <Text style={styles.actionItemMeta}>
                                {item.isCompleted
                                  ? `Completed ${formatDateLabel(item.completedAt)}`
                                  : 'Tap to mark complete'}
                              </Text>
                            </View>
                          </TouchableOpacity>
                        ))
                    )}

                    {isMentorViewer && (
                      <View style={styles.actionItemComposer}>
                        <TextInput
                          style={styles.milestoneInput}
                          value={actionItemDraft}
                          onChangeText={setActionItemDraft}
                          placeholder="Add an action item"
                          placeholderTextColor="#B0A89E"
                        />
                        <TouchableOpacity
                          style={[styles.goalSaveButton, (!actionItemDraft.trim() || actionItemCreating) && { opacity: 0.5 }]}
                          onPress={addActionItem}
                          disabled={!actionItemDraft.trim() || actionItemCreating}
                        >
                          {actionItemCreating
                            ? <ActivityIndicator size="small" color="#F8F6F2" />
                            : <Text style={styles.goalSaveText}>Add Action Item</Text>}
                        </TouchableOpacity>
                      </View>
                    )}
                  </View>
                ) : null}
              </>
            )}
          </View>

          {isViewingMentor && (
            <>
              <Text style={styles.sectionTitle}>MENTOR AVAILABILITY</Text>

              <View style={styles.card}>
                {availabilityLoading ? (
                  <ActivityIndicator size="small" color="#456B50" />
                ) : (
                  <>
                    <View style={styles.daysGrid}>
                      {DAY_LIST.map(({ api, short }) => {
                        const available = mentorSlots.some((s) => s.dayOfWeek === api);
                        return (
                          <View key={api} style={[styles.dayCell, available && styles.dayCellActive]}>
                            <Text style={[styles.dayCellText, available && styles.dayCellTextActive]}>
                              {short}
                            </Text>
                          </View>
                        );
                      })}
                    </View>

                    {mentorSlots.length === 0 ? (
                      <Text style={styles.cardText}>No availability set by mentor yet.</Text>
                    ) : (
                      DAY_LIST
                        .filter(({ api }) => mentorSlots.some((s) => s.dayOfWeek === api))
                        .map(({ api, short }) => {
                          const slots = mentorSlots.filter((s) => s.dayOfWeek === api);
                          return (
                            <View key={api} style={styles.availabilityRow}>
                              <Text style={styles.availabilityDay}>{short}</Text>
                              <View style={styles.availabilitySlots}>
                                {slots.map((s, i) => (
                                  <View key={i} style={styles.availabilityBadge}>
                                    <Text style={styles.availabilityBadgeText}>
                                      {s.startTime.substring(0, 5)} – {s.endTime.substring(0, 5)}
                                    </Text>
                                  </View>
                                ))}
                              </View>
                            </View>
                          );
                        })
                    )}
                  </>
                )}
              </View>
            </>
          )}

          <Text style={styles.sectionTitle}>ACTIONS</Text>

          <View style={styles.actionsGrid}>
            <TouchableOpacity
              style={styles.actionButtonPrimary}
              onPress={openMessages}
            >
              <Text style={styles.actionButtonPrimaryText}>Open Messages</Text>
            </TouchableOpacity>

            <View style={styles.actionButtonRow}>
              <TouchableOpacity style={[styles.actionButtonSecondary, styles.actionButtonHalf]} onPress={openMeetings}>
                <Text style={styles.actionButtonSecondaryText}>📅 Meetings</Text>
              </TouchableOpacity>
              <TouchableOpacity style={[styles.actionButtonSecondary, styles.actionButtonHalf]} onPress={openTasks}>
                <Text style={styles.actionButtonSecondaryText}>✅ My Tasks</Text>
              </TouchableOpacity>
            </View>

            <TouchableOpacity style={styles.actionButtonSecondary} onPress={() => openRequest('meeting')}>
              <Text style={styles.actionButtonSecondaryText}>Setup Meeting Request</Text>
            </TouchableOpacity>

            <TouchableOpacity style={styles.actionButtonSecondary} onPress={() => openRequest('change')}>
              <Text style={styles.actionButtonSecondaryText}>Change Request</Text>
            </TouchableOpacity>

            <TouchableOpacity
              style={[styles.actionButtonDanger, mentorshipActionLoading && { opacity: 0.6 }]}
              onPress={handleEndMentorship}
              disabled={mentorshipActionLoading}
            >
              <Text style={styles.actionButtonDangerText}>
                {mentorshipActionLoading
                  ? 'Please wait...'
                  : isMentorViewer
                  ? 'End Mentorship'
                  : 'Cancel Mentorship'}
              </Text>
            </TouchableOpacity>
          </View>
        </View>
      </ScrollView>

      <Modal
        visible={timelineModalEvent !== null}
        transparent
        animationType="slide"
        onRequestClose={() => setTimelineModalEvent(null)}
      >
        <TouchableOpacity
          activeOpacity={1}
          style={styles.timelineModalBackdrop}
          onPress={() => setTimelineModalEvent(null)}
        >
          <TouchableOpacity activeOpacity={1} style={styles.timelineModalSheet}>
            <Text style={styles.timelineModalKind}>
              {timelineModalEvent?.kind.toUpperCase()}
            </Text>
            <Text style={styles.timelineModalTitle}>
              {timelineModalEvent?.label}
            </Text>
            <Text style={styles.timelineModalDate}>
              {timelineModalEvent?.date}
              {timelineModalEvent?.status ? `  ·  ${timelineModalEvent.status}` : ''}
            </Text>
            {timelineModalEvent?.kind === 'meeting' && (
              <Text style={[styles.cardText, { marginBottom: 18 }]}>
                Open the Meetings & Sessions screen to manage this meeting.
              </Text>
            )}
            {timelineModalEvent?.kind === 'task' && (
              <Text style={[styles.cardText, { marginBottom: 18 }]}>
                Open the Task Tracker screen to manage this task.
              </Text>
            )}
            <TouchableOpacity
              style={styles.timelineModalCloseButton}
              onPress={() => setTimelineModalEvent(null)}
            >
              <Text style={styles.timelineModalCloseText}>Close</Text>
            </TouchableOpacity>
          </TouchableOpacity>
        </TouchableOpacity>
      </Modal>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#ECE8E1',
  },
  scrollContent: {
    paddingBottom: 36,
  },
  header: {
    backgroundColor: '#456B50',
    paddingTop: 54,
    paddingBottom: 80,
    paddingHorizontal: 24,
    alignItems: 'center',
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
  leftCircle: {
    position: 'absolute',
    width: 220,
    height: 220,
    borderRadius: 110,
    backgroundColor: 'rgba(255,255,255,0.04)',
    bottom: 20,
    left: -50,
  },
  statusRow: {
    width: '100%',
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
  backButton: {
    alignSelf: 'flex-start',
    marginTop: 16,
    marginBottom: 10,
  },
  backText: {
    color: '#FFFFFF',
    fontSize: 30,
    fontWeight: '500',
  },
  avatarCircle: {
    width: 110,
    height: 110,
    borderRadius: 55,
    justifyContent: 'center',
    alignItems: 'center',
    marginTop: 8,
    marginBottom: 16,
    borderWidth: 3,
    borderColor: 'rgba(255,255,255,0.35)',
  },
  avatarText: {
    fontSize: 32,
    fontWeight: '700',
  },
  name: {
    color: '#F5F1E9',
    fontSize: 24,
    fontWeight: '700',
    marginBottom: 6,
    textAlign: 'center',
  },
  roleText: {
    color: 'rgba(245,241,233,0.75)',
    fontSize: 14,
    fontWeight: '500',
    marginBottom: 14,
    textAlign: 'center',
  },
  headerBadge: {
    backgroundColor: 'rgba(255,255,255,0.12)',
    paddingHorizontal: 18,
    paddingVertical: 10,
    borderRadius: 20,
  },
  headerBadgeText: {
    color: '#F5F1E9',
    fontSize: 13,
    fontWeight: '600',
  },
  statsCard: {
    marginTop: -36,
    marginHorizontal: 24,
    backgroundColor: '#F8F6F2',
    borderRadius: 28,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingVertical: 26,
    paddingHorizontal: 20,
    shadowColor: '#000',
    shadowOpacity: 0.06,
    shadowRadius: 12,
    shadowOffset: { width: 0, height: 4 },
    elevation: 3,
  },
  statItem: {
    flex: 1,
    alignItems: 'center',
  },
  statNumber: {
    color: '#2F563C',
    fontSize: 22,
    fontWeight: '700',
    marginBottom: 4,
  },
  statLabel: {
    color: '#8B8176',
    fontSize: 13,
    fontWeight: '500',
  },
  statDivider: {
    width: 1,
    height: 54,
    backgroundColor: '#DDD5CA',
  },
  body: {
    paddingHorizontal: 24,
    paddingTop: 26,
  },
  sectionTitle: {
    color: '#8B8176',
    fontSize: 13,
    fontWeight: '700',
    letterSpacing: 2,
    marginBottom: 18,
  },
  timelineSubtitle: {
    color: '#7E7368',
    fontSize: 13,
    fontWeight: '500',
    marginBottom: 18,
  },
  timelineScroll: {
    paddingHorizontal: 8,
    paddingTop: 50,
    paddingBottom: 8,
  },
  timelineContainer: {
    position: 'relative',
    minWidth: 600,
    height: 110,
  },
  timelineTrack: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 84,
    height: 4,
    borderRadius: 2,
    backgroundColor: '#E5DDD1',
  },
  timelineSlot: {
    position: 'absolute',
    top: 0,
    width: 130,
    marginLeft: -65,
    alignItems: 'center',
  },
  timelinePill: {
    paddingHorizontal: 10,
    paddingVertical: 5,
    borderRadius: 10,
    marginBottom: 6,
    maxWidth: 124,
  },
  timelinePillText: {
    color: '#FFFFFF',
    fontSize: 11,
    fontWeight: '700',
    textAlign: 'center',
  },
  timelineDot: {
    position: 'absolute',
    top: 79,
    alignSelf: 'center',
    width: 14,
    height: 14,
    borderRadius: 7,
    borderWidth: 2,
    borderColor: '#F8F6F2',
    zIndex: 2,
  },
  timelineDateLabel: {
    color: '#9A8F82',
    fontSize: 10,
    fontWeight: '500',
    marginTop: 0,
  },
  timelineLegend: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 14,
    marginTop: 14,
    paddingTop: 12,
    borderTopWidth: 1,
    borderTopColor: '#E5DDD1',
  },
  timelineLegendItem: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  timelineLegendDot: {
    width: 10,
    height: 10,
    borderRadius: 5,
  },
  timelineLegendText: {
    color: '#7E7368',
    fontSize: 12,
    fontWeight: '600',
  },
  timelineModalBackdrop: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.45)',
    justifyContent: 'flex-end',
  },
  timelineModalSheet: {
    backgroundColor: '#F8F6F2',
    borderTopLeftRadius: 24,
    borderTopRightRadius: 24,
    padding: 24,
    paddingBottom: 36,
  },
  timelineModalKind: {
    color: '#8B8176',
    fontSize: 12,
    fontWeight: '700',
    letterSpacing: 2,
    marginBottom: 8,
  },
  timelineModalTitle: {
    color: '#23372B',
    fontSize: 20,
    fontWeight: '700',
    marginBottom: 6,
  },
  timelineModalDate: {
    color: '#7E7368',
    fontSize: 14,
    fontWeight: '500',
    marginBottom: 18,
  },
  timelineModalCloseButton: {
    backgroundColor: '#456B50',
    paddingVertical: 14,
    borderRadius: 18,
    alignItems: 'center',
  },
  timelineModalCloseText: {
    color: '#F8F6F2',
    fontSize: 15,
    fontWeight: '700',
  },
  card: {
    backgroundColor: '#F8F6F2',
    borderRadius: 26,
    padding: 22,
    marginBottom: 20,
  },
  cardLabel: {
    color: '#7E7368',
    fontSize: 12,
    fontWeight: '700',
    marginBottom: 10,
    marginTop: 4,
  },
  cardText: {
    color: '#4A4138',
    fontSize: 15,
    lineHeight: 22,
    marginBottom: 12,
  },
  tokensWrap: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
    marginBottom: 10,
  },
  tokenChip: {
    paddingHorizontal: 14,
    paddingVertical: 9,
    borderRadius: 18,
    backgroundColor: '#EEF3EE',
    borderWidth: 1,
    borderColor: '#D7E8DA',
  },
  tokenChipText: {
    color: '#2F563C',
    fontSize: 13,
    fontWeight: '600',
  },
  availabilityRow: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 10,
    gap: 10,
  },
  availabilityDay: {
    width: 36,
    color: '#2F563C',
    fontSize: 13,
    fontWeight: '700',
  },
  availabilitySlots: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 6,
  },
  availabilityBadge: {
    backgroundColor: '#D7E8DA',
    borderRadius: 12,
    paddingHorizontal: 10,
    paddingVertical: 5,
  },
  availabilityBadgeText: {
    color: '#2F563C',
    fontSize: 13,
    fontWeight: '600',
  },
  daysGrid: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 18,
  },
  dayCell: {
    width: '13%',
    aspectRatio: 1,
    borderRadius: 14,
    backgroundColor: '#FCFBF8',
    borderWidth: 1,
    borderColor: '#E1D7CA',
    alignItems: 'center',
    justifyContent: 'center',
  },
  dayCellActive: {
    backgroundColor: '#D7E8DA',
    borderColor: '#BFD3C2',
  },
  dayCellText: {
    color: '#8B8176',
    fontSize: 13,
    fontWeight: '600',
  },
  dayCellTextActive: {
    color: '#2F563C',
    fontWeight: '700',
  },
  meetingRow: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#FCFBF8',
    borderRadius: 18,
    borderWidth: 1,
    borderColor: '#E1D7CA',
    padding: 14,
    marginBottom: 10,
  },
  meetingTimeBox: {
    width: 72,
    marginRight: 12,
  },
  meetingDay: {
    color: '#2F563C',
    fontSize: 12,
    fontWeight: '700',
    marginBottom: 2,
  },
  meetingTime: {
    color: '#4A4138',
    fontSize: 14,
    fontWeight: '700',
  },
  meetingInfo: {
    flex: 1,
  },
  meetingTitle: {
    color: '#23372B',
    fontSize: 14,
    fontWeight: '700',
    marginBottom: 2,
  },
  meetingDate: {
    color: '#9A8F82',
    fontSize: 12,
  },
  meetingBadge: {
    paddingHorizontal: 10,
    paddingVertical: 6,
    borderRadius: 14,
  },
  meetingBadgeConfirmed: {
    backgroundColor: '#D7E8DA',
  },
  meetingBadgePending: {
    backgroundColor: '#F5E8CC',
  },
  meetingBadgeText: {
    fontSize: 11,
    fontWeight: '700',
  },
  meetingBadgeTextConfirmed: {
    color: '#2F563C',
  },
  meetingBadgeTextPending: {
    color: '#7A5010',
  },
  actionsGrid: {
    marginBottom: 24,
  },
  actionButtonPrimary: {
    backgroundColor: '#4B7B57',
    borderRadius: 24,
    paddingVertical: 18,
    alignItems: 'center',
    marginBottom: 12,
  },
  actionButtonPrimaryText: {
    color: '#F8F6F2',
    fontSize: 16,
    fontWeight: '700',
  },
  actionButtonSecondary: {
    backgroundColor: '#D7E8DA',
    borderRadius: 24,
    paddingVertical: 18,
    alignItems: 'center',
    marginBottom: 12,
  },
  actionButtonSecondaryText: {
    color: '#2F563C',
    fontSize: 16,
    fontWeight: '700',
  },
  actionButtonDanger: {
    backgroundColor: '#FDF0EF',
    borderWidth: 1,
    borderColor: '#FAD4D4',
    borderRadius: 24,
    paddingVertical: 18,
    alignItems: 'center',
  },
  actionButtonDangerText: {
    color: '#D9534F',
    fontSize: 16,
    fontWeight: '700',
  },
  actionButtonRow: {
    flexDirection: 'row',
    gap: 12,
    marginBottom: 12,
  },
  actionButtonHalf: {
    flex: 1,
    marginBottom: 0,
  },
  goalInput: {
    backgroundColor: '#FCFBF8',
    borderRadius: 14,
    borderWidth: 1.5,
    borderColor: '#C8D9CA',
    padding: 14,
    fontSize: 15,
    color: '#23372B',
    minHeight: 90,
    textAlignVertical: 'top',
    marginBottom: 6,
  },
  goalCharCount: {
    color: '#B0A89E',
    fontSize: 12,
    textAlign: 'right',
    marginBottom: 14,
  },
  goalButtonRow: {
    flexDirection: 'row',
    gap: 10,
  },
  goalCancelButton: {
    flex: 1,
    backgroundColor: '#EDE8E1',
    borderRadius: 16,
    paddingVertical: 14,
    alignItems: 'center',
  },
  goalCancelText: {
    color: '#6B6158',
    fontSize: 15,
    fontWeight: '600',
  },
  goalSaveButton: {
    flex: 2,
    backgroundColor: '#4B7B57',
    borderRadius: 16,
    paddingVertical: 14,
    alignItems: 'center',
  },
  goalSaveText: {
    color: '#F8F6F2',
    fontSize: 15,
    fontWeight: '700',
  },
  goalEditButton: {
    alignSelf: 'flex-start',
    backgroundColor: '#EEF3EE',
    borderRadius: 14,
    paddingHorizontal: 16,
    paddingVertical: 10,
    marginTop: 6,
  },
  goalEditText: {
    color: '#2F563C',
    fontSize: 13,
    fontWeight: '700',
  },
  goalSummaryHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    gap: 12,
  },
  progressHeadline: {
    color: '#2E2A24',
    fontSize: 22,
    fontWeight: '700',
    marginTop: 4,
  },
  progressSubtext: {
    color: '#6E655A',
    fontSize: 13,
    fontWeight: '500',
    marginTop: 4,
  },
  progressTrack: {
    height: 10,
    backgroundColor: '#E1D9CF',
    borderRadius: 999,
    overflow: 'hidden',
    marginTop: 14,
    marginBottom: 18,
  },
  progressFill: {
    height: '100%',
    backgroundColor: '#456B50',
    borderRadius: 999,
  },
  milestoneComposer: {
    marginBottom: 18,
  },
  milestoneInput: {
    backgroundColor: '#FCFBF8',
    borderRadius: 14,
    borderWidth: 1.5,
    borderColor: '#C8D9CA',
    padding: 14,
    fontSize: 15,
    color: '#23372B',
    marginBottom: 10,
  },
  inlineLoader: {
    marginVertical: 12,
  },
  milestoneList: {
    paddingBottom: 6,
    gap: 12,
  },
  milestoneCard: {
    width: 210,
    backgroundColor: '#F3EEE7',
    borderRadius: 18,
    padding: 16,
    borderWidth: 1,
    borderColor: '#E3DACE',
  },
  milestoneCardActive: {
    borderColor: '#456B50',
    backgroundColor: '#EDF4EF',
  },
  milestoneCardHeader: {
    gap: 10,
  },
  milestoneCardTitle: {
    color: '#2E2A24',
    fontSize: 16,
    fontWeight: '700',
  },
  milestoneMeta: {
    color: '#6E655A',
    fontSize: 12,
    fontWeight: '500',
    marginTop: 8,
  },
  statusChip: {
    alignSelf: 'flex-start',
    paddingHorizontal: 10,
    paddingVertical: 6,
    borderRadius: 999,
  },
  statusPending: {
    backgroundColor: '#EEE4D0',
  },
  statusInProgress: {
    backgroundColor: '#DDE7F6',
  },
  statusCompleted: {
    backgroundColor: '#DCEBDF',
  },
  statusChipText: {
    color: '#2E2A24',
    fontSize: 11,
    fontWeight: '700',
  },
  milestoneDetailCard: {
    marginTop: 18,
    paddingTop: 18,
    borderTopWidth: 1,
    borderTopColor: '#E4DBD0',
  },
  milestoneDetailHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    gap: 12,
  },
  flexOne: {
    flex: 1,
  },
  milestoneDetailTitle: {
    color: '#2E2A24',
    fontSize: 20,
    fontWeight: '700',
    marginTop: 4,
  },
  statusFilterRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 10,
    marginBottom: 14,
  },
  statusFilterChip: {
    paddingHorizontal: 12,
    paddingVertical: 8,
    borderRadius: 999,
    borderWidth: 1,
    borderColor: '#D5CABC',
    backgroundColor: '#F7F3EC',
  },
  statusFilterChipActive: {
    backgroundColor: '#456B50',
    borderColor: '#456B50',
  },
  statusFilterText: {
    color: '#5E5348',
    fontSize: 12,
    fontWeight: '700',
  },
  statusFilterTextActive: {
    color: '#F8F6F2',
  },
  actionItemRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    paddingVertical: 10,
    borderBottomWidth: 1,
    borderBottomColor: '#ECE4DA',
  },
  actionItemCheckbox: {
    width: 28,
    height: 28,
    borderRadius: 14,
    borderWidth: 2,
    borderColor: '#456B50',
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#F8F6F2',
  },
  actionItemCheckboxActive: {
    backgroundColor: '#456B50',
  },
  actionItemCheckmark: {
    color: '#F8F6F2',
    fontWeight: '800',
  },
  actionItemContent: {
    flex: 1,
  },
  actionItemText: {
    color: '#2E2A24',
    fontSize: 15,
    fontWeight: '600',
  },
  actionItemTextDone: {
    textDecorationLine: 'line-through',
    color: '#6E655A',
  },
  actionItemMeta: {
    color: '#84796C',
    fontSize: 12,
    marginTop: 4,
  },
  actionItemComposer: {
    marginTop: 16,
  },
});
