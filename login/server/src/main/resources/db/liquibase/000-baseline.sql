--liquibase formatted sql

--changeset login:000-baseline
CREATE COLLATION case_insensitive (provider = icu, locale = 'und-u-ks-level2', deterministic = false);

CREATE TABLE accounts (
    id uuid PRIMARY KEY,
    login text COLLATE case_insensitive NOT NULL UNIQUE CHECK (login <> ''),
    password text NOT NULL CHECK (password <> ''),
    email text CHECK (email <> ''),
    created_at timestamptz NOT NULL DEFAULT now(),
    last_active_at timestamptz,
    access_level integer NOT NULL DEFAULT 0,
    banned_until timestamptz,
    last_ip inet,
    last_server_id integer,
    pc_ip inet,
    hop1 inet,
    hop2 inet,
    hop3 inet,
    hop4 inet
);

CREATE TABLE account_ip_rules (
    account_id uuid NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    ip inet NOT NULL,
    rule text NOT NULL DEFAULT 'ALLOW' CHECK (rule IN ('ALLOW', 'DENY')),
    PRIMARY KEY (account_id, ip)
);

CREATE TABLE game_servers (
    server_id integer PRIMARY KEY,
    hex_id bytea NOT NULL CHECK (octet_length(hex_id) BETWEEN 1 AND 64),
    chronicle text CHECK (chronicle <> '')
);
