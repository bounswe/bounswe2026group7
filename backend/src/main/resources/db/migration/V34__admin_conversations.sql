-- Issue #280: extend conversations to carry admin-initiated DMs and broadcasts.
--
-- ADMIN_DIRECT reuses the (pair_a_id, pair_b_id) layout from MENTOR_PAIR — same
-- canonical-key + uq_conversations_pair_kind treatment, just with a different
-- authorization gate enforced in the service layer (one side must be ADMIN, no
-- mentorship required). The existing partial unique index on
-- (pair_a_id, pair_b_id, kind) already keys on `kind`, so it transparently
-- accepts the new value without a re-create.
--
-- ADMIN_BROADCAST is a singleton conversation: at most one row exists, and all
-- admins are participants via the junction table. The singleton property is
-- enforced by a partial unique index on a constant expression so concurrent
-- first-broadcast inserts collapse the same way mentorship and mentor-pair
-- creation do.

-- The original V14 inline `check (kind in (...))` produced an auto-named CHECK.
-- Look it up by definition shape rather than guessing the generated name so
-- this migration is portable across deployments where Postgres picked
-- different identifiers for unnamed constraints.
--
-- Match shape: a single CHECK over only the `kind` column (no reference to
-- `mentorship_id` or `pair_a_id`) that enumerates the legacy values. This
-- excludes `conversations_kind_columns_consistent`, which is a multi-column
-- CHECK and is dropped explicitly below.
do $$
declare
    cname text;
begin
    for cname in
        select conname from pg_constraint
        where conrelid = 'conversations'::regclass
          and contype = 'c'
          and pg_get_constraintdef(oid) ilike '%kind%'
          and pg_get_constraintdef(oid) ilike '%MENTORSHIP%'
          and pg_get_constraintdef(oid) ilike '%MENTOR_PAIR%'
          and pg_get_constraintdef(oid) not ilike '%ADMIN_DIRECT%'
          and pg_get_constraintdef(oid) not ilike '%mentorship_id%'
          and pg_get_constraintdef(oid) not ilike '%pair_a_id%'
    loop
        execute format('alter table conversations drop constraint %I', cname);
    end loop;
end$$;

alter table conversations
    add constraint conversations_kind_check
    check (kind in ('MENTORSHIP', 'MENTOR_PAIR', 'ADMIN_DIRECT', 'ADMIN_BROADCAST'));

-- Replace the V17 columns-consistent CHECK so it covers the two new kinds.
alter table conversations
    drop constraint conversations_kind_columns_consistent;

alter table conversations
    add constraint conversations_kind_columns_consistent check (
        (kind = 'MENTORSHIP'      and mentorship_id is not null and pair_a_id is null)
        or
        (kind = 'MENTOR_PAIR'     and mentorship_id is null     and pair_a_id is not null)
        or
        (kind = 'ADMIN_DIRECT'    and mentorship_id is null     and pair_a_id is not null)
        or
        (kind = 'ADMIN_BROADCAST' and mentorship_id is null     and pair_a_id is null)
    );

-- Singleton enforcement for the broadcast conversation. Indexing on a constant
-- expression makes "the row" the only row whose admin_broadcast key value is
-- (TRUE), so a concurrent second insert raises a unique-violation that the
-- service layer recovers via re-find — same pattern as mentorship and pair
-- creation races.
create unique index uq_conversations_admin_broadcast_singleton
    on conversations ((true))
    where kind = 'ADMIN_BROADCAST';
