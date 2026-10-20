package app.l2nx.highfive.mechanics.olympiad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import app.l2nx.highfive.data.sql.ClanTable;
import app.l2nx.highfive.entity.clan.Clan;
import app.l2nx.highfive.repository.character.CharacterRepository;
import app.l2nx.highfive.repository.character.model.CharacterClanAlly;
import app.l2nx.highfive.repository.item.ItemRepository;
import app.l2nx.highfive.repository.olympiad.HeroDiaryRepository;
import app.l2nx.highfive.repository.olympiad.HeroRepository;
import app.l2nx.highfive.repository.olympiad.OlympiadFightRepository;
import app.l2nx.highfive.util.StatSet;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.mockito.MockedStatic;

@Execution(ExecutionMode.SAME_THREAD)
class HeroTest {
    private static final int CHARACTER_ID = 268_480_000;
    private static final int CLAN_ID = 268_435_456;
    private static final int ALLY_ID = 7;

    private final CharacterRepository characters = mock(CharacterRepository.class);
    private final ClanTable clanTable = mock(ClanTable.class);

    private MockedStatic<ClanTable> clanTableBridge;
    private Hero hero;

    @BeforeEach
    void createHero() {
        clanTableBridge = mockStatic(ClanTable.class);
        clanTableBridge.when(ClanTable::getInstance).thenReturn(clanTable);
        hero = new Hero(
                mock(HeroRepository.class),
                mock(HeroDiaryRepository.class),
                mock(OlympiadFightRepository.class),
                characters,
                mock(ItemRepository.class));
    }

    @AfterEach
    void releaseClanTable() {
        clanTableBridge.close();
    }

    @Nested
    class ProcessHeros {
        @Test
        void processHeros_shouldUseNoClanDefaults_whenClanIsGone() {
            when(characters.findClanAndAlly(CHARACTER_ID))
                    .thenReturn(Optional.of(new CharacterClanAlly(CLAN_ID, ALLY_ID)));
            when(clanTable.getClan(CLAN_ID)).thenReturn(null);
            final StatSet stats = new StatSet();

            hero.processHeros(CHARACTER_ID, stats);

            assertThat(stats.contains(Hero.CLAN_NAME)).isFalse();
            assertThat(stats.contains(Hero.ALLY_NAME)).isFalse();
            assertThat(stats.getInt(Hero.CLAN_CREST)).isZero();
            assertThat(stats.getInt(Hero.ALLY_CREST)).isZero();
        }

        @Test
        void processHeros_shouldCopyClanAndAlly_whenClanExists() {
            final Clan clan = mock(Clan.class);
            when(clan.getName()).thenReturn("Clan");
            when(clan.getCrestId()).thenReturn(11);
            when(clan.getAllyName()).thenReturn("Ally");
            when(clan.getAllyCrestId()).thenReturn(22);
            when(characters.findClanAndAlly(CHARACTER_ID))
                    .thenReturn(Optional.of(new CharacterClanAlly(CLAN_ID, ALLY_ID)));
            when(clanTable.getClan(CLAN_ID)).thenReturn(clan);
            final StatSet stats = new StatSet();

            hero.processHeros(CHARACTER_ID, stats);

            assertThat(stats.getString(Hero.CLAN_NAME)).isEqualTo("Clan");
            assertThat(stats.getInt(Hero.CLAN_CREST)).isEqualTo(11);
            assertThat(stats.getString(Hero.ALLY_NAME)).isEqualTo("Ally");
            assertThat(stats.getInt(Hero.ALLY_CREST)).isEqualTo(22);
        }
    }
}
