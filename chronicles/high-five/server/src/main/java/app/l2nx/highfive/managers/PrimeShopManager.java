/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.managers;

import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.repository.item.PrimeShopTransactionRepository;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PrimeShopManager {
    private static volatile PrimeShopManager instance;

    private final PrimeShopTransactionRepository transactionRepository;

    public PrimeShopManager(PrimeShopTransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
        instance = this;
    }

    public void recordPurchase(int characterId, int productId, int count) {
        transactionRepository.insert(
                characterId, productId, count, ClockBridge.wallClock().instant());
    }

    public List<Integer> findPurchasedProductIds(int characterId) {
        return transactionRepository.findProductIds(characterId);
    }

    // TODO: callers receive the bean through their constructor (#41).
    public static PrimeShopManager getInstance() {
        final PrimeShopManager current = instance;
        if (current == null) {
            throw new IllegalStateException("PrimeShopManager is used before the Spring context created it.");
        }
        return current;
    }
}
