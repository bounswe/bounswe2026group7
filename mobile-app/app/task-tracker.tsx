import { router, useLocalSearchParams } from 'expo-router';
import React, { useState } from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  TouchableOpacity,
} from 'react-native';

type PendingTask = {
  id: number;
  title: string;
  due: 'Today' | 'Tomorrow';
};

type ReviewTask = {
  id: number;
  title: string;
  description: string;
  mentorInitials: string;
  mentorName: string;
  status: string;
};

type CompletedTask = {
  id: number;
  title: string;
  checked: boolean;
};

function parseString(value: string | string[] | undefined) {
  return Array.isArray(value) ? value[0] : value ?? '';
}

export default function TaskTrackerScreen() {
  const params = useLocalSearchParams();
  const connectedUserName = parseString(params.connectedUserName) || 'Your Mentor';

  const [pendingTasks] = useState<PendingTask[]>([
    { id: 1, title: 'Mentor List with FlatList', due: 'Today' },
    { id: 2, title: 'AsyncStorage Token\nManagement', due: 'Tomorrow' },
  ]);

  const [reviewTasks] = useState<ReviewTask[]>([
    {
      id: 1,
      title: 'Registration Screen\nImplementation',
      description: 'Form validation added, awaiting mentor\nreview',
      mentorInitials: connectedUserName.substring(0, 2).toUpperCase(),
      mentorName: connectedUserName,
      status: 'In Review',
    },
  ]);

  const [completedTasks, setCompletedTasks] = useState<CompletedTask[]>([
    { id: 1, title: 'Expo Project Setup', checked: true },
    { id: 2, title: 'React Navigation Integration', checked: true },
  ]);

  const toggleCompleted = (id: number) => {
    setCompletedTasks((prev) =>
      prev.map((task) =>
        task.id === id ? { ...task, checked: !task.checked } : task
      )
    );
  };

  return (
    <View style={styles.container}>
      <ScrollView
        style={styles.scrollArea}
        contentContainerStyle={styles.content}
        showsVerticalScrollIndicator={false}
      >
        <View style={styles.statusRow}>
          <Text style={styles.statusText}>{new Date().toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit', hour12: false })}</Text>
          <Text style={styles.statusIcons}>●●●</Text>
        </View>

        <View style={styles.headerRow}>
          <View style={{ flexDirection: 'row', alignItems: 'center' }}>
            <TouchableOpacity 
              onPress={() => router.back()} 
              style={{ marginRight: 12, padding: 5 }}
            >
              <Text style={{ fontSize: 24, color: '#1D1D38', fontWeight: 'bold' }}>←</Text>
            </TouchableOpacity>
            <Text style={styles.title}>My Tasks</Text>
          </View>
          <TouchableOpacity>
            <Text style={styles.addText}>+ Add</Text>
          </TouchableOpacity>
        </View>

        <Text style={styles.sectionTitle}>PENDING</Text>

        {pendingTasks.map((task) => (
          <View key={task.id} style={styles.pendingCard}>
            <View style={styles.pendingRow}>
              <View style={styles.emptyCircle} />
              <View style={styles.pendingTextArea}>
                <Text style={styles.pendingTitle}>{task.title}</Text>

                <View
                  style={[
                    styles.dueBadge,
                    task.due === 'Today' ? styles.todayBadge : styles.tomorrowBadge,
                  ]}
                >
                  <Text
                    style={[
                      styles.dueBadgeText,
                      task.due === 'Today'
                        ? styles.todayBadgeText
                        : styles.tomorrowBadgeText,
                    ]}
                  >
                    {task.due}
                  </Text>
                </View>
              </View>
            </View>
          </View>
        ))}

        <Text style={styles.sectionTitle}>AWAITING FEEDBACK</Text>

        {reviewTasks.map((task) => (
          <View key={task.id} style={styles.reviewCard}>
            <View style={styles.reviewLine} />

            <View style={styles.reviewContent}>
              <Text style={styles.reviewTitle}>{task.title}</Text>
              <Text style={styles.reviewDescription}>{task.description}</Text>

              <View style={styles.reviewFooter}>
                <View style={styles.mentorPill}>
                  <Text style={styles.mentorPillText}>{task.mentorInitials}</Text>
                </View>

                <Text style={styles.mentorName}>{task.mentorName}</Text>

                <View style={styles.reviewBadge}>
                  <Text style={styles.reviewBadgeText}>{task.status}</Text>
                </View>
              </View>
            </View>
          </View>
        ))}

        <Text style={styles.sectionTitle}>COMPLETED</Text>

        {completedTasks.map((task) => (
          <TouchableOpacity
            key={task.id}
            style={[
              styles.completedCard,
              !task.checked && styles.completedCardInactive,
            ]}
            onPress={() => toggleCompleted(task.id)}
            activeOpacity={0.8}
          >
            <View
              style={[
                styles.checkCircle,
                task.checked ? styles.checkCircleActive : styles.checkCircleInactive,
              ]}
            >
              {task.checked && <Text style={styles.checkMark}>✓</Text>}
            </View>

            <Text
              style={[
                styles.completedText,
                task.checked && styles.completedTextChecked,
                !task.checked && styles.completedTextUnchecked,
              ]}
            >
              {task.title}
            </Text>
          </TouchableOpacity>
        ))}
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
  title: {
    fontSize: 28,
    fontWeight: '700',
    color: '#1D1D38',
  },
  addText: {
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
  pendingCard: {
    backgroundColor: '#F8F8F7',
    borderRadius: 24,
    padding: 20,
    marginBottom: 18,
  },
  pendingRow: {
    flexDirection: 'row',
    alignItems: 'flex-start',
  },
  emptyCircle: {
    width: 34,
    height: 34,
    borderRadius: 17,
    borderWidth: 2,
    borderColor: '#D2D2D2',
    marginRight: 16,
    marginTop: 4,
  },
  pendingTextArea: {
    flex: 1,
  },
  pendingTitle: {
    fontSize: 18,
    fontWeight: '700',
    color: '#1D1D38',
    lineHeight: 26,
    marginBottom: 12,
  },
  dueBadge: {
    alignSelf: 'flex-start',
    paddingHorizontal: 16,
    paddingVertical: 8,
    borderRadius: 16,
  },
  todayBadge: {
    backgroundColor: '#F2E4C9',
  },
  tomorrowBadge: {
    backgroundColor: '#DCEAF9',
  },
  dueBadgeText: {
    fontSize: 12,
    fontWeight: '700',
  },
  todayBadgeText: {
    color: '#9B6A1B',
  },
  tomorrowBadgeText: {
    color: '#295FAF',
  },
  reviewCard: {
    backgroundColor: '#F8F8F7',
    borderRadius: 24,
    padding: 18,
    marginBottom: 22,
    flexDirection: 'row',
  },
  reviewLine: {
    width: 5,
    borderRadius: 3,
    backgroundColor: '#4FA06F',
    marginRight: 16,
  },
  reviewContent: {
    flex: 1,
  },
  reviewTitle: {
    fontSize: 18,
    fontWeight: '700',
    color: '#1D1D38',
    lineHeight: 26,
    marginBottom: 8,
  },
  reviewDescription: {
    fontSize: 14,
    color: '#8C8A8A',
    lineHeight: 21,
    marginBottom: 14,
  },
  reviewFooter: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  mentorPill: {
    width: 36,
    height: 36,
    borderRadius: 18,
    backgroundColor: '#D7E8DA',
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: 10,
  },
  mentorPillText: {
    color: '#2F563C',
    fontSize: 14,
    fontWeight: '700',
  },
  mentorName: {
    fontSize: 14,
    color: '#7E7368',
    fontWeight: '500',
  },
  reviewBadge: {
    marginLeft: 'auto',
    backgroundColor: '#F2E4C9',
    paddingHorizontal: 16,
    paddingVertical: 8,
    borderRadius: 16,
  },
  reviewBadgeText: {
    color: '#9B6A1B',
    fontSize: 12,
    fontWeight: '700',
  },
  completedCard: {
    backgroundColor: '#F8F8F7',
    borderRadius: 24,
    paddingHorizontal: 18,
    paddingVertical: 20,
    marginBottom: 18,
    flexDirection: 'row',
    alignItems: 'center',
  },
  completedCardInactive: {
    opacity: 0.7,
  },
  checkCircle: {
    width: 36,
    height: 36,
    borderRadius: 18,
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: 16,
  },
  checkCircleActive: {
    backgroundColor: '#8CC6AE',
  },
  checkCircleInactive: {
    backgroundColor: '#E2E2E2',
  },
  checkMark: {
    color: '#FFFFFF',
    fontSize: 18,
    fontWeight: '700',
  },
  completedText: {
    fontSize: 17,
    flex: 1,
  },
  completedTextChecked: {
    color: '#B0B0B0',
    textDecorationLine: 'line-through',
  },
  completedTextUnchecked: {
    color: '#4A4A4A',
    textDecorationLine: 'none',
    fontWeight: '500',
  },
});