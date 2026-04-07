import React, { useMemo, useState, useEffect } from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  TouchableOpacity,
  TextInput,
} from 'react-native';
import { useLocalSearchParams } from 'expo-router';
import { useRole } from '../../components/RoleContext';

type Conversation = {
  id: string;
  name: string;
  subtitle?: string;
  preview: string;
  time: string;
  unread?: number;
  online?: boolean;
  initials: string;
  avatarBg: string;
  avatarText: string;
  pinned?: boolean;
  type?: 'mentor' | 'mentee' | 'support';
};

type Message = {
  id: string;
  sender: 'me' | 'them' | 'system';
  text: string;
  time?: string;
  attachment?: {
    name: string;
    meta: string;
  };
};

const mentorConversations: Conversation[] = [
  {
    id: 'ovgu',
    name: 'Övgü Su Afşar',
    preview: "Got it! I'll push the changes tonight.",
    time: 'now',
    unread: 2,
    online: true,
    initials: 'ÖA',
    avatarBg: '#D4E8DC',
    avatarText: '#2D5A3D',
    type: 'mentee',
  },
  {
    id: 'zeynep',
    name: 'Zeynep Demir',
    preview: "Can we move Thursday's session?",
    time: '14m',
    unread: 1,
    online: false,
    initials: 'ZD',
    avatarBg: '#E8E4D4',
    avatarText: '#5A4E2D',
    type: 'mentee',
  },
  {
    id: 'ali',
    name: 'Ali Çetin',
    preview: 'Thanks for the feedback on my PR!',
    time: '1h',
    unread: 0,
    online: true,
    initials: 'AC',
    avatarBg: '#D4DCE8',
    avatarText: '#2D3A5A',
    type: 'mentee',
  },
  {
    id: 'merve',
    name: 'Merve Rüzgar',
    preview: 'Sent the final project draft.',
    time: '3h',
    unread: 0,
    online: false,
    initials: 'MR',
    avatarBg: '#E8D4DC',
    avatarText: '#5A2D3A',
    type: 'mentee',
  },
];

const menteeConversations: Conversation[] = [
  {
    id: 'burak',
    name: 'Burak Afşar',
    subtitle: 'Your Mentor',
    preview: "Also — here's a guide on writing clean component APIs.",
    time: 'now',
    unread: 1,
    online: true,
    initials: 'BA',
    avatarBg: '#D4E8DC',
    avatarText: '#2D5A3D',
    pinned: true,
    type: 'mentor',
  },
  {
    id: 'mentornet',
    name: 'MentorNet',
    subtitle: 'Support',
    preview: 'Your weekly progress report is ready.',
    time: '1h',
    unread: 1,
    online: false,
    initials: 'MN',
    avatarBg: '#E8E4D4',
    avatarText: '#5A4E2D',
    type: 'support',
  },
  {
    id: 'ayse',
    name: 'Ayşe Yıldız',
    subtitle: 'Mentor (ML)',
    preview: 'Happy to help if you have questions!',
    time: '2d',
    unread: 0,
    online: true,
    initials: 'AY',
    avatarBg: '#E8D4E8',
    avatarText: '#5A2D5A',
    type: 'mentor',
  },
];

const mentorChatMessages: Message[] = [
  { id: '1', sender: 'system', text: 'Today, 14:18' },
  {
    id: '2',
    sender: 'them',
    text: 'Hey! I finished the FlatList implementation. Should I open a PR?',
    time: '14:18',
  },
  {
    id: '3',
    sender: 'me',
    text: 'Yes, go ahead! Make sure you add prop types and a loading state before you open it.',
    time: '14:21',
  },
  {
    id: '4',
    sender: 'them',
    text: "Got it! I'll push the changes tonight.",
    time: '14:23',
  },
  {
    id: '5',
    sender: 'me',
    text: "Also — here's a guide on writing clean component APIs. Worth a read before the PR.",
    time: '14:25',
    attachment: {
      name: 'clean-component-apis.pdf',
      meta: '180 KB · PDF',
    },
  },
  { id: '6', sender: 'system', text: 'Session scheduled — Today at 15:00' },
  {
    id: '7',
    sender: 'them',
    text: 'Perfect, see you then!',
    time: '14:27',
  },
];

