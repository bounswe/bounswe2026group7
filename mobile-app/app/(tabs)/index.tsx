import React from 'react';
import { router } from 'expo-router';
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  ScrollView,
} from 'react-native';
import { useRole } from '../../components/RoleContext';

type MeetingItem = {
  id: string;
  day: string;
  date: string;
  time: string;
  title: string;
  status: 'confirmed' | 'pending';
};

type ConnectionCard = {
  id: string;
  initials: string;
  avatarBg: string;
  avatarText: string;
  name: string;
  subtitle: string;
  progress: number;
  type: 'mentor' | 'mentee';
  department?: string;
  title?: string;
  about?: string;
  interests?: string[];
  goals?: string[];
  mentoringGoals?: string[];
  preferences?: string[];
  meetings: MeetingItem[];
  stats: {
    first: { label: string; value: string };
    second: { label: string; value: string };
    third: { label: string; value: string };
  };
};

export default function HomeScreen() {
  const { role } = useRole();
  const isMentor = role === 'mentor';

  const activeMentees: ConnectionCard[] = [
    {
      id: '1',
      initials: 'ZD',
      avatarBg: '#DFD9C9',
      avatarText: '#66582F',
      name: 'Zeynep Demir',
      subtitle: 'Goal: Learn React Native · Week 3',
      progress: 65,
      type: 'mentee',
      department: 'Computer Engineering',
      about:
        '3rd year student focused on mobile development and wants to build stronger React Native projects.',
      interests: ['React Native', 'Frontend', 'UI'],
      goals: ['Learn React Native', 'Ship one portfolio app'],
      meetings: [
        {
          id: 'm1',
          day: 'Tue',
          date: 'Apr 8',
          time: '15:00',
          title: 'Weekly Check-in',
          status: 'confirmed',
        },
        {
          id: 'm2',
          day: 'Fri',
          date: 'Apr 11',
          time: '17:30',
          title: 'Code Review',
          status: 'pending',
        },
      ],
      stats: {
        first: { label: 'Tasks', value: '12' },
        second: { label: 'Meetings', value: '3' },
        third: { label: 'Progress', value: '65%' },
      },
    },
    {
      id: '2',
      initials: 'AC',
      avatarBg: '#CCD6E5',
      avatarText: '#4A5D7A',
      name: 'Ali Çetin',
      subtitle: 'Goal: Backend API Design · Week 1',
      progress: 20,
      type: 'mentee',
      department: 'Software Engineering',
      about:
        'Interested in backend systems and wants to improve API design fundamentals.',
      interests: ['Backend', 'Node.js', 'System Design'],
      goals: ['Design better REST APIs', 'Understand auth flows'],
      meetings: [
        {
          id: 'm3',
          day: 'Wed',
          date: 'Apr 9',
          time: '14:00',
          title: 'API Review',
          status: 'confirmed',
        },
      ],
      stats: {
        first: { label: 'Tasks', value: '4' },
        second: { label: 'Meetings', value: '1' },
        third: { label: 'Progress', value: '20%' },
      },
    },
  ];

  const activeMentors: ConnectionCard[] = [
    {
      id: '1',
      initials: 'BA',
      avatarBg: '#D7E8DA',
      avatarText: '#2F563C',
      name: 'Burak Afşar',
      subtitle: 'Senior Software Engineer',
      progress: 45,
      type: 'mentor',
      title: 'Senior Software Engineer · Mobile',
      about:
        'Focused on mobile architecture, clean code and long-term growth for junior developers.',
      interests: ['Swift', 'React Native', 'Mobile'],
      mentoringGoals: ['Guide junior developers', 'Improve project structure'],
      preferences: ['Motivated', 'Consistent', 'Open to feedback'],
      meetings: [
        {
          id: 'm4',
          day: 'Tue',
          date: 'Apr 8',
          time: '15:00',
          title: 'Weekly Check-in',
          status: 'confirmed',
        },
        {
          id: 'm5',
          day: 'Fri',
          date: 'Apr 11',
          time: '17:30',
          title: 'Code Review',
          status: 'pending',
        },
      ],
      stats: {
        first: { label: 'Rating', value: '4.9' },
        second: { label: 'Reviews', value: '24' },
        third: { label: 'Mentees', value: '8' },
      },
    },
    {
      id: '2',
      initials: 'OA',
      avatarBg: '#E2D1E6',
      avatarText: '#6D3F72',
      name: 'Övgü Su',
      subtitle: 'UI/UX Designer',
      progress: 80,
      type: 'mentor',
      title: 'UI/UX Designer · Product Design',
      about:
        'Helps mentees improve product thinking, portfolio presentation and interface clarity.',
      interests: ['UI/UX', 'Product Thinking', 'Design Systems'],
      mentoringGoals: ['Build stronger portfolios', 'Teach design reasoning'],
      preferences: ['Curious', 'Communicative', 'Iterative mindset'],
      meetings: [
        {
          id: 'm6',
          day: 'Mon',
          date: 'Apr 7',
          time: '18:00',
          title: 'Portfolio Review',
          status: 'confirmed',
        },
        {
          id: 'm7',
          day: 'Thu',
          date: 'Apr 10',
          time: '16:00',
          title: 'Design Feedback',
          status: 'confirmed',
        },
      ],
      stats: {
        first: { label: 'Rating', value: '4.8' },
        second: { label: 'Reviews', value: '18' },
        third: { label: 'Mentees', value: '5' },
      },
    },
  ];

  const currentList = isMentor ? activeMentees : activeMentors;
  const sectionTitle = isMentor ? 'ACTIVE MENTEES' : 'ACTIVE MENTORS';

  const openConnectionProfile = (item: ConnectionCard) => {
    router.push({
      pathname: '/connection-profile',
      params: {
        id: item.id,
        type: item.type,
        name: item.name,
        initials: item.initials,
        avatarBg: item.avatarBg,
        avatarText: item.avatarText,
        subtitle: item.subtitle,
        progress: String(item.progress),
        department: item.department ?? '',
        title: item.title ?? '',
        about: item.about ?? '',
        interests: JSON.stringify(item.interests ?? []),
        goals: JSON.stringify(item.goals ?? []),
        mentoringGoals: JSON.stringify(item.mentoringGoals ?? []),
        preferences: JSON.stringify(item.preferences ?? []),
        meetings: JSON.stringify(item.meetings),
        stat1Label: item.stats.first.label,
        stat1Value: item.stats.first.value,
        stat2Label: item.stats.second.label,
        stat2Value: item.stats.second.value,
        stat3Label: item.stats.third.label,
        stat3Value: item.stats.third.value,
      },
    });
  };

  return (
    <View style={styles.container}>
      <View style={styles.fixedHeader}>
        <View style={styles.topCircle} />
        <View style={styles.rightCircle} />

        <View style={styles.statusRow}>
          <Text style={styles.statusText}>9:41</Text>
          <Text style={styles.statusIcons}>▲ ▮</Text>
        </View>

        <View style={styles.headerTopRow}>
          <View style={styles.profileBadge}>
            <Text style={styles.profileBadgeText}>
              {isMentor ? 'Mentor Mode' : 'Mentee Mode'}
            </Text>
          </View>

          <TouchableOpacity
            style={styles.notificationButton}
            onPress={() => console.log('Navigate to Notifications')}
          >
            <Text style={styles.notificationIcon}>🔔</Text>
            <View style={styles.notificationDot} />
          </TouchableOpacity>
        </View>

        <Text style={styles.title}>
          Your{'\n'}
          <Text style={styles.titleItalic}>Dashboard.</Text>
        </Text>
      </View>

      <ScrollView
        style={styles.scrollArea}
        contentContainerStyle={styles.scrollContent}
        showsVerticalScrollIndicator={false}
      >
        <Text style={styles.sectionTitle}>{sectionTitle}</Text>

        {currentList.map((item) => (
          <View key={item.id} style={styles.activeCard}>
            <TouchableOpacity activeOpacity={0.9} onPress={() => openConnectionProfile(item)}>
              <View style={styles.topRow}>
                <View style={[styles.avatar, { backgroundColor: item.avatarBg }]}>
                  <Text style={[styles.avatarText, { color: item.avatarText }]}>
                    {item.initials}
                  </Text>
                </View>

                <View style={styles.infoArea}>
                  <Text style={styles.name}>{item.name}</Text>
                  <Text style={styles.subtitle}>{item.subtitle}</Text>
                </View>

                <View style={styles.activeBadge}>
                  <Text style={styles.activeBadgeText}>Active</Text>
                </View>
              </View>

              <View style={styles.progressTrack}>
                <View
                  style={[
                    styles.progressFill,
                    { width: `${item.progress}%` },
                  ]}
                />
              </View>

              <Text style={styles.progressText}>Progress: {item.progress}%</Text>

              <TouchableOpacity
                style={styles.viewProfileButton}
                onPress={() => openConnectionProfile(item)}
              >
                <Text style={styles.viewProfileButtonText}>Open Shared Space</Text>
              </TouchableOpacity>
            </TouchableOpacity>
          </View>
        ))}

        {currentList.length === 0 && (
          <View style={styles.emptyStateContainer}>
            <Text style={styles.emptyStateText}>No active connections found.</Text>
          </View>
        )}
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
    marginBottom: 8,
  },
  profileBadge: {
    backgroundColor: 'rgba(255,255,255,0.15)',
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderRadius: 16,
  },
  profileBadgeText: {
    color: '#F7F4EE',
    fontSize: 14,
    fontWeight: '700',
  },
  notificationButton: {
    width: 44,
    height: 44,
    borderRadius: 22,
    backgroundColor: 'rgba(255,255,255,0.15)',
    justifyContent: 'center',
    alignItems: 'center',
    position: 'relative',
  },
  notificationIcon: {
    fontSize: 20,
  },
  notificationDot: {
    position: 'absolute',
    top: 10,
    right: 12,
    width: 8,
    height: 8,
    borderRadius: 4,
    backgroundColor: '#E76F51',
    borderWidth: 1.5,
    borderColor: '#456B50',
  },
  title: {
    color: '#F7F4EE',
    fontSize: 34,
    lineHeight: 38,
    fontWeight: '700',
    marginTop: 10,
  },
  titleItalic: {
    fontStyle: 'italic',
    fontWeight: '700',
  },
  scrollArea: {
    flex: 1,
    backgroundColor: '#ECE8E1',
  },
  scrollContent: {
    paddingHorizontal: 24,
    paddingTop: 24,
    paddingBottom: 34,
  },
  sectionTitle: {
    fontSize: 13,
    fontWeight: '700',
    letterSpacing: 2,
    color: '#8B8176',
    marginBottom: 18,
  },
  activeCard: {
    backgroundColor: '#F8F6F2',
    borderRadius: 26,
    padding: 18,
    marginBottom: 18,
  },
  topRow: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  avatar: {
    width: 68,
    height: 68,
    borderRadius: 34,
    justifyContent: 'center',
    alignItems: 'center',
    marginRight: 16,
  },
  avatarText: {
    fontSize: 20,
    fontWeight: '700',
  },
  infoArea: {
    flex: 1,
  },
  name: {
    color: '#23372B',
    fontSize: 17,
    fontWeight: '700',
    marginBottom: 4,
  },
  subtitle: {
    color: '#9A8F82',
    fontSize: 13,
    fontWeight: '500',
  },
  activeBadge: {
    backgroundColor: '#D7E8DA',
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 14,
  },
  activeBadgeText: {
    color: '#2F563C',
    fontSize: 11,
    fontWeight: '700',
  },
  progressTrack: {
    height: 9,
    borderRadius: 999,
    backgroundColor: '#DDD8CF',
    overflow: 'hidden',
    marginTop: 18,
    marginBottom: 10,
  },
  progressFill: {
    height: '100%',
    borderRadius: 999,
    backgroundColor: '#5D8D66',
  },
  progressText: {
    color: '#8B8176',
    fontSize: 13,
    fontWeight: '500',
    marginBottom: 14,
  },
  viewProfileButton: {
    backgroundColor: '#D7E8DA',
    borderRadius: 16,
    paddingVertical: 12,
    alignItems: 'center',
  },
  viewProfileButtonText: {
    color: '#2F563C',
    fontSize: 14,
    fontWeight: '700',
  },
  emptyStateContainer: {
    paddingVertical: 40,
    alignItems: 'center',
  },
  emptyStateText: {
    color: '#9A8F82',
    fontSize: 15,
    fontWeight: '500',
  },
});