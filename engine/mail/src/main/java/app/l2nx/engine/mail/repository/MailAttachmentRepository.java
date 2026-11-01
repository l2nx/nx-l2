/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.mail.repository;

import static app.l2nx.database.JdbcTimes.instant;
import static app.l2nx.database.JdbcTimes.utc;

import app.l2nx.engine.mail.view.AttachmentSnapshot;
import app.l2nx.engine.mail.view.Augmentation;
import app.l2nx.engine.mail.view.Elements;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** The {@code mail_attachments} table: the snapshots, written once at send and never changed. */
@Repository
@RequiredArgsConstructor
public class MailAttachmentRepository {
    private static final String COLUMNS = """
        slot, item_template_id, count, enchant_level, custom_type1, custom_type2, mana_left, item_expires_at,
        augmentation_option1, augmentation_option2, attack_element, attack_element_power, fire, water, wind, earth,
        holy, dark, enchant_option1, enchant_option2, enchant_option3\
        """;
    private static final String INSERT = """
        INSERT INTO mail_attachments (mail_id, %s)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)\
        """.formatted(COLUMNS);
    private static final String FIND_BY_MAIL = """
        SELECT %s
        FROM mail_attachments
        WHERE mail_id = ?
        ORDER BY slot\
        """.formatted(COLUMNS);
    private static final String FIND_BY_MAILS = """
        SELECT mail_id, %s
        FROM mail_attachments
        WHERE mail_id IN (:mailIds)
        ORDER BY mail_id, slot\
        """.formatted(COLUMNS);
    private static final String COPY = """
        INSERT INTO mail_attachments (mail_id, %1$s)
        SELECT ?, %1$s
        FROM mail_attachments
        WHERE mail_id = ?\
        """.formatted(COLUMNS);

    private final JdbcClient jdbcClient;

    public void insertAll(UUID mailId, List<AttachmentSnapshot> snapshots) {
        for (AttachmentSnapshot snapshot : snapshots) {
            insert(mailId, snapshot);
        }
    }

    public List<AttachmentSnapshot> findByMail(UUID mailId) {
        return jdbcClient
                .sql(FIND_BY_MAIL)
                .param(mailId)
                .query(MailAttachmentRepository::snapshot)
                .list();
    }

    /** @return the snapshots of each mail that has any, in slot order */
    public Map<UUID, List<AttachmentSnapshot>> findByMails(Collection<UUID> mailIds) {
        if (mailIds.isEmpty()) {
            return Map.of();
        }
        final Map<UUID, List<AttachmentSnapshot>> snapshots = new HashMap<>();
        jdbcClient.sql(FIND_BY_MAILS).param("mailIds", mailIds).query(rs -> {
            snapshots
                    .computeIfAbsent(rs.getObject("mail_id", UUID.class), id -> new ArrayList<>())
                    .add(snapshot(rs, rs.getRow()));
        });
        return snapshots;
    }

    public void copy(UUID fromMailId, UUID toMailId) {
        jdbcClient.sql(COPY).param(toMailId).param(fromMailId).update();
    }

    private void insert(UUID mailId, AttachmentSnapshot snapshot) {
        final Augmentation augmentation = snapshot.augmentation();
        final Elements elements = snapshot.elements();
        final List<Integer> options = snapshot.enchantOptions();
        jdbcClient
                .sql(INSERT)
                .param(mailId)
                .param(snapshot.slot())
                .param(snapshot.itemTemplateId())
                .param(snapshot.count())
                .param(snapshot.enchantLevel())
                .param(snapshot.customType1())
                .param(snapshot.customType2())
                .param(snapshot.manaLeft())
                .param(utc(snapshot.itemExpiresAt()))
                .param(augmentation == null ? null : augmentation.option1())
                .param(augmentation == null ? null : augmentation.option2())
                .param(elements == null ? null : elements.attackElement())
                .param((elements == null) || (elements.attackElement() == null) ? null : elements.attackElementPower())
                .param(elements == null ? null : elements.fire())
                .param(elements == null ? null : elements.water())
                .param(elements == null ? null : elements.wind())
                .param(elements == null ? null : elements.earth())
                .param(elements == null ? null : elements.holy())
                .param(elements == null ? null : elements.dark())
                .param(option(options, 0))
                .param(option(options, 1))
                .param(option(options, 2))
                .update();
    }

    private static Integer option(List<Integer> options, int index) {
        return index < options.size() ? options.get(index) : null;
    }

    private static AttachmentSnapshot snapshot(ResultSet rs, int rowNum) throws SQLException {
        return AttachmentSnapshot.builder()
                .slot(rs.getInt("slot"))
                .itemTemplateId(rs.getInt("item_template_id"))
                .count(rs.getLong("count"))
                .enchantLevel(rs.getInt("enchant_level"))
                .customType1(rs.getInt("custom_type1"))
                .customType2(rs.getInt("custom_type2"))
                .manaLeft(rs.getObject("mana_left", Integer.class))
                .itemExpiresAt(instant(rs, "item_expires_at"))
                .augmentation(augmentation(rs))
                .elements(elements(rs))
                .enchantOptions(enchantOptions(rs))
                .build();
    }

    private static Augmentation augmentation(ResultSet rs) throws SQLException {
        final Integer option1 = rs.getObject("augmentation_option1", Integer.class);
        if (option1 == null) {
            return null;
        }
        return new Augmentation(option1, rs.getInt("augmentation_option2"));
    }

    // The defence columns are written together, so one of them tells whether the item had attributes at all.
    private static Elements elements(ResultSet rs) throws SQLException {
        if (rs.getObject("fire", Integer.class) == null) {
            return null;
        }
        return Elements.builder()
                .attackElement(rs.getObject("attack_element", Integer.class))
                .attackElementPower(rs.getInt("attack_element_power"))
                .fire(rs.getInt("fire"))
                .water(rs.getInt("water"))
                .wind(rs.getInt("wind"))
                .earth(rs.getInt("earth"))
                .holy(rs.getInt("holy"))
                .dark(rs.getInt("dark"))
                .build();
    }

    private static List<Integer> enchantOptions(ResultSet rs) throws SQLException {
        final List<Integer> options = new ArrayList<>(AttachmentSnapshot.MAX_ENCHANT_OPTIONS);
        for (int i = 1; i <= AttachmentSnapshot.MAX_ENCHANT_OPTIONS; i++) {
            final Integer option = rs.getObject("enchant_option" + i, Integer.class);
            if (option != null) {
                options.add(option);
            }
        }
        return options;
    }
}