const menteeChatMessages: Message[] = [
  { id: '1', sender: 'system', text: 'Today, 14:18' },
  {
    id: '2',
    sender: 'me',
    text: 'Hey! I finished the FlatList implementation. Should I open a PR?',
    time: '14:18',
  },
  {
    id: '3',
    sender: 'them',
    text: 'Yes, go ahead! Make sure you add prop types and a loading state before you open it.',
    time: '14:21',
  },
  {
    id: '4',
    sender: 'me',
    text: "Got it! I'll push the changes tonight.",
    time: '14:23',
  },
  {
    id: '5',
    sender: 'them',
    text: "Also — here's a guide on writing clean component APIs. Worth a read before the PR.",
    time: '14:25',
    attachment: {
      name: 'clean-component-apis.pdf',
      meta: '180 KB · PDF',
    },
  },
  { id: '6', sender: 'system', text: 'Session scheduled — Today at 15:00' },
  {
    id: '7',
    sender: 'me',
    text: 'Perfect, see you then!',
    time: '14:27',
  },
];

const supportMessages: Message[] = [
  { id: '1', sender: 'system', text: 'Today, 13:00 — Automated' },
  {
    id: '2',
    sender: 'them',
    text: "Hi Övgü! Here's your weekly progress summary.",
    time: '13:00',
  },
  {
    id: '3',
    sender: 'them',
    text: 'Keep it up! Your next session with Burak is tomorrow at 15:00. Anything you would like to prepare?',
    time: '13:01',
  },
];

