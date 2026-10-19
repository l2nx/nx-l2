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

import java.util.List;

import app.l2nx.highfive.config.GeneralConfig;
import app.l2nx.highfive.data.xml.SkillData;
import app.l2nx.highfive.data.xml.SkillTreeData;
import app.l2nx.highfive.entity.actor.Npc;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.enums.creature.InstanceType;
import app.l2nx.highfive.entity.actor.templates.NpcTemplate;
import app.l2nx.highfive.managers.FishingChampionshipManager;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.mechanics.skill.enums.AcquireSkillType;
import app.l2nx.highfive.mechanics.skill.holders.SkillLearn;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.serverpackets.AcquireSkillList;
import app.l2nx.highfive.network.serverpackets.NpcHtmlMessage;
import app.l2nx.highfive.network.serverpackets.SystemMessage;

public class Fisherman extends Merchant
{
	/**
	 * Creates a fisherman.
	 * @param template the fisherman NPC template
	 */
	public Fisherman(NpcTemplate template)
	{
		super(template);
		setInstanceType(InstanceType.Fisherman);
	}
	
	@Override
	public String getHtmlPath(int npcId, int value)
	{
		String pom = "";
		if (value == 0)
		{
			pom = Integer.toString(npcId);
		}
		else
		{
			pom = npcId + "-" + value;
		}
		
		return "data/html/fisherman/" + pom + ".htm";
	}
	
	@Override
	public void onBypassFeedback(Player player, String command)
	{
		if (command.equalsIgnoreCase("FishSkillList"))
		{
			showFishSkillList(player);
		}
		else if (command.startsWith("FishingChampionship"))
		{
			if (GeneralConfig.ALT_FISH_CHAMPIONSHIP_ENABLED)
			{
				FishingChampionshipManager.getInstance().showChampScreen(player, this);
			}
			else
			{
				sendHtml(player, this, "no_fish_event001.htm");
			}
		}
		else if (command.startsWith("FishingReward"))
		{
			if (GeneralConfig.ALT_FISH_CHAMPIONSHIP_ENABLED)
			{
				if (FishingChampionshipManager.getInstance().isWinner(player.getName()))
				{
					FishingChampionshipManager.getInstance().getReward(player);
				}
				else
				{
					sendHtml(player, this, "no_fish_event_reward001.htm");
				}
			}
			else
			{
				sendHtml(player, this, "no_fish_event001.htm");
			}
		}
		else
		{
			super.onBypassFeedback(player, command);
		}
	}
	
	public static void showFishSkillList(Player player)
	{
		final List<SkillLearn> skills = SkillTreeData.getInstance().getAvailableFishingSkills(player);
		final AcquireSkillList asl = new AcquireSkillList(AcquireSkillType.FISHING);
		int count = 0;
		for (SkillLearn s : skills)
		{
			final Skill sk = SkillData.getInstance().getSkill(s.getSkillId(), s.getSkillLevel());
			if (sk == null)
			{
				continue;
			}
			
			count++;
			asl.addSkill(s.getSkillId(), s.getSkillLevel(), s.getSkillLevel(), s.getLevelUpSp(), 1);
		}
		
		if (count == 0)
		{
			final int minlLevel = SkillTreeData.getInstance().getMinLevelForNewSkill(player, SkillTreeData.getInstance().getFishingSkillTree());
			if (minlLevel > 0)
			{
				final SystemMessage sm = new SystemMessage(SystemMessageId.YOU_DO_NOT_HAVE_ANY_FURTHER_SKILLS_TO_LEARN_COME_BACK_WHEN_YOU_HAVE_REACHED_LEVEL_S1);
				sm.addInt(minlLevel);
				player.sendPacket(sm);
			}
			else
			{
				player.sendPacket(SystemMessageId.THERE_ARE_NO_OTHER_SKILLS_TO_LEARN);
			}
		}
		else
		{
			player.sendPacket(asl);
		}
	}
	
	private void sendHtml(Player player, Npc npc, String htmlName)
	{
		final NpcHtmlMessage html = new NpcHtmlMessage(npc.getObjectId());
		html.setFile(player, "data/html/fisherman/championship/" + htmlName);
		player.sendPacket(html);
	}
}
