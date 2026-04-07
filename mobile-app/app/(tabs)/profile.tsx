import React, { useState, useEffect } from 'react';
import apiClient from '../../api/client'; // Klasör yapına göre kontrol et (api/client.ts)

import { router } from 'expo-router';
import * as SecureStore from 'expo-secure-store';
import { useRole } from '../../components/RoleContext';
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  StyleSheet,
  ScrollView,
  Alert,
  Image,
} from 'react-native';
import * as ImagePicker from 'expo-image-picker';

// İsme göre baş harfleri hesaplayan yardımcı fonksiyon
const getInitials = (name: string) => {
  if (!name) return 'U';
  const parts = name.trim().split(' ');
  if (parts.length > 1) {
    return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
  }
  return name.substring(0, 2).toUpperCase();
};

export default function ProfileScreen() {
  const { role, clearRole } = useRole();
  const isMentor = role === 'mentor';

  const handleLogout = async () => {
    try {
      await SecureStore.deleteItemAsync('userToken');
      await SecureStore.deleteItemAsync('userId');
      clearRole();
      router.replace('/login');
    } catch {
      Alert.alert('Error', 'An error occurred while logging out.');
    }
  };

  if (isMentor) {
    return <MentorProfileContent onLogout={handleLogout} />;
  }

  return <MenteeProfileContent onLogout={handleLogout} />;
}

// Token (Chip) Editörü Bileşeni
function TokenEditor({
  label,
  placeholder,
  values,
  inputValue,
  setInputValue,
  onAdd,
  onRemove,
}: {
  label: string;
  placeholder: string;
  values: string[];
  inputValue: string;
  setInputValue: React.Dispatch<React.SetStateAction<string>>;
  onAdd: () => void;
  onRemove: (token: string) => void;
}) {
  return (
    <View style={styles.tokenSection}>
      <Text style={styles.inputLabel}>{label}</Text>
      <View style={styles.tokenInputRow}>
        <TextInput
          style={styles.tokenInput}
          placeholder={placeholder}
          placeholderTextColor="#B5ADA3"
          value={inputValue}
          onChangeText={setInputValue}
          onSubmitEditing={onAdd}
          returnKeyType="done"
        />
        <TouchableOpacity style={styles.addTokenButton} onPress={onAdd}>
          <Text style={styles.addTokenButtonText}>Add</Text>
        </TouchableOpacity>
      </View>
      <View style={styles.tokensWrap}>
        {values.map((item) => (
          <View key={item} style={styles.tokenChip}>
            <Text style={styles.tokenChipText}>{item}</Text>
            <TouchableOpacity onPress={() => onRemove(item)}>
              <Text style={styles.tokenRemoveText}>×</Text>
            </TouchableOpacity>
          </View>
        ))}
      </View>
    </View>
  );
}

type SentRequest = {
  id: number;
  mentorFirstName: string;
  message: string;
  status: string;
  createdAt: string;
};

async function pickAvatar(storageKey: string): Promise<string | null> {
  const { status } = await ImagePicker.requestMediaLibraryPermissionsAsync();
  if (status !== 'granted') {
    Alert.alert('Permission needed', 'Please allow access to your photo library.');
    return null;
  }
  const result = await ImagePicker.launchImageLibraryAsync({
    mediaTypes: ['images'],
    allowsEditing: true,
    aspect: [1, 1],
    quality: 0.7,
  });
  if (result.canceled || !result.assets[0]) return null;
  const uri = result.assets[0].uri;
  await SecureStore.setItemAsync(storageKey, uri);
  return uri;
}

