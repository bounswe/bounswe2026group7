import { router } from 'expo-router';
import { useRole } from '../components/RoleContext';
import React, { useMemo, useState } from 'react';
import * as ImagePicker from 'expo-image-picker';
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  StyleSheet,
  ScrollView,
  Image,
} from 'react-native';

export default function RegisterScreen() {
  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [bio, setBio] = useState('');
  const [selectedRole, setSelectedRole] = useState<'Mentee' | 'Mentor'>('Mentee');
  const [interests, setInterests] = useState<string[]>([]);
  const [interestInput, setInterestInput] = useState('');
  const [image, setImage] = useState<string | null>(null);

  const { setRole: setGlobalRole } = useRole();

  const pickImage = async () => {
    const result = await ImagePicker.launchImageLibraryAsync({
      mediaTypes: ['images'],
      allowsEditing: true,
      aspect: [1, 1],
      quality: 1,
    });

    if (!result.canceled) {
      setImage(result.assets[0].uri);
    }
  };

  const addInterest = () => {
    const trimmed = interestInput.trim();
    if (!trimmed) return;

    const exists = interests.some(
      (item) => item.toLowerCase() === trimmed.toLowerCase()
    );
    if (exists) {
      setInterestInput('');
      return;
    }

    setInterests([...interests, trimmed]);
    setInterestInput('');
  };

  const removeInterest = (interestToRemove: string) => {
    setInterests(interests.filter((item) => item !== interestToRemove));
  };

  const isFormValid = useMemo(() => {
    return (
      fullName.trim().length > 0 &&
      email.trim().length > 0 &&
      password.trim().length > 0 &&
      interests.length > 0
    );
  }, [fullName, email, password, interests]);

  const handleRegister = () => {
    const appRole = selectedRole === 'Mentor' ? 'mentor' : 'mentee';
    setGlobalRole(appRole);
    router.replace('/(tabs)/profile');
  };

  return (
    <View style={styles.container}>
      <View style={styles.topCircle} />
      <View style={styles.bottomCircle} />

      <ScrollView
        contentContainerStyle={styles.scrollContent}
        showsVerticalScrollIndicator={false}
      >
        <View style={styles.statusRow}>
          <Text style={styles.statusText}>9:41</Text>
          <Text style={styles.statusIcons}>▲ ▮</Text>
        </View>

        <View style={styles.content}>
          <Text style={styles.title}>Create Account</Text>
          <Text style={styles.subtitle}>Join the mentorship community</Text>

          <TouchableOpacity style={styles.imagePickerContainer} onPress={pickImage}>
            {image ? (
              <Image source={{ uri: image }} style={styles.profileImage} />
            ) : (
              <View style={styles.imagePlaceholder}>
                <Text style={styles.imagePlaceholderText}>+</Text>
                <Text style={styles.imagePlaceholderSub}>Photo</Text>
              </View>
            )}
          </TouchableOpacity>

          <Text style={styles.label}>FULL NAME</Text>
          <TextInput
            style={styles.input}
            placeholder="Name Surname"
            placeholderTextColor="rgba(255,255,255,0.45)"
            value={fullName}
            onChangeText={setFullName}
          />

          <Text style={styles.label}>EMAIL</Text>
          <TextInput
            style={styles.input}
            placeholder="example@boun.edu.tr"
            placeholderTextColor="rgba(255,255,255,0.45)"
            keyboardType="email-address"
            autoCapitalize="none"
            value={email}
            onChangeText={setEmail}
          />

          <Text style={styles.label}>BIO / SHORT FORM</Text>
          <TextInput
            style={[styles.input, styles.textArea]}
            placeholder="Tell us about yourself..."
            placeholderTextColor="rgba(255,255,255,0.45)"
            multiline
            numberOfLines={3}
            value={bio}
            onChangeText={setBio}
          />

          <Text style={styles.label}>INTERESTS (Add at least one)</Text>

          <View style={styles.interestInputRow}>
            <TextInput
              style={styles.interestInput}
              placeholder="Add an interest"
              placeholderTextColor="rgba(255,255,255,0.45)"
              value={interestInput}
              onChangeText={setInterestInput}
              onSubmitEditing={addInterest}
              returnKeyType="done"
            />
            <TouchableOpacity style={styles.addButton} onPress={addInterest}>
              <Text style={styles.addButtonText}>Add</Text>
            </TouchableOpacity>
          </View>

          <View style={styles.interestsContainer}>
            {interests.map((item) => (
              <View key={item} style={styles.interestChip}>
                <Text style={styles.interestText}>{item}</Text>
                <TouchableOpacity onPress={() => removeInterest(item)}>
                  <Text style={styles.removeText}>×</Text>
                </TouchableOpacity>
              </View>
            ))}
          </View>

          <Text style={styles.label}>PASSWORD</Text>
          <TextInput
            style={styles.input}
            placeholder="••••••••"
            placeholderTextColor="rgba(255,255,255,0.45)"
            secureTextEntry
            value={password}
            onChangeText={setPassword}
          />

          <Text style={styles.label}>I AM A</Text>
          <View style={styles.roleRow}>
            <TouchableOpacity
              style={[
                styles.roleButton,
                selectedRole === 'Mentee' && styles.roleButtonActive,
              ]}
              onPress={() => setSelectedRole('Mentee')}
            >
              <Text
                style={[
                  styles.roleText,
                  selectedRole === 'Mentee' && styles.roleTextActive,
                ]}
              >
                Mentee
              </Text>
            </TouchableOpacity>

            <TouchableOpacity
              style={[
                styles.roleButton,
                selectedRole === 'Mentor' && styles.roleButtonActive,
              ]}
              onPress={() => setSelectedRole('Mentor')}
            >
              <Text
                style={[
                  styles.roleText,
                  selectedRole === 'Mentor' && styles.roleTextActive,
                ]}
              >
                Mentor
              </Text>
            </TouchableOpacity>
          </View>

          <TouchableOpacity
            style={[
              styles.primaryButton,
              !isFormValid && styles.primaryButtonDisabled,
            ]}
            disabled={!isFormValid}
            onPress={handleRegister}
          >
            <Text
              style={[
                styles.primaryButtonText,
                !isFormValid && styles.primaryButtonTextDisabled,
              ]}
            >
              Create Account
            </Text>
          </TouchableOpacity>

          <Text style={styles.bottomText}>
            Already have an account?{' '}
            <Text style={styles.signInText} onPress={() => router.push('/login')}>
              Sign in
            </Text>
          </Text>
        </View>
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#4D7257',
    overflow: 'hidden',
  },
  scrollContent: {
    paddingHorizontal: 28,
    paddingTop: 54,
    paddingBottom: 40,
  },
  topCircle: {
    position: 'absolute',
    width: 360,
    height: 360,
    borderRadius: 180,
    backgroundColor: 'rgba(255,255,255,0.05)',
    top: -10,
    right: -70,
  },
  bottomCircle: {
    position: 'absolute',
    width: 240,
    height: 240,
    borderRadius: 120,
    backgroundColor: 'rgba(255,255,255,0.04)',
    bottom: 120,
    left: -70,
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
  content: {
    marginTop: 20,
  },
  title: {
    color: '#F7F4EE',
    fontSize: 34,
    fontWeight: '700',
    marginBottom: 8,
  },
  subtitle: {
    color: 'rgba(255,255,255,0.65)',
    fontSize: 15,
    marginBottom: 24,
    fontWeight: '500',
  },
  imagePickerContainer: {
    alignSelf: 'center',
    marginBottom: 30,
  },
  imagePlaceholder: {
    width: 100,
    height: 100,
    borderRadius: 50,
    backgroundColor: 'rgba(255,255,255,0.12)',
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.2)',
    justifyContent: 'center',
    alignItems: 'center',
  },
  imagePlaceholderText: {
    color: '#FFFFFF',
    fontSize: 30,
    fontWeight: '300',
  },
  imagePlaceholderSub: {
    color: 'rgba(255,255,255,0.6)',
    fontSize: 12,
    marginTop: -4,
  },
  profileImage: {
    width: 100,
    height: 100,
    borderRadius: 50,
  },
  label: {
    color: 'rgba(255,255,255,0.70)',
    fontSize: 12,
    fontWeight: '700',
    marginBottom: 10,
    letterSpacing: 0.5,
  },
  input: {
    height: 60,
    borderRadius: 20,
    borderWidth: 1.5,
    borderColor: 'rgba(255,255,255,0.16)',
    backgroundColor: 'rgba(255,255,255,0.06)',
    paddingHorizontal: 20,
    color: '#FFFFFF',
    fontSize: 16,
    fontWeight: '500',
    marginBottom: 20,
  },
  textArea: {
    height: 100,
    paddingTop: 15,
    textAlignVertical: 'top',
  },
  interestInputRow: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 14,
  },
  interestInput: {
    flex: 1,
    height: 54,
    borderRadius: 18,
    borderWidth: 1.5,
    borderColor: 'rgba(255,255,255,0.16)',
    backgroundColor: 'rgba(255,255,255,0.06)',
    paddingHorizontal: 18,
    color: '#FFFFFF',
    fontSize: 15,
    fontWeight: '500',
    marginRight: 10,
  },
  addButton: {
    height: 54,
    paddingHorizontal: 18,
    borderRadius: 18,
    backgroundColor: '#F8F8F6',
    justifyContent: 'center',
    alignItems: 'center',
  },
  addButtonText: {
    color: '#274B34',
    fontSize: 14,
    fontWeight: '700',
  },
  interestsContainer: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
    marginBottom: 24,
  },
  interestChip: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 16,
    paddingVertical: 10,
    borderRadius: 20,
    backgroundColor: 'rgba(255,255,255,0.08)',
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.15)',
  },
  interestText: {
    color: 'rgba(255,255,255,0.92)',
    fontSize: 14,
    fontWeight: '600',
  },
  removeText: {
    color: 'rgba(255,255,255,0.92)',
    fontSize: 18,
    fontWeight: '700',
    marginLeft: 8,
    lineHeight: 18,
  },
  roleRow: {
    flexDirection: 'row',
    gap: 16,
    marginBottom: 30,
  },
  roleButton: {
    flex: 1,
    height: 60,
    borderRadius: 20,
    borderWidth: 1.5,
    borderColor: 'rgba(255,255,255,0.18)',
    backgroundColor: 'rgba(255,255,255,0.05)',
    justifyContent: 'center',
    alignItems: 'center',
  },
  roleButtonActive: {
    backgroundColor: '#F8F8F6',
    borderColor: '#F8F8F6',
  },
  roleText: {
    color: 'rgba(255,255,255,0.55)',
    fontSize: 16,
    fontWeight: '600',
  },
  roleTextActive: {
    color: '#274B34',
    fontWeight: '700',
  },
  primaryButton: {
    backgroundColor: '#F8F8F6',
    borderRadius: 22,
    paddingVertical: 18,
    alignItems: 'center',
    marginBottom: 28,
  },
  primaryButtonDisabled: {
    backgroundColor: 'rgba(255,255,255,0.35)',
  },
  primaryButtonText: {
    color: '#2F563C',
    fontSize: 18,
    fontWeight: '700',
  },
  primaryButtonTextDisabled: {
    color: 'rgba(47,86,60,0.55)',
  },
  bottomText: {
    textAlign: 'center',
    color: 'rgba(255,255,255,0.45)',
    fontSize: 15,
    fontWeight: '500',
    marginBottom: 20,
  },
  signInText: {
    color: '#DCE7D9',
    fontWeight: '700',
  },
});