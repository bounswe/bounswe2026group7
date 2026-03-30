import { router } from 'expo-router';
import { useRole } from '../components/RoleContext';
import React, { useMemo, useState } from 'react';
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  StyleSheet,
  ScrollView,
} from 'react-native';

export default function RegisterScreen() {
  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [selectedRole, setSelectedRole] = useState<'Mentee' | 'Mentor'>('Mentee');
  const { setRole: setGlobalRole } = useRole();

  const isFormValid = useMemo(() => {
    return (
      fullName.trim().length > 0 &&
      email.trim().length > 0 &&
      password.trim().length > 0
    );
  }, [fullName, email, password]);

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

          <Text style={styles.label}>FULL NAME</Text>
          <TextInput
            style={styles.input}
            placeholder="Övgü Su Afşar"
            placeholderTextColor="rgba(255,255,255,0.45)"
            value={fullName}
            onChangeText={setFullName}
          />

          <Text style={styles.label}>EMAIL</Text>
          <TextInput
            style={styles.input}
            placeholder="ovgu@boun.edu.tr"
            placeholderTextColor="rgba(255,255,255,0.45)"
            keyboardType="email-address"
            autoCapitalize="none"
            value={email}
            onChangeText={setEmail}
          />

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
            onPress={() => {
              const appRole = selectedRole === 'Mentor' ? 'mentor' : 'mentee';
              setGlobalRole(appRole);
              router.replace('/(tabs)/profile');
            }}
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
    marginTop: 34,
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
    marginBottom: 34,
    fontWeight: '500',
  },
  label: {
    color: 'rgba(255,255,255,0.70)',
    fontSize: 12,
    fontWeight: '700',
    marginBottom: 10,
    letterSpacing: 0.5,
  },
  input: {
    height: 78,
    borderRadius: 24,
    borderWidth: 1.5,
    borderColor: 'rgba(255,255,255,0.16)',
    backgroundColor: 'rgba(255,255,255,0.06)',
    paddingHorizontal: 28,
    color: '#FFFFFF',
    fontSize: 16,
    fontWeight: '500',
    marginBottom: 22,
  },
  roleRow: {
    flexDirection: 'row',
    gap: 16,
    marginBottom: 30,
  },
  roleButton: {
    flex: 1,
    height: 80,
    borderRadius: 24,
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
    fontSize: 18,
    fontWeight: '600',
  },
  roleTextActive: {
    color: '#274B34',
    fontWeight: '700',
  },
  primaryButton: {
    backgroundColor: '#F8F8F6',
    borderRadius: 26,
    paddingVertical: 22,
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