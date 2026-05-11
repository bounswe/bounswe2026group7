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

function isStrongPassword(password: string) {
  return (
    password.length >= 8 &&
    /[A-Z]/.test(password) &&
    /[a-z]/.test(password) &&
    /[0-9]/.test(password)
  );
}

export default function ResetPasswordScreen() {
  const params = useLocalSearchParams();
  const routeToken = parseString(params.token);

  const [tokenInput, setTokenInput] = useState(routeToken);
  const [tokenValid, setTokenValid] = useState<boolean | null>(null);
  const [newPassword, setNewPassword] = useState('');
  const [passwordError, setPasswordError] = useState('');
  const [serverError, setServerError] = useState('');
  const [successMessage, setSuccessMessage] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isValidatingToken, setIsValidatingToken] = useState(false);

  const validateToken = async (candidate: string) => {
    const trimmedToken = candidate.trim();
    if (!trimmedToken) {
      setTokenValid(false);
      return;
    }

    setIsValidatingToken(true);
    setServerError('');
    try {
      await apiClient.get(`/auth/validate-reset-token?token=${encodeURIComponent(trimmedToken)}`);
      setTokenValid(true);
    } catch (error: any) {
      setTokenValid(false);
      setServerError(
        error.response?.data?.message || 'This reset link is invalid or has expired.'
      );
    } finally {
      setIsValidatingToken(false);
    }
  };

  useEffect(() => {
    if (!routeToken) {
      setTokenValid(false);
      return;
    }

    validateToken(routeToken);
  }, [routeToken]);

  const isFormValid = useMemo(() => isStrongPassword(newPassword), [newPassword]);

  const handleSubmit = async () => {
    setPasswordError('');
    setServerError('');

    if (!newPassword) {
      setPasswordError('Password is required.');
      return;
    }

    if (!isStrongPassword(newPassword)) {
      setPasswordError('Password must be at least 8 characters with uppercase, lowercase, and a number.');
      return;
    }

    setIsSubmitting(true);
    try {
      const response = await apiClient.post('/auth/reset-password', {
        token: tokenInput.trim(),
        newPassword,
      });
      setSuccessMessage(response.data?.message || 'Password reset successfully. You can now sign in.');
    } catch (error: any) {
      setServerError(
        error.response?.data?.message || error.message || 'Something went wrong. Please try again.'
      );
    } finally {
      setIsSubmitting(false);
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

        <TouchableOpacity
          style={styles.backButton}
          onPress={() => router.replace('/login')}
          accessibilityRole="button"
          accessibilityLabel="Go back to sign in"
        >
          <Text style={styles.backButtonText}>‹ Back</Text>
        </TouchableOpacity>

        <View style={styles.content}>
          {isValidatingToken || tokenValid === null ? (
            <>
              <Text style={styles.title}>Validating{'\n'}<Text style={styles.titleItalic}>link...</Text></Text>
              <ActivityIndicator color="#F7F4EE" style={{ marginTop: 12 }} />
            </>
          ) : tokenValid === false ? (
            <>
              <Text style={styles.title}>Invalid{'\n'}<Text style={styles.titleItalic}>link.</Text></Text>
              <Text style={styles.errorText}>
                {serverError || 'This reset link is invalid or has expired.'}
              </Text>

              <Text style={styles.label}>RESET TOKEN</Text>
              <TextInput
                style={styles.input}
                placeholder="Paste token from your email"
                placeholderTextColor="rgba(255,255,255,0.45)"
                autoCapitalize="none"
                value={tokenInput}
                onChangeText={(value) => {
                  setTokenInput(value);
                  setServerError('');
                  setTokenValid(null);
                }}
                accessibilityLabel="Reset token"
                accessibilityHint="Paste the password reset token from your email"
              />

              <TouchableOpacity
                style={[styles.primaryButton, !tokenInput.trim() && styles.primaryButtonDisabled]}
                disabled={!tokenInput.trim() || isValidatingToken}
                onPress={() => validateToken(tokenInput)}
                accessibilityRole="button"
                accessibilityLabel="Validate token"
                accessibilityState={{ disabled: !tokenInput.trim() || isValidatingToken, busy: isValidatingToken }}
              >
                {isValidatingToken ? (
                  <ActivityIndicator color="#2F563C" />
                ) : (
                  <Text style={styles.primaryButtonText}>Validate Token</Text>
                )}
              </TouchableOpacity>

              <TouchableOpacity
                style={styles.secondaryButton}
                onPress={() => router.replace('/forgot-password' as any)}
                accessibilityRole="button"
                accessibilityLabel="Request a new reset link"
              >
                <Text style={styles.secondaryButtonText}>Request a New Link</Text>
              </TouchableOpacity>
            </>
          ) : successMessage ? (
            <>
              <Text style={styles.title}>Password{'\n'}<Text style={styles.titleItalic}>reset.</Text></Text>
              <Text style={styles.successText}>{successMessage}</Text>
              <TouchableOpacity
                style={styles.primaryButton}
                onPress={() => router.replace('/login')}
                accessibilityRole="button"
                accessibilityLabel="Go to sign in"
              >
                <Text style={styles.primaryButtonText}>Go to Sign In</Text>
              </TouchableOpacity>
            </>
          ) : (
            <>
              <Text style={styles.title}>Reset{'\n'}<Text style={styles.titleItalic}>password.</Text></Text>
              <Text style={styles.subtitle}>Enter your new password below.</Text>

              {!!serverError && <Text style={styles.errorText}>{serverError}</Text>}

              <Text style={styles.label}>RESET TOKEN</Text>
              <TextInput
                style={styles.input}
                placeholder="Paste token from your email"
                placeholderTextColor="rgba(255,255,255,0.45)"
                autoCapitalize="none"
                value={tokenInput}
                onChangeText={(value) => {
                  setTokenInput(value);
                  setServerError('');
                }}
                accessibilityLabel="Reset token"
                accessibilityHint="Paste the password reset token from your email"
              />

              <Text style={styles.label}>NEW PASSWORD</Text>
              <TextInput
                style={styles.input}
                placeholder="••••••••"
                placeholderTextColor="rgba(255,255,255,0.45)"
                secureTextEntry
                value={newPassword}
                onChangeText={(value) => {
                  setNewPassword(value);
                  setPasswordError('');
                  setServerError('');
                }}
                accessibilityLabel="New password"
                accessibilityHint="Enter a strong password with uppercase, lowercase, and a number"
              />
              {!!passwordError && <Text style={styles.errorText}>{passwordError}</Text>}

              <TouchableOpacity
                style={[styles.primaryButton, !isFormValid && styles.primaryButtonDisabled]}
                disabled={!isFormValid || isSubmitting}
                onPress={handleSubmit}
                accessibilityRole="button"
                accessibilityLabel="Reset password"
                accessibilityState={{ disabled: !isFormValid || isSubmitting, busy: isSubmitting }}
              >
                {isSubmitting ? (
                  <ActivityIndicator color="#2F563C" />
                ) : (
                  <Text style={styles.primaryButtonText}>Reset Password</Text>
                )}
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
    width: 320,
    height: 320,
    borderRadius: 160,
    backgroundColor: 'rgba(255,255,255,0.05)',
    top: -30,
    left: -60,
  },
  bottomCircle: {
    position: 'absolute',
    width: 220,
    height: 220,
    borderRadius: 110,
    backgroundColor: 'rgba(255,255,255,0.04)',
    bottom: 120,
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
    marginTop: 18,
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
