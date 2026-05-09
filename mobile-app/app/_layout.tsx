import { DarkTheme, DefaultTheme, ThemeProvider } from '@react-navigation/native';
import { RoleProvider } from '../components/RoleContext';
import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import 'react-native-reanimated';
import React from 'react';
import { useColorScheme } from '../hooks/use-color-scheme';

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

          <Stack.Screen name="notifications" options={{ headerShown: false }} />
          <Stack.Screen name="availability-scheduling" options={{ headerShown: false }} />
          <Stack.Screen name="meetings-sessions" options={{ headerShown: false }} />
          <Stack.Screen name="mentorship-requests" options={{ headerShown: false }} />
          <Stack.Screen name="task-tracker" options={{ headerShown: false }} />
          <Stack.Screen name="connection-profile" options={{ headerShown: false }} />
          <Stack.Screen name="mentor-public-profile" options={{ headerShown: false }} />
          <Stack.Screen name="request-candidate-profile" options={{ headerShown: false }} />
          <Stack.Screen name="connection-request" options={{ headerShown: false }} />
          <Stack.Screen name="social-feed" options={{ headerShown: false }} />
          <Stack.Screen name="forgot-password" options={{ headerShown: false }} />
          <Stack.Screen name="reset-password" options={{ headerShown: false }} />
          <Stack.Screen name="verify-email" options={{ headerShown: false }} />
        </Stack>
        <StatusBar style="auto" />
      </ThemeProvider>
    </RoleProvider>
  );
}
