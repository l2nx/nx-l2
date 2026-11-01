/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity.actor.instance;

import static app.l2nx.highfive.entity.actor.instance.TestNpcs.call;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.engine.core.identity.WireHandleAllocator;
import app.l2nx.highfive.clock.ClockBridgeExtension;
import app.l2nx.highfive.managers.IdManager;
import app.l2nx.highfive.partition.WorldLoopExtension;
import java.time.Clock;
import java.time.Duration;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@ExtendWith({ClockBridgeExtension.class, WorldLoopExtension.class})
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(ClockBridgeExtension.LOCK)
@ResourceLock(WorldLoopExtension.LOCK)
class MerchantTest {
    @BeforeEach
    void ids() {
        new IdManager(new IdentityRegistry(new WireHandleAllocator(Clock.systemUTC(), Duration.ofMinutes(5))));
    }

    @Nested
    class OwnershipCheck {
        @ParameterizedTest(name = "{0}")
        @MethodSource("ownerCheckedCalls")
        void ownerCheckedCall_shouldThrow_whenCalledOffTheLoop(TestNpcs.OffLoopCall call) {
            final Runnable offLoop = call.prepare();

            assertThatThrownBy(offLoop::run)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("touched from thread");
        }

        static Stream<Named<TestNpcs.OffLoopCall>> ownerCheckedCalls() {
            return Stream.of(
                    call("getMpc (accessor)", MerchantTest::merchant, Merchant::getMpc),
                    call("onSpawn", MerchantTest::merchant, Merchant::onSpawn),
                    call(
                            "getHtmlPath (override)",
                            MerchantTest::merchant,
                            merchant -> merchant.getHtmlPath(TestNpcs.NPC_ID, 0)),
                    call("showBuyWindow", MerchantTest::merchant, merchant -> merchant.showBuyWindow(null, 1)));
        }
    }

    private static Merchant merchant() {
        return new Merchant(TestNpcs.template());
    }
}
