import React from 'react';
import { Alert } from 'react-native';
import { fireEvent, render, waitFor } from '@testing-library/react-native';
import * as SecureStore from 'expo-secure-store';
import { router } from 'expo-router';

import LoginScreen from '../app/login';
import apiClient from '../api/client';

const mockSetRole = jest.fn();

jest.mock('../api/client', () => ({
  __esModule: true,
  default: {
    post: jest.fn(),
  },
}));

jest.mock('expo-secure-store', () => ({
  setItemAsync: jest.fn(),
  getItemAsync: jest.fn(),
  deleteItemAsync: jest.fn(),
}));

jest.mock('../components/RoleContext', () => ({
  useRole: () => ({
    setRole: mockSetRole,
  }),
}));

jest.mock('expo-router', () => ({
  router: {
    push: jest.fn(),
    replace: jest.fn(),
    back: jest.fn(),
  },
}));

describe('LoginScreen', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    (SecureStore.deleteItemAsync as jest.Mock).mockResolvedValue(undefined);
    (SecureStore.setItemAsync as jest.Mock).mockResolvedValue(undefined);
  });

  it('does not submit until both fields are filled', () => {
    const { getByText, getByPlaceholderText } = render(<LoginScreen />);

    fireEvent.press(getByText('Sign In'));
    expect(apiClient.post).not.toHaveBeenCalled();

    fireEvent.changeText(getByPlaceholderText('ovgu@boun.edu.tr'), 'mentor@example.com');
    fireEvent.press(getByText('Sign In'));
    expect(apiClient.post).not.toHaveBeenCalled();
  });

  it('submits credentials, clears stale session data, stores session data, and redirects on success', async () => {
    (apiClient.post as jest.Mock).mockResolvedValue({
      data: {
        sessionToken: 'session-token',
        role: 'MENTOR',
        userId: 42,
      },
    });

    const { getByPlaceholderText, getByText } = render(<LoginScreen />);

    fireEvent.changeText(getByPlaceholderText('ovgu@boun.edu.tr'), 'mentor@example.com');
    fireEvent.changeText(getByPlaceholderText('••••••••'), 'secret123');
    fireEvent.press(getByText('Sign In'));

    await waitFor(() => {
      expect(apiClient.post).toHaveBeenCalledWith('/auth/login', {
        email: 'mentor@example.com',
        password: 'secret123',
      });
    });

    expect(SecureStore.deleteItemAsync).toHaveBeenCalledWith('userToken');
    expect(SecureStore.deleteItemAsync).toHaveBeenCalledWith('userId');
    expect(SecureStore.deleteItemAsync).toHaveBeenCalledWith('userRole');
    expect(SecureStore.setItemAsync).toHaveBeenCalledWith('userId', '42');
    expect(SecureStore.setItemAsync).toHaveBeenCalledWith('userRole', 'mentor');
    expect(SecureStore.setItemAsync).toHaveBeenCalledWith('userToken', 'session-token');
    expect(mockSetRole).toHaveBeenCalledWith('mentor');
    expect(router.replace).toHaveBeenCalledWith('/(tabs)');
  });

  it('shows an alert when login fails', async () => {
    const alertSpy = jest.spyOn(Alert, 'alert').mockImplementation(() => undefined);
    const consoleErrorSpy = jest.spyOn(console, 'error').mockImplementation(() => undefined);
    (apiClient.post as jest.Mock).mockRejectedValue(new Error('Invalid credentials'));

    const { getByPlaceholderText, getByText } = render(<LoginScreen />);

    fireEvent.changeText(getByPlaceholderText('ovgu@boun.edu.tr'), 'mentor@example.com');
    fireEvent.changeText(getByPlaceholderText('••••••••'), 'wrong-pass');
    fireEvent.press(getByText('Sign In'));

    await waitFor(() => {
      expect(alertSpy).toHaveBeenCalledWith('Login Failed', 'Invalid email or password.');
    });

    consoleErrorSpy.mockRestore();
  });

  it('cleans up partial session state when secure storage write fails', async () => {
    const alertSpy = jest.spyOn(Alert, 'alert').mockImplementation(() => undefined);
    const consoleErrorSpy = jest.spyOn(console, 'error').mockImplementation(() => undefined);

    (apiClient.post as jest.Mock).mockResolvedValue({
      data: {
        sessionToken: 'session-token',
        role: 'MENTOR',
        userId: 42,
      },
    });

    (SecureStore.setItemAsync as jest.Mock).mockImplementation(async (key: string) => {
      if (key === 'userRole') {
        throw new Error('Disk write failed');
      }
      return undefined;
    });

    const { getByPlaceholderText, getByText } = render(<LoginScreen />);

    fireEvent.changeText(getByPlaceholderText('ovgu@boun.edu.tr'), 'mentor@example.com');
    fireEvent.changeText(getByPlaceholderText('••••••••'), 'secret123');
    fireEvent.press(getByText('Sign In'));

    await waitFor(() => {
      expect(alertSpy).toHaveBeenCalledWith('Login Failed', 'Invalid email or password.');
    });

    expect(SecureStore.deleteItemAsync).toHaveBeenCalledWith('userToken');
    expect(SecureStore.deleteItemAsync).toHaveBeenCalledWith('userId');
    expect(SecureStore.deleteItemAsync).toHaveBeenCalledWith('userRole');
    expect(router.replace).not.toHaveBeenCalled();

    consoleErrorSpy.mockRestore();
  });
});
