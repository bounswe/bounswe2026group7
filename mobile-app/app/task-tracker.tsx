import { router, useLocalSearchParams } from 'expo-router';
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native';
import apiClient from '../api/client';
import { useRole } from '../components/RoleContext';
import { useProtectedSession } from '../components/useProtectedSession';

type TaskSummary = {
  id: number;
  title: string;
  dueDate?: string | null;
  status: 'PENDING' | 'SUBMITTED' | 'REVISION_REQUESTED' | 'COMPLETED';
  isOverdue: boolean;
  createdAt: string;
};

type AttachmentSummary = {
  id: string;
  downloadUrl: string;
  filename: string;
  contentType: string;
  sizeBytes: number;
};

type TaskSubmission = {
  id: number;
  submissionText: string;
  feedback?: string | null;
  submittedAt: string;
  reviewedAt?: string | null;
  attachments: AttachmentSummary[];
};

type TaskDetail = {
  id: number;
  mentorshipId: number;
  title: string;
  description?: string | null;
  dueDate?: string | null;
  status: TaskSummary['status'];
  isOverdue: boolean;
  createdAt: string;
  assignmentAttachments: AttachmentSummary[];
  submissions: TaskSubmission[];
};

function parseString(value: string | string[] | undefined) {
  return Array.isArray(value) ? value[0] : value ?? '';
}

function formatStatusLabel(status: TaskSummary['status']) {
  return status
    .toLowerCase()
    .split('_')
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ');
}

function formatDate(value?: string | null) {
  if (!value) return 'No due date';
  return new Date(value).toLocaleDateString('en-US', {
    month: 'long',
    day: 'numeric',
    year: 'numeric',
  });
}

