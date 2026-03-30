import React, { useState } from 'react';
import { router } from 'expo-router';
import { useRole } from '../../components/RoleContext';
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  StyleSheet,
  ScrollView,
} from 'react-native';

export default function ProfileScreen() {
  const { role } = useRole();
  const isMentor = role === 'mentor';

  if (isMentor) {
    return <MentorProfileContent />;
  }

  return <MenteeProfileContent />;
}

function MenteeProfileContent() {
  const [fullName, setFullName] = useState('Övgü Su Afşar');
  const [department, setDepartment] = useState('Computer Engineering');
  const [aboutMe, setAboutMe] = useState('');

  return (
    <View style={styles.container}>
      <ScrollView
        contentContainerStyle={styles.scrollContent}
        showsVerticalScrollIndicator={false}
      >
        <View style={styles.header}>
          <View style={styles.topCircle} />
          <View style={styles.leftCircle} />

          <View style={styles.statusRow}>
            <Text style={styles.statusText}>9:41</Text>
            <Text style={styles.statusIcons}>▲ ▮</Text>
          </View>

          <View style={styles.avatarCircle}>
            <Text style={styles.avatarText}>ÖA</Text>
          </View>

          <Text style={styles.name}>Övgü Su Afşar</Text>
          <Text style={styles.roleText}>Mentee • Computer Engineering</Text>

          <View style={styles.badgeMentee}>
            <Text style={styles.badgeMenteeText}>Active Mentorship: 1</Text>
          </View>
        </View>

        <View style={styles.statsCardMentee}>
          <View style={styles.statItem}>
            <Text style={styles.statNumber}>12</Text>
            <Text style={styles.statLabel}>Tasks</Text>
          </View>

          <View style={styles.statDividerTall} />

          <View style={styles.statItem}>
            <Text style={styles.statNumber}>3</Text>
            <Text style={styles.statLabel}>Meetings</Text>
          </View>

          <View style={styles.statDividerTall} />

          <View style={styles.statItem}>
            <Text style={styles.statNumber}>4.8</Text>
            <Text style={styles.statLabel}>Rating</Text>
          </View>
        </View>

        <View style={styles.body}>
          <Text style={styles.sectionTitleMentee}>PERSONAL INFO</Text>

          <View style={styles.formCardMentee}>
            <Text style={styles.inputLabel}>Full Name</Text>
            <TextInput
              style={styles.input}
              placeholder="Enter your full name"
              placeholderTextColor="#B5ADA3"
              value={fullName}
              onChangeText={setFullName}
            />

            <Text style={styles.inputLabel}>Department</Text>
            <TextInput
              style={styles.input}
              placeholder="Enter your department"
              placeholderTextColor="#B5ADA3"
              value={department}
              onChangeText={setDepartment}
            />

            <Text style={styles.inputLabel}>About Me</Text>
            <TextInput
              style={[styles.input, styles.aboutInput]}
              placeholder="Tell us about yourself"
              placeholderTextColor="#B5ADA3"
              value={aboutMe}
              onChangeText={setAboutMe}
              multiline
              textAlignVertical="top"
            />
          </View>

          <TouchableOpacity
          style={styles.myTasksButton}
          onPress={() => router.push('/task-tracker')}
          >
            <Text style={styles.myTasksButtonText}>My Tasks</Text>
            </TouchableOpacity>

          <TouchableOpacity style={styles.saveButtonMentee}>
            <Text style={styles.saveButtonText}>Save Changes</Text>
          </TouchableOpacity>
        </View>
      </ScrollView>
    </View>
  );
}

