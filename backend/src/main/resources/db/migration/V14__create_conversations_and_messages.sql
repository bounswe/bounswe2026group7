create table conversations (
  id            bigserial primary key,
  kind          varchar(32) not null check (kind in ('MENTORSHIP', 'MENTOR_PAIR')),
  mentorship_id bigint references mentorships(id) on delete cascade,
  created_at    timestamptz not null default now()
);

-- A mentorship has at most one conversation.
create unique index uq_conversations_mentorship
  on conversations (mentorship_id)
  where mentorship_id is not null;

create table conversation_participants (
  conversation_id bigint not null references conversations(id) on delete cascade,
  user_id         bigint not null references users(id) on delete cascade,
  joined_at       timestamptz not null default now(),
  primary key (conversation_id, user_id)
);

create index idx_conversation_participants_user
  on conversation_participants (user_id);

create table messages (
  id              bigserial primary key,
  conversation_id bigint not null references conversations(id) on delete cascade,
  sender_id       bigint not null references users(id) on delete cascade,
  content         text not null,
  attachment_url  varchar(512),
  sent_at         timestamptz not null default now(),
  read_at         timestamptz
);

create index idx_messages_conversation_sent_at
  on messages (conversation_id, sent_at desc, id desc);

create index idx_messages_conversation_unread
  on messages (conversation_id, read_at)
  where read_at is null;
