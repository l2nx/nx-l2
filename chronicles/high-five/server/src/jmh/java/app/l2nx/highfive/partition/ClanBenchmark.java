/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.partition;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.l2nx.engine.core.clock.ManualGameClock;
import app.l2nx.engine.core.loop.EngineProperties;
import app.l2nx.engine.core.loop.Loop;
import app.l2nx.engine.core.loop.LoopBinding;
import app.l2nx.engine.core.partition.PartitionRuntime;
import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.data.sql.ClanTable;
import app.l2nx.highfive.entity.ScratchBuffers;
import app.l2nx.highfive.entity.ScratchBuffers.Snapshot;
import app.l2nx.highfive.entity.actor.enums.creature.Race;
import app.l2nx.highfive.entity.actor.enums.player.Sex;
import app.l2nx.highfive.entity.clan.Clan;
import app.l2nx.highfive.entity.clan.ClanMember;
import app.l2nx.highfive.managers.ItemManager;
import app.l2nx.highfive.repository.character.CharacterRepository;
import app.l2nx.highfive.repository.character.model.CharacterClanMember;
import app.l2nx.highfive.repository.clan.ClanNoticeRepository;
import app.l2nx.highfive.repository.clan.ClanRankPrivilegeRepository;
import app.l2nx.highfive.repository.clan.ClanRepository;
import app.l2nx.highfive.repository.clan.ClanSkillRepository;
import app.l2nx.highfive.repository.clan.ClanSubPledgeRepository;
import app.l2nx.highfive.repository.clan.ClanWarRepository;
import app.l2nx.highfive.repository.clan.model.ClanRow;
import app.l2nx.highfive.repository.item.ItemAugmentationRepository;
import app.l2nx.highfive.repository.item.ItemElementalRepository;
import app.l2nx.highfive.repository.item.ItemRepository;
import app.l2nx.highfive.repository.item.ItemVariableRepository;
import app.l2nx.highfive.repository.residence.CastleStateRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.springframework.transaction.support.TransactionTemplate;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Thread)
public class ClanBenchmark {
    private static final int CLAN_ID = 1;
    private static final int MEMBERS = 100;
    private static final int WARS = 20;
    private static final int FIRST_ID = 268_480_000;
    private static final String LAST_NAME = "member" + (MEMBERS - 1);

    private Loop previous;
    private Clan clan;
    private int cursor;

    // Setup runs on the benchmark thread, which therefore owns the world loop.
    @Setup
    public void setUp() {
        final ManualGameClock clock = new ManualGameClock(Instant.parse("2026-10-26T12:00:00Z"), ZoneOffset.UTC);
        final Loop loop =
                new Loop(PartitionRuntime.WORLD, clock, 1L, EngineProperties.defaults(), new SimpleMeterRegistry());
        LoopBinding.bindToCurrentThread(loop);
        previous = WorldPartition.swap(loop);
        ClockBridge.bindLoops(clock);

        final ClanRepository clans = mock(ClanRepository.class);
        final CharacterRepository characters = mock(CharacterRepository.class);
        when(clans.find(CLAN_ID))
                .thenReturn(Optional.of(
                        ClanRow.builder().clanId(CLAN_ID).name("Bench").build()));
        final List<CharacterClanMember> rows = new ArrayList<>();
        for (int i = 0; i < MEMBERS; i++) {
            rows.add(
                    new CharacterClanMember(FIRST_ID + i, "member" + i, 40, 0, null, 6, 0, 0, 0, Sex.MALE, Race.HUMAN));
        }
        when(characters.findClanMembers(CLAN_ID)).thenReturn(rows);
        new ClanTable(
                clans,
                mock(ClanNoticeRepository.class),
                mock(ClanRankPrivilegeRepository.class),
                mock(ClanSkillRepository.class),
                mock(ClanSubPledgeRepository.class),
                mock(ClanWarRepository.class),
                mock(CastleStateRepository.class),
                characters,
                mock(TransactionTemplate.class));
        new ItemManager(
                mock(ItemRepository.class),
                mock(ItemAugmentationRepository.class),
                mock(ItemElementalRepository.class),
                mock(ItemVariableRepository.class),
                mock(TransactionTemplate.class));
        clan = new Clan(CLAN_ID);

        for (int i = 0; i < WARS; i++) {
            clan.setEnemyClan(100 + i);
        }
    }

    @TearDown
    public void tearDown() {
        WorldPartition.swap(previous);
    }

    @Benchmark
    public ClanMember memberById() {
        return clan.getClanMember(FIRST_ID + (cursor++ % MEMBERS));
    }

    @Benchmark
    public ClanMember memberByName() {
        return clan.getClanMember(LAST_NAME);
    }

    @Benchmark
    public int onlineCount() {
        return clan.getOnlineMembersCount();
    }

    @Benchmark
    public int members() {
        int levels = 0;
        for (ClanMember member : clan.getMembers()) {
            levels += member.getLevel();
        }

        return levels;
    }

    @Benchmark
    public int membersLease() {
        int levels = 0;
        try (Snapshot<ClanMember> lease = ScratchBuffers.empty()) {
            clan.collectMembers(lease);
            for (int i = 0; i < lease.size(); i++) {
                levels += lease.get(i).getLevel();
            }
        }

        return levels;
    }

    @Benchmark
    public boolean war() {
        return clan.isAtWarWith(100 + (cursor++ % (WARS * 2)));
    }
}
