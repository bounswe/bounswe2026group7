import React from 'react';
import { fireEvent, render, waitFor } from '@testing-library/react-native';

import MessagesScreen from '../app/(tabs)/messages';
import apiClient from '../api/client';

const mockUseLocalSearchParams = jest.fn();
const mockGetItemAsync = jest.fn();
let mockCurrentRole: 'mentor' | 'mentee' = 'mentee';

jest.mock('../api/client', () => ({
  __esModule: true,
  default: {
    get: jest.fn(),
    post: jest.fn(),
    patch: jest.fn(),
  },
}));

jest.mock('expo-router', () => ({
  useLocalSearchParams: () => mockUseLocalSearchParams(),
}));

jest.mock('expo-secure-store', () => ({
  getItemAsync: (...args: unknown[]) => mockGetItemAsync(...args),
}));

jest.mock('../components/RoleContext', () => ({
  useRole: () => ({
    role: mockCurrentRole,
  }),
}));

jest.mock('expo-document-picker', () => ({
  getDocumentAsync: jest.fn(),
}));

jest.mock('expo-image-picker', () => ({
  requestMediaLibraryPermissionsAsync: jest.fn(),
  launchImageLibraryAsync: jest.fn(),
}));

jest.mock('expo-file-system/legacy', () => ({
  cacheDirectory: 'file:///tmp/',
  downloadAsync: jest.fn(),
}));

jest.mock('expo-sharing', () => ({
  isAvailableAsync: jest.fn(),
  shareAsync: jest.fn(),
}));

describe('MessagesScreen', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockCurrentRole = 'mentee';
    mockUseLocalSearchParams.mockReturnValue({});
    mockGetItemAsync.mockImplementation(async (key: string) => {
      if (key === 'userId') return '42';
      if (key === 'userToken') return 'token-123';
      return null;
    });
    (apiClient.patch as jest.Mock).mockResolvedValue({});
    (apiClient.post as jest.Mock).mockResolvedValue({});
  });

  it('loads mentorship conversations for mentees and opens a thread', async () => {
    (apiClient.get as jest.Mock).mockImplementation(async (url: string) => {
      if (url === '/mentorships') {
        return {
          data: [
            {
              id: 1,
              mentorId: 9,
              mentorFirstName: 'Aylin Mentor',
              menteeId: 42,
              menteeFirstName: 'Mentee Me',
            },
          ],
        };
      }

      if (url === '/mentorships/1/messages?page=0&size=100') {
        return {
          data: {
            content: [
              {
                id: 101,
                senderId: 9,
                content: 'Hello there',
                sentAt: '2026-05-10T10:00:00Z',
                readAt: null,
              },
            ],
          },
        };
      }

      throw new Error(`Unexpected GET ${url}`);
    });

    const { findByText, getAllByText } = render(<MessagesScreen />);

    expect(await findByText('Aylin Mentor')).toBeTruthy();
    expect(await findByText('Hello there')).toBeTruthy();

    fireEvent.press(getAllByText('Aylin Mentor')[0]);

    expect(await findByText('Mentorship Chat')).toBeTruthy();
    expect(await findByText('Real messages and attachment support')).toBeTruthy();

    await waitFor(() => {
      expect(apiClient.patch).toHaveBeenCalledWith('/mentorships/1/messages/read');
    });
  });

  it('shows the empty mentorship state when there are no conversations', async () => {
    (apiClient.get as jest.Mock).mockImplementation(async (url: string) => {
      if (url === '/mentorships') {
        return { data: [] };
      }

      throw new Error(`Unexpected GET ${url}`);
    });

    const { findByText } = render(<MessagesScreen />);

    expect(await findByText('No conversations found')).toBeTruthy();
    expect(
      await findByText('Once you have an active mentorship, your chat threads will appear here.')
    ).toBeTruthy();
  });

  it('shows mentor peer inbox and directory options on the Mentor Network tab', async () => {
    mockCurrentRole = 'mentor';

    (apiClient.get as jest.Mock).mockImplementation(async (url: string) => {
      if (url === '/mentorships') {
        return {
          data: [
            {
              id: 1,
              mentorId: 42,
              mentorFirstName: 'Current Mentor',
              menteeId: 71,
              menteeFirstName: 'Mentee One',
            },
          ],
        };
      }

      if (url === '/mentorships/1/messages?page=0&size=100') {
        return {
          data: {
            content: [],
          },
        };
      }

      if (url === '/conversations/mentor-pair?page=0&size=100') {
        return {
          data: {
            content: [
              {
                peerId: 50,
                peerFirstName: 'Peer Existing',
                lastMessageContent: 'Thanks for the tip',
                lastMessageSentAt: '2026-05-10T09:00:00Z',
                unreadCount: 2,
              },
            ],
          },
        };
      }

      if (url === '/users/mentors/all') {
        return {
          data: [
            { id: 42, firstName: 'Current', lastName: 'Mentor', field: 'CS' },
            { id: 50, firstName: 'Peer', lastName: 'Existing', field: 'AI' },
            { id: 51, firstName: 'Fresh', lastName: 'Mentor', field: 'ML' },
          ],
        };
      }

      throw new Error(`Unexpected GET ${url}`);
    });

    const { findByText, getByText } = render(<MessagesScreen />);

    expect(await findByText('Active Mentees')).toBeTruthy();

    fireEvent.press(getByText('Mentor Network'));

    expect(await findByText('Peer Existing')).toBeTruthy();
    expect(await findByText('Start New Conversation')).toBeTruthy();
    expect(await findByText('Fresh Mentor')).toBeTruthy();
  });
});
