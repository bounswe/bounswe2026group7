import { router } from 'expo-router';
import React, { useMemo, useState } from 'react';
import {
  ActivityIndicator,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native';
import apiClient from '../api/client';

const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export default function ForgotPasswordScreen() {
  const [email, setEmail] = useState('');
  const [emailError, setEmailError] = useState('');
  const [serverError, setServerError] = useState('');
  const [successMessage, setSuccessMessage] = useState('');
  const [isLoading, setIsLoading] = useState(false);

  const isFormValid = useMemo(() => EMAIL_REGEX.test(email.trim()), [email]);

  const handleSubmit = async () => {
    const trimmedEmail = email.trim();
    setEmailError('');
    setServerError('');

    if (!trimmedEmail) {
      setEmailError('Email is required.');
      return;
    }

    if (!EMAIL_REGEX.test(trimmedEmail)) {
      setEmailError('Enter a valid email address.');
      return;
    }

    setIsLoading(true);
    try {
      const response = await apiClient.post('/auth/forgot-password', { email: trimmedEmail });
      setSuccessMessage(
        response.data?.message || 'If that email is registered, a password reset link has been sent.'
      );
    } catch (error: any) {
      setServerError(
        error.response?.data?.message || error.message || 'Something went wrong. Please try again.'
      );
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <View style={styles.container}>
      <View style={styles.topCircle} />
      <View style={styles.bottomCircle} />

      <ScrollView contentContainerStyle={styles.scrollContent} showsVerticalScrollIndicator={false}>
        <View style={styles.statusRow}>
          <Text style={styles.statusText}>
            {new Date().toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit', hour12: false })}
          </Text>
          <Text style={styles.statusIcons}>▲ ▮</Text>
        </View>

        <TouchableOpacity style={styles.backButton} onPress={() => router.back()}>
          <Text style={styles.backButtonText}>‹ Back</Text>
        </TouchableOpacity>

        <View style={styles.content}>
          <Text style={styles.title}>
            Forgot{'\n'}
            <Text style={styles.titleItalic}>password?</Text>
          </Text>
          <Text style={styles.subtitle}>Enter your email to receive a reset link.</Text>

          {!!serverError && <Text style={styles.errorText}>{serverError}</Text>}
          {!!successMessage && <Text style={styles.successText}>{successMessage}</Text>}

          {!successMessage && (
            <>
              <Text style={styles.label}>EMAIL</Text>
              <TextInput
                style={styles.input}
                placeholder="you@example.com"
                placeholderTextColor="rgba(255,255,255,0.45)"
                keyboardType="email-address"
                autoCapitalize="none"
                value={email}
                onChangeText={(value) => {
                  setEmail(value);
                  setEmailError('');
                  setServerError('');
                }}
              />
              {!!emailError && <Text style={styles.errorText}>{emailError}</Text>}

              <TouchableOpacity
                style={[styles.primaryButton, !isFormValid && styles.primaryButtonDisabled]}
                disabled={!isFormValid || isLoading}
                onPress={handleSubmit}
              >
                {isLoading ? (
                  <ActivityIndicator color="#2F563C" />
                ) : (
                  <Text style={styles.primaryButtonText}>Send Reset Link</Text>
                )}
              </TouchableOpacity>
            </>
          )}

          {!!successMessage && (
            <TouchableOpacity
              style={styles.primaryButton}
              onPress={() => router.push('/reset-password' as any)}
            >
              <Text style={styles.primaryButtonText}>I Have a Reset Token</Text>
            </TouchableOpacity>
          )}

          <TouchableOpacity onPress={() => router.replace('/login')}>
            <Text style={styles.footerLink}>Back to Sign In</Text>
          </TouchableOpacity>
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
    right: -70,
  },
  bottomCircle: {
    position: 'absolute',
    width: 220,
    height: 220,
    borderRadius: 110,
    backgroundColor: 'rgba(255,255,255,0.04)',
    bottom: 160,
    left: -30,
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
  backButton: {
    marginTop: 24,
  },
  backButtonText: {
    color: '#F7F4EE',
    fontSize: 16,
    fontWeight: '700',
  },
  content: {
    marginTop: 28,
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
    color: 'rgba(255,255,255,0.68)',
    fontSize: 15,
    marginBottom: 28,
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
    marginBottom: 12,
  },
  primaryButton: {
    backgroundColor: '#F8F8F6',
    borderRadius: 26,
    paddingVertical: 22,
    alignItems: 'center',
    marginTop: 12,
    marginBottom: 24,
  },
  primaryButtonDisabled: {
    backgroundColor: 'rgba(255,255,255,0.35)',
  },
  primaryButtonText: {
    color: '#2F563C',
    fontSize: 18,
    fontWeight: '700',
  },
  footerLink: {
    color: '#C9E6CE',
    fontSize: 15,
    fontWeight: '600',
  },
  errorText: {
    color: '#FFD5CF',
    fontSize: 14,
    marginBottom: 12,
  },
  successText: {
    color: '#D6F0DA',
    fontSize: 15,
    lineHeight: 22,
    marginBottom: 24,
  },
});
