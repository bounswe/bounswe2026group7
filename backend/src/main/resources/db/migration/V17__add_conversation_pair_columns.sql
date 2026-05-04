-- Adds the pair columns + invariant constraints needed for mentor-to-mentor
-- conversations (issue #284). The conversations table already supports a
-- MENTOR_PAIR kind (V14), but had no columns to identify which two users a
-- pair conversation belongs to and no DB-level guarantee that the kind
-- discriminator agrees with the populated columns.
--
-- After this migration each conversation row is exactly one of:
--   MENTORSHIP : mentorship_id non-null, pair_a_id null
--   MENTOR_PAIR: mentorship_id null,    pair_a_id non-null < pair_b_id
--
-- The FK ON DELETE CASCADE on pair_*_id matches the existing semantics for
-- mentorship_id: deleting a participant cascades the conversation away.

alter table conversations
    add column pair_a_id bigint references users(id) on delete cascade,
    add column pair_b_id bigint references users(id) on delete cascade;

-- Pair columns are either both populated or both null, and pair_a_id is the
-- numerically-smaller user id when populated. Strict ordering is what makes
-- (pair_a_id, pair_b_id) a canonical key for an unordered user pair.
alter table conversations
    add constraint conversations_pair_completeness check (
        (pair_a_id is null and pair_b_id is null)
        or (pair_a_id is not null and pair_b_id is not null)
    ),
    add constraint conversations_pair_ordering check (
        pair_a_id is null or pair_a_id < pair_b_id
    );

-- The kind discriminator and the column populations must agree. Without this
-- check, a buggy migration or hand-written INSERT could produce a MENTOR_PAIR
-- row with a non-null mentorship_id, and MessageService.assertSendable would
-- silently misbehave because it dispatches on kind, not on which column is
-- populated.
alter table conversations
    add constraint conversations_kind_columns_consistent check (
        (kind = 'MENTORSHIP'  and mentorship_id is not null and pair_a_id is null)
        or
        (kind = 'MENTOR_PAIR' and mentorship_id is null     and pair_a_id is not null)
    );

-- Defense-in-depth uniqueness for canonical mentor pairs. Includes `kind` in
-- the key so a future kind that reuses the pair columns (e.g. open DM) can
-- coexist with mentor-pair without colliding.
create unique index uq_conversations_pair_kind
    on conversations (pair_a_id, pair_b_id, kind)
    where pair_a_id is not null;
