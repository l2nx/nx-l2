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
package app.l2nx.highfive.network.serverpackets;

import java.util.Collection;

import app.l2nx.highfive.data.xml.SkillData;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.ServerPackets;
import app.l2nx.network.buffer.WriteBuffer;

public class GMViewSkillInfo extends ServerPacket
{
	private final Player _player;
	private final Collection<Skill> _skills;
	
	public GMViewSkillInfo(Player player)
	{
		_player = player;
		_skills = _player.getAllSkills();
	}
	
	@Override
	public void writeImpl(GameClient client, WriteBuffer buffer)
	{
		ServerPackets.GM_VIEW_SKILL_INFO.writeId(this, buffer);
		buffer.writeString(_player.getName());
		buffer.writeInt(_skills.size());
		final boolean isDisabled = (_player.getClan() != null) ? (_player.getClan().getReputationScore() < 0) : false;
		for (Skill skill : _skills)
		{
			buffer.writeInt(skill.isPassive());
			buffer.writeInt(skill.getDisplayLevel());
			buffer.writeInt(skill.getDisplayId());
			buffer.writeByte(isDisabled && skill.isClanSkill());
			buffer.writeByte(SkillData.getInstance().isEnchantable(skill.getDisplayId()));
		}
	}
}
