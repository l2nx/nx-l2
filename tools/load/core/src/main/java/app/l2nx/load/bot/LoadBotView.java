/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.bot;

import app.l2nx.load.bot.model.LoadBotIdentity;
import app.l2nx.load.bot.model.LoadBotState;
import app.l2nx.load.bot.model.Position;
import app.l2nx.load.bot.observation.ObjectSeen;
import java.util.Collection;

/** What a behaviour may read about its load bot: the observations kept so far. Read on the load bot's event loop. */
public interface LoadBotView {
    LoadBotIdentity identity();

    LoadBotState state();

    /** Own object id; 0 until the load bot entered the world. */
    int selfObjectId();

    /** Own position; {@code null} until the load bot entered the world. */
    Position position();

    int hp();

    int mp();

    boolean dead();

    /** Visible world objects with their latest position and state; a live, unmodifiable view. */
    Collection<ObjectSeen> visibleObjects();
}
