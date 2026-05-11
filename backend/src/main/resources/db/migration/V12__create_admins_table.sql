create table admins (
  id  bigint primary key references users(id)
);
