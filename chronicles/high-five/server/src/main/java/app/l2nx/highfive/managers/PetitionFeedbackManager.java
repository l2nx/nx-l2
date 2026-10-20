/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.managers;

import app.l2nx.highfive.repository.world.PetitionFeedbackRepository;
import app.l2nx.highfive.repository.world.model.PetitionFeedback;
import org.springframework.stereotype.Component;

/**
 * Stores the rating a player gives the GM who answered the player's last petition.
 */
@Component
public class PetitionFeedbackManager {
    private static volatile PetitionFeedbackManager instance;

    private final PetitionFeedbackRepository feedbackRepository;

    public PetitionFeedbackManager(PetitionFeedbackRepository feedbackRepository) {
        this.feedbackRepository = feedbackRepository;
        instance = this;
    }

    public void save(PetitionFeedback feedback) {
        feedbackRepository.insert(feedback);
    }

    // TODO: callers receive the bean through their constructor (#41).
    public static PetitionFeedbackManager getInstance() {
        final PetitionFeedbackManager current = instance;
        if (current == null) {
            throw new IllegalStateException("PetitionFeedbackManager is used before the Spring context created it.");
        }
        return current;
    }
}
