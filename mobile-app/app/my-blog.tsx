import React, { useState, useEffect, useCallback } from 'react';
import { router, useLocalSearchParams } from 'expo-router';
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  StyleSheet,
  ScrollView,
  ActivityIndicator,
  Image,
  Alert,
} from 'react-native';
import * as ImagePicker from 'expo-image-picker';
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

  // Create post state
  const [showCreate, setShowCreate] = useState(false);
  const [postBody, setPostBody] = useState('');
  const [postHashtags, setPostHashtags] = useState('');
  const [selectedImageUri, setSelectedImageUri] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

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

  const pickImage = async () => {
    const { status } = await ImagePicker.requestMediaLibraryPermissionsAsync();
    if (status !== 'granted') {
      Alert.alert('Permission needed', 'Please allow access to your photo library.');
      return;
    }
    const result = await ImagePicker.launchImageLibraryAsync({
      mediaTypes: ['images'],
      allowsEditing: false,
      quality: 0.8,
    });
    if (!result.canceled && result.assets[0]) {
      setSelectedImageUri(result.assets[0].uri);
    }
  };

  const uploadAttachment = async (uri: string): Promise<string | null> => {
    const extension = uri.split('.').pop()?.toLowerCase();
    const type = extension === 'png' ? 'image/png'
      : extension === 'webp' ? 'image/webp'
      : 'image/jpeg';
    const formData = new FormData();
    formData.append('file', { uri, name: `post-image.${extension || 'jpg'}`, type } as any);
    const res = await apiClient.post('/messages/attachments', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    return res.data?.id ?? null;
  };

  const submitPost = async () => {
    if (!postBody.trim()) return;
    setSubmitting(true);
    try {
      const hashtags = postHashtags
        .split(/[\s,#]+/)
        .map((t) => t.trim().toLowerCase())
        .filter(Boolean);

      let attachmentIds: string[] = [];
      if (selectedImageUri) {
        const id = await uploadAttachment(selectedImageUri);
        if (id) attachmentIds = [id];
      }

      await apiClient.post('/feed/posts', { body: postBody.trim(), hashtags, attachmentIds });
      setPostBody('');
      setPostHashtags('');
      setSelectedImageUri(null);
      setShowCreate(false);
      await fetchPosts(0);
    } catch (e: any) {
      Alert.alert('Error', e?.response?.data?.message ?? 'Could not create post.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => router.back()} style={styles.backButton}>
          <Text style={styles.backText}>‹ Back</Text>
        </TouchableOpacity>
        <View style={styles.headerRow}>
          <Text style={styles.title}>My Blog</Text>
          <TouchableOpacity
            style={styles.createButton}
            onPress={() => setShowCreate((v) => !v)}
          >
            <Text style={styles.createButtonText}>{showCreate ? '✕ Cancel' : '+ New Post'}</Text>
          </TouchableOpacity>
        </View>
      </View>

      {showCreate && (
        <View style={styles.createCard}>
          <TextInput
            style={styles.bodyInput}
            value={postBody}
            onChangeText={setPostBody}
            placeholder="What's on your mind?"
            placeholderTextColor="#B5ADA3"
            multiline
          />
          <TextInput
            style={styles.hashtagInput}
            value={postHashtags}
            onChangeText={setPostHashtags}
            placeholder="Hashtags (e.g. datascience ai)"
            placeholderTextColor="#B5ADA3"
          />
          {selectedImageUri && (
            <View style={styles.imagePreviewWrapper}>
              <Image source={{ uri: selectedImageUri }} style={styles.imagePreview} resizeMode="cover" />
              <TouchableOpacity style={styles.removeImageBtn} onPress={() => setSelectedImageUri(null)}>
                <Text style={styles.removeImageText}>✕</Text>
              </TouchableOpacity>
            </View>
          )}
          <View style={styles.createActions}>
            <TouchableOpacity style={styles.attachBtn} onPress={pickImage}>
              <Text style={styles.attachBtnText}>📷 Photo</Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={[styles.submitBtn, (!postBody.trim() || submitting) && { opacity: 0.5 }]}
              onPress={submitPost}
              disabled={!postBody.trim() || submitting}
            >
              {submitting ? (
                <ActivityIndicator size="small" color="#fff" />
              ) : (
                <Text style={styles.submitBtnText}>Post</Text>
              )}
            </TouchableOpacity>
          </View>
        </View>
      )}

      {loading ? (
        <ActivityIndicator size="large" color="#456B50" style={{ marginTop: 60 }} />
      ) : (
        <ScrollView contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
          {posts.length === 0 ? (
            <View style={styles.empty}>
              <Text style={styles.emptyText}>No posts yet.</Text>
              <Text style={styles.emptySubText}>Tap "New Post" to write your first post.</Text>
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
  headerRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  title: { color: '#F7F4EE', fontSize: 28, fontWeight: '700' },
  createButton: {
    backgroundColor: 'rgba(255,255,255,0.18)',
    borderRadius: 16,
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.25)',
  },
  createButtonText: { color: '#F7F4EE', fontSize: 14, fontWeight: '700' },
  createCard: {
    backgroundColor: '#F8F6F2',
    margin: 16,
    borderRadius: 22,
    padding: 18,
    borderWidth: 1,
    borderColor: '#DDD5CA',
  },
  bodyInput: {
    borderWidth: 1.5,
    borderColor: '#D8CEC0',
    borderRadius: 14,
    padding: 14,
    fontSize: 15,
    color: '#3E352C',
    minHeight: 100,
    textAlignVertical: 'top',
    marginBottom: 10,
  },
  hashtagInput: {
    borderWidth: 1.5,
    borderColor: '#D8CEC0',
    borderRadius: 14,
    padding: 12,
    fontSize: 14,
    color: '#3E352C',
    marginBottom: 10,
  },
  imagePreviewWrapper: { position: 'relative', marginBottom: 10 },
  imagePreview: { width: '100%', height: 180, borderRadius: 14 },
  removeImageBtn: {
    position: 'absolute',
    top: 8,
    right: 8,
    backgroundColor: 'rgba(0,0,0,0.55)',
    width: 28,
    height: 28,
    borderRadius: 14,
    alignItems: 'center',
    justifyContent: 'center',
  },
  removeImageText: { color: '#fff', fontSize: 13, fontWeight: '700' },
  createActions: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  attachBtn: {
    backgroundColor: '#EEF3EE',
    borderRadius: 14,
    paddingHorizontal: 16,
    paddingVertical: 10,
  },
  attachBtnText: { color: '#2F563C', fontSize: 14, fontWeight: '700' },
  submitBtn: {
    backgroundColor: '#456B50',
    borderRadius: 14,
    paddingHorizontal: 24,
    paddingVertical: 10,
  },
  submitBtnText: { color: '#fff', fontSize: 14, fontWeight: '700' },
  content: { padding: 16, paddingBottom: 48 },
  empty: { paddingTop: 60, alignItems: 'center' },
  emptyText: { color: '#6F6459', fontSize: 18, fontWeight: '700', marginBottom: 8 },
  emptySubText: { color: '#9A8F82', fontSize: 14, textAlign: 'center' },
  postCard: {
    backgroundColor: '#F8F6F2',
    borderRadius: 22,
    padding: 20,
    marginBottom: 14,
    borderWidth: 1,
    borderColor: '#DDD5CA',
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
