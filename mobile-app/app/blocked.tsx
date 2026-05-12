import React, { useEffect, useState } from 'react';
import { router } from 'expo-router';
import { ScrollView, StyleSheet, Text, TouchableOpacity, View } from 'react-native';

import { clearBanNotice, readBanNotice } from '../utils/banNotice';

function formatExpiry(expiresAt: string | null) {
  if (!expiresAt) return null;
  const date = new Date(expiresAt);
  if (Number.isNaN(date.getTime())) return null;
  return date.toLocaleString('tr-TR', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

export default function BlockedScreen() {
  const [reason, setReason] = useState<string | null>(null);
  const [expiresAt, setExpiresAt] = useState<string | null>(null);

  useEffect(() => {
    readBanNotice().then((notice) => {
      setReason(notice.reason);
      setExpiresAt(notice.expiresAt);
    });
  }, []);

  const goBackToLogin = async () => {
    await clearBanNotice();
    router.replace('/login');
  };

  return (
    <View style={styles.container}>
      <View style={styles.topCircle} />
      <View style={styles.bottomCircle} />

      <ScrollView contentContainerStyle={styles.scrollContent} showsVerticalScrollIndicator={false}>
        <View style={styles.card}>
          <Text style={styles.eyebrow}>ACCOUNT STATUS</Text>
          <Text style={styles.title}>Hesabiniz bloklandi</Text>
          <Text style={styles.message}>
            Bu hesaba erisim su anda kisitli. Ban kaldirilana kadar mobil uygulamada sadece bu uyari ekrani gosterilir.
          </Text>

          {reason ? (
            <View style={styles.infoBox}>
              <Text style={styles.infoLabel}>Neden</Text>
              <Text style={styles.infoValue}>{reason}</Text>
            </View>
          ) : null}

          {formatExpiry(expiresAt) ? (
            <View style={styles.infoBox}>
              <Text style={styles.infoLabel}>Bitis zamani</Text>
              <Text style={styles.infoValue}>{formatExpiry(expiresAt)}</Text>
            </View>
          ) : null}

          <Text style={styles.helpText}>
            Bunun bir hata oldugunu dusunuyorsaniz sistem yoneticisiyle iletisime gecin.
          </Text>

          <TouchableOpacity style={styles.button} onPress={goBackToLogin}>
            <Text style={styles.buttonText}>Giris ekranina don</Text>
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
    flexGrow: 1,
    justifyContent: 'center',
    paddingHorizontal: 24,
    paddingVertical: 40,
  },
  topCircle: {
    position: 'absolute',
    width: 300,
    height: 300,
    borderRadius: 150,
    backgroundColor: 'rgba(255,255,255,0.05)',
    top: -30,
    left: -70,
  },
  bottomCircle: {
    position: 'absolute',
    width: 220,
    height: 220,
    borderRadius: 110,
    backgroundColor: 'rgba(255,255,255,0.04)',
    bottom: -20,
    right: -40,
  },
  card: {
    backgroundColor: '#F7F4EE',
    borderRadius: 28,
    paddingHorizontal: 24,
    paddingVertical: 28,
    borderWidth: 1,
    borderColor: '#DDD5CA',
  },
  eyebrow: {
    color: '#8C3E35',
    fontSize: 12,
    fontWeight: '800',
    letterSpacing: 1,
    marginBottom: 10,
  },
  title: {
    color: '#23372B',
    fontSize: 30,
    lineHeight: 34,
    fontWeight: '800',
    marginBottom: 14,
  },
  message: {
    color: '#5E554D',
    fontSize: 15,
    lineHeight: 22,
    marginBottom: 18,
  },
  infoBox: {
    backgroundColor: '#EFE8DE',
    borderRadius: 18,
    paddingHorizontal: 16,
    paddingVertical: 14,
    marginBottom: 12,
  },
  infoLabel: {
    color: '#7E7368',
    fontSize: 11,
    fontWeight: '800',
    letterSpacing: 0.4,
    marginBottom: 6,
  },
  infoValue: {
    color: '#23372B',
    fontSize: 15,
    lineHeight: 21,
    fontWeight: '600',
  },
  helpText: {
    color: '#7E7368',
    fontSize: 13,
    lineHeight: 20,
    marginTop: 8,
    marginBottom: 22,
  },
  button: {
    backgroundColor: '#456B50',
    borderRadius: 18,
    paddingVertical: 16,
    alignItems: 'center',
  },
  buttonText: {
    color: '#F8F6F2',
    fontSize: 15,
    fontWeight: '800',
  },
});