export default function MessagesScreen() {
  const { role } = useRole();
  const isMentor = role === 'mentor';
  const params = useLocalSearchParams();

  const [search, setSearch] = useState('');
  const [selectedConversation, setSelectedConversation] = useState<Conversation | null>(null);
  const [draft, setDraft] = useState('');

  const conversations = isMentor ? mentorConversations : menteeConversations;

  // connection-profile'dan "Open Messages" ile gelindiyse ilgili conversation'ı otomatik aç
  useEffect(() => {
    const openWith = Array.isArray(params.openWith) ? params.openWith[0] : params.openWith;
    if (!openWith) return;
    const match = conversations.find((c) =>
      c.name.toLowerCase().includes(openWith.toLowerCase())
    );
    if (match) setSelectedConversation(match);
  }, [params.openWith, conversations]);

  const filteredConversations = useMemo(() => {
    const q = search.trim().toLowerCase();
    if (!q) return conversations;
    return conversations.filter(
      (item) =>
        item.name.toLowerCase().includes(q) ||
        item.preview.toLowerCase().includes(q) ||
        item.subtitle?.toLowerCase().includes(q)
    );
  }, [conversations, search]);

  const currentMessages = useMemo(() => {
    if (!selectedConversation) return [];
    if (isMentor) return mentorChatMessages;
    if (selectedConversation.type === 'support') return supportMessages;
    return menteeChatMessages;
  }, [isMentor, selectedConversation]);

  const titleLine = isMentor ? 'Your mentees.' : 'Your mentor.';

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
            <TouchableOpacity onPress={() => setSelectedConversation(null)}>
              <Text style={styles.backArrow}>‹</Text>
            </TouchableOpacity>

            <View style={styles.chatAvatarWrap}>
              <View
                style={[
                  styles.avatar,
                  {
                    backgroundColor: selectedConversation.avatarBg,
                  },
                ]}
              >
                <Text
                  style={[
                    styles.avatarText,
                    { color: selectedConversation.avatarText },
                  ]}
                >
                  {selectedConversation.initials}
                </Text>
              </View>
              {selectedConversation.online ? <View style={styles.onlineDot} /> : null}
            </View>

            <View style={styles.chatHeaderInfo}>
              <Text style={styles.chatHeaderName}>{selectedConversation.name}</Text>
              <Text style={styles.chatHeaderSub}>
                {selectedConversation.type === 'support'
                  ? 'Progress report ready'
                  : isMentor
                  ? selectedConversation.online
                    ? 'Online · Active now'
                    : 'Last seen 2h ago'
                  : 'Your Mentor · Online now'}
              </Text>
            </View>

            <View style={styles.chatHeaderActions}>
              <Text style={styles.chatHeaderIcon}>📅</Text>
              <Text style={styles.chatHeaderIcon}>⋯</Text>
            </View>
          </View>
        </View>

        {selectedConversation.type === 'support' ? (
          <View style={styles.contextBar}>
            <Text style={styles.badgeAmber}>Week 3 Summary</Text>
            <Text style={styles.contextText}>Automated progress update</Text>
          </View>
        ) : isMentor ? (
          <View style={styles.contextBar}>
            <Text style={styles.badgeSage}>Week 3</Text>
            <Text style={styles.contextText}>2 tasks open</Text>
            <View style={styles.contextDivider} />
            <Text style={styles.contextText}>Next session: Today 15:00</Text>
          </View>
        ) : (
          <View style={styles.contextBar}>
            <Text style={styles.badgeAmber}>Today 15:00</Text>
            <Text style={styles.contextText}>Code Review session</Text>
            <TouchableOpacity style={styles.joinButton}>
              <Text style={styles.joinButtonText}>Join</Text>
            </TouchableOpacity>
          </View>
        )}

        <ScrollView
          style={styles.chatScroll}
          contentContainerStyle={styles.chatScrollContent}
          showsVerticalScrollIndicator={false}
        >
          {currentMessages.map((message) => {
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
                    <View style={styles.attachmentPill}>
                      <Text style={styles.attachmentIcon}>📄</Text>
                      <View>
                        <Text style={styles.attachmentTitle}>{message.attachment.name}</Text>
                        <Text style={styles.attachmentMeta}>{message.attachment.meta}</Text>
                      </View>
                    </View>
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
          })}

          {isMentor && selectedConversation.type !== 'support' ? (
            <View style={styles.cardSuggestion}>
              <Text style={styles.cardSuggestionLabel}>Suggested Action</Text>
              <Text style={styles.cardSuggestionText}>
                Assign a task for reviewing the PDF before your 15:00 session?
              </Text>
              <View style={styles.row}>
                <TouchableOpacity style={styles.primaryMiniButton}>
                  <Text style={styles.primaryMiniButtonText}>Assign Task</Text>
                </TouchableOpacity>
                <TouchableOpacity style={styles.secondaryMiniButton}>
                  <Text style={styles.secondaryMiniButtonText}>Dismiss</Text>
                </TouchableOpacity>
              </View>
            </View>
          ) : null}

          {!isMentor && selectedConversation.type !== 'support' ? (
            <View style={styles.cardSuggestion}>
              <Text style={styles.cardSuggestionLabel}>New Task Assigned</Text>
              <Text style={styles.cardSuggestionText}>
                Read &quot;Clean Component APIs&quot; before your 15:00 session.
              </Text>
              <View style={styles.row}>
                <TouchableOpacity style={styles.primaryMiniButton}>
                  <Text style={styles.primaryMiniButtonText}>Mark as Done</Text>
                </TouchableOpacity>
                <TouchableOpacity style={styles.secondaryMiniButton}>
                  <Text style={styles.secondaryMiniButtonText}>View</Text>
                </TouchableOpacity>
              </View>
            </View>
          ) : null}

          {!isMentor && selectedConversation.type === 'support' ? (
            <View style={styles.progressCard}>
              <Text style={styles.progressTitle}>Week 3 Summary</Text>
              <ProgressRow label="Tasks completed" value="3 / 5" progress={0.6} badge="amber" />
              <ProgressRow label="Sessions attended" value="2 / 2" progress={1} badge="sage" />
              <ProgressRow label="Overall progress" value="68%" progress={0.68} badge="blue" />
              <View style={styles.progressDivider} />
              <Text style={styles.progressNote}>
                Mentor note: Great consistency this week. Focus on finishing the remaining 2 tasks
                before your Friday session.
              </Text>
            </View>
          ) : null}
        </ScrollView>

        <View style={styles.inputBar}>
          <Text style={styles.inputIcon}>📎</Text>
          <TextInput
            value={draft}
            onChangeText={setDraft}
            placeholder={`Message ${selectedConversation.name.split(' ')[0]}...`}
            placeholderTextColor="#B7B0A4"
            style={styles.input}
          />
          <TouchableOpacity style={styles.sendButton}>
            <Text style={styles.sendButtonText}>➤</Text>
          </TouchableOpacity>
        </View>
      </View>
    );
  }

  const pinned = filteredConversations.filter((item) => item.pinned);
  const others = filteredConversations.filter((item) => !item.pinned);

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

          <View style={styles.searchBar}>
            <Text style={styles.searchIcon}>🔍</Text>
            <TextInput
              value={search}
              onChangeText={setSearch}
              placeholder={isMentor ? 'Search conversations...' : 'Search messages...'}
              placeholderTextColor="rgba(255,255,255,0.45)"
              style={styles.searchInput}
            />
          </View>
        </View>
      </View>

      <ScrollView showsVerticalScrollIndicator={false} contentContainerStyle={styles.listScroll}>
        {isMentor ? (
          <>
            <View style={styles.filterRow}>
              {['All', 'Unread', 'Active', 'Pending'].map((item, index) => (
                <View
                  key={item}
                  style={[styles.filterChip, index === 0 && styles.filterChipActive]}
                >
                  <Text style={[styles.filterText, index === 0 && styles.filterTextActive]}>
                    {item}
                    {item === 'Unread' ? ' (3)' : ''}
                  </Text>
                </View>
              ))}
            </View>

            <View style={styles.cardList}>
              {filteredConversations.map((conversation) => (
                <ConversationRow
                  key={conversation.id}
                  item={conversation}
                  onPress={() => setSelectedConversation(conversation)}
                />
              ))}
            </View>

            <View style={styles.section}>
              <Text style={styles.sectionTitle}>Quick Actions</Text>
              <View style={styles.quickActionRow}>
                <QuickActionCard emoji="📢" title="Announce" subtitle="to all mentees" />
                <QuickActionCard emoji="📋" title="Assign Task" subtitle="send to mentee" />
                <QuickActionCard emoji="📅" title="Schedule" subtitle="new session" />
              </View>
            </View>
          </>
        ) : (
          <>
            <View style={styles.section}>
              <Text style={styles.sectionTitle}>Pinned</Text>
              <View style={styles.cardList}>
                {pinned.map((conversation) => (
                  <ConversationRow
                    key={conversation.id}
                    item={conversation}
                    onPress={() => setSelectedConversation(conversation)}
                  />
                ))}
              </View>
            </View>

            <View style={styles.section}>
              <Text style={styles.sectionTitle}>Other</Text>
              <View style={styles.cardList}>
                {others.map((conversation) => (
                  <ConversationRow
                    key={conversation.id}
                    item={conversation}
                    onPress={() => setSelectedConversation(conversation)}
                  />
                ))}
              </View>
            </View>

            <View style={styles.tipCard}>
              <Text style={styles.tipLabel}>Tip</Text>
              <Text style={styles.tipText}>
                Prepare 2–3 questions before each session to make the most of your mentor&apos;s
                time.
              </Text>
            </View>
          </>
        )}
      </ScrollView>
    </View>
  );
}

