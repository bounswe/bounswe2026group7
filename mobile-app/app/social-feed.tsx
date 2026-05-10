import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { router } from 'expo-router';
import {
  ActivityIndicator,
  RefreshControl,
  ScrollView,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import apiClient from '../api/client';

type FeedTab = 'forYou' | 'following';

type FeedPostListItem = {
  id: number;
  authorId: number;
  authorFirstName: string;
  body: string;
  hashtags: string[];
  createdAt: string;
  likeCount: number;
  commentCount: number;
};

type FeedUnreadCountResponse = {
  count: number;
  cappedAtMax: boolean;
};

function formatRelativeLabel(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '';

  const diffMinutes = Math.max(1, Math.round((Date.now() - date.getTime()) / 60000));
  if (diffMinutes < 60) return `${diffMinutes}m ago`;
  const diffHours = Math.round(diffMinutes / 60);
  if (diffHours < 24) return `${diffHours}h ago`;
  const diffDays = Math.round(diffHours / 24);
  return `${diffDays}d ago`;
}

export default function SocialFeedScreen() {
  const [activeTab, setActiveTab] = useState<FeedTab>('forYou');
  const [forYouPosts, setForYouPosts] = useState<FeedPostListItem[]>([]);
  const [followingPosts, setFollowingPosts] = useState<FeedPostListItem[]>([]);
  const [unreadCount, setUnreadCount] = useState<FeedUnreadCountResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [markingRead, setMarkingRead] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');

  const loadUnreadCount = useCallback(async () => {
    const res = await apiClient.get('/feed/unread-count');
    setUnreadCount(res.data);
  }, []);

  const loadFeeds = useCallback(async () => {
    setErrorMessage('');
    try {
      const [forYouRes, followingRes, unreadRes] = await Promise.all([
        apiClient.get('/feed/for-you?page=0&size=20'),
        apiClient.get('/feed/following?page=0&size=20'),
        apiClient.get('/feed/unread-count'),
      ]);

      setForYouPosts(forYouRes.data.content ?? []);
      setFollowingPosts(followingRes.data.content ?? []);
      setUnreadCount(unreadRes.data);
    } catch {
      setErrorMessage('Could not load the social feed right now.');
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  useEffect(() => {
    loadFeeds();
  }, [loadFeeds]);

  const activePosts = useMemo(
    () => (activeTab === 'forYou' ? forYouPosts : followingPosts),
    [activeTab, forYouPosts, followingPosts]
  );

  const onRefresh = () => {
    setRefreshing(true);
    loadFeeds();
  };

  const markFeedRead = async () => {
    setMarkingRead(true);
    try {
      await apiClient.post('/feed/mark-read');
      await loadUnreadCount();
    } catch {
      setErrorMessage('Could not update the feed read state.');
    } finally {
      setMarkingRead(false);
    }
  };

  return (
    <View style={styles.container}>
      <ScrollView
        style={styles.scrollArea}
        contentContainerStyle={styles.content}
        showsVerticalScrollIndicator={false}
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor="#456B50" />}
      >
        <View style={styles.statusRow}>
          <Text style={styles.statusText}>
            {new Date().toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit', hour12: false })}
          </Text>
          <Text style={styles.statusIcons}>▲ ▮</Text>
        </View>

        <View style={styles.headerRow}>
          <TouchableOpacity onPress={() => router.back()} style={styles.backButton}>
            <Text style={styles.backText}>‹</Text>
          </TouchableOpacity>
          <View style={styles.headerCopy}>
            <Text style={styles.eyebrow}>SOCIAL FEED</Text>
            <Text style={styles.title}>Your network, in one place.</Text>
          </View>
        </View>

        <View style={styles.summaryCard}>
          <View style={styles.summaryHeader}>
            <View>
              <Text style={styles.summaryLabel}>Unread updates</Text>
              <Text style={styles.summaryCount}>
                {unreadCount ? (unreadCount.cappedAtMax ? '99+' : unreadCount.count) : '...'}
              </Text>
            </View>
            <TouchableOpacity
              style={[styles.markReadButton, markingRead && { opacity: 0.6 }]}
              onPress={markFeedRead}
              disabled={markingRead}
            >
              <Text style={styles.markReadButtonText}>
                {markingRead ? 'Updating...' : 'Mark Feed Read'}
              </Text>
            </TouchableOpacity>
          </View>
          <Text style={styles.summaryText}>
            Switch between `For You` and `Following` to browse the mobile feed.
          </Text>
        </View>

        <View style={styles.tabRow}>
          <TouchableOpacity
            style={[styles.tabButton, activeTab === 'forYou' && styles.tabButtonActive]}
            onPress={() => setActiveTab('forYou')}
          >
            <Text style={[styles.tabButtonText, activeTab === 'forYou' && styles.tabButtonTextActive]}>
              For You
            </Text>
          </TouchableOpacity>
          <TouchableOpacity
            style={[styles.tabButton, activeTab === 'following' && styles.tabButtonActive]}
            onPress={() => setActiveTab('following')}
          >
            <Text style={[styles.tabButtonText, activeTab === 'following' && styles.tabButtonTextActive]}>
              Following
            </Text>
          </TouchableOpacity>
        </View>

        {loading ? (
          <ActivityIndicator size="large" color="#456B50" style={styles.loader} />
        ) : errorMessage ? (
          <View style={styles.emptyCard}>
            <Text style={styles.emptyTitle}>Feed unavailable</Text>
            <Text style={styles.emptyText}>{errorMessage}</Text>
          </View>
        ) : activePosts.length === 0 ? (
          <View style={styles.emptyCard}>
            <Text style={styles.emptyTitle}>
              {activeTab === 'forYou' ? 'No recommendations yet' : 'No followed posts yet'}
            </Text>
            <Text style={styles.emptyText}>
              {activeTab === 'forYou'
                ? 'Pull to refresh after more activity is available.'
                : 'Once you follow people with posts, they will appear here.'}
            </Text>
          </View>
        ) : (
          activePosts.map((post) => (
            <View key={post.id} style={styles.postCard}>
              <View style={styles.postHeader}>
                <View style={styles.avatar}>
                  <Text style={styles.avatarText}>{post.authorFirstName.substring(0, 2).toUpperCase()}</Text>
                </View>
                <View style={styles.postHeaderCopy}>
                  <Text style={styles.authorName}>{post.authorFirstName}</Text>
                  <Text style={styles.postMeta}>{formatRelativeLabel(post.createdAt)}</Text>
                </View>
                <View style={styles.feedSourceBadge}>
                  <Text style={styles.feedSourceBadgeText}>
                    {activeTab === 'forYou' ? 'For You' : 'Following'}
                  </Text>
                </View>
              </View>

              <Text style={styles.postBody}>{post.body}</Text>

              {post.hashtags.length > 0 && (
                <View style={styles.hashtagRow}>
                  {post.hashtags.map((hashtag) => (
                    <View key={`${post.id}-${hashtag}`} style={styles.hashtagChip}>
                      <Text style={styles.hashtagText}>#{hashtag}</Text>
                    </View>
                  ))}
                </View>
              )}

              <View style={styles.postFooter}>
                <Text style={styles.footerStat}>{post.likeCount} likes</Text>
                <Text style={styles.footerStat}>{post.commentCount} comments</Text>
              </View>
            </View>
          ))
        )}
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#ECE8E1',
  },
  scrollArea: {
    flex: 1,
  },
  content: {
    paddingHorizontal: 24,
    paddingTop: 54,
    paddingBottom: 40,
  },
  statusRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 24,
  },
  statusText: {
    color: '#2E2A24',
    fontSize: 16,
    fontWeight: '700',
  },
  statusIcons: {
    color: '#5D554C',
    fontSize: 18,
    fontWeight: '700',
  },
  headerRow: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: 14,
    marginBottom: 22,
  },
  backButton: {
    paddingTop: 2,
    paddingRight: 8,
  },
  backText: {
    color: '#23372B',
    fontSize: 32,
    fontWeight: '500',
  },
  headerCopy: {
    flex: 1,
  },
  eyebrow: {
    color: '#8B8176',
    fontSize: 12,
    fontWeight: '700',
    letterSpacing: 1.8,
    marginBottom: 8,
  },
  title: {
    color: '#23372B',
    fontSize: 30,
    lineHeight: 36,
    fontWeight: '700',
  },
  summaryCard: {
    backgroundColor: '#F8F6F2',
    borderRadius: 24,
    padding: 20,
    borderWidth: 1,
    borderColor: '#DDD5CA',
    marginBottom: 18,
  },
  summaryHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    gap: 12,
  },
  summaryLabel: {
    color: '#8B8176',
    fontSize: 12,
    fontWeight: '700',
    marginBottom: 6,
  },
  summaryCount: {
    color: '#23372B',
    fontSize: 30,
    fontWeight: '700',
  },
  summaryText: {
    color: '#6F6459',
    fontSize: 14,
    lineHeight: 21,
    marginTop: 12,
  },
  markReadButton: {
    backgroundColor: '#456B50',
    borderRadius: 16,
    paddingHorizontal: 14,
    paddingVertical: 10,
  },
  markReadButtonText: {
    color: '#F8F6F2',
    fontSize: 13,
    fontWeight: '700',
  },
  tabRow: {
    flexDirection: 'row',
    gap: 10,
    marginBottom: 18,
  },
  tabButton: {
    flex: 1,
    backgroundColor: '#F8F6F2',
    borderRadius: 18,
    paddingVertical: 14,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: '#DDD5CA',
  },
  tabButtonActive: {
    backgroundColor: '#456B50',
    borderColor: '#456B50',
  },
  tabButtonText: {
    color: '#5F5449',
    fontSize: 15,
    fontWeight: '700',
  },
  tabButtonTextActive: {
    color: '#F8F6F2',
  },
  loader: {
    marginTop: 36,
  },
  emptyCard: {
    backgroundColor: '#F8F6F2',
    borderRadius: 22,
    padding: 22,
    borderWidth: 1,
    borderColor: '#DDD5CA',
  },
  emptyTitle: {
    color: '#23372B',
    fontSize: 18,
    fontWeight: '700',
    marginBottom: 8,
  },
  emptyText: {
    color: '#6F6459',
    fontSize: 14,
    lineHeight: 21,
  },
  postCard: {
    backgroundColor: '#F8F6F2',
    borderRadius: 24,
    padding: 18,
    borderWidth: 1,
    borderColor: '#DDD5CA',
    marginBottom: 14,
  },
  postHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 14,
  },
  avatar: {
    width: 44,
    height: 44,
    borderRadius: 22,
    backgroundColor: '#D7E8DA',
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: 12,
  },
  avatarText: {
    color: '#2F563C',
    fontSize: 14,
    fontWeight: '700',
  },
  postHeaderCopy: {
    flex: 1,
  },
  authorName: {
    color: '#23372B',
    fontSize: 15,
    fontWeight: '700',
    marginBottom: 2,
  },
  postMeta: {
    color: '#8B8176',
    fontSize: 12,
    fontWeight: '500',
  },
  feedSourceBadge: {
    backgroundColor: '#EEF3EE',
    borderRadius: 999,
    paddingHorizontal: 10,
    paddingVertical: 6,
  },
  feedSourceBadgeText: {
    color: '#2F563C',
    fontSize: 11,
    fontWeight: '700',
  },
  postBody: {
    color: '#3E352C',
    fontSize: 15,
    lineHeight: 22,
  },
  hashtagRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
    marginTop: 14,
  },
  hashtagChip: {
    backgroundColor: '#EEF3EE',
    borderRadius: 999,
    paddingHorizontal: 10,
    paddingVertical: 6,
  },
  hashtagText: {
    color: '#2F563C',
    fontSize: 12,
    fontWeight: '700',
  },
  postFooter: {
    flexDirection: 'row',
    gap: 16,
    marginTop: 16,
  },
  footerStat: {
    color: '#8B8176',
    fontSize: 12,
    fontWeight: '600',
  },
});
