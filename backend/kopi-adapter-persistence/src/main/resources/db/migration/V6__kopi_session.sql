-- The Kopi session on this computer (ADR-019): the tokens of the
-- kopi-chess-client app client, which only open the person's Kopi id and
-- online games. One row at most: one Kopi account per Kopi Chess.
--
-- The tokens are kept in the local database, like the games: Kopi Chess
-- only answers this computer (ADR-016) and PostgreSQL only listens inside
-- Docker's network.
create table kopi_session (
    id            int         primary key default 1 check (id = 1),
    access_token  text        not null,
    refresh_token text        not null,
    expires_at    timestamptz not null,
    sub           text        not null,
    email         text        not null default '',
    updated_at    timestamptz not null default now()
);
