/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity.zone;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.zone.form.ZoneCuboid;
import java.util.ArrayList;
import java.util.List;

/** Zones and creatures for zone tests; creatures stand at the origin unless moved. */
public final class ZoneTestObjects {
    private ZoneTestObjects() {}

    public static ZoneCuboid boxAroundOrigin() {
        return new ZoneCuboid(-100, 100, -100, 100, -100, 100);
    }

    public static Creature creature(int objectId) {
        final Creature creature = mock(Creature.class);
        when(creature.getObjectId()).thenReturn(objectId);
        return creature;
    }

    public static Player player(int objectId) {
        final Player player = mock(Player.class);
        when(player.getObjectId()).thenReturn(objectId);
        when(player.isPlayer()).thenReturn(true);
        when(player.isPlayable()).thenReturn(true);
        when(player.asPlayer()).thenReturn(player);
        when(player.isOnline()).thenReturn(true);
        return player;
    }

    public static void moveTo(Creature creature, int x, int y, int z) {
        when(creature.getX()).thenReturn(x);
        when(creature.getY()).thenReturn(y);
        when(creature.getZ()).thenReturn(z);
    }

    public static final class RecordingZone extends ZoneType {
        public final List<String> events = new ArrayList<>();

        public RecordingZone(int id) {
            super(id);
            setZone(boxAroundOrigin());
        }

        @Override
        protected void onEnter(Creature creature) {
            events.add("enter " + creature.getObjectId());
        }

        @Override
        protected void onExit(Creature creature) {
            events.add("exit " + creature.getObjectId());
        }
    }
}
