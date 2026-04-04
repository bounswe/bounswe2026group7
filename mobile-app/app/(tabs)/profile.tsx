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
} from 'react-native';

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
  const { role } = useRole();
  const isMentor = role === 'mentor';

  const handleLogout = async () => {
    try {
      await SecureStore.deleteItemAsync('userToken');
      await SecureStore.deleteItemAsync('userId');
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

// --- MENTEE PROFILI ---
function MenteeProfileContent({ onLogout }: { onLogout: () => void }) {
  const [fullName, setFullName] = useState('');
  const [department, setDepartment] = useState('');
  const [aboutMe, setAboutMe] = useState('');

  const [interestInput, setInterestInput] = useState('');
  const [goalInput, setGoalInput] = useState('');

  // MenteeProfileContent içinde:
  const [interests, setInterests] = useState<string[]>([]); // [] yerine <string[]>([])
  const [goals, setGoals] = useState<string[]>([]);

  const handleSave = async () => {
    try {
      // Artık userId'ye ihtiyacımız yok, backend bizi token'dan tanıyacak
      const fullNameParts = fullName.trim().split(' ');
      
      const updateData = {
        firstName: fullNameParts[0],
        lastName: fullNameParts.length > 1 ? fullNameParts.slice(1).join(' ') : '',
        major: department,        // Beratcan'ın beklediği field isimlerini kontrol et
        backgroundInfo: aboutMe,  // Mentee için backgroundInfo, Mentor için bio olabilir
        interests: interests      // Liste olarak gönderiyoruz
      };

      console.log("İstek atılıyor: PATCH /api/users/me");
      
      // PUT yerine PATCH kullanıyoruz ve URL'yi /me yapıyoruz
      const response = await apiClient.patch('/users/me', updateData); 
      
      Alert.alert('Başarılı', 'Profilin güncellendi!');
    } catch (error: any) {
      const serverMessage = error.response?.data?.message || error.message;
      console.error('Update hatası:', error.response?.data);
      Alert.alert('Güncelleme Başarısız', serverMessage);
    }
  };

  useEffect(() => {
    const fetchProfileData = async () => {
      try {
        const userId = await SecureStore.getItemAsync('userId');
        if (!userId) return;

        const response = await apiClient.get(`/users/${userId}`); //
        const data = response.data; //

        setFullName(`${data.firstName} ${data.lastName}`);
        setDepartment(data.major || '');
        setAboutMe(data.backgroundInfo || '');
        if (data.interests) setInterests(data.interests);
        // Backend'den gelen goals string ise JSON parse edilebilir veya direkt atanabilir
      } catch (error) {
        console.error('Error fetching mentee profile:', error);
      }
    };
    fetchProfileData();
  }, []);

  return (
    <View style={styles.container}>
      <ScrollView contentContainerStyle={styles.scrollContent} showsVerticalScrollIndicator={false}>
        <View style={styles.header}>
          <View style={styles.topCircle} />
          <View style={styles.leftCircle} />
          <View style={styles.avatarCircle}>
            <Text style={styles.avatarText}>{getInitials(fullName)}</Text>
          </View>
          <Text style={styles.name}>{fullName || 'Loading...'}</Text>
          <Text style={styles.roleText}>Mentee • {department}</Text>
        </View>

        <View style={styles.body}>
          <View style={styles.formCardMentee}>
            <Text style={styles.inputLabel}>Full Name</Text>
            <TextInput style={styles.input} value={fullName} onChangeText={setFullName} />
            <Text style={styles.inputLabel}>Department</Text>
            <TextInput style={styles.input} value={department} onChangeText={setDepartment} />
            <Text style={styles.inputLabel}>About Me</Text>
            <TextInput style={[styles.input, styles.aboutInput]} value={aboutMe} onChangeText={setAboutMe} multiline />
            
            <TokenEditor 
              label="Interests" 
              placeholder="Add interest" 
              values={interests} 
              inputValue={interestInput} 
              setInputValue={setInterestInput} 
              onAdd={() => { if(interestInput) setInterests([...interests, interestInput]); setInterestInput(''); }} 
              onRemove={(t) => setInterests(interests.filter(i => i !== t))} 
            />
          </View>
          {/* Mentee için */}
          <TouchableOpacity style={styles.saveButtonMentee} onPress={handleSave}>
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

// --- MENTOR PROFILI ---
function MentorProfileContent({ onLogout }: { onLogout: () => void }) {
  const [displayName, setDisplayName] = useState('');
  const [title, setTitle] = useState('');
  const [bio, setBio] = useState('');
// MentorProfileContent içinde:
  const [expertise, setExpertise] = useState<string[]>([]);
  const [expertiseInput, setExpertiseInput] = useState('');

  useEffect(() => {
    const fetchProfileData = async () => {
      try {
        const userId = await SecureStore.getItemAsync('userId');
        if (!userId) return;

        const response = await apiClient.get(`/users/${userId}`); //
        const data = response.data; //

        setDisplayName(`${data.firstName} ${data.lastName}`);
        setTitle(data.field || '');
        setBio(data.bio || '');
        if (data.interests) setExpertise(data.interests);
      } catch (error) {
        console.error('Error fetching mentor profile:', error);
      }
    };
    fetchProfileData();
  }, []);

  return (
    <View style={styles.container}>
      <ScrollView contentContainerStyle={styles.scrollContent} showsVerticalScrollIndicator={false}>
        <View style={styles.header}>
          <View style={styles.topCircle} />
          <View style={styles.avatarCircle}>
            <Text style={styles.avatarText}>{getInitials(displayName)}</Text>
          </View>
          <Text style={styles.name}>{displayName || 'Loading...'}</Text>
          <Text style={styles.roleText}>{title}</Text>
        </View>
        
        <View style={styles.body}>
          <View style={styles.formCardMentor}>
            <Text style={styles.inputLabel}>Display Name</Text>
            <TextInput style={styles.input} value={displayName} onChangeText={setDisplayName} />
            <Text style={styles.inputLabel}>Title</Text>
            <TextInput style={styles.input} value={title} onChangeText={setTitle} />
            <Text style={styles.inputLabel}>Bio</Text>
            <TextInput style={[styles.input, styles.bigInput]} value={bio} onChangeText={setBio} multiline />
          </View>
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
  avatarCircle: { width: 110, height: 110, borderRadius: 55, borderWidth: 3, borderColor: 'rgba(255,255,255,0.38)', justifyContent: 'center', alignItems: 'center', marginTop: 20, marginBottom: 15 },
  avatarText: { color: '#F5F1E9', fontSize: 32, fontWeight: '700' },
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
  logoutButton: { backgroundColor: '#FDF0EF', borderWidth: 1, borderColor: '#FAD4D4', borderRadius: 20, paddingVertical: 15, alignItems: 'center' },
  logoutButtonText: { color: '#D9534F', fontSize: 16, fontWeight: '700' },
  // ... mevcut stillerinin sonuna şunları ekle:

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
});