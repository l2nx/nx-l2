/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.forum;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.engine.core.identity.EntityIds;
import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.repository.forum.ForumRepository;
import app.l2nx.highfive.repository.forum.model.ForumRow;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ForumRepositoryTest extends RepositoryTest {
    private static final int CLAN = 2;
    private static final int MEMO = 3;
    private static final int MAIL = 4;
    private static final int OWNERONLY = 3;

    private final ForumRepository repository = new ForumRepository(JDBC, registry);

    private static int rootId(String name) {
        return JDBC.sql("SELECT id FROM forums WHERE name = ? AND parent_id IS NULL")
                .param(name)
                .query(Integer.class)
                .single();
    }

    private static <T> T column(int forumId, String column, Class<T> type) {
        return JDBC.sql("SELECT " + column + " FROM forums WHERE id = ?")
                .param(forumId)
                .query(type)
                .optional()
                .orElse(null);
    }

    @Nested
    class Insert {
        @Test
        void insert_shouldStoreClanUuidAndNoName_whenClanForum() {
            final UUID clan = insertClan();
            final ForumRow forum = ForumRow.builder()
                    .parentId(rootId("ClanRoot"))
                    .name("SomeClan")
                    .type(CLAN)
                    .permission(2)
                    .ownerId(registry.handleOf(clan))
                    .build();

            final int id = repository.insert(forum);

            assertThat(column(id, "clan_id", UUID.class)).isEqualTo(clan);
            assertThat(JDBC.sql("SELECT name IS NULL AND permission = 'CLANMEMBERONLY' FROM forums WHERE id = ?")
                            .param(id)
                            .query(Boolean.class)
                            .single())
                    .isTrue();
        }

        @Test
        void insert_shouldStoreCharacterUuid_whenMailForum() {
            final UUID character = insertCharacter();

            final int id = repository.insert(ForumRow.builder()
                    .parentId(rootId("MailRoot"))
                    .name("SomeCharacter")
                    .type(MAIL)
                    .permission(OWNERONLY)
                    .ownerId(registry.handleOf(character))
                    .build());

            assertThat(column(id, "character_id", UUID.class)).isEqualTo(character);
        }

        @Test
        void insert_shouldStoreAccountId_whenMemoForum() {
            final UUID account = EntityIds.next();

            final int id = repository.insert(ForumRow.builder()
                    .parentId(rootId("MemoRoot"))
                    .name("account")
                    .type(MEMO)
                    .permission(OWNERONLY)
                    .accountId(account)
                    .build());

            assertThat(column(id, "account_id", UUID.class)).isEqualTo(account);
        }
    }

    @Nested
    class FindById {
        @Test
        void findById_shouldReturnRootWithZeroParentAndOwner_whenSeeded() {
            final int id = rootId("NormalRoot");

            assertThat(repository.findById(id))
                    .contains(ForumRow.builder()
                            .id(id)
                            .name("NormalRoot")
                            .type(0)
                            .permission(1)
                            .build());
        }

        @Test
        void findById_shouldResolveOwnerHandle_whenMailForum() {
            final UUID character = insertCharacter();
            final ForumRow expected = ForumRow.builder()
                    .parentId(rootId("MailRoot"))
                    .type(MAIL)
                    .permission(OWNERONLY)
                    .ownerId(registry.handleOf(character))
                    .build();
            final int id = repository.insert(expected);

            assertThat(repository.findById(id))
                    .contains(expected.toBuilder().id(id).build());
        }

        @Test
        void findById_shouldReturnEmpty_whenForumIsMissing() {
            assertThat(repository.findById(-1)).isEmpty();
        }
    }

    @Nested
    class FindChildIds {
        @Test
        void findChildIds_shouldReturnChildren_whenParentHasForums() {
            final int parent = repository.insert(ForumRow.builder()
                    .parentId(rootId("NormalRoot"))
                    .name("Parent " + EntityIds.next())
                    .type(1)
                    .permission(1)
                    .build());
            final int child = repository.insert(ForumRow.builder()
                    .parentId(parent)
                    .name("Child")
                    .type(1)
                    .permission(1)
                    .build());

            assertThat(repository.findChildIds(parent)).containsExactly(child);
        }
    }

    @Nested
    class FindRootIds {
        @Test
        void findRootIds_shouldReturnSeededRoots_whenCalled() {
            assertThat(repository.findRootIds())
                    .contains(rootId("NormalRoot"), rootId("ClanRoot"), rootId("MemoRoot"), rootId("MailRoot"));
        }
    }
}
