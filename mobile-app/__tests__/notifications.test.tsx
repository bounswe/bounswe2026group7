import React from 'react';
import { fireEvent, render, waitFor } from '@testing-library/react-native';
import { router } from 'expo-router';

import NotificationsScreen from '../app/notifications';
import apiClient from '../api/client';

jest.mock('../api/client', () => ({
  __esModule: true,
  default: {
    get: jest.fn(),
    patch: jest.fn(),
  },
}));

jest.mock('expo-router', () => ({
  router: {
    push: jest.fn(),
    replace: jest.fn(),
    back: jest.fn(),
  },
}));

const notificationsFixture = [
  {
    id: 1,
    type: 'NEW_MESSAGE',
    title: 'New message',
    body: 'You received a new chat message.',
    isRead: false,
    createdAt: '2026-05-09T10:00:00Z',
  },
  {
    id: 2,
    type: 'MATCH_FOUND',
    title: 'Match found',
    body: 'A mentorship connection is ready.',
    isRead: true,
    createdAt: '2026-05-08T09:00:00Z',
  },
];

describe('NotificationsScreen', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('loads notifications and marks all unread items as read on first open', async () => {
    (apiClient.get as jest.Mock).mockResolvedValue({ data: notificationsFixture });
    (apiClient.patch as jest.Mock).mockResolvedValue({});

    const { findByText } = render(<NotificationsScreen />);

    expect(await findByText('New message')).toBeTruthy();

    await waitFor(() => {
      expect(apiClient.get).toHaveBeenCalledWith('/notifications');
    });

    await waitFor(() => {
      expect(apiClient.patch).toHaveBeenCalledWith('/notifications/read-all');
    });
  });

  it('routes to messages when a message notification is opened', async () => {
    (apiClient.get as jest.Mock).mockResolvedValue({
      data: [
        {
          ...notificationsFixture[0],
          isRead: true,
        },
      ],
    });
    (apiClient.patch as jest.Mock).mockResolvedValue({});

    const { findByText } = render(<NotificationsScreen />);

    const card = await findByText('New message');
    fireEvent.press(card);

    expect(router.push).toHaveBeenCalledWith('/(tabs)/messages');
  });

  it('shows the empty state when there are no notifications', async () => {
    (apiClient.get as jest.Mock).mockResolvedValue({ data: [] });

    const { findByText } = render(<NotificationsScreen />);

    expect(await findByText('No notifications yet.')).toBeTruthy();
  });
});
