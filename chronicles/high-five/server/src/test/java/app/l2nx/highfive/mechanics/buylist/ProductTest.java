/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.mechanics.buylist;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.engine.core.clock.ManualGameClock;
import app.l2nx.highfive.clock.ClockBridgeExtension;
import app.l2nx.highfive.taskmanagers.BuyListTaskManager;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;

@ExtendWith(ClockBridgeExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(ClockBridgeExtension.LOCK)
class ProductTest {

    /** The singleton manager keeps scheduled restocks in static state; clear them so no test sees another's. */
    @AfterEach
    void clearScheduledRestocks() throws ReflectiveOperationException {
        final Field products = BuyListTaskManager.class.getDeclaredField("PRODUCTS");
        products.setAccessible(true);
        ((Map<?, ?>) products.get(null)).clear();
    }

    @Nested
    class RestartRestockTask {

        @Test
        void restartRestockTask_shouldKeepStoredEndTime_whenRestockIsAhead(ManualGameClock clock) {
            // The template is only read on save, which a pending restock does not reach.
            final Product product = new Product(1, null, 10, 60, 5);
            final long expected = clock.wallMillis() + Duration.ofMinutes(30).toMillis();

            product.restartRestockTask(expected);

            final long actual = BuyListTaskManager.getInstance().getRestockDelay(product);
            assertThat(actual).isEqualTo(expected);
        }
    }
}
