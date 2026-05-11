import React, { useEffect, useRef } from 'react';
import { View, Text, StyleSheet, Animated, Image } from 'react-native';
import { router } from 'expo-router';
import * as SecureStore from 'expo-secure-store';

export default function SplashScreen() {
  const fadeAnim = useRef(new Animated.Value(0)).current;
  const scaleAnim = useRef(new Animated.Value(0.9)).current;

  useEffect(() => {
    Animated.parallel([
      Animated.timing(fadeAnim, {
        toValue: 1,
        duration: 1000,
        useNativeDriver: true,
      }),
      Animated.spring(scaleAnim, {
        toValue: 1,
        friction: 8,
        tension: 40,
        useNativeDriver: true,
      }),
    ]).start();

    const initializeApp = async () => {
      try {
        await new Promise((resolve) => setTimeout(resolve, 2500));
        
        const [token, userId, userRole] = await Promise.all([
          SecureStore.getItemAsync('userToken'),
          SecureStore.getItemAsync('userId'),
          SecureStore.getItemAsync('userRole'),
        ]);

        if (token && userId && (userRole === 'mentor' || userRole === 'mentee')) {
          router.replace('/(tabs)/profile');
        } else {
          await Promise.allSettled([
            SecureStore.deleteItemAsync('userToken'),
            SecureStore.deleteItemAsync('userId'),
            SecureStore.deleteItemAsync('userRole'),
          ]);
          router.replace('/login');
        }
      } catch (error) {
        router.replace('/login');
      }
    };

    initializeApp();
  }, [fadeAnim, scaleAnim]);

  return (
    <View style={styles.container}>
      <View style={styles.topCircle} />
      <View style={styles.bottomLeftCircle} />
      <View style={styles.bottomRightCircle} />

      <Animated.View 
        style={[
          styles.content, 
          { 
            opacity: fadeAnim,
            transform: [{ scale: scaleAnim }]
          }
        ]}
      >
        <View style={styles.logoContainer}>
          <Image
            source={require('../assets/images/splash-icon.png')}
            style={styles.logoImage}
            resizeMode="contain"
          />
        </View>
        <Text style={styles.title}>MentorNet</Text>
        <Text style={styles.subtitle}>
          Grow with <Text style={styles.subtitleItalic}>purpose.</Text>
        </Text>
      </Animated.View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#456B50',
    justifyContent: 'center',
    alignItems: 'center',
    overflow: 'hidden',
  },
  topCircle: {
    position: 'absolute',
    width: 400,
    height: 400,
    borderRadius: 200,
    backgroundColor: 'rgba(255,255,255,0.04)',
    top: -100,
    right: -100,
  },
  bottomLeftCircle: {
    position: 'absolute',
    width: 250,
    height: 250,
    borderRadius: 125,
    backgroundColor: 'rgba(255,255,255,0.05)',
    bottom: -50,
    left: -80,
  },
  bottomRightCircle: {
    position: 'absolute',
    width: 150,
    height: 150,
    borderRadius: 75,
    backgroundColor: 'rgba(255,255,255,0.03)',
    bottom: 150,
    right: -40,
  },
  content: {
    alignItems: 'center',
    zIndex: 10,
  },
  logoContainer: {
    width: 124,
    height: 124,
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: 24,
  },
  logoImage: {
    width: 124,
    height: 124,
  },
  title: {
    fontSize: 42,
    fontWeight: '800',
    color: '#F7F4EE',
    letterSpacing: 1.5,
    marginBottom: 12,
  },
  subtitle: {
    fontSize: 18,
    color: 'rgba(247,244,238,0.85)',
    fontWeight: '500',
    letterSpacing: 0.5,
  },
  subtitleItalic: {
    fontStyle: 'italic',
    fontWeight: '700',
    color: '#F7F4EE',
  },
});
