import { router } from 'expo-router';
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native';

import apiClient from '../api/client';
import { useRole } from '../components/RoleContext';

type FeedPostResponse = {
  id: number;
  authorId: number;
  authorFirstName: string;
  body: string;
  hashtags: string[];
  createdAt: string;
  updatedAt: string;
  isEdited: boolean;
  isAuthor: boolean;
};

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

type FeedPostInteractionState = {
  likeCount: number;
  commentCount: number;
  shareCount: number;
  bookmarkCount: number;
  viewerHasLiked: boolean;
  viewerHasBookmarked: boolean;
};

type FeedCommentResponse = {
  id: number;
  postId: number;
  authorId?: number | null;
  authorFirstName?: string | null;
  body?: string | null;
  createdAt: string;
  updatedAt: string;
  isEdited: boolean;
  isAuthor: boolean;
  isDeleted: boolean;
};

type EnrichedFeedPost = FeedPostListItem & {
  interactionState?: FeedPostInteractionState;
};

type FeedTab = 'for-you' | 'following';

function splitHashtags(raw: string) {
  return raw
    .split(/[,\s]+/)
    .map((token) => token.trim())
    .filter(Boolean)
    .map((token) => (token.startsWith('#') ? token : `#${token}`));
}

function formatTimestamp(value: string) {
  return new Date(value).toLocaleString('en-GB', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

function buildSearchParams(query: string) {
  const trimmed = query.trim();
  if (!trimmed) return null;

  if (trimmed.startsWith('#') && !trimmed.includes(' ')) {
    return { hashtag: trimmed };
  }

  return { q: trimmed };
}

function normalizePostFromCreate(post: FeedPostResponse): EnrichedFeedPost {
  return {
    id: post.id,
    authorId: post.authorId,
    authorFirstName: post.authorFirstName,
    body: post.body,
    hashtags: post.hashtags,
    createdAt: post.createdAt,
    likeCount: 0,
    commentCount: 0,
    interactionState: {
      likeCount: 0,
      commentCount: 0,
      shareCount: 0,
      bookmarkCount: 0,
      viewerHasLiked: false,
      viewerHasBookmarked: false,
    },
  };
}

export default function SocialFeedScreen() {
  const { role } = useRole();
  const [body, setBody] = useState('');
  const [hashtagInput, setHashtagInput] = useState('');
  const [searchQuery, setSearchQuery] = useState('');
  const [activeTab, setActiveTab] = useState<FeedTab>('for-you');
  const [submitting, setSubmitting] = useState(false);
  const [loadingFeed, setLoadingFeed] = useState(true);
  const [posts, setPosts] = useState<EnrichedFeedPost[]>([]);
  const [createdPost, setCreatedPost] = useState<FeedPostResponse | null>(null);
  const [selectedPostId, setSelectedPostId] = useState<number | null>(null);
  const [selectedPostState, setSelectedPostState] = useState<FeedPostInteractionState | null>(null);
  const [comments, setComments] = useState<FeedCommentResponse[]>([]);
  const [commentsLoading, setCommentsLoading] = useState(false);
  const [commentDraft, setCommentDraft] = useState('');
  const [commentSubmitting, setCommentSubmitting] = useState(false);

  const hashtagPreview = useMemo(() => splitHashtags(hashtagInput), [hashtagInput]);

  const enrichPostsWithInteractionState = useCallback(async (items: FeedPostListItem[]) => {
    const stateEntries = await Promise.all(
      items.map(async (post) => {
        try {
          const response = await apiClient.get(`/feed/posts/${post.id}/interactions`);
          return [post.id, response.data] as const;
        } catch {
          return [
            post.id,
            {
              likeCount: post.likeCount,
              commentCount: post.commentCount,
              shareCount: 0,
              bookmarkCount: 0,
              viewerHasLiked: false,
              viewerHasBookmarked: false,
            },
          ] as const;
        }
      })
    );

    const stateMap = new Map<number, FeedPostInteractionState>(stateEntries);
    return items.map((post) => ({
      ...post,
      interactionState: stateMap.get(post.id),
    }));
  }, []);

  const fetchPosts = useCallback(async () => {
    try {
      setLoadingFeed(true);
      const searchParams = buildSearchParams(searchQuery);
      const endpoint = searchParams
        ? '/feed/search'
        : activeTab === 'for-you'
        ? '/feed/for-you'
        : '/feed/following';

      const response = await apiClient.get(endpoint, {
        params: {
          size: 20,
          ...(searchParams ?? {}),
        },
      });

      const content = response.data?.content ?? [];
      const enriched = await enrichPostsWithInteractionState(content);
      setPosts(enriched);
    } catch (error) {
      console.error('Failed to load feed posts:', error);
      Alert.alert('Error', 'Could not load social feed posts.');
    } finally {
      setLoadingFeed(false);
    }
  }, [activeTab, enrichPostsWithInteractionState, searchQuery]);

  useEffect(() => {
    void fetchPosts();
  }, [fetchPosts]);

  const fetchCommentsForPost = useCallback(async (postId: number) => {
    try {
      setCommentsLoading(true);
      const [commentsRes, stateRes] = await Promise.all([
        apiClient.get(`/feed/posts/${postId}/comments`, { params: { size: 50 } }),
        apiClient.get(`/feed/posts/${postId}/interactions`),
      ]);
      setComments(commentsRes.data?.content ?? []);
      setSelectedPostState(stateRes.data);
    } catch (error) {
      console.error('Failed to load comments:', error);
      Alert.alert('Error', 'Could not load post interactions.');
    } finally {
      setCommentsLoading(false);
    }
  }, []);

  const openPost = async (postId: number) => {
    setSelectedPostId(postId);
    setCommentDraft('');
    await fetchCommentsForPost(postId);
  };

  const submitPost = async () => {
    const trimmedBody = body.trim();
    if (!trimmedBody) {
      Alert.alert('Missing content', 'Please write some text before sharing your post.');
      return;
    }

    try {
      setSubmitting(true);
      const response = await apiClient.post('/feed/posts', {
        body: trimmedBody,
        hashtags: hashtagPreview,
      });
      const post = response.data as FeedPostResponse;
      setCreatedPost(post);
      setBody('');
      setHashtagInput('');
      setPosts((prev) => [normalizePostFromCreate(post), ...prev]);
      Alert.alert('Success', 'Your social feed post has been published.');
    } catch (error: any) {
      const message =
        error.response?.data?.message ||
        error.response?.data?.error ||
        'Could not publish your post.';
      Alert.alert('Error', message);
    } finally {
      setSubmitting(false);
    }
  };

  const toggleLike = async (postId: number) => {
    try {
      const response = await apiClient.post(`/feed/posts/${postId}/like`);
      const nextState = response.data as FeedPostInteractionState;
      setPosts((prev) =>
        prev.map((post) =>
          post.id === postId
            ? { ...post, likeCount: nextState.likeCount, commentCount: nextState.commentCount, interactionState: nextState }
            : post
        )
      );
      if (selectedPostId === postId) {
        setSelectedPostState(nextState);
      }
    } catch (error: any) {
      const message =
        error.response?.data?.message ||
        error.response?.data?.error ||
        'Could not update the like state.';
      Alert.alert('Error', message);
    }
  };

  const addComment = async () => {
    const trimmed = commentDraft.trim();
    if (!selectedPostId || !trimmed) {
      Alert.alert('Missing comment', 'Please write a comment before sending it.');
      return;
    }

    try {
      setCommentSubmitting(true);
      await apiClient.post(`/feed/posts/${selectedPostId}/comments`, { body: trimmed });
      setCommentDraft('');
      await fetchCommentsForPost(selectedPostId);
      setPosts((prev) =>
        prev.map((post) =>
          post.id === selectedPostId
            ? {
                ...post,
                commentCount: (selectedPostState?.commentCount ?? post.commentCount) + 1,
              }
            : post
        )
      );
    } catch (error: any) {
      const message =
        error.response?.data?.message ||
        error.response?.data?.error ||
        'Could not add your comment.';
      Alert.alert('Error', message);
    } finally {
      setCommentSubmitting(false);
    }
  };

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <View style={styles.statusRow}>
          <Text style={styles.statusText}>
            {new Date().toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit', hour12: false })}
          </Text>
          <Text style={styles.statusIcons}>▲ ▮</Text>
        </View>

        <TouchableOpacity onPress={() => router.back()} style={styles.backButton}>
          <Text style={styles.backButtonText}>‹ Back</Text>
        </TouchableOpacity>

        <Text style={styles.title}>
          Social{'\n'}
          <Text style={styles.titleItalic}>Feed.</Text>
        </Text>
        <Text style={styles.subtitle}>
          {role === 'mentor'
            ? 'Share insights with mentors and mentees.'
            : 'Share your progress and learning journey.'}
        </Text>
      </View>

      <ScrollView style={styles.scrollArea} contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
        <View style={styles.composerCard}>
          <Text style={styles.sectionLabel}>CREATE A POST</Text>
          <TextInput
            style={styles.bodyInput}
            value={body}
            onChangeText={setBody}
            placeholder="What would you like to share with the community?"
            placeholderTextColor="#A89F93"
            multiline
            maxLength={2000}
            textAlignVertical="top"
          />
          <Text style={styles.charCount}>{body.trim().length}/2000</Text>

          <Text style={styles.sectionLabel}>HASHTAGS</Text>
          <TextInput
            style={styles.tagInput}
            value={hashtagInput}
            onChangeText={setHashtagInput}
            placeholder="#career #react-native #mentorship"
            placeholderTextColor="#A89F93"
            autoCapitalize="none"
            autoCorrect={false}
          />

          {hashtagPreview.length > 0 ? (
            <View style={styles.tagWrap}>
              {hashtagPreview.map((tag) => (
                <View key={tag} style={styles.tagChip}>
                  <Text style={styles.tagChipText}>{tag.toLowerCase()}</Text>
                </View>
              ))}
            </View>
          ) : (
            <Text style={styles.helperText}>
              Separate hashtags with spaces or commas. The server will normalize them automatically.
            </Text>
          )}

          <TouchableOpacity
            style={[styles.publishButton, submitting && styles.publishButtonDisabled]}
            onPress={submitPost}
            disabled={submitting}
          >
            <Text style={styles.publishButtonText}>
              {submitting ? 'Publishing...' : 'Publish Post'}
            </Text>
          </TouchableOpacity>
        </View>

        <View style={styles.feedCard}>
          <View style={styles.feedTopRow}>
            <Text style={styles.sectionLabel}>DISCOVER POSTS</Text>
            <TouchableOpacity onPress={() => fetchPosts()}>
              <Text style={styles.refreshText}>Refresh</Text>
            </TouchableOpacity>
          </View>

          <View style={styles.tabRow}>
            <TouchableOpacity
              style={[styles.tabButton, activeTab === 'for-you' && styles.tabButtonActive]}
              onPress={() => setActiveTab('for-you')}
            >
              <Text style={[styles.tabButtonText, activeTab === 'for-you' && styles.tabButtonTextActive]}>
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

          <TextInput
            style={styles.searchInput}
            value={searchQuery}
            onChangeText={setSearchQuery}
            placeholder="Search by keyword or #hashtag"
            placeholderTextColor="#A89F93"
            autoCapitalize="none"
            autoCorrect={false}
          />

          {loadingFeed ? (
            <ActivityIndicator size="large" color="#456B50" style={styles.feedLoader} />
          ) : posts.length === 0 ? (
            <View style={styles.emptyState}>
              <Text style={styles.emptyStateTitle}>No posts found</Text>
              <Text style={styles.emptyStateText}>
                Try another keyword or switch tabs to explore more posts.
              </Text>
            </View>
          ) : (
            posts.map((post) => {
              const interactionState = post.interactionState;
              const isSelected = post.id === selectedPostId;
              return (
                <View key={post.id} style={[styles.postCard, isSelected && styles.postCardSelected]}>
                  <View style={styles.previewHeader}>
                    <View style={styles.avatar}>
                      <Text style={styles.avatarText}>{post.authorFirstName.slice(0, 2).toUpperCase()}</Text>
                    </View>
                    <View style={styles.previewMeta}>
                      <Text style={styles.previewAuthor}>{post.authorFirstName}</Text>
                      <Text style={styles.previewDate}>{formatTimestamp(post.createdAt)}</Text>
                    </View>
                  </View>

                  <Text style={styles.previewBody}>{post.body}</Text>

                  {post.hashtags.length > 0 ? (
                    <View style={styles.tagWrap}>
                      {post.hashtags.map((tag) => (
                        <View key={tag} style={styles.previewTagChip}>
                          <Text style={styles.previewTagText}>#{tag}</Text>
                        </View>
                      ))}
                    </View>
                  ) : null}

                  <View style={styles.interactionRow}>
                    <TouchableOpacity style={styles.interactionButton} onPress={() => toggleLike(post.id)}>
                      <Text style={styles.interactionButtonText}>
                        {interactionState?.viewerHasLiked ? '♥' : '♡'} {interactionState?.likeCount ?? post.likeCount}
                      </Text>
                    </TouchableOpacity>
                    <TouchableOpacity style={styles.interactionButton} onPress={() => openPost(post.id)}>
                      <Text style={styles.interactionButtonText}>
                        💬 {interactionState?.commentCount ?? post.commentCount}
                      </Text>
                    </TouchableOpacity>
                  </View>

                  {isSelected ? (
                    <View style={styles.commentPanel}>
                      <Text style={styles.sectionLabel}>COMMENTS</Text>
                      {commentsLoading ? (
                        <ActivityIndicator size="small" color="#456B50" />
                      ) : comments.length === 0 ? (
                        <Text style={styles.helperText}>No comments yet. Start the conversation.</Text>
                      ) : (
                        comments.map((comment) => (
                          <View key={comment.id} style={styles.commentCard}>
                            <Text style={styles.commentAuthor}>
                              {comment.authorFirstName || 'Unknown User'}
                            </Text>
                            <Text style={styles.commentBody}>
                              {comment.isDeleted ? '[comment removed]' : comment.body}
                            </Text>
                            <Text style={styles.commentMeta}>{formatTimestamp(comment.createdAt)}</Text>
                          </View>
                        ))
                      )}

                      <TextInput
                        style={styles.commentInput}
                        value={commentDraft}
                        onChangeText={setCommentDraft}
                        placeholder="Write a comment"
                        placeholderTextColor="#A89F93"
                        multiline
                      />
                      <TouchableOpacity
                        style={[styles.publishButton, commentSubmitting && styles.publishButtonDisabled]}
                        onPress={addComment}
                        disabled={commentSubmitting}
                      >
                        <Text style={styles.publishButtonText}>
                          {commentSubmitting ? 'Sending...' : 'Add Comment'}
                        </Text>
                      </TouchableOpacity>
                    </View>
                  ) : null}
                </View>
              );
            })
          )}
        </View>

        {createdPost ? (
          <View style={styles.previewCard}>
            <Text style={styles.sectionLabel}>LATEST CREATED POST</Text>
            <View style={styles.previewHeader}>
              <View style={styles.avatar}>
                <Text style={styles.avatarText}>
                  {createdPost.authorFirstName.slice(0, 2).toUpperCase()}
                </Text>
              </View>
              <View style={styles.previewMeta}>
                <Text style={styles.previewAuthor}>{createdPost.authorFirstName}</Text>
                <Text style={styles.previewDate}>{formatTimestamp(createdPost.createdAt)}</Text>
              </View>
            </View>

            <Text style={styles.previewBody}>{createdPost.body}</Text>

            {createdPost.hashtags.length > 0 ? (
              <View style={styles.tagWrap}>
                {createdPost.hashtags.map((tag) => (
                  <View key={tag} style={styles.previewTagChip}>
                    <Text style={styles.previewTagText}>#{tag}</Text>
                  </View>
                ))}
              </View>
            ) : null}

            <Text style={styles.previewFooter}>
              {createdPost.isAuthor ? 'You are the author of this post.' : 'Post published successfully.'}
            </Text>
          </View>
        ) : null}
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#ECE8E1',
  },
  header: {
    backgroundColor: '#456B50',
    paddingTop: 54,
    paddingHorizontal: 24,
    paddingBottom: 24,
  },
  statusRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  statusText: {
    color: '#FFFFFF',
    fontSize: 16,
    fontWeight: '700',
  },
  statusIcons: {
    color: '#FFFFFF',
    fontSize: 18,
    fontWeight: '700',
  },
  backButton: {
    alignSelf: 'flex-start',
    marginTop: 18,
    backgroundColor: 'rgba(255,255,255,0.10)',
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.16)',
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderRadius: 16,
  },
  backButtonText: {
    color: '#F7F4EE',
    fontSize: 14,
    fontWeight: '700',
  },
  title: {
    color: '#F7F4EE',
    fontSize: 34,
    lineHeight: 38,
    fontWeight: '700',
    marginTop: 18,
  },
  titleItalic: {
    fontStyle: 'italic',
  },
  subtitle: {
    color: 'rgba(247,244,238,0.84)',
    fontSize: 14,
    lineHeight: 20,
    marginTop: 10,
    maxWidth: 280,
  },
  scrollArea: {
    flex: 1,
  },
  content: {
    padding: 24,
    gap: 18,
  },
  composerCard: {
    backgroundColor: '#F8F6F2',
    borderRadius: 28,
    padding: 20,
  },
  feedCard: {
    backgroundColor: '#F8F6F2',
    borderRadius: 28,
    padding: 20,
  },
  previewCard: {
    backgroundColor: '#F8F6F2',
    borderRadius: 28,
    padding: 20,
  },
  sectionLabel: {
    color: '#8B8176',
    fontSize: 12,
    fontWeight: '700',
    letterSpacing: 1.2,
    marginBottom: 10,
  },
  bodyInput: {
    minHeight: 150,
    borderRadius: 22,
    borderWidth: 1.5,
    borderColor: '#DDD5CA',
    backgroundColor: '#FFFFFF',
    paddingHorizontal: 16,
    paddingVertical: 16,
    color: '#23372B',
    fontSize: 15,
  },
  charCount: {
    color: '#8B8176',
    fontSize: 12,
    textAlign: 'right',
    marginTop: 8,
    marginBottom: 18,
  },
  tagInput: {
    height: 54,
    borderRadius: 18,
    borderWidth: 1.5,
    borderColor: '#DDD5CA',
    backgroundColor: '#FFFFFF',
    paddingHorizontal: 16,
    color: '#23372B',
    fontSize: 15,
  },
  helperText: {
    color: '#8B8176',
    fontSize: 13,
    lineHeight: 18,
    marginTop: 10,
  },
  tagWrap: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
    marginTop: 12,
  },
  tagChip: {
    backgroundColor: '#E4EEE6',
    borderRadius: 999,
    paddingHorizontal: 12,
    paddingVertical: 8,
  },
  tagChipText: {
    color: '#2F563C',
    fontSize: 13,
    fontWeight: '700',
  },
  publishButton: {
    marginTop: 20,
    backgroundColor: '#4B7B57',
    borderRadius: 20,
    paddingVertical: 16,
    alignItems: 'center',
  },
  publishButtonDisabled: {
    opacity: 0.6,
  },
  publishButtonText: {
    color: '#F8F6F2',
    fontSize: 15,
    fontWeight: '700',
  },
  feedTopRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  refreshText: {
    color: '#456B50',
    fontSize: 13,
    fontWeight: '700',
  },
  tabRow: {
    flexDirection: 'row',
    gap: 10,
    marginBottom: 14,
  },
  tabButton: {
    flex: 1,
    borderRadius: 16,
    backgroundColor: '#EFE8DE',
    paddingVertical: 12,
    alignItems: 'center',
  },
  tabButtonActive: {
    backgroundColor: '#D7E8DA',
  },
  tabButtonText: {
    color: '#6A5E52',
    fontSize: 14,
    fontWeight: '700',
  },
  tabButtonTextActive: {
    color: '#2F563C',
  },
  searchInput: {
    height: 54,
    borderRadius: 18,
    borderWidth: 1.5,
    borderColor: '#DDD5CA',
    backgroundColor: '#FFFFFF',
    paddingHorizontal: 16,
    color: '#23372B',
    fontSize: 15,
    marginBottom: 14,
  },
  feedLoader: {
    marginVertical: 24,
  },
  emptyState: {
    backgroundColor: '#FFFFFF',
    borderRadius: 22,
    padding: 18,
    alignItems: 'center',
  },
  emptyStateTitle: {
    color: '#23372B',
    fontSize: 18,
    fontWeight: '700',
    marginBottom: 6,
  },
  emptyStateText: {
    color: '#8B8176',
    fontSize: 13,
    lineHeight: 18,
    textAlign: 'center',
  },
  postCard: {
    backgroundColor: '#FFFFFF',
    borderRadius: 24,
    padding: 18,
    marginTop: 14,
    borderWidth: 1,
    borderColor: '#EEE4D8',
  },
  postCardSelected: {
    borderColor: '#C8D8CB',
  },
  previewHeader: {
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
    fontSize: 15,
    fontWeight: '700',
  },
  previewMeta: {
    flex: 1,
  },
  previewAuthor: {
    color: '#23372B',
    fontSize: 16,
    fontWeight: '700',
  },
  previewDate: {
    color: '#8B8176',
    fontSize: 12,
    marginTop: 2,
  },
  previewBody: {
    color: '#2E2A25',
    fontSize: 15,
    lineHeight: 22,
  },
  previewTagChip: {
    backgroundColor: '#EFE8DE',
    borderRadius: 999,
    paddingHorizontal: 12,
    paddingVertical: 8,
  },
  previewTagText: {
    color: '#6A5E52',
    fontSize: 13,
    fontWeight: '700',
  },
  interactionRow: {
    flexDirection: 'row',
    gap: 10,
    marginTop: 14,
  },
  interactionButton: {
    borderRadius: 16,
    backgroundColor: '#F5EFE7',
    paddingHorizontal: 14,
    paddingVertical: 10,
  },
  interactionButtonText: {
    color: '#4D463E',
    fontSize: 13,
    fontWeight: '700',
  },
  commentPanel: {
    marginTop: 16,
    paddingTop: 16,
    borderTopWidth: 1,
    borderTopColor: '#EEE4D8',
  },
  commentCard: {
    backgroundColor: '#F8F6F2',
    borderRadius: 18,
    padding: 14,
    marginTop: 10,
  },
  commentAuthor: {
    color: '#23372B',
    fontSize: 14,
    fontWeight: '700',
    marginBottom: 6,
  },
  commentBody: {
    color: '#4D463E',
    fontSize: 14,
    lineHeight: 20,
  },
  commentMeta: {
    color: '#8B8176',
    fontSize: 12,
    marginTop: 8,
  },
  commentInput: {
    minHeight: 90,
    borderRadius: 18,
    borderWidth: 1.5,
    borderColor: '#DDD5CA',
    backgroundColor: '#FFFFFF',
    paddingHorizontal: 16,
    paddingVertical: 14,
    color: '#23372B',
    fontSize: 15,
    marginTop: 14,
    textAlignVertical: 'top',
  },
  previewFooter: {
    color: '#6A5E52',
    fontSize: 13,
    marginTop: 16,
  },
});
