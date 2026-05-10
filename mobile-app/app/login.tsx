
import apiClient from '../api/client';
import * as SecureStore from 'expo-secure-store';
import { useRole } from '../components/RoleContext';

import { router } from 'expo-router';
import React, { useMemo, useState } from 'react';
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  StyleSheet,
  ScrollView,
  Alert,
} from 'react-native';

export default function LoginScreen() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');

  const isFormValid = useMemo(() => {
    return email.trim().length > 0 && password.trim().length > 0;
  }, [email, password]);

  const { setRole } = useRole(); // RoleContext'i alıyoruz

  const clearStoredSession = async () => {
    await Promise.allSettled([
      SecureStore.deleteItemAsync('userToken'),
      SecureStore.deleteItemAsync('userId'),
      SecureStore.deleteItemAsync('userRole'),
    ]);
  };

  const handleLogin = async () => {
    if (!email || !password) {
      Alert.alert("Error", "Please fill in all fields.");
      return;
    }

    try {
      // 1. Backend'e giriş isteği atıyoruz
      const response = await apiClient.post('/auth/login', {
        email: email,
        password: password
      });


    // 2. Gelen cevaptan token, rol VE userId'yi çıkarıyoruz
      const { sessionToken, role, userId } = response.data; // userId'yi de alıyoruz
      const normalizedRole = role.toLowerCase();

      await clearStoredSession();
      await SecureStore.setItemAsync('userId', userId.toString());
      await SecureStore.setItemAsync('userRole', normalizedRole);

      // 4. Backend'den dönen rolü uygulamamıza set ediyoruz 
      // (Backend "MENTOR" veya "MENTEE" dönüyorsa bunu küçük harfe çevirip context'e veriyoruz)
      setRole(normalizedRole);
      await SecureStore.setItemAsync('userToken', sessionToken);

      // 5. Başarılı giriş, ana sayfaya yönlendir
      router.replace('/(tabs)');

    } catch (error) {
      await clearStoredSession();
      Alert.alert("Login Failed", "Invalid email or password.");
      console.error(error);
    }
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
          <Text style={styles.statusText}>{new Date().toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit', hour12: false })}</Text>
          <Text style={styles.statusIcons}>▲ ▮</Text>
        </View>

        <View style={styles.content}>
          <Text style={styles.title}>
            Welcome{'\n'}
            <Text style={styles.titleItalic}>back.</Text>
          </Text>

          <Text style={styles.subtitle}>Sign in to continue your journey</Text>

          <Text style={styles.label}>EMAIL</Text>
          <TextInput
            style={styles.input}
            placeholder="ovgu@boun.edu.tr"
            placeholderTextColor="rgba(255,255,255,0.45)"
            keyboardType="email-address"
            autoCapitalize="none"
            value={email}
            onChangeText={setEmail}
            accessibilityLabel="Email address"
            accessibilityHint="Enter the email address for your account"
          />

          <Text style={styles.label}>PASSWORD</Text>
          <TextInput
            style={styles.input}
            placeholder="••••••••"
            placeholderTextColor="rgba(255,255,255,0.45)"
            secureTextEntry
            value={password}
            onChangeText={setPassword}
            accessibilityLabel="Password"
            accessibilityHint="Enter your account password"
          />

          <TouchableOpacity
            onPress={() => router.push('/forgot-password' as any)}
            accessibilityRole="button"
            accessibilityLabel="Forgot password"
            accessibilityHint="Opens password recovery"
          >
            <Text style={styles.forgotText}>Forgot password?</Text>
          </TouchableOpacity>

          <TouchableOpacity
            style={[
              styles.primaryButton,
              !isFormValid && styles.primaryButtonDisabled,
            ]}
            disabled={!isFormValid}
            onPress={handleLogin}
            accessibilityRole="button"
            accessibilityLabel="Sign in"
            accessibilityHint="Signs you in and opens the main app"
            accessibilityState={{ disabled: !isFormValid }}
          >
            <Text
              style={[
                styles.primaryButtonText,
                !isFormValid && styles.primaryButtonTextDisabled,
              ]}
            >
              Sign In
            </Text>
          </TouchableOpacity>

          <View style={styles.orRow}>
            <View style={styles.line} />
            <Text style={styles.orText}>or</Text>
            <View style={styles.line} />
          </View>

          <TouchableOpacity
            style={styles.googleButton}
            accessibilityRole="button"
            accessibilityLabel="Continue with Google"
            accessibilityHint="Starts Google sign in when available"
          >
            <Text style={styles.googleButtonText}>Continue with Google</Text>
          </TouchableOpacity>

          <Text style={styles.bottomText}>
            {"Don't have an account? "}
            <Text
              style={styles.signUpText}
              onPress={() => router.push('/register')}
            >
              Sign up
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
    width: 320,
    height: 320,
    borderRadius: 160,
    backgroundColor: 'rgba(255,255,255,0.05)',
    top: -20,
    left: -70,
  },
  bottomCircle: {
    position: 'absolute',
    width: 220,
    height: 220,
    borderRadius: 110,
    backgroundColor: 'rgba(255,255,255,0.04)',
    bottom: 170,
    right: -20,
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
    lineHeight: 38,
    fontWeight: '700',
    marginBottom: 10,
  },
  titleItalic: {
    fontStyle: 'italic',
    fontWeight: '700',
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
  forgotText: {
    textAlign: 'right',
    color: '#B9D9BE',
    fontSize: 14,
    fontWeight: '600',
    marginBottom: 26,
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
  orRow: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 28,
  },
  line: {
    flex: 1,
    height: 1,
    backgroundColor: 'rgba(255,255,255,0.18)',
  },
  orText: {
    marginHorizontal: 14,
    color: 'rgba(255,255,255,0.40)',
    fontSize: 14,
    fontWeight: '600',
  },
  googleButton: {
    borderWidth: 1.5,
    borderColor: 'rgba(255,255,255,0.20)',
    borderRadius: 24,
    paddingVertical: 22,
    alignItems: 'center',
    marginBottom: 28,
    backgroundColor: 'rgba(255,255,255,0.03)',
  },
  googleButtonText: {
    color: '#F6F7F2',
    fontSize: 17,
    fontWeight: '700',
  },
  bottomText: {
    textAlign: 'center',
    color: 'rgba(255,255,255,0.45)',
    fontSize: 15,
    fontWeight: '500',
    marginBottom: 20,
  },
  signUpText: {
    color: '#DCE7D9',
    fontWeight: '700',
  },
});
