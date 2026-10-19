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
package app.l2nx.highfive.scripts.custom.events.Elpies;

import java.io.File;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;

import org.w3c.dom.Document;
import org.w3c.dom.Node;

import app.l2nx.commons.threads.ThreadPool;
import app.l2nx.commons.time.TimeUtil;
import app.l2nx.commons.util.IXmlReader;
import app.l2nx.highfive.config.GeneralConfig;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.Npc;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.instance.EventMonster;
import app.l2nx.highfive.mechanics.script.Event;
import app.l2nx.highfive.schedule.RecurrenceSchedule;
import app.l2nx.highfive.util.StatSet;

public class Elpies extends Event
{
	// NPC
	private static final int ELPY = 900100;
	
	// Amount of Elpies to spawn when the event starts.
	private static final int ELPY_AMOUNT = 100;
	
	// Event duration in minutes
	private static final int EVENT_DURATION_MINUTES = 2;
	// @formatter:off
	private static final int[][] DROPLIST_CONSUMABLES =
	{
		// itemId, chance, min amount, max amount
		{  1540, 80, 10, 15 },	// Quick Healing Potion
		{  1538, 60,  5, 10 },	// Blessed Scroll of Escape
		{  3936, 40,  5, 10 },	// Blessed Scroll of Ressurection
		{  6387, 25,  5, 10 },	// Blessed Scroll of Ressurection Pets
		{ 22025, 15,  5, 10 },	// Powerful Healing Potion
		{  6622, 10,  1, 1 },	// Giant's Codex
		{ 20034,  5,  1, 1 },	// Revita Pop
		{ 20004,  1,  1, 1 },	// Energy Ginseng
		{ 20004,  0,  1, 1 }	// Energy Ginseng
	};
	
	private static final int[][] DROPLIST_CRYSTALS =
	{
		{ 1458, 80, 50, 100 },	// Crystal D-Grade
		{ 1459, 60, 40,  80 },	// Crystal C-Grade
		{ 1460, 40, 30,  60 },	// Crystal B-Grade
		{ 1461, 20, 20,  30 },	// Crystal A-Grade
		{ 1462,  0, 10,  20 }	// Crystal S-Grade
	};
	// @formatter:on
	
	// Non-final variables
	private static boolean EVENT_ACTIVE = false;
	private ScheduledFuture<?> _eventTask = null;
	private final Set<Npc> _elpies = ConcurrentHashMap.newKeySet(ELPY_AMOUNT);
	
	private Elpies()
	{
		addSpawnId(ELPY);
		addKillId(ELPY);
		
		loadConfig();
	}
	
	private void loadConfig()
	{
		new IXmlReader()
		{
			@Override
			public void load()
			{
				parseDatapackFile("data/scripts/custom/events/Elpies/config.xml");
			}
			
			@Override
			public void parseDocument(Document document, File file)
			{
				final AtomicInteger count = new AtomicInteger(0);
				forEach(document, "event", eventNode ->
				{
					final StatSet att = new StatSet(parseAttributes(eventNode));
					final String name = att.getString("name");
					for (Node node = document.getDocumentElement().getFirstChild(); node != null; node = node.getNextSibling())
					{
						switch (node.getNodeName())
						{
							case "schedule":
							{
								final StatSet attributes = new StatSet(parseAttributes(node));
								final StatSet params = new StatSet();
								params.set("Name", name);
								params.set("Schedule", new RecurrenceSchedule(attributes.getString("rrule")));
								scheduleStart("Schedule" + count.incrementAndGet(), params);
								break;
							}
						}
					}
				});
			}
		}.load();
	}
	
	@Override
	public void onTimerEvent(String event, StatSet params, Npc npc, Player player)
	{
		if (event.startsWith("Schedule"))
		{
			eventStart(null);
			scheduleStart(event, params);
		}
	}

	private void scheduleStart(String timer, StatSet params)
	{
		params.getObject("Schedule", RecurrenceSchedule.class).next().ifPresentOrElse(start ->
		{
			getTimers().addTimer(timer, params, (start.toEpochMilli() - System.currentTimeMillis()) + 5000, null, null); // Added 5 seconds to prevent overlapping.
			LOGGER.info("Event " + params.getString("Name") + " scheduled at " + TimeUtil.getDateTimeString(start.toEpochMilli()));
		}, () -> LOGGER.warning("Event " + params.getString("Name") + " has no further start in its schedule."));
	}
	