function MentorProfileContent() {
  const [displayName, setDisplayName] = useState('Burak Afşar');
  const [title, setTitle] = useState('Senior iOS Developer · Apple');
  const [bio, setBio] = useState(
    '7+ years iOS dev. Passionate about mobile and mentorship.'
  );

  return (
    <View style={styles.container}>
      <View style={styles.fixedHeader}>
        <View style={styles.topCircle} />
        <View style={styles.leftCircleMentor} />

        <View style={styles.statusRow}>
          <Text style={styles.statusText}>9:41</Text>
          <Text style={styles.statusIcons}>▲ ▮</Text>
        </View>

        <View style={styles.avatarWrapper}>
          <View style={styles.avatarCircleMentor}>
            <Text style={styles.avatarText}>BA</Text>
          </View>
          <View style={styles.onlineDot} />
        </View>

        <Text style={styles.name}>Burak Afşar</Text>
        <Text style={styles.roleText}>Senior iOS Developer · Apple</Text>

        <View style={styles.tagsRow}>
          <View style={styles.tag}>
            <Text style={styles.tagText}>Swift</Text>
          </View>
          <View style={styles.tag}>
            <Text style={styles.tagText}>Mobile</Text>
          </View>
          <View style={styles.tag}>
            <Text style={styles.tagText}>React Native</Text>
          </View>
        </View>
      </View>

      <ScrollView
        style={styles.scrollArea}
        contentContainerStyle={styles.scrollContentMentor}
        showsVerticalScrollIndicator={false}
      >
        <View style={styles.statsCardMentor}>
          <View style={styles.statItem}>
            <Text style={styles.statNumber}>4.9</Text>
            <Text style={styles.statLabel}>Rating</Text>
          </View>

          <View style={styles.statDivider} />

          <View style={styles.statItem}>
            <Text style={styles.statNumber}>24</Text>
            <Text style={styles.statLabel}>Reviews</Text>
          </View>

          <View style={styles.statDivider} />

          <View style={styles.statItem}>
            <Text style={styles.statNumber}>8</Text>
            <Text style={styles.statLabel}>Mentees</Text>
          </View>

          <View style={styles.statDivider} />

          <View style={styles.statItem}>
            <Text style={styles.statNumber}>2y</Text>
            <Text style={styles.statLabel}>Experience</Text>
          </View>
        </View>

        <Text style={styles.sectionTitle}>MANAGE</Text>

        <TouchableOpacity
          style={styles.actionCard}
          onPress={() => router.push('/mentorship-requests')}
        >
          <View style={[styles.cardIconBox, { backgroundColor: '#F3E7E2' }]}>
            <Text style={styles.cardIcon}>📥</Text>
          </View>

          <View style={styles.cardTextArea}>
            <Text style={styles.cardTitle}>Mentorship Requests</Text>
            <Text style={styles.cardSubtitle}>Review incoming applications</Text>
          </View>

          <View style={styles.badgeRed}>
            <Text style={styles.badgeRedText}>2</Text>
          </View>

          <Text style={styles.chevron}>›</Text>
        </TouchableOpacity>

        <TouchableOpacity
          style={styles.actionCard}
          onPress={() => router.push('/availability-scheduling')}
        >
          <View style={[styles.cardIconBox, { backgroundColor: '#E3ECE6' }]}>
            <Text style={styles.cardIcon}>📅</Text>
          </View>

          <View style={styles.cardTextArea}>
            <Text style={styles.cardTitle}>Availability & Scheduling</Text>
            <Text style={styles.cardSubtitle}>Set your weekly hours</Text>
          </View>

          <Text style={styles.chevron}>›</Text>
        </TouchableOpacity>

        <TouchableOpacity
          style={styles.actionCard}
          onPress={() => router.push('/meetings-sessions')}
        >
          <View style={[styles.cardIconBox, { backgroundColor: '#E7E9EF' }]}>
            <Text style={styles.cardIcon}>🗒️</Text>
          </View>

          <View style={styles.cardTextArea}>
            <Text style={styles.cardTitle}>Meetings & Sessions</Text>
            <Text style={styles.cardSubtitle}>3 upcoming this week</Text>
          </View>

          <Text style={styles.chevron}>›</Text>
        </TouchableOpacity>

        <Text style={styles.sectionTitle}>PROFILE INFO</Text>

        <View style={styles.formCardMentor}>
          <Text style={styles.inputLabel}>Display Name</Text>
          <TextInput
            style={styles.input}
            value={displayName}
            onChangeText={setDisplayName}
          />

          <Text style={styles.inputLabel}>Title</Text>
          <TextInput
            style={styles.input}
            value={title}
            onChangeText={setTitle}
          />

          <Text style={styles.inputLabel}>Bio</Text>
          <TextInput
            style={[styles.input, styles.bigInput]}
            value={bio}
            onChangeText={setBio}
            multiline
            textAlignVertical="top"
          />
        </View>

        <TouchableOpacity style={styles.saveButtonMentor}>
          <Text style={styles.saveButtonText}>Save Profile</Text>
        </TouchableOpacity>
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
   myTasksButton: {
    backgroundColor: '#D7E8DA',
    borderRadius: 24,
    paddingVertical: 20,
    alignItems: 'center',
    marginBottom: 14,
    },
  myTasksButtonText: {
    color: '#2F563C',
    fontSize: 17,
    fontWeight: '700',
    },
  container: {
    flex: 1,
    backgroundColor: '#ECE8E1',
  },

  scrollContent: {
    paddingBottom: 32,
  },

  scrollArea: {
    flex: 1,
    backgroundColor: '#ECE8E1',
  },

  scrollContentMentor: {
    paddingHorizontal: 24,
    paddingTop: 14,
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

  fixedHeader: {
    backgroundColor: '#456B50',
    paddingTop: 54,
    paddingHorizontal: 24,
    paddingBottom: 24,
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
    width: 200,
    height: 200,
    borderRadius: 100,
    backgroundColor: 'rgba(255,255,255,0.04)',
    bottom: 20,
    left: -50,
  },

  leftCircleMentor: {
    position: 'absolute',
    width: 220,
    height: 220,
    borderRadius: 110,
    backgroundColor: 'rgba(255,255,255,0.04)',
    bottom: 30,
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

  avatarWrapper: {
    marginTop: 22,
    marginBottom: 16,
  },

  avatarCircle: {
    width: 118,
    height: 118,
    borderRadius: 59,
    borderWidth: 3,
    borderColor: 'rgba(255,255,255,0.38)',
    justifyContent: 'center',
    alignItems: 'center',
    marginTop: 26,
    marginBottom: 18,
  },

  avatarCircleMentor: {
    width: 118,
    height: 118,
    borderRadius: 59,
    borderWidth: 3,
    borderColor: 'rgba(255,255,255,0.35)',
    justifyContent: 'center',
    alignItems: 'center',
  },

  avatarText: {
    color: '#F5F1E9',
    fontSize: 34,
    fontWeight: '700',
  },

  onlineDot: {
    width: 24,
    height: 24,
    borderRadius: 12,
    backgroundColor: '#63D17A',
    position: 'absolute',
    right: 4,
    bottom: 8,
    borderWidth: 3,
    borderColor: '#456B50',
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

  badgeMentee: {
    backgroundColor: 'rgba(255,255,255,0.12)',
    paddingHorizontal: 18,
    paddingVertical: 10,
    borderRadius: 20,
  },

  badgeMenteeText: {
    color: '#F5F1E9',
    fontSize: 13,
    fontWeight: '600',
  },

  tagsRow: {
    flexDirection: 'row',
    gap: 10,
  },

  tag: {
    borderWidth: 1.5,
    borderColor: 'rgba(255,255,255,0.18)',
    backgroundColor: 'rgba(255,255,255,0.05)',
    paddingHorizontal: 16,
    paddingVertical: 9,
    borderRadius: 16,
  },

  tagText: {
    color: '#F5F1E9',
    fontSize: 13,
    fontWeight: '600',
  },

  statsCardMentee: {
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

  statsCardMentor: {
    backgroundColor: '#F8F6F2',
    borderRadius: 26,
    flexDirection: 'row',
    overflow: 'hidden',
    marginBottom: 24,
    marginTop: 2,
  },

  statItem: {
    flex: 1,
    alignItems: 'center',
    paddingVertical: 22,
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

  statDividerTall: {
    width: 1,
    height: 54,
    backgroundColor: '#DDD5CA',
  },

  statDivider: {
    width: 1,
    backgroundColor: '#DDD5CA',
  },

  body: {
    paddingHorizontal: 24,
    paddingTop: 26,
  },

  sectionTitleMentee: {
    color: '#8B8176',
    fontSize: 13,
    fontWeight: '700',
    letterSpacing: 2,
    marginBottom: 18,
  },

  sectionTitle: {
    color: '#8B8176',
    fontSize: 13,
    fontWeight: '700',
    letterSpacing: 2,
    marginBottom: 16,
  },

  formCardMentee: {
    backgroundColor: '#F8F6F2',
    borderRadius: 26,
    padding: 22,
    marginBottom: 22,
    shadowColor: '#000',
    shadowOpacity: 0.04,
    shadowRadius: 10,
    shadowOffset: { width: 0, height: 3 },
    elevation: 2,
  },

  formCardMentor: {
    backgroundColor: '#F8F6F2',
    borderRadius: 26,
    padding: 22,
    marginBottom: 22,
  },

  inputLabel: {
    color: '#7E7368',
    fontSize: 12,
    fontWeight: '700',
    marginBottom: 10,
  },

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

  aboutInput: {
    height: 110,
    paddingTop: 18,
    marginBottom: 0,
  },

  bigInput: {
    height: 110,
    paddingTop: 18,
    marginBottom: 0,
  },

  saveButtonMentee: {
    backgroundColor: '#4B7B57',
    borderRadius: 24,
    paddingVertical: 20,
    alignItems: 'center',
    marginBottom: 12,
  },

  saveButtonMentor: {
    backgroundColor: '#467853',
    borderRadius: 24,
    paddingVertical: 20,
    alignItems: 'center',
  },

  saveButtonText: {
    color: '#F8F6F2',
    fontSize: 17,
    fontWeight: '700',
  },

  actionCard: {
    backgroundColor: '#F8F6F2',
    borderRadius: 24,
    padding: 18,
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 16,
  },

  cardIconBox: {
    width: 68,
    height: 68,
    borderRadius: 20,
    justifyContent: 'center',
    alignItems: 'center',
    marginRight: 16,
  },

  cardIcon: {
    fontSize: 30,
  },

  cardTextArea: {
    flex: 1,
  },

  cardTitle: {
    color: '#23372B',
    fontSize: 17,
    fontWeight: '700',
    marginBottom: 4,
  },

  cardSubtitle: {
    color: '#9A8F82',
    fontSize: 13,
    lineHeight: 20,
  },

  badgeRed: {
    minWidth: 34,
    height: 34,
    borderRadius: 17,
    backgroundColor: '#EA4E4E',
    justifyContent: 'center',
    alignItems: 'center',
    marginRight: 10,
    paddingHorizontal: 8,
  },

  badgeRedText: {
    color: '#FFFFFF',
    fontSize: 14,
    fontWeight: '700',
  },

  chevron: {
    fontSize: 28,
    color: '#B9B0A5',
    fontWeight: '400',
  },
});