function formatDateTime(value?: string | null) {
  if (!value) return '';
  return new Date(value).toLocaleString('en-GB', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

function statusStyles(status: TaskSummary['status']) {
  switch (status) {
    case 'COMPLETED':
      return { bg: '#D7E8DA', text: '#2F563C' };
    case 'SUBMITTED':
      return { bg: '#DCEAF9', text: '#255FA8' };
    case 'REVISION_REQUESTED':
      return { bg: '#FDF0EF', text: '#D9534F' };
    case 'PENDING':
    default:
      return { bg: '#F1E1BB', text: '#8A5D12' };
  }
}

function buildSectionTitle(isMentor: boolean, key: 'pending' | 'review' | 'completed') {
  if (key === 'pending') {
    return isMentor ? 'ASSIGNED TASKS' : 'READY TO WORK ON';
  }
  if (key === 'review') {
    return isMentor ? 'AWAITING YOUR REVIEW' : 'AWAITING FEEDBACK';
  }
  return 'COMPLETED';
}

export default function TaskTrackerScreen() {
  const params = useLocalSearchParams();
  const { role } = useRole();
  const { session, sessionLoading } = useProtectedSession('task-tracker');
  const isMentor = role === 'mentor';
  const connectedUserName = parseString(params.connectedUserName) || 'Your Mentor';
  const mentorshipId = parseString(params.mentorshipId);

  const [tasks, setTasks] = useState<TaskSummary[]>([]);
  const [selectedTask, setSelectedTask] = useState<TaskDetail | null>(null);
  const [listLoading, setListLoading] = useState(true);
  const [detailLoading, setDetailLoading] = useState(false);
  const [submissionDraft, setSubmissionDraft] = useState('');
  const [feedbackDraft, setFeedbackDraft] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [reviewing, setReviewing] = useState(false);
  const [taskComposerOpen, setTaskComposerOpen] = useState(false);
  const [taskTitleDraft, setTaskTitleDraft] = useState('');
  const [taskDescDraft, setTaskDescDraft] = useState('');
  const [taskDueDraft, setTaskDueDraft] = useState('');
  const [taskCreating, setTaskCreating] = useState(false);

  useEffect(() => {
    console.log('[tasks] route context', {
      mentorshipId,
      sourceScreen: parseString(params.sourceScreen),
      connectedUserName,
      connectedUserType: parseString(params.connectedUserType),
      storedUserId: session?.userId ?? null,
      storedRole: session?.role ?? null,
      roleFromContext: role,
    });
  }, [connectedUserName, mentorshipId, params.connectedUserType, params.sourceScreen, role, session?.role, session?.userId]);

  const fetchTasks = useCallback(async () => {
    if (sessionLoading) return;
    if (!session) {
      console.log('[tasks] skipping protected fetch because session is missing');
      setListLoading(false);
      return;
    }
    if (!mentorshipId) {
      setListLoading(false);
      return;
    }

    try {
      setListLoading(true);
      console.log('[tasks] loading tasks', { mentorshipId });
      const response = await apiClient.get(`/mentorships/${mentorshipId}/tasks`);
      setTasks(response.data ?? []);
    } catch (error: any) {
      console.error('[tasks] failed to load tasks', {
        status: error?.response?.status,
        data: error?.response?.data,
        mentorshipId,
      });
      Alert.alert('Error', 'Could not load tasks for this mentorship.');
    } finally {
      setListLoading(false);
    }
  }, [mentorshipId, session, sessionLoading]);

  useEffect(() => {
    void fetchTasks();
  }, [fetchTasks]);

  const openTask = async (taskId: number) => {
    try {
      setDetailLoading(true);
      const response = await apiClient.get(`/tasks/${taskId}`);
      setSelectedTask(response.data);
      setSubmissionDraft('');
      setFeedbackDraft('');
    } catch (error) {
      console.error('Failed to load task detail:', error);
      Alert.alert('Error', 'Could not load task details.');
    } finally {
      setDetailLoading(false);
    }
  };

  const pendingTasks = useMemo(
    () => tasks.filter((task) => task.status === 'PENDING' || task.status === 'REVISION_REQUESTED'),
    [tasks]
  );
  const reviewTasks = useMemo(() => tasks.filter((task) => task.status === 'SUBMITTED'), [tasks]);
  const completedTasks = useMemo(() => tasks.filter((task) => task.status === 'COMPLETED'), [tasks]);

  const latestSubmission = selectedTask?.submissions?.[0] ?? null;
  const canSubmit =
    !isMentor && !!selectedTask && (selectedTask.status === 'PENDING' || selectedTask.status === 'REVISION_REQUESTED');
  const canReview =
    isMentor &&
    !!selectedTask &&
    selectedTask.status === 'SUBMITTED' &&
    !!latestSubmission &&
    !latestSubmission.reviewedAt;

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

  const submitTask = async () => {
    if (!selectedTask || !submissionDraft.trim()) {
      Alert.alert('Missing submission', 'Please enter the work you want to submit.');
      return;
    }

    try {
      setSubmitting(true);
      const response = await apiClient.post(`/tasks/${selectedTask.id}/submission`, {
        submissionText: submissionDraft.trim(),
      });
      setSelectedTask(response.data);
      setSubmissionDraft('');
      await fetchTasks();
      Alert.alert('Success', 'Your task submission has been sent to your mentor.');
    } catch (error: any) {
      const message =
        error.response?.data?.message ||
        error.response?.data?.error ||
        'Could not submit this task.';
      Alert.alert('Error', message);
    } finally {
      setSubmitting(false);
    }
  };

  const createTask = async () => {
    if (!mentorshipId || !taskTitleDraft.trim()) return;
    setTaskCreating(true);
    try {
      let dueDate: string | null = null;
      if (taskDueDraft.trim()) {
        const d = new Date(taskDueDraft.trim());
        if (!isNaN(d.getTime())) dueDate = d.toISOString();
      }
      await apiClient.post(`/mentorships/${mentorshipId}/tasks`, {
        title: taskTitleDraft.trim(),
        description: taskDescDraft.trim() || null,
        dueDate,
      });
      setTaskTitleDraft('');
      setTaskDescDraft('');
      setTaskDueDraft('');
      setTaskComposerOpen(false);
      await fetchTasks();
      Alert.alert('Success', 'Task created and assigned to your mentee.');
    } catch (error: any) {
      Alert.alert('Error', error?.response?.data?.message || 'Could not create task.');
    } finally {
      setTaskCreating(false);
    }
  };

  const reviewTask = async (status: 'COMPLETED' | 'REVISION_REQUESTED') => {
    if (!selectedTask || !feedbackDraft.trim()) {
      Alert.alert('Missing feedback', 'Please add feedback before reviewing this submission.');
      return;
    }

    try {
      setReviewing(true);
      const response = await apiClient.patch(`/tasks/${selectedTask.id}/feedback`, {
        feedback: feedbackDraft.trim(),
        status,
      });
      setSelectedTask(response.data);
      setFeedbackDraft('');
      await fetchTasks();
      Alert.alert(
        'Success',
        status === 'COMPLETED'
          ? 'The task has been marked as completed.'
          : 'Revision feedback has been sent to your mentee.'
      );
    } catch (error: any) {
      const message =
        error.response?.data?.message ||
        error.response?.data?.error ||
        'Could not review this task.';
      Alert.alert('Error', message);
    } finally {
      setReviewing(false);
    }
  };

  const renderTaskCard = (task: TaskSummary) => {
    const badge = statusStyles(task.status);
    return (
      <TouchableOpacity key={task.id} style={styles.taskCard} onPress={() => openTask(task.id)}>
        <View style={styles.taskCardLeft}>
          <Text style={styles.taskTitle}>{task.title}</Text>
          <Text style={styles.taskMeta}>
            Due: {formatDate(task.dueDate)}
            {task.isOverdue ? ' · Overdue' : ''}
          </Text>
        </View>

        <View style={[styles.statusBadge, { backgroundColor: badge.bg }]}>
          <Text style={[styles.statusBadgeText, { color: badge.text }]}>{formatStatusLabel(task.status)}</Text>
        </View>
      </TouchableOpacity>
    );
  };

  if (selectedTask) {
    const badge = statusStyles(selectedTask.status);

    return (
      <View style={styles.container}>
        <View style={styles.headerShell}>
          <View style={styles.statusRow}>
            <Text style={styles.statusText}>
              {new Date().toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit', hour12: false })}
            </Text>
            <Text style={styles.statusIcons}>●●●</Text>
          </View>

          <TouchableOpacity onPress={() => setSelectedTask(null)} style={styles.backButton}>
            <Text style={styles.backButtonText}>← Back</Text>
          </TouchableOpacity>

          <Text style={styles.title}>Task Detail</Text>
          <Text style={styles.subtitle}>
            {isMentor ? 'Review your mentee submission' : `Assigned by ${connectedUserName}`}
          </Text>
        </View>

        {detailLoading ? (
          <View style={styles.centeredState}>
            <ActivityIndicator size="large" color="#3FA06F" />
          </View>
        ) : (
          <ScrollView style={styles.scrollArea} contentContainerStyle={styles.content}>
            <View style={styles.detailCard}>
              <View style={styles.detailTopRow}>
                <Text style={styles.detailTitle}>{selectedTask.title}</Text>
                <View style={[styles.statusBadge, { backgroundColor: badge.bg }]}>
                  <Text style={[styles.statusBadgeText, { color: badge.text }]}>
                    {formatStatusLabel(selectedTask.status)}
                  </Text>
                </View>
              </View>

              <Text style={styles.detailMeta}>Due: {formatDate(selectedTask.dueDate)}</Text>
              <Text style={styles.detailMeta}>Created: {formatDate(selectedTask.createdAt)}</Text>
              {selectedTask.isOverdue ? <Text style={styles.overdueText}>This task is currently overdue.</Text> : null}

              {!!selectedTask.description && (
                <>
                  <Text style={styles.sectionLabel}>DESCRIPTION</Text>
                  <Text style={styles.detailBody}>{selectedTask.description}</Text>
                </>
              )}

              {selectedTask.assignmentAttachments.length > 0 ? (
                <>
                  <Text style={styles.sectionLabel}>ASSIGNMENT FILES</Text>
                  {selectedTask.assignmentAttachments.map((attachment) => (
                    <View key={attachment.id} style={styles.attachmentRow}>
                      <Text style={styles.attachmentName}>{attachment.filename}</Text>
                    </View>
                  ))}
                </>
              ) : null}

              <Text style={styles.sectionLabel}>SUBMISSION HISTORY</Text>
              {selectedTask.submissions.length === 0 ? (
                <Text style={styles.placeholderText}>No submission has been made for this task yet.</Text>
              ) : (
                selectedTask.submissions.map((submission) => (
                  <View key={submission.id} style={styles.submissionCard}>
                    <Text style={styles.submissionLabel}>Submitted {formatDateTime(submission.submittedAt)}</Text>
                    <Text style={styles.detailBody}>{submission.submissionText}</Text>

                    {submission.attachments.length > 0 ? (
                      <View style={styles.attachmentGroup}>
                        {submission.attachments.map((attachment) => (
                          <View key={attachment.id} style={styles.attachmentRow}>
                            <Text style={styles.attachmentName}>{attachment.filename}</Text>
                          </View>
                        ))}
                      </View>
                    ) : null}

                    {!!submission.feedback && (
                      <View style={styles.feedbackBox}>
                        <Text style={styles.feedbackTitle}>Mentor Feedback</Text>
                        <Text style={styles.feedbackText}>{submission.feedback}</Text>
                        {submission.reviewedAt ? (
                          <Text style={styles.feedbackMeta}>Reviewed {formatDateTime(submission.reviewedAt)}</Text>
                        ) : null}
                      </View>
                    )}
                  </View>
                ))
              )}

              {canSubmit ? (
                <View style={styles.formCard}>
                  <Text style={styles.formTitle}>
                    {selectedTask.status === 'REVISION_REQUESTED' ? 'Submit Revision' : 'Submit Work'}
                  </Text>
                  <TextInput
                    style={[styles.input, styles.multiLineInput]}
                    value={submissionDraft}
                    onChangeText={setSubmissionDraft}
                    placeholder="Describe what you completed for this task"
                    placeholderTextColor="#A5A5A5"
                    multiline
                    textAlignVertical="top"
                  />
                  <TouchableOpacity
                    style={[styles.primaryButton, submitting && styles.disabledButton]}
                    onPress={submitTask}
                    disabled={submitting}
                  >
                    <Text style={styles.primaryButtonText}>{submitting ? 'Submitting...' : 'Submit Task'}</Text>
                  </TouchableOpacity>
                </View>
              ) : null}

              {canReview ? (
                <View style={styles.formCard}>
                  <Text style={styles.formTitle}>Review Latest Submission</Text>
                  <TextInput
                    style={[styles.input, styles.multiLineInput]}
                    value={feedbackDraft}
                    onChangeText={setFeedbackDraft}
                    placeholder="Write feedback for your mentee"
                    placeholderTextColor="#A5A5A5"
                    multiline
                    textAlignVertical="top"
                  />
                  <View style={styles.buttonRow}>
                    <TouchableOpacity
                      style={[styles.secondaryButton, reviewing && styles.disabledButton]}
                      onPress={() => reviewTask('REVISION_REQUESTED')}
                      disabled={reviewing}
                    >
                      <Text style={styles.secondaryButtonText}>Request Revision</Text>
                    </TouchableOpacity>
                    <TouchableOpacity
                      style={[styles.primaryButton, reviewing && styles.disabledButton, styles.flexButton]}
                      onPress={() => reviewTask('COMPLETED')}
                      disabled={reviewing}
                    >
                      <Text style={styles.primaryButtonText}>Mark Complete</Text>
                    </TouchableOpacity>
                  </View>
                </View>
              ) : null}
            </View>
          </ScrollView>
        )}
      </View>
    );
  }

  if (!mentorshipId) {
    return (
      <View style={styles.container}>
        <View style={styles.centeredState}>
          <Text style={styles.emptyTitle}>No mentorship selected</Text>
          <Text style={styles.emptyText}>Open this screen from an active mentorship profile to load tasks.</Text>
        </View>
      </View>
    );
  }

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
          <View style={styles.headerTitleRow}>
            <TouchableOpacity onPress={() => router.back()} style={styles.inlineBackButton}>
              <Text style={styles.inlineBackButtonText}>←</Text>
            </TouchableOpacity>
            <View>
              <Text style={styles.title}>My Tasks</Text>
              <Text style={styles.subtitle}>{isMentor ? connectedUserName : `Mentor: ${connectedUserName}`}</Text>
            </View>
          </View>

          <TouchableOpacity onPress={() => fetchTasks()}>
            <Text style={styles.refreshText}>Refresh</Text>
          </TouchableOpacity>
        </View>

        {isMentor && (
          <>
            <TouchableOpacity
              style={styles.createTaskBtn}
              onPress={() => setTaskComposerOpen((v) => !v)}
            >
              <Text style={styles.createTaskBtnText}>
                {taskComposerOpen ? 'Hide Form' : '+ Create Task'}
              </Text>
            </TouchableOpacity>

            {taskComposerOpen && (
              <View style={styles.composerCard}>
                <Text style={styles.formTitle}>New Task</Text>
                <TextInput
                  style={styles.input}
                  value={taskTitleDraft}
                  onChangeText={setTaskTitleDraft}
                  placeholder="Task title"
                  placeholderTextColor="#A5A5A5"
                />
                <TextInput
                  style={[styles.input, styles.multiLineInput, { marginTop: 10 }]}
                  value={taskDescDraft}
                  onChangeText={setTaskDescDraft}
                  placeholder="Description (optional)"
                  placeholderTextColor="#A5A5A5"
                  multiline
                  textAlignVertical="top"
                />
                <TextInput
                  style={[styles.input, { marginTop: 10 }]}
                  value={taskDueDraft}
                  onChangeText={setTaskDueDraft}
                  placeholder="Due date (YYYY-MM-DD, optional)"
                  placeholderTextColor="#A5A5A5"
                  autoCapitalize="none"
                />
                <TouchableOpacity
                  style={[styles.primaryButton, (!taskTitleDraft.trim() || taskCreating) && styles.disabledButton]}
                  onPress={createTask}
                  disabled={!taskTitleDraft.trim() || taskCreating}
                >
                  <Text style={styles.primaryButtonText}>
                    {taskCreating ? 'Creating...' : 'Create Task'}
                  </Text>
                </TouchableOpacity>
              </View>
            )}
          </>
        )}

        {listLoading ? (
          <View style={styles.centeredState}>
            <ActivityIndicator size="large" color="#3FA06F" />
          </View>
        ) : (
          <>
            <Text style={styles.sectionTitle}>{buildSectionTitle(isMentor, 'pending')}</Text>
            {pendingTasks.length === 0 ? (
              <View style={styles.emptyCard}>
                <Text style={styles.emptyText}>No open tasks in this section.</Text>
              </View>
            ) : (
              pendingTasks.map(renderTaskCard)
            )}

            <Text style={styles.sectionTitle}>{buildSectionTitle(isMentor, 'review')}</Text>
            {reviewTasks.length === 0 ? (
              <View style={styles.emptyCard}>
                <Text style={styles.emptyText}>Nothing is waiting for review right now.</Text>
              </View>
            ) : (
              reviewTasks.map(renderTaskCard)
            )}

            <Text style={styles.sectionTitle}>{buildSectionTitle(isMentor, 'completed')}</Text>
            {completedTasks.length === 0 ? (
              <View style={styles.emptyCard}>
                <Text style={styles.emptyText}>Completed tasks will appear here.</Text>
              </View>
            ) : (
              completedTasks.map(renderTaskCard)
            )}
          </>
        )}
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#EEF0F4',
  },
  scrollArea: {
    flex: 1,
  },
  content: {
    paddingHorizontal: 24,
    paddingTop: 54,
    paddingBottom: 36,
  },
  centeredState: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    paddingHorizontal: 24,
  },
  statusRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 24,
  },
  statusText: {
    fontSize: 16,
    fontWeight: '700',
    color: '#2B2B2B',
  },
  statusIcons: {
    fontSize: 16,
    fontWeight: '700',
    color: '#666666',
  },
  headerRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 28,
  },
  headerShell: {
    backgroundColor: '#FFFFFF',
    paddingTop: 54,
    paddingHorizontal: 24,
    paddingBottom: 20,
    borderBottomLeftRadius: 28,
    borderBottomRightRadius: 28,
  },
  headerTitleRow: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  inlineBackButton: {
    marginRight: 12,
    padding: 5,
  },
  inlineBackButtonText: {
    fontSize: 24,
    color: '#1D1D38',
    fontWeight: '700',
  },
  backButton: {
    alignSelf: 'flex-start',
    backgroundColor: '#F2F4F8',
    borderRadius: 16,
    paddingHorizontal: 14,
    paddingVertical: 8,
    marginBottom: 16,
  },
  backButtonText: {
    color: '#1D1D38',
    fontSize: 14,
    fontWeight: '700',
  },
  title: {
    fontSize: 28,
    fontWeight: '700',
    color: '#1D1D38',
  },
  subtitle: {
    fontSize: 14,
    color: '#6A6A7A',
    marginTop: 4,
  },
  refreshText: {
    fontSize: 16,
    fontWeight: '700',
    color: '#3FA06F',
  },
  sectionTitle: {
    color: '#8C8A8A',
    fontSize: 13,
    fontWeight: '700',
    letterSpacing: 2,
    marginBottom: 14,
    marginTop: 6,
  },
  taskCard: {
    backgroundColor: '#F8F8F7',
    borderRadius: 24,
    padding: 20,
    marginBottom: 18,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 16,
  },
  taskCardLeft: {
    flex: 1,
  },
  taskTitle: {
    fontSize: 18,
    fontWeight: '700',
    color: '#1D1D38',
    marginBottom: 6,
  },
  taskMeta: {
    fontSize: 14,
    color: '#666666',
  },
  statusBadge: {
    borderRadius: 16,
    paddingHorizontal: 14,
    paddingVertical: 8,
  },
  statusBadgeText: {
    fontSize: 12,
    fontWeight: '700',
  },
  emptyCard: {
    backgroundColor: '#F8F8F7',
    borderRadius: 24,
    padding: 18,
    marginBottom: 18,
  },
  emptyTitle: {
    fontSize: 20,
    fontWeight: '700',
    color: '#1D1D38',
    marginBottom: 8,
  },
  emptyText: {
    fontSize: 14,
    lineHeight: 20,
    color: '#666666',
    textAlign: 'center',
  },
  detailCard: {
    backgroundColor: '#F8F8F7',
    borderRadius: 24,
    padding: 20,
    marginTop: 20,
    marginBottom: 32,
  },
  detailTopRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    gap: 12,
  },
  detailTitle: {
    flex: 1,
    fontSize: 24,
    fontWeight: '700',
    color: '#1D1D38',
    marginBottom: 8,
  },
  detailMeta: {
    fontSize: 14,
    color: '#666666',
    marginBottom: 6,
  },
  overdueText: {
    color: '#D9534F',
    fontSize: 14,
    fontWeight: '700',
    marginTop: 4,
  },
  sectionLabel: {
    color: '#8C8A8A',
    fontSize: 12,
    fontWeight: '700',
    letterSpacing: 1.2,
    marginTop: 18,
    marginBottom: 10,
  },
  detailBody: {
    fontSize: 15,
    color: '#2B2B2B',
    lineHeight: 22,
  },
  placeholderText: {
    fontSize: 14,
    color: '#777777',
    lineHeight: 20,
  },
  attachmentGroup: {
    marginTop: 12,
  },
  attachmentRow: {
    backgroundColor: '#EEF3EE',
    borderRadius: 14,
    paddingHorizontal: 14,
    paddingVertical: 10,
    marginTop: 8,
  },
  attachmentName: {
    color: '#2F563C',
    fontSize: 13,
    fontWeight: '600',
  },
  submissionCard: {
    backgroundColor: '#FFFFFF',
    borderRadius: 20,
    padding: 16,
    marginTop: 12,
    borderWidth: 1,
    borderColor: '#E5E5E5',
  },
  submissionLabel: {
    fontSize: 12,
    fontWeight: '700',
    color: '#6A6A7A',
    marginBottom: 10,
  },
  feedbackBox: {
    marginTop: 14,
    backgroundColor: '#F0F7F2',
    borderRadius: 16,
    padding: 14,
  },
  feedbackTitle: {
    fontSize: 13,
    fontWeight: '700',
    color: '#2F563C',
    marginBottom: 8,
  },
  feedbackText: {
    fontSize: 14,
    lineHeight: 20,
    color: '#2B2B2B',
  },
  feedbackMeta: {
    fontSize: 12,
    color: '#6A6A7A',
    marginTop: 8,
  },
  formCard: {
    marginTop: 18,
    backgroundColor: '#FFFFFF',
    borderRadius: 20,
    padding: 18,
    borderWidth: 1,
    borderColor: '#E5E5E5',
  },
  formTitle: {
    fontSize: 18,
    fontWeight: '700',
    color: '#1D1D38',
    marginBottom: 12,
  },
  input: {
    borderRadius: 18,
    borderWidth: 1.5,
    borderColor: '#D7D7D7',
    backgroundColor: '#FAFAFA',
    paddingHorizontal: 14,
    paddingVertical: 14,
    fontSize: 15,
    color: '#2B2B2B',
  },
  multiLineInput: {
    minHeight: 130,
  },
  buttonRow: {
    flexDirection: 'row',
    gap: 12,
    marginTop: 16,
  },
  primaryButton: {
    marginTop: 16,
    backgroundColor: '#3FA06F',
    borderRadius: 18,
    paddingVertical: 16,
    alignItems: 'center',
  },
  flexButton: {
    flex: 1,
  },
  secondaryButton: {
    flex: 1,
    marginTop: 16,
    backgroundColor: '#F4E8E7',
    borderRadius: 18,
    paddingVertical: 16,
    alignItems: 'center',
  },
  primaryButtonText: {
    color: '#FFFFFF',
    fontSize: 15,
    fontWeight: '700',
  },
  secondaryButtonText: {
    color: '#B94A48',
    fontSize: 15,
    fontWeight: '700',
  },
  disabledButton: {
    opacity: 0.6,
  },
  createTaskBtn: {
    backgroundColor: '#3FA06F',
    borderRadius: 18,
    paddingVertical: 14,
    alignItems: 'center',
    marginBottom: 18,
  },
  createTaskBtnText: {
    color: '#FFFFFF',
    fontSize: 15,
    fontWeight: '700',
  },
  composerCard: {
    backgroundColor: '#F8F8F7',
    borderRadius: 24,
    padding: 20,
    marginBottom: 20,
    borderWidth: 1,
    borderColor: '#E5E5E5',
  },
});