// --- MENTEE PROFILI ---
function MenteeProfileContent({ onLogout }: { onLogout: () => void }) {
  const [fullName, setFullName] = useState('');
  const [department, setDepartment] = useState('');
  const [aboutMe, setAboutMe] = useState('');
  const [goals, setGoals] = useState('');
  const [careerInterest, setCareerInterest] = useState('');
  const [meetingFreqPref, setMeetingFreqPref] = useState('');
  const [profileVisibility, setProfileVisibility] = useState(true);
  const [interestInput, setInterestInput] = useState('');
  const [interests, setInterests] = useState<string[]>([]);
  const [skillInput, setSkillInput] = useState('');
  const [skills, setSkills] = useState<string[]>([]);
  const [profilePhoto, setProfilePhoto] = useState<string | null>(null);
  const [sentRequests, setSentRequests] = useState<SentRequest[]>([]);
  const [requestsLoading, setRequestsLoading] = useState(true);

  const handleSave = async () => {
    try {
      // Artık userId'ye ihtiyacımız yok, backend bizi token'dan tanıyacak
      const fullNameParts = fullName.trim().split(' ');
      
      const updateData = {
        firstName: fullNameParts[0],
        lastName: fullNameParts.length > 1 ? fullNameParts.slice(1).join(' ') : '',
        major: department,
        backgroundInfo: aboutMe,
        goals,
        careerInterest,
        meetingFreqPref: meetingFreqPref || undefined,
        profileVisibility,
        interests,
        skills,
      };

      await apiClient.patch('/users/me/mentee', updateData);
      Alert.alert('Başarılı', 'Profilin güncellendi!');
    } catch (error: any) {
      const serverMessage = error.response?.data?.message || error.message;
      console.error('Update hatası:', error.response?.data);
      Alert.alert('Güncelleme Başarısız', serverMessage);
    }
  };

  useEffect(() => {
    const fetchAll = async () => {
      try {
        const userId = await SecureStore.getItemAsync('userId');
        if (!userId) return;
        const profileRes = await apiClient.get(`/users/${userId}`);
        const data = profileRes.data;
        setFullName(`${data.firstName} ${data.lastName}`);
        setDepartment(data.major || '');
        setAboutMe(data.backgroundInfo || '');
        setGoals(data.goals || '');
        setCareerInterest(data.careerInterest || '');
        setMeetingFreqPref(data.meetingFreqPref || '');
        if (data.profileVisibility != null) setProfileVisibility(data.profileVisibility);
        if (data.interests) setInterests(data.interests);
        if (data.skills) setSkills(data.skills);
        if (data.profilePhoto) {
          setProfilePhoto(data.profilePhoto);
        } else {
          const local = await SecureStore.getItemAsync('menteeAvatarUri');
          if (local) setProfilePhoto(local);
        }
      } catch (error) {
        console.error('Error fetching mentee profile:', error);
        const local = await SecureStore.getItemAsync('menteeAvatarUri');
        if (local) setProfilePhoto(local);
      }

      try {
        const reqRes = await apiClient.get('/mentorship-requests/sent');
        const list = reqRes.data.content ?? reqRes.data;
        setSentRequests(list);
      } catch (error) {
        console.error('Error fetching sent requests:', error);
      } finally {
        setRequestsLoading(false);
      }
    };
    fetchAll();
  }, []);

  return (
    <View style={styles.container}>
      <ScrollView contentContainerStyle={styles.scrollContent} showsVerticalScrollIndicator={false}>
        <View style={styles.header}>
          <View style={styles.topCircle} />
          <View style={styles.leftCircle} />
          <TouchableOpacity
            style={styles.avatarCircle}
            onPress={async () => {
              const uri = await pickAvatar('menteeAvatarUri');
              if (uri) setProfilePhoto(uri);
            }}
          >
            {profilePhoto ? (
              <Image source={{ uri: profilePhoto }} style={styles.avatarImage} />
            ) : (
              <Text style={styles.avatarText}>{getInitials(fullName)}</Text>
            )}
            <View style={styles.avatarEditBadge}>
              <Text style={styles.avatarEditText}>✎</Text>
            </View>
          </TouchableOpacity>
          <Text style={styles.name}>{fullName || 'Loading...'}</Text>
          <Text style={styles.roleText}>Mentee • {department}</Text>
        </View>

        <View style={styles.body}>
          {/* Quick Actions */}
          <View style={styles.quickActionsRow}>
            <TouchableOpacity
              style={styles.quickActionButton}
              onPress={() => router.push('/mentorship-requests')}
            >
              <Text style={styles.quickActionIcon}>📋</Text>
              <Text style={styles.quickActionText}>My Requests</Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={styles.quickActionButton}
              onPress={() => router.push('/(tabs)/explore')}
            >
              <Text style={styles.quickActionIcon}>🔍</Text>
              <Text style={styles.quickActionText}>Find Mentor</Text>
            </TouchableOpacity>
          </View>

          <View style={styles.formCardMentee}>
            <Text style={styles.inputLabel}>Full Name</Text>
            <TextInput style={styles.input} value={fullName} onChangeText={setFullName} />
            <Text style={styles.inputLabel}>Major</Text>
            <TextInput style={styles.input} value={department} onChangeText={setDepartment} placeholder="e.g. Computer Engineering" placeholderTextColor="#B5ADA3" />
            <Text style={styles.inputLabel}>Background</Text>
            <TextInput style={[styles.input, styles.aboutInput]} value={aboutMe} onChangeText={setAboutMe} multiline placeholder="Your educational and professional background..." placeholderTextColor="#B5ADA3" />
            <Text style={styles.inputLabel}>Goals</Text>
            <TextInput style={[styles.input, styles.aboutInput]} value={goals} onChangeText={setGoals} multiline placeholder="What do you want to achieve..." placeholderTextColor="#B5ADA3" />
            <Text style={styles.inputLabel}>Career Interest</Text>
            <TextInput style={styles.input} value={careerInterest} onChangeText={setCareerInterest} placeholder="e.g. Data Science" placeholderTextColor="#B5ADA3" />
            <Text style={styles.inputLabel}>Meeting Frequency Preference</Text>
            <TextInput style={styles.input} value={meetingFreqPref} onChangeText={setMeetingFreqPref} placeholder="e.g. Weekly" placeholderTextColor="#B5ADA3" />
            <TokenEditor
              label="Interests"
              placeholder="Add interest"
              values={interests}
              inputValue={interestInput}
              setInputValue={setInterestInput}
              onAdd={() => { if (interestInput.trim()) setInterests([...interests, interestInput.trim()]); setInterestInput(''); }}
              onRemove={(t) => setInterests(interests.filter(i => i !== t))}
            />
            <TokenEditor
              label="Skills"
              placeholder="e.g. JavaScript, Python"
              values={skills}
              inputValue={skillInput}
              setInputValue={setSkillInput}
              onAdd={() => { if (skillInput.trim()) setSkills([...skills, skillInput.trim()]); setSkillInput(''); }}
              onRemove={(t) => setSkills(skills.filter(s => s !== t))}
            />
            <View style={styles.visibilityRow}>
              <Text style={styles.inputLabel}>Profile Visibility</Text>
              <TouchableOpacity
                style={[styles.toggleButton, profileVisibility && styles.toggleButtonOn]}
                onPress={() => setProfileVisibility(!profileVisibility)}
              >
                <Text style={styles.toggleButtonText}>{profileVisibility ? 'Public' : 'Private'}</Text>
              </TouchableOpacity>
            </View>
          </View>
          <TouchableOpacity style={styles.saveButtonMentee} onPress={handleSave}>
            <Text style={styles.saveButtonText}>Save Changes</Text>
          </TouchableOpacity>

          {/* Sent Requests */}
          <Text style={styles.sectionHeaderText}>MY REQUESTS</Text>
          {requestsLoading ? null : sentRequests.length === 0 ? (
            <View style={styles.emptyRequestsCard}>
              <Text style={styles.emptyRequestsText}>No requests sent yet.</Text>
            </View>
          ) : (
            sentRequests.map((req) => {
              const statusColor =
                req.status === 'PENDING' ? '#8A5D12' :
                req.status === 'ACCEPTED' ? '#2F563C' : '#D9534F';
              const statusBg =
                req.status === 'PENDING' ? '#F1E1BB' :
                req.status === 'ACCEPTED' ? '#D7E8DA' : '#FDF0EF';
              return (
                <View key={req.id} style={styles.requestCard}>
                  <View style={styles.requestCardRow}>
                    <Text style={styles.requestCardName}>{req.mentorFirstName}</Text>
                    <View style={[styles.statusBadge, { backgroundColor: statusBg }]}>
                      <Text style={[styles.statusBadgeText, { color: statusColor }]}>
                        {req.status}
                      </Text>
                    </View>
                  </View>
                  {!!req.message && (
                    <Text style={styles.requestCardMessage} numberOfLines={2}>{req.message}</Text>
                  )}
                </View>
              );
            })
          )}

          <TouchableOpacity style={styles.logoutButton} onPress={onLogout}>
            <Text style={styles.logoutButtonText}>Log Out</Text>
          </TouchableOpacity>
        </View>
      </ScrollView>
    </View>
  );
}

