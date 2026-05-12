import { Tabs, router } from 'expo-router';
import React, { useEffect, useState } from 'react';
import { Text, View, ActivityIndicator } from 'react-native';
import * as SecureStore from 'expo-secure-store';
import { useRole } from '../../components/RoleContext';

export default function TabLayout() {
  const { role } = useRole();
  const isMentor = role === 'mentor';
  const [ready, setReady] = useState(false);

  useEffect(() => {
    SecureStore.getItemAsync('userToken').then((token) => {
      if (!token) {
        router.replace('/onboarding');
      } else {
        setReady(true);
      }
    });
  }, []);

  if (!ready) {
    return (
      <View style={{ flex: 1, backgroundColor: '#ECE8E1', justifyContent: 'center', alignItems: 'center' }}>
        <ActivityIndicator size="large" color="#456B50" />
      </View>
    );
  }

  return (
    <Tabs
      screenOptions={{
        headerShown: false,
        tabBarActiveTintColor: '#456B50',
        tabBarInactiveTintColor: '#A29A90',
        tabBarStyle: {
          backgroundColor: '#F8F6F2',
          borderTopColor: '#DDD5CA',
          height: 82,
          paddingTop: 8,
          paddingBottom: 12,
        },
        tabBarLabelStyle: {
          fontSize: 12,
          fontWeight: '500',
        },
      }}
    >
      <Tabs.Screen
        name="index"
        options={{
          title: 'Home',
          tabBarIcon: ({ color }) => <Text style={{ fontSize: 22, color }}>🏠</Text>,
          tabBarAccessibilityLabel: 'Home tab',
          tabBarButtonTestID: 'tabs.home',
        }}
      />

      <Tabs.Screen
        name="explore"
        options={{
          title: isMentor ? 'Requests' : 'Explore',
          tabBarIcon: ({ color }) => <Text style={{ fontSize: 22, color }}>{isMentor ? '📋' : '🔍'}</Text>,
          tabBarAccessibilityLabel: isMentor ? 'Requests tab' : 'Explore tab',
          tabBarButtonTestID: 'tabs.explore',
        }}
      />

      <Tabs.Screen
        name="feed"
        options={{
          title: 'Feed',
          tabBarIcon: ({ color }) => <Text style={{ fontSize: 22, color }}>📰</Text>,
          tabBarAccessibilityLabel: 'Feed tab',
          tabBarButtonTestID: 'tabs.feed',
        }}
      />

      <Tabs.Screen
        name="messages"
        options={{
          title: 'Messages',
          tabBarIcon: ({ color }) => <Text style={{ fontSize: 22, color }}>💬</Text>,
          tabBarAccessibilityLabel: 'Messages tab',
          tabBarButtonTestID: 'tabs.messages',
        }}
      />

      <Tabs.Screen
        name="profile"
        options={{
          title: 'Profile',
          tabBarIcon: ({ color }) => <Text style={{ fontSize: 22, color }}>👤</Text>,
          tabBarAccessibilityLabel: 'Profile tab',
          tabBarButtonTestID: 'tabs.profile',
        }}
      />
    </Tabs>
  );
}
