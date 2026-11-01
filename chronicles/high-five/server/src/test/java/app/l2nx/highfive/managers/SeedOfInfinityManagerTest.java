/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.managers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.data.xml.DoorData;
import app.l2nx.highfive.entity.actor.instance.Door;
import app.l2nx.highfive.partition.WorldLoopExtension;
import app.l2nx.highfive.repository.world.GlobalVariableRepository;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.mockito.MockedStatic;
import org.slf4j.LoggerFactory;

@ExtendWith(WorldLoopExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(WorldLoopExtension.LOCK)
class SeedOfInfinityManagerTest {
    private static final long NOW = 1_800_000_000_000L;
    private static final long OPEN_DURATION = 24L * 60 * 60 * 1000;

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    private MockedStatic<ClockBridge> clock;
    private MockedStatic<DoorData> doors;
    private Logger logger;

    @BeforeEach
    void stubWorld() {
        new GlobalVariablesManager(mock(GlobalVariableRepository.class));
        clock = mockStatic(ClockBridge.class);
        clock.when(ClockBridge::wallMillis).thenReturn(NOW);
        final DoorData doorData = mock(DoorData.class);
        when(doorData.getDoor(14240102)).thenReturn(mock(Door.class));
        doors = mockStatic(DoorData.class);
        doors.when(DoorData::getInstance).thenReturn(doorData);

        appender.start();
        logger = (Logger) LoggerFactory.getLogger(SeedOfInfinityManager.class);
        logger.addAppender(appender);
    }

    @AfterEach
    void releaseWorld() {
        logger.detachAppender(appender);
        appender.stop();
        doors.close();
        clock.close();
    }

    @Nested
    class OpenSeed {
        @Test
        void openSeed_shouldLogCloseTime_whenSeedOpens() {
            WorldLoopExtension.runInPass(() -> SeedOfInfinityManager.openSeed(OPEN_DURATION));

            final String expectedTime = DateFormatUtils.format(NOW + OPEN_DURATION, "dd/MM/yyyy HH:mm:ss");
            assertThat(appender.list)
                    .extracting(ILoggingEvent::getFormattedMessage)
                    .contains("Seed of Infinity Manager: Opening the seed until " + expectedTime)
                    .noneMatch(message -> message.contains("1970"));
        }
    }
}
