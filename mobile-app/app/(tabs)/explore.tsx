import React from 'react';
import { useRole } from '../../components/RoleContext';
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  StyleSheet,
  ScrollView,
} from 'react-native';

export default function ExploreScreen() {
  const { role } = useRole();
  const isMentor = role === 'mentor';

  if (isMentor) {
    return <MentorExploreContent />;
  }

  return <MenteeExploreContent />;
}

function MenteeExploreContent() {
  const mentors = [
    {
      initials: 'BA',
      avatarBg: '#D6E8DC',
      avatarText: '#2F563C',
      name: 'Burak Afşar',
      role: 'Senior iOS Dev · Apple',
      available: true,
      tags: ['Swift', 'Mobile'],
      rating: '4.9',
      reviews: '24',
    },
    {
      initials: 'AY',
      avatarBg: '#E2D1E6',
      avatarText: '#6D3F72',
      name: 'Ayşe Yıldız',
      role: 'ML Engineer · Google',
      available: true,
      tags: ['Python', 'ML'],
      rating: '4.7',
      reviews: '18',
    },
    {
      initials: 'MK',
      avatarBg: '#DFD9C9',
      avatarText: '#66582F',
      name: 'Mehmet Kaya',
      role: 'Backend Lead · Trendyol',
      available: false,
      tags: ['Node.js', 'AWS'],
      rating: '4.8',
      reviews: '31',
    },
    {
      initials: 'EA',
      avatarBg: '#D7E4F1',
      avatarText: '#355B7A',
      name: 'Elif Arslan',
      role: 'Data Scientist · Microsoft',
      available: true,
      tags: ['SQL', 'Data'],
      rating: '4.8',
      reviews: '21',
    },
  ];

  return (
    <View style={styles.container}>
      <View style={styles.fixedHeader}>
        <View style={styles.topCircle} />

        <View style={styles.statusRow}>
          <Text style={styles.statusText}>9:41</Text>
          <Text style={styles.statusIcons}>▲ ▮</Text>
        </View>

        <Text style={styles.title}>
          Find a{'\n'}
          <Text style={styles.titleItalic}>Mentor.</Text>
        </Text>

        <View style={styles.searchBox}>
          <Text style={styles.searchIcon}>🔍</Text>
          <TextInput
            placeholder="Search topics or mentors..."
            placeholderTextColor="rgba(255,255,255,0.45)"
            style={styles.searchInput}
          />
        </View>

        <ScrollView
          horizontal
          showsHorizontalScrollIndicator={false}
          contentContainerStyle={styles.filterRow}
        >
          <TouchableOpacity style={[styles.filterButton, styles.filterButtonActive]}>
            <Text style={[styles.filterText, styles.filterTextActive]}>All</Text>
          </TouchableOpacity>

          <TouchableOpacity style={styles.filterButton}>
            <Text style={styles.filterText}>Backend</Text>
          </TouchableOpacity>

          <TouchableOpacity style={styles.filterButton}>
            <Text style={styles.filterText}>Mobile</Text>
          </TouchableOpacity>

          <TouchableOpacity style={styles.filterButton}>
            <Text style={styles.filterText}>AI/ML</Text>
          </TouchableOpacity>

          <TouchableOpacity style={styles.filterButton}>
            <Text style={styles.filterText}>Data</Text>
          </TouchableOpacity>
        </ScrollView>
      </View>

      <ScrollView
        style={styles.listArea}
        contentContainerStyle={styles.listContent}
        showsVerticalScrollIndicator={false}
      >
        {mentors.map((mentor, index) => (
          <View key={index} style={styles.card}>
            <View style={styles.cardTopRow}>
              <View
                style={[
                  styles.avatar,
                  { backgroundColor: mentor.avatarBg },
                ]}
              >
                <Text
                  style={[
                    styles.avatarText,
                    { color: mentor.avatarText },
                  ]}
                >
                  {mentor.initials}
                </Text>
              </View>

              <View style={styles.cardInfo}>
                <Text style={styles.cardName}>{mentor.name}</Text>
                <Text style={styles.cardRole}>{mentor.role}</Text>
              </View>

              <View
                style={[
                  styles.statusBadge,
                  mentor.available
                    ? styles.availableBadge
                    : styles.fullBadge,
                ]}
              >
                <Text
                  style={[
                    styles.statusBadgeText,
                    mentor.available
                      ? styles.availableBadgeText
                      : styles.fullBadgeText,
                  ]}
                >
                  {mentor.available ? 'Available' : 'Full'}
                </Text>
              </View>
            </View>

            <View style={styles.tagsRow}>
              {mentor.tags.map((tag, tagIndex) => (
                <View key={tagIndex} style={styles.tag}>
                  <Text style={styles.tagText}>{tag}</Text>
                </View>
              ))}
            </View>

            <View style={styles.divider} />

            <View style={styles.cardBottomRow}>
              <View style={styles.ratingRow}>
                <Text style={styles.stars}>★★★★★</Text>
                <Text style={styles.ratingText}>
                  {mentor.rating} ({mentor.reviews})
                </Text>
              </View>

              <TouchableOpacity style={styles.viewButton}>
                <Text style={styles.viewButtonText}>View Profile</Text>
              </TouchableOpacity>
            </View>
          </View>
        ))}
      </ScrollView>
    </View>
  );
}

