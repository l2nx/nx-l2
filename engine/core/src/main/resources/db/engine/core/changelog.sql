--liquibase formatted sql

--changeset core:001-value-transactions
CREATE TABLE value_transactions (
    id uuid PRIMARY KEY,
    idempotency_key uuid NOT NULL UNIQUE,
    type text NOT NULL CHECK (type IN ('MAIL_SEND', 'MAIL_RECEIVE', 'MAIL_RETURN', 'MAIL_CANCEL', 'MAIL_EXPIRE', 'MAIL_CHARACTER_DELETE')),
    -- the main entity of the transaction (a mail); returned on Replayed
    subject_id uuid,
    committed_at timestamptz NOT NULL
);

CREATE TABLE value_ledger (
    id uuid PRIMARY KEY,
    transaction_id uuid NOT NULL REFERENCES value_transactions (id),
    holder_type text NOT NULL CHECK (holder_type IN ('INVENTORY', 'WAREHOUSE', 'MAIL')),
    -- a character for INVENTORY and WAREHOUSE, a mail for MAIL
    holder_id uuid NOT NULL,
    item_template_id integer NOT NULL,
    item_id uuid NOT NULL,
    delta bigint NOT NULL CHECK (delta <> 0)
);

CREATE INDEX value_ledger_transaction_id_idx ON value_ledger (transaction_id);
CREATE INDEX value_ledger_item_id_idx ON value_ledger (item_id);