function ConversationRow({
  item,
  onPress,
}: {
  item: Conversation;
  onPress: () => void;
}) {
  return (
    <TouchableOpacity style={styles.conversationRow} onPress={onPress}>
      <View style={styles.avatarWrap}>
        <View style={[styles.avatar, { backgroundColor: item.avatarBg }]}>
          <Text style={[styles.avatarText, { color: item.avatarText }]}>{item.initials}</Text>
        </View>
        {item.online ? <View style={styles.onlineDot} /> : null}
      </View>

      <View style={styles.conversationBody}>
        <View style={styles.conversationTop}>
          <View style={styles.conversationTitleWrap}>
            <Text style={[styles.conversationName, item.unread ? styles.bold : null]}>
              {item.name}
            </Text>
            {item.subtitle ? <Text style={styles.conversationSubtitle}>{item.subtitle}</Text> : null}
          </View>
          <Text style={[styles.conversationTime, item.unread ? styles.timeActive : null]}>
            {item.time}
          </Text>
        </View>

        <Text
          numberOfLines={1}
          style={[styles.conversationPreview, item.unread ? styles.previewUnread : null]}
        >
          {item.preview}
        </Text>
      </View>

      {item.unread ? (
        <View style={styles.unreadBadge}>
          <Text style={styles.unreadBadgeText}>{item.unread}</Text>
        </View>
      ) : null}
    </TouchableOpacity>
  );
}