function MentorExploreContent() {
  const featuredLearning = [
    {
      icon: '📘',
      iconBg: '#D9E6F2',
      title: 'Structuring Your First Session',
      progress: 65,
      timeLeft: '4 min left',
    },
    {
      icon: '🎬',
      iconBg: '#E7DFF0',
      title: 'How to Set SMART Goals with\nMentees',
      progress: 30,
      timeLeft: '12 min left',
    },
  ];

  const recommended = [
    {
      icon: '🧠',
      iconBg: '#DFE7C8',
      title: 'Dealing with Disengaged\nMentees',
      badge: 'New',
      badgeType: 'green',
      meta: 'Article · 6 min',
    },
    {
      icon: '🎙️',
      iconBg: '#EADFD6',
      title: 'Active Listening in\nMentorship',
      badge: 'Popular',
      badgeType: 'rose',
      meta: 'Podcast · 22 min',
    },
    {
      icon: '📋',
      iconBg: '#ECE4C8',
      title: 'Mentorship Contract\nTemplates',
      badge: 'Toolkit',
      badgeType: 'gold',
      meta: 'Guide · Free',
    },
    {
      icon: '📊',
      iconBg: '#DCE5F1',
      title: 'Tracking Mentee Progress\nEffectively',
      badge: 'Data',
      badgeType: 'blue',
      meta: 'Article · 5 min',
    },
  ];

  return (
    <View style={styles.container}>
      <View style={styles.fixedHeaderMentor}>
        <View style={styles.mentorTopCircle} />
        <View style={styles.mentorLeftCircle} />

        <View style={styles.statusRow}>
          <Text style={styles.statusText}>9:41</Text>
          <Text style={styles.statusIcons}>▲ ▮</Text>
        </View>

        <Text style={styles.title}>
          Grow as a{'\n'}
          <Text style={styles.titleItalic}>Mentor.</Text>
        </Text>

        <View style={styles.searchBoxMentor}>
          <Text style={styles.searchIcon}>🔍</Text>
          <TextInput
            placeholder="Search articles, guides, events..."
            placeholderTextColor="rgba(255,255,255,0.45)"
            style={styles.searchInput}
          />
        </View>
      </View>

      <ScrollView
        style={styles.listArea}
        contentContainerStyle={styles.mentorListContent}
        showsVerticalScrollIndicator={false}
      >
        <ScrollView
          horizontal
          showsHorizontalScrollIndicator={false}
          contentContainerStyle={styles.mentorFilterRow}
        >
          <TouchableOpacity style={[styles.pillButton, styles.pillButtonActive]}>
            <Text style={[styles.pillText, styles.pillTextActive]}>All</Text>
          </TouchableOpacity>

          <TouchableOpacity style={styles.pillButton}>
            <Text style={styles.pillText}>Articles</Text>
          </TouchableOpacity>

          <TouchableOpacity style={styles.pillButton}>
            <Text style={styles.pillText}>Videos</Text>
          </TouchableOpacity>

          <TouchableOpacity style={styles.pillButton}>
            <Text style={styles.pillText}>Guides</Text>
          </TouchableOpacity>

          <TouchableOpacity style={styles.pillButton}>
            <Text style={styles.pillText}>Events</Text>
          </TouchableOpacity>
        </ScrollView>

        <Text style={styles.sectionTitle}>FEATURED RESOURCE</Text>

        <View style={styles.featuredCard}>
          <View style={styles.featuredCircle} />
          <View style={styles.editorPill}>
            <Text style={styles.editorPillText}>⭐ Editor&apos;s Pick</Text>
          </View>

          <Text style={styles.featuredTitle}>
            The Art of Giving Feedback That{'\n'}
            Actually Sticks
          </Text>

          <Text style={styles.featuredMeta}>8 min read · Mentoring Skills</Text>
        </View>

        <Text style={styles.sectionTitle}>CONTINUE LEARNING</Text>

        {featuredLearning.map((item, index) => (
          <View key={index} style={styles.learningCard}>
            <View style={[styles.learningIconBox, { backgroundColor: item.iconBg }]}>
              <Text style={styles.learningIcon}>{item.icon}</Text>
            </View>

            <View style={styles.learningTextArea}>
              <Text style={styles.learningTitle}>{item.title}</Text>

              <View style={styles.learningBottomRow}>
                <View style={styles.progressTrack}>
                  <View
                    style={[
                      styles.progressFill,
                      { width: `${item.progress}%` },
                    ]}
                  />
                </View>
                <Text style={styles.learningTime}>{item.timeLeft}</Text>
              </View>
            </View>

            <Text style={styles.chevron}>›</Text>
          </View>
        ))}

        <Text style={styles.sectionTitle}>RECOMMENDED FOR YOU</Text>

        {recommended.map((item, index) => (
          <View key={index} style={styles.recommendCard}>
            <View style={[styles.recommendIconBox, { backgroundColor: item.iconBg }]}>
              <Text style={styles.recommendIcon}>{item.icon}</Text>
            </View>

            <View style={styles.recommendTextArea}>
              <Text style={styles.recommendTitle}>{item.title}</Text>

              <View style={styles.metaRow}>
                <View
                  style={[
                    styles.metaBadge,
                    item.badgeType === 'green' && styles.metaBadgeGreen,
                    item.badgeType === 'rose' && styles.metaBadgeRose,
                    item.badgeType === 'gold' && styles.metaBadgeGold,
                    item.badgeType === 'blue' && styles.metaBadgeBlue,
                  ]}
                >
                  <Text
                    style={[
                      styles.metaBadgeText,
                      item.badgeType === 'green' && styles.metaBadgeTextGreen,
                      item.badgeType === 'rose' && styles.metaBadgeTextRose,
                      item.badgeType === 'gold' && styles.metaBadgeTextGold,
                      item.badgeType === 'blue' && styles.metaBadgeTextBlue,
                    ]}
                  >
                    {item.badge}
                  </Text>
                </View>

                <Text style={styles.metaText}>{item.meta}</Text>
              </View>
            </View>

            <Text style={styles.chevron}>›</Text>
          </View>
        ))}
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#ECE8E1',
  },

  fixedHeader: {
    backgroundColor: '#456B50',
    paddingTop: 54,
    paddingHorizontal: 24,
    paddingBottom: 18,
    overflow: 'hidden',
  },

  fixedHeaderMentor: {
    backgroundColor: '#456B50',
    paddingTop: 54,
    paddingHorizontal: 24,
    paddingBottom: 18,
    overflow: 'hidden',
  },

  topCircle: {
    position: 'absolute',
    width: 310,
    height: 310,
    borderRadius: 155,
    backgroundColor: 'rgba(255,255,255,0.05)',
    top: -20,
    right: -70,
  },

  mentorTopCircle: {
    position: 'absolute',
    width: 320,
    height: 320,
    borderRadius: 160,
    backgroundColor: 'rgba(255,255,255,0.05)',
    top: -30,
    right: -70,
  },

  mentorLeftCircle: {
    position: 'absolute',
    width: 230,
    height: 230,
    borderRadius: 115,
    backgroundColor: 'rgba(255,255,255,0.04)',
    top: 40,
    left: -60,
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

  title: {
    color: '#F7F4EE',
    fontSize: 34,
    lineHeight: 38,
    fontWeight: '700',
    marginTop: 20,
    marginBottom: 20,
  },

  titleItalic: {
    fontStyle: 'italic',
    fontWeight: '700',
  },

  searchBox: {
    height: 66,
    borderRadius: 22,
    borderWidth: 1.5,
    borderColor: 'rgba(255,255,255,0.16)',
    backgroundColor: 'rgba(255,255,255,0.06)',
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 18,
    marginBottom: 18,
  },

  searchBoxMentor: {
    height: 66,
    borderRadius: 22,
    borderWidth: 1.5,
    borderColor: 'rgba(255,255,255,0.16)',
    backgroundColor: 'rgba(255,255,255,0.06)',
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 18,
    marginBottom: 18,
  },

  searchIcon: {
    fontSize: 18,
    marginRight: 10,
  },

  searchInput: {
    flex: 1,
    color: '#FFFFFF',
    fontSize: 16,
    fontWeight: '500',
  },

  filterRow: {
    paddingRight: 20,
    gap: 12,
  },

  filterButton: {
    paddingHorizontal: 22,
    paddingVertical: 12,
    borderRadius: 22,
    borderWidth: 1.5,
    borderColor: 'rgba(255,255,255,0.16)',
    backgroundColor: 'rgba(255,255,255,0.06)',
  },

  filterButtonActive: {
    backgroundColor: '#F8F6F2',
    borderColor: '#F8F6F2',
  },

  filterText: {
    color: 'rgba(255,255,255,0.65)',
    fontSize: 14,
    fontWeight: '600',
  },

  filterTextActive: {
    color: '#2F563C',
  },

  listArea: {
    flex: 1,
    backgroundColor: '#ECE8E1',
  },

  listContent: {
    padding: 20,
    paddingBottom: 40,
  },

  mentorListContent: {
    paddingTop: 16,
    paddingHorizontal: 20,
    paddingBottom: 40,
  },

  mentorFilterRow: {
    paddingRight: 20,
    gap: 12,
    marginBottom: 18,
  },

  pillButton: {
    paddingHorizontal: 24,
    paddingVertical: 12,
    borderRadius: 24,
    backgroundColor: '#FBFAF7',
    borderWidth: 1.5,
    borderColor: '#E5DDD1',
  },

  pillButtonActive: {
    backgroundColor: '#D7E8DA',
    borderColor: '#3F7653',
  },

  pillText: {
    color: '#9A8F82',
    fontSize: 14,
    fontWeight: '600',
  },

  pillTextActive: {
    color: '#2F563C',
  },

  sectionTitle: {
    color: '#8B8176',
    fontSize: 13,
    fontWeight: '700',
    letterSpacing: 2,
    marginBottom: 16,
  },

  featuredCard: {
    backgroundColor: '#3F7653',
    borderRadius: 30,
    padding: 22,
    overflow: 'hidden',
    marginBottom: 26,
  },

  featuredCircle: {
    position: 'absolute',
    width: 170,
    height: 170,
    borderRadius: 85,
    backgroundColor: 'rgba(255,255,255,0.06)',
    right: -10,
    top: 20,
  },

  editorPill: {
    alignSelf: 'flex-start',
    backgroundColor: 'rgba(255,255,255,0.10)',
    borderWidth: 1.5,
    borderColor: 'rgba(255,255,255,0.14)',
    paddingHorizontal: 16,
    paddingVertical: 10,
    borderRadius: 18,
    marginBottom: 18,
  },

  editorPillText: {
    color: '#F7F4EE',
    fontSize: 13,
    fontWeight: '700',
  },

  featuredTitle: {
    color: '#F7F4EE',
    fontSize: 24,
    lineHeight: 34,
    fontWeight: '700',
    marginBottom: 10,
  },

  featuredMeta: {
    color: 'rgba(247,244,238,0.8)',
    fontSize: 14,
    fontWeight: '500',
  },

  learningCard: {
    backgroundColor: '#F8F6F2',
    borderRadius: 26,
    padding: 18,
    marginBottom: 16,
    flexDirection: 'row',
    alignItems: 'center',
  },

  learningIconBox: {
    width: 86,
    height: 86,
    borderRadius: 22,
    justifyContent: 'center',
    alignItems: 'center',
    marginRight: 16,
  },

  learningIcon: {
    fontSize: 34,
  },

  learningTextArea: {
    flex: 1,
  },

  learningTitle: {
    color: '#23372B',
    fontSize: 18,
    fontWeight: '700',
    lineHeight: 26,
    marginBottom: 14,
  },

  learningBottomRow: {
    flexDirection: 'row',
    alignItems: 'center',
  },

  progressTrack: {
    flex: 1,
    height: 8,
    borderRadius: 999,
    backgroundColor: '#E2DACE',
    overflow: 'hidden',
    marginRight: 12,
  },

  progressFill: {
    height: '100%',
    borderRadius: 999,
    backgroundColor: '#5B9565',
  },

  learningTime: {
    color: '#9A8F82',
    fontSize: 14,
    fontWeight: '500',
  },

  recommendCard: {
    backgroundColor: '#F8F6F2',
    borderRadius: 26,
    padding: 18,
    marginBottom: 18,
    flexDirection: 'row',
    alignItems: 'center',
  },

  recommendIconBox: {
    width: 86,
    height: 86,
    borderRadius: 22,
    justifyContent: 'center',
    alignItems: 'center',
    marginRight: 16,
  },

  recommendIcon: {
    fontSize: 34,
  },

  recommendTextArea: {
    flex: 1,
  },

  recommendTitle: {
    color: '#23372B',
    fontSize: 18,
    fontWeight: '700',
    lineHeight: 26,
    marginBottom: 10,
  },

  metaRow: {
    flexDirection: 'row',
    alignItems: 'center',
    flexWrap: 'wrap',
  },

  metaBadge: {
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderRadius: 16,
    marginRight: 10,
  },

  metaBadgeGreen: {
    backgroundColor: '#D7E8DA',
  },

  metaBadgeRose: {
    backgroundColor: '#EAD7D3',
  },

  metaBadgeGold: {
    backgroundColor: '#F1E1BB',
  },

  metaBadgeBlue: {
    backgroundColor: '#D8E5F1',
  },

  metaBadgeText: {
    fontSize: 13,
    fontWeight: '700',
  },

  metaBadgeTextGreen: {
    color: '#2F563C',
  },

  metaBadgeTextRose: {
    color: '#8A4B2E',
  },

  metaBadgeTextGold: {
    color: '#8A5D12',
  },

  metaBadgeTextBlue: {
    color: '#315A7A',
  },

  metaText: {
    color: '#A49A8E',
    fontSize: 14,
    fontWeight: '500',
  },

  chevron: {
    fontSize: 28,
    color: '#B9B0A5',
    marginLeft: 10,
    fontWeight: '400',
  },

  card: {
    backgroundColor: '#F8F6F2',
    borderRadius: 28,
    padding: 18,
    marginBottom: 18,
    shadowColor: '#000',
    shadowOpacity: 0.04,
    shadowRadius: 10,
    shadowOffset: { width: 0, height: 3 },
    elevation: 2,
  },

  cardTopRow: {
    flexDirection: 'row',
    alignItems: 'center',
  },

  avatar: {
    width: 64,
    height: 64,
    borderRadius: 32,
    justifyContent: 'center',
    alignItems: 'center',
    marginRight: 14,
  },

  avatarText: {
    fontSize: 20,
    fontWeight: '700',
  },

  cardInfo: {
    flex: 1,
  },

  cardName: {
    color: '#21372A',
    fontSize: 18,
    fontWeight: '700',
    marginBottom: 4,
  },

  cardRole: {
    color: '#9A8F82',
    fontSize: 13,
    fontWeight: '500',
  },

  statusBadge: {
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderRadius: 16,
  },

  availableBadge: {
    backgroundColor: '#D7E8DA',
  },

  fullBadge: {
    backgroundColor: '#F0D6D7',
  },

  statusBadgeText: {
    fontSize: 12,
    fontWeight: '700',
  },

  availableBadgeText: {
    color: '#2F563C',
  },

  fullBadgeText: {
    color: '#7E2F2F',
  },

  tagsRow: {
    flexDirection: 'row',
    gap: 10,
    marginTop: 16,
    marginBottom: 16,
  },

  tag: {
    backgroundColor: '#D8E5F1',
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderRadius: 14,
  },

  tagText: {
    color: '#315A7A',
    fontSize: 12,
    fontWeight: '600',
  },

  divider: {
    height: 1,
    backgroundColor: '#E2DACE',
    marginBottom: 16,
  },

  cardBottomRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },

  ratingRow: {
    flexDirection: 'row',
    alignItems: 'center',
  },

  stars: {
    color: '#D09541',
    fontSize: 18,
    marginRight: 8,
  },

  ratingText: {
    color: '#7E7368',
    fontSize: 13,
    fontWeight: '500',
  },

  viewButton: {
    backgroundColor: '#D7E8DA',
    paddingHorizontal: 20,
    paddingVertical: 12,
    borderRadius: 18,
  },

  viewButtonText: {
    color: '#2F563C',
    fontSize: 14,
    fontWeight: '700',
  },
});