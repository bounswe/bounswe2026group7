import React, { useState, useEffect, useCallback } from 'react';
import { router } from 'expo-router';
import ActionModal from '../../components/ActionModal';
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  StyleSheet,
  ScrollView,
  Modal,
  KeyboardAvoidingView,
  Platform,
  Alert,
  ActivityIndicator,
  RefreshControl,
} from 'react-native';
import apiClient from '../../api/client';

type Post = {
  id: string;
  authorId: number | null;
  author: string;
  initials: string;
  avatarBg: string;
  avatarText: string;
  time: string;
  text: string;
  hashtags: string[];
  likes: number;
  comments: number;
  liked: boolean;
  type: 'following' | 'recommended';
  isAuthor: boolean;
};

const AVATAR_COLORS = [
  { bg: '#D7E8DA', text: '#2F563C' },
  { bg: '#E8D7E0', text: '#563C4A' },
  { bg: '#D7DCE8', text: '#3C4256' },
  { bg: '#E8E4D7', text: '#564A3C' },
  { bg: '#E8DCD7', text: '#56433C' },
];

function getInitials(name: string) {
  return name.split(' ').map((n) => n[0]).join('').substring(0, 2).toUpperCase();
}

function getAvatarColor(name: string) {
  const idx = name.charCodeAt(0) % AVATAR_COLORS.length;
  return AVATAR_COLORS[idx];
}

function timeAgo(dateStr: string): string {
  const diff = Math.floor((Date.now() - new Date(dateStr).getTime()) / 1000);
  if (diff < 60) return 'now';
  if (diff < 3600) return `${Math.floor(diff / 60)}m ago`;
  if (diff < 86400) return `${Math.floor(diff / 3600)}h ago`;
  return `${Math.floor(diff / 86400)}d ago`;
}

