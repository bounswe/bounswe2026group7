import { Tabs } from 'expo-router';
import React from 'react';
import { Text } from 'react-native';
import { useRole } from '../../components/RoleContext';

export default function TabLayout() {
  const { role } = useRole();
  const isMentor = role === 'mentor';

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
        }}
      />

      <Tabs.Screen
        name="explore"
        options={{
          title: isMentor ? 'Requests' : 'Explore',
          tabBarIcon: ({ color }) => <Text style={{ fontSize: 22, color }}>{isMentor ? '📋' : '🔍'}</Text>,
          tabBarAccessibilityLabel: isMentor ? 'Requests tab' : 'Explore tab',
        }}
      />

      <Tabs.Screen
        name="messages"
        options={{
          title: 'Messages',
          tabBarIcon: ({ color }) => <Text style={{ fontSize: 22, color }}>💬</Text>,
          tabBarAccessibilityLabel: 'Messages tab',
        }}
      />

      <Tabs.Screen
        name="profile"
        options={{
          title: 'Profile',
          tabBarIcon: ({ color }) => <Text style={{ fontSize: 22, color }}>👤</Text>,
          tabBarAccessibilityLabel: 'Profile tab',
        }}
      />
    </Tabs>
  );
}
