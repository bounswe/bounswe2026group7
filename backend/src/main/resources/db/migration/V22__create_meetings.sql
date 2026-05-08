-- Meeting scheduling support (#246)

ALTER TABLE users ADD COLUMN IF NOT EXISTS timezone varchar(64) not null default 'UTC';

create table meetings (
  id                    bigserial primary key,
  mentorship_id         bigint not null references mentorships(id) on delete cascade,
  title                 varchar(120) not null,
  description           text,
  start_time            timestamptz not null,
  end_time              timestamptz not null,
  status                varchar(32) not null,
  meeting_link          varchar(500),
  meeting_type          varchar(16) not null,
  is_recurring          boolean not null default false,
  recurrence_rule       varchar(200),
  created_by_id         bigint not null references users(id) on delete cascade,
  confirmed_at          timestamptz,
  confirmation_deadline timestamptz,
  notes                 text,
  notes_updated_at      timestamptz,
  notes_updated_by_id   bigint references users(id) on delete set null,
  created_at            timestamptz not null default now()
);

create index idx_meetings_mentorship_start
  on meetings (mentorship_id, start_time);

create index idx_meetings_status_start
  on meetings (status, start_time);

create table meeting_action_items (
  id               bigserial primary key,
  meeting_id       bigint not null references meetings(id) on delete cascade,
  text             text not null,
  is_completed     boolean not null default false,
  completed_at     timestamptz,
  completed_by_id  bigint references users(id) on delete set null,
  created_by_id    bigint not null references users(id) on delete cascade,
  order_index      int not null default 0,
  created_at       timestamptz not null default now()
);

create index idx_meeting_action_items_meeting
  on meeting_action_items (meeting_id, order_index, id);

create table meeting_reschedule_requests (
  id              bigserial primary key,
  meeting_id      bigint not null references meetings(id) on delete cascade,
  requested_by_id bigint not null references users(id) on delete cascade,
  proposed_start  timestamptz not null,
  proposed_end    timestamptz not null,
  reason          text,
  status          varchar(16) not null,
  created_at      timestamptz not null default now(),
  decided_at      timestamptz
);

create index idx_meeting_reschedule_meeting
  on meeting_reschedule_requests (meeting_id, status, created_at);

create table meeting_reminder_states (
  id                      bigserial primary key,
  meeting_id              bigint not null references meetings(id) on delete cascade,
  reminder_offset_minutes int not null,
  sent_at                 timestamptz not null default now(),
  unique (meeting_id, reminder_offset_minutes)
);


