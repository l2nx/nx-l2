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
package app.l2nx.highfive.network.protocol.p267.clientpackets;

import app.l2nx.highfive.config.PlayerConfig;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.transform.Transform;
import app.l2nx.highfive.mechanics.effects.EffectType;
import app.l2nx.highfive.mechanics.skill.CommonSkill;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.mechanics.skill.targets.TargetType;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ActionFailed;

public class RequestMagicSkillUse extends ClientPacket {
    private int magicId;
    private boolean ctrlPressed;
    private boolean shiftPressed;

    @Override
    protected void readImpl() {
        final app.l2nx.protocol.game.p267.clientpackets.RequestMagicSkillUse packet =
                app.l2nx.protocol.game.p267.clientpackets.RequestMagicSkillUse.decode(frame());
        magicId = packet.skillId();
        ctrlPressed = packet.ctrlPressed();
        shiftPressed = packet.shiftPressed();
    }

    @Override
    protected void runImpl() {
        final Player player = getPlayer();
        if (player == null) {
            return;
        }

        if (player.isDead()) {
            player.sendPacket(ActionFailed.STATIC_PACKET);
            return;
        }

        if (player.isFakeDeath()) {
            if (magicId != CommonSkill.FAKE_DEATH.getId()) {
                player.sendPacket(SystemMessageId.YOU_CANNOT_MOVE_WHILE_SITTING);
                player.sendPacket(ActionFailed.STATIC_PACKET);
                return;
            } else if (player.isSkillDisabled(CommonSkill.FAKE_DEATH.getSkill())) {
                player.sendPacket(ActionFailed.STATIC_PACKET);
                return;
            }
        }

        // Get the level of the used skill.
        Skill skill = player.getKnownSkill(magicId);
        if (skill == null) {
            // Player doesn't know this skill, maybe it's the display Id.
            skill = player.getCustomSkill(magicId);
            if (skill == null) {
                skill = player.getTransformSkill(magicId);
                if (skill == null) {
                    player.sendPacket(ActionFailed.STATIC_PACKET);

                    // if (_magicId > 0)
                    // {
                    // LOGGER.warning("Skill Id " + _magicId + " not found in player: " + player);
                    // }
                    return;
                }
            }
        }

        // Avoid Use of Skills in AirShip.
        if (player.isPlayable() && player.isInAirShip()) {
            player.sendPacket(SystemMessageId.THIS_ACTION_IS_PROHIBITED_WHILE_MOUNTED_OR_ON_AN_AIRSHIP);
            player.sendPacket(ActionFailed.STATIC_PACKET);
            return;
        }

        // Do not cast non transform skills when transformed.
        final Transform transform = player.getTransformation();
        if ((transform != null) && !transform.canUseWeaponStats() && !player.hasTransformSkill(skill.getId())) {
            player.sendPacket(ActionFailed.STATIC_PACKET);
            return;
        }

        // If alternate rule Karma punishment is set to true, forbid teleport to player with Karma.
        if (!PlayerConfig.ALT_GAME_KARMA_PLAYER_CAN_TELEPORT
                && (player.getKarma() > 0)
                && skill.hasEffectType(EffectType.TELEPORT)) {
            return;
        }

        // Players mounted on pets cannot use any toggle skills.
        if (skill.isToggle() && player.isMounted()) {
            return;
        }

        player.onActionRequest();

        // Stop if use self-buff (except if on AirShip or Boat).
        if ((skill.isContinuous() && !skill.isDebuff() && (skill.getTargetType() == TargetType.SELF))
                && (!player.isInAirShip() || !player.isInBoat())) {
            player.getAI().setIntentionMoveTo(player.getLocation());
        }

        player.useMagic(skill, ctrlPressed, shiftPressed);
    }
}
