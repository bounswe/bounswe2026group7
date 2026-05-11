import React from 'react';
import { fireEvent, render, waitFor } from '@testing-library/react-native';
import { router } from 'expo-router';

import ConnectionProfileScreen from '../app/connection-profile';
import apiClient from '../api/client';

const mockUseLocalSearchParams = jest.fn();
let mockRole: 'mentor' | 'mentee' = 'mentee';

jest.mock('../api/client', () => ({
  __esModule: true,
  default: {
    get: jest.fn(),
    put: jest.fn(),
    post: jest.fn(),
    patch: jest.fn(),
  },
}));

jest.mock('expo-router', () => ({
  router: {
    push: jest.fn(),
    navigate: jest.fn(),
    back: jest.fn(),
  },
  useLocalSearchParams: () => mockUseLocalSearchParams(),
}));

jest.mock('../components/RoleContext', () => ({
  useRole: () => ({
    role: mockRole,
  }),
}));

describe('ConnectionProfileScreen', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockRole = 'mentee';
    mockUseLocalSearchParams.mockReturnValue({
      id: '11',
      mentorshipId: '8',
      type: 'mentor',
      name: 'Aylin Mentor',
      initials: 'AM',
      subtitle: 'Career growth',
      about: '',
      department: '',
      title: '',
      interests: '[]',
      goals: '[]',
      mentoringGoals: '[]',
      preferences: '[]',
      stat1Label: 'Progress',
      stat1Value: '20%',
      stat2Label: 'Duration',
      stat2Value: '3mo',
      stat3Label: 'Status',
      stat3Value: 'Active',
    });
    (apiClient.put as jest.Mock).mockResolvedValue({});
    (apiClient.post as jest.Mock).mockResolvedValue({});
    (apiClient.patch as jest.Mock).mockResolvedValue({});
  });

  it('loads the shared goal and saves edits', async () => {
    (apiClient.get as jest.Mock).mockImplementation(async (url: string) => {
      if (url === '/users/11') {
        return {
          data: {
            bio: 'Experienced mentor',
            field: 'Software Engineering',
            interests: ['Backend'],
          },
        };
      }
      if (url === '/mentorships/8') {
        return {
          data: {
            sharedGoal: 'Land a summer internship',
          },
        };
      }
      if (url === '/mentorships/8/milestones') {
        return { data: [] };
      }
      throw new Error(`Unexpected GET ${url}`);
    });

    const { findByText, getByText, getByPlaceholderText } = render(<ConnectionProfileScreen />);

    expect(await findByText('Land a summer internship')).toBeTruthy();
    expect(await findByText('No milestones added yet.')).toBeTruthy();

    fireEvent.press(getByText('Edit Goal'));
    fireEvent.changeText(
      getByPlaceholderText('Describe your shared mentorship goal...'),
      'Prepare a strong portfolio'
    );
    fireEvent.press(getByText('Save Goal'));

    await waitFor(() => {
      expect(apiClient.put).toHaveBeenCalledWith('/mentorships/8/goal', {
        sharedGoal: 'Prepare a strong portfolio',
      });
    });
  });

  it('renders mentor milestones and toggles an action item', async () => {
    mockRole = 'mentor';
    mockUseLocalSearchParams.mockReturnValue({
      id: '21',
      mentorshipId: '9',
      type: 'mentor',
      name: 'Peer Mentor',
      initials: 'PM',
      subtitle: 'Shared systems goal',
      about: '',
      department: '',
      title: '',
      interests: '[]',
      goals: '[]',
      mentoringGoals: '[]',
      preferences: '[]',
      stat1Label: 'Progress',
      stat1Value: '50%',
      stat2Label: 'Duration',
      stat2Value: '6mo',
      stat3Label: 'Status',
      stat3Value: 'Active',
    });

    (apiClient.get as jest.Mock).mockImplementation(async (url: string) => {
      if (url === '/users/21') {
        return {
          data: {
            bio: 'Distributed systems mentor',
            field: 'Systems',
            interests: ['Distributed Systems'],
          },
        };
      }
      if (url === '/mentorships/9') {
        return {
          data: {
            sharedGoal: 'Design a stronger backend architecture',
          },
        };
      }
      if (url === '/mentorships/9/milestones') {
        return {
          data: [
            {
              id: 101,
              title: 'Architecture Review',
              targetDate: '2026-06-10T00:00:00.000Z',
              status: 'IN_PROGRESS',
              orderIndex: 0,
            },
          ],
        };
      }
      if (url === '/milestones/101') {
        return {
          data: {
            id: 101,
            mentorshipId: 9,
            title: 'Architecture Review',
            description: 'Review the current service boundaries.',
            targetDate: '2026-06-10T00:00:00.000Z',
            status: 'IN_PROGRESS',
            orderIndex: 0,
            completedAt: null,
            createdAt: '2026-05-10T10:00:00.000Z',
            actionItems: [
              {
                id: 401,
                text: 'Draft a service map',
                isCompleted: false,
                orderIndex: 0,
                completedAt: null,
                completedById: null,
                createdById: 21,
                createdAt: '2026-05-10T10:00:00.000Z',
              },
            ],
          },
        };
      }
      if (url === '/availability/21') {
        return {
          data: [
            {
              dayOfWeek: 'MONDAY',
              startTime: '09:00:00',
              endTime: '11:00:00',
            },
          ],
        };
      }
      throw new Error(`Unexpected GET ${url}`);
    });

    const { findAllByText, findByText, getByText } = render(<ConnectionProfileScreen />);

    expect((await findAllByText('Architecture Review')).length).toBeGreaterThan(0);
    expect((await findAllByText('In Progress')).length).toBeGreaterThan(0);
    expect(await findByText('Draft a service map')).toBeTruthy();
    expect(await findByText('09:00 – 11:00')).toBeTruthy();

    fireEvent.press(getByText('Draft a service map'));

    await waitFor(() => {
      expect(apiClient.patch).toHaveBeenCalledWith('/milestone-action-items/401', {
        completed: true,
      });
    });

    fireEvent.press(getByText('📅 Meetings'));
    expect(router.push).toHaveBeenCalledWith({
      pathname: '/meetings-sessions',
      params: { connectedUserName: 'Peer Mentor', connectedUserType: 'mentor', mentorshipId: '9' },
    });
  });
});
