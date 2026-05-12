import React, { useState, useEffect, useCallback } from 'react';
import { router, useLocalSearchParams } from 'expo-router';
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  ScrollView,
  ActivityIndicator,
} from 'react-native';
import apiClient from '../api/client';
import AuthImage from '../components/AuthImage';

type PostAttachment = {
  id: string;
  downloadUrl: string;
  filename: string;
  contentType: string;
};

type BlogPost = {
  id: number;
  body: string;
  hashtags: string[];
  createdAt: string;
  likeCount: number;
  commentCount: number;
  attachments: PostAttachment[];
};

function parseString(v: string | string[] | undefined) {
  return Array.isArray(v) ? v[0] : v ?? '';
}

function formatRelativeLabel(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '';
  const diffMinutes = Math.max(1, Math.round((Date.now() - date.getTime()) / 60000));
  if (diffMinutes < 60) return `${diffMinutes}m ago`;
  const diffHours = Math.round(diffMinutes / 60);
  if (diffHours < 24) return `${diffHours}h ago`;
  return `${Math.round(diffHours / 24)}d ago`;
}

export default function MyBlogScreen() {
  const params = useLocalSearchParams();
  const authorId = parseString(params.authorId);

  const [posts, setPosts] = useState<BlogPost[]>([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [hasMore, setHasMore] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);

  const fetchPosts = useCallback(async (p = 0) => {
    if (!authorId) return;
    try {
      if (p === 0) setLoading(true);
      else setLoadingMore(true);
      const res = await apiClient.get(`/feed/users/${authorId}/posts?page=${p}&size=10`);
      const data = res.data;
      const items: BlogPost[] = data.content ?? data ?? [];
      setPosts((prev) => (p === 0 ? items : [...prev, ...items]));
      setHasMore(!data.last);
      setPage(p);
    } catch {
      // silently ignore
    } finally {
      setLoading(false);
      setLoadingMore(false);
    }
  }, [authorId]);

  useEffect(() => {
    fetchPosts(0);
  }, [fetchPosts]);

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => router.back()} style={styles.backButton}>
          <Text style={styles.backText}>‹ Back</Text>
        </TouchableOpacity>
        <Text style={styles.title}>My Blog</Text>
      </View>

      {loading ? (
        <ActivityIndicator size="large" color="#456B50" style={{ marginTop: 60 }} />
      ) : (
        <ScrollView
          contentContainerStyle={styles.content}
          showsVerticalScrollIndicator={false}
        >
          {posts.length === 0 ? (
            <View style={styles.empty}>
              <Text style={styles.emptyText}>No posts yet.</Text>
              <Text style={styles.emptySubText}>Posts you create in the feed will appear here.</Text>
            </View>
          ) : (
            <>
              {posts.map((post) => (
                <View key={post.id} style={styles.postCard}>
                  <Text style={styles.postBody}>{post.body}</Text>
                  {post.attachments?.length > 0 && post.attachments.map((att) => (
                    <AuthImage key={att.id} downloadUrl={att.downloadUrl} filename={att.filename} />
                  ))}
                  {post.hashtags.length > 0 && (
                    <View style={styles.hashtagRow}>
                      {post.hashtags.map((h) => (
                        <Text key={h} style={styles.hashtag}>#{h}</Text>
                      ))}
                    </View>
                  )}
                  <View style={styles.postFooter}>
                    <Text style={styles.postMeta}>{formatRelativeLabel(post.createdAt)}</Text>
                    <Text style={styles.postMeta}>♥ {post.likeCount}  💬 {post.commentCount}</Text>
                  </View>
                </View>
              ))}
              {hasMore && (
                <TouchableOpacity
                  style={styles.loadMoreBtn}
                  onPress={() => fetchPosts(page + 1)}
                  disabled={loadingMore}
                >
                  {loadingMore ? (
                    <ActivityIndicator size="small" color="#456B50" />
                  ) : (
                    <Text style={styles.loadMoreText}>Load more</Text>
                  )}
                </TouchableOpacity>
              )}
            </>
          )}
        </ScrollView>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#ECE8E1' },
  header: {
    backgroundColor: '#456B50',
    paddingTop: 54,
    paddingHorizontal: 24,
    paddingBottom: 24,
  },
  backButton: {
    backgroundColor: 'rgba(255,255,255,0.12)',
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.18)',
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderRadius: 16,
    alignSelf: 'flex-start',
    marginBottom: 16,
  },
  backText: { color: '#F7F4EE', fontSize: 14, fontWeight: '700' },
  title: { color: '#F7F4EE', fontSize: 28, fontWeight: '700' },
  content: { padding: 24, paddingBottom: 48 },
  empty: { paddingTop: 60, alignItems: 'center' },
  emptyText: { color: '#6F6459', fontSize: 18, fontWeight: '700', marginBottom: 8 },
  emptySubText: { color: '#9A8F82', fontSize: 14, textAlign: 'center' },
  postCard: {
    backgroundColor: '#F8F6F2',
    borderRadius: 22,
    padding: 20,
    marginBottom: 14,
  },
  postBody: { fontSize: 15, color: '#2D2D2D', lineHeight: 23, marginBottom: 12 },
  hashtagRow: { flexDirection: 'row', flexWrap: 'wrap', gap: 6, marginBottom: 12 },
  hashtag: { fontSize: 13, color: '#456B50', fontWeight: '600' },
  postFooter: { flexDirection: 'row', justifyContent: 'space-between' },
  postMeta: { fontSize: 12, color: '#A0A0A0', fontWeight: '500' },
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
