/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2013 L2jMobius
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package app.l2nx.highfive.entity.zone.type;

import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.zone.ZoneId;
import app.l2nx.highfive.entity.zone.ZoneType;
import app.l2nx.highfive.managers.CastleManager;
import app.l2nx.highfive.mechanics.siege.Castle;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.OnEventTrigger;
import lombok.Getter;

/**
 * another type of zone where your speed is changed
 * @author Kerberos, Mobius
 */
public class SwampZone extends ZoneType {
    @Getter
    private double moveBonus;

    private int castleId;
    private Castle castle;
    private int eventId;

    public SwampZone(int id) {
        super(id);

        // Setup default speed reduce (in %).
        moveBonus = 0.5;

        // no castle by default
        castleId = 0;
        castle = null;

        // no event by default
        eventId = 0;
    }

    @Override
    public void setParameter(String name, String value) {
        if (name.equals("move_bonus")) {
            moveBonus = Double.parseDouble(value);
        } else if (name.equals("castleId")) {
            castleId = Integer.parseInt(value);
        } else if (name.equals("eventId")) {
            eventId = Integer.parseInt(value);
        } else {
            super.setParameter(name, value);
        }
    }

    private Castle getCastle() {
        if ((castleId > 0) && (castle == null)) {
            castle = CastleManager.getInstance().getCastleById(castleId);
        }

        return castle;
    }

    @Override
    protected void onEnter(Creature creature) {
        if (getCastle() != null) {
            // castle zones active only during siege
            if (!getCastle().getSiege().isInProgress()) {
                return;
            }

            // defenders not affected
            final Player player = creature.asPlayer();
            if ((player != null) && player.isInSiege() && (player.getSiegeState() == 2)) {
                return;
            }
        }

        creature.setInsideZone(ZoneId.SWAMP, true);
        if (creature.isPlayer()) {
            if (eventId > 0) {
                creature.sendPacket(new OnEventTrigger(eventId, true));
            }

            creature.asPlayer().broadcastUserInfo();
        }
    }

    @Override
    protected void onExit(Creature creature) {
        // don't broadcast info if not needed
        if (creature.isInsideZone(ZoneId.SWAMP)) {
            creature.setInsideZone(ZoneId.SWAMP, false);
            if (creature.isPlayer()) {
                if (eventId > 0) {
                    creature.sendPacket(new OnEventTrigger(eventId, false));
                }

                if (!creature.isTeleporting()) {
                    creature.asPlayer().broadcastUserInfo();
                }
            }
        }
    }
}
