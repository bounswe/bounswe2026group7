import React from 'react';
import { Alert } from 'react-native';
import { fireEvent, render, waitFor } from '@testing-library/react-native';
import { router } from 'expo-router';

import ProfileScreen from '../app/(tabs)/profile';
import apiClient from '../api/client';

const mockGetItemAsync = jest.fn();
const mockDeleteItemAsync = jest.fn();
const mockSetItemAsync = jest.fn();
const mockClearRole = jest.fn();
const mockUnregisterStoredPushToken = jest.fn();
let mockRole: 'mentor' | 'mentee' = 'mentee';

jest.mock('../api/client', () => ({
  __esModule: true,
  default: {
    get: jest.fn(),
    patch: jest.fn(),
    post: jest.fn(),
    delete: jest.fn(),
  },
}));

jest.mock('expo-router', () => ({
  router: {
    push: jest.fn(),
    replace: jest.fn(),
    navigate: jest.fn(),
    back: jest.fn(),
  },
}));

jest.mock('expo-secure-store', () => ({
  getItemAsync: (...args: unknown[]) => mockGetItemAsync(...args),
  deleteItemAsync: (...args: unknown[]) => mockDeleteItemAsync(...args),
  setItemAsync: (...args: unknown[]) => mockSetItemAsync(...args),
}));

jest.mock('../components/RoleContext', () => ({
  useRole: () => ({
    role: mockRole,
    clearRole: mockClearRole,
  }),
}));

jest.mock('../lib/pushNotifications', () => ({
  getPushPermissionState: jest.fn(),
  getStoredPushToken: jest.fn(),
  registerPushToken: jest.fn(),
  unregisterStoredPushToken: (...args: unknown[]) => mockUnregisterStoredPushToken(...args),
}));

jest.mock('expo-image-picker', () => ({
  requestMediaLibraryPermissionsAsync: jest.fn(),
  launchImageLibraryAsync: jest.fn(),
}));

describe('ProfileScreen', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockRole = 'mentee';
    mockGetItemAsync.mockImplementation(async (key: string) => {
      if (key === 'userId') return '42';
      if (key === 'userRole') return mockRole;
      return null;
    });
    mockDeleteItemAsync.mockResolvedValue(undefined);
    mockSetItemAsync.mockResolvedValue(undefined);
    mockUnregisterStoredPushToken.mockResolvedValue(undefined);
  });

  it('renders mentee profile content and loads sent requests', async () => {
    (apiClient.get as jest.Mock).mockImplementation(async (url: string) => {
      if (url === '/users/me') {
        return {
          data: {
            firstName: 'Ovgu',
            lastName: 'Afsar',
            major: 'Computer Engineering',
            backgroundInfo: 'Curious builder',
            goals: 'Find a mentor',
            careerInterest: 'AI',
            meetingFreqPref: 'Weekly',
            profileVisibility: true,
            interests: ['AI'],
            skills: ['Python'],
          },
        };
      }

      if (url === '/mentorship-requests/sent') {
        return {
          data: {
            content: [
              {
                id: 1,
                mentorFirstName: 'Aylin',
                message: 'Would love to connect',
                status: 'PENDING',
                createdAt: '2026-05-10T10:00:00Z',
              },
            ],
          },
        };
      }

      throw new Error(`Unexpected GET ${url}`);
    });

    const { findByDisplayValue, findByText } = render(<ProfileScreen />);

    expect(await findByDisplayValue('Ovgu Afsar')).toBeTruthy();
    expect(await findByDisplayValue('Computer Engineering')).toBeTruthy();
    expect(await findByText('MY REQUESTS')).toBeTruthy();
    expect(await findByText('Aylin')).toBeTruthy();
  });

  it('renders mentor profile content with max mentee capacity', async () => {
    mockRole = 'mentor';

    (apiClient.get as jest.Mock).mockImplementation(async (url: string) => {
      if (url === '/users/me') {
        return {
          data: {
            firstName: 'Burak',
            lastName: 'Ogut',
            field: 'Software Engineering',
            bio: 'Mentor bio',
            expertise: 'Backend',
            affiliation: 'Bogazici',
            mentoringGoals: 'Help mentees grow',
            preferredMenteeMajor: 'CENG',
            preferredMenteeSkills: ['Java'],
            interests: ['Systems'],
            maxMenteeCapacity: 4,
            mentorshipDuration: 6,
          },
        };
      }

      throw new Error(`Unexpected GET ${url}`);
    });

    const { findByDisplayValue, findByText } = render(<ProfileScreen />);

    expect(await findByDisplayValue('Burak Ogut')).toBeTruthy();
    expect(await findByDisplayValue('4')).toBeTruthy();
    expect(await findByDisplayValue('6')).toBeTruthy();
    expect(await findByText('Save Changes')).toBeTruthy();
  });

  it('clears session state and routes to login on logout', async () => {
    (apiClient.get as jest.Mock).mockImplementation(async (url: string) => {
      if (url === '/users/me') {
        return {
          data: {
            firstName: 'Ovgu',
            lastName: 'Afsar',
            major: 'Computer Engineering',
            interests: [],
            skills: [],
          },
        };
      }

      if (url === '/mentorship-requests/sent') {
        return { data: { content: [] } };
      }

      throw new Error(`Unexpected GET ${url}`);
    });

    const { findByText } = render(<ProfileScreen />);

    const logoutButton = await findByText('Log Out');
    fireEvent.press(logoutButton);

    await waitFor(() => {
      expect(mockUnregisterStoredPushToken).toHaveBeenCalled();
    });

    expect(mockDeleteItemAsync).toHaveBeenCalledWith('userToken');
    expect(mockDeleteItemAsync).toHaveBeenCalledWith('userId');
    expect(mockDeleteItemAsync).toHaveBeenCalledWith('userRole');
    expect(mockClearRole).toHaveBeenCalled();
    expect(router.replace).toHaveBeenCalledWith('/login');
  });

  it('shows an alert if logout fails', async () => {
    const alertSpy = jest.spyOn(Alert, 'alert').mockImplementation(() => undefined);
    let logoutReads = 0;
    mockGetItemAsync.mockImplementation(async (key: string) => {
      if (key === 'userId' || key === 'userRole') {
        logoutReads += 1;
        if (logoutReads >= 3) {
          throw new Error('SecureStore failure');
        }
      }

      if (key === 'userId') return '42';
      if (key === 'userRole') return mockRole;
      return null;
    });

    (apiClient.get as jest.Mock).mockImplementation(async (url: string) => {
      if (url === '/users/me') {
        return {
          data: {
            firstName: 'Ovgu',
            lastName: 'Afsar',
            major: 'Computer Engineering',
            interests: [],
            skills: [],
          },
        };
      }

      if (url === '/mentorship-requests/sent') {
        return { data: { content: [] } };
      }

      throw new Error(`Unexpected GET ${url}`);
    });

    const { findByText } = render(<ProfileScreen />);

    fireEvent.press(await findByText('Log Out'));

    await waitFor(() => {
      expect(alertSpy).toHaveBeenCalledWith('Error', 'An error occurred while logging out.');
    });
  });
});
