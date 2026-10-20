/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.managers;

import app.l2nx.highfive.repository.forum.BbsFavoriteRepository;
import app.l2nx.highfive.repository.forum.model.BbsFavorite;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class BbsFavoriteManager {
    private static volatile BbsFavoriteManager instance;

    private final BbsFavoriteRepository favoriteRepository;

    public BbsFavoriteManager(BbsFavoriteRepository favoriteRepository) {
        this.favoriteRepository = favoriteRepository;
        instance = this;
    }

    /**
     * @return the favorites, newest first
     */
    public List<BbsFavorite> findFavorites(int characterId) {
        return favoriteRepository.findByCharacter(characterId);
    }

    public int countFavorites(int characterId) {
        return favoriteRepository.countByCharacter(characterId);
    }

    /**
     * Adds the link, or renames and moves to the top a link already bookmarked.
     */
    public void addFavorite(int characterId, String title, String bypass, Instant now) {
        favoriteRepository.save(characterId, title, bypass, now);
    }

    public boolean deleteFavorite(int characterId, int favoriteId) {
        return favoriteRepository.delete(characterId, favoriteId);
    }

    // TODO: callers receive the bean through their constructor (#41).
    public static BbsFavoriteManager getInstance() {
        final BbsFavoriteManager current = instance;
        if (current == null) {
            throw new IllegalStateException("BbsFavoriteManager is used before the Spring context created it.");
        }
        return current;
    }
}
