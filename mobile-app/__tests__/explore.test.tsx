import React from 'react';
import { fireEvent, render, waitFor } from '@testing-library/react-native';

import ExploreScreen from '../app/(tabs)/explore';
import apiClient from '../api/client';

const mockPush = jest.fn();
const mockGetItemAsync = jest.fn();

jest.mock('../api/client', () => ({
  __esModule: true,
  default: {
    get: jest.fn(),
    post: jest.fn(),
    delete: jest.fn(),
  },
}));

jest.mock('expo-router', () => ({
  router: {
    push: (...args: unknown[]) => mockPush(...args),
  },
}));

jest.mock('expo-secure-store', () => ({
  getItemAsync: (...args: unknown[]) => mockGetItemAsync(...args),
}));

jest.mock('../components/RoleContext', () => ({
  useRole: () => ({
    role: 'mentee',
  }),
}));

describe('ExploreScreen AI matches', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockGetItemAsync.mockImplementation(async (key: string) => {
      if (key === 'userId') return '42';
      return null;
    });
    (apiClient.post as jest.Mock).mockResolvedValue({});
    (apiClient.delete as jest.Mock).mockResolvedValue({});
  });

  it('shows AI matches as a separate section without replacing the full mentor list', async () => {
    (apiClient.get as jest.Mock).mockImplementation(async (url: string) => {
      if (url === '/users/mentors/all') {
        return {
          data: {
            content: [
              {
                id: 10,
                firstName: 'Aylin',
                lastName: 'Mentor',
                field: 'Computer Science',
                interests: ['AI', 'Career'],
                bio: 'Guides students in AI.',
                currentMenteeCount: 1,
                maxMenteeCapacity: 3,
              },
              {
                id: 11,
                firstName: 'Burak',
                lastName: 'Coach',
                field: 'Data Science',
                interests: ['ML'],
                bio: 'Data mentor.',
                currentMenteeCount: 0,
                maxMenteeCapacity: 2,
              },
            ],
          },
        };
      }

      if (url === '/users/42/following?size=100') {
        return {
          data: {
            content: [],
          },
        };
      }

      if (url === '/matching/mentors?size=5') {
        return {
          data: [
            {
              id: 10,
              firstName: 'Aylin',
              lastName: 'Mentor',
              field: 'Computer Science',
              interests: ['AI', 'Career'],
              bio: 'Guides students in AI.',
              currentMenteeCount: 1,
              maxMenteeCapacity: 3,
              matchScore: 92,
              factors: [
                'major-exact',
                'shared-interest:AI',
                'availability:2h',
                'city-match',
                'semantic-unavailable',
                'diverse-pick',
              ],
              explanation: 'Strong fit for your AI mentoring goals.',
              distanceKm: 2.4,
            },
          ],
        };
      }

      throw new Error(`Unexpected GET ${url}`);
    });

    const { findByText, getByText, queryByText } = render(<ExploreScreen />);

    expect(await findByText('Aylin Mentor')).toBeTruthy();
    expect(await findByText('Burak Coach')).toBeTruthy();
    expect(queryByText('Top AI Matches')).toBeNull();

    fireEvent.press(getByText('✦  Find Best Matches'));

    expect(await findByText('Top AI Matches')).toBeTruthy();
    expect(await findByText('All Mentors')).toBeTruthy();
    expect(await findByText('Diverse pick')).toBeTruthy();
    expect(await findByText('AI signal unavailable')).toBeTruthy();
    expect(await findByText('Strong fit for your AI mentoring goals.')).toBeTruthy();
    expect(await findByText('📍 2 km away')).toBeTruthy();
    expect(await findByText('Same major')).toBeTruthy();
    expect(await findByText('2h overlap')).toBeTruthy();

    await waitFor(() => {
      expect(apiClient.get).toHaveBeenCalledWith('/matching/mentors?size=5');
    });

    expect(getByText('Burak Coach')).toBeTruthy();
  });
});
