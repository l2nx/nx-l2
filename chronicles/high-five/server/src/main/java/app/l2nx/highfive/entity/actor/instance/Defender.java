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
package app.l2nx.highfive.entity.actor.instance;

import app.l2nx.engine.core.loop.SingleWriter;
import app.l2nx.highfive.ai.CreatureAI;
import app.l2nx.highfive.ai.FortSiegeGuardAI;
import app.l2nx.highfive.ai.SiegeGuardAI;
import app.l2nx.highfive.ai.SpecialSiegeGuardAI;
import app.l2nx.highfive.entity.actor.Attackable;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.enums.creature.InstanceType;
import app.l2nx.highfive.entity.actor.templates.NpcTemplate;
import app.l2nx.highfive.managers.CastleManager;
import app.l2nx.highfive.managers.FortManager;
import app.l2nx.highfive.managers.TerritoryWarManager;
import app.l2nx.highfive.mechanics.siege.Castle;
import app.l2nx.highfive.mechanics.siege.Fort;
import app.l2nx.highfive.mechanics.siege.clanhalls.SiegableHall;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ActionFailed;
import app.l2nx.highfive.partition.WorldPartition;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@SingleWriter
public class Defender extends Attackable {
    private Castle castle = null;
    private Fort fort = null;
    private SiegableHall hall = null;

    public Defender(NpcTemplate template) {
        super(template);
        setInstanceType(InstanceType.Defender);
    }

    @Override
    protected CreatureAI initAI() {
        if ((getConquerableHall() == null) && (getCastle(10000) == null)) {
            return new FortSiegeGuardAI(this);
        } else if (getCastle(10000) != null) {
            return new SiegeGuardAI(this);
        }

        return new SpecialSiegeGuardAI(this);
    }

    @Override
    public boolean isAutoAttackable(Creature attacker) {
        WorldPartition.checkOwner();
        if (!attacker.isPlayable()) {
            return false;
        }

        final Player player = attacker.asPlayer();

        if (((fort != null) && fort.getZone().isActive())
                || ((castle != null) && castle.getZone().isActive())
                || ((hall != null) && hall.getSiegeZone().isActive())) {
            final int activeSiegeId = (fort != null
                    ? fort.getResidenceId()
                    : (castle != null ? castle.getResidenceId() : (hall != null ? hall.getId() : 0)));

            if ((player != null)
                    && (((player.getSiegeState() == 2) && !player.isRegisteredOnThisSiegeField(activeSiegeId))
                            || ((player.getSiegeState() == 1)
                                    && !TerritoryWarManager.getInstance().isAllyField(player, activeSiegeId))
                            || (player.getSiegeState() == 0))) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean hasRandomAnimation() {
        WorldPartition.checkOwner();
        return false;
    }

    @Override
    public void returnHome() {
        WorldPartition.checkOwner();
        if (getWalkSpeed() <= 0) {
            return;
        }

        if (getSpawn() == null) {
            return;
        }

        if (!isInsideRadius2D(getSpawn(), 40)) {
            clearAggroList();

            if (hasAI()) {
                getAI().setIntentionMoveTo(getSpawn().getLocation());
            }
        }
    }

    @Override
    public void onSpawn() {
        WorldPartition.checkOwner();
        super.onSpawn();

        fort = FortManager.getInstance().getFort(getX(), getY(), getZ());
        castle = CastleManager.getInstance().getCastle(getX(), getY(), getZ());
        hall = getConquerableHall();
        if ((fort == null) && (castle == null) && (hall == null)) {
            log.warn(
                    "Defender spawned outside of Fortress, Castle or Siegable hall Zone! NpcId: {} x={} y={} z={}",
                    getId(),
                    getX(),
                    getY(),
                    getZ());
        }
    }

    // Does not call super: guards must decide between interact and attack on click.
    @Override
    public void onAction(Player player, boolean interact) {
        WorldPartition.checkOwner();
        if (!canTarget(player)) {
            player.sendPacket(ActionFailed.STATIC_PACKET);
            return;
        }

        if (this != player.getTarget()) {
            player.setTarget(this);
        } else if (interact) {
            if (isAutoAttackable(player) && !isAlikeDead() && (Math.abs(player.getZ() - getZ()) < 600)) {
                player.getAI().setIntentionAttack(this);
            }

            if (!isAutoAttackable(player) && !canInteract(player)) {
                player.getAI().setIntentionInteract(this);
            }
        }

        player.sendPacket(ActionFailed.STATIC_PACKET);
    }

    @Override
    public void addDamageHate(Creature attacker, long damage, long aggro) {
        WorldPartition.checkOwner();
        if (attacker == null) {
            return;
        }

        if (!(attacker instanceof Defender)) {
            if ((damage == 0) && (aggro <= 1) && attacker.isPlayable()) {
                final Player player = attacker.asPlayer();

                if (((fort != null) && fort.getZone().isActive())
                        || ((castle != null) && castle.getZone().isActive())
                        || ((hall != null) && hall.getSiegeZone().isActive())) {
                    final int activeSiegeId = (fort != null
                            ? fort.getResidenceId()
                            : (castle != null ? castle.getResidenceId() : (hall != null ? hall.getId() : 0)));

                    if ((player.getSiegeState() == 2) && player.isRegisteredOnThisSiegeField(activeSiegeId)) {
                        return;
                    }
                }
            }

            super.addDamageHate(attacker, damage, aggro);
        }
    }
}
