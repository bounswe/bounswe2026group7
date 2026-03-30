import { DarkTheme, DefaultTheme, ThemeProvider } from '@react-navigation/native';
import { RoleProvider } from '../components/RoleContext';
import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import 'react-native-reanimated';
import React from 'react';
import { useColorScheme } from '@/hooks/use-color-scheme';

export const unstable_settings = {
  anchor: '(tabs)',
};

export default function RootLayout() {
  const colorScheme = useColorScheme();

  return (
    <RoleProvider>
      <ThemeProvider value={colorScheme === 'dark' ? DarkTheme : DefaultTheme}>
        <Stack>
          <Stack.Screen name="(tabs)" options={{ headerShown: false }} />
          <Stack.Screen name="modal" options={{ presentation: 'modal', title: 'Modal' }} />

          <Stack.Screen name="availability-scheduling" options={{ headerShown: false }} />
          <Stack.Screen name="meetings-sessions" options={{ headerShown: false }} />
          <Stack.Screen name="mentorship-requests" options={{ headerShown: false }} />
          <Stack.Screen name="task-tracker" options={{ headerShown: false }} />
        </Stack>
        <StatusBar style="auto" />
      </ThemeProvider>
    </RoleProvider>
  );
}