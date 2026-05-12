import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { router } from 'expo-router';
import {
  ActivityIndicator,
  Modal,
  RefreshControl,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native';
import apiClient from '../api/client';
import AuthImage from '../components/AuthImage';

type FeedTab = 'forYou' | 'following';

type PostAttachment = {
  id: string;
  downloadUrl: string;
  filename: string;
  contentType: string;
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
  shareCount: number;
  attachments: PostAttachment[];
  sharedById?: number;
  sharedByFirstName?: string;
  shareCommentary?: string;
  sharedAt?: string;
  viewerHasLiked?: boolean;
  viewerHasBookmarked?: boolean;
};

type FeedUnreadCountResponse = {
  count: number;
  cappedAtMax: boolean;
};

type TrendingHashtag = {
  tag: string;
  postCount: number;
  score: number;
};

type FeedComment = {
  id: number;
  authorId: number | null;
  authorFirstName: string | null;
  body: string | null;
  createdAt: string;
  isEdited: boolean;
  isAuthor: boolean;
  isDeleted: boolean;
  likeCount: number;
  viewerHasLiked: boolean;
};

type FollowRecommendation = {
  id: number;
  firstName: string;
  lastName: string;
  role: string;
  score: number;
  factors: string[];
};

function formatRelativeLabel(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '';
  const diffMinutes = Math.max(1, Math.round((Date.now() - date.getTime()) / 60000));
  if (diffMinutes < 60) return `${diffMinutes}m ago`;
  const diffHours = Math.round(diffMinutes / 60);
  if (diffHours < 24) return `${diffHours}h ago`;
  return `${Math.round(diffHours / 24)}d ago`;
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

  // Trending
  const [trendingHashtags, setTrendingHashtags] = useState<TrendingHashtag[]>([]);

  // Like state per post: { liked, count }
  const [likeState, setLikeState] = useState<Record<number, { liked: boolean; count: number }>>({});
  const [likingPostId, setLikingPostId] = useState<number | null>(null);

  // Comments
  const [expandedPostId, setExpandedPostId] = useState<number | null>(null);
  const [comments, setComments] = useState<Record<number, FeedComment[]>>({});
  const [commentsLoading, setCommentsLoading] = useState<Record<number, boolean>>({});
  const [commentInput, setCommentInput] = useState('');
  const [submittingComment, setSubmittingComment] = useState(false);

  const commentInputRef = useRef<TextInput>(null);

  // Repost
  const [repostModalPost, setRepostModalPost] = useState<FeedPostListItem | null>(null);
  const [repostBusy, setRepostBusy] = useState(false);
  const [repostCommentary, setRepostCommentary] = useState('');
  const [shareCountState, setShareCountState] = useState<Record<number, number>>({});

  // Bookmark
  const [bookmarkState, setBookmarkState] = useState<Record<number, boolean>>({});
  const [bookmarkingId, setBookmarkingId] = useState<number | null>(null);

  // Comment likes
  const [commentLikeState, setCommentLikeState] = useState<Record<number, { liked: boolean; count: number }>>({});

  // Who to follow
  const [followRecs, setFollowRecs] = useState<FollowRecommendation[]>([]);
  const [followingIds, setFollowingIds] = useState<Set<number>>(new Set());
  const [followingInFlight, setFollowingInFlight] = useState<Set<number>>(new Set());

  const loadUnreadCount = useCallback(async () => {
    const res = await apiClient.get('/feed/unread-count');
    setUnreadCount(res.data);
  }, []);

  const loadTrending = useCallback(async () => {
    try {
      const res = await apiClient.get('/feed/trending/hashtags?limit=10');
      setTrendingHashtags(res.data ?? []);
    } catch {
      // silently ignore
    }
  }, []);

  const loadFollowRecs = useCallback(async () => {
    try {
      const res = await apiClient.get('/users/me/follow-recommendations?page=0&size=5');
      setFollowRecs(res.data?.content ?? []);
    } catch {
      // silently ignore
    }
  }, []);

  const handleFollow = async (userId: number) => {
    if (followingInFlight.has(userId)) return;
    setFollowingInFlight((s) => new Set(s).add(userId));
    try {
      if (followingIds.has(userId)) {
        await apiClient.delete(`/users/${userId}/follow`);
        setFollowingIds((s) => { const n = new Set(s); n.delete(userId); return n; });
      } else {
        await apiClient.post(`/users/${userId}/follow`);
        setFollowingIds((s) => new Set(s).add(userId));
      }
    } catch {
      // silently ignore
    } finally {
      setFollowingInFlight((s) => { const n = new Set(s); n.delete(userId); return n; });
    }
  };

  const loadFeeds = useCallback(async () => {
    setErrorMessage('');
    try {
      const [forYouRes, followingRes, unreadRes] = await Promise.all([
        apiClient.get('/feed/for-you?page=0&size=20'),
        apiClient.get('/feed/following?page=0&size=20'),
        apiClient.get('/feed/unread-count'),
      ]);
      const forYou: FeedPostListItem[] = forYouRes.data.content ?? [];
      const following: FeedPostListItem[] = followingRes.data.content ?? [];
      setForYouPosts(forYou);
      setFollowingPosts(following);
      setUnreadCount(unreadRes.data);

      // Seed like, bookmark, and share counts from posts
      const seed: Record<number, { liked: boolean; count: number }> = {};
      const shareSeed: Record<number, number> = {};
      const bmarkSeed: Record<number, boolean> = {};
      [...forYou, ...following].forEach((p) => {
        if (!(p.id in seed)) seed[p.id] = { liked: p.viewerHasLiked ?? false, count: p.likeCount };
        if (!(p.id in shareSeed)) shareSeed[p.id] = p.shareCount ?? 0;
        if (!(p.id in bmarkSeed)) bmarkSeed[p.id] = p.viewerHasBookmarked ?? false;
      });
      setLikeState((prev) => ({ ...seed, ...prev }));
      setShareCountState((prev) => ({ ...shareSeed, ...prev }));
      setBookmarkState((prev) => ({ ...bmarkSeed, ...prev }));
    } catch {
      setErrorMessage('Could not load the social feed right now.');
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  useEffect(() => {
    loadFeeds();
    loadTrending();
    loadFollowRecs();
  }, [loadFeeds, loadTrending, loadFollowRecs]);

  const activePosts = useMemo(
    () => (activeTab === 'forYou' ? forYouPosts : followingPosts),
    [activeTab, forYouPosts, followingPosts]
  );

  const onRefresh = () => {
    setRefreshing(true);
    loadFeeds();
    loadTrending();
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

  const toggleLike = async (postId: number) => {
    if (likingPostId !== null) return;
    const prev = likeState[postId] ?? { liked: false, count: 0 };
    // Optimistic update
    setLikeState((s) => ({
      ...s,
      [postId]: { liked: !prev.liked, count: prev.liked ? prev.count - 1 : prev.count + 1 },
    }));
    setLikingPostId(postId);
    try {
      const res = await apiClient.post(`/feed/posts/${postId}/like`);
      setLikeState((s) => ({
        ...s,
        [postId]: { liked: res.data.viewerHasLiked, count: res.data.likeCount },
      }));
    } catch {
      // Revert optimistic
      setLikeState((s) => ({ ...s, [postId]: prev }));
    } finally {
      setLikingPostId(null);
    }
  };

  const toggleComments = async (postId: number) => {
    if (expandedPostId === postId) {
      setExpandedPostId(null);
      setCommentInput('');
      return;
    }
    setExpandedPostId(postId);
    setCommentInput('');
    if (comments[postId]) return;
    setCommentsLoading((s) => ({ ...s, [postId]: true }));
    try {
      const res = await apiClient.get(`/feed/posts/${postId}/comments?page=0&size=20`);
      const loaded: FeedComment[] = res.data.content ?? res.data ?? [];
      setComments((s) => ({ ...s, [postId]: loaded }));
      const seed: Record<number, { liked: boolean; count: number }> = {};
      loaded.forEach((c) => { seed[c.id] = { liked: c.viewerHasLiked ?? false, count: c.likeCount ?? 0 }; });
      setCommentLikeState((s) => ({ ...seed, ...s }));
    } catch {
      setComments((s) => ({ ...s, [postId]: [] }));
    } finally {
      setCommentsLoading((s) => ({ ...s, [postId]: false }));
    }
  };

  const toggleCommentLike = async (commentId: number) => {
    const prev = commentLikeState[commentId] ?? { liked: false, count: 0 };
    setCommentLikeState((s) => ({ ...s, [commentId]: { liked: !prev.liked, count: prev.liked ? prev.count - 1 : prev.count + 1 } }));
    try {
      const res = await apiClient.post(`/feed/comments/${commentId}/like`);
      setCommentLikeState((s) => ({ ...s, [commentId]: { liked: res.data.viewerHasLiked, count: res.data.likeCount } }));
    } catch {
      setCommentLikeState((s) => ({ ...s, [commentId]: prev }));
    }
  };

  const submitComment = async (postId: number) => {
    if (!commentInput.trim() || submittingComment) return;
    setSubmittingComment(true);
    try {
      const res = await apiClient.post(`/feed/posts/${postId}/comments`, { body: commentInput.trim() });
      setComments((s) => ({ ...s, [postId]: [res.data, ...(s[postId] ?? [])] }));
      setCommentInput('');
      // Update comment count on post
      setLikeState((s) => s); // no-op, comment count on the post object is separate
      setForYouPosts((posts) =>
        posts.map((p) => p.id === postId ? { ...p, commentCount: p.commentCount + 1 } : p)
      );
      setFollowingPosts((posts) =>
        posts.map((p) => p.id === postId ? { ...p, commentCount: p.commentCount + 1 } : p)
      );
    } catch {
      // silently ignore
    } finally {
      setSubmittingComment(false);
    }
  };

  const toggleBookmark = async (postId: number) => {
    if (bookmarkingId !== null) return;
    const prev = bookmarkState[postId] ?? false;
    setBookmarkState((s) => ({ ...s, [postId]: !prev }));
    setBookmarkingId(postId);
    try {
      await apiClient.post(`/feed/posts/${postId}/bookmark`);
    } catch {
      setBookmarkState((s) => ({ ...s, [postId]: prev }));
    } finally {
      setBookmarkingId(null);
    }
  };

  const openRepost = (post: FeedPostListItem) => {
    setRepostCommentary('');
    setRepostModalPost(post);
  };

  const handleRepost = async () => {
    if (!repostModalPost || repostBusy) return;
    setRepostBusy(true);
    try {
      const res = await apiClient.post(`/feed/posts/${repostModalPost.id}/reposts`, {
        body: repostCommentary.trim() || null,
      });
      if (res.data?.shareCount != null) {
        setShareCountState((s) => ({ ...s, [repostModalPost.id]: res.data.shareCount }));
      }
      setRepostModalPost(null);
    } catch {
      // silently ignore
    } finally {
      setRepostBusy(false);
    }
  };

  const navigateToUserProfile = (authorId: number) => {
    router.push({ pathname: '/user-profile', params: { userId: String(authorId) } } as any);
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

        {/* Trending hashtags */}
        {trendingHashtags.length > 0 && (
          <View style={styles.trendingCard}>
            <Text style={styles.trendingLabel}>TRENDING</Text>
            <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.trendingScroll}>
              {trendingHashtags.map((h) => (
                <View key={h.tag} style={styles.trendingChip}>
                  <Text style={styles.trendingChipText}>#{h.tag}</Text>
                  <Text style={styles.trendingChipCount}>{h.postCount}</Text>
                </View>
              ))}
            </ScrollView>
          </View>
        )}

        {/* Who to Follow */}
        {followRecs.length > 0 && (
          <View style={styles.whoToFollowCard}>
            <Text style={styles.whoToFollowLabel}>WHO TO FOLLOW</Text>
            {followRecs.map((rec) => {
              const isFollowing = followingIds.has(rec.id);
              const inFlight = followingInFlight.has(rec.id);
              return (
                <View key={rec.id} style={styles.whoToFollowRow}>
                  <View style={styles.whoToFollowAvatar}>
                    <Text style={styles.whoToFollowAvatarText}>
                      {rec.firstName.substring(0, 1).toUpperCase()}
                    </Text>
                  </View>
                  <View style={styles.whoToFollowInfo}>
                    <Text style={styles.whoToFollowName}>{rec.firstName} {rec.lastName}</Text>
                    <Text style={styles.whoToFollowRole}>{rec.role === 'MENTOR' ? 'Mentor' : 'Mentee'}</Text>
                  </View>
                  <TouchableOpacity
                    style={[styles.followBtn, isFollowing && styles.followBtnActive]}
                    onPress={() => handleFollow(rec.id)}
                    disabled={inFlight}
                  >
                    <Text style={[styles.followBtnText, isFollowing && styles.followBtnTextActive]}>
                      {inFlight ? '...' : isFollowing ? 'Following' : 'Follow'}
                    </Text>
                  </TouchableOpacity>
                </View>
              );
            })}
          </View>
        )}

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
          activePosts.map((post) => {
            const like = likeState[post.id] ?? { liked: false, count: post.likeCount };
            const isExpanded = expandedPostId === post.id;
            const postComments = comments[post.id] ?? [];
            const loadingCmts = commentsLoading[post.id] ?? false;

            const shareCount = shareCountState[post.id] ?? (post.shareCount ?? 0);
            const isRepostSurface = post.sharedById != null && activeTab === 'following';

            return (
              <View key={post.id} style={styles.postCard}>
                {isRepostSurface && (
                  <View style={styles.repostBanner}>
                    <Text style={styles.repostBannerText}>
                      🔁 Reposted by {post.sharedByFirstName || 'someone'}
                      {post.sharedAt ? ` · ${formatRelativeLabel(post.sharedAt)}` : ''}
                    </Text>
                  </View>
                )}

                {isRepostSurface && !!post.shareCommentary && (
                  <View style={styles.repostCommentaryBox}>
                    <Text style={styles.repostCommentaryText}>{post.shareCommentary}</Text>
                  </View>
                )}

                <View style={styles.postHeader}>
                  <TouchableOpacity onPress={() => navigateToUserProfile(post.authorId)}>
                    <View style={styles.avatar}>
                      <Text style={styles.avatarText}>{post.authorFirstName.substring(0, 2).toUpperCase()}</Text>
                    </View>
                  </TouchableOpacity>
                  <TouchableOpacity style={styles.postHeaderCopy} onPress={() => navigateToUserProfile(post.authorId)}>
                    <Text style={styles.authorName}>{post.authorFirstName}</Text>
                    <Text style={styles.postMeta}>{formatRelativeLabel(post.createdAt)}</Text>
                  </TouchableOpacity>
                  <View style={styles.feedSourceBadge}>
                    <Text style={styles.feedSourceBadgeText}>
                      {activeTab === 'forYou' ? 'For You' : 'Following'}
                    </Text>
                  </View>
                </View>

                <Text style={styles.postBody}>{post.body}</Text>

                {post.attachments?.length > 0 && post.attachments.map((att) => (
                  <AuthImage key={att.id} downloadUrl={att.downloadUrl} filename={att.filename} />
                ))}

                {post.hashtags.length > 0 && (
                  <View style={styles.hashtagRow}>
                    {post.hashtags.map((hashtag) => (
                      <View key={`${post.id}-${hashtag}`} style={styles.hashtagChip}>
                        <Text style={styles.hashtagText}>#{hashtag}</Text>
                      </View>
                    ))}
                  </View>
                )}

                {/* Actions */}
                <View style={styles.postActions}>
                  <TouchableOpacity
                    style={styles.actionBtn}
                    onPress={() => toggleLike(post.id)}
                    disabled={likingPostId === post.id}
                  >
                    <Text style={[styles.actionIcon, like.liked && styles.actionIconActive]}>
                      {like.liked ? '♥' : '♡'}
                    </Text>
                    <Text style={[styles.actionCount, like.liked && styles.actionCountActive]}>
                      {like.count}
                    </Text>
                  </TouchableOpacity>

                  <TouchableOpacity
                    style={styles.actionBtn}
                    onPress={() => toggleComments(post.id)}
                  >
                    <Text style={[styles.actionIcon, isExpanded && styles.actionIconActive]}>💬</Text>
                    <Text style={[styles.actionCount, isExpanded && styles.actionCountActive]}>
                      {post.commentCount}
                    </Text>
                  </TouchableOpacity>

                  <TouchableOpacity
                    style={styles.actionBtn}
                    onPress={() => openRepost(post)}
                  >
                    <Text style={styles.actionIcon}>🔁</Text>
                    <Text style={styles.actionCount}>{shareCount}</Text>
                  </TouchableOpacity>

                  <TouchableOpacity
                    style={[styles.actionBtn, styles.bookmarkBtn, bookmarkState[post.id] && styles.bookmarkBtnActive, { marginLeft: 'auto' }]}
                    onPress={() => toggleBookmark(post.id)}
                    disabled={bookmarkingId === post.id}
                  >
                    <Text style={[styles.bookmarkBtnText, bookmarkState[post.id] && styles.bookmarkBtnTextActive]}>
                      {bookmarkState[post.id] ? '★' : '☆'}
                    </Text>
                  </TouchableOpacity>
                </View>

                {/* Comments section */}
                {isExpanded && (
                  <View style={styles.commentsSection}>
                    <View style={styles.commentInputRow}>
                      <TextInput
                        ref={commentInputRef}
                        style={styles.commentInput}
                        value={commentInput}
                        onChangeText={setCommentInput}
                        placeholder="Write a comment..."
                        placeholderTextColor="#B5ADA3"
                        multiline
                      />
                      <TouchableOpacity
                        style={[styles.commentSendBtn, (!commentInput.trim() || submittingComment) && { opacity: 0.4 }]}
                        onPress={() => submitComment(post.id)}
                        disabled={!commentInput.trim() || submittingComment}
                      >
                        {submittingComment ? (
                          <ActivityIndicator size="small" color="#fff" />
                        ) : (
                          <Text style={styles.commentSendText}>Send</Text>
                        )}
                      </TouchableOpacity>
                    </View>

                    {loadingCmts ? (
                      <ActivityIndicator color="#456B50" style={{ marginTop: 12 }} />
                    ) : postComments.length === 0 ? (
                      <Text style={styles.noComments}>No comments yet.</Text>
                    ) : (
                      postComments.map((c) => {
                        const clike = commentLikeState[c.id] ?? { liked: false, count: 0 };
                        return (
                          <View key={c.id} style={styles.commentItem}>
                            <View style={styles.commentAvatar}>
                              <Text style={styles.commentAvatarText}>
                                {c.authorFirstName ? c.authorFirstName.substring(0, 1).toUpperCase() : '?'}
                              </Text>
                            </View>
                            <View style={styles.commentBody}>
                              <Text style={styles.commentAuthor}>
                                {c.authorFirstName ?? 'Deleted user'}
                                {c.isEdited ? <Text style={styles.editedTag}> · edited</Text> : null}
                              </Text>
                              <Text style={styles.commentText}>
                                {c.isDeleted ? '[comment removed]' : c.body}
                              </Text>
                              {!c.isDeleted && (
                                <TouchableOpacity
                                  onPress={() => toggleCommentLike(c.id)}
                                  style={{ flexDirection: 'row', alignItems: 'center', marginTop: 4, gap: 4 }}
                                >
                                  <Text style={{ fontSize: 13, color: clike.liked ? '#E05C5C' : '#9A8F82' }}>{clike.liked ? '♥' : '♡'}</Text>
                                  {clike.count > 0 && <Text style={{ fontSize: 11, color: '#9A8F82' }}>{clike.count}</Text>}
                                </TouchableOpacity>
                              )}
                            </View>
                          </View>
                        );
                      })
                    )}
                  </View>
                )}
              </View>
            );
          })
        )}
      </ScrollView>

      {/* Repost / Quote-share modal */}
      <Modal
        visible={repostModalPost !== null}
        transparent
        animationType="fade"
        onRequestClose={() => !repostBusy && setRepostModalPost(null)}
      >
        <View style={styles.modalOverlay}>
          <View style={styles.modalCard}>
            <Text style={styles.modalTitle}>Repost</Text>
            <Text style={styles.modalSubtitle}>
              Reshare this post to your followers. Add commentary to quote-share, or leave empty for a bare repost.
            </Text>

            <Text style={styles.modalLabel}>Your commentary (optional)</Text>
            <TextInput
              style={styles.modalTextarea}
              value={repostCommentary}
              onChangeText={setRepostCommentary}
              placeholder="What's your take on this?"
              placeholderTextColor="#B5ADA3"
              multiline
              maxLength={2000}
              editable={!repostBusy}
            />
            <Text style={styles.modalCharCount}>{2000 - repostCommentary.length} characters left</Text>

            {repostModalPost && (
              <View style={styles.repostEmbed}>
                <Text style={styles.repostEmbedAuthor}>{repostModalPost.authorFirstName}</Text>
                <Text style={styles.repostEmbedBody} numberOfLines={4}>{repostModalPost.body}</Text>
              </View>
            )}

            <View style={styles.modalActions}>
              <TouchableOpacity
                style={styles.modalCancelBtn}
                onPress={() => setRepostModalPost(null)}
                disabled={repostBusy}
              >
                <Text style={styles.modalCancelText}>Cancel</Text>
              </TouchableOpacity>
              <TouchableOpacity
                style={[styles.modalConfirmBtn, repostBusy && { opacity: 0.6 }]}
                onPress={handleRepost}
                disabled={repostBusy}
              >
                {repostBusy ? (
                  <ActivityIndicator size="small" color="#fff" />
                ) : (
                  <Text style={styles.modalConfirmText}>
                    {repostCommentary.trim() ? 'Quote-share' : 'Repost'}
                  </Text>
                )}
              </TouchableOpacity>
            </View>
          </View>
        </View>
      </Modal>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#ECE8E1' },
  scrollArea: { flex: 1 },
  content: { paddingHorizontal: 24, paddingTop: 54, paddingBottom: 40 },
  statusRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 24,
  },
  statusText: { color: '#2E2A24', fontSize: 16, fontWeight: '700' },
  statusIcons: { color: '#5D554C', fontSize: 18, fontWeight: '700' },
  headerRow: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: 14,
    marginBottom: 18,
  },
  backButton: { paddingTop: 2, paddingRight: 8 },
  backText: { color: '#23372B', fontSize: 32, fontWeight: '500' },
  headerCopy: { flex: 1 },
  eyebrow: {
    color: '#8B8176',
    fontSize: 12,
    fontWeight: '700',
    letterSpacing: 1.8,
    marginBottom: 8,
  },
  title: { color: '#23372B', fontSize: 30, lineHeight: 36, fontWeight: '700' },
  trendingCard: {
    backgroundColor: '#F8F6F2',
    borderRadius: 20,
    padding: 16,
    marginBottom: 14,
    borderWidth: 1,
    borderColor: '#DDD5CA',
  },
  trendingLabel: {
    color: '#8B8176',
    fontSize: 11,
    fontWeight: '700',
    letterSpacing: 1.5,
    marginBottom: 10,
  },
  trendingScroll: { flexDirection: 'row' },
  trendingChip: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#EEF3EE',
    borderRadius: 999,
    paddingHorizontal: 12,
    paddingVertical: 7,
    marginRight: 8,
    gap: 6,
  },
  trendingChipText: { color: '#2F563C', fontSize: 13, fontWeight: '700' },
  trendingChipCount: { color: '#5D8D66', fontSize: 11, fontWeight: '600' },
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
  summaryLabel: { color: '#8B8176', fontSize: 12, fontWeight: '700', marginBottom: 6 },
  summaryCount: { color: '#23372B', fontSize: 30, fontWeight: '700' },
  markReadButton: {
    backgroundColor: '#456B50',
    borderRadius: 16,
    paddingHorizontal: 14,
    paddingVertical: 10,
  },
  markReadButtonText: { color: '#F8F6F2', fontSize: 13, fontWeight: '700' },
  tabRow: { flexDirection: 'row', gap: 10, marginBottom: 18 },
  tabButton: {
    flex: 1,
    backgroundColor: '#F8F6F2',
    borderRadius: 18,
    paddingVertical: 14,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: '#DDD5CA',
  },
  tabButtonActive: { backgroundColor: '#456B50', borderColor: '#456B50' },
  tabButtonText: { color: '#5F5449', fontSize: 15, fontWeight: '700' },
  tabButtonTextActive: { color: '#F8F6F2' },
  loader: { marginTop: 36 },
  emptyCard: {
    backgroundColor: '#F8F6F2',
    borderRadius: 22,
    padding: 22,
    borderWidth: 1,
    borderColor: '#DDD5CA',
  },
  emptyTitle: { color: '#23372B', fontSize: 18, fontWeight: '700', marginBottom: 8 },
  emptyText: { color: '#6F6459', fontSize: 14, lineHeight: 21 },
  postCard: {
    backgroundColor: '#F8F6F2',
    borderRadius: 24,
    padding: 18,
    borderWidth: 1,
    borderColor: '#DDD5CA',
    marginBottom: 14,
  },
  postHeader: { flexDirection: 'row', alignItems: 'center', marginBottom: 14 },
  avatar: {
    width: 44,
    height: 44,
    borderRadius: 22,
    backgroundColor: '#D7E8DA',
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: 12,
  },
  avatarText: { color: '#2F563C', fontSize: 14, fontWeight: '700' },
  postHeaderCopy: { flex: 1 },
  authorName: { color: '#23372B', fontSize: 15, fontWeight: '700', marginBottom: 2 },
  postMeta: { color: '#8B8176', fontSize: 12, fontWeight: '500' },
  feedSourceBadge: {
    backgroundColor: '#EEF3EE',
    borderRadius: 999,
    paddingHorizontal: 10,
    paddingVertical: 6,
  },
  feedSourceBadgeText: { color: '#2F563C', fontSize: 11, fontWeight: '700' },
  postBody: { color: '#3E352C', fontSize: 15, lineHeight: 22 },
  hashtagRow: { flexDirection: 'row', flexWrap: 'wrap', gap: 8, marginTop: 14 },
  hashtagChip: {
    backgroundColor: '#EEF3EE',
    borderRadius: 999,
    paddingHorizontal: 10,
    paddingVertical: 6,
  },
  hashtagText: { color: '#2F563C', fontSize: 12, fontWeight: '700' },
  postActions: {
    flexDirection: 'row',
    gap: 20,
    marginTop: 16,
    paddingTop: 14,
    borderTopWidth: 1,
    borderTopColor: '#EDE8E1',
  },
  actionBtn: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  actionIcon: { fontSize: 18, color: '#8B8176' },
  actionIconActive: { color: '#E05C5C' },
  actionCount: { fontSize: 13, fontWeight: '600', color: '#8B8176' },
  actionCountActive: { color: '#E05C5C' },
  commentsSection: { marginTop: 14 },
  commentInputRow: { flexDirection: 'row', gap: 8, marginBottom: 12, alignItems: 'flex-end' },
  commentInput: {
    flex: 1,
    borderWidth: 1.5,
    borderColor: '#D8CEC0',
    borderRadius: 16,
    paddingHorizontal: 14,
    paddingVertical: 10,
    fontSize: 14,
    color: '#3E352C',
    backgroundColor: '#FCFBF8',
    maxHeight: 80,
  },
  commentSendBtn: {
    backgroundColor: '#456B50',
    borderRadius: 14,
    paddingHorizontal: 14,
    paddingVertical: 10,
    justifyContent: 'center',
  },
  commentSendText: { color: '#fff', fontSize: 13, fontWeight: '700' },
  noComments: { color: '#9A8F82', fontSize: 13, textAlign: 'center', marginVertical: 8 },
  commentItem: { flexDirection: 'row', gap: 10, marginBottom: 12 },
  commentAvatar: {
    width: 32,
    height: 32,
    borderRadius: 16,
    backgroundColor: '#D7E8DA',
    alignItems: 'center',
    justifyContent: 'center',
  },
  commentAvatarText: { color: '#2F563C', fontSize: 12, fontWeight: '700' },
  commentBody: { flex: 1, backgroundColor: '#EDE8E1', borderRadius: 12, padding: 10 },
  commentAuthor: { color: '#23372B', fontSize: 12, fontWeight: '700', marginBottom: 4 },
  commentText: { color: '#3E352C', fontSize: 13, lineHeight: 18 },
  editedTag: { color: '#9A8F82', fontSize: 11, fontWeight: '400' },

  // Repost attribution banner (shown in Following tab)
  repostBanner: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 10,
    paddingBottom: 10,
    borderBottomWidth: 1,
    borderBottomColor: '#EDE8E1',
  },
  repostBannerText: { color: '#7A6E62', fontSize: 12, fontWeight: '600' },
  bookmarkBtn: {
    borderWidth: 1,
    borderColor: '#D8CEC0',
    borderRadius: 10,
    paddingHorizontal: 8,
    paddingVertical: 4,
  },
  bookmarkBtnActive: { borderColor: '#456B50', backgroundColor: '#EEF3EE' },
  bookmarkBtnText: { fontSize: 16, color: '#9A8F82' },
  bookmarkBtnTextActive: { color: '#456B50' },
  whoToFollowCard: {
    backgroundColor: '#F8F6F2',
    borderRadius: 20,
    padding: 16,
    marginBottom: 14,
    borderWidth: 1,
    borderColor: '#DDD5CA',
  },
  whoToFollowLabel: {
    color: '#8B8176',
    fontSize: 11,
    fontWeight: '700',
    letterSpacing: 1.5,
    marginBottom: 12,
  },
  whoToFollowRow: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 12,
  },
  whoToFollowAvatar: {
    width: 38,
    height: 38,
    borderRadius: 19,
    backgroundColor: '#D7E8DA',
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: 10,
  },
  whoToFollowAvatarText: { color: '#2F563C', fontSize: 14, fontWeight: '700' },
  whoToFollowInfo: { flex: 1 },
  whoToFollowName: { color: '#23372B', fontSize: 14, fontWeight: '700' },
  whoToFollowRole: { color: '#8B8176', fontSize: 12, fontWeight: '500' },
  followBtn: {
    borderWidth: 1.5,
    borderColor: '#456B50',
    borderRadius: 14,
    paddingHorizontal: 14,
    paddingVertical: 6,
  },
  followBtnActive: { backgroundColor: '#456B50' },
  followBtnText: { color: '#456B50', fontSize: 13, fontWeight: '700' },
  followBtnTextActive: { color: '#fff' },

  // Quote-share commentary block
  repostCommentaryBox: {
    backgroundColor: '#EEF3EE',
    borderRadius: 12,
    padding: 12,
    marginBottom: 12,
    borderLeftWidth: 3,
    borderLeftColor: '#456B50',
  },
  repostCommentaryText: { color: '#2F563C', fontSize: 14, lineHeight: 20 },

  // Repost modal
  modalOverlay: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.45)',
    justifyContent: 'center',
    alignItems: 'center',
    padding: 24,
  },
  modalCard: {
    backgroundColor: '#F8F6F2',
    borderRadius: 24,
    padding: 22,
    width: '100%',
    maxWidth: 440,
  },
  modalTitle: { color: '#23372B', fontSize: 20, fontWeight: '700', marginBottom: 6 },
  modalSubtitle: { color: '#6F6459', fontSize: 13, lineHeight: 19, marginBottom: 16 },
  modalLabel: { color: '#5D554C', fontSize: 13, fontWeight: '700', marginBottom: 8 },
  modalTextarea: {
    borderWidth: 1.5,
    borderColor: '#D8CEC0',
    borderRadius: 14,
    paddingHorizontal: 14,
    paddingVertical: 10,
    fontSize: 14,
    color: '#3E352C',
    backgroundColor: '#FCFBF8',
    minHeight: 90,
    maxHeight: 140,
    textAlignVertical: 'top',
  },
  modalCharCount: { color: '#9A8F82', fontSize: 11, textAlign: 'right', marginTop: 4, marginBottom: 14 },
  repostEmbed: {
    backgroundColor: '#EDE8E1',
    borderRadius: 14,
    padding: 14,
    marginBottom: 18,
    borderWidth: 1,
    borderColor: '#D8CEC0',
  },
  repostEmbedAuthor: { color: '#23372B', fontSize: 13, fontWeight: '700', marginBottom: 4 },
  repostEmbedBody: { color: '#3E352C', fontSize: 13, lineHeight: 18 },
  modalActions: { flexDirection: 'row', gap: 10, justifyContent: 'flex-end' },
  modalCancelBtn: {
    borderWidth: 1.5,
    borderColor: '#D8CEC0',
    borderRadius: 14,
    paddingHorizontal: 18,
    paddingVertical: 10,
  },
  modalCancelText: { color: '#5D554C', fontSize: 14, fontWeight: '700' },
  modalConfirmBtn: {
    backgroundColor: '#456B50',
    borderRadius: 14,
    paddingHorizontal: 18,
    paddingVertical: 10,
    minWidth: 90,
    alignItems: 'center',
  },
  modalConfirmText: { color: '#fff', fontSize: 14, fontWeight: '700' },
});
