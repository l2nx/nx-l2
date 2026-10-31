/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.game.p267.crypt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class GameXorKeysTest {
    @Test
    void fromSent_shouldAppendTheFixedTailTheServerKeyHas() {
        final byte[] key = GameXorKeys.getRandomKey();

        assertThat(GameXorKeys.fromSent(Arrays.copyOf(key, 8))).containsExactly(key);
    }

    @Test
    void fromSent_shouldRefuseAnythingButTheEightSentBytes() {
        assertThatIllegalArgumentException().isThrownBy(() -> GameXorKeys.fromSent(new byte[7]));
    }
}
