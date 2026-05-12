import React, { useCallback, useEffect, useState } from 'react';
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
import AuthImage from '../components/AuthImage';

type PostAttachment = {
  id: string;
  downloadUrl: string;
  filename: string;
  contentType: string;
};

type BookmarkedPost = {
  id: number;
  authorId: number;
  authorFirstName: string;
  body: string;
  hashtags: string[];
  createdAt: string;
  likeCount: number;
  commentCount: number;
  attachments: PostAttachment[];
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

export default function BookmarksScreen() {
  const [posts, setPosts] = useState<BookmarkedPost[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [page, setPage] = useState(0);
  const [hasMore, setHasMore] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const [removingId, setRemovingId] = useState<number | null>(null);

  const fetchBookmarks = useCallback(async (p = 0) => {
    try {
      if (p === 0) setLoading(true);
      else setLoadingMore(true);
      const res = await apiClient.get(`/feed/me/bookmarks?page=${p}&size=20`);
      const data = res.data;
      const items: BookmarkedPost[] = data.content ?? [];
      setPosts((prev) => (p === 0 ? items : [...prev, ...items]));
      setHasMore(!data.last);
      setPage(p);
    } catch {
      // silently ignore
    } finally {
      setLoading(false);
      setRefreshing(false);
      setLoadingMore(false);
    }
  }, []);

  useEffect(() => {
    fetchBookmarks(0);
  }, [fetchBookmarks]);

  const onRefresh = () => {
    setRefreshing(true);
    fetchBookmarks(0);
  };

  const removeBookmark = async (postId: number) => {
    if (removingId !== null) return;
    setRemovingId(postId);
    try {
      await apiClient.post(`/feed/posts/${postId}/bookmark`);
      setPosts((prev) => prev.filter((p) => p.id !== postId));
    } catch {
      // silently ignore
    } finally {
      setRemovingId(null);
    }
  };

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => router.back()} style={styles.backButton}>
          <Text style={styles.backText}>‹</Text>
        </TouchableOpacity>
        <View style={styles.headerCopy}>
          <Text style={styles.eyebrow}>SAVED POSTS</Text>
          <Text style={styles.title}>Bookmarks</Text>
        </View>
      </View>

      {loading ? (
        <ActivityIndicator size="large" color="#456B50" style={{ marginTop: 60 }} />
      ) : (
        <ScrollView
          contentContainerStyle={styles.content}
          showsVerticalScrollIndicator={false}
          refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor="#456B50" />}
        >
          {posts.length === 0 ? (
            <View style={styles.emptyCard}>
              <Text style={styles.emptyTitle}>No bookmarks yet</Text>
              <Text style={styles.emptyText}>
                Tap 🔖 on any post in the feed to save it here.
              </Text>
            </View>
          ) : (
            posts.map((post) => (
              <View key={post.id} style={styles.postCard}>
                <View style={styles.postHeader}>
                  <TouchableOpacity
                    onPress={() => router.push({ pathname: '/user-profile', params: { userId: String(post.authorId) } } as any)}
                  >
                    <View style={styles.avatar}>
                      <Text style={styles.avatarText}>{post.authorFirstName.substring(0, 2).toUpperCase()}</Text>
                    </View>
                  </TouchableOpacity>
                  <View style={styles.postHeaderCopy}>
                    <Text style={styles.authorName}>{post.authorFirstName}</Text>
                    <Text style={styles.postMeta}>{formatRelativeLabel(post.createdAt)}</Text>
                  </View>
                  <TouchableOpacity
                    style={styles.removeBtn}
                    onPress={() => removeBookmark(post.id)}
                    disabled={removingId === post.id}
                  >
                    {removingId === post.id ? (
                      <ActivityIndicator size="small" color="#9A8F82" />
                    ) : (
                      <Text style={styles.removeBtnText}>🔖</Text>
                    )}
                  </TouchableOpacity>
                </View>

                <Text style={styles.postBody}>{post.body}</Text>

                {post.attachments?.length > 0 && post.attachments.map((att) => (
                  <AuthImage key={att.id} downloadUrl={att.downloadUrl} filename={att.filename} />
                ))}

                {post.hashtags.length > 0 && (
                  <View style={styles.hashtagRow}>
                    {post.hashtags.map((h) => (
                      <View key={`${post.id}-${h}`} style={styles.hashtagChip}>
                        <Text style={styles.hashtagText}>#{h}</Text>
                      </View>
                    ))}
                  </View>
                )}

                <View style={styles.postFooter}>
                  <Text style={styles.footerMeta}>♥ {post.likeCount}</Text>
                  <Text style={styles.footerMeta}>💬 {post.commentCount}</Text>
                </View>
              </View>
            ))
          )}

          {hasMore && (
            <TouchableOpacity
              style={styles.loadMoreBtn}
              onPress={() => fetchBookmarks(page + 1)}
              disabled={loadingMore}
            >
              {loadingMore ? (
                <ActivityIndicator size="small" color="#456B50" />
              ) : (
                <Text style={styles.loadMoreText}>Load more</Text>
              )}
            </TouchableOpacity>
          )}

          <View style={{ height: 40 }} />
        </ScrollView>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#ECE8E1' },
  header: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: 14,
    paddingTop: 54,
    paddingHorizontal: 24,
    paddingBottom: 20,
    backgroundColor: '#ECE8E1',
  },
  backButton: { paddingTop: 2, paddingRight: 8 },
  backText: { color: '#23372B', fontSize: 32, fontWeight: '500' },
  headerCopy: { flex: 1 },
  eyebrow: { color: '#8B8176', fontSize: 12, fontWeight: '700', letterSpacing: 1.8, marginBottom: 8 },
  title: { color: '#23372B', fontSize: 30, lineHeight: 36, fontWeight: '700' },
  content: { paddingHorizontal: 24, paddingTop: 8, paddingBottom: 40 },
  emptyCard: {
    backgroundColor: '#F8F6F2',
    borderRadius: 22,
    padding: 24,
    borderWidth: 1,
    borderColor: '#DDD5CA',
    alignItems: 'center',
    marginTop: 20,
  },
  emptyTitle: { color: '#23372B', fontSize: 18, fontWeight: '700', marginBottom: 8 },
  emptyText: { color: '#6F6459', fontSize: 14, lineHeight: 21, textAlign: 'center' },
  postCard: {
    backgroundColor: '#F8F6F2',
    borderRadius: 22,
    padding: 18,
    borderWidth: 1,
    borderColor: '#DDD5CA',
    marginBottom: 14,
  },
  postHeader: { flexDirection: 'row', alignItems: 'center', marginBottom: 12 },
  avatar: {
    width: 40,
    height: 40,
    borderRadius: 20,
    backgroundColor: '#D7E8DA',
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: 10,
  },
  avatarText: { color: '#2F563C', fontSize: 13, fontWeight: '700' },
  postHeaderCopy: { flex: 1 },
  authorName: { color: '#23372B', fontSize: 14, fontWeight: '700', marginBottom: 2 },
  postMeta: { color: '#8B8176', fontSize: 12 },
  removeBtn: { padding: 8 },
  removeBtnText: { fontSize: 20, color: '#456B50' },
  postBody: { color: '#3E352C', fontSize: 15, lineHeight: 22, marginBottom: 10 },
  hashtagRow: { flexDirection: 'row', flexWrap: 'wrap', gap: 6, marginBottom: 10 },
  hashtagChip: { backgroundColor: '#EEF3EE', borderRadius: 999, paddingHorizontal: 10, paddingVertical: 5 },
  hashtagText: { color: '#2F563C', fontSize: 12, fontWeight: '700' },
  postFooter: { flexDirection: 'row', gap: 16, paddingTop: 10, borderTopWidth: 1, borderTopColor: '#EDE8E1' },
  footerMeta: { color: '#8B8176', fontSize: 13, fontWeight: '600' },
  loadMoreBtn: {
    alignSelf: 'center',
    paddingVertical: 12,
    paddingHorizontal: 28,
    borderRadius: 16,
    backgroundColor: '#EDE8E1',
    marginTop: 8,
  },
  loadMoreText: { fontSize: 14, fontWeight: '700', color: '#456B50' },
});
