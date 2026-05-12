// Stable e2e selectors. Keep in sync with data-testid attributes in src/.
// One source of truth so spec renames are grep-able.
export const FEED = {
  composerToggle: 'feed-composer-toggle',
  composerBody: 'feed-composer-body',
  composerHashtags: 'feed-composer-hashtags',
  composerSend: 'feed-composer-send',
  composerError: 'feed-composer-error',
  searchInput: 'feed-search-input',
  tab: (key) => `feed-tab-${key}`,
  listForYou: 'feed-list-for-you',
  listFollowing: 'feed-list-following',
  recCard: (id) => `feed-rec-card-${id}`,
  recFactor: (id, i) => `feed-rec-factor-${id}-${i}`,
  recFollow: (id) => `feed-rec-follow-${id}`,
  editModal: 'feed-edit-modal',
  editBody: 'feed-edit-body',
  editHashtags: 'feed-edit-hashtags',
  editSave: 'feed-edit-save',
  editCancel: 'feed-edit-cancel',
};

export const FEED_POST = {
  card: (id) => `feed-post-card-${id}`,
  body: (id) => `feed-post-body-${id}`,
  edited: (id) => `feed-post-edited-${id}`,
  hashtag: (id, tag) => `feed-post-hashtag-${id}-${tag}`,
  attachments: (id) => `feed-post-attachments-${id}`,
  like: (id) => `feed-post-like-${id}`,
  comment: (id) => `feed-post-comment-${id}`,
  share: (id) => `feed-post-share-${id}`,
  bookmark: (id) => `feed-post-bookmark-${id}`,
  menu: (id) => `feed-post-menu-${id}`,
  edit: (id) => `feed-post-edit-${id}`,
  delete: (id) => `feed-post-delete-${id}`,
};

export const FEED_BOOKMARKS = { page: 'feed-bookmarks-page', list: 'feed-bookmarks-list', empty: 'feed-bookmarks-empty' };
export const USER_PROFILE = { posts: (userId) => `user-profile-posts-${userId}`, name: 'user-profile-name' };
export const EXPLORE = {
  searchInput: 'explore-search-input',
  mentorCard: (id) => `explore-mentor-card-${id}`,
  mentorName: (id) => `explore-mentor-name-${id}`,
  mentorCity: (id) => `explore-mentor-city-${id}`,
  factor: (id, i) => `explore-factor-${id}-${i}`,
  diversePill: (id) => `explore-diverse-pill-${id}`,
  sendRequest: (id) => `explore-send-request-${id}`,
};
export const PROFILE = { maxMenteeCapacity: 'profile-max-mentee-capacity', save: 'profile-save' };
export const TIMELINE = {
  start: 'timeline-start',
  end: 'timeline-end',
  today: 'timeline-today',
  milestone: (id) => `timeline-milestone-${id}`,
  event: (id) => `timeline-event-${id}`,
};
export const MILESTONES = {
  add: 'milestones-add',
  card: (id) => `milestone-card-${id}`,
  cardBody: (id) => `milestone-card-body-${id}`,
  edit: (id) => `milestone-edit-${id}`,
  delete: (id) => `milestone-delete-${id}`,
  modal: 'milestone-modal',
  modalTitle: 'milestone-modal-title',
  modalSave: 'milestone-modal-save',
};
export const MENTORSHIP = {
  extendOpen: 'mentorship-extend-open',
  extendModal: 'mentorship-extend-modal',
  extendOption: (m) => `mentorship-extend-option-${m}`,
  extendConfirm: 'mentorship-extend-confirm',
};
export const MESSAGES = { conversation: (id) => `messages-conversation-${id}`, threadAdmin: 'messages-thread-admin' };