// --- MENTOR PROFILI ---
function MentorProfileContent({ onLogout }: { onLogout: () => void }) {
  const [displayName, setDisplayName] = useState('');
  const [title, setTitle] = useState('');
  const [bio, setBio] = useState('');
  const [expertise, setExpertise] = useState('');
  const [affiliation, setAffiliation] = useState('');
  const [mentoringGoals, setMentoringGoals] = useState('');
  const [preferredMenteeMajor, setPreferredMenteeMajor] = useState('');
  const [preferredMenteeSkills, setPreferredMenteeSkills] = useState<string[]>([]);
  const [skillInput, setSkillInput] = useState('');
  const [interests, setInterests] = useState<string[]>([]);
  const [interestInput, setInterestInput] = useState('');
  const [maxMenteeCapacity, setMaxMenteeCapacity] = useState('3');
  const [mentorshipDuration, setMentorshipDuration] = useState('');
  const [profilePhoto, setProfilePhoto] = useState<string | null>(null);

  useEffect(() => {
    const fetchProfileData = async () => {
      try {
        const userId = await SecureStore.getItemAsync('userId');
        if (!userId) return;
        const response = await apiClient.get(`/users/${userId}`);
        const data = response.data;
        setDisplayName(`${data.firstName} ${data.lastName}`);
        setTitle(data.field || '');
        setBio(data.bio || '');
        setExpertise(data.expertise || '');
        setAffiliation(data.affiliation || '');
        setMentoringGoals(data.mentoringGoals || '');
        setPreferredMenteeMajor(data.preferredMenteeMajor || '');
        if (data.preferredMenteeSkills) setPreferredMenteeSkills(data.preferredMenteeSkills);
        if (data.interests) setInterests(data.interests);
        if (data.maxMenteeCapacity != null) setMaxMenteeCapacity(String(data.maxMenteeCapacity));
        if (data.mentorshipDuration != null) setMentorshipDuration(String(data.mentorshipDuration));
        if (data.profilePhoto) {
          setProfilePhoto(data.profilePhoto);
        } else {
          const local = await SecureStore.getItemAsync('mentorAvatarUri');
          if (local) setProfilePhoto(local);
        }
      } catch (error) {
        console.error('Error fetching mentor profile:', error);
        const local = await SecureStore.getItemAsync('mentorAvatarUri');
        if (local) setProfilePhoto(local);
      }
    };
    fetchProfileData();
  }, []);

  const handleSave = async () => {
    const capacity = parseInt(maxMenteeCapacity, 10);
    if (isNaN(capacity) || capacity < 1) {
      Alert.alert('Hata', 'Mentee kapasitesi en az 1 olmalıdır.');
      return;
    }
    try {
      const nameParts = displayName.trim().split(' ');
      const duration = parseInt(mentorshipDuration, 10);
      await apiClient.patch('/users/me/mentor', {
        firstName: nameParts[0],
        lastName: nameParts.length > 1 ? nameParts.slice(1).join(' ') : '',
        field: title,
        bio,
        expertise,
        affiliation,
        mentoringGoals,
        preferredMenteeMajor,
        preferredMenteeSkills,
        interests,
        maxMenteeCapacity: capacity,
        ...(mentorshipDuration && !isNaN(duration) ? { mentorshipDuration: duration } : {}),
      });
      Alert.alert('Başarılı', 'Profilin güncellendi!');
    } catch (error: any) {
      const serverMessage = error.response?.data?.message || error.message;
      Alert.alert('Güncelleme Başarısız', serverMessage);
    }
  };

  return (
    <View style={styles.container}>
      <ScrollView contentContainerStyle={styles.scrollContent} showsVerticalScrollIndicator={false}>
        <View style={styles.header}>
          <View style={styles.topCircle} />
          <TouchableOpacity
            style={styles.avatarCircle}
            onPress={async () => {
              const uri = await pickAvatar('mentorAvatarUri');
              if (uri) setProfilePhoto(uri);
            }}
          >
            {profilePhoto ? (
              <Image source={{ uri: profilePhoto }} style={styles.avatarImage} />
            ) : (
              <Text style={styles.avatarText}>{getInitials(displayName)}</Text>
            )}
            <View style={styles.avatarEditBadge}>
              <Text style={styles.avatarEditText}>✎</Text>
            </View>
          </TouchableOpacity>
          <Text style={styles.name}>{displayName || 'Loading...'}</Text>
          <Text style={styles.roleText}>{title}</Text>
        </View>

        <View style={styles.body}>
          {/* Quick Actions */}
          <View style={styles.quickActionsRow}>
            <TouchableOpacity
              style={styles.quickActionButton}
              onPress={() => router.push('/(tabs)/explore' as any)}
            >
              <Text style={styles.quickActionIcon}>📋</Text>
              <Text style={styles.quickActionText}>Requests</Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={styles.quickActionButton}
              onPress={() => router.push('/availability-scheduling')}
            >
              <Text style={styles.quickActionIcon}>📅</Text>
              <Text style={styles.quickActionText}>Availability</Text>
            </TouchableOpacity>
          </View>

          <View style={styles.formCardMentor}>
            <Text style={styles.inputLabel}>Display Name</Text>
            <TextInput style={styles.input} value={displayName} onChangeText={setDisplayName} />
            <Text style={styles.inputLabel}>Field</Text>
            <TextInput style={styles.input} value={title} onChangeText={setTitle} placeholder="e.g. Computer Science" placeholderTextColor="#B5ADA3" />
            <Text style={styles.inputLabel}>Expertise</Text>
            <TextInput style={styles.input} value={expertise} onChangeText={setExpertise} placeholder="e.g. Backend Development" placeholderTextColor="#B5ADA3" />
            <Text style={styles.inputLabel}>Affiliation</Text>
            <TextInput style={styles.input} value={affiliation} onChangeText={setAffiliation} placeholder="e.g. Boğaziçi University" placeholderTextColor="#B5ADA3" />
            <Text style={styles.inputLabel}>Bio</Text>
            <TextInput style={[styles.input, styles.bigInput]} value={bio} onChangeText={setBio} multiline placeholder="Short bio about yourself..." placeholderTextColor="#B5ADA3" />
            <Text style={styles.inputLabel}>Mentoring Goals</Text>
            <TextInput style={[styles.input, styles.bigInput]} value={mentoringGoals} onChangeText={setMentoringGoals} multiline placeholder="What do you want to help mentees achieve..." placeholderTextColor="#B5ADA3" />
            <Text style={styles.inputLabel}>Preferred Mentee Major</Text>
            <TextInput style={styles.input} value={preferredMenteeMajor} onChangeText={setPreferredMenteeMajor} placeholder="e.g. Computer Engineering" placeholderTextColor="#B5ADA3" />
            <TokenEditor
              label="Preferred Mentee Skills"
              placeholder="e.g. Java, Python"
              values={preferredMenteeSkills}
              inputValue={skillInput}
              setInputValue={setSkillInput}
              onAdd={() => { if (skillInput.trim()) setPreferredMenteeSkills([...preferredMenteeSkills, skillInput.trim()]); setSkillInput(''); }}
              onRemove={(t) => setPreferredMenteeSkills(preferredMenteeSkills.filter(s => s !== t))}
            />
            <TokenEditor
              label="Interests"
              placeholder="Add interest"
              values={interests}
              inputValue={interestInput}
              setInputValue={setInterestInput}
              onAdd={() => { if (interestInput.trim()) setInterests([...interests, interestInput.trim()]); setInterestInput(''); }}
              onRemove={(t) => setInterests(interests.filter(i => i !== t))}
            />
            <Text style={styles.inputLabel}>Max Mentee Capacity</Text>
            <TextInput
              style={styles.input}
              value={maxMenteeCapacity}
              onChangeText={setMaxMenteeCapacity}
              keyboardType="number-pad"
              placeholder="e.g. 3"
              placeholderTextColor="#B5ADA3"
            />
            <Text style={styles.inputLabel}>Mentorship Duration (months)</Text>
            <TextInput
              style={styles.input}
              value={mentorshipDuration}
              onChangeText={setMentorshipDuration}
              keyboardType="number-pad"
              placeholder="e.g. 3"
              placeholderTextColor="#B5ADA3"
            />
          </View>
          <TouchableOpacity style={styles.saveButtonMentor} onPress={handleSave}>
            <Text style={styles.saveButtonText}>Save Changes</Text>
          </TouchableOpacity>
          <TouchableOpacity style={styles.logoutButton} onPress={onLogout}>
            <Text style={styles.logoutButtonText}>Log Out</Text>
          </TouchableOpacity>
        </View>
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#ECE8E1' },
  scrollContent: { paddingBottom: 32 },
  header: { backgroundColor: '#456B50', paddingTop: 54, paddingBottom: 60, alignItems: 'center', overflow: 'hidden' },
  topCircle: { position: 'absolute', width: 300, height: 300, borderRadius: 150, backgroundColor: 'rgba(255,255,255,0.05)', top: -30, right: -70 },
  leftCircle: { position: 'absolute', width: 200, height: 200, borderRadius: 100, backgroundColor: 'rgba(255,255,255,0.04)', bottom: 20, left: -50 },
  avatarCircle: { width: 110, height: 110, borderRadius: 55, borderWidth: 3, borderColor: 'rgba(255,255,255,0.38)', justifyContent: 'center', alignItems: 'center', marginTop: 20, marginBottom: 15, overflow: 'hidden', position: 'relative' },
  avatarText: { color: '#F5F1E9', fontSize: 32, fontWeight: '700' },
  avatarImage: { width: 110, height: 110, borderRadius: 55 },
  avatarEditBadge: { position: 'absolute', bottom: 0, right: 0, backgroundColor: '#456B50', borderRadius: 12, width: 28, height: 28, justifyContent: 'center', alignItems: 'center', borderWidth: 2, borderColor: '#F5F1E9' },
  avatarEditText: { color: '#F5F1E9', fontSize: 14 },
  name: { color: '#F5F1E9', fontSize: 24, fontWeight: '700', marginBottom: 5 },
  roleText: { color: 'rgba(245,241,233,0.75)', fontSize: 14, fontWeight: '500' },
  body: { paddingHorizontal: 24, paddingTop: 20 },
  formCardMentee: { backgroundColor: '#F8F6F2', borderRadius: 26, padding: 20, marginBottom: 20 },
  formCardMentor: { backgroundColor: '#F8F6F2', borderRadius: 26, padding: 20, marginBottom: 20 },
  inputLabel: { color: '#7E7368', fontSize: 12, fontWeight: '700', marginBottom: 8 },
  input: { height: 60, borderRadius: 18, borderWidth: 1.5, borderColor: '#D8CEC0', backgroundColor: '#FCFBF8', paddingHorizontal: 15, fontSize: 16, color: '#4A4138', marginBottom: 15 },
  aboutInput: { height: 100, textAlignVertical: 'top', paddingTop: 10 },
  bigInput: { height: 100, textAlignVertical: 'top', paddingTop: 10 },
  tokenSection: { marginBottom: 15 },
  tokenInputRow: { flexDirection: 'row', marginBottom: 10 },
  tokenInput: { flex: 1, height: 50, borderRadius: 15, borderWidth: 1.5, borderColor: '#D8CEC0', backgroundColor: '#FCFBF8', paddingHorizontal: 12, marginRight: 10 },
  addTokenButton: { backgroundColor: '#4B7B57', borderRadius: 15, paddingHorizontal: 15, justifyContent: 'center' },
  addTokenButtonText: { color: '#F8F6F2', fontWeight: '700' },
  tokensWrap: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  tokenChip: { flexDirection: 'row', alignItems: 'center', paddingHorizontal: 12, paddingVertical: 8, borderRadius: 15, backgroundColor: '#EEF3EE', borderWidth: 1, borderColor: '#D7E8DA' },
  tokenChipText: { color: '#2F563C', fontSize: 13, fontWeight: '600' },
  tokenRemoveText: { color: '#2F563C', fontSize: 18, fontWeight: '700', marginLeft: 8 },
  logoutButton: { backgroundColor: '#FDF0EF', borderWidth: 1, borderColor: '#FAD4D4', borderRadius: 20, paddingVertical: 15, alignItems: 'center', marginTop: 12 },
  logoutButtonText: { color: '#D9534F', fontSize: 16, fontWeight: '700' },
  quickActionsRow: { flexDirection: 'row', gap: 12, marginBottom: 16 },
  quickActionButton: {
    flex: 1,
    backgroundColor: '#F8F6F2',
    borderRadius: 20,
    paddingVertical: 18,
    alignItems: 'center',
    borderWidth: 1.5,
    borderColor: '#D7E8DA',
  },
  quickActionIcon: { fontSize: 24, marginBottom: 6 },
  quickActionText: { color: '#2F563C', fontSize: 13, fontWeight: '700' },
  sectionHeaderText: { fontSize: 12, fontWeight: '700', letterSpacing: 2, color: '#8B8176', marginTop: 8, marginBottom: 12 },
  emptyRequestsCard: { backgroundColor: '#F8F6F2', borderRadius: 18, padding: 18, alignItems: 'center', marginBottom: 16 },
  emptyRequestsText: { color: '#9A8F82', fontSize: 14, fontWeight: '500' },
  requestCard: { backgroundColor: '#F8F6F2', borderRadius: 18, padding: 16, marginBottom: 10 },
  requestCardRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  requestCardName: { color: '#23372B', fontSize: 15, fontWeight: '700' },
  statusBadge: { paddingHorizontal: 12, paddingVertical: 6, borderRadius: 12 },
  statusBadgeText: { fontSize: 12, fontWeight: '700' },
  requestCardMessage: { color: '#7E7368', fontSize: 13, marginTop: 8, lineHeight: 18 },

  saveButtonMentee: {
    backgroundColor: '#4B7B57',
    borderRadius: 24,
    paddingVertical: 18,
    alignItems: 'center',
    marginTop: 10,
    marginBottom: 12,
    // Hafif gölge efekti
    shadowColor: '#000',
    shadowOpacity: 0.1,
    shadowRadius: 5,
    shadowOffset: { width: 0, height: 2 },
    elevation: 3,
  },
  saveButtonMentor: {
    backgroundColor: '#467853',
    borderRadius: 24,
    paddingVertical: 18,
    alignItems: 'center',
    marginTop: 20,
    // Hafif gölge efekti
    shadowColor: '#000',
    shadowOpacity: 0.1,
    shadowRadius: 5,
    shadowOffset: { width: 0, height: 2 },
    elevation: 3,
  },
  saveButtonText: {
    color: '#F8F6F2',
    fontSize: 17,
    fontWeight: '700',
  },
  visibilityRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: 15,
  },
  toggleButton: {
    backgroundColor: '#E2DACE',
    borderRadius: 16,
    paddingHorizontal: 18,
    paddingVertical: 8,
  },
  toggleButtonOn: {
    backgroundColor: '#D7E8DA',
  },
  toggleButtonText: {
    color: '#2F563C',
    fontSize: 13,
    fontWeight: '700',
  },
});