	@Override
	public boolean eventBypass(Player player, String bypass)
	{
		return false;
	}
	
	@Override
	public boolean eventStart(Player eventMaker)
	{
		if (EVENT_ACTIVE)
		{
			return false;
		}
		
		// Check Custom Table - we use custom NPCs.
		if (!GeneralConfig.CUSTOM_NPC_DATA)
		{
			LOGGER.info(getName() + ": Event can't be started because custom NPC table is disabled!");
			eventMaker.sendMessage("Event " + getName() + " can't be started because custom NPC table is disabled!");
			return false;
		}
		
		EVENT_ACTIVE = true;
		
		final EventLocation randomLoc = getRandomEntry(EventLocation.values());
		final long despawnDelay = EVENT_DURATION_MINUTES * 60000;
		for (int i = 0; i < ELPY_AMOUNT; i++)
		{
			_elpies.add(addSpawn(ELPY, randomLoc.getRandomX(), randomLoc.getRandomY(), randomLoc.getZ(), 0, true, despawnDelay));
		}
		
		World.broadcastToAllOnlinePlayers("*Squeak Squeak*");
		World.broadcastToAllOnlinePlayers("Elpy invasion in " + randomLoc.getName());
		World.broadcastToAllOnlinePlayers("Help us exterminate them!");
		World.broadcastToAllOnlinePlayers("You have " + EVENT_DURATION_MINUTES + " minutes!");
		_eventTask = ThreadPool.schedule(() ->
		{
			World.broadcastToAllOnlinePlayers("Time is up!");
			eventStop();
		}, despawnDelay);
		return true;
	}
	
	@Override
	public boolean eventStop()
	{
		if (!EVENT_ACTIVE)
		{
			return false;
		}
		
		EVENT_ACTIVE = false;
		if (_eventTask != null)
		{
			_eventTask.cancel(true);
			_eventTask = null;
		}
		
		for (Npc npc : _elpies)
		{
			npc.deleteMe();
		}
		
		_elpies.clear();
		
		World.broadcastToAllOnlinePlayers("*Squeak Squeak*");
		World.broadcastToAllOnlinePlayers("Elpy Event finished!");
		return true;
	}
	
	@Override
	public void onKill(Npc npc, Player killer, boolean isSummon)
	{
		if (EVENT_ACTIVE)
		{
			_elpies.remove(npc);
			
			dropItem(npc, killer, DROPLIST_CONSUMABLES);
			dropItem(npc, killer, DROPLIST_CRYSTALS);
			if (_elpies.isEmpty())
			{
				World.broadcastToAllOnlinePlayers("All elpies have been killed!");
				eventStop();
			}
		}
	}
	
	@Override
	public void onSpawn(Npc npc)
	{
		((EventMonster) npc).eventSetDropOnGround(true);
		((EventMonster) npc).eventSetBlockOffensiveSkills(true);
	}
	
	private enum EventLocation
	{
		ADEN("Aden", 146558, 148341, 26622, 28560, -2200),
		DION("Dion", 18564, 19200, 144377, 145782, -3081),
		GLUDIN("Gludin", -84040, -81420, 150257, 151175, -3125),
		HV("Hunters Village", 116094, 117141, 75776, 77072, -2700),
		OREN("Oren", 82048, 82940, 53240, 54126, -1490);
		
		private final String _name;
		private final int _minX;
		private final int _maxX;
		private final int _minY;
		private final int _maxY;
		private final int _z;
		
		EventLocation(String name, int minX, int maxX, int minY, int maxY, int z)
		{
			_name = name;
			_minX = minX;
			_maxX = maxX;
			_minY = minY;
			_maxY = maxY;
			_z = z;
		}
		
		public String getName()
		{
			return _name;
		}
		
		public int getRandomX()
		{
			return getRandom(_minX, _maxX);
		}
		
		public int getRandomY()
		{
			return getRandom(_minY, _maxY);
		}
		
		public int getZ()
		{
			return _z;
		}
	}
	
	private void dropItem(Npc mob, Player player, int[][] droplist)
	{
		final int chance = getRandom(100);
		for (int[] drop : droplist)
		{
			if (chance >= drop[1])
			{
				mob.dropItem(player, drop[0], getRandom(drop[2], drop[3]));
				break;
			}
		}
	}
	
	public static void main(String[] args)
	{
		new Elpies();
	}
}