function QuickActionCard({
  emoji,
  title,
  subtitle,
}: {
  emoji: string;
  title: string;
  subtitle: string;
}) {
  return (
    <TouchableOpacity style={styles.quickActionCard}>
      <Text style={styles.quickActionEmoji}>{emoji}</Text>
      <Text style={styles.quickActionTitle}>{title}</Text>
      <Text style={styles.quickActionSubtitle}>{subtitle}</Text>
    </TouchableOpacity>
  );
}

function ProgressRow({
  label,
  value,
  progress,
  badge,
}: {
  label: string;
  value: string;
  progress: number;
  badge: 'sage' | 'amber' | 'blue';
}) {
  return (
    <View style={styles.progressRow}>
      <View style={styles.progressHeader}>
        <Text style={styles.progressLabel}>{label}</Text>
        <Text
          style={[
            styles.progressBadge,
            badge === 'sage'
              ? styles.badgeSageSmall
              : badge === 'amber'
              ? styles.badgeAmberSmall
              : styles.badgeBlueSmall,
          ]}
        >
          {value}
        </Text>
      </View>
      <View style={styles.progressTrack}>
        <View style={[styles.progressFill, { width: `${progress * 100}%` }]} />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#F5F0E8',
  },
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
  filterRow: {
    flexDirection: 'row',
    paddingHorizontal: 14,
    marginBottom: 10,
  },
  filterChip: {
    marginRight: 8,
    paddingHorizontal: 12,
    paddingVertical: 5,
    borderRadius: 999,
    borderWidth: 1,
    borderColor: '#E5DED2',
    backgroundColor: '#FFFFFF',
  },
  filterChipActive: {
    backgroundColor: '#D4E8DC',
    borderColor: '#3D6B52',
  },
  filterText: {
    color: '#9A9288',
    fontSize: 11,
  },
  filterTextActive: {
    color: '#2D5A3D',
    fontWeight: '600',
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
  onlineDot: {
    position: 'absolute',
    right: 1,
    bottom: 1,
    width: 11,
    height: 11,
    borderRadius: 999,
    backgroundColor: '#6DD68A',
    borderWidth: 2,
    borderColor: '#FFFFFF',
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
  timeActive: {
    color: '#3D6B52',
    fontWeight: '700',
  },
  conversationPreview: {
    color: '#9A9288',
    fontSize: 11,
  },
  previewUnread: {
    color: '#5A5248',
    fontWeight: '500',
  },
  unreadBadge: {
    backgroundColor: '#3D6B52',
    minWidth: 18,
    borderRadius: 10,
    paddingHorizontal: 6,
    paddingVertical: 2,
    alignItems: 'center',
    justifyContent: 'center',
    marginLeft: 8,
  },
  unreadBadgeText: {
    color: '#FFFFFF',
    fontSize: 10,
    fontWeight: '700',
  },
  bold: {
    fontWeight: '700',
  },
  quickActionRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  quickActionCard: {
    width: '31%',
    backgroundColor: '#FFFFFF',
    borderWidth: 1,
    borderColor: '#EDE8DF',
    borderRadius: 14,
    paddingVertical: 12,
    paddingHorizontal: 8,
    alignItems: 'center',
  },
  quickActionEmoji: {
    fontSize: 18,
    marginBottom: 5,
  },
  quickActionTitle: {
    color: '#1A2E22',
    fontSize: 11,
    fontWeight: '700',
  },
  quickActionSubtitle: {
    color: '#9A9288',
    fontSize: 10,
    textAlign: 'center',
    marginTop: 2,
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
  chatHeaderActions: {
    flexDirection: 'row',
  },
  chatHeaderIcon: {
    color: 'rgba(255,255,255,0.7)',
    fontSize: 17,
    marginLeft: 14,
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
  badgeAmber: {
    backgroundColor: '#F5E8CC',
    color: '#7A5010',
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
  contextDivider: {
    width: 1,
    height: 12,
    backgroundColor: '#D8D2C7',
    marginHorizontal: 8,
  },
  joinButton: {
    marginLeft: 'auto',
    backgroundColor: '#3D6B52',
    borderRadius: 8,
    paddingHorizontal: 10,
    paddingVertical: 5,
  },
  joinButtonText: {
    color: '#FFFFFF',
    fontSize: 10,
    fontWeight: '700',
  },
  chatScroll: {
    flex: 1,
  },
  chatScrollContent: {
    paddingHorizontal: 13,
    paddingVertical: 12,
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
  cardSuggestion: {
    backgroundColor: '#FFFFFF',
    borderWidth: 1,
    borderColor: '#EDE8DF',
    borderRadius: 14,
    padding: 13,
    marginTop: 4,
    marginBottom: 6,
  },
  cardSuggestionLabel: {
    color: '#8A8278',
    fontSize: 10,
    fontWeight: '700',
    textTransform: 'uppercase',
    marginBottom: 6,
    letterSpacing: 0.7,
  },
  cardSuggestionText: {
    color: '#2A2A2A',
    fontSize: 12,
    lineHeight: 18,
    marginBottom: 10,
  },
  row: {
    flexDirection: 'row',
  },
  primaryMiniButton: {
    flex: 1,
    backgroundColor: '#D4E8DC',
    borderRadius: 9,
    paddingVertical: 8,
    alignItems: 'center',
    marginRight: 7,
  },
  primaryMiniButtonText: {
    color: '#2D5A3D',
    fontSize: 11,
    fontWeight: '700',
  },
  secondaryMiniButton: {
    borderRadius: 9,
    borderWidth: 1,
    borderColor: '#EDE8DF',
    backgroundColor: '#F5F0E8',
    paddingVertical: 8,
    paddingHorizontal: 11,
    alignItems: 'center',
    justifyContent: 'center',
  },
  secondaryMiniButtonText: {
    color: '#9A9288',
    fontSize: 11,
    fontWeight: '500',
  },
  progressCard: {
    backgroundColor: '#FFFFFF',
    borderWidth: 1,
    borderColor: '#EDE8DF',
    borderRadius: 14,
    padding: 13,
    marginBottom: 8,
  },
  progressTitle: {
    color: '#2D4D3A',
    fontSize: 11,
    fontWeight: '700',
    marginBottom: 10,
  },
  progressRow: {
    marginBottom: 10,
  },
  progressHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 4,
  },
  progressLabel: {
    color: '#5A5248',
    fontSize: 11,
  },
  progressBadge: {
    fontSize: 10,
    fontWeight: '600',
    paddingHorizontal: 8,
    paddingVertical: 2,
    borderRadius: 8,
    overflow: 'hidden',
  },
  badgeSageSmall: {
    backgroundColor: '#D4E8DC',
    color: '#2D5A3D',
  },
  badgeAmberSmall: {
    backgroundColor: '#F5E8CC',
    color: '#7A5010',
  },
  badgeBlueSmall: {
    backgroundColor: '#DCEAF5',
    color: '#1A4A6E',
  },
  progressTrack: {
    height: 5,
    borderRadius: 999,
    backgroundColor: '#F0EBE3',
    overflow: 'hidden',
  },
  progressFill: {
    height: 5,
    borderRadius: 999,
    backgroundColor: '#3D6B52',
  },
  progressDivider: {
    height: 1,
    backgroundColor: '#F0EBE3',
    marginTop: 2,
    marginBottom: 8,
  },
  progressNote: {
    color: '#5A5248',
    fontSize: 11,
    lineHeight: 17,
  },
  inputBar: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 12,
    paddingVertical: 8,
    backgroundColor: '#FFFFFF',
    borderTopWidth: 1,
    borderTopColor: '#EDE8DF',
  },
  inputIcon: {
    fontSize: 19,
    color: '#BBB4A8',
    marginRight: 8,
  },
  input: {
    flex: 1,
    backgroundColor: '#F5F0E8',
    borderWidth: 1,
    borderColor: '#EDE8DF',
    borderRadius: 20,
    paddingHorizontal: 13,
    paddingVertical: 10,
    fontSize: 12,
    color: '#2A2A2A',
  },
  sendButton: {
    width: 33,
    height: 33,
    borderRadius: 999,
    backgroundColor: '#3D6B52',
    alignItems: 'center',
    justifyContent: 'center',
    marginLeft: 8,
  },
  sendButtonText: {
    color: '#FFFFFF',
    fontSize: 13,
    fontWeight: '700',
  },
});