import React from 'react';
import { fireEvent, render, waitFor } from '@testing-library/react-native';
import { router } from 'expo-router';

import ForgotPasswordScreen from '../app/forgot-password';
import apiClient from '../api/client';

jest.mock('../api/client', () => ({
  __esModule: true,
  default: {
    post: jest.fn(),
  },
}));

jest.mock('expo-router', () => ({
  router: {
    push: jest.fn(),
    replace: jest.fn(),
    back: jest.fn(),
  },
}));

describe('ForgotPasswordScreen', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('does not submit for invalid email input', () => {
    const { getByText, getByPlaceholderText } = render(<ForgotPasswordScreen />);

    fireEvent.press(getByText('Send Reset Link'));
    expect(apiClient.post).not.toHaveBeenCalled();

    fireEvent.changeText(getByPlaceholderText('you@example.com'), 'not-an-email');
    fireEvent.press(getByText('Send Reset Link'));
    expect(apiClient.post).not.toHaveBeenCalled();
  });

  it('shows the backend success message after requesting a reset link', async () => {
    (apiClient.post as jest.Mock).mockResolvedValue({
      data: {
        message: 'Reset email sent.',
      },
    });

    const { getByPlaceholderText, getByText, findByText } = render(<ForgotPasswordScreen />);

    fireEvent.changeText(getByPlaceholderText('you@example.com'), 'mentee@example.com');
    fireEvent.press(getByText('Send Reset Link'));

    await waitFor(() => {
      expect(apiClient.post).toHaveBeenCalledWith('/auth/forgot-password', {
        email: 'mentee@example.com',
      });
    });

    expect(await findByText('Reset email sent.')).toBeTruthy();
    expect(getByText('I Have a Reset Token')).toBeTruthy();
  });

  it('navigates to reset-password after success when the CTA is pressed', async () => {
    (apiClient.post as jest.Mock).mockResolvedValue({
      data: {
        message: 'Reset email sent.',
      },
    });

    const { getByPlaceholderText, getByText, findByText } = render(<ForgotPasswordScreen />);

    fireEvent.changeText(getByPlaceholderText('you@example.com'), 'mentee@example.com');
    fireEvent.press(getByText('Send Reset Link'));

    const resetButton = await findByText('I Have a Reset Token');
    fireEvent.press(resetButton);

    expect(router.push).toHaveBeenCalledWith('/reset-password');
  });

  it('shows the backend error message when the request fails', async () => {
    (apiClient.post as jest.Mock).mockRejectedValue({
      response: {
        data: {
          message: 'Unable to process reset request.',
        },
      },
    });

    const { getByPlaceholderText, getByText, findByText } = render(<ForgotPasswordScreen />);

    fireEvent.changeText(getByPlaceholderText('you@example.com'), 'mentee@example.com');
    fireEvent.press(getByText('Send Reset Link'));

    expect(await findByText('Unable to process reset request.')).toBeTruthy();
  });
});
