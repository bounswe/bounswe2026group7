alter table users
  add column version bigint not null default 0;
