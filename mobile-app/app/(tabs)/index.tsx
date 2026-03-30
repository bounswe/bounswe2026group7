import { View, Text, StyleSheet } from 'react-native';
import { useRole } from '../../components/RoleContext';

export default function HomeScreen() {
  const { role } = useRole();
  const isMentor = role === 'mentor';

  if (isMentor) {
    return <MentorHome />;
  }

  return <MenteeHome />;
}

function MenteeHome() {
  return (
    <View style={styles.container}>
      <View style={styles.topCircle} />
      <View style={styles.bottomCircle} />

      <View style={styles.header}>
        <Text style={styles.welcome}>Welcome back,</Text>
        <Text style={styles.name}>Övgü</Text>
        <Text style={styles.subtitle}>
          Continue your mentorship journey from here.
        </Text>
      </View>

      <View style={styles.card}>
        <Text style={styles.cardTitle}>Your Progress</Text>
        <View style={styles.statsRow}>
          <View style={styles.statBox}>
            <Text style={styles.statNumber}>3</Text>
            <Text style={styles.statLabel}>Meetings</Text>
          </View>

          <View style={styles.statBox}>
            <Text style={styles.statNumber}>12</Text>
            <Text style={styles.statLabel}>Tasks</Text>
          </View>

          <View style={styles.statBox}>
            <Text style={styles.statNumber}>1</Text>
            <Text style={styles.statLabel}>Mentor</Text>
          </View>
        </View>
      </View>

      <View style={styles.card}>
        <Text style={styles.cardTitle}>Next Step</Text>
        <Text style={styles.cardText}>
          Check your messages, review mentor suggestions, or update your profile.
        </Text>
      </View>
    </View>
  );
}

function MentorHome() {
  return (
    <View style={styles.container}>
      <View style={styles.topCircle} />
      <View style={styles.bottomCircle} />

      <View style={styles.header}>
        <Text style={styles.welcome}>Welcome back,</Text>
        <Text style={styles.name}>Burak</Text>
        <Text style={styles.subtitle}>
          Manage your mentees and upcoming sessions from here.
        </Text>
      </View>

      <View style={styles.card}>
        <Text style={styles.cardTitle}>Mentor Overview</Text>
        <View style={styles.statsRow}>
          <View style={styles.statBox}>
            <Text style={styles.statNumber}>8</Text>
            <Text style={styles.statLabel}>Mentees</Text>
          </View>

          <View style={styles.statBox}>
            <Text style={styles.statNumber}>5</Text>
            <Text style={styles.statLabel}>Sessions</Text>
          </View>

          <View style={styles.statBox}>
            <Text style={styles.statNumber}>2</Text>
            <Text style={styles.statLabel}>Requests</Text>
          </View>
        </View>
      </View>

      <View style={styles.card}>
        <Text style={styles.cardTitle}>Next Step</Text>
        <Text style={styles.cardText}>
          Review mentorship requests, update availability, or prepare for your next meeting.
        </Text>
      </View>
    </View>
  );
}


const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#ECE8E1',
    padding: 24,
    overflow: 'hidden',
  },
  topCircle: {
    position: 'absolute',
    width: 280,
    height: 280,
    borderRadius: 140,
    backgroundColor: 'rgba(69,107,80,0.08)',
    top: -40,
    right: -60,
  },
  bottomCircle: {
    position: 'absolute',
    width: 220,
    height: 220,
    borderRadius: 110,
    backgroundColor: 'rgba(69,107,80,0.06)',
    bottom: 40,
    left: -50,
  },
  header: {
    marginTop: 30,
    marginBottom: 28,
  },
  welcome: {
    fontSize: 20,
    color: '#6E665E',
    fontWeight: '500',
  },
  name: {
    fontSize: 36,
    color: '#2F563C',
    fontWeight: '700',
    marginTop: 4,
    marginBottom: 8,
  },
  subtitle: {
    fontSize: 15,
    color: '#8A8177',
    lineHeight: 22,
  },
  card: {
    backgroundColor: '#F8F6F2',
    borderRadius: 24,
    padding: 20,
    marginBottom: 18,
    shadowColor: '#000',
    shadowOpacity: 0.04,
    shadowRadius: 8,
    shadowOffset: { width: 0, height: 3 },
    elevation: 2,
  },
  cardTitle: {
    fontSize: 18,
    fontWeight: '700',
    color: '#2F563C',
    marginBottom: 16,
  },
  statsRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    gap: 12,
  },
  statBox: {
    flex: 1,
    backgroundColor: '#EEF2EC',
    borderRadius: 18,
    paddingVertical: 18,
    alignItems: 'center',
  },
  statNumber: {
    fontSize: 24,
    fontWeight: '700',
    color: '#2F563C',
    marginBottom: 6,
  },
  statLabel: {
    fontSize: 13,
    color: '#7E7368',
    fontWeight: '500',
  },
  cardText: {
    fontSize: 15,
    lineHeight: 24,
    color: '#7E7368',
  },
});