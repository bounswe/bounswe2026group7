import { router } from 'expo-router';
import React, { useMemo, useState } from 'react';
import {
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

export default function SocialFeedScreen() {
  const { role } = useRole();
  const [body, setBody] = useState('');
  const [hashtagInput, setHashtagInput] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [createdPost, setCreatedPost] = useState<FeedPostResponse | null>(null);

  const hashtagPreview = useMemo(() => splitHashtags(hashtagInput), [hashtagInput]);

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
      setCreatedPost(response.data);
      setBody('');
      setHashtagInput('');
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

        {createdPost ? (
          <View style={styles.previewCard}>
            <Text style={styles.sectionLabel}>LATEST POST</Text>
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
  previewFooter: {
    color: '#6A5E52',
    fontSize: 13,
    marginTop: 16,
  },
});
