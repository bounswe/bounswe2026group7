-- Replaces the free-form messages.attachment_url with a foreign key to a
-- dedicated attachments table that tracks the uploader and provenance.
--
-- V14 (this branch) introduced messages.attachment_url; it has not yet
-- merged to dev, so no production rows hold a value there. We drop the
-- column outright instead of backfilling. If V14 is merged to a shared
-- environment before this migration ships, add an INSERT...SELECT
-- backfill step before the DROP.

create table attachments (
  id            uuid primary key,
  filename      varchar(255) not null,
  content_type  varchar(100) not null,
  size_bytes    bigint not null,
  uploader_id   bigint not null references users(id) on delete cascade,
  created_at    timestamptz not null default now()
);

create index idx_attachments_uploader
  on attachments (uploader_id);

alter table messages
  add column attachment_id uuid references attachments(id) on delete set null;

create index idx_messages_attachment
  on messages (attachment_id)
  where attachment_id is not null;

alter table messages
  drop column attachment_url;
