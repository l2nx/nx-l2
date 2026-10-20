/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.managers;

import app.l2nx.highfive.repository.character.CharacterRepository;
import app.l2nx.highfive.repository.character.model.BirthdayCharacter;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class CharacterBirthdayManager {
    private static volatile CharacterBirthdayManager instance;

    private final CharacterRepository characterRepository;

    public CharacterBirthdayManager(CharacterRepository characterRepository) {
        this.characterRepository = characterRepository;
        instance = this;
    }

    /**
     * @return the characters whose creation anniversary fell between {@code since} and {@code now}, at least one year
     *     after the creation
     */
    public List<BirthdayCharacter> findBirthdays(Instant since, Instant now) {
        return characterRepository.findBirthdays(since, now);
    }

    // TODO: callers receive the bean through their constructor (#41).
    public static CharacterBirthdayManager getInstance() {
        final CharacterBirthdayManager current = instance;
        if (current == null) {
            throw new IllegalStateException("CharacterBirthdayManager is used before the Spring context created it.");
        }
        return current;
    }
}
