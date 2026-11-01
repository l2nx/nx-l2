/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.partition;

import app.l2nx.engine.core.clock.ManualGameClock;
import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.engine.core.identity.WireHandleAllocator;
import app.l2nx.engine.core.loop.EngineProperties;
import app.l2nx.engine.core.loop.Loop;
import app.l2nx.engine.core.loop.LoopBinding;
import app.l2nx.engine.core.partition.PartitionRuntime;
import app.l2nx.highfive.clock.FixedClockBinding;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.item.EtcItem;
import app.l2nx.highfive.entity.item.enums.ItemLocation;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.entity.itemcontainer.ItemContainer;
import app.l2nx.highfive.managers.IdManager;
import app.l2nx.highfive.util.StatSet;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Map;
import java.util.SplittableRandom;
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

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Thread)
public class ItemContainerBenchmark {
    private static final int FIRST_ITEM_ID = 20_000_000;
    private static final int RESIDENT = 60;
    private static final int TURNOVER = 10;

    private Loop previous;
    private FixedClockBinding clockBinding;
    private Bag bag;
    private Item[] resident;
    private Item[] turnover;

    // Setup runs on the benchmark thread, which therefore owns the world loop.
    @Setup
    public void setUp() {
        final ManualGameClock clock = new ManualGameClock(Instant.parse("2026-10-26T12:00:00Z"), ZoneOffset.UTC);
        final Loop loop =
                new Loop(PartitionRuntime.WORLD, clock, 1L, EngineProperties.defaults(), new SimpleMeterRegistry());
        LoopBinding.bindToCurrentThread(loop);
        previous = WorldPartition.swap(loop);
        final SplittableRandom random = new SplittableRandom(42L);
        clockBinding = FixedClockBinding.bind(clock, () -> random);

        final IdentityRegistry registry =
                new IdentityRegistry(new WireHandleAllocator(Clock.systemUTC(), Duration.ofMinutes(5)));
        new IdManager(registry);

        bag = new Bag();
        resident = new Item[RESIDENT];
        for (int i = 0; i < RESIDENT; i++) {
            resident[i] = new Item(registry.allocateTransient(), template(FIRST_ITEM_ID + i));
            bag.put(resident[i]);
        }

        turnover = new Item[TURNOVER];
        for (int i = 0; i < TURNOVER; i++) {
            turnover[i] = new Item(registry.allocateTransient(), template(FIRST_ITEM_ID + RESIDENT + i));
        }
    }

    @TearDown
    public void tearDown() {
        clockBinding.close();
        WorldPartition.swap(previous);
    }

    @Benchmark
    public void addRemove() {
        for (Item item : turnover) {
            bag.put(item);
        }

        for (Item item : turnover) {
            bag.take(item);
        }
    }

    @Benchmark
    public Item byItemId() {
        return bag.getItemByItemId(FIRST_ITEM_ID + RESIDENT - 1);
    }

    @Benchmark
    public Item byObjectId() {
        return bag.getItemByObjectId(resident[RESIDENT - 1].getObjectId());
    }

    @Benchmark
    public Collection<Item> items() {
        return bag.getItems();
    }

    @Benchmark
    public long counts() {
        long sum = 0;
        for (Item item : resident) {
            sum += item.getCount();
        }

        return sum;
    }

    private static EtcItem template(int id) {
        return new EtcItem(new StatSet(Map.of("item_id", id, "name", "Bench " + id)));
    }

    // The container's own add and remove, without the database writes of its public add and destroy paths.
    private static final class Bag extends ItemContainer {
        void put(Item item) {
            addItem(item);
        }

        void take(Item item) {
            removeItem(item);
        }

        @Override
        protected Creature getOwner() {
            return null;
        }

        @Override
        protected ItemLocation getBaseLocation() {
            return ItemLocation.INVENTORY;
        }
    }
}
