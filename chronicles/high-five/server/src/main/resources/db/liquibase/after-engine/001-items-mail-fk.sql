--liquibase formatted sql

--changeset game:001-items-mail-fk
-- No ON DELETE: a mail row is never deleted, a mail only settles or hides.
ALTER TABLE items
    ADD CONSTRAINT items_mail_id_fkey
        FOREIGN KEY (mail_id) REFERENCES mails (id);
