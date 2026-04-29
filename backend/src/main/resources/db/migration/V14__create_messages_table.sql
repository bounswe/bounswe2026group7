create table messages (
  id             bigserial primary key,
  mentorship_id  bigint not null references mentorships(id) on delete cascade,
  sender_id      bigint not null references users(id) on delete cascade,
  content        text not null,
  attachment_url varchar(512),
  sent_at        timestamptz not null default now(),
  read_at        timestamptz
);

create index idx_messages_mentorship_sent_at
  on messages (mentorship_id, sent_at desc, id desc);

create index idx_messages_mentorship_unread
  on messages (mentorship_id, read_at)
  where read_at is null;
