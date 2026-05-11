import React from 'react';
import { fireEvent, render, waitFor } from '@testing-library/react-native';
import { router } from 'expo-router';

import SocialFeedScreen from '../app/social-feed';
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
    back: jest.fn(),
  },
}));

const forYouFixture = [
  {
    id: 1,
    authorId: 11,
    authorFirstName: 'Aylin',
    body: 'Building a better mentoring habit.',
    hashtags: ['mentoring', 'growth'],
    createdAt: '2026-05-10T10:00:00Z',
    likeCount: 5,
    commentCount: 2,
  },
];

const followingFixture = [
  {
    id: 2,
    authorId: 12,
    authorFirstName: 'Burak',
    body: 'Following tab post.',
    hashtags: ['community'],
    createdAt: '2026-05-10T09:00:00Z',
    likeCount: 3,
    commentCount: 1,
  },
];

describe('SocialFeedScreen', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    (apiClient.post as jest.Mock).mockResolvedValue({});
  });

  it('loads the feed, shows unread count, and switches tabs', async () => {
    (apiClient.get as jest.Mock).mockImplementation(async (url: string) => {
      if (url === '/feed/for-you?page=0&size=20') {
        return { data: { content: forYouFixture } };
      }
      if (url === '/feed/following?page=0&size=20') {
        return { data: { content: followingFixture } };
      }
      if (url === '/feed/unread-count') {
        return { data: { count: 7, cappedAtMax: false } };
      }
      throw new Error(`Unexpected GET ${url}`);
    });

    const { findByText, getByText, queryByText } = render(<SocialFeedScreen />);

    expect(await findByText('Aylin')).toBeTruthy();
    expect(await findByText('Building a better mentoring habit.')).toBeTruthy();
    expect(await findByText('#mentoring')).toBeTruthy();
    expect(await findByText('7')).toBeTruthy();

    fireEvent.press(getByText('Following'));

    expect(await findByText('Burak')).toBeTruthy();
    expect(await findByText('Following tab post.')).toBeTruthy();
    expect(queryByText('Building a better mentoring habit.')).toBeNull();
  });

  it('marks the feed as read and refreshes the unread count', async () => {
    let unreadCalls = 0;

    (apiClient.get as jest.Mock).mockImplementation(async (url: string) => {
      if (url === '/feed/for-you?page=0&size=20') {
        return { data: { content: forYouFixture } };
      }
      if (url === '/feed/following?page=0&size=20') {
        return { data: { content: [] } };
      }
      if (url === '/feed/unread-count') {
        unreadCalls += 1;
        return {
          data: unreadCalls > 1 ? { count: 0, cappedAtMax: false } : { count: 4, cappedAtMax: false },
        };
      }
      throw new Error(`Unexpected GET ${url}`);
    });

    const { findByText, getByText } = render(<SocialFeedScreen />);

    expect(await findByText('Mark Feed Read')).toBeTruthy();
    fireEvent.press(getByText('Mark Feed Read'));

    await waitFor(() => {
      expect(apiClient.post).toHaveBeenCalledWith('/feed/mark-read');
    });

    await waitFor(() => {
      expect(apiClient.get).toHaveBeenCalledWith('/feed/unread-count');
    });

    expect(await findByText('0')).toBeTruthy();
  });

  it('shows the empty state when a tab has no posts', async () => {
    (apiClient.get as jest.Mock).mockImplementation(async (url: string) => {
      if (url === '/feed/for-you?page=0&size=20') {
        return { data: { content: [] } };
      }
      if (url === '/feed/following?page=0&size=20') {
        return { data: { content: [] } };
      }
      if (url === '/feed/unread-count') {
        return { data: { count: 0, cappedAtMax: false } };
      }
      throw new Error(`Unexpected GET ${url}`);
    });

    const { findByText, getByText } = render(<SocialFeedScreen />);

    expect(await findByText('No recommendations yet')).toBeTruthy();
    fireEvent.press(getByText('Following'));
    expect(await findByText('No followed posts yet')).toBeTruthy();
  });

  it('shows the error state when feed loading fails', async () => {
    (apiClient.get as jest.Mock).mockRejectedValue(new Error('Feed unavailable'));

    const { findByText } = render(<SocialFeedScreen />);

    expect(await findByText('Feed unavailable')).toBeTruthy();
    expect(await findByText('Could not load the social feed right now.')).toBeTruthy();
  });

  it('returns to the previous screen when back is pressed', async () => {
    (apiClient.get as jest.Mock).mockImplementation(async (url: string) => {
      if (url === '/feed/for-you?page=0&size=20') {
        return { data: { content: forYouFixture } };
      }
      if (url === '/feed/following?page=0&size=20') {
        return { data: { content: [] } };
      }
      if (url === '/feed/unread-count') {
        return { data: { count: 0, cappedAtMax: false } };
      }
      throw new Error(`Unexpected GET ${url}`);
    });

    const { findByText } = render(<SocialFeedScreen />);

    fireEvent.press(await findByText('‹'));

    expect(router.back).toHaveBeenCalled();
  });
});
