import React, { useEffect, useMemo, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  Linking,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native';
import { useLocalSearchParams } from 'expo-router';
import * as SecureStore from 'expo-secure-store';
import * as DocumentPicker from 'expo-document-picker';
import * as ImagePicker from 'expo-image-picker';
import * as FileSystem from 'expo-file-system/legacy';
import * as Sharing from 'expo-sharing';

import apiClient from '../../api/client';
import { useRole } from '../../components/RoleContext';

type ConversationListTab = 'mentorships' | 'mentorPeers';

type ConversationItem = {
  id: string;
  threadKind: 'mentorship' | 'mentorPair';
  mentorshipId?: number;
  counterpartId: number;
  counterpartName: string;
  subtitle?: string;
  preview: string;
  time: string;
  unread?: number;
  online?: boolean;
  initials: string;
  avatarBg: string;
  avatarText: string;
  type: 'mentor' | 'mentee';
};

type AttachmentSummary = {
  id: string;
  downloadUrl: string;
  filename: string;
  contentType: string;
  sizeBytes: number;
};

type ChatMessage = {
  id: string;
  sender: 'me' | 'them' | 'system';
  text: string;
  time?: string;
  attachment?: {
    id?: string;
    name: string;
    meta: string;
    downloadUrl?: string;
    contentType?: string;
  };
};

type PendingAttachment = {
  uri: string;
  name: string;
  type: string;
  size?: number;
};

function formatRelativeTime(iso?: string | null) {
  if (!iso) return '';
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return '';

  const diffMs = Date.now() - date.getTime();
  const diffMinutes = Math.floor(diffMs / 60000);
  if (diffMinutes < 1) return 'now';
  if (diffMinutes < 60) return `${diffMinutes}m`;

  const diffHours = Math.floor(diffMinutes / 60);
  if (diffHours < 24) return `${diffHours}h`;

  const diffDays = Math.floor(diffHours / 24);
  return `${diffDays}d`;
}

function formatClock(iso?: string | null) {
  if (!iso) return '';
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return '';
  return date.toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' });
}

function formatAttachmentMeta(sizeBytes?: number, contentType?: string) {
  const size = typeof sizeBytes === 'number'
    ? sizeBytes >= 1024 * 1024
      ? `${(sizeBytes / (1024 * 1024)).toFixed(1)} MB`
      : `${Math.max(1, Math.round(sizeBytes / 1024))} KB`
    : 'Unknown size';

  const kind = contentType
    ? contentType.includes('pdf')
      ? 'PDF'
      : contentType.includes('word')
      ? 'DOCX'
      : contentType.includes('text')
      ? 'TXT'
      : contentType.includes('image')
      ? 'Image'
      : contentType.toUpperCase()
    : 'File';

  return `${size} · ${kind}`;
}

function getInitials(name: string) {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return 'U';
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return `${parts[0][0]}${parts[parts.length - 1][0]}`.toUpperCase();
}

function avatarPalette(index: number) {
  const palette = [
    { bg: '#D4E8DC', text: '#2D5A3D' },
    { bg: '#E8E4D4', text: '#5A4E2D' },
    { bg: '#D4DCE8', text: '#2D3A5A' },
    { bg: '#E8D4DC', text: '#5A2D3A' },
  ];
  return palette[index % palette.length];
}

function mapMessages(rawMessages: any[], currentUserId: number): ChatMessage[] {
  const ordered = [...rawMessages].reverse();
  const messages: ChatMessage[] = [];
  let lastDayLabel = '';

  for (const raw of ordered) {
    const sentAt = raw.sentAt ? new Date(raw.sentAt) : null;
    const dayLabel = sentAt && !Number.isNaN(sentAt.getTime())
      ? sentAt.toLocaleDateString('en-GB', { day: 'numeric', month: 'short' })
      : '';

    if (dayLabel && dayLabel !== lastDayLabel) {
      messages.push({
        id: `day-${dayLabel}`,
        sender: 'system',
        text: dayLabel,
      });
      lastDayLabel = dayLabel;
    }

    messages.push({
      id: String(raw.id),
      sender: raw.senderId === currentUserId ? 'me' : 'them',
      text: raw.content,
      time: formatClock(raw.sentAt),
      attachment: raw.attachment
        ? {
            id: raw.attachment.id,
            name: raw.attachment.filename,
            meta: formatAttachmentMeta(raw.attachment.sizeBytes, raw.attachment.contentType),
            downloadUrl: raw.attachment.downloadUrl,
            contentType: raw.attachment.contentType,
          }
        : undefined,
    });
  }

  return messages;
}

export default function MessagesScreen() {
  const { role } = useRole();
  const isMentor = role === 'mentor';
  const params = useLocalSearchParams();

  const [search, setSearch] = useState('');
  const [draft, setDraft] = useState('');
  const [activeListTab, setActiveListTab] = useState<ConversationListTab>('mentorships');
  const [currentUserId, setCurrentUserId] = useState<number | null>(null);
  const [mentorshipConversations, setMentorshipConversations] = useState<ConversationItem[]>([]);
  const [peerMentorConversations, setPeerMentorConversations] = useState<ConversationItem[]>([]);
  const [mentorDirectoryOptions, setMentorDirectoryOptions] = useState<ConversationItem[]>([]);
  const [selectedConversation, setSelectedConversation] = useState<ConversationItem | null>(null);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [listLoading, setListLoading] = useState(true);
  const [threadLoading, setThreadLoading] = useState(false);
  const [sending, setSending] = useState(false);
  const [openingAttachmentId, setOpeningAttachmentId] = useState<string | null>(null);
  const [pendingAttachment, setPendingAttachment] = useState<PendingAttachment | null>(null);

  useEffect(() => {
    const loadConversations = async () => {
      setListLoading(true);
      try {
        const storedUserId = await SecureStore.getItemAsync('userId');
        const parsedUserId = storedUserId ? Number(storedUserId) : null;
        setCurrentUserId(parsedUserId);

        const mentorshipsRes = await apiClient.get('/mentorships');
        const mentorships = mentorshipsRes.data ?? [];

        const mentorshipThreads = await Promise.all(
          mentorships.map(async (mentorship: any, index: number) => {
            const counterpartName = isMentor
              ? mentorship.menteeFirstName
              : mentorship.mentorFirstName;
            const counterpartId = isMentor
              ? mentorship.menteeId
              : mentorship.mentorId;
            const colors = avatarPalette(index);

            let preview = 'No messages yet';
            let time = '';
            try {
              const threadRes = await apiClient.get(`/mentorships/${mentorship.id}/messages?page=0&size=1`);
              const latest = threadRes.data?.content?.[0];
              if (latest) {
                preview = latest.attachment
                  ? `${latest.content || 'Attachment'} · ${latest.attachment.filename}`
                  : latest.content;
                time = formatRelativeTime(latest.sentAt);
              }
            } catch {
              // Keep list usable even if preview fetch fails for one mentorship.
            }

            return {
              id: `mentorship-${mentorship.id}`,
              threadKind: 'mentorship',
              mentorshipId: mentorship.id,
              counterpartId,
              counterpartName: counterpartName || 'Unknown User',
              subtitle: isMentor ? 'Your Mentee' : 'Your Mentor',
              preview,
              time,
              unread: 0,
              online: false,
              initials: getInitials(counterpartName || 'Unknown User'),
              avatarBg: colors.bg,
              avatarText: colors.text,
              type: isMentor ? 'mentee' : 'mentor',
            } satisfies ConversationItem;
          })
        );

        setMentorshipConversations(mentorshipThreads);

        if (isMentor && parsedUserId != null) {
          const [peerInboxRes, mentorsRes] = await Promise.all([
            apiClient.get('/conversations/mentor-pair?page=0&size=100'),
            apiClient.get('/users/mentors/all'),
          ]);

          const peerInboxItems = peerInboxRes.data?.content ?? [];
          const peerInbox = peerInboxItems.map((conversation: any, index: number) => {
            const colors = avatarPalette(index + mentorshipThreads.length);
            return {
              id: `mentor-pair-${conversation.peerId}`,
              threadKind: 'mentorPair',
              counterpartId: Number(conversation.peerId),
              counterpartName: conversation.peerFirstName || 'Unknown Mentor',
              subtitle: 'Peer Mentor',
              preview: conversation.lastMessageContent || 'No messages yet',
              time: formatRelativeTime(conversation.lastMessageSentAt),
              unread: Number(conversation.unreadCount ?? 0),
              online: false,
              initials: getInitials(conversation.peerFirstName || 'Unknown Mentor'),
              avatarBg: colors.bg,
              avatarText: colors.text,
              type: 'mentor',
            } satisfies ConversationItem;
          });
          setPeerMentorConversations(peerInbox);

          const allMentors = mentorsRes.data ?? [];
          const peerMentors = allMentors
            .filter((mentor: any) => Number(mentor.id) !== parsedUserId)
            .map((mentor: any, index: number) => {
              const fullName = mentor.lastName
                ? `${mentor.firstName} ${mentor.lastName}`
                : mentor.firstName || 'Unknown Mentor';
              const colors = avatarPalette(index + mentorshipThreads.length + peerInbox.length);

              return {
                id: `mentor-pair-${mentor.id}`,
                threadKind: 'mentorPair',
                counterpartId: Number(mentor.id),
                counterpartName: fullName,
                subtitle: mentor.field || mentor.expertise || 'Peer Mentor',
                preview: 'Open a peer conversation with this mentor.',
                time: '',
                unread: 0,
                online: false,
                initials: getInitials(fullName),
                avatarBg: colors.bg,
                avatarText: colors.text,
                type: 'mentor',
              } satisfies ConversationItem;
            });
          setMentorDirectoryOptions(peerMentors);
        } else {
          setPeerMentorConversations([]);
          setMentorDirectoryOptions([]);
        }
      } catch (error) {
        console.error('Failed to load conversations:', error);
        Alert.alert('Error', 'Could not load your conversations.');
      } finally {
        setListLoading(false);
      }
    };

    loadConversations();
  }, [isMentor]);

  useEffect(() => {
    const openWith = Array.isArray(params.openWith) ? params.openWith[0] : params.openWith;
    const allConversations = [...mentorshipConversations, ...peerMentorConversations];
    if (!openWith || allConversations.length === 0) return;

    const match = allConversations.find((conversation) =>
      conversation.counterpartName.toLowerCase().includes(String(openWith).toLowerCase())
    );
    if (match) {
      setSelectedConversation(match);
    }
  }, [params.openWith, mentorshipConversations, peerMentorConversations]);

  useEffect(() => {
    const loadMessages = async () => {
      if (!selectedConversation || currentUserId == null) {
        return;
      }

      setThreadLoading(true);
      try {
        const endpoint = selectedConversation.threadKind === 'mentorship'
          ? `/mentorships/${selectedConversation.mentorshipId}/messages?page=0&size=100`
          : `/conversations/mentor-pair/${selectedConversation.counterpartId}/messages?page=0&size=100`;
        const readEndpoint = selectedConversation.threadKind === 'mentorship'
          ? `/mentorships/${selectedConversation.mentorshipId}/messages/read`
          : `/conversations/mentor-pair/${selectedConversation.counterpartId}/messages/read`;

        const res = await apiClient.get(endpoint);
        const rawMessages = res.data?.content ?? [];
        setMessages(mapMessages(rawMessages, currentUserId));
        await apiClient.patch(readEndpoint).catch(() => undefined);
      } catch (error) {
        console.error('Failed to load thread:', error);
        Alert.alert('Error', 'Could not load the message thread.');
      } finally {
        setThreadLoading(false);
      }
    };

    loadMessages();
  }, [selectedConversation, currentUserId]);

  const visibleConversations = useMemo(() => {
    if (!isMentor) {
      return mentorshipConversations;
    }
    return activeListTab === 'mentorships'
      ? mentorshipConversations
      : peerMentorConversations;
  }, [activeListTab, isMentor, mentorshipConversations, peerMentorConversations]);

  const filteredMentorDirectoryOptions = useMemo(() => {
    if (!isMentor || activeListTab !== 'mentorPeers') {
      return [];
    }

    const existingPeerIds = new Set(peerMentorConversations.map((item) => item.counterpartId));
    const availableMentors = mentorDirectoryOptions.filter(
      (item) => !existingPeerIds.has(item.counterpartId)
    );

    const q = search.trim().toLowerCase();
    if (!q) {
      return availableMentors;
    }

    return availableMentors.filter(
      (item) =>
        item.counterpartName.toLowerCase().includes(q) ||
        item.subtitle?.toLowerCase().includes(q) ||
        item.preview.toLowerCase().includes(q)
    );
  }, [activeListTab, isMentor, mentorDirectoryOptions, peerMentorConversations, search]);

  const filteredConversations = useMemo(() => {
    const q = search.trim().toLowerCase();
    if (!q) return visibleConversations;
    return visibleConversations.filter(
      (item) =>
        item.counterpartName.toLowerCase().includes(q) ||
        item.preview.toLowerCase().includes(q) ||
        item.subtitle?.toLowerCase().includes(q)
    );
  }, [visibleConversations, search]);

  const titleLine = isMentor ? 'Your mentees and mentor peers.' : 'Your mentor.';

  const pickImageAttachment = async () => {
    const permission = await ImagePicker.requestMediaLibraryPermissionsAsync();
    if (permission.status !== 'granted') {
      Alert.alert('Permission needed', 'Please allow photo library access to attach images.');
      return;
    }

    const result = await ImagePicker.launchImageLibraryAsync({
      mediaTypes: ['images'],
      allowsEditing: false,
      quality: 0.8,
    });

    if (result.canceled || !result.assets?.[0]) {
      return;
    }

    const asset = result.assets[0];
    setPendingAttachment({
      uri: asset.uri,
      name: asset.fileName || `image-${Date.now()}.jpg`,
      type: asset.mimeType || 'image/jpeg',
      size: asset.fileSize,
    });
  };

  const pickDocumentAttachment = async () => {
    const result = await DocumentPicker.getDocumentAsync({
      type: [
        'application/pdf',
        'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
        'text/plain',
        'image/*',
      ],
      copyToCacheDirectory: true,
      multiple: false,
    });

    if (result.canceled || !result.assets?.[0]) {
      return;
    }

    const asset = result.assets[0];
    setPendingAttachment({
      uri: asset.uri,
      name: asset.name,
      type: asset.mimeType || 'application/octet-stream',
      size: asset.size,
    });
  };

  const chooseAttachment = () => {
    Alert.alert('Attach file', 'Choose what you want to attach.', [
      { text: 'Photo', onPress: pickImageAttachment },
      { text: 'Document', onPress: pickDocumentAttachment },
      { text: 'Cancel', style: 'cancel' },
    ]);
  };

  const sendMessage = async () => {
    if (!selectedConversation || sending) {
      return;
    }

    const trimmedDraft = draft.trim();
    if (!trimmedDraft && !pendingAttachment) {
      return;
    }

    setSending(true);
    try {
      let uploadedAttachment: AttachmentSummary | null = null;

      if (pendingAttachment) {
        const formData = new FormData();
        formData.append('file', {
          uri: pendingAttachment.uri,
          name: pendingAttachment.name,
          type: pendingAttachment.type,
        } as any);

        const uploadRes = await apiClient.post('/messages/attachments', formData, {
          headers: {
            'Content-Type': 'multipart/form-data',
          },
        });
        uploadedAttachment = uploadRes.data;
      }

      const content = trimmedDraft || pendingAttachment?.name || 'Attachment';
      const messageEndpoint = selectedConversation.threadKind === 'mentorship'
        ? `/mentorships/${selectedConversation.mentorshipId}/messages`
        : `/conversations/mentor-pair/${selectedConversation.counterpartId}/messages`;

      const messageRes = await apiClient.post(messageEndpoint, {
        content,
        ...(uploadedAttachment ? { attachmentId: uploadedAttachment.id } : {}),
      });

      const rawMessage = messageRes.data;
      const newMessages = mapMessages([rawMessage], currentUserId ?? -1).filter((m) => m.sender !== 'system');
      setMessages((prev) => [...prev, ...newMessages]);

      const applyPreviewUpdate = (items: ConversationItem[]) =>
        items.map((conversation) =>
          conversation.id === selectedConversation.id
            ? {
                ...conversation,
                preview: uploadedAttachment
                  ? `${content} · ${uploadedAttachment.filename}`
                  : content,
                time: 'now',
              }
            : conversation
        );

      if (selectedConversation.threadKind === 'mentorship') {
        setMentorshipConversations((prev) => applyPreviewUpdate(prev));
      } else {
        setPeerMentorConversations((prev) => applyPreviewUpdate(prev));
      }

      setDraft('');
      setPendingAttachment(null);
    } catch (error: any) {
      const message =
        error.response?.data?.message ||
        error.response?.data?.error ||
        'Could not send the message.';
      Alert.alert('Error', message);
    } finally {
      setSending(false);
    }
  };

  const openAttachment = async (attachment: NonNullable<ChatMessage['attachment']>) => {
    if (!attachment.downloadUrl || !attachment.id || openingAttachmentId) {
      return;
    }

    const token = await SecureStore.getItemAsync('userToken');
    if (!token) {
      Alert.alert('Error', 'You need to sign in again to open attachments.');
      return;
    }

    setOpeningAttachmentId(attachment.id);
    try {
      const targetPath = `${FileSystem.cacheDirectory}${attachment.name}`;
      const result = await FileSystem.downloadAsync(attachment.downloadUrl, targetPath, {
        headers: {
          Authorization: `Bearer ${token}`,
        },
      });

      const canShare = await Sharing.isAvailableAsync();
      if (canShare) {
        await Sharing.shareAsync(result.uri);
      } else {
        await Linking.openURL(result.uri);
      }
    } catch (error) {
      console.error('Failed to open attachment:', error);
      Alert.alert('Error', 'Could not open the attachment.');
    } finally {
      setOpeningAttachmentId(null);
    }
  };

  if (selectedConversation) {
    return (
      <View style={styles.container}>
        <View style={styles.header}>
          <View style={styles.bgBlobLarge} />
          <View style={styles.bgBlobSmall} />

          <View style={styles.statusRow}>
            <Text style={styles.statusText}>{new Date().toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit', hour12: false })}</Text>
            <Text style={styles.statusText}>▲ ▮</Text>
          </View>

          <View style={styles.chatHeaderRow}>
            <TouchableOpacity
              onPress={() => setSelectedConversation(null)}
              accessibilityRole="button"
              accessibilityLabel="Back to conversation list"
              hitSlop={8}
            >
              <Text style={styles.backArrow}>‹</Text>
            </TouchableOpacity>

            <View style={styles.chatAvatarWrap}>
              <View style={[styles.avatar, { backgroundColor: selectedConversation.avatarBg }]}>
                <Text style={[styles.avatarText, { color: selectedConversation.avatarText }]}>
                  {selectedConversation.initials}
                </Text>
              </View>
            </View>

            <View style={styles.chatHeaderInfo}>
              <Text style={styles.chatHeaderName}>{selectedConversation.counterpartName}</Text>
              <Text style={styles.chatHeaderSub}>{selectedConversation.subtitle}</Text>
            </View>
          </View>
        </View>

        <View style={styles.contextBar}>
          <Text
            style={styles.badgeSage}
            accessibilityLabel={
              selectedConversation.threadKind === 'mentorPair'
                ? 'Peer mentor chat'
                : 'Mentorship chat'
            }
          >
            {selectedConversation.threadKind === 'mentorPair' ? 'Peer Mentor Chat' : 'Mentorship Chat'}
          </Text>
          <Text style={styles.contextText}>
            {selectedConversation.threadKind === 'mentorPair'
              ? 'Private mentor-to-mentor conversation with attachment support'
              : 'Real messages and attachment support'}
          </Text>
        </View>

        {threadLoading ? (
          <View style={styles.centeredState}>
            <ActivityIndicator size="large" color="#3D6B52" />
            <Text style={styles.stateText}>Loading messages...</Text>
          </View>
        ) : (
          <ScrollView
            style={styles.chatScroll}
            contentContainerStyle={styles.chatScrollContent}
            showsVerticalScrollIndicator={false}
          >
            {messages.length === 0 ? (
              <View style={styles.emptyThreadCard}>
                <Text style={styles.emptyThreadTitle}>No messages yet</Text>
                <Text style={styles.emptyThreadText}>
                  Start the conversation by sending a message or attaching a file.
                </Text>
              </View>
            ) : (
              messages.map((message) => {
                if (message.sender === 'system') {
                  return (
                    <Text key={message.id} style={styles.systemMessage}>
                      {message.text}
                    </Text>
                  );
                }

                const isMe = message.sender === 'me';

                return (
                  <View
                    key={message.id}
                    style={[
                      styles.messageWrap,
                      isMe ? styles.messageWrapRight : styles.messageWrapLeft,
                    ]}
                  >
                    <View style={[styles.bubble, isMe ? styles.bubbleMe : styles.bubbleThem]}>
                      <Text style={[styles.bubbleText, isMe && styles.bubbleTextMe]}>
                        {message.text}
                      </Text>

                      {message.attachment ? (
                        <TouchableOpacity
                          style={styles.attachmentPill}
                          onPress={() => openAttachment(message.attachment!)}
                          disabled={openingAttachmentId === message.attachment.id}
                          accessibilityRole="button"
                          accessibilityLabel={`Open attachment ${message.attachment.name}`}
                          accessibilityHint={
                            openingAttachmentId === message.attachment.id
                              ? 'Attachment is opening'
                              : 'Opens the shared attachment'
                          }
                        >
                          <Text style={styles.attachmentIcon}>
                            {message.attachment.contentType?.includes('image') ? '🖼️' : '📄'}
                          </Text>
                          <View style={styles.attachmentTextWrap}>
                            <Text style={styles.attachmentTitle}>{message.attachment.name}</Text>
                            <Text style={styles.attachmentMeta}>
                              {openingAttachmentId === message.attachment.id
                                ? 'Opening...'
                                : message.attachment.meta}
                            </Text>
                          </View>
                        </TouchableOpacity>
                      ) : null}
                    </View>

                    {message.time ? (
                      <Text
                        style={[
                          styles.messageTime,
                          isMe ? styles.messageTimeRight : styles.messageTimeLeft,
                        ]}
                      >
                        {message.time}
                      </Text>
                    ) : null}
                  </View>
                );
              })
            )}
          </ScrollView>
        )}

        {pendingAttachment ? (
          <View style={styles.pendingAttachmentBar}>
            <View style={styles.pendingAttachmentInfo}>
              <Text style={styles.pendingAttachmentTitle}>{pendingAttachment.name}</Text>
              <Text style={styles.pendingAttachmentMeta}>
                {formatAttachmentMeta(pendingAttachment.size, pendingAttachment.type)}
              </Text>
            </View>
            <TouchableOpacity
              onPress={() => setPendingAttachment(null)}
              accessibilityRole="button"
              accessibilityLabel={`Remove attachment ${pendingAttachment.name}`}
              hitSlop={8}
            >
              <Text style={styles.pendingAttachmentRemove}>✕</Text>
            </TouchableOpacity>
          </View>
        ) : null}

        <View style={styles.inputBar}>
          <TouchableOpacity
            onPress={chooseAttachment}
            disabled={sending}
            accessibilityRole="button"
            accessibilityLabel="Add attachment"
            accessibilityHint="Choose a photo or document to attach"
            accessibilityState={{ disabled: sending }}
            hitSlop={8}
          >
            <Text style={styles.inputIcon}>📎</Text>
          </TouchableOpacity>
          <TextInput
            value={draft}
            onChangeText={setDraft}
            placeholder={`Message ${selectedConversation.counterpartName.split(' ')[0]}...`}
            placeholderTextColor="#B7B0A4"
            style={styles.input}
            multiline
            accessibilityLabel={`Message ${selectedConversation.counterpartName}`}
            accessibilityHint="Type your message here"
          />
          <TouchableOpacity
            style={[styles.sendButton, sending && styles.sendButtonDisabled]}
            onPress={sendMessage}
            disabled={sending}
            accessibilityRole="button"
            accessibilityLabel="Send message"
            accessibilityState={{ disabled: sending, busy: sending }}
          >
            <Text style={styles.sendButtonText}>{sending ? '…' : '➤'}</Text>
          </TouchableOpacity>
        </View>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <View style={styles.bgBlobLarge} />
        <View style={styles.bgBlobSmall} />

        <View style={styles.statusRow}>
          <Text style={styles.statusText}>{new Date().toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit', hour12: false })}</Text>
          <Text style={styles.statusText}>▲ ▮</Text>
        </View>

        <View style={styles.headerContent}>
          <Text style={styles.title}>Messages</Text>
          <Text style={styles.titleItalic}>{titleLine}</Text>

          {isMentor ? (
            <View style={styles.listTabRow}>
              <TouchableOpacity
                style={[
                  styles.listTabButton,
                  activeListTab === 'mentorships' && styles.listTabButtonActive,
                ]}
                onPress={() => setActiveListTab('mentorships')}
                accessibilityRole="tab"
                accessibilityLabel="Mentorship conversations tab"
                accessibilityState={{ selected: activeListTab === 'mentorships' }}
              >
                <Text
                  style={[
                    styles.listTabButtonText,
                    activeListTab === 'mentorships' && styles.listTabButtonTextActive,
                  ]}
                >
                  Active Mentees
                </Text>
              </TouchableOpacity>
              <TouchableOpacity
                style={[
                  styles.listTabButton,
                  activeListTab === 'mentorPeers' && styles.listTabButtonActive,
                ]}
                onPress={() => setActiveListTab('mentorPeers')}
                accessibilityRole="tab"
                accessibilityLabel="Peer mentor conversations tab"
                accessibilityState={{ selected: activeListTab === 'mentorPeers' }}
              >
                <Text
                  style={[
                    styles.listTabButtonText,
                    activeListTab === 'mentorPeers' && styles.listTabButtonTextActive,
                  ]}
                >
                  Mentor Network
                </Text>
              </TouchableOpacity>
            </View>
          ) : null}

          <View style={styles.searchBar}>
            <Text style={styles.searchIcon}>🔍</Text>
            <TextInput
              value={search}
              onChangeText={setSearch}
              placeholder={
                isMentor && activeListTab === 'mentorPeers'
                  ? 'Search mentor peers...'
                  : isMentor
                  ? 'Search conversations...'
                  : 'Search messages...'
              }
              placeholderTextColor="rgba(255,255,255,0.45)"
              style={styles.searchInput}
              accessibilityLabel="Search conversations"
              accessibilityHint="Filters the visible conversation list"
            />
          </View>
        </View>
      </View>

      {listLoading ? (
        <View style={styles.centeredState}>
          <ActivityIndicator size="large" color="#3D6B52" />
          <Text style={styles.stateText}>Loading conversations...</Text>
        </View>
      ) : (
        <ScrollView showsVerticalScrollIndicator={false} contentContainerStyle={styles.listScroll}>
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>
              {isMentor && activeListTab === 'mentorPeers' ? 'Mentor Network' : 'Active Mentorships'}
            </Text>
            <View style={styles.cardList}>
              {filteredConversations.length === 0 ? (
                <View style={styles.emptyConversationBlock}>
                  <Text style={styles.emptyConversationTitle}>No conversations found</Text>
                  <Text style={styles.emptyConversationText}>
                    {isMentor && activeListTab === 'mentorPeers'
                      ? 'No existing mentor-to-mentor conversations matched your search.'
                      : 'Once you have an active mentorship, your chat threads will appear here.'}
                  </Text>
                </View>
              ) : (
                filteredConversations.map((conversation) => (
                  <ConversationRow
                    key={conversation.id}
                    item={conversation}
                    onPress={() => setSelectedConversation(conversation)}
                  />
                ))
              )}
            </View>
          </View>

          {isMentor && activeListTab === 'mentorPeers' ? (
            <View style={styles.section}>
              <Text style={styles.sectionTitle}>Start New Conversation</Text>
              <View style={styles.cardList}>
                {filteredMentorDirectoryOptions.length === 0 ? (
                  <View style={styles.emptyConversationBlock}>
                    <Text style={styles.emptyConversationTitle}>No mentors available</Text>
                    <Text style={styles.emptyConversationText}>
                      Every visible mentor is already in your mentor-pair inbox, or none matched your search.
                    </Text>
                  </View>
                ) : (
                  filteredMentorDirectoryOptions.map((conversation) => (
                    <ConversationRow
                      key={`directory-${conversation.counterpartId}`}
                      item={conversation}
                      onPress={() => setSelectedConversation(conversation)}
                    />
                  ))
                )}
              </View>
            </View>
          ) : null}

          <View style={styles.tipCard}>
            <Text style={styles.tipLabel}>
              {isMentor && activeListTab === 'mentorPeers' ? 'Peer Messaging' : 'Attachment Support'}
            </Text>
            <Text style={styles.tipText}>
              {isMentor && activeListTab === 'mentorPeers'
                ? 'Mentors can start private peer conversations here and reuse the same attachment-enabled chat flow.'
                : 'You can now attach images, PDF files, DOCX files, and TXT files directly from the chat composer.'}
            </Text>
          </View>
        </ScrollView>
      )}
    </View>
  );
}

function ConversationRow({
  item,
  onPress,
}: {
  item: ConversationItem;
  onPress: () => void;
}) {
  return (
    <TouchableOpacity
      style={styles.conversationRow}
      onPress={onPress}
      accessibilityRole="button"
      accessibilityLabel={`${item.counterpartName}. ${item.subtitle ? `${item.subtitle}. ` : ''}${item.preview}. ${item.unread && item.unread > 0 ? `${item.unread} unread messages.` : 'No unread messages.'}`}
      accessibilityHint="Opens the conversation thread"
    >
      <View style={styles.avatarWrap}>
        <View style={[styles.avatar, { backgroundColor: item.avatarBg }]}>
          <Text style={[styles.avatarText, { color: item.avatarText }]}>{item.initials}</Text>
        </View>
      </View>

      <View style={styles.conversationBody}>
        <View style={styles.conversationTop}>
          <View style={styles.conversationTitleWrap}>
            <Text style={styles.conversationName}>{item.counterpartName}</Text>
            {item.subtitle ? <Text style={styles.conversationSubtitle}>{item.subtitle}</Text> : null}
          </View>
          <Text style={styles.conversationTime}>{item.time}</Text>
        </View>

        <Text numberOfLines={1} style={styles.conversationPreview}>
          {item.preview}
        </Text>
      </View>
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#F5F0E8' },
  header: {
    backgroundColor: '#3D5C4A',
    paddingTop: 54,
    paddingBottom: 16,
    overflow: 'hidden',
  },
  bgBlobLarge: {
    position: 'absolute',
    width: 180,
    height: 180,
    borderRadius: 999,
    backgroundColor: '#5A8C6E',
    opacity: 0.2,
    top: -50,
    right: -40,
  },
  bgBlobSmall: {
    position: 'absolute',
    width: 120,
    height: 120,
    borderRadius: 999,
    backgroundColor: '#6FA586',
    opacity: 0.14,
    top: 10,
    left: -50,
  },
  statusRow: {
    paddingHorizontal: 18,
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 8,
  },
  statusText: {
    color: '#FFFFFF',
    fontSize: 14,
    fontWeight: '700',
  },
  headerContent: {
    paddingHorizontal: 18,
  },
  title: {
    color: '#FFFFFF',
    fontSize: 30,
    fontWeight: '700',
  },
  titleItalic: {
    color: 'rgba(255,255,255,0.75)',
    fontSize: 20,
    fontStyle: 'italic',
    marginTop: -2,
  },
  searchBar: {
    marginTop: 12,
    borderRadius: 14,
    backgroundColor: 'rgba(255,255,255,0.12)',
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.18)',
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 12,
    height: 42,
  },
  searchIcon: {
    marginRight: 8,
    color: 'rgba(255,255,255,0.6)',
    fontSize: 14,
  },
  searchInput: {
    flex: 1,
    color: '#FFFFFF',
    fontSize: 13,
  },
  listScroll: {
    paddingTop: 10,
    paddingBottom: 28,
  },
  listTabRow: {
    flexDirection: 'row',
    marginTop: 12,
    marginBottom: 2,
  },
  listTabButton: {
    paddingHorizontal: 12,
    paddingVertical: 7,
    borderRadius: 999,
    marginRight: 8,
    backgroundColor: 'rgba(255,255,255,0.12)',
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.18)',
  },
  listTabButtonActive: {
    backgroundColor: '#D4E8DC',
    borderColor: '#D4E8DC',
  },
  listTabButtonText: {
    color: 'rgba(255,255,255,0.8)',
    fontSize: 11,
    fontWeight: '600',
  },
  listTabButtonTextActive: {
    color: '#2D5A3D',
  },
  section: {
    paddingHorizontal: 14,
    marginTop: 12,
  },
  sectionTitle: {
    color: '#8A8278',
    fontSize: 11,
    fontWeight: '700',
    textTransform: 'uppercase',
    marginBottom: 8,
    letterSpacing: 0.8,
  },
  cardList: {
    backgroundColor: '#FFFFFF',
    borderWidth: 1,
    borderColor: '#EDE8DF',
    borderRadius: 18,
    overflow: 'hidden',
  },
  conversationRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 14,
    paddingVertical: 12,
    borderBottomWidth: 1,
    borderBottomColor: '#F0EBE3',
  },
  avatarWrap: {
    marginRight: 11,
    position: 'relative',
  },
  avatar: {
    width: 44,
    height: 44,
    borderRadius: 999,
    alignItems: 'center',
    justifyContent: 'center',
  },
  avatarText: {
    fontSize: 13,
    fontWeight: '700',
  },
  conversationBody: {
    flex: 1,
  },
  conversationTop: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 3,
  },
  conversationTitleWrap: {
    flex: 1,
    paddingRight: 8,
  },
  conversationName: {
    color: '#1A2E22',
    fontSize: 13,
    fontWeight: '700',
  },
  conversationSubtitle: {
    color: '#9A9288',
    fontSize: 10,
    marginTop: 1,
  },
  conversationTime: {
    color: '#BBB4A8',
    fontSize: 10,
  },
  conversationPreview: {
    color: '#9A9288',
    fontSize: 11,
  },
  centeredState: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 24,
  },
  stateText: {
    marginTop: 12,
    color: '#7E7368',
    fontSize: 14,
  },
  emptyConversationBlock: {
    padding: 18,
    alignItems: 'center',
  },
  emptyConversationTitle: {
    color: '#2D4D3A',
    fontSize: 14,
    fontWeight: '700',
    marginBottom: 6,
  },
  emptyConversationText: {
    color: '#9A9288',
    fontSize: 12,
    lineHeight: 18,
    textAlign: 'center',
  },
  tipCard: {
    marginHorizontal: 14,
    marginTop: 12,
    backgroundColor: '#D4E8DC',
    borderWidth: 1,
    borderColor: '#B8D8C4',
    borderRadius: 14,
    padding: 14,
  },
  tipLabel: {
    color: '#2D5A3D',
    fontSize: 11,
    fontWeight: '700',
    textTransform: 'uppercase',
    marginBottom: 5,
  },
  tipText: {
    color: '#2D4D3A',
    fontSize: 12,
    lineHeight: 18,
  },
  chatHeaderRow: {
    paddingHorizontal: 16,
    flexDirection: 'row',
    alignItems: 'center',
  },
  backArrow: {
    color: 'rgba(255,255,255,0.85)',
    fontSize: 28,
    marginRight: 8,
  },
  chatAvatarWrap: {
    position: 'relative',
    marginRight: 10,
  },
  chatHeaderInfo: {
    flex: 1,
  },
  chatHeaderName: {
    color: '#FFFFFF',
    fontSize: 13,
    fontWeight: '700',
  },
  chatHeaderSub: {
    color: '#9FD4B2',
    fontSize: 10,
    marginTop: 1,
  },
  contextBar: {
    minHeight: 40,
    backgroundColor: '#F0EBE3',
    borderBottomWidth: 1,
    borderBottomColor: '#E5E0D8',
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 14,
    paddingVertical: 8,
  },
  badgeSage: {
    backgroundColor: '#D4E8DC',
    color: '#2D5A3D',
    fontSize: 10,
    fontWeight: '600',
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderRadius: 8,
    overflow: 'hidden',
  },
  contextText: {
    color: '#9A9288',
    fontSize: 10,
    marginLeft: 8,
  },
  chatScroll: {
    flex: 1,
  },
  chatScrollContent: {
    paddingHorizontal: 13,
    paddingVertical: 12,
  },
  emptyThreadCard: {
    backgroundColor: '#FFFFFF',
    borderWidth: 1,
    borderColor: '#EDE8DF',
    borderRadius: 14,
    padding: 16,
    alignItems: 'center',
  },
  emptyThreadTitle: {
    color: '#2D4D3A',
    fontSize: 14,
    fontWeight: '700',
    marginBottom: 6,
  },
  emptyThreadText: {
    color: '#9A9288',
    fontSize: 12,
    lineHeight: 18,
    textAlign: 'center',
  },
  systemMessage: {
    alignSelf: 'center',
    color: '#B6AEA2',
    fontSize: 10,
    fontStyle: 'italic',
    marginVertical: 6,
  },
  messageWrap: {
    marginBottom: 8,
    maxWidth: '82%',
  },
  messageWrapLeft: {
    alignSelf: 'flex-start',
  },
  messageWrapRight: {
    alignSelf: 'flex-end',
  },
  bubble: {
    paddingHorizontal: 12,
    paddingVertical: 10,
  },
  bubbleThem: {
    backgroundColor: '#FFFFFF',
    borderWidth: 1,
    borderColor: '#EDE8DF',
    borderRadius: 16,
    borderBottomLeftRadius: 4,
  },
  bubbleMe: {
    backgroundColor: '#3D6B52',
    borderRadius: 16,
    borderBottomRightRadius: 4,
  },
  bubbleText: {
    color: '#2A2A2A',
    fontSize: 12,
    lineHeight: 18,
  },
  bubbleTextMe: {
    color: '#FFFFFF',
  },
  messageTime: {
    fontSize: 10,
    color: '#B6AEA2',
    marginTop: 3,
  },
  messageTimeLeft: {
    marginLeft: 4,
  },
  messageTimeRight: {
    textAlign: 'right',
    marginRight: 4,
  },
  attachmentPill: {
    marginTop: 7,
    borderRadius: 11,
    borderWidth: 1,
    borderColor: '#C8DDD2',
    backgroundColor: '#E8F0EC',
    paddingHorizontal: 11,
    paddingVertical: 8,
    flexDirection: 'row',
    alignItems: 'center',
  },
  attachmentIcon: {
    fontSize: 15,
    marginRight: 7,
  },
  attachmentTextWrap: {
    flex: 1,
  },
  attachmentTitle: {
    color: '#2D5A3D',
    fontSize: 11,
    fontWeight: '700',
  },
  attachmentMeta: {
    color: '#5A8C6E',
    fontSize: 10,
    marginTop: 1,
  },
  pendingAttachmentBar: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#ECF3EE',
    borderTopWidth: 1,
    borderTopColor: '#D6E3DA',
    paddingHorizontal: 14,
    paddingVertical: 10,
  },
  pendingAttachmentInfo: {
    flex: 1,
  },
  pendingAttachmentTitle: {
    color: '#2D5A3D',
    fontSize: 12,
    fontWeight: '700',
  },
  pendingAttachmentMeta: {
    color: '#6D7E72',
    fontSize: 10,
    marginTop: 2,
  },
  pendingAttachmentRemove: {
    color: '#7E7368',
    fontSize: 18,
    fontWeight: '700',
    paddingHorizontal: 6,
  },
  inputBar: {
    borderTopWidth: 1,
    borderTopColor: '#E5E0D8',
    backgroundColor: '#FFFFFF',
    paddingHorizontal: 12,
    paddingTop: 10,
    paddingBottom: 18,
    flexDirection: 'row',
    alignItems: 'flex-end',
  },
  inputIcon: {
    fontSize: 18,
    color: '#6D7E72',
    marginRight: 10,
    marginBottom: 12,
  },
  input: {
    flex: 1,
    minHeight: 42,
    maxHeight: 110,
    backgroundColor: '#F5F0E8',
    borderRadius: 14,
    paddingHorizontal: 12,
    paddingVertical: 11,
    color: '#2A2A2A',
    fontSize: 13,
  },
  sendButton: {
    marginLeft: 10,
    width: 42,
    height: 42,
    borderRadius: 21,
    backgroundColor: '#3D6B52',
    alignItems: 'center',
    justifyContent: 'center',
  },
  sendButtonDisabled: {
    opacity: 0.6,
  },
  sendButtonText: {
    color: '#FFFFFF',
    fontSize: 18,
    fontWeight: '700',
  },
});