function extractHashtags(text: string): string[] {
  const matches = text.match(/#(\w+)/g) ?? [];
  return [...new Set(matches.map((t) => t.slice(1).toLowerCase()))];
}

function mapFeedItem(item: any, feedType: 'recommended' | 'following'): Post {
  const authorName = item.authorFirstName || 'Unknown';
  const color = getAvatarColor(authorName);
  return {
    id: String(item.id),
    authorId: item.authorId ?? null,
    author: authorName,
    initials: getInitials(authorName),
    avatarBg: color.bg,
    avatarText: color.text,
    time: item.createdAt ? timeAgo(item.createdAt) : '',
    text: item.body || '',
    hashtags: item.hashtags ?? [],
    likes: item.likeCount ?? 0,
    comments: item.commentCount ?? 0,
    liked: false,
    type: feedType,
    isAuthor: item.isAuthor === true,
  };
}

function renderTextWithHashtags(text: string) {
  const parts = text.split(/(#\w+)/g);
  return (
    <Text style={styles.postText}>
      {parts.map((part, i) =>
        part.startsWith('#') ? (
          <Text key={i} style={styles.hashtag}>{part}</Text>
        ) : (
          <Text key={i}>{part}</Text>
        )
      )}
    </Text>
  );
}

export default function FeedScreen() {
  const [posts, setPosts] = useState<Post[]>([]);
  const [activeTab, setActiveTab] = useState<'forYou' | 'following'>('forYou');
  const [searchQuery, setSearchQuery] = useState('');
  const [createVisible, setCreateVisible] = useState(false);
  const [postDraft, setPostDraft] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [myName, setMyName] = useState('');
  const [editPost, setEditPost] = useState<Post | null>(null);
  const [editDraft, setEditDraft] = useState('');
  const [editSubmitting, setEditSubmitting] = useState(false);
  const [commentPostId, setCommentPostId] = useState<string | null>(null);
  const [comments, setComments] = useState<any[]>([]);
  const [commentsLoading, setCommentsLoading] = useState(false);
  const [commentDraft, setCommentDraft] = useState('');
  const [commentSubmitting, setCommentSubmitting] = useState(false);
  const [reportPostTarget, setReportPostTarget] = useState<Post | null>(null);
  const [reportPostReason, setReportPostReason] = useState('');

  useEffect(() => {
    apiClient.get('/users/me').then((res) => {
      const firstName = res.data?.firstName || res.data?.profile?.firstName || '';
      setMyName(firstName);
    }).catch(() => {});
  }, []);

  const fetchPosts = useCallback(async () => {
    try {
      const endpoint =
        activeTab === 'forYou'
          ? '/feed/for-you?page=0&size=20'
          : '/feed/following?page=0&size=20';
      const res = await apiClient.get(endpoint);
      const items: any[] = res.data?.content ?? res.data ?? [];
      const feedType: 'recommended' | 'following' = activeTab === 'forYou' ? 'recommended' : 'following';
      setPosts(items.map((item) => mapFeedItem(item, feedType)));
    } catch (err: any) {
      const status = err?.response?.status;
      if (status !== 403) {
        Alert.alert('Error', 'Could not load posts.');
      }
      setPosts([]);
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, [activeTab]);

  useEffect(() => {
    setLoading(true);
    fetchPosts();
  }, [fetchPosts]);

  const onRefresh = useCallback(() => {
    setRefreshing(true);
    fetchPosts();
  }, [fetchPosts]);

  const [searchResults, setSearchResults] = useState<Post[] | null>(null);
  const [searchLoading, setSearchLoading] = useState(false);

  const runSearch = useCallback(async (q: string) => {
    if (!q.trim()) { setSearchResults(null); return; }
    setSearchLoading(true);
    try {
      const isHashtag = q.trim().startsWith('#');
      const param = isHashtag
        ? `hashtag=${encodeURIComponent(q.trim().replace('#', ''))}`
        : `q=${encodeURIComponent(q.trim())}`;
      const res = await apiClient.get(`/feed/search?${param}&page=0&size=20`);
      const items: any[] = res.data?.content ?? res.data ?? [];
      setSearchResults(items.map((item) => mapFeedItem(item, 'recommended')));
    } catch {
      setSearchResults([]);
    } finally {
      setSearchLoading(false);
    }
  }, []);

  useEffect(() => {
    const timer = setTimeout(() => runSearch(searchQuery), 400);
    return () => clearTimeout(timer);
  }, [searchQuery, runSearch]);

  const filteredPosts = searchQuery.trim()
    ? (searchResults ?? [])
    : posts;

  const openComments = async (postId: string) => {
    setCommentPostId(postId);
    setComments([]);
    setCommentDraft('');
    setCommentsLoading(true);
    try {
      const res = await apiClient.get(`/feed/posts/${postId}/comments?page=0&size=50`);
      setComments(res.data?.content ?? res.data ?? []);
    } catch {
      setComments([]);
    } finally {
      setCommentsLoading(false);
    }
  };

  const submitComment = async () => {
    if (!commentDraft.trim() || !commentPostId || commentSubmitting) return;
    setCommentSubmitting(true);
    try {
      const res = await apiClient.post(`/feed/posts/${commentPostId}/comments`, { body: commentDraft.trim() });
      setComments((prev) => [...prev, res.data]);
      setCommentDraft('');
      setPosts((prev) => prev.map((p) => p.id === commentPostId ? { ...p, comments: p.comments + 1 } : p));
    } catch {
      Alert.alert('Error', 'Could not post comment.');
    } finally {
      setCommentSubmitting(false);
    }
  };

  const toggleLike = async (id: string) => {
    setPosts((prev) =>
      prev.map((p) =>
        p.id === id ? { ...p, liked: !p.liked, likes: p.liked ? p.likes - 1 : p.likes + 1 } : p
      )
    );
    try {
      await apiClient.post(`/feed/posts/${id}/like`);
    } catch {
      setPosts((prev) =>
        prev.map((p) =>
          p.id === id ? { ...p, liked: !p.liked, likes: p.liked ? p.likes - 1 : p.likes + 1 } : p
        )
      );
    }
  };

  const handleDeletePost = (post: Post) => {
    Alert.alert('Delete Post', 'Are you sure you want to delete this post?', [
      { text: 'Cancel', style: 'cancel' },
      {
        text: 'Delete',
        style: 'destructive',
        onPress: async () => {
          try {
            await apiClient.delete(`/feed/posts/${post.id}`);
            setPosts((prev) => prev.filter((p) => p.id !== post.id));
          } catch {
            Alert.alert('Error', 'Could not delete post. Please try again.');
          }
        },
      },
    ]);
  };

  const handleEditPost = async () => {
    if (!editPost || !editDraft.trim() || editSubmitting) return;
    setEditSubmitting(true);
    try {
      await apiClient.patch(`/feed/posts/${editPost.id}`, { body: editDraft.trim() });
      setPosts((prev) => prev.map((p) => p.id === editPost.id ? { ...p, text: editDraft.trim() } : p));
      setEditPost(null);
    } catch {
      Alert.alert('Error', 'Could not edit post.');
    } finally {
      setEditSubmitting(false);
    }
  };

  const handlePostLongPress = (post: Post) => {
    if (post.isAuthor) {
      Alert.alert('Post Options', undefined, [
        { text: 'Edit Post', onPress: () => { setEditPost(post); setEditDraft(post.text); } },
        { text: 'Delete Post', style: 'destructive', onPress: () => handleDeletePost(post) },
        { text: 'Cancel', style: 'cancel' },
      ]);
    } else {
      Alert.alert('Post Options', undefined, [
        {
          text: 'Report Post',
          style: 'destructive',
          onPress: () => { setReportPostReason(''); setReportPostTarget(post); },
        },
        { text: 'Cancel', style: 'cancel' },
      ]);
    }
  };

  const submitPost = async () => {
    if (!postDraft.trim() || submitting) return;
    const hashtags = extractHashtags(postDraft);
    setSubmitting(true);
    try {
      const res = await apiClient.post('/feed/posts', {
        body: postDraft.trim(),
        hashtags,
      });
      const data = res.data;
      const color = getAvatarColor(myName || 'Me');
      const newPost: Post = {
        id: String(data.id),
        authorId: data.authorId,
        author: myName || data.authorFirstName || 'You',
        initials: getInitials(myName || data.authorFirstName || 'Me'),
        avatarBg: color.bg,
        avatarText: color.text,
        time: 'now',
        text: data.body,
        hashtags: data.hashtags ?? hashtags,
        likes: 0,
        comments: 0,
        liked: false,
        type: 'following',
        isAuthor: true,
      };
      setPosts((prev) => [newPost, ...prev]);
      setPostDraft('');
      setCreateVisible(false);
    } catch (err: any) {
      const msg = err.response?.data?.message || 'Could not create post. Please try again.';
      Alert.alert('Error', msg);
    } finally {
      setSubmitting(false);
    }
  };

  const previewHashtags = extractHashtags(postDraft);

  return (
    <View style={styles.container}>
      <ActionModal
        visible={!!reportPostTarget}
        title="Report Post"
        message="Help us understand the issue. Your report is anonymous."
        fields={[{
          label: 'Reason',
          placeholder: 'e.g. spam, misinformation, inappropriate content',
          value: reportPostReason,
          onChange: setReportPostReason,
          multiline: true,
          required: true,
        }]}
        confirmLabel="Submit Report"
        danger
        onConfirm={() => {
          if (!reportPostReason.trim()) return;
          setReportPostTarget(null);
          setReportPostReason('');
          Alert.alert('Report Submitted', 'Thank you. Our team will review this report.');
        }}
        onCancel={() => { setReportPostTarget(null); setReportPostReason(''); }}
      />
      <View style={styles.header}>
        <View style={styles.topCircle} />
        <View style={styles.statusRow}>
          <Text style={styles.statusText}>
            {new Date().toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit', hour12: false })}
          </Text>
          <Text style={styles.statusIcons}>▲ ▮</Text>
        </View>
        <Text style={styles.headerTitle}>Feed</Text>
        <View style={styles.searchBar}>
          <Text style={styles.searchIcon}>🔍</Text>
          <TextInput
            style={styles.searchInput}
            placeholder="Search posts, #hashtags..."
            placeholderTextColor="rgba(255,255,255,0.5)"
            value={searchQuery}
            onChangeText={setSearchQuery}
            returnKeyType="search"
          />
          {searchLoading && <ActivityIndicator size="small" color="rgba(255,255,255,0.7)" style={{ marginRight: 4 }} />}
          {searchQuery.length > 0 && !searchLoading && (
            <TouchableOpacity onPress={() => { setSearchQuery(''); setSearchResults(null); }}>
              <Text style={styles.searchClear}>✕</Text>
            </TouchableOpacity>
          )}
        </View>
      </View>

      <View style={styles.filterRow}>
        {[
          { key: 'forYou', label: 'For You' },
          { key: 'following', label: 'Following' },
        ].map((tab) => (
          <TouchableOpacity
            key={tab.key}
            style={[styles.filterTab, activeTab === tab.key && styles.filterTabActive]}
            onPress={() => setActiveTab(tab.key as any)}
          >
            <Text style={[styles.filterTabText, activeTab === tab.key && styles.filterTabTextActive]}>
              {tab.label}
            </Text>
          </TouchableOpacity>
        ))}
      </View>

      {loading ? (
        <View style={styles.loadingState}>
          <ActivityIndicator size="large" color="#456B50" />
        </View>
      ) : (
        <ScrollView
          style={styles.feed}
          contentContainerStyle={styles.feedContent}
          showsVerticalScrollIndicator={false}
          refreshControl={
            <RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor="#456B50" />
          }
        >
          {filteredPosts.length === 0 && (
            <View style={styles.emptyState}>
              <Text style={styles.emptyIcon}>📭</Text>
              <Text style={styles.emptyText}>
                {activeTab === 'following' ? 'Follow people to see their posts here.' : 'No posts found.'}
              </Text>
            </View>
          )}

          {filteredPosts.map((post) => (
            <TouchableOpacity
              key={post.id}
              style={styles.postCard}
              onLongPress={() => handlePostLongPress(post)}
              activeOpacity={0.97}
            >
              <View style={styles.postHeader}>
                <TouchableOpacity
                  onPress={() => post.authorId && router.push({ pathname: '/user-profile', params: { userId: String(post.authorId) } } as any)}
                  disabled={!post.authorId}
                  activeOpacity={0.7}
                >
                  <View style={[styles.postAvatar, { backgroundColor: post.avatarBg }]}>
                    <Text style={[styles.postAvatarText, { color: post.avatarText }]}>{post.initials}</Text>
                  </View>
                </TouchableOpacity>
                <View style={styles.postMeta}>
                  <View style={styles.postMetaTop}>
                    <TouchableOpacity
                      onPress={() => post.authorId && router.push({ pathname: '/user-profile', params: { userId: String(post.authorId) } } as any)}
                      disabled={!post.authorId}
                    >
                      <Text style={styles.postAuthor}>{post.author}</Text>
                    </TouchableOpacity>
                    {post.type === 'recommended' && (
                      <View style={styles.recommendedBadge}>
                        <Text style={styles.recommendedBadgeText}>✦ For You</Text>
                      </View>
                    )}
                    {post.isAuthor && (
                      <View style={styles.myPostBadge}>
                        <Text style={styles.myPostBadgeText}>You</Text>
                      </View>
                    )}
                  </View>
                  <Text style={styles.postTime}>{post.time}</Text>
                </View>
              </View>

              <View style={styles.postBody}>
                {renderTextWithHashtags(post.text)}
              </View>

              {post.hashtags.length > 0 && (
                <View style={styles.hashtagRow}>
                  {post.hashtags.map((tag) => (
                    <View key={tag} style={styles.hashtagChip}>
                      <Text style={styles.hashtagChipText}>#{tag}</Text>
                    </View>
                  ))}
                </View>
              )}

              <View style={styles.postActions}>
                <TouchableOpacity style={styles.actionBtn} onPress={() => toggleLike(post.id)}>
                  <Text style={[styles.actionIcon, post.liked && styles.actionIconLiked]}>
                    {post.liked ? '♥' : '♡'}
                  </Text>
                  <Text style={[styles.actionCount, post.liked && styles.actionCountLiked]}>
                    {post.likes}
                  </Text>
                </TouchableOpacity>

                <TouchableOpacity style={styles.actionBtn} onPress={() => openComments(post.id)}>
                  <Text style={styles.actionIcon}>💬</Text>
                  <Text style={styles.actionCount}>{post.comments}</Text>
                </TouchableOpacity>

                <TouchableOpacity style={styles.actionBtn}>
                  <Text style={styles.actionIcon}>↗</Text>
                </TouchableOpacity>
              </View>
            </TouchableOpacity>
          ))}

          <View style={{ height: 100 }} />
        </ScrollView>
      )}

      <TouchableOpacity style={styles.createFab} onPress={() => setCreateVisible(true)}>
        <Text style={styles.createFabIcon}>✏</Text>
      </TouchableOpacity>

      <Modal visible={editPost !== null} animationType="slide" transparent onRequestClose={() => setEditPost(null)}>
        <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : 'height'} style={styles.modalOverlay}>
          <View style={styles.createSheet}>
            <View style={styles.createSheetHandle} />
            <View style={styles.createHeader}>
              <Text style={styles.createTitle}>Edit Post</Text>
              <TouchableOpacity onPress={() => setEditPost(null)} disabled={editSubmitting}>
                <Text style={styles.createClose}>✕</Text>
              </TouchableOpacity>
            </View>
            <TextInput
              style={[styles.createInput, { height: 120 }]}
              value={editDraft}
              onChangeText={setEditDraft}
              multiline
              placeholder="Edit your post..."
              placeholderTextColor="#B0A898"
            />
            <TouchableOpacity
              style={[styles.postBtn, (!editDraft.trim() || editSubmitting) && { opacity: 0.4 }]}
              onPress={handleEditPost}
              disabled={!editDraft.trim() || editSubmitting}
            >
              <Text style={styles.postBtnText}>{editSubmitting ? 'Saving…' : 'Save'}</Text>
            </TouchableOpacity>
          </View>
        </KeyboardAvoidingView>
      </Modal>

      <Modal visible={commentPostId !== null} animationType="slide" transparent onRequestClose={() => setCommentPostId(null)}>
        <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : 'height'} style={styles.modalOverlay}>
          <View style={[styles.createSheet, { maxHeight: '80%' }]}>
            <View style={styles.createSheetHandle} />
            <View style={styles.createHeader}>
              <Text style={styles.createTitle}>Comments</Text>
              <TouchableOpacity onPress={() => setCommentPostId(null)}>
                <Text style={styles.createClose}>✕</Text>
              </TouchableOpacity>
            </View>

            {commentsLoading ? (
              <ActivityIndicator size="large" color="#456B50" style={{ marginVertical: 24 }} />
            ) : (
              <ScrollView style={{ flex: 1 }} showsVerticalScrollIndicator={false}>
                {comments.length === 0 && (
                  <Text style={{ color: '#9A8F82', textAlign: 'center', marginVertical: 24 }}>No comments yet. Be the first!</Text>
                )}
                {comments.filter((c) => !c.isDeleted).map((c) => (
                  <View key={c.id} style={{ marginBottom: 14, paddingBottom: 14, borderBottomWidth: 1, borderBottomColor: '#F0EDE8' }}>
                    <Text style={{ fontWeight: '700', color: '#1D1D38', fontSize: 13, marginBottom: 3 }}>{c.authorFirstName || 'User'}</Text>
                    <Text style={{ color: '#3A3A3A', fontSize: 14, lineHeight: 20 }}>{c.body}</Text>
                  </View>
                ))}
              </ScrollView>
            )}

            <View style={{ flexDirection: 'row', gap: 10, marginTop: 12 }}>
              <TextInput
                style={[styles.createInput, { flex: 1, marginBottom: 0, height: 44 }]}
                placeholder="Write a comment..."
                placeholderTextColor="#B0A898"
                value={commentDraft}
                onChangeText={setCommentDraft}
                returnKeyType="send"
                onSubmitEditing={submitComment}
              />
              <TouchableOpacity
                style={[styles.postBtn, { paddingHorizontal: 18, height: 44, justifyContent: 'center' }]}
                onPress={submitComment}
                disabled={commentSubmitting || !commentDraft.trim()}
              >
                <Text style={styles.postBtnText}>{commentSubmitting ? '...' : 'Send'}</Text>
              </TouchableOpacity>
            </View>
          </View>
        </KeyboardAvoidingView>
      </Modal>

      <Modal visible={createVisible} animationType="slide" transparent>
        <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : 'height'} style={styles.modalOverlay}>
          <View style={styles.createSheet}>
            <View style={styles.createSheetHandle} />

            <View style={styles.createHeader}>
              <Text style={styles.createTitle}>New Post</Text>
              <TouchableOpacity
                onPress={() => { setCreateVisible(false); setPostDraft(''); }}
                disabled={submitting}
              >
                <Text style={styles.createClose}>✕</Text>
              </TouchableOpacity>
            </View>

            <View style={styles.createAuthorRow}>
              <View style={[styles.postAvatar, { backgroundColor: getAvatarColor(myName || 'Me').bg }]}>
                <Text style={[styles.postAvatarText, { color: getAvatarColor(myName || 'Me').text }]}>
                  {getInitials(myName || 'Me')}
                </Text>
              </View>
              <View>
                <Text style={styles.createAuthorName}>{myName || 'You'}</Text>
                <Text style={styles.createAuthorSub}>Posting publicly</Text>
              </View>
            </View>

            <TextInput
              style={styles.createInput}
              placeholder="What's on your mind? Use #hashtags to tag topics..."
              placeholderTextColor="#B0A89E"
              value={postDraft}
              onChangeText={setPostDraft}
              multiline
              maxLength={500}
              autoFocus
            />

            <Text style={styles.createCharCount}>{postDraft.length}/500</Text>

            {previewHashtags.length > 0 && (
              <View style={styles.previewHashtagRow}>
                {previewHashtags.map((tag) => (
                  <View key={tag} style={styles.hashtagChip}>
                    <Text style={styles.hashtagChipText}>#{tag}</Text>
                  </View>
                ))}
              </View>
            )}

            <TouchableOpacity
              style={[styles.postBtn, (!postDraft.trim() || submitting) && { opacity: 0.4 }]}
              onPress={submitPost}
              disabled={!postDraft.trim() || submitting}
            >
              {submitting
                ? <ActivityIndicator size="small" color="#F7F4EE" />
                : <Text style={styles.postBtnText}>Post</Text>}
            </TouchableOpacity>
          </View>
        </KeyboardAvoidingView>
      </Modal>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#ECE8E1' },

  header: {
    backgroundColor: '#456B50',
    paddingTop: 54,
    paddingHorizontal: 20,
    paddingBottom: 18,
    overflow: 'hidden',
  },
  topCircle: {
    position: 'absolute',
    width: 260,
    height: 260,
    borderRadius: 130,
    backgroundColor: 'rgba(255,255,255,0.05)',
    top: -40,
    right: -60,
  },
  statusRow: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: 8 },
  statusText: { color: '#fff', fontSize: 16, fontWeight: '700' },
  statusIcons: { color: '#fff', fontSize: 18, fontWeight: '700' },
  headerTitle: { color: '#F7F4EE', fontSize: 28, fontWeight: '800', marginBottom: 14 },

  searchBar: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: 'rgba(255,255,255,0.13)',
    borderRadius: 18,
    paddingHorizontal: 14,
    paddingVertical: 10,
    gap: 8,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.18)',
  },
  searchIcon: { fontSize: 16 },
  searchInput: { flex: 1, color: '#F7F4EE', fontSize: 15, fontWeight: '500' },
  searchClear: { color: 'rgba(255,255,255,0.6)', fontSize: 16, fontWeight: '700' },

  filterRow: {
    flexDirection: 'row',
    backgroundColor: '#F8F6F2',
    borderBottomWidth: 1,
    borderBottomColor: '#E5DED4',
    paddingHorizontal: 16,
    paddingTop: 10,
  },
  filterTab: {
    flex: 1,
    alignItems: 'center',
    paddingBottom: 10,
    borderBottomWidth: 2,
    borderBottomColor: 'transparent',
  },
  filterTabActive: { borderBottomColor: '#456B50' },
  filterTabText: { color: '#9A8F82', fontSize: 14, fontWeight: '600' },
  filterTabTextActive: { color: '#456B50' },

  loadingState: { flex: 1, alignItems: 'center', justifyContent: 'center' },

  feed: { flex: 1 },
  feedContent: { paddingTop: 12, paddingHorizontal: 16 },

  emptyState: { paddingVertical: 60, alignItems: 'center' },
  emptyIcon: { fontSize: 36, marginBottom: 10 },
  emptyText: { color: '#9A8F82', fontSize: 15, fontWeight: '500', textAlign: 'center' },

  postCard: {
    backgroundColor: '#F8F6F2',
    borderRadius: 22,
    padding: 16,
    marginBottom: 12,
  },
  postHeader: { flexDirection: 'row', alignItems: 'flex-start', gap: 12, marginBottom: 10 },
  postAvatar: {
    width: 44,
    height: 44,
    borderRadius: 22,
    justifyContent: 'center',
    alignItems: 'center',
    flexShrink: 0,
  },
  postAvatarText: { fontSize: 15, fontWeight: '700' },
  postMeta: { flex: 1 },
  postMetaTop: { flexDirection: 'row', alignItems: 'center', flexWrap: 'wrap', gap: 6, marginBottom: 3 },
  postAuthor: { color: '#23372B', fontSize: 15, fontWeight: '700' },
  recommendedBadge: { backgroundColor: '#EEE8F8', borderRadius: 8, paddingHorizontal: 7, paddingVertical: 3 },
  recommendedBadgeText: { color: '#6B4FA0', fontSize: 11, fontWeight: '700' },
  myPostBadge: { backgroundColor: '#D7E8DA', borderRadius: 8, paddingHorizontal: 7, paddingVertical: 3 },
  myPostBadgeText: { color: '#2F563C', fontSize: 11, fontWeight: '700' },
  postTime: { color: '#9A8F82', fontSize: 13, fontWeight: '500' },

  postBody: { marginBottom: 10 },
  postText: { color: '#3A332C', fontSize: 15, lineHeight: 22 },
  hashtag: { color: '#456B50', fontWeight: '600' },

  hashtagRow: { flexDirection: 'row', flexWrap: 'wrap', gap: 6, marginBottom: 10 },
  hashtagChip: {
    backgroundColor: '#EEF3EE',
    borderRadius: 10,
    paddingHorizontal: 9,
    paddingVertical: 4,
  },
  hashtagChipText: { color: '#456B50', fontSize: 12, fontWeight: '600' },

  postActions: { flexDirection: 'row', gap: 20, paddingTop: 4, borderTopWidth: 1, borderTopColor: '#EDE8E0' },
  actionBtn: { flexDirection: 'row', alignItems: 'center', gap: 5, paddingTop: 10 },
  actionIcon: { fontSize: 20, color: '#9A8F82' },
  actionIconLiked: { color: '#D9534F' },
  actionCount: { color: '#9A8F82', fontSize: 14, fontWeight: '600' },
  actionCountLiked: { color: '#D9534F' },

  createFab: {
    position: 'absolute',
    bottom: 104,
    right: 20,
    width: 54,
    height: 54,
    borderRadius: 27,
    backgroundColor: '#456B50',
    justifyContent: 'center',
    alignItems: 'center',
    shadowColor: '#000',
    shadowOpacity: 0.18,
    shadowRadius: 10,
    shadowOffset: { width: 0, height: 4 },
    elevation: 5,
  },
  createFabIcon: { fontSize: 20, color: '#F7F4EE' },

  modalOverlay: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.4)',
    justifyContent: 'flex-end',
  },
  createSheet: {
    backgroundColor: '#F8F6F2',
    borderTopLeftRadius: 28,
    borderTopRightRadius: 28,
    padding: 20,
    paddingBottom: 36,
    minHeight: 360,
  },
  createSheetHandle: {
    width: 40,
    height: 4,
    borderRadius: 2,
    backgroundColor: '#DDD5CA',
    alignSelf: 'center',
    marginBottom: 18,
  },
  createHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 },
  createTitle: { color: '#23372B', fontSize: 18, fontWeight: '700' },
  createClose: { color: '#9A8F82', fontSize: 20, fontWeight: '600', padding: 4 },
  createAuthorRow: { flexDirection: 'row', alignItems: 'center', gap: 12, marginBottom: 14 },
  createAuthorName: { color: '#23372B', fontSize: 15, fontWeight: '700' },
  createAuthorSub: { color: '#9A8F82', fontSize: 13 },
  createInput: {
    backgroundColor: '#FCFBF8',
    borderRadius: 16,
    borderWidth: 1.5,
    borderColor: '#DDD5CA',
    padding: 14,
    fontSize: 15,
    color: '#23372B',
    minHeight: 110,
    textAlignVertical: 'top',
    marginBottom: 6,
  },
  createCharCount: { color: '#B0A89E', fontSize: 12, textAlign: 'right', marginBottom: 10 },
  previewHashtagRow: { flexDirection: 'row', flexWrap: 'wrap', gap: 6, marginBottom: 14 },
  postBtn: {
    backgroundColor: '#456B50',
    borderRadius: 16,
    paddingVertical: 14,
    alignItems: 'center',
  },
  postBtnText: { color: '#F7F4EE', fontSize: 15, fontWeight: '700' },
});
