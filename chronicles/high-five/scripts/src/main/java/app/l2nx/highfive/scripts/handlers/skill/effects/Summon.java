/*
 * This file is part of the L2J Mobius project.
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package app.l2nx.highfive.scripts.handlers.skill.effects;

import app.l2nx.highfive.data.xml.ExperienceData;
import app.l2nx.highfive.data.xml.NpcData;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.enums.creature.Race;
import app.l2nx.highfive.entity.actor.instance.Servitor;
import app.l2nx.highfive.entity.actor.templates.NpcTemplate;
import app.l2nx.highfive.entity.item.holders.ItemHolder;
import app.l2nx.highfive.mechanics.conditions.Condition;
import app.l2nx.highfive.mechanics.effects.AbstractEffect;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.util.StatSet;

/**
 * Summon effect implementation.
 * @author UnAfraid
 */
public class Summon extends AbstractEffect
{
	private final int _npcId;
	private final float _expMultiplier;
	private final ItemHolder _consumeItem;
	private final int _lifeTime;
	private final int _consumeItemInterval;
	
	public Summon(Condition attachCond, Condition applyCond, StatSet set, StatSet params)
	{
		super(attachCond, applyCond, set, params);
		
		if (params.isEmpty())
		{
			throw new IllegalArgumentException("Summon effect without parameters!");
		}
		
		_npcId = params.getInt("npcId");
		_expMultiplier = params.getFloat("expMultiplier", 1);
		_consumeItem = new ItemHolder(params.getInt("consumeItemId", 0), params.getInt("consumeItemCount", 1));
		_consumeItemInterval = params.getInt("consumeItemInterval", 0);
		_lifeTime = params.getInt("lifeTime", 3600) * 1000;
	}
	
	@Override
	public boolean isInstant()
	{
		return true;
	}
	
	@Override
	public void onStart(Creature effector, Creature effected, Skill skill)
	{
		if (!effector.isPlayer() || effector.asPlayer().hasSummon())
		{
			return;
		}
		
		final Player player = effected.asPlayer();
		final NpcTemplate template = NpcData.getInstance().getTemplate(_npcId);
		final Servitor summon = new Servitor(template, player);
		final int consumeItemInterval = (_consumeItemInterval > 0 ? _consumeItemInterval : (template.getRace() != Race.SIEGE_WEAPON ? 240 : 60)) * 1000;
		summon.setName(template.getName());
		summon.setTitle(effected.getName());
		summon.setReferenceSkill(skill.getId());
		summon.setExpMultiplier(_expMultiplier);
		summon.setLifeTime(_lifeTime);
		summon.setItemConsume(_consumeItem);
		summon.setItemConsumeInterval(consumeItemInterval);
		
		final int maxPetLevel = ExperienceData.getInstance().getMaxPetLevel();
		if (summon.getLevel() >= maxPetLevel)
		{
			summon.getStat().setExp(ExperienceData.getInstance().getExpForLevel(maxPetLevel - 1));
		}
		else
		{
			summon.getStat().setExp(ExperienceData.getInstance().getExpForLevel(summon.getLevel() % maxPetLevel));
		}
		
		summon.fullRestore();
		summon.setHeading(player.getHeading());
		player.setPet(summon);
		summon.spawnMe();
		summon.setRunning();
	}
}
