package app.l2nx.highfive.managers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import app.l2nx.highfive.data.holders.GrandBossSpawn;
import app.l2nx.highfive.data.xml.GrandBossData;
import app.l2nx.highfive.data.xml.NpcData;
import app.l2nx.highfive.entity.actor.templates.NpcTemplate;
import app.l2nx.highfive.repository.boss.GrandBossStateRepository;
import app.l2nx.highfive.repository.boss.GrandBossZoneCharacterRepository;
import app.l2nx.highfive.threads.ThreadPool;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.mockito.MockedStatic;

@Execution(ExecutionMode.SAME_THREAD)
class GrandBossManagerTest {
    private static final int LISTED_BOSS_ID = 29001;
    private static final int UNLISTED_BOSS_ID = 99999;

    private MockedStatic<GrandBossData> bossData;
    private MockedStatic<NpcData> npcData;
    private MockedStatic<ThreadPool> threadPool;
    private GrandBossManager manager;

    @BeforeEach
    void loadManager() {
        final GrandBossData data = mock(GrandBossData.class);
        when(data.getBosses())
                .thenReturn(
                        List.of(GrandBossSpawn.builder().npcId(LISTED_BOSS_ID).build()));
        bossData = mockStatic(GrandBossData.class);
        bossData.when(GrandBossData::getInstance).thenReturn(data);

        final NpcData npcs = mock(NpcData.class);
        when(npcs.getTemplate(LISTED_BOSS_ID)).thenReturn(mock(NpcTemplate.class));
        npcData = mockStatic(NpcData.class);
        npcData.when(NpcData::getInstance).thenReturn(npcs);
        threadPool = mockStatic(ThreadPool.class);

        manager = new GrandBossManager(
                mock(GrandBossStateRepository.class), mock(GrandBossZoneCharacterRepository.class));
        manager.load();
    }

    @AfterEach
    void releaseStatics() {
        threadPool.close();
        npcData.close();
        bossData.close();
    }

    @Nested
    class GetStatus {
        @Test
        void getStatus_shouldReturnZero_whenListedBossHasNoSavedState() {
            assertThat(manager.getStatus(LISTED_BOSS_ID)).isZero();
        }

        @Test
        void getStatus_shouldThrowNamingBossId_whenBossIsNotInGrandBosses() {
            assertThatThrownBy(() -> manager.getStatus(UNLISTED_BOSS_ID))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(String.valueOf(UNLISTED_BOSS_ID))
                    .hasMessageContaining("GrandBosses.xml");
        }
    }
}
