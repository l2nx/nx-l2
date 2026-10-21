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

import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Npc;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.enums.creature.InstanceType;
import app.l2nx.highfive.entity.actor.templates.NpcTemplate;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ActionFailed;

public class Artefact extends Npc {
    /**
     * Creates a castle siege artifact.
     * @param template the artifact NPC template
     */
    public Artefact(NpcTemplate template) {
        super(template);
        setInstanceType(InstanceType.Artefact);
    }

    @Override
    public void onSpawn() {
        super.onSpawn();
        getCastle().registerArtefact(this);
    }

    @Override
    public boolean isArtefact() {
        return true;
    }

    @Override
    public boolean isAutoAttackable(Creature attacker) {
        return false;
    }

    @Override
    public boolean canBeAttacked() {
        return false;
    }

    @Override
    public void onForcedAttack(Player player) {
        // Send a Server->Client ActionFailed to the Player in order to avoid that the client wait another packet.
        player.sendPacket(ActionFailed.STATIC_PACKET);
    }

    @Override
    public void reduceCurrentHp(double damage, Creature attacker, Skill skill) {}

    @Override
    public void reduceCurrentHp(double damage, Creature attacker, boolean awake, boolean isDOT, Skill skill) {}
}
