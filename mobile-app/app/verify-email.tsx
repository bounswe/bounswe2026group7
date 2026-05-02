import { router, useLocalSearchParams } from 'expo-router';
import React, { useEffect, useMemo, useState } from 'react';
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

function parseString(value: string | string[] | undefined) {
  return Array.isArray(value) ? value[0] : value ?? '';
}

const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

type VerifyStatus = 'pending' | 'loading' | 'success' | 'error';

export default function VerifyEmailScreen() {
  const params = useLocalSearchParams();
  const routeToken = parseString(params.token);
  const initialEmail = parseString(params.email);
  const [email, setEmail] = useState(initialEmail);
  const [verificationToken, setVerificationToken] = useState(routeToken);
  const [status, setStatus] = useState<VerifyStatus>(routeToken ? 'loading' : 'pending');
  const [message, setMessage] = useState(
    routeToken ? 'Verifying your email...' : 'Check your inbox to verify your account.'
  );
  const [emailError, setEmailError] = useState('');
  const [isResending, setIsResending] = useState(false);
  const [isVerifyingToken, setIsVerifyingToken] = useState(false);

  const handleVerifyToken = async (candidate: string) => {
    const trimmedToken = candidate.trim();
    if (!trimmedToken) {
      setStatus('error');
      setMessage('Please paste the verification token from your email link.');
      return;
    }

    setIsVerifyingToken(true);
    setStatus('loading');
    setMessage('Verifying your email...');
    try {
      const response = await apiClient.get(`/auth/verify-email?token=${encodeURIComponent(trimmedToken)}`);
      setStatus('success');
      setMessage(response.data?.message || 'Your email has been verified. You can now sign in.');
    } catch (error: any) {
      setStatus('error');
      setMessage(
        error.response?.data?.message || 'This verification link is invalid or has expired.'
      );
    } finally {
      setIsVerifyingToken(false);
    }
  };

  useEffect(() => {
    if (!routeToken) return;
    handleVerifyToken(routeToken);
  }, [routeToken]);

  const canResend = useMemo(() => EMAIL_REGEX.test(email.trim()), [email]);

  const handleResend = async () => {
    const trimmedEmail = email.trim();
    setEmailError('');

    if (!trimmedEmail) {
      setEmailError('Email is required.');
      return;
    }

    if (!EMAIL_REGEX.test(trimmedEmail)) {
      setEmailError('Enter a valid email address.');
      return;
    }

    setIsResending(true);
    try {
      const response = await apiClient.post('/auth/resend-verification', { email: trimmedEmail });
      setStatus('pending');
      setMessage(response.data?.message || 'Verification email sent. Please check your inbox.');
    } catch (error: any) {
      setStatus('error');
      setMessage(
        error.response?.data?.message || error.message || 'Could not resend verification email.'
      );
    } finally {
      setIsResending(false);
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

        <TouchableOpacity style={styles.backButton} onPress={() => router.replace('/login')}>
          <Text style={styles.backButtonText}>‹ Back</Text>
        </TouchableOpacity>

        <View style={styles.content}>
          {status === 'loading' ? (
            <>
              <Text style={styles.title}>Verifying{'\n'}<Text style={styles.titleItalic}>email...</Text></Text>
              <ActivityIndicator color="#F7F4EE" style={{ marginTop: 12 }} />
            </>
          ) : (
            <>
              <Text style={styles.title}>
                {status === 'success' ? 'Email' : 'Verify'}
                {'\n'}
                <Text style={styles.titleItalic}>{status === 'success' ? 'verified!' : 'email.'}</Text>
              </Text>
              <Text style={status === 'success' ? styles.successText : status === 'error' ? styles.errorText : styles.subtitle}>
                {message}
              </Text>

              {status !== 'success' && (
                <>
                  <Text style={styles.label}>VERIFICATION TOKEN</Text>
                  <TextInput
                    style={styles.input}
                    placeholder="Paste token from your email"
                    placeholderTextColor="rgba(255,255,255,0.45)"
                    autoCapitalize="none"
                    value={verificationToken}
                    onChangeText={(value) => {
                      setVerificationToken(value);
                    }}
                  />

                  <TouchableOpacity
                    style={[styles.primaryButton, !verificationToken.trim() && styles.primaryButtonDisabled]}
                    disabled={!verificationToken.trim() || isVerifyingToken}
                    onPress={() => handleVerifyToken(verificationToken)}
                  >
                    {isVerifyingToken ? (
                      <ActivityIndicator color="#2F563C" />
                    ) : (
                      <Text style={styles.primaryButtonText}>Verify with Token</Text>
                    )}
                  </TouchableOpacity>

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
                    }}
                  />
                  {!!emailError && <Text style={styles.errorText}>{emailError}</Text>}

                  <TouchableOpacity
                    style={[styles.primaryButton, !canResend && styles.primaryButtonDisabled]}
                    disabled={!canResend || isResending}
                    onPress={handleResend}
                  >
                    {isResending ? (
                      <ActivityIndicator color="#2F563C" />
                    ) : (
                      <Text style={styles.primaryButtonText}>Resend Verification</Text>
                    )}
                  </TouchableOpacity>
                </>
              )}

              <TouchableOpacity style={styles.secondaryButton} onPress={() => router.replace('/login')}>
                <Text style={styles.secondaryButtonText}>{status === 'success' ? 'Sign In' : 'Back to Sign In'}</Text>
              </TouchableOpacity>
            </>
          )}
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
    width: 340,
    height: 340,
    borderRadius: 170,
    backgroundColor: 'rgba(255,255,255,0.05)',
    top: -30,
    right: -70,
  },
  bottomCircle: {
    position: 'absolute',
    width: 230,
    height: 230,
    borderRadius: 115,
    backgroundColor: 'rgba(255,255,255,0.04)',
    bottom: 140,
    left: -40,
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
    lineHeight: 22,
    marginBottom: 24,
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
    marginBottom: 14,
  },
  primaryButtonDisabled: {
    backgroundColor: 'rgba(255,255,255,0.35)',
  },
  primaryButtonText: {
    color: '#2F563C',
    fontSize: 18,
    fontWeight: '700',
  },
  secondaryButton: {
    borderRadius: 24,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.24)',
    paddingVertical: 18,
    alignItems: 'center',
  },
  secondaryButtonText: {
    color: '#F7F4EE',
    fontSize: 16,
    fontWeight: '700',
  },
  errorText: {
    color: '#FFD5CF',
    fontSize: 14,
    lineHeight: 20,
    marginBottom: 12,
  },
  successText: {
    color: '#D6F0DA',
    fontSize: 15,
    lineHeight: 22,
    marginBottom: 24,
  },
});
