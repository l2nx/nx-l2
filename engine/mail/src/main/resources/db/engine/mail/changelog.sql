--liquibase formatted sql

--changeset mail:001-mail
CREATE TABLE mails (
    id uuid PRIMARY KEY,
    type text NOT NULL CHECK (type IN ('PLAYER', 'SYSTEM', 'BIRTHDAY')),
    -- NULL: system mail, or the sender was deleted
    sender_id uuid REFERENCES characters (id) ON DELETE SET NULL,
    -- captured at send; NULL for system mail, the protocol shows its system sender
    sender_name text CHECK (sender_name <> ''),
    receiver_id uuid REFERENCES characters (id) ON DELETE SET NULL,
    receiver_name text NOT NULL CHECK (receiver_name <> ''),
    subject text NOT NULL CHECK (subject <> ''),
    body text CHECK (body <> ''),
    cod_adena bigint CHECK (cod_adena > 0),
    attachments text NOT NULL CHECK (attachments IN
        ('NONE', 'ATTACHED', 'RECEIVED', 'RETURNED', 'CANCELLED', 'EXPIRED', 'DESTROYED')),
    returned_from_id uuid REFERENCES mails (id),
    sent_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    read_at timestamptz,
    -- when the attachments left ATTACHED
    settled_at timestamptz,
    sender_deleted_at timestamptz,
    receiver_deleted_at timestamptz,
    -- processed by the expiry job; both boxes hide the mail
    expired_at timestamptz,
    CHECK ((cod_adena IS NULL) OR (attachments <> 'NONE'))
);

CREATE INDEX mails_inbox_idx ON mails (receiver_id) WHERE receiver_deleted_at IS NULL AND expired_at IS NULL;
CREATE INDEX mails_outbox_idx ON mails (sender_id) WHERE sender_deleted_at IS NULL AND expired_at IS NULL;
CREATE INDEX mails_due_idx ON mails (expires_at) WHERE expired_at IS NULL;
-- Whole columns for the foreign key actions on a character or mail delete and for dropping cash on delivery.
CREATE INDEX mails_sender_id_idx ON mails (sender_id);
CREATE INDEX mails_receiver_id_idx ON mails (receiver_id);
CREATE INDEX mails_returned_from_id_idx ON mails (returned_from_id);

CREATE TABLE mail_attachments (
    mail_id uuid NOT NULL REFERENCES mails (id),
    slot smallint NOT NULL CHECK (slot BETWEEN 0 AND 7),
    item_template_id integer NOT NULL,
    count bigint NOT NULL CHECK (count > 0),
    enchant_level integer NOT NULL,
    custom_type1 integer NOT NULL,
    custom_type2 integer NOT NULL,
    mana_left integer,
    item_expires_at timestamptz,
    augmentation_option1 integer,
    augmentation_option2 integer,
    attack_element smallint,
    attack_element_power integer,
    fire integer,
    water integer,
    wind integer,
    earth integer,
    holy integer,
    dark integer,
    enchant_option1 integer,
    enchant_option2 integer,
    enchant_option3 integer,
    PRIMARY KEY (mail_id, slot)
);
