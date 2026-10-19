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
package app.l2nx.highfive.mechanics.script;

import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.time.DateFormatUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import app.l2nx.highfive.data.xml.IXmlReader;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.schedule.RecurrenceSchedule;
import app.l2nx.highfive.util.StatSet;

/**
 * Abstract event class.
 * @author JIV, Mobius
 */
public abstract class Event extends Script
{
	/**
	 * Name prefix of the timers {@link #scheduleStart(String, StatSet)} sets; the script's timer handler starts the event on
	 * them and calls {@link #scheduleStart(String, StatSet)} again for the next start.
	 */
	protected static final String SCHEDULE_TIMER = "Schedule";

	public abstract boolean eventStart(Player eventMaker);

	public abstract boolean eventStop();

	public abstract boolean eventBypass(Player player, String bypass);

	/**
	 * Schedules a start of the event for every {@code <schedule rrule="..."/>} of the config file.
	 * @param configFile the config file path in the datapack
	 */
	protected void loadSchedules(String configFile)
	{
		new IXmlReader()
		{
			@Override
			public void load()
			{
				parseDatapackFile(configFile);
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
								scheduleStart(SCHEDULE_TIMER + count.incrementAndGet(), params);
								break;
							}
						}
					}
				});
			}
		}.load();
	}

	/**
	 * Sets the timer to the next instance of the schedule in {@code params}.
	 * @param timer the timer name, starting with {@link #SCHEDULE_TIMER}
	 * @param params the event name and its schedule, as set by {@link #loadSchedules(String)}
	 */
	protected void scheduleStart(String timer, StatSet params)
	{
		params.getObject("Schedule", RecurrenceSchedule.class).next().ifPresentOrElse(start ->
		{
			getTimers().addTimer(timer, params, (start.toEpochMilli() - System.currentTimeMillis()) + 5000, null, null); // Added 5 seconds to prevent overlapping.
			LOGGER.info("Event " + params.getString("Name") + " scheduled at " + DateFormatUtils.format(start.toEpochMilli(), "dd/MM/yyyy HH:mm:ss"));
		}, () -> LOGGER.warning("Event " + params.getString("Name") + " has no further start in its schedule."));
	}
}
