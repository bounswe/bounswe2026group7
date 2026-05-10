import React from 'react';
import { fireEvent, render, waitFor } from '@testing-library/react-native';
import { router, useLocalSearchParams } from 'expo-router';

import ResetPasswordScreen from '../app/reset-password';
import apiClient from '../api/client';

jest.mock('../api/client', () => ({
  __esModule: true,
  default: {
    get: jest.fn(),
    post: jest.fn(),
  },
}));

jest.mock('expo-router', () => ({
  router: {
    push: jest.fn(),
    replace: jest.fn(),
    back: jest.fn(),
  },
  useLocalSearchParams: jest.fn(),
}));

describe('ResetPasswordScreen', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    (useLocalSearchParams as jest.Mock).mockReturnValue({ token: 'valid-token' });
  });

  it('validates the incoming token on mount and resets the password successfully', async () => {
    (apiClient.get as jest.Mock).mockResolvedValue({});
    (apiClient.post as jest.Mock).mockResolvedValue({
      data: {
        message: 'Password reset successfully.',
      },
    });

    const { getByPlaceholderText, getByText, findByText } = render(<ResetPasswordScreen />);

    await waitFor(() => {
      expect(apiClient.get).toHaveBeenCalledWith('/auth/validate-reset-token?token=valid-token');
    });

    fireEvent.changeText(getByPlaceholderText('••••••••'), 'StrongPass1');
    fireEvent.press(getByText('Reset Password'));

    await waitFor(() => {
      expect(apiClient.post).toHaveBeenCalledWith('/auth/reset-password', {
        token: 'valid-token',
        newPassword: 'StrongPass1',
      });
    });

    expect(await findByText('Password reset successfully.')).toBeTruthy();
    fireEvent.press(getByText('Go to Sign In'));
    expect(router.replace).toHaveBeenCalledWith('/login');
  });

  it('shows the invalid-link state and can route to request a new link', async () => {
    (apiClient.get as jest.Mock).mockRejectedValue({
      response: {
        data: {
          message: 'Token expired.',
        },
      },
    });

    const { findByText, getByText } = render(<ResetPasswordScreen />);

    expect(await findByText('Token expired.')).toBeTruthy();
    fireEvent.press(getByText('Request a New Link'));
    expect(router.replace).toHaveBeenCalledWith('/forgot-password');
  });

  it('keeps the form disabled for weak passwords', async () => {
    (apiClient.get as jest.Mock).mockResolvedValue({});

    const { getByPlaceholderText, getByText } = render(<ResetPasswordScreen />);

    await waitFor(() => {
      expect(apiClient.get).toHaveBeenCalled();
    });

    fireEvent.changeText(getByPlaceholderText('••••••••'), 'weak');
    fireEvent.press(getByText('Reset Password'));

    expect(apiClient.post).not.toHaveBeenCalled();
  });
});
