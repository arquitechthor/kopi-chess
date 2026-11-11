-- Server mode (ADR-022): the switch, the Kopi account it was turned on with,
-- and the name the Android app shows (one row). Off by default; remembered
-- across restarts (decision D7).
create table server_mode (
    id           int      primary key default 1 check (id = 1),
    enabled      boolean  not null default false,
    account_sub  text     not null default '',   -- another account signed in pauses it
    name         text     not null default ''    -- empty: this computer's own name
);
