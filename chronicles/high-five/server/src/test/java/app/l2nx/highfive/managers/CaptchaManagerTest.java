/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.managers;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.mechanics.captcha.ImageData;
import app.l2nx.highfive.partition.BackgroundTasksExtension;
import app.l2nx.highfive.partition.WorldLoopExtension;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;

@ExtendWith({WorldLoopExtension.class, BackgroundTasksExtension.class})
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(WorldLoopExtension.LOCK)
@ResourceLock(BackgroundTasksExtension.LOCK)
class CaptchaManagerTest {
    private final Map<Integer, ImageData> current = Map.of(0, new ImageData());

    private Map<Integer, ImageData> previous;

    @BeforeEach
    void setImages() {
        previous = CaptchaManager.IMAGES;
        CaptchaManager.IMAGES = current;
    }

    @AfterEach
    void restoreImages() {
        CaptchaManager.IMAGES = previous;
    }

    private static void rebuildInBackgroundAndApply() {
        BackgroundTasksExtension.runPending();
        WorldLoopExtension.passAfter(Duration.ZERO);
    }

    @Nested
    class RebuildImages {
        @Test
        void rebuildImages_shouldSwapTheImagesInTheNextWorldPass_afterTheBackgroundBuild() {
            CaptchaManager.rebuildImages();

            assertThat(BackgroundTasksExtension.pending()).hasSize(1);
            assertThat(CaptchaManager.IMAGES).isSameAs(current);

            BackgroundTasksExtension.runPending();

            assertThat(CaptchaManager.IMAGES).isSameAs(current);

            WorldLoopExtension.passAfter(Duration.ZERO);

            assertThat(CaptchaManager.IMAGES).isNotSameAs(current).hasSize(1000);
        }

        @Test
        void rebuildImages_shouldNotStartASecondBuild_whileOneIsPending() {
            CaptchaManager.rebuildImages();
            CaptchaManager.rebuildImages();

            assertThat(BackgroundTasksExtension.pending()).hasSize(1);

            rebuildInBackgroundAndApply();
        }

        @Test
        void rebuildImages_shouldStartAgain_afterThePreviousBuildWasApplied() {
            CaptchaManager.rebuildImages();
            rebuildInBackgroundAndApply();

            CaptchaManager.rebuildImages();

            assertThat(BackgroundTasksExtension.pending()).hasSize(1);

            rebuildInBackgroundAndApply();
        }
    }
